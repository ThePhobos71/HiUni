### Task 20: App-seitiger RelayClient (WebSocket + Outbox-Flush)

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/sync/RelayClient.kt`
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/sync/SyncFrame.kt` (Spiegel von Relay-Schema)
- Create: `app/src/test/java/de/transio/hiuni/feature/mensa/review/sync/RelayClientTest.kt`
- Modify: `app/src/main/java/de/transio/hiuni/feature/mensa/review/di/ReviewModule.kt`

**Interfaces:**
- Produces:
  - `class RelayClient(httpClient: OkHttpClient, relayUrl: String, reviewDao, trustDao, outboxDao, peerCursorDao, eventValidator)`
  - `fun start(): Flow<RelayState>`, `suspend fun stop()`
  - `sealed class RelayState { Connecting; Synced; Disconnected }`

- [ ] **Step 1:** `SyncFrame.kt` als 1:1 Spiegel der Relay-Frames (kann auch in `:shared-events` extrahiert werden — empfohlen ist letzteres, denn Wire-Format teilt sich App+Relay).

Verschiebe `SyncFrame.kt` aus `:hiuni-relay` nach `:shared-events`, ändere Relay-Import. Saubere Trennung.

```bash
git mv hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/SyncProtocol.kt \
       shared-events/src/main/kotlin/de/transio/hiuni/events/SyncFrame.kt
# Package umändern: de.transio.hiuni.relay → de.transio.hiuni.events
```

Imports im Relay anpassen.

- [ ] **Step 2:** `RelayClient.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.sync

import de.transio.hiuni.events.*
import de.transio.hiuni.feature.mensa.review.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class RelayState { object Connecting:RelayState(); object Synced:RelayState(); object Disconnected:RelayState() }

class RelayClient @Inject constructor(
    private val http: OkHttpClient,
    private val cfg: RelayConfig,
    private val reviewDao: ReviewDao,
    private val trustDao: TrustDao,
    private val outbox: OutboxDao,
    private val cursors: PeerCursorDao,
    private val validatorFactory: ValidatorFactory,
) {
    private val state = MutableStateFlow<RelayState>(RelayState.Disconnected)
    private var ws: WebSocket? = null
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(): Flow<RelayState> {
        connect()
        return state.asStateFlow()
    }

    fun stop() {
        ws?.close(1000, "user")
        ws = null
        state.value = RelayState.Disconnected
    }

    private fun connect() {
        state.value = RelayState.Connecting
        val req = Request.Builder().url("${cfg.baseUrl}/sync").build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(socket: WebSocket, resp: Response) {
                scope.launch { sendHello(socket) }
            }
            override fun onMessage(socket: WebSocket, text: String) {
                scope.launch { handleFrame(socket, text) }
            }
            override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                state.value = RelayState.Disconnected
                scope.launch { delay(3_000); connect() } // Auto-reconnect
            }
            override fun onFailure(socket: WebSocket, t: Throwable, response: Response?) {
                state.value = RelayState.Disconnected
                scope.launch { delay(5_000); connect() }
            }
        })
    }

    private suspend fun sendHello(socket: WebSocket) {
        val cursor = cursors.getOrInit("relay")
        socket.send(json.encodeToString<SyncFrame>(SyncFrame.Hello(since = cursor)))
    }

    private suspend fun handleFrame(socket: WebSocket, text: String) {
        val f = json.decodeFromString<SyncFrame>(text)
        when (f) {
            is SyncFrame.Events -> {
                f.items.forEach { ingest(it) }
                if (f.hasMore) {
                    cursors.upsert("relay", f.cursor)
                    socket.send(json.encodeToString<SyncFrame>(SyncFrame.Hello(since = f.cursor)))
                } else {
                    cursors.upsert("relay", f.cursor)
                    state.value = RelayState.Synced
                    flushOutbox(socket)
                }
            }
            is SyncFrame.Event -> ingest(f.data)
            is SyncFrame.Pong -> {}
            else -> {}
        }
    }

    private suspend fun ingest(element: kotlinx.serialization.json.JsonElement) {
        val obj = element.jsonObject
        val type = obj["type"]?.jsonPrimitive?.content ?: return
        val event: SignedEvent? = when (type) {
            "review"      -> json.decodeFromJsonElement<ReviewEvent>(element)
            "validation"  -> json.decodeFromJsonElement<ValidationEvent>(element)
            "intro"       -> json.decodeFromJsonElement<IntroEvent>(element)
            "retraction"  -> json.decodeFromJsonElement<RetractionEvent>(element)
            else -> null
        } ?: return
        val v = validatorFactory.create()
        when (val r = v.accept(event)) {
            is AcceptResult.Ok -> persist(event)
            is AcceptResult.Reject -> { /* drop silently */ }
        }
    }

    private suspend fun persist(e: SignedEvent) {
        when (e) {
            is ReviewEvent -> reviewDao.insert(ReviewEventEntity(e.eventId(), e.recipeHash, e.pubkey,
                e.schemaVersion, e.overall, e.wouldOrderAgain, e.taste, e.portion, e.value, e.satiation,
                e.ts, e.sig, false))
            is RetractionEvent -> reviewDao.markRetracted(e.targetEventId)
            is ValidationEvent -> trustDao.insert(TrustEntity(e.pubkey, "relay", 0, e.ts, e.sig))
            is IntroEvent -> {
                val parentDepth = trustDao.find(e.inviter)?.depth ?: return
                val newDepth = parentDepth + 1
                if (newDepth <= 2) trustDao.insert(TrustEntity(e.invitee, e.inviter, newDepth, e.ts, e.sig))
            }
        }
    }

    private suspend fun flushOutbox(socket: WebSocket) {
        outbox.getAll().forEach { entry ->
            val element = json.parseToJsonElement(entry.payload)
            val type = element.jsonObject["type"]?.jsonPrimitive?.content ?: return@forEach
            socket.send(json.encodeToString<SyncFrame>(SyncFrame.Event(type, element)))
            outbox.delete(entry.eventId)
        }
    }
}

data class RelayConfig(val baseUrl: String)
class ValidatorFactory @Inject constructor(
    private val trustDao: TrustDao,
    private val reviewDao: ReviewDao,
    private val masterPubkey: MasterPubkeyProvider,
) {
    fun create(): EventValidator = EventValidator(
        trust = object : TrustResolver { override fun depthOf(p: String) = runBlocking { trustDao.find(p)?.depth } },
        store = object : EventStore {
            override fun exists(id: String) = runBlocking { reviewDao.exists(id) }
            override fun countSince(p: String, s: Long) = runBlocking { reviewDao.countSince(p, s) }
        },
        masterPubkey = masterPubkey.get(),
    )
}
class MasterPubkeyProvider @Inject constructor(private val store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) {
    suspend fun get(): String = store.data.first()[androidx.datastore.preferences.core.stringPreferencesKey("relay_master_pubkey")] ?: ""
    suspend fun set(b64: String) = store.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("relay_master_pubkey")] = b64 }
}
```

- [ ] **Step 3:** Hilt-Bindings im `ReviewModule`:

```kotlin
@Provides @Singleton fun okHttp(): OkHttpClient = OkHttpClient.Builder()
    .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
    .build()
@Provides @Singleton fun relayConfig(): RelayConfig = RelayConfig(
    baseUrl = de.transio.hiuni.BuildConfig.RELAY_BASE_URL  // in build.gradle als String setzen
)
```

`app/build.gradle.kts` — `buildConfigField("String", "RELAY_BASE_URL", "\"wss://relay.hiuni.example\"")` (in debug auf `ws://10.0.2.2:8080` setzen).

- [ ] **Step 4:** Lokaler End-to-End-Test: Relay starten, App auf Emulator, eine Review abgeben, in Relay-SQLite-DB nachsehen (`sqlite3 /data/relay.db 'SELECT * FROM events'`).

- [ ] **Step 5:** Commit:

```bash
git add app/ shared-events/
git commit -m "feat(reviews): RelayClient mit WebSocket-Sync + Outbox-Flush"
```

