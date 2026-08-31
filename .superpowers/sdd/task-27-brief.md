### Task 27: Embedded Ktor-Server pro App

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/sync/LanSyncServer.kt`

**Interfaces:**
- Produces:
  - `class LanSyncServer(port: Int = 9234, peerId: String, eventStoreFacade, validatorFactory, scope) { fun start(); fun stop() }`

- [ ] **Step 1:** App-Modul braucht Ktor-Server-Dependencies (Android-kompatibel, kein Netty wegen Größe — `ktor-server-cio` ist leichter):

```kotlin
// In app/build.gradle.kts dependencies:
implementation("io.ktor:ktor-server-cio:3.0.2")
implementation("io.ktor:ktor-server-websockets:3.0.2")
implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2")
```

- [ ] **Step 2:** `LanSyncServer.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.sync

import de.transio.hiuni.events.*
import de.transio.hiuni.feature.mensa.review.data.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LanSyncServer(
    val port: Int = 9234,
    val peerId: String,
    private val reviewDao: ReviewDao,
    private val trustDao: TrustDao,
    private val validatorFactory: ValidatorFactory,
) {
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
    private var engine: ApplicationEngine? = null

    fun start() {
        engine = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) { json(json) }
            install(WebSockets)
            routing { webSocket("/sync") { handleSession() } }
        }.start(wait = false)
    }
    fun stop() { engine?.stop(0, 0); engine = null }

    private suspend fun DefaultWebSocketServerSession.handleSession() {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val parsed = json.decodeFromString<SyncFrame>(frame.readText())
            when (parsed) {
                is SyncFrame.Hello -> { /* TODO: queryAfter via shared local DAO query — siehe Task 28 */ }
                is SyncFrame.Event -> { /* identische ingest-Logik wie RelayClient */ }
                is SyncFrame.Ping  -> send(Frame.Text(json.encodeToString<SyncFrame>(SyncFrame.Pong(parsed.ts))))
                else -> {}
            }
        }
    }
}
```

- [ ] **Step 3:** Im `ProcessLifecycleOwner` (oder `MainActivity.onResume/onPause`) Start/Stop verdrahten:

```kotlin
// In MainActivity oder einem ApplicationLifecycleObserver
override fun onResume() { lanSyncServer.start(); nsdRegistration.register() }
override fun onPause() { lanSyncServer.stop(); nsdRegistration.unregister() }
```

- [ ] **Step 4:** Commit:

```bash
git add app/
git commit -m "feat(reviews): embedded Ktor-Server für LAN-Sync"
```

