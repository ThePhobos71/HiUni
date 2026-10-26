"""FCM-Tickle-Server für HiUni.

Kennt NUR FCM-Device-Tokens (keine Zugangsdaten, keine Mail-Inhalte).
Weckt registrierte Apps periodisch per FCM-High-Priority-Data-Message
({"type": TICKLE_TYPE}) auf, damit die App selbst ihre Daten abruft. Default-Typ
ist "sync_tickle" (Mail + gestaffelter Feature-Prefetch); via Env TICKLE_TYPE
umstellbar auf "mail_tickle" für ältere App-Versionen, die nur den Mail-Wecker
verstehen.

Endpunkte:
  POST /register    {"token": "..."}   -> Token speichern/aktualisieren (upsert)
  POST /unregister  {"token": "..."}   -> Token entfernen
  GET  /healthz                        -> Health-Check (kein Auth nötig)

Auth: Header "X-Api-Key" muss dem Env "API_KEY" entsprechen (constant-time compare).
"""

from __future__ import annotations

import asyncio
import hmac
import json
import logging
import os
import re
import sqlite3
import time
from collections import Counter
from contextlib import asynccontextmanager, contextmanager
from datetime import datetime
from datetime import time as dtime
from pathlib import Path
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

import firebase_admin
from fastapi import Depends, FastAPI, Header, HTTPException, Request, status
from firebase_admin import credentials, messaging
from firebase_admin import exceptions as fb_exceptions
from pydantic import BaseModel, Field

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger("fcm_tickle")


# --------------------------------------------------------------------------
# Konfiguration (Env)
# --------------------------------------------------------------------------
#
# Alle Werte werden einmal beim Import gelesen. Funktionen greifen zur Laufzeit
# auf die Modul-Variablen zu, Tests können sie daher per monkeypatch umbiegen.

_QUIET_HOURS_RE = re.compile(r"^\s*(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})\s*$")


def parse_quiet_hours(spec: str) -> tuple[dtime, dtime] | None:
    """Parst "HH:MM-HH:MM" zu (start, ende). Leerer String -> None (deaktiviert).

    Der Bereich darf über Mitternacht gehen (z.B. "23:00-06:00"). Start und Ende
    sind inklusive bzw. exklusive: 23:00 ist schon ruhig, 06:00 nicht mehr.
    Bei ungültigem Format wird ein ValueError mit klarer Meldung geworfen.
    """
    if spec is None or not spec.strip():
        return None
    match = _QUIET_HOURS_RE.match(spec)
    if not match:
        raise ValueError(
            f"QUIET_HOURS={spec!r} ist ungültig: erwartet wird das Format "
            "'HH:MM-HH:MM' (z.B. '23:00-06:00') oder ein leerer String zum Deaktivieren."
        )
    h1, m1, h2, m2 = (int(g) for g in match.groups())
    for hour, minute in ((h1, m1), (h2, m2)):
        if not (0 <= hour <= 23 and 0 <= minute <= 59):
            raise ValueError(
                f"QUIET_HOURS={spec!r} ist ungültig: Stunden 00-23, Minuten 00-59."
            )
    start, end = dtime(h1, m1), dtime(h2, m2)
    if start == end:
        raise ValueError(
            f"QUIET_HOURS={spec!r} ist ungültig: Start und Ende sind identisch. "
            "Zum Deaktivieren QUIET_HOURS leer setzen."
        )
    return start, end


def is_quiet_time(local_time: dtime, window: tuple[dtime, dtime] | None) -> bool:
    """True, wenn local_time im Ruhezeit-Fenster [start, ende) liegt."""
    if window is None:
        return False
    start, end = window
    t = local_time.replace(tzinfo=None)
    if start < end:
        return start <= t < end
    # Fenster geht über Mitternacht (z.B. 23:00-06:00).
    return t >= start or t < end


def load_timezone(name: str) -> ZoneInfo:
    try:
        return ZoneInfo(name)
    except (ZoneInfoNotFoundError, ValueError) as exc:
        raise ValueError(
            f"TIMEZONE={name!r} ist ungültig: erwartet wird ein IANA-Zeitzonenname "
            "wie 'Europe/Berlin'."
        ) from exc


API_KEY = os.environ.get("API_KEY", "")
DB_PATH = os.environ.get("DB_PATH", "/data/tokens.db")
TICKLE_INTERVAL_MINUTES = float(os.environ.get("TICKLE_INTERVAL_MINUTES", "15"))
# Data-Message-Typ. "sync_tickle" (Default) → neue Apps machen Mail-Refresh +
# gestaffelten Feature-Prefetch. "mail_tickle" → alte Apps machen nur Mail.
TICKLE_TYPE = os.environ.get("TICKLE_TYPE", "sync_tickle")
# Tokens, deren last_seen älter als so viele Tage ist, werden im Tickle-Loop
# aufgeräumt. Die App re-registriert sich idempotent bei jedem Start und bei
# jedem FCM-Token-Rotate (onNewToken) → ein aktives Gerät aktualisiert last_seen
# also weit häufiger. 60 Tage sind großzügig darüber und fangen nur wirklich
# tote Geräte ab (App deinstalliert, nie wieder gestartet).
TOKEN_MAX_AGE_DAYS = float(os.environ.get("TOKEN_MAX_AGE_DAYS", "60"))
# Simples In-Memory-Rate-Limit für /register + /unregister (Token-Bucket je IP).
RATE_LIMIT_PER_MINUTE = float(os.environ.get("RATE_LIMIT_PER_MINUTE", "10"))
# Obergrenze für gespeicherte Tokens. Neue Tokens werden bei voller Tabelle mit
# 503 abgelehnt (die App versucht es dann mit Backoff später erneut), bereits
# bekannte Tokens dürfen sich immer re-registrieren. 0 = unbegrenzt.
MAX_TOKENS = int(os.environ.get("MAX_TOKENS", "5000"))
# Ruhezeit, in der keine Tickles verschickt werden (Akku/Nachtruhe). Format
# "HH:MM-HH:MM" in TIMEZONE, darf über Mitternacht gehen. Leer = deaktiviert.
TIMEZONE_NAME = os.environ.get("TIMEZONE", "Europe/Berlin")
TIMEZONE = load_timezone(TIMEZONE_NAME)
QUIET_HOURS_RAW = os.environ.get("QUIET_HOURS", "23:00-06:00")
QUIET_HOURS = parse_quiet_hours(QUIET_HOURS_RAW)
# FCM erlaubt maximal 500 Nachrichten pro send_each-Aufruf.
FCM_BATCH_SIZE = 500

if not API_KEY:
    logger.warning("API_KEY ist nicht gesetzt: /register und /unregister sind unerreichbar!")


# --------------------------------------------------------------------------
# Datenbank
# --------------------------------------------------------------------------

def _init_db() -> None:
    try:
        Path(DB_PATH).parent.mkdir(parents=True, exist_ok=True)
        with _connect() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS tokens (
                    token TEXT PRIMARY KEY,
                    created_at INTEGER NOT NULL,
                    last_seen INTEGER NOT NULL
                )
                """
            )
            conn.commit()
    except (OSError, sqlite3.Error) as exc:
        raise RuntimeError(
            f"SQLite-Datenbank unter DB_PATH={DB_PATH!r} nicht beschreibbar ({exc}). "
            "Liegt das Verzeichnis auf einem Volume, muss es dem Container-User "
            "(uid 1000) gehören, z.B. per 'chown -R 1000:1000 <volume-pfad>'."
        ) from exc


@contextmanager
def _connect():
    conn = sqlite3.connect(DB_PATH)
    try:
        yield conn
    finally:
        conn.close()


def register_token(token: str, max_tokens: int) -> bool:
    """Speichert ein Token oder aktualisiert last_seen eines bekannten Tokens.

    Gibt False zurück, wenn das Token neu ist und die Tabelle bereits
    max_tokens Einträge hat (max_tokens <= 0 = unbegrenzt). Bekannte Tokens
    werden immer akzeptiert.
    """
    now = int(time.time())
    with _connect() as conn:
        # IMMEDIATE: Prüfen + Einfügen als eine atomare Schreib-Transaktion,
        # damit parallele Registrierungen das Limit nicht überholen.
        conn.execute("BEGIN IMMEDIATE")
        try:
            cur = conn.execute(
                "UPDATE tokens SET last_seen = ? WHERE token = ?", (now, token)
            )
            if cur.rowcount == 0:
                if max_tokens > 0:
                    (count,) = conn.execute("SELECT COUNT(*) FROM tokens").fetchone()
                    if count >= max_tokens:
                        conn.rollback()
                        return False
                conn.execute(
                    "INSERT INTO tokens (token, created_at, last_seen) VALUES (?, ?, ?)",
                    (token, now, now),
                )
            conn.commit()
        except BaseException:
            conn.rollback()
            raise
    return True


def delete_token(token: str) -> None:
    with _connect() as conn:
        conn.execute("DELETE FROM tokens WHERE token = ?", (token,))
        conn.commit()


def delete_tokens(tokens: list[str]) -> None:
    if not tokens:
        return
    with _connect() as conn:
        conn.executemany("DELETE FROM tokens WHERE token = ?", [(t,) for t in tokens])
        conn.commit()


def all_tokens() -> list[str]:
    with _connect() as conn:
        rows = conn.execute("SELECT token FROM tokens").fetchall()
        return [row[0] for row in rows]


def purge_stale_tokens(max_age_days: float) -> int:
    """Löscht Tokens, deren last_seen älter als max_age_days ist. Gibt die Anzahl
    der entfernten Zeilen zurück. <= 0 deaktiviert das Purging."""
    if max_age_days <= 0:
        return 0
    cutoff = int(time.time()) - int(max_age_days * 24 * 60 * 60)
    with _connect() as conn:
        cur = conn.execute("DELETE FROM tokens WHERE last_seen < ?", (cutoff,))
        conn.commit()
        return cur.rowcount


# --------------------------------------------------------------------------
# Firebase Admin Init
# --------------------------------------------------------------------------

def _init_firebase() -> None:
    if firebase_admin._apps:
        return
    # Variante 1 (z.B. Coolify/PaaS ohne Datei-Mounts): kompletter Inhalt des
    # Service-Account-JSON als (mehrzeilige) Env-Variable.
    cred_json = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS_JSON", "").strip()
    cred_path = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS")
    if cred_json:
        try:
            cred = credentials.Certificate(json.loads(cred_json))
        except (ValueError, KeyError) as exc:
            raise RuntimeError(
                "GOOGLE_APPLICATION_CREDENTIALS_JSON ist gesetzt, aber kein "
                "gültiges Service-Account-JSON."
            ) from exc
        firebase_admin.initialize_app(cred)
        logger.info("Firebase Admin SDK initialisiert (Credentials aus Env-JSON).")
        return
    # Variante 2: Pfad zu einer gemounteten JSON-Datei (docker-compose/systemd).
    if cred_path and Path(cred_path).is_file():
        cred = credentials.Certificate(cred_path)
        firebase_admin.initialize_app(cred)
    else:
        # Fällt auf Application Default Credentials zurück (z.B. GCE/Cloud Run).
        firebase_admin.initialize_app()
    logger.info("Firebase Admin SDK initialisiert.")


# --------------------------------------------------------------------------
# Tickle-Loop
# --------------------------------------------------------------------------

# Merkt sich, ob die letzte Runde in der Ruhezeit lag, damit Beginn und Ende
# einer Ruhephase nur je einmal geloggt werden statt in jeder Runde.
_quiet_active = False


def in_quiet_hours(now: datetime | None = None) -> bool:
    if QUIET_HOURS is None:
        return False
    if now is None:
        now = datetime.now(TIMEZONE)
    elif now.tzinfo is not None:
        now = now.astimezone(TIMEZONE)
    return is_quiet_time(now.time(), QUIET_HOURS)


async def tickle_loop() -> None:
    interval_seconds = max(TICKLE_INTERVAL_MINUTES, 1) * 60
    while True:
        try:
            await tickle_round()
        except Exception:
            logger.exception("Tickle-Loop: unerwarteter Fehler, mache trotzdem weiter.")
        await asyncio.sleep(interval_seconds)


async def tickle_round(now: datetime | None = None) -> None:
    """Eine Runde: alte Tokens aufräumen, dann (außer in der Ruhezeit) tickeln."""
    global _quiet_active
    try:
        removed = await asyncio.to_thread(purge_stale_tokens, TOKEN_MAX_AGE_DAYS)
        if removed:
            logger.info(
                "Token-Purge: %d Token(s) älter als %s Tage entfernt.",
                removed,
                TOKEN_MAX_AGE_DAYS,
            )
    except Exception:
        logger.exception("Token-Purge fehlgeschlagen, mache trotzdem weiter.")

    if in_quiet_hours(now):
        if not _quiet_active:
            _quiet_active = True
            logger.info(
                "Ruhezeit beginnt (%s, %s): keine Tickles bis %s.",
                QUIET_HOURS_RAW.strip(),
                TIMEZONE_NAME,
                QUIET_HOURS[1].strftime("%H:%M"),
            )
        return
    if _quiet_active:
        _quiet_active = False
        logger.info("Ruhezeit vorbei, Tickles laufen wieder.")

    await send_tickle_to_all()


# Fehler, bei denen das Token dauerhaft unbrauchbar ist und gelöscht wird.
# Alles andere (Quota, Server-Fehler, Netzwerk) gilt als temporär.
_PERMANENT_TOKEN_ERRORS = (
    messaging.UnregisteredError,
    messaging.SenderIdMismatchError,
    fb_exceptions.InvalidArgumentError,
)


def _build_message(token: str) -> messaging.Message:
    return messaging.Message(
        token=token,
        data={"type": TICKLE_TYPE},
        android=messaging.AndroidConfig(priority="high"),
    )


def _send_batch(tokens: list[str]) -> tuple[int, int, int]:
    """Schickt einen Batch (max. FCM_BATCH_SIZE) per send_each und wertet jede
    Einzelantwort aus. Gibt (ok, entfernt, fehlgeschlagen) zurück."""
    batch = messaging.send_each([_build_message(t) for t in tokens])
    ok = 0
    to_delete: list[str] = []
    transient: Counter[str] = Counter()
    for token, resp in zip(tokens, batch.responses):
        if resp.success:
            ok += 1
        elif isinstance(resp.exception, _PERMANENT_TOKEN_ERRORS):
            to_delete.append(token)
        else:
            transient[type(resp.exception).__name__] += 1
    if to_delete:
        delete_tokens(to_delete)
        logger.info(
            "%d Token(s) ungültig/nicht mehr registriert, aus DB entfernt.",
            len(to_delete),
        )
    if transient:
        logger.warning(
            "%d Token(s) mit temporärem Fehler, Tokens bleiben erhalten: %s",
            sum(transient.values()),
            ", ".join(f"{name}={n}" for name, n in transient.most_common()),
        )
    return ok, len(to_delete), sum(transient.values())


async def send_tickle_to_all() -> dict[str, int]:
    summary = {"ok": 0, "removed": 0, "failed": 0}
    tokens = all_tokens()
    if not tokens:
        logger.info("Tickle-Runde: keine Tokens registriert, überspringe.")
        return summary

    logger.info("Tickle-Runde: sende an %d Token(s).", len(tokens))
    for start in range(0, len(tokens), FCM_BATCH_SIZE):
        chunk = tokens[start : start + FCM_BATCH_SIZE]
        try:
            ok, removed, failed = await asyncio.to_thread(_send_batch, chunk)
        except Exception:
            # Ganzer Batch gescheitert (z.B. Credentials, Netzwerk): nichts
            # löschen, nächste Runde versucht es erneut.
            logger.exception(
                "Batch mit %d Token(s) fehlgeschlagen, Tokens bleiben erhalten.",
                len(chunk),
            )
            ok, removed, failed = 0, 0, len(chunk)
        summary["ok"] += ok
        summary["removed"] += removed
        summary["failed"] += failed

    logger.info(
        "Tickle-Runde fertig: %d gesendet, %d entfernt, %d fehlgeschlagen.",
        summary["ok"],
        summary["removed"],
        summary["failed"],
    )
    return summary


# --------------------------------------------------------------------------
# FastAPI App
# --------------------------------------------------------------------------

_tickle_task: asyncio.Task | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _tickle_task
    _init_db()
    _init_firebase()
    _tickle_task = asyncio.create_task(tickle_loop())
    logger.info(
        "FCM-Tickle-Server gestartet (Intervall=%s Minuten, Typ=%s, DB=%s, "
        "Token-Max-Alter=%s Tage, Rate-Limit=%s/min, Max-Tokens=%s, "
        "Ruhezeit=%s, Zeitzone=%s).",
        TICKLE_INTERVAL_MINUTES,
        TICKLE_TYPE,
        DB_PATH,
        TOKEN_MAX_AGE_DAYS,
        RATE_LIMIT_PER_MINUTE,
        MAX_TOKENS if MAX_TOKENS > 0 else "unbegrenzt",
        QUIET_HOURS_RAW.strip() if QUIET_HOURS else "aus",
        TIMEZONE_NAME,
    )
    try:
        yield
    finally:
        if _tickle_task:
            _tickle_task.cancel()
            try:
                await _tickle_task
            except asyncio.CancelledError:
                pass


app = FastAPI(title="HiUni FCM Tickle Server", lifespan=lifespan)


class TokenRequest(BaseModel):
    token: str = Field(min_length=1, max_length=4096)


def require_api_key(x_api_key: str | None = Header(default=None)) -> None:
    if not API_KEY or not x_api_key or not hmac.compare_digest(x_api_key, API_KEY):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Ungültiger API-Key.")


# --------------------------------------------------------------------------
# In-Memory-Rate-Limit (Token-Bucket je Client-IP)
# --------------------------------------------------------------------------
#
# Bewusst ohne externe Dependency (kein slowapi/redis): ein Prozess, ein Dict.
# Jede IP bekommt einen Bucket mit RATE_LIMIT_PER_MINUTE Tokens, der sich mit
# RATE_LIMIT_PER_MINUTE/60 Tokens pro Sekunde bis zur Kapazität wieder auffüllt.
# Ein Request kostet 1 Token; ist der Bucket leer → HTTP 429. Reicht als
# Missbrauchs-Bremse gegen Register/Unregister-Fluten hinter dem Reverse-Proxy.

_buckets: dict[str, tuple[float, float]] = {}  # ip -> (tokens, last_refill_ts)


def _client_ip(request: Request) -> str:
    # Hinter einem Reverse-Proxy (Coolify/Traefik) trägt X-Forwarded-For die
    # echte Client-IP (erste Adresse der Kette). Sonst die direkte Peer-Adresse.
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


def _allow(ip: str) -> bool:
    if RATE_LIMIT_PER_MINUTE <= 0:
        return True
    capacity = RATE_LIMIT_PER_MINUTE
    refill_per_sec = RATE_LIMIT_PER_MINUTE / 60.0
    now = time.monotonic()
    tokens, last = _buckets.get(ip, (capacity, now))
    tokens = min(capacity, tokens + (now - last) * refill_per_sec)
    if tokens < 1.0:
        _buckets[ip] = (tokens, now)
        return False
    _buckets[ip] = (tokens - 1.0, now)
    return True


def rate_limit(request: Request) -> None:
    if not _allow(_client_ip(request)):
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Zu viele Anfragen, bitte kurz warten.",
        )


@app.get("/healthz")
async def healthz() -> dict:
    return {"status": "ok"}


@app.post("/register", dependencies=[Depends(rate_limit), Depends(require_api_key)])
async def register(body: TokenRequest) -> dict:
    if not register_token(body.token, MAX_TOKENS):
        logger.warning(
            "Token-Limit erreicht (MAX_TOKENS=%d), neue Registrierung abgelehnt.",
            MAX_TOKENS,
        )
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Server ist ausgelastet (Token-Limit erreicht), bitte später erneut versuchen.",
        )
    return {"status": "registered"}


@app.post("/unregister", dependencies=[Depends(rate_limit), Depends(require_api_key)])
async def unregister(body: TokenRequest) -> dict:
    delete_token(body.token)
    return {"status": "unregistered"}
