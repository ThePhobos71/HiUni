"""FCM-Tickle-Server für HiUni.

Kennt NUR FCM-Device-Tokens (keine Zugangsdaten, keine Mail-Inhalte).
Weckt registrierte Apps periodisch per FCM-High-Priority-Data-Message
({"type": "mail_tickle"}) auf, damit die App selbst ihre Mails abruft.

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
from fastapi import Depends, FastAPI, Header, HTTPException, status
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
        data={"type": "mail_tickle"},
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
        "FCM-Tickle-Server gestartet (Intervall=%s Minuten, DB=%s).",
        TICKLE_INTERVAL_MINUTES,
        DB_PATH,
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


@app.get("/healthz")
async def healthz() -> dict:
    return {"status": "ok"}


@app.post("/register", dependencies=[Depends(require_api_key)])
async def register(body: TokenRequest) -> dict:
    upsert_token(body.token)
    return {"status": "registered"}


@app.post("/unregister", dependencies=[Depends(require_api_key)])
async def unregister(body: TokenRequest) -> dict:
    delete_token(body.token)
    return {"status": "unregistered"}
