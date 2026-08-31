### Task 16: `/sync` WebSocket-Endpoint mit Sync-Protokoll

**Files:**
- Create: `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/SyncProtocol.kt`
- Create: `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Routes.kt`
- Modify: `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Application.kt`
- Create: `hiuni-relay/src/test/kotlin/de/transio/hiuni/relay/SyncEndpointTest.kt`

**Interfaces:**
- Produces:
  - Wire-Frames als `@Serializable sealed class SyncFrame { @SerialName("hello") Hello(since: Long); @SerialName("event") Event(...); @SerialName("events") Events(items, hasMore, cursor); @SerialName("ping") Ping(ts); @SerialName("pong") Pong(ts) }`
  - `webSocket("/sync") { ... }`-Route, akzeptiert Frames.

- [ ] **Step 1:** `SyncProtocol.kt` mit polymorphem Sealed-Class-Wire-Format:

```kotlin
package de.transio.hiuni.relay

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable sealed class SyncFrame {
    @Serializable @SerialName("hello") data class Hello(val v: Int = 1, val since: Long) : SyncFrame()
    @Serializable @SerialName("event") data class Event(val type: String, val data: JsonElement) : SyncFrame()
    @Serializable @SerialName("events") data class Events(val items: List<JsonElement>, val hasMore: Boolean, val cursor: Long) : SyncFrame()
    @Serializable @SerialName("ping") data class Ping(val ts: Long) : SyncFrame()
    @Serializable @SerialName("pong") data class Pong(val ts: Long) : SyncFrame()
}
```

Hinweis: Wir kapseln den signierten Event-Payload als `JsonElement` damit das Wire-Format Erweiterbarkeit (neue Event-Typen) erlaubt ohne Wire-Class-Renames.

- [ ] **Step 2:** `Routes.kt`:

```kotlin
package de.transio.hiuni.relay

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Collections

internal data class Session(val ws: WebSocketServerSession)
private val sessions: MutableSet<Session> = Collections.synchronizedSet(mutableSetOf())
internal val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

fun Routing.health() = get("/health") {
    call.respond(mapOf(
        "events" to call.application.eventStore().queryAfter(0L, Int.MAX_VALUE).items.size,
        "connections" to sessions.size,
    ))
}

fun Routing.sync() = webSocket("/sync") {
    val session = Session(this)
    sessions.add(session)
    val store = call.application.eventStore()
    try {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val parsed = json.decodeFromString<SyncFrame>(frame.readText())
            when (parsed) {
                is SyncFrame.Hello -> {
                    val batch = store.queryAfter(parsed.since, 100)
                    val items = batch.items.map { json.parseToJsonElement(it) }
                    send(Frame.Text(json.encodeToString<SyncFrame>(
                        SyncFrame.Events(items, batch.hasMore, batch.cursor))))
                }
                is SyncFrame.Event -> {
                    val obj = parsed.data.jsonObject
                    val type = parsed.type
                    val eventId = obj["eventId"]?.jsonPrimitive?.content
                        ?: obj["sig"]?.jsonPrimitive?.content ?: continue
                    val pubkey = obj["pubkey"]?.jsonPrimitive?.content ?: continue
                    val ts = obj["ts"]?.jsonPrimitive?.content?.toLongOrNull() ?: continue
                    if (!store.exists(eventId)) {
                        store.insert(eventId, type, pubkey, ts, parsed.data.toString())
                        broadcastExcept(session, parsed)
                    }
                }
                is SyncFrame.Ping -> send(Frame.Text(json.encodeToString<SyncFrame>(SyncFrame.Pong(parsed.ts))))
                else -> {}
            }
        }
    } finally { sessions.remove(session) }
}

private suspend fun broadcastExcept(except: Session, ev: SyncFrame.Event) {
    val text = json.encodeToString<SyncFrame>(ev)
    sessions.toList().filter { it !== except }.forEach {
        runCatching { it.ws.send(Frame.Text(text)) }
    }
}

// Application-scoped store access:
private val EVENT_STORE_KEY = io.ktor.util.AttributeKey<EventStore>("eventStore")
fun Application.installEventStore(store: EventStore) { attributes.put(EVENT_STORE_KEY, store) }
fun Application.eventStore(): EventStore = attributes[EVENT_STORE_KEY]
```

- [ ] **Step 3:** `Application.kt` updaten:

```kotlin
fun main() {
    val dbPath = System.getenv("DB_PATH") ?: "relay.db"
    val store = EventStore(dbPath).also { it.init() }

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        installEventStore(store)
        install(ContentNegotiation) { json(json) }
        install(WebSockets)
        routing {
            health()
            sync()
        }
    }.start(wait = true)
}
```

- [ ] **Step 4:** Integration-Test mit `ktor-server-test-host`:

```kotlin
package de.transio.hiuni.relay

import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SyncEndpointTest {
    @Test fun `hello with since=0 returns empty events`() = runBlocking {
        testApplication {
            val tmp = Files.createTempFile("relay", ".db").toFile().also { it.deleteOnExit() }
            val store = EventStore(tmp.absolutePath).also { it.init() }
            application {
                installEventStore(store)
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
                install(io.ktor.server.websocket.WebSockets)
                routing { sync(); health() }
            }
            val client = createClient { install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(json) } }
            client.webSocket("/sync") {
                send(Frame.Text(json.encodeToString<SyncFrame>(SyncFrame.Hello(since = 0L))))
                val raw = (incoming.receive() as Frame.Text).readText()
                val frame = json.decodeFromString<SyncFrame>(raw)
                assertTrue(frame is SyncFrame.Events)
                assertEquals(0, (frame as SyncFrame.Events).items.size)
            }
        }
    }
}
```

- [ ] **Step 5:** Tests PASS.

- [ ] **Step 6:** Commit:

```bash
git add hiuni-relay/
git commit -m "feat(relay): WebSocket /sync mit Hello/Event/Events-Protokoll"
```

