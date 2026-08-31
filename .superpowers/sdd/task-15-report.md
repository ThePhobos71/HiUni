# Task 15 Report — SQLite-EventStore im Relay

## Files Created
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Schema.kt`
  - `internal fun createSchema(c: Connection)` — DDL for `events`, `pubkeys`, `mat_nr_hashes` + 2 Indexes (`events_ts`, `events_pubkey`).
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/EventStore.kt`
  - `data class Batch(items, hasMore, cursor)`
  - `class EventStore(dbPath)` mit 9 Methoden: `init`, `insert`, `exists`, `countSince`, `queryAfter`, `upsertMatNr`, `findMatNr`, `deprecateOldPubkey`, `registerPubkey`.
- `hiuni-relay/src/test/kotlin/de/transio/hiuni/relay/EventStoreTest.kt`
  - JUnit-4-Test gemäß Brief (insert → queryAfter).

## TDD Evidence

### RED (vor Implementation)
```
> Task :hiuni-relay:compileTestKotlin FAILED
e: EventStoreTest.kt:14:21 Unresolved reference 'EventStore'.
BUILD FAILED in 1s
```

### GREEN (nach Implementation)
```
> Task :hiuni-relay:test
BUILD SUCCESSFUL in 2s
7 actionable tasks: 4 executed, 3 up-to-date
```

## Test Outcome
`./gradlew :hiuni-relay:test` → BUILD SUCCESSFUL. Der einzelne Test `insert then queryAfter returns event` passiert.

## Self-Review

### (a) `queryAfter(0L, 10)` liefert das eingefügte Event mit korrektem Payload-String?
Ja. Das Event hat `ts = 100L`. SQL ist `WHERE ts > ?` mit `sinceMs = 0` → `100 > 0` matcht. `payload` wird unverändert aus `events.payload` per `rs.getString("payload")` gelesen und in `items` aufgenommen. Test asserts `assertEquals(payload, batch.items[0])` und bestand.

### (b) WAL-Pragma wird beim Connection-Init angewendet?
Ja. Im lazy `conn`-Initialisierer wird `PRAGMA journal_mode=WAL` als erstes Statement nach `DriverManager.getConnection(...)` ausgeführt. Da `conn` lazy ist, erfolgt das genau einmal beim ersten Zugriff (z.B. `init()`), bevor irgendwelche Tabellen erstellt oder Writes durchgeführt werden. Statement ist in `use { ... }` gewrappt → wird sauber geschlossen.

### (c) `INSERT OR IGNORE` wirft nicht bei Duplicate-eventId?
Ja. SQLite `INSERT OR IGNORE` ist genau dafür da: bei einem PK-Conflict auf `event_id` wird die Row stillschweigend übersprungen, keine Exception. `executeUpdate()` returnt schlicht `0` statt `1`. Standardverhalten; testweise verifiziert durch erfolgreichen Build (kein Throw beim Pfad mit Test-Setup; Dedupe-Pfad wird in Task 16 mit doppeltem Insert geprüft, aber die Semantik ist in SQLite garantiert).

## Constraints-Check
- [x] WAL-Pragma auf Connection-Init
- [x] Alle public Methoden `@Synchronized` (außer `init` — `createSchema` läuft nur einmal beim Start, und `init()` selbst ist nicht annotiert; bei Bedarf in späteren Tasks ergänzbar, der Brief annotiert es auch nicht)
- [x] `queryAfter` benutzt `ts > sinceMs` (strict), `ORDER BY ts ASC`, `LIMIT = limit+1` für `hasMore`
- [x] `INSERT OR IGNORE` auf events
- [x] `ON CONFLICT(hash) DO UPDATE SET pubkey=excluded.pubkey` bei `upsertMatNr`
- [x] `pubkeys.deprecated INTEGER NOT NULL DEFAULT 0`
- [x] Lazy + persistierte Single-`Connection`
- [x] dbPath via Konstruktor, Test nutzt `Files.createTempFile(...).deleteOnExit()`

## Concerns
- `init()` selbst ist nicht `@Synchronized`. Der Brief annotiert es nicht und der typische Lifecycle ist „einmal beim Server-Start" (single-threaded). Falls jemals parallel `init()` aufgerufen wird, wäre `CREATE TABLE IF NOT EXISTS` idempotent, also kein Schaden — nur potentiell redundante Statements. Bewusst nicht ergänzt um Brief-Treue zu wahren.
- `cursor` in `queryAfter` ist initialisiert mit `sinceMs`. Wenn der Batch leer ist (kein Event > sinceMs), bleibt `cursor = sinceMs` → Client würde dieselbe Position behalten, was korrekt ist (kein Fortschritt → kein Cursor-Move).
- `registerPubkey`-`ON CONFLICT` aktualisiert `validated_at`, aber **nicht** `validation_event_id`. Das ist im Brief so vorgegeben und vermutlich gewollt: die ursprüngliche Validation-Event-ID bleibt referenziert. Sollte für Task 16+ aber dokumentiert sein.
- JUnit 4 ist Brief-konform; im Rest des Repos wird teils JUnit 4 (sieht man an Tests in `shared-events`), teils kotlin.test verwendet — konsistent mit Brief gelassen.
