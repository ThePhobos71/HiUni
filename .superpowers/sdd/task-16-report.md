# Task 16 — `/sync` WebSocket-Endpoint mit Hello/Event/Events-Protokoll

## Status

GREEN. Beide Tests im `:hiuni-relay`-Modul laufen durch (`EventStoreTest`,
`SyncEndpointTest`). Build steht.

## Geänderte / neu erstellte Dateien

| Datei | Art | Zweck |
| --- | --- | --- |
| `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/SyncFrame.kt` | neu | `@Serializable sealed class SyncFrame` mit Hello/Event/Events/Ping/Pong, polymorphes JSON-Format |
| `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Routes.kt` | neu | `Routing.health()`, `Routing.sync()`, `Application.installEventStore`, `Application.eventStore()` + Session-Tracking + Broadcast |
| `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Application.kt` | modifiziert | `EventStore` gebootstrapped, beide Routen registriert, `WebSockets` + `ContentNegotiation(json)` installiert |
| `hiuni-relay/src/test/kotlin/de/transio/hiuni/relay/SyncEndpointTest.kt` | modifiziert (Imports geschärft) | Ktor-Test-Host öffnet WS, sendet `Hello(since=0)`, erwartet `Events`-Frame mit 0 Items |

Hinweis: Die Brief-Vorlage hatte für die Sealed-Class den Dateinamen
`SyncProtocol.kt` vorgesehen. Der Plan-Constraint vorgibt jedoch
`SyncFrame.kt`, was wir umgesetzt haben.

## TDD-Evidenz

**RED** — vor Implementation lief `./gradlew :hiuni-relay:test`:

```
e: SyncEndpointTest.kt:24:17 Unresolved reference 'installEventStore'.
e: SyncEndpointTest.kt:25:17 'install' cannot be called in this context with an implicit receiver.
e: SyncEndpointTest.kt:26:56 Unresolved reference 'json'.
e: SyncEndpointTest.kt:29:27 Unresolved reference 'sync'.
e: SyncEndpointTest.kt:29:35 Unresolved reference 'health'.
e: SyncEndpointTest.kt:37:53 Unresolved reference 'SyncFrame'.
… (insgesamt 15 Compiler-Errors)
BUILD FAILED
```

**GREEN** — nach Implementation:

```
> Task :hiuni-relay:compileKotlin
> Task :hiuni-relay:compileTestKotlin
> Task :hiuni-relay:test

BUILD SUCCESSFUL
```

JUnit-Reports (`build/test-results/test/TEST-*.xml`):

| Suite | Tests | Failures | Errors | Time |
| --- | --- | --- | --- | --- |
| `EventStoreTest` | 1 | 0 | 0 | 0.645 s |
| `SyncEndpointTest` | 1 | 0 | 0 | 0.222 s |

## Selbst-Review der Protokoll-Invarianten

1. **Hello mit `since=0` liefert leeres `Events`-Frame korrekt zurück** — `is SyncFrame.Hello`-Branch ruft `store.queryAfter(parsed.since, 100)`, mappt Payloads zu `JsonElement` und sendet `SyncFrame.Events(items, hasMore, cursor)`. Der Integration-Test verifiziert genau das: `assertTrue(frame is SyncFrame.Events)` und `assertEquals(0, items.size)`. Auf leerer DB also genau erwartetes Verhalten.
2. **Event-Broadcast echoed nicht zum Sender zurück** — `broadcastExcept(except, ev)` filtert per Identitäts-Vergleich `it !== except` über `sessions.toList()` und schickt nur an die übrigen Sessions. Da `Session` per Konstruktor an genau eine `WebSocketServerSession` gebunden ist, ist die Identität stabil über die Lebenszeit der Verbindung. Kein Echo möglich.
3. **Ping liefert Pong mit identischem `ts`** — `is SyncFrame.Ping -> send(Frame.Text(json.encodeToString<SyncFrame>(SyncFrame.Pong(parsed.ts))))` — die `ts`-Long wird wortwörtlich übernommen, kein Resampling.

## Implementierungs-Details / Abweichungen vom Brief

- **Test-Imports geschärft.** Der Brief verwendete in der `application { … }`-Lambda vollqualifizierte Plugin-Namen ohne expliziten `install`-Import. Das compilierte unter Kotlin 2.0 in der aktuellen Konfiguration nicht (`'install' cannot be called in this context with an implicit receiver`), daher: `import io.ktor.server.application.install` + Klartext-Imports mit `as`-Aliassen, da sowohl Server-`WebSockets` als auch Client-`WebSockets` im selben File benötigt werden.
- **Sessions-Tracking** in `Collections.synchronizedSet(mutableSetOf<Session>())` wie spezifiziert; `broadcastExcept` snapshottet via `toList()`, vermeidet `ConcurrentModificationException` beim Iterieren.
- `installEventStore`/`eventStore()` Extensions hängen am `Application`-Scope per `AttributeKey<EventStore>`, sodass die Routen jederzeit Zugriff ohne globalen State haben.

## Concerns / Carry-Over für Phase 7 Wire-Refactor

- **`eventId`-Extraktion aus dem Client-`sig` ist eine pragmatische V1-Lösung, kein Crypto-Vertrag.** Der Brief versucht zuerst `obj["eventId"]`, fällt sonst auf `obj["sig"]` zurück. Das ist faktisch: *„Vertrau dem Client, dass seine Signatur eindeutig genug ist, um sie als Primary-Key in der Events-Tabelle zu nutzen.“* Risiken:
    - Zwei Clients könnten denselben `sig`-String submitten (vorausgesetzt Schlüssel-Reuse oder PRNG-Kollision in Ed25519-Nonce) und der Relay würde den zweiten Insert dedupen, statt die Anomalie zu erkennen.
    - Der Relay verifiziert hier **nicht** die Signatur — Task 17 wird das nachholen (Ed25519-Verify im Validator). Bis dahin akzeptiert `/sync` Events *vertrauensvoll*.
    - Korrekt wäre: der Relay berechnet selbst `SHA-256(canonical(event))` als `eventId`. Das stellt sicher: (a) `eventId` ist eindeutig vom Inhalt abgeleitet, (b) der Client kann den Key nicht fälschen.
- **Empfehlung Phase 7**: `eventId = sha256(canonicalJson(data))` als deterministische Ableitung; `sig` bleibt separates Feld nur für Signaturverifikation. Außerdem sollte der Sealed-Type ins `:shared-events`-Modul wandern (Plan-Task 20), damit App und Relay denselben Wire-Type ohne Wire-Drift teilen.
- **Health-Endpoint zählt alle Events durch `queryAfter(0, Int.MAX_VALUE)`.** Für eine kleine Relay-Instanz unkritisch; bei Wachstum durch dedizierten `SELECT COUNT(*)` ersetzen.

## Commit

```
feat(relay): WebSocket /sync mit Hello/Event/Events-Protokoll
```
