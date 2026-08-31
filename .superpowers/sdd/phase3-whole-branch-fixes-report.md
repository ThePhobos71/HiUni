# Phase-3 Whole-Branch-Review — Fix-Report

Single batch commit covering C1, C2, I1–I5, M1–M3. Reviewer was Opus, all 10 findings tracked.

## Files modified per finding

### C1 — SyncFrame.Event.type collision with classDiscriminator
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/SyncFrame.kt`
  - `Event.type` renamed → `Event.kind`
  - (Bonus, opt-in I1 wire extension: `Hello.sinceId`, `Events.cursorId`)
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Routes.kt`
  - `parsed.type` → `parsed.kind`, `store.insert(eventId, kind, …)` constructor uses `kind`
- `hiuni-relay/src/test/kotlin/de/transio/hiuni/relay/SyncEndpointTest.kt`
  - New regression test: `SyncFrame_Event roundtrips through classDiscriminator-aware Json`
  - Encodes a `SyncFrame.Event(kind="review", data=…)` via the same `json` instance that
    Routes.kt uses (with `classDiscriminator = "type"`), decodes back, asserts `kind` and
    `data` preserved. **PASSES** (see `test-results/test/TEST-…SyncEndpointTest.xml`,
    `tests=2 failures=0 errors=0`).

### C2 — Client-supplied eventId is trusted
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Routes.kt`
  - Inline `TODO(task-19)` comment above the `eventId` fallback, pointing at the
    Phase-3 review and the canonical-hash work that lands with signature verification.
  - No semantic rename (per instructions — column stays `event_id`).

### I1 — Composite (ts, eventId) cursor
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/SyncFrame.kt`
  - `Hello.sinceId: String? = null` (backward-compat default null)
  - `Events.cursorId: String? = null`
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/EventStore.kt`
  - `Batch` data class gained `cursorId: String? = null`
  - `queryAfter(sinceMs, limit, sinceId: String? = null)` — when `sinceId` given,
    SQL becomes `WHERE ts > ? OR (ts = ? AND event_id > ?) ORDER BY ts ASC, event_id ASC`;
    otherwise legacy `ts > ?` keeps backward-compat
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Routes.kt`
  - Hello handler passes `parsed.sinceId` to `queryAfter` and surfaces `batch.cursorId`
    in the outgoing `Events` frame
- **Wire-protocol-extension is opt-in**: legacy clients sending `Hello` without
  `sinceId` get the same behaviour as before; legacy clients receiving an `Events`
  frame with a `cursorId` field ignore the unknown field (because the relay's
  `json` config uses `ignoreUnknownKeys = true`, and so do app-side decoders).
  New clients can adopt the composite cursor when ready.

### I2 — /health O(n) → O(1)
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/EventStore.kt`
  - Added `totalCount(): Long` (`SELECT COUNT(*) FROM events`)
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Routes.kt`
  - `/health` uses `store.totalCount()` instead of `queryAfter(0L, Int.MAX_VALUE).items.size`

### I3 — Relay-side spam-limit
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Routes.kt`
  - Constant `SPAM_LIMIT_PER_DAY = 50`
  - Event handler: `store.countSince(pubkey, ts - 24h) >= 50` → drop with
    `log.warn("Spam-Guard: pubkey={} hat {} Events in 24h — drop", pubkey, recent)`

### I4 — Dockerfile.dockerignore simplification
- **Deleted** `hiuni-relay/Dockerfile.dockerignore`
- Updated header comment in `/.dockerignore` to reflect single-source-of-truth setup
  (top-level `.dockerignore` covers both classic-builder and BuildKit code paths)

### I5 — HMAC_SECRET / MASTER_KEY_B64 startup warnings
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Application.kt`
  - Reads both env vars at startup
  - `logger.warn("…required for Task 19 (LSF Validation)")` when blank
  - Values not used yet — purely a visibility log

### M1 — ReviewBadge KDoc
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewBadge.kt`
  - `👍 86%` → `86% bestellen wieder`
  - `P/L` → `Preis-Leistung`
  - `[ReviewBottomSheet]` → `[ReviewSubmitScreen]`
  - Removed "Caller verwaltet sheetOpen-State" line (caller now passes `onBewerten` lambda)

### M2 — Dead jetty logger
- `hiuni-relay/src/main/resources/logback.xml`
  - Removed `<logger name="org.eclipse.jetty" …/>` line (Netty is used)

### M3 — Hardcoded junit
- `hiuni-relay/build.gradle.kts`
  - `testImplementation("junit:junit:4.13.2")` → `testImplementation(libs.junit)`
  - Catalog alias already existed (`gradle/libs.versions.toml:148`)

## Build + test outcomes

```
./gradlew :hiuni-relay:test :hiuni-relay:assemble  → BUILD SUCCESSFUL (3s)
./gradlew :app:assembleDebug                       → BUILD SUCCESSFUL (1m 12s)
```

Test summary (`hiuni-relay/build/test-results/test/TEST-…SyncEndpointTest.xml`):
- `tests=2, skipped=0, failures=0, errors=0`
  - `hello with since=0 returns empty events` — pre-existing, still green
  - `SyncFrame_Event roundtrips through classDiscriminator-aware Json` — **new C1 regression**, green

`:app:assembleDebug` produced only the same pre-existing deprecation warnings
(AutoMirrored icons, LocalLifecycleOwner) — no new errors or warnings related
to these changes.

## Concerns / Follow-ups

1. **Task 19 still owes the canonical-hash relay-side eventId.** Until then, the
   client can spoof eventIds. The spam-guard (I3) caps the blast radius to 50/day
   per pubkey, but a client could still poison the dedupe set with arbitrary IDs.
2. **Composite cursor is opt-in.** The Android sync-client (not yet written for
   real WS flush) needs to start sending `sinceId` and persisting `cursorId` once
   Task 19/20 lands. Until then, ts-collisions can still drop or duplicate events
   at sub-millisecond granularity. Low risk for now (single test fixture, no live
   traffic), tracked.
3. **HMAC_SECRET / MASTER_KEY_B64 are warned but unused.** Operators who deploy
   the relay today will see WARN logs even though nothing consumes the values.
   This is intentional and tracked to Task 19 — once verification lands, the
   warning level should escalate to ERROR + refuse to start.
4. **`store.countSince` is called on every event insert.** SQLite + index on
   `(pubkey, ts)` keeps it cheap, but if event throughput grows this becomes
   the hot path; consider an in-memory bloom/LRU counter pre-DB.
