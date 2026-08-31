# Task 20 Fix Report (Opus Review)

## Scope
Three findings from the Task 20 Opus review applied in a single batched commit:
- **C1 (Critical):** `SignedEvent.type` als reales serialisierbares Feld
- **I2 (Important):** RelayClient nutzt jetzt das (ts, eventId) Composite-Cursor aus den Server-Frames
- **M1 (Minor):** `sendHello` macht nur noch einen DB-Read statt zwei

## Files Modified

### C1 — SignedEvent.type wird serialisiert
- `shared-events/src/main/kotlin/de/transio/hiuni/events/Events.kt`
  - `type` ist jetzt in allen 4 SignedEvent-Klassen ein reales Property
    (`@EncodeDefault val type: String = "<value>"`) als ERSTES Property
    der Daten-Klasse — spiegelt die `canonical()`-Reihenfolge.
  - `@EncodeDefault` (ExperimentalSerializationApi) ist nötig, weil kotlinx-
    serialization Defaults sonst weglässt — sonst wäre `type` weiterhin
    nicht im JSON.
  - Alter TODO-Kommentar entfernt.
- `shared-events/src/test/kotlin/de/transio/hiuni/events/SerializationTest.kt` (neu)
  - 3 Tests: type-Feld im JSON, Round-Trip mit Sig-Verify, alle 4
    Event-Typen serialisieren ihren Diskriminator.

### Updated positional callers (forced by constructor reorder)
- `shared-events/src/test/kotlin/de/transio/hiuni/events/CanonicalTest.kt` (named args)
- `shared-events/src/test/kotlin/de/transio/hiuni/events/EventSignerTest.kt` (named args)
- `shared-events/src/test/kotlin/de/transio/hiuni/events/EventValidatorTest.kt`
  (Helper `review()` extrahiert + alle Aufrufe darauf umgestellt)
- `hiuni-relay/src/test/kotlin/de/transio/hiuni/relay/EventStoreTest.kt` (named args)

### I2 + M1 — RelayClient
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/sync/RelayClient.kt`
  - Neuer `@Volatile lastCursorId: String?` Feld (in-memory, TODO für Persistenz dokumentiert).
  - `sendHello`: ein einziger `cursors.find(peerId)`-Call (M1) + `sinceId = lastCursorId` (I2).
  - `handleFrame` aktualisiert `lastCursorId` aus `SyncFrame.Events.cursorId` und
    gibt `sinceId` beim Hello-Follow-up mit, wenn `hasMore`.

## Test Outcomes

```
:shared-events:test       BUILD SUCCESSFUL
  - CanonicalTest:        2/2 PASS  (canonical-form unverändert — Regression-Guard)
  - EventSignerTest:      2/2 PASS  (sig roundtrip)
  - EventValidatorTest:   8/8 PASS
  - Ed25519Test:          2/2 PASS
  - RecipeHashTest:       9/9 PASS
  - SerializationTest:    3/3 PASS  (neu — bestätigt C1-Fix)
:hiuni-relay:test         BUILD SUCCESSFUL
  - EventStoreTest:       1/1 PASS
  - SyncEndpointTest:     2/2 PASS  (relay-side untouched, weiter grün)
  - ValidateEndpointTest: 6/6 PASS
:app:assembleDebug        BUILD SUCCESSFUL
:app:testDebugUnitTest (review.*) BUILD SUCCESSFUL  (ReviewRepositoryTest weiter grün)
```

### Spezifisch bestätigt:
- **CanonicalTest grün** → canonical()-Form ist byte-identisch — bestehende
  Signaturen über Restart bleiben verifizierbar (kein Sig-Bruch).
- **EventSignerTest grün** → `signWith(kp)` → `verify()` Round-Trip
  funktioniert weiter.
- **SerializationTest neu & grün** → JSON enthält jetzt das `"type"`-Feld;
  Outbox/Polymorpher-Ingest in RelayClient.ingest funktionieren ab jetzt.

## Concerns

1. **`@EncodeDefault` ist `ExperimentalSerializationApi`.** Pragmatisch in
   kotlinx-serialization seit 1.4+, aber technisch instabil. Falls die API
   in einer zukünftigen Major-Version umbenannt wird, müssen wir hier
   anpassen. Alternativ ginge ein Json-Config-Flag (`encodeDefaults = true`)
   in jedem Json-Builder, aber das würde globalen State ändern und ist
   spröder.
2. **lastCursorId in-memory only.** Bei App-Restart geht der `sinceId`-Tail
   verloren — wir fallen zurück auf reines `since` (ts). Das ist akzeptabel
   für V1, weil ts-Kollisionen genau am Restart-Boundary extrem
   unwahrscheinlich sind. Für eine vollständige Persistenz müsste
   `PeerCursorEntity` um `lastSeenId: String?` erweitert werden (DB-Migration
   v34→v35). TODO-Kommentar im Code markiert.
3. **`ValidationEvent.pubkey_` mit `@SerialName("pubkey")` weiterhin hinter
   `type`.** Wire-Format-Reihenfolge ist `{"type":"validation","pubkey":...}`
   — passt zur canonical()-Form `type|pubkey_|ts|issuer`.
