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
import sqlite3
import time
from contextlib import asynccontextmanager, contextmanager
from pathlib import Path

import firebase_admin
from fastapi import Depends, FastAPI, Header, HTTPException, Request, status
from firebase_admin import credentials, messaging
from pydantic import BaseModel, Field

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger("fcm_tickle")

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

if not API_KEY:
    logger.warning("API_KEY ist nicht gesetzt — /register und /unregister sind unerreichbar!")


# --------------------------------------------------------------------------
# Datenbank
# --------------------------------------------------------------------------

def _init_db() -> None:
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


@contextmanager
def _connect():
    conn = sqlite3.connect(DB_PATH)
    try:
        yield conn
    finally:
        conn.close()


def upsert_token(token: str) -> None:
    now = int(time.time())
    with _connect() as conn:
        conn.execute(
            """
            INSERT INTO tokens (token, created_at, last_seen)
            VALUES (?, ?, ?)
            ON CONFLICT(token) DO UPDATE SET last_seen = excluded.last_seen
            """,
            (token, now, now),
        )
        conn.commit()


def delete_token(token: str) -> None:
    with _connect() as conn:
        conn.execute("DELETE FROM tokens WHERE token = ?", (token,))
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

async def tickle_loop() -> None:
    interval_seconds = max(TICKLE_INTERVAL_MINUTES, 1) * 60
    while True:
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
        try:
            await send_tickle_to_all()
        except Exception:
            logger.exception("Tickle-Loop: unerwarteter Fehler, mache trotzdem weiter.")
        await asyncio.sleep(interval_seconds)


async def send_tickle_to_all() -> None:
    tokens = all_tokens()
    if not tokens:
        logger.info("Tickle-Runde: keine Tokens registriert, überspringe.")
        return

    logger.info("Tickle-Runde: sende an %d Token(s).", len(tokens))
    for token in tokens:
        await asyncio.to_thread(_send_one, token)


def _send_one(token: str) -> None:
    message = messaging.Message(
        token=token,
        data={"type": TICKLE_TYPE},
        android=messaging.AndroidConfig(priority="high"),
    )
    try:
        messaging.send(message)
    except (messaging.UnregisteredError, messaging.SenderIdMismatchError):
        logger.info("Token ungültig/nicht mehr registriert, entferne aus DB.")
        delete_token(token)
    except firebase_admin.exceptions.InvalidArgumentError:
        logger.warning("Ungültiges Token-Format, entferne aus DB.")
        delete_token(token)
    except Exception:
        logger.exception("Senden an ein Token fehlgeschlagen, Token bleibt erhalten.")


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
        "Token-Max-Alter=%s Tage, Rate-Limit=%s/min).",
        TICKLE_INTERVAL_MINUTES,
        TICKLE_TYPE,
        DB_PATH,
        TOKEN_MAX_AGE_DAYS,
        RATE_LIMIT_PER_MINUTE,
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
    # Hinter einem Reverse-Proxy trägt X-Forwarded-For die echte Client-IP
    # (erste Adresse der Kette). Sonst die direkte Peer-Adresse.
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
    upsert_token(body.token)
    return {"status": "registered"}


@app.post("/unregister", dependencies=[Depends(rate_limit), Depends(require_api_key)])
async def unregister(body: TokenRequest) -> dict:
    delete_token(body.token)
    return {"status": "unregistered"}
