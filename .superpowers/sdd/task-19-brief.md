### Task 19: `/validate` Endpoint (LSF-Cookie → ValidationEvent)

**Files:**
- Create: `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/LsfBridge.kt`
- Create: `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/MasterKey.kt`
- Create: `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Hmac.kt`
- Modify: `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Routes.kt`
- Modify: `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Application.kt`

**Interfaces:**
- Produces:
  - `interface LsfBridge { suspend fun whoami(sessionCookie: String): LsfUser? }; data class LsfUser(matrikelnummer: String)`
  - `class MasterKey(b64: String) { val pubkeyB64: String; fun signValidation(pubkey: String, ts: Long): ValidationEvent }`
  - `fun hmacHex(secret: String, msg: String): String`
  - `POST /validate { lsfSessionCookie, pubkey } → ValidationEvent`

- [ ] **Step 1:** `MasterKey.kt`:

```kotlin
package de.transio.hiuni.relay

import de.transio.hiuni.events.*

class MasterKey(b64: String) {
    private val kp: Keypair = if (b64.isBlank()) Ed25519.generateKeypair()
                              else Keypair(b64.fromBase64(), b64.fromBase64())
    // Hinweis: Wenn nur ein b64-String übergeben wird, behandeln wir ihn als secret-key
    // und leiten pub draus ab; alternativ via separater Env-Var.

    val pubkeyB64: String = kp.publicKey.toBase64()

    fun signValidation(pubkey: String, ts: Long): ValidationEvent =
        ValidationEvent(pubkey_ = pubkey, ts = ts, issuer = "relay", sig = "").signWith(kp)
}
```

**Realistisch:** Wir bauen MasterKey so, dass beim ersten Start ohne `MASTER_KEY_B64` ein Keypair generiert wird und einmalig in einer Datei `/data/master.key` persistiert wird (damit der Pubkey über Restarts gleich bleibt). Spec sieht ENV-Var vor — wir kombinieren: ENV-Var hat Vorrang, sonst Datei-Persistenz.

```kotlin
class MasterKey(envB64: String?, persistPath: String = "/data/master.key") {
    private val kp: Keypair = run {
        if (!envB64.isNullOrBlank()) {
            // ENV liefert komplettes Keypair als (pub||sec)-Base64
            decodeFromEnv(envB64)
        } else {
            val f = java.io.File(persistPath)
            if (f.exists()) decodeFromFile(f) else { Ed25519.generateKeypair().also { writeKeypair(f, it) } }
        }
    }
    val pubkeyB64: String = kp.publicKey.toBase64()
    fun signValidation(pubkey: String, ts: Long) = ValidationEvent(pubkey, ts, "relay", "").signWith(kp)
    private fun decodeFromEnv(b64: String): Keypair { val all = b64.fromBase64(); val pub = all.copyOfRange(0, 32); val sec = all.copyOfRange(32, all.size); return Keypair(pub, sec) }
    private fun decodeFromFile(f: java.io.File): Keypair { val all = f.readBytes(); return Keypair(all.copyOfRange(0,32), all.copyOfRange(32, all.size)) }
    private fun writeKeypair(f: java.io.File, kp: Keypair) { f.parentFile.mkdirs(); f.writeBytes(kp.publicKey + kp.secretKey) }
}
```

(Tink-Keysets sind nicht 32 Bytes — passe die Längen entsprechend dem tatsächlichen Tink-Keyset-Format an. In der ersten Implementierung kannst du das ganze Tink-Keyset serialisieren ohne nach pub/sec aufzuspalten: `data class Keypair(val publicKey: ByteArray, val secretKey: ByteArray)` — secretKey ist eh ein voller Keyset-Blob, public-Pendant analog. Speichere beide getrennt mit Längen-Prefix.)

- [ ] **Step 2:** `Hmac.kt`:

```kotlin
package de.transio.hiuni.relay

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun hmacHex(secret: String, msg: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    return mac.doFinal(msg.toByteArray()).joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 3:** `LsfBridge.kt`:

```kotlin
package de.transio.hiuni.relay

interface LsfBridge {
    suspend fun whoami(sessionCookie: String): LsfUser?
}
data class LsfUser(val matrikelnummer: String)

/** Stub für Phase 3-Tests; in Phase 4-Production wird das durch echten LSF-Call ersetzt. */
class StubLsfBridge : LsfBridge {
    override suspend fun whoami(sessionCookie: String): LsfUser? =
        if (sessionCookie.startsWith("stub-")) LsfUser(matrikelnummer = sessionCookie.removePrefix("stub-"))
        else null
}
```

(Echte LSF-Implementierung — vermutlich HTTP-Call gegen euer LSF-Backend mit Cookie als Header — kommt in Phase 4-Production. Spec verweist auf einen einmaligen Session-Validity-Check; konkrete HTTP-Verb-Definition hängt von eurem LSF-Endpoint ab und wird zur Implementation-Zeit ergänzt.)

- [ ] **Step 4:** `Routes.kt` erweitern um `/validate`:

```kotlin
@kotlinx.serialization.Serializable
data class ValidateRequest(val lsfSessionCookie: String, val pubkey: String)

fun Routing.validate(masterKey: MasterKey, lsf: LsfBridge, hmacSecret: String) =
    post("/validate") {
        val req = call.receive<ValidateRequest>()
        val store = call.application.eventStore()
        val user = lsf.whoami(req.lsfSessionCookie)
            ?: return@post call.respond(HttpStatusCode.Unauthorized)

        val matHash = hmacHex(hmacSecret, user.matrikelnummer)
        val existing = store.findMatNr(matHash)
        if (existing != null && existing != req.pubkey) {
            store.deprecateOldPubkey(existing)
        }

        val ts = System.currentTimeMillis()
        val ev = masterKey.signValidation(req.pubkey, ts)
        val payload = json.encodeToString(ev)
        store.insert(ev.eventId(), "validation", req.pubkey, ts, payload)
        store.upsertMatNr(matHash, req.pubkey)
        store.registerPubkey(req.pubkey, ev.eventId())
        broadcastValidation(payload)

        call.respond(mapOf("validationEvent" to ev, "relayMasterPubkey" to masterKey.pubkeyB64))
    }

private suspend fun broadcastValidation(payloadJson: String) {
    val frame = SyncFrame.Event(type = "validation", data = json.parseToJsonElement(payloadJson))
    val text = json.encodeToString<SyncFrame>(frame)
    sessions.toList().forEach { runCatching { it.ws.send(Frame.Text(text)) } }
}
```

- [ ] **Step 5:** Application-Init mit MasterKey + LsfBridge:

```kotlin
fun main() {
    val dbPath = System.getenv("DB_PATH") ?: "relay.db"
    val masterKey = MasterKey(System.getenv("MASTER_KEY_B64"))
    val hmacSecret = System.getenv("HMAC_SECRET") ?: error("HMAC_SECRET required")
    val lsf: LsfBridge = StubLsfBridge() // TODO: HiUniLsfBridge in Phase 4-Production
    val store = EventStore(dbPath).also { it.init() }

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        installEventStore(store)
        install(ContentNegotiation) { json(json) }
        install(WebSockets)
        routing {
            health(); sync()
            validate(masterKey, lsf, hmacSecret)
        }
    }.start(wait = true)
}
```

- [ ] **Step 6:** Integration-Test:

```kotlin
@Test fun `validate happy path returns ValidationEvent`() = runBlocking {
    testApplication {
        val tmp = Files.createTempFile("relay", ".db").toFile().also { it.deleteOnExit() }
        val store = EventStore(tmp.absolutePath).also { it.init() }
        val mk = MasterKey(null, java.io.File.createTempFile("master", ".key").absolutePath)
        application {
            installEventStore(store)
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            install(io.ktor.server.websocket.WebSockets)
            routing { health(); sync(); validate(mk, StubLsfBridge(), "secret") }
        }
        val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(json) } }
        val resp = client.post("/validate") {
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(ValidateRequest("stub-12345", "pk-test"))
        }
        assertEquals(200, resp.status.value)
    }
}
```

- [ ] **Step 7:** Commit:

```bash
git add hiuni-relay/
git commit -m "feat(relay): /validate Endpoint + MasterKey + HMAC + LsfBridge-Stub"
```

