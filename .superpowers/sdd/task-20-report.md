# Task 20 Report: App-seitiger RelayClient

## Worktree

`/Users/kjell/AndroidStudioProjects/UniHi/.claude/worktrees/agent-addff3f6140a06547`
Branch: `worktree-agent-addff3f6140a06547`

## Files Created

- `app/src/main/java/de/transio/hiuni/feature/mensa/review/sync/RelayClient.kt`
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/sync/RelayConfig.kt`
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/sync/MasterPubkeyProvider.kt`
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/sync/ValidatorFactory.kt`

## Files Modified

- `app/src/main/java/de/transio/hiuni/feature/mensa/review/di/ReviewModule.kt`
  - Hilt-Bindings für `OkHttpClient` (mit `@RelayHttpClient`-Qualifier, `pingInterval = 30s`) und `RelayConfig`
  - Qualifier-Annotation `RelayHttpClient` definiert
- `app/build.gradle.kts`
  - `buildConfigField("String", "RELAY_BASE_URL", ...)` (Release-Default + Debug-Override `ws://10.0.2.2:8080`)

## Build Outcome

`./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL** in 26s. Nur die vorab bekannten
Material-Icons-Deprecation-Warnings, keine Errors.

## Self-Review

(a) **`RelayClient.start()` returns `Flow<RelayState>`** — ja, gibt `state.asStateFlow()`
zurück; State-Sealed-Class hat `Connecting | Synced | Disconnected`. `start()` ist
idempotent (mehrfacher Aufruf öffnet nur einmal). Auto-Reconnect via `scheduleReconnect`
(3s nach Close, 5s nach Failure) im `SupervisorJob`-Scope.

(b) **Outbox-Flush sendet `SyncFrame.Event(kind = ..., data = ...)`** — bestätigt, siehe
`flushOutbox`: extrahiert `type`-Feld aus dem persistierten JSON-Payload, sendet
`SyncFrame.Event(kind = kind, data = payload)` (das NEUE `kind`-Feld, nicht das alte
`type`). Erfolgreiches `send()` → `outbox.delete(eventId)`.

(c) **Ingest persistiert 4 Entity-Typen korrekt**:
   - `ReviewEvent` → `reviewDao.insert(ReviewEventEntity(...))` mit allen Sterne-Dimensionen.
   - `RetractionEvent` → `reviewDao.markRetracted(targetEventId)`.
   - `ValidationEvent` → `trustDao.insert(TrustEntity(pubkey, source = "relay", depth = 0, ...))`.
   - `IntroEvent` → `trustDao.find(inviter)?.depth → newDepth = depth + 1`, nur wenn `≤ 2`
     persistiert (Trust-Tiefe wie spezifiziert).

## Design Notes

- **OkHttp-Qualifier**: Eigener `@RelayHttpClient`-OkHttpClient mit `pingInterval(30s)`,
  damit der globale Client (für scraper) nicht beeinflusst wird und NAT-Mappings am
  Mobilfunk-Provider aktiv bleiben.
- **PeerCursor-Persistenz**: `PeerCursorDao` hat kein `getOrInit`/`upsert`; ich nutze
  `find()`-Check + `insert(REPLACE)`. Funktional identisch.
- **`runBlocking` in `ValidatorFactory`**: Bewusst zulässig, weil die TrustResolver-/
  EventStore-Adapter immer aus dem `Dispatchers.IO`-Scope des RelayClient aufgerufen
  werden — blockiert einen IO-Worker, niemals den Main-Thread. Doku-Kommentar im Code.
- **Frame-Handling**: `SyncFrame.Hello`/`SyncFrame.Ping` werden als Server-Inputs ignoriert
  (Client schickt sie); `SyncFrame.Pong` kein State-Change (Ping/Pong übernimmt OkHttp
  intern via `pingInterval`).

## Concerns

1. **Schema-Drift**: Beim Build wurde `app/schemas/.../34.json` regeneriert (KSP-Effekt),
   weil das eingecheckte Schema noch `submissionStatus`/`lastSubmittedEpoch`-Spalten für
   `learnweb_assignments` enthält, das Entity aber nicht mehr. Habe diese Änderung
   bewusst **nicht committed** (out of scope für Task 20). Sollte separat untersucht
   werden — vermutlich Phase-3-Whole-Branch-Fixup-Artifact.
2. **Validator pro Frame**: `validatorFactory.create()` wird pro Ingest-Element aufgerufen.
   Bei grossen Initial-Batches könnte das hot path sein — die Erzeugung selbst ist billig
   (nur Wrapper), aber `masterPubkeyProvider.get()` macht jedes Mal einen DataStore-Read.
   In Task 21 ggf. cachen.
3. **Outbox-Retry-Strategie**: Aktuell wird ein einmaliger Flush nach `Synced` versucht.
   Wenn `socket.send()` false zurückgibt (Buffer voll), bleibt das Item in der Outbox —
   beim nächsten Connect-Cycle wird's erneut probiert. Kein Exponential-Backoff pro Item.
4. **`start()` nicht automatisch aufgerufen**: Korrekt — Task 21 wired das ein (Lifecycle).

## Commit

`feat(reviews): RelayClient mit WebSocket-Sync + Outbox-Flush` auf Branch
`worktree-agent-addff3f6140a06547`.
