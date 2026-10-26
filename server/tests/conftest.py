"""Gemeinsame Fixtures. Firebase wird nie echt angesprochen: die Tests starten
die FastAPI-Lifespan nicht (kein _init_firebase, kein Tickle-Loop) und ersetzen
messaging.send_each, wo gesendet wird."""

from __future__ import annotations

import os
import sys
from pathlib import Path

import pytest

SERVER_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SERVER_DIR))

# Env muss vor dem Import von app gesetzt sein (Konfiguration wird beim Import gelesen).
os.environ.setdefault("API_KEY", "test-key")
os.environ.setdefault("DB_PATH", str(SERVER_DIR / "tests" / ".unused.db"))
os.environ.pop("GOOGLE_APPLICATION_CREDENTIALS_JSON", None)
os.environ.pop("GOOGLE_APPLICATION_CREDENTIALS", None)

import app as server  # noqa: E402

API_KEY = "test-key"


@pytest.fixture(autouse=True)
def fresh_state(tmp_path, monkeypatch):
    """Eigene SQLite-DB pro Test und zurückgesetzter Modul-Zustand."""
    monkeypatch.setattr(server, "DB_PATH", str(tmp_path / "tokens.db"))
    monkeypatch.setattr(server, "API_KEY", API_KEY)
    monkeypatch.setattr(server, "RATE_LIMIT_PER_MINUTE", 0.0)
    monkeypatch.setattr(server, "MAX_TOKENS", 5000)
    monkeypatch.setattr(server, "_quiet_active", False)
    server._buckets.clear()
    server._init_db()
    yield
    server._buckets.clear()


@pytest.fixture
def client():
    from fastapi.testclient import TestClient

    # Bewusst ohne "with": so läuft die Lifespan (Firebase-Init + Loop) nicht.
    return TestClient(server.app)


@pytest.fixture
def auth():
    return {"X-Api-Key": API_KEY}
