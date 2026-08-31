### Task 14: `:hiuni-relay` Modul + minimaler Ktor-Server

**Files:**
- Create: `hiuni-relay/build.gradle.kts`
- Create: `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Application.kt`
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml` (Ktor-Versionen)

**Interfaces:**
- Produces: laufender Ktor-Server auf `localhost:8080` mit `GET /health`.

- [ ] **Step 1:** `libs.versions.toml` erweitern:

```toml
[versions]
ktor = "3.0.2"
sqliteJdbc = "3.46.1.3"
logback = "1.5.12"

[libraries]
ktor-server-core = { group = "io.ktor", name = "ktor-server-core-jvm", version.ref = "ktor" }
ktor-server-netty = { group = "io.ktor", name = "ktor-server-netty-jvm", version.ref = "ktor" }
ktor-server-websockets = { group = "io.ktor", name = "ktor-server-websockets-jvm", version.ref = "ktor" }
ktor-server-content-negotiation = { group = "io.ktor", name = "ktor-server-content-negotiation-jvm", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json-jvm", version.ref = "ktor" }
ktor-server-test-host = { group = "io.ktor", name = "ktor-server-test-host-jvm", version.ref = "ktor" }
sqlite-jdbc = { group = "org.xerial", name = "sqlite-jdbc", version.ref = "sqliteJdbc" }
logback-classic = { group = "ch.qos.logback", name = "logback-classic", version.ref = "logback" }
```

- [ ] **Step 2:** `settings.gradle.kts` erweitern:

```kotlin
include(":app", ":shared-events", ":hiuni-relay")
```

- [ ] **Step 3:** `hiuni-relay/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application { mainClass.set("de.transio.hiuni.relay.ApplicationKt") }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

dependencies {
    implementation(project(":shared-events"))
    implementation(libs.google.tink)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.sqlite.jdbc)
    implementation(libs.logback.classic)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.ktor.server.test.host)
}
```

- [ ] **Step 4:** Minimal-`Application.kt`:

```kotlin
package de.transio.hiuni.relay

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(ContentNegotiation) { json() }
        install(WebSockets)
        routing {
            get("/health") { call.respond(mapOf("status" to "ok")) }
        }
    }.start(wait = true)
}
```

- [ ] **Step 5:** Server starten + `/health` testen:

```bash
./gradlew :hiuni-relay:run &
sleep 5
curl http://localhost:8080/health
# Expected: {"status":"ok"}
kill %1
```

- [ ] **Step 6:** Commit:

```bash
git add hiuni-relay/ settings.gradle.kts gradle/libs.versions.toml
git commit -m "feat(relay): minimal Ktor server mit /health"
```

