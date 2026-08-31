# Task 14 Report — `:hiuni-relay` Modul + minimaler Ktor-Server

## Status
Done. Commit `7a7e1c8` on `feature/mensa-reviews`.

## Files Created
- `hiuni-relay/build.gradle.kts` — JVM 21, plugins: `kotlin-jvm`, `kotlin-serialization`, `application`. Main class `de.transio.hiuni.relay.ApplicationKt`. Dependencies: `:shared-events`, `google-tink` (JVM artifact), Ktor server (core/netty/websockets/content-negotiation/json), `sqlite-jdbc`, `logback-classic`. Test: junit + `ktor-server-test-host`.
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Application.kt` — `fun main()` startet `embeddedServer(Netty, port = 8080, host = "0.0.0.0")` mit `ContentNegotiation { json() }`, `WebSockets`-Install und Route `GET /health` → `{"status":"ok"}`.
- `hiuni-relay/src/main/resources/logback.xml` — Console-Appender, Root INFO, netty/jetty auf WARN gedämpft (optional, vermeidet `defaultConfigurator`-Fallback-Geschwätz).

## Files Modified
- `gradle/libs.versions.toml`
  - `[versions]` ergänzt: `ktor = "3.0.2"`, `sqliteJdbc = "3.46.1.3"`, `logback = "1.5.12"`.
  - `[libraries]` ergänzt: `ktor-server-core`, `ktor-server-netty`, `ktor-server-websockets`, `ktor-server-content-negotiation`, `ktor-serialization-kotlinx-json`, `ktor-server-test-host`, `sqlite-jdbc`, `logback-classic` — alle exakt nach Brief.
- `settings.gradle.kts` — `include(":app", ":shared-events", ":hiuni-relay")`.

## Gradle Build Outcome
- `./gradlew :hiuni-relay:compileKotlin --no-daemon` → BUILD SUCCESSFUL in 22s; sieht `:shared-events:jar` und kompiliert `Application.kt` ohne Warnungen.
- `./gradlew :hiuni-relay:run` startet Netty auf `0.0.0.0:8080`; Cold-Start ≈ paar Sekunden bis Port hört.

## Curl /health Output
```
$ curl -s http://localhost:8080/health
{"status":"ok"}
```
Exit 0, Content-Type via ContentNegotiation/json → korrekt serialisiert via Map<String,String>.

## Self-Review
**(a) Tink JVM-Artefakt, nicht tink-android:** `build.gradle.kts` referenziert `libs.google.tink` (= `com.google.crypto.tink:tink:1.15.0`, pure JVM). `libs.google.tink.android` wird im Relay-Modul nicht angefasst. Bestätigt durch erfolgreichen JVM-Compile ohne Android-Resource-Resolver.

**(b) `:shared-events` clean konsumiert:** `implementation(project(":shared-events"))` — `:shared-events` ist selbst ein pure-JVM-Modul mit `kotlin.jvm` + Tink JVM. Daher keine Classpath-Konflikte (kein doppeltes Tink, keine Android-only Klassen). Gradle resolved beide Module auf JDK 21 toolchain; `compileKotlin` würde bei AAR/Android-Artefakten brechen — tat es nicht.

**(c) Application.kt main fun korrekt:** Top-level `fun main()` in package `de.transio.hiuni.relay`, kompiliert zu Klasse `de.transio.hiuni.relay.ApplicationKt` (Kotlin-Konvention `<File>Kt`). `application { mainClass.set("de.transio.hiuni.relay.ApplicationKt") }` matched. `./gradlew :hiuni-relay:run` startet den Server und `GET /health` antwortet — End-to-End-Beweis dass der Entry Point gefunden wird.

## Concerns
- **Logback default-Konfiguration:** Ohne `logback.xml` druckt Ktor 3 eine "No SLF4J providers were found"-Variante als Default-Config-Hinweis. Habe `logback.xml` ergänzt — minimal, Console-only. Wenn das später unerwünscht ist (z.B. tests wollen leeres Log), lässt sich die Datei rauswerfen.
- **`junit:junit:4.13.2` hardcoded:** Brief schreibt es so vor, aber `libs.junit` existiert bereits in der version catalog. Bewusst nicht umgestellt, um Brief-konform zu bleiben — kann bei Task 17+ refactored werden, falls Tests dem Relay zugefügt werden.
- **Port 8080 hardcoded:** Brief-Vorgabe. Für Task 15+ (WebSocket-Peer) wird Port-Config wahrscheinlich aus ENV oder `application.conf` kommen müssen.
- **Keine `application.conf`:** Ktor läuft hier rein code-konfiguriert via `embeddedServer{}`. Wenn später Hocon-Style-Config gewünscht ist (Bot/Production-Setup), wird die `Application.kt` umgebaut auf `EngineMain.main(args)` + `module(Application.module)`. Aktuell aber Brief-konform "minimal".
- **App-Modul unberührt:** Während des Tasks hatte das Repo unrelated dirty Files in `app/` (Learnweb-Refactor). Habe sie nicht angefasst — Commit enthält ausschließlich `hiuni-relay/`, `settings.gradle.kts`, `gradle/libs.versions.toml`.

## Notes for Next Task (15+)
- `:shared-events` ist jetzt cross-konsumiert (`:app` + `:hiuni-relay`). Bei Schema-Änderungen dort beide Konsumenten anfassen.
- `kotlinxSerializationCore`-Alias wurde NICHT eingeführt — wir verwenden weiterhin `serialization = "1.7.3"` über `libs.kotlinx.serialization.json`. Ktor’s `kotlinx-json`-Plugin transitiv zieht kompatibles `kotlinx-serialization-json:1.7.x`, also kein Konflikt.
