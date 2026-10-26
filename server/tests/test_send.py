from __future__ import annotations

import asyncio
import sqlite3

from firebase_admin import exceptions as fb_exceptions
from firebase_admin import messaging

from conftest import server


def _insert(tokens):
    with sqlite3.connect(server.DB_PATH) as conn:
        conn.executemany(
            "INSERT INTO tokens (token, created_at, last_seen) VALUES (?, 0, 4102444800)",
            [(t,) for t in tokens],
        )


def _stored():
    with sqlite3.connect(server.DB_PATH) as conn:
        return {row[0] for row in conn.execute("SELECT token FROM tokens")}


class FakeFcm:
    """Ersatz für messaging.send_each. errors: token -> Exception."""

    def __init__(self, errors=None, raise_batch=None):
        self.errors = errors or {}
        self.raise_batch = raise_batch
        self.calls: list[list[messaging.Message]] = []

    def __call__(self, messages, dry_run=False, app=None):
        self.calls.append(list(messages))
        if self.raise_batch:
            raise self.raise_batch
        responses = []
        for msg in messages:
            exc = self.errors.get(msg.token)
            if exc is None:
                responses.append(messaging.SendResponse({"name": f"projects/x/messages/{msg.token}"}, None))
            else:
                responses.append(messaging.SendResponse(None, exc))
        return messaging.BatchResponse(responses)


def _run():
    return asyncio.run(server.send_tickle_to_all())


def test_message_payload_is_content_free(monkeypatch):
    fake = FakeFcm()
    monkeypatch.setattr(server.messaging, "send_each", fake)
    _insert(["a"])
    _run()
    (msg,) = fake.calls[0]
    assert msg.token == "a"
    assert msg.data == {"type": server.TICKLE_TYPE}
    assert msg.notification is None
    assert msg.android.priority == "high"


def test_no_tokens_no_send(monkeypatch):
    fake = FakeFcm()
    monkeypatch.setattr(server.messaging, "send_each", fake)
    assert _run() == {"ok": 0, "removed": 0, "failed": 0}
    assert fake.calls == []


def test_permanent_errors_delete_token_transient_keeps(monkeypatch):
    fake = FakeFcm(
        errors={
            "unreg": messaging.UnregisteredError("weg"),
            "mismatch": messaging.SenderIdMismatchError("anderes Projekt"),
            "invalid": fb_exceptions.InvalidArgumentError("kaputt"),
            "quota": messaging.QuotaExceededError("zu viel"),
            "unavail": fb_exceptions.UnavailableError("später"),
        }
    )
    monkeypatch.setattr(server.messaging, "send_each", fake)
    _insert(["ok1", "unreg", "mismatch", "invalid", "quota", "unavail", "ok2"])

    summary = _run()

    assert summary == {"ok": 2, "removed": 3, "failed": 2}
    assert _stored() == {"ok1", "ok2", "quota", "unavail"}


def test_more_than_500_tokens_split_into_batches(monkeypatch):
    fake = FakeFcm(errors={"t0750": messaging.UnregisteredError("weg")})
    monkeypatch.setattr(server.messaging, "send_each", fake)
    tokens = [f"t{i:04d}" for i in range(1201)]
    _insert(tokens)

    summary = _run()

    assert [len(c) for c in fake.calls] == [500, 500, 201]
    sent = [m.token for call in fake.calls for m in call]
    assert sorted(sent) == tokens  # jedes Token genau einmal
    assert summary == {"ok": 1200, "removed": 1, "failed": 0}
    assert "t0750" not in _stored()
    assert len(_stored()) == 1200


def test_whole_batch_failure_keeps_all_tokens(monkeypatch):
    fake = FakeFcm(raise_batch=fb_exceptions.UnauthenticatedError("keine Credentials"))
    monkeypatch.setattr(server.messaging, "send_each", fake)
    _insert(["a", "b", "c"])

    summary = _run()

    assert summary == {"ok": 0, "removed": 0, "failed": 3}
    assert _stored() == {"a", "b", "c"}
