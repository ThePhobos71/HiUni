from __future__ import annotations

import sqlite3
import time

import pytest

from conftest import server


def _rows():
    with sqlite3.connect(server.DB_PATH) as conn:
        return {
            token: (created, seen)
            for token, created, seen in conn.execute(
                "SELECT token, created_at, last_seen FROM tokens"
            )
        }


def test_healthz_needs_no_auth(client):
    resp = client.get("/healthz")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


@pytest.mark.parametrize("path", ["/register", "/unregister"])
@pytest.mark.parametrize("headers", [{}, {"X-Api-Key": "falsch"}, {"X-Api-Key": ""}])
def test_missing_or_wrong_api_key_is_401(client, path, headers):
    resp = client.post(path, json={"token": "abc"}, headers=headers)
    assert resp.status_code == 401
    assert _rows() == {}


def test_empty_server_api_key_locks_endpoints(client, monkeypatch):
    monkeypatch.setattr(server, "API_KEY", "")
    resp = client.post("/register", json={"token": "abc"}, headers={"X-Api-Key": ""})
    assert resp.status_code == 401


def test_register_with_right_key(client, auth):
    resp = client.post("/register", json={"token": "abc"}, headers=auth)
    assert resp.status_code == 200
    assert resp.json() == {"status": "registered"}
    assert set(_rows()) == {"abc"}


def test_register_upsert_updates_last_seen(client, auth, monkeypatch):
    now = [1_000_000]
    monkeypatch.setattr(server.time, "time", lambda: now[0])
    assert client.post("/register", json={"token": "abc"}, headers=auth).status_code == 200
    assert _rows()["abc"] == (1_000_000, 1_000_000)

    now[0] = 1_000_500
    assert client.post("/register", json={"token": "abc"}, headers=auth).status_code == 200
    rows = _rows()
    assert len(rows) == 1
    assert rows["abc"] == (1_000_000, 1_000_500)  # created_at bleibt, last_seen neu


def test_register_rejects_empty_token(client, auth):
    resp = client.post("/register", json={"token": ""}, headers=auth)
    assert resp.status_code == 422


def test_unregister_removes_token(client, auth):
    client.post("/register", json={"token": "abc"}, headers=auth)
    client.post("/register", json={"token": "def"}, headers=auth)
    resp = client.post("/unregister", json={"token": "abc"}, headers=auth)
    assert resp.status_code == 200
    assert resp.json() == {"status": "unregistered"}
    assert set(_rows()) == {"def"}


def test_unregister_unknown_token_is_ok(client, auth):
    resp = client.post("/unregister", json={"token": "gibts-nicht"}, headers=auth)
    assert resp.status_code == 200


def test_rate_limit_returns_429(client, auth, monkeypatch):
    monkeypatch.setattr(server, "RATE_LIMIT_PER_MINUTE", 2.0)
    ip = {"X-Forwarded-For": "203.0.113.7, 10.0.0.1"}
    assert client.post("/register", json={"token": "a"}, headers={**auth, **ip}).status_code == 200
    assert client.post("/register", json={"token": "b"}, headers={**auth, **ip}).status_code == 200
    resp = client.post("/register", json={"token": "c"}, headers={**auth, **ip})
    assert resp.status_code == 429
    # Andere Client-IP hat ihren eigenen Bucket.
    other = {"X-Forwarded-For": "198.51.100.1"}
    assert client.post("/register", json={"token": "c"}, headers={**auth, **other}).status_code == 200


def test_rate_limit_also_applies_without_valid_key(client, monkeypatch):
    monkeypatch.setattr(server, "RATE_LIMIT_PER_MINUTE", 1.0)
    assert client.post("/register", json={"token": "a"}).status_code == 401
    assert client.post("/register", json={"token": "a"}).status_code == 429


def test_token_cap_rejects_new_token_with_503(client, auth, monkeypatch):
    monkeypatch.setattr(server, "MAX_TOKENS", 2)
    assert client.post("/register", json={"token": "a"}, headers=auth).status_code == 200
    assert client.post("/register", json={"token": "b"}, headers=auth).status_code == 200
    resp = client.post("/register", json={"token": "c"}, headers=auth)
    assert resp.status_code == 503
    assert "Token-Limit" in resp.json()["detail"]
    assert set(_rows()) == {"a", "b"}


def test_token_cap_allows_reregister_of_known_token(client, auth, monkeypatch):
    monkeypatch.setattr(server, "MAX_TOKENS", 1)
    now = [1_000_000]
    monkeypatch.setattr(server.time, "time", lambda: now[0])
    assert client.post("/register", json={"token": "a"}, headers=auth).status_code == 200
    now[0] += 60
    assert client.post("/register", json={"token": "a"}, headers=auth).status_code == 200
    assert _rows()["a"] == (1_000_000, 1_000_060)


def test_token_cap_zero_means_unlimited(client, auth, monkeypatch):
    monkeypatch.setattr(server, "MAX_TOKENS", 0)
    for i in range(20):
        assert client.post("/register", json={"token": f"t{i}"}, headers=auth).status_code == 200
    assert len(_rows()) == 20


def test_token_cap_frees_slot_after_unregister(client, auth, monkeypatch):
    monkeypatch.setattr(server, "MAX_TOKENS", 1)
    client.post("/register", json={"token": "a"}, headers=auth)
    assert client.post("/register", json={"token": "b"}, headers=auth).status_code == 503
    client.post("/unregister", json={"token": "a"}, headers=auth)
    assert client.post("/register", json={"token": "b"}, headers=auth).status_code == 200


def test_purge_stale_tokens():
    now = int(time.time())
    day = 24 * 60 * 60
    with sqlite3.connect(server.DB_PATH) as conn:
        conn.executemany(
            "INSERT INTO tokens (token, created_at, last_seen) VALUES (?, ?, ?)",
            [
                ("frisch", now, now),
                ("grenzwertig", now - 59 * day, now - 59 * day),
                ("alt", now - 61 * day, now - 61 * day),
                ("uralt", now - 400 * day, now - 400 * day),
            ],
        )
    assert server.purge_stale_tokens(60) == 2
    assert set(_rows()) == {"frisch", "grenzwertig"}


def test_purge_disabled_with_zero():
    with sqlite3.connect(server.DB_PATH) as conn:
        conn.execute("INSERT INTO tokens VALUES ('alt', 0, 0)")
    assert server.purge_stale_tokens(0) == 0
    assert set(_rows()) == {"alt"}
