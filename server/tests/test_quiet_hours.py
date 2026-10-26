from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timezone
from datetime import time as dtime
from zoneinfo import ZoneInfo

import pytest

from conftest import server


def test_parse_simple_range():
    assert server.parse_quiet_hours("01:30-05:00") == (dtime(1, 30), dtime(5, 0))


def test_parse_allows_single_digit_hour_and_spaces():
    assert server.parse_quiet_hours(" 7:05 - 9:00 ") == (dtime(7, 5), dtime(9, 0))


@pytest.mark.parametrize("spec", ["", "   ", "off", "OFF", " Aus ", "none", "0"])
def test_parse_empty_disables(spec):
    assert server.parse_quiet_hours(spec) is None


@pytest.mark.parametrize(
    "spec",
    ["23-06", "23:00", "23:00-", "abc", "24:00-06:00", "23:60-06:00", "23:00-06:00-07:00", "10:00-10:00"],
)
def test_parse_rejects_garbage(spec):
    with pytest.raises(ValueError, match="QUIET_HOURS"):
        server.parse_quiet_hours(spec)


def test_load_timezone_rejects_garbage():
    with pytest.raises(ValueError, match="TIMEZONE"):
        server.load_timezone("Mars/Olympus_Mons")


@pytest.mark.parametrize(
    "t,expected",
    [
        (dtime(22, 59), False),
        (dtime(23, 0), True),
        (dtime(23, 59), True),
        (dtime(0, 0), True),
        (dtime(3, 15), True),
        (dtime(5, 59), True),
        (dtime(6, 0), False),
        (dtime(12, 0), False),
    ],
)
def test_midnight_wrap(t, expected):
    window = server.parse_quiet_hours("23:00-06:00")
    assert server.is_quiet_time(t, window) is expected


@pytest.mark.parametrize(
    "t,expected",
    [(dtime(0, 59), False), (dtime(1, 0), True), (dtime(4, 59), True), (dtime(5, 0), False)],
)
def test_same_day_range(t, expected):
    window = server.parse_quiet_hours("01:00-05:00")
    assert server.is_quiet_time(t, window) is expected


def test_disabled_is_never_quiet():
    assert server.is_quiet_time(dtime(3, 0), None) is False


def test_in_quiet_hours_converts_to_configured_timezone(monkeypatch):
    monkeypatch.setattr(server, "QUIET_HOURS", server.parse_quiet_hours("23:00-06:00"))
    monkeypatch.setattr(server, "TIMEZONE", ZoneInfo("Europe/Berlin"))
    # 21:30 UTC im Sommer = 23:30 in Berlin (CEST) -> ruhig.
    assert server.in_quiet_hours(datetime(2026, 7, 1, 21, 30, tzinfo=timezone.utc)) is True
    # 21:30 UTC im Winter = 22:30 in Berlin (CET) -> nicht ruhig.
    assert server.in_quiet_hours(datetime(2026, 1, 15, 21, 30, tzinfo=timezone.utc)) is False


def _berlin(hour, minute=0):
    return datetime(2026, 3, 10, hour, minute, tzinfo=ZoneInfo("Europe/Berlin"))


def test_tickle_round_skips_send_in_quiet_hours_and_logs_once(monkeypatch, caplog):
    monkeypatch.setattr(server, "QUIET_HOURS_RAW", "23:00-06:00")
    monkeypatch.setattr(server, "QUIET_HOURS", server.parse_quiet_hours("23:00-06:00"))
    monkeypatch.setattr(server, "TIMEZONE", ZoneInfo("Europe/Berlin"))
    purges, sends = [], []
    monkeypatch.setattr(server, "purge_stale_tokens", lambda days: purges.append(days) or 0)

    async def fake_send():
        sends.append(1)

    monkeypatch.setattr(server, "send_tickle_to_all", fake_send)

    with caplog.at_level(logging.INFO, logger="fcm_tickle"):
        for when in (_berlin(23, 0), _berlin(23, 15), _berlin(3, 0)):
            asyncio.run(server.tickle_round(when))
        assert sends == []
        assert len(purges) == 3  # Purge läuft auch in der Ruhezeit
        starts = [r for r in caplog.records if "Ruhezeit beginnt" in r.getMessage()]
        assert len(starts) == 1

        asyncio.run(server.tickle_round(_berlin(6, 0)))
        asyncio.run(server.tickle_round(_berlin(6, 15)))
        assert sends == [1, 1]
        ends = [r for r in caplog.records if "Ruhezeit vorbei" in r.getMessage()]
        assert len(ends) == 1


def test_tickle_round_sends_when_quiet_hours_disabled(monkeypatch):
    monkeypatch.setattr(server, "QUIET_HOURS", None)
    sends = []

    async def fake_send():
        sends.append(1)

    monkeypatch.setattr(server, "send_tickle_to_all", fake_send)
    asyncio.run(server.tickle_round(_berlin(3, 0)))
    assert sends == [1]
