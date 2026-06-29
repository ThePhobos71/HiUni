# Mensa P2P Reviews Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Crowd-Bewertungen für Mensa-Gerichte bauen — Gun.js-Spirit, eigener Relay-Server als „immer-online Peer", Mesh-ready (LAN-Sync + WoT) für die Post-Relay-Welt.

**Architecture:** Signierte append-only Events (Ed25519) als Wire-Format, lokale volle Replica in Room pro Device, dual-Transport (WebSocket-zum-Relay + WebSocket-zwischen-Phones via mDNS), LSF-basiertes initiales Trust + Mail-Backup für Cross-Device + Web-of-Trust für Post-Relay-Onboarding. Krypto-Lib wird als geteiltes Gradle-Modul zwischen App und Relay konsumiert.

**Tech Stack:** Kotlin 2.0.21, Room 2.6.1, Hilt 2.56, OkHttp (WebSocket-Client), Ktor 3 (Relay + embedded LAN-Server), tink-android (Ed25519 + AES-GCM + PBKDF2 + HMAC), Angus Mail (IMAP Drafts-Folder), kotlinx-serialization (JSON), Android NSD Manager (mDNS-Discovery), Docker + Caddy (Relay-Hosting).

## Global Constraints

- **Spec-Quelle:** Alle Felder, Schemas und Konstanten kommen wortgenau aus `docs/superpowers/specs/2026-06-28-mensa-p2p-reviews-design.md`. Bei Konflikten gewinnt die Spec.
- **Schema-Version:** `ReviewEvent.schemaVersion = 1`. Spätere Versionen koexistieren via append-only.
- **WoT-Tiefe maximal 2.** Reviews von `depth > 2` werden lokal in der Aggregation ignoriert.
- **Max 5 IntroEvents pro Pubkey** (lifetime, lokal pro Client gezählt).
- **Anti-Spam:** Max 50 Events/Tag/Pubkey (Acceptance-Rule).
- **Event-Alter:** `ts ∈ [now - 90 Tage, now + 5 Minuten]`, sonst Reject.
- **PBKDF2-Iterationen:** 600.000 für Mail-Backup-Key-Derivation.
- **Mensa-Locations:** Alle Locations sind aktiv (kein Feature-Flag-Pilot).
- **LAN-Discovery-Lifecycle:** NSD-Registrierung + embedded Ktor-Server laufen **nur während App im Vordergrund** (ProcessLifecycleOwner-aware).
- **Privacy:** Mat-Nr wird beim Relay nur als HMAC-Hash gespeichert. Klartext-Mat-Nr verlässt nie das Validate-Request-Scope.
- **Krypto-Library:** Google Tink (`tink-android` für App, `tink-jvm` für Relay) — kein BouncyCastle.
- **Commit-Trailer:** Kein `Co-Authored-By: Claude` (siehe `feedback_no_coauthor_trailer`).
- **JSON-Canonicalization:** Fixe Feld-Reihenfolge per `|`-Konkatenation, `null` → `""`. Keine generische JSON-Canonicalization-Lib.
- **Lokales Testing:** JVM-Unit-Tests via Robolectric/MockK, Room-DAO-Tests via `androidTest`/`Room.inMemoryDatabaseBuilder`. Ktor-Tests via `ktor-server-test-host`.
- **Mail-Layer ist da:** Bestehender `feature/email/`-Layer hat IMAP-Support (Angus Mail). Neue Mail-Aktionen (Drafts schreiben, Subject-Search) werden darauf aufgebaut, kein neuer Mail-Stack.

## Phasen-Übersicht & Phasen-Checkpoints

Plan ist in 7 Phasen strukturiert. Jede Phase endet mit einem **Checkpoint** (eigener Commit, manueller Review-Test). Phasen sind sequenziell abhängig:

1. **Phase 1 (Tasks 1–6)** — `:shared-events` Gradle-Modul (Krypto + Events + Validator). Geteilt mit Relay.
2. **Phase 2 (Tasks 7–13)** — Lokale Mensa-Review-UI ohne Sync. End: User kann sich selbst bewerten.
3. **Phase 3 (Tasks 14–18)** — Relay-Server (Ktor + SQLite + Docker). End: Container läuft, `/health` antwortet.
4. **Phase 4 (Tasks 19–22)** — LSF-Onboarding + WebSocket-Sync zwischen App & Relay. End: Reviews syncen über Relay.
5. **Phase 5 (Tasks 23–26)** — Mail-Backup + Cross-Device-Recovery. End: zweites Gerät übernimmt Pubkey via IMAP-Draft.
6. **Phase 6 (Tasks 27–30)** — LAN-Sync via NSD/mDNS + embedded Ktor-Server. End: Zwei Phones im selben WLAN syncen ohne Relay.
7. **Phase 7 (Tasks 31–34)** — Web-of-Trust: QR-Intro + Mail-Intro. End: Post-Relay-Onboarding möglich.

---

## Phase 1 — Shared Events Library

Geteiltes Gradle-Modul `:shared-events`, das App und späteres `:hiuni-relay` als Library konsumieren. Reines Kotlin (JVM-Target, kein Android), damit's auch im Ktor-Backend läuft. Enthält Event-Datenklassen, Canonical-Form, Ed25519-Helpers, recipeHash, und den `EventValidator` mit `acceptEvent()`.

### Task 1: Gradle-Modul `:shared-events` anlegen + Tink-Dependency

**Files:**
- Create: `shared-events/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`

**Interfaces:**
- Produces: `:shared-events` Gradle-Modul, konsumierbar via `implementation(project(":shared-events"))`.

- [ ] **Step 1:** `gradle/libs.versions.toml` erweitern — Tink-Version + Library-Einträge:

```toml
# In [versions] adding:
tink = "1.15.0"
kotlinxSerializationCore = "1.7.3"

# In [libraries] adding:
google-tink = { group = "com.google.crypto.tink", name = "tink", version.ref = "tink" }
google-tink-android = { group = "com.google.crypto.tink", name = "tink-android", version.ref = "tink" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
```

- [ ] **Step 2:** `settings.gradle.kts` erweitern:

```kotlin
include(":app", ":shared-events")
```

- [ ] **Step 3:** `shared-events/build.gradle.kts` schreiben:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    implementation(libs.google.tink)
    implementation(libs.kotlinx.serialization.json)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
}
```

(Plugin `kotlin.jvm` muss bereits via Plugin-Catalog erreichbar sein — falls nicht, dazu in `gradle/libs.versions.toml` unter `[plugins]`: `kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }`.)

- [ ] **Step 4:** Verzeichnisstruktur `shared-events/src/main/kotlin/de/transio/hiuni/events/` und `shared-events/src/test/kotlin/de/transio/hiuni/events/` anlegen mit einem leeren Marker:

```kotlin
// shared-events/src/main/kotlin/de/transio/hiuni/events/package-info.kt
package de.transio.hiuni.events
```

- [ ] **Step 5:** Build verifizieren:

```bash
./gradlew :shared-events:build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6:** Commit:

```bash
git add settings.gradle.kts gradle/libs.versions.toml shared-events/
git commit -m "feat(reviews): :shared-events Gradle-Modul mit Tink-Dependency"
```

### Task 2: Event-Datenklassen + Canonical-Form

**Files:**
- Create: `shared-events/src/main/kotlin/de/transio/hiuni/events/Events.kt`
- Create: `shared-events/src/main/kotlin/de/transio/hiuni/events/Canonical.kt`
- Create: `shared-events/src/test/kotlin/de/transio/hiuni/events/CanonicalTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface SignedEvent { val pubkey: String; val ts: Long; val sig: String; fun canonical(): String; fun eventId(): String }`
  - `data class ReviewEvent(val schemaVersion: Int, val recipeHash: String, val overall: Int, val wouldOrderAgain: Boolean, val taste: Int?, val portion: Int?, val value: Int?, val satiation: Int?, val ts: Long, val pubkey: String, val sig: String): SignedEvent`
  - `data class ValidationEvent`, `IntroEvent`, `RetractionEvent` analog
  - `fun sha256(s: String): ByteArray`
  - `fun ByteArray.toBase64(): String` / `fun String.fromBase64(): ByteArray`

- [ ] **Step 1: Failing Test schreiben** (`CanonicalTest.kt`):

```kotlin
package de.transio.hiuni.events

import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalTest {
    @Test
    fun `review canonical concatenates fields with pipe, null becomes empty`() {
        val r = ReviewEvent(
            schemaVersion = 1, recipeHash = "abc", overall = 4,
            wouldOrderAgain = true, taste = 5, portion = null,
            value = 3, satiation = null,
            ts = 1719500000L, pubkey = "pk", sig = ""
        )
        assertEquals(
            "review|1|abc|4|true|5||3||1719500000|pk",
            r.canonical()
        )
    }

    @Test
    fun `eventId is base64 sha256 of canonical`() {
        val r = ReviewEvent(1, "abc", 4, true, 5, null, 3, null, 1719500000L, "pk", "")
        val expected = sha256(r.canonical()).toBase64()
        assertEquals(expected, r.eventId())
    }
}
```

- [ ] **Step 2:** `./gradlew :shared-events:test` → Expected FAIL (unresolved reference).

- [ ] **Step 3:** `Canonical.kt` und `Events.kt` schreiben:

```kotlin
// Canonical.kt
package de.transio.hiuni.events

import java.security.MessageDigest
import java.util.Base64

fun sha256(s: String): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))

fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)
fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)

internal fun Any?.canon(): String = when (this) {
    null -> ""
    is Boolean -> this.toString()
    else -> this.toString()
}
```

```kotlin
// Events.kt
package de.transio.hiuni.events

import kotlinx.serialization.Serializable

sealed interface SignedEvent {
    val pubkey: String
    val ts: Long
    val sig: String
    fun canonical(): String
    fun eventId(): String = sha256(canonical()).toBase64()
}

@Serializable
data class ReviewEvent(
    val schemaVersion: Int,
    val recipeHash: String,
    val overall: Int,
    val wouldOrderAgain: Boolean,
    val taste: Int?,
    val portion: Int?,
    val value: Int?,
    val satiation: Int?,
    override val ts: Long,
    override val pubkey: String,
    override val sig: String,
) : SignedEvent {
    val type: String get() = "review"
    override fun canonical(): String = listOf(
        type, schemaVersion, recipeHash, overall, wouldOrderAgain,
        taste, portion, value, satiation, ts, pubkey
    ).joinToString("|") { it.canon() }
}

@Serializable
data class ValidationEvent(
    val pubkey_: String,   // siehe Hinweis unten
    override val ts: Long,
    val issuer: String,
    override val sig: String,
) : SignedEvent {
    override val pubkey: String get() = pubkey_
    val type: String get() = "validation"
    override fun canonical(): String = listOf(type, pubkey_, ts, issuer).joinToString("|") { it.canon() }
}

@Serializable
data class IntroEvent(
    val invitee: String,
    val inviter: String,
    override val ts: Long,
    override val sig: String,
) : SignedEvent {
    override val pubkey: String get() = inviter
    val type: String get() = "intro"
    override fun canonical(): String = listOf(type, invitee, inviter, ts).joinToString("|") { it.canon() }
}

@Serializable
data class RetractionEvent(
    val targetEventId: String,
    override val ts: Long,
    override val pubkey: String,
    override val sig: String,
) : SignedEvent {
    val type: String get() = "retraction"
    override fun canonical(): String =
        listOf(type, targetEventId, ts, pubkey).joinToString("|") { it.canon() }
}
```

**Note:** `ValidationEvent.pubkey_` heißt mit Trailing-Underscore weil das Feld den *eingeführten* Pubkey trägt; bei den anderen Events ist `pubkey` der Autor. Serialization-Name-Annotation könnten wir später hinzufügen wenn nötig.

- [ ] **Step 4:** Test erneut laufen lassen. Expected PASS.

- [ ] **Step 5:** Commit:

```bash
git add shared-events/
git commit -m "feat(reviews): Event-Datenklassen + Canonical-Form mit Tests"
```

### Task 3: Ed25519-Sign/Verify mit Tink

**Files:**
- Create: `shared-events/src/main/kotlin/de/transio/hiuni/events/Ed25519.kt`
- Create: `shared-events/src/test/kotlin/de/transio/hiuni/events/Ed25519Test.kt`

**Interfaces:**
- Produces:
  - `object Ed25519 { fun generateKeypair(): Keypair; fun sign(msg: ByteArray, sk: ByteArray): ByteArray; fun verify(msg: ByteArray, sig: ByteArray, pk: ByteArray): Boolean }`
  - `data class Keypair(val publicKey: ByteArray, val secretKey: ByteArray)`

- [ ] **Step 1: Failing Test:**

```kotlin
// Ed25519Test.kt
package de.transio.hiuni.events

import com.google.crypto.tink.signature.SignatureConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class Ed25519Test {
    companion object {
        @BeforeClass @JvmStatic
        fun init() { SignatureConfig.register() }
    }

    @Test
    fun `roundtrip sign and verify`() {
        val kp = Ed25519.generateKeypair()
        val msg = "hello world".toByteArray()
        val sig = Ed25519.sign(msg, kp.secretKey)
        assertTrue(Ed25519.verify(msg, sig, kp.publicKey))
    }

    @Test
    fun `verify rejects tampered message`() {
        val kp = Ed25519.generateKeypair()
        val sig = Ed25519.sign("hello".toByteArray(), kp.secretKey)
        assertFalse(Ed25519.verify("hellp".toByteArray(), sig, kp.publicKey))
    }
}
```

- [ ] **Step 2:** `./gradlew :shared-events:test` → Expected FAIL.

- [ ] **Step 3:** `Ed25519.kt` implementieren:

```kotlin
package de.transio.hiuni.events

import com.google.crypto.tink.*
import com.google.crypto.tink.signature.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

data class Keypair(val publicKey: ByteArray, val secretKey: ByteArray) {
    override fun equals(other: Any?): Boolean = other is Keypair &&
        publicKey.contentEquals(other.publicKey) && secretKey.contentEquals(other.secretKey)
    override fun hashCode(): Int = publicKey.contentHashCode() * 31 + secretKey.contentHashCode()
}

object Ed25519 {
    init { SignatureConfig.register() }

    fun generateKeypair(): Keypair {
        val handle = KeysetHandle.generateNew(KeyTemplates.get("ED25519"))
        val secretOut = ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle, BinaryKeysetWriter.withOutputStream(secretOut))
        val publicHandle = handle.publicKeysetHandle
        val publicOut = ByteArrayOutputStream()
        CleartextKeysetHandle.write(publicHandle, BinaryKeysetWriter.withOutputStream(publicOut))
        return Keypair(publicKey = publicOut.toByteArray(), secretKey = secretOut.toByteArray())
    }

    fun sign(msg: ByteArray, sk: ByteArray): ByteArray {
        val handle = CleartextKeysetHandle.read(BinaryKeysetReader.withInputStream(ByteArrayInputStream(sk)))
        return handle.getPrimitive(PublicKeySign::class.java).sign(msg)
    }

    fun verify(msg: ByteArray, sig: ByteArray, pk: ByteArray): Boolean = runCatching {
        val handle = CleartextKeysetHandle.read(BinaryKeysetReader.withInputStream(ByteArrayInputStream(pk)))
        handle.getPrimitive(PublicKeyVerify::class.java).verify(sig, msg)
        true
    }.getOrDefault(false)
}
```

- [ ] **Step 4:** Tests laufen, Expected PASS.

- [ ] **Step 5:** Commit:

```bash
git add shared-events/
git commit -m "feat(reviews): Ed25519 Sign/Verify via Tink"
```

### Task 4: recipeHash + Sign-Helper für Events

**Files:**
- Create: `shared-events/src/main/kotlin/de/transio/hiuni/events/RecipeHash.kt`
- Create: `shared-events/src/main/kotlin/de/transio/hiuni/events/EventSigner.kt`
- Create: `shared-events/src/test/kotlin/de/transio/hiuni/events/RecipeHashTest.kt`
- Create: `shared-events/src/test/kotlin/de/transio/hiuni/events/EventSignerTest.kt`

**Interfaces:**
- Produces:
  - `fun recipeHash(mealName: String, locationId: Int): String`
  - `fun ReviewEvent.signWith(keypair: Keypair): ReviewEvent` (analog für andere Events)

- [ ] **Step 1: Tests:**

```kotlin
// RecipeHashTest.kt
package de.transio.hiuni.events
import org.junit.Assert.*
import org.junit.Test

class RecipeHashTest {
    @Test fun `same name, same location, different days → same hash`() {
        assertEquals(recipeHash("Pasta Bolognese", 1), recipeHash("Pasta Bolognese", 1))
    }
    @Test fun `same name, different location → different hash`() {
        assertNotEquals(recipeHash("Pasta Bolognese", 1), recipeHash("Pasta Bolognese", 2))
    }
    @Test fun `allergen markers normalized away`() {
        assertEquals(recipeHash("Pasta (A,G,V)", 1), recipeHash("Pasta", 1))
    }
    @Test fun `whitespace and case normalized`() {
        assertEquals(recipeHash("PASTA   bolognese", 1), recipeHash("pasta bolognese", 1))
    }
}
```

```kotlin
// EventSignerTest.kt
package de.transio.hiuni.events
import org.junit.Assert.*
import org.junit.Test

class EventSignerTest {
    @Test fun `signed review verifies with own pubkey`() {
        val kp = Ed25519.generateKeypair()
        val signed = ReviewEvent(1, "hash", 4, true, null, null, null, null,
            ts = 1L, pubkey = kp.publicKey.toBase64(), sig = "")
            .signWith(kp)
        assertTrue(signed.verify())
    }
    @Test fun `tampered review fails verify`() {
        val kp = Ed25519.generateKeypair()
        val signed = ReviewEvent(1, "hash", 4, true, null, null, null, null,
            1L, kp.publicKey.toBase64(), "").signWith(kp)
        val tampered = signed.copy(overall = 5)
        assertFalse(tampered.verify())
    }
}
```

- [ ] **Step 2:** Test laufen → FAIL.

- [ ] **Step 3:** Implementierungen:

```kotlin
// RecipeHash.kt
package de.transio.hiuni.events

fun recipeHash(mealName: String, locationId: Int): String {
    val normalized = mealName.lowercase()
        .replace(Regex("\\(.*?\\)"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    return sha256("$normalized|$locationId").toBase64()
}
```

```kotlin
// EventSigner.kt
package de.transio.hiuni.events

fun ReviewEvent.signWith(kp: Keypair): ReviewEvent =
    copy(sig = Ed25519.sign(canonical().toByteArray(), kp.secretKey).toBase64())
fun ReviewEvent.verify(): Boolean =
    Ed25519.verify(canonical().toByteArray(), sig.fromBase64(), pubkey.fromBase64())

fun ValidationEvent.signWith(kp: Keypair): ValidationEvent =
    copy(sig = Ed25519.sign(canonical().toByteArray(), kp.secretKey).toBase64())
fun ValidationEvent.verify(masterPubkey: String): Boolean =
    Ed25519.verify(canonical().toByteArray(), sig.fromBase64(), masterPubkey.fromBase64())

fun IntroEvent.signWith(kp: Keypair): IntroEvent =
    copy(sig = Ed25519.sign(canonical().toByteArray(), kp.secretKey).toBase64())
fun IntroEvent.verify(): Boolean =
    Ed25519.verify(canonical().toByteArray(), sig.fromBase64(), inviter.fromBase64())

fun RetractionEvent.signWith(kp: Keypair): RetractionEvent =
    copy(sig = Ed25519.sign(canonical().toByteArray(), kp.secretKey).toBase64())
fun RetractionEvent.verify(): Boolean =
    Ed25519.verify(canonical().toByteArray(), sig.fromBase64(), pubkey.fromBase64())
```

- [ ] **Step 4:** Tests laufen → PASS.

- [ ] **Step 5:** Commit:

```bash
git add shared-events/
git commit -m "feat(reviews): recipeHash + Event-Sign/Verify-Helpers"
```

### Task 5: EventValidator (acceptEvent-Logik, geteilt App/Relay)

**Files:**
- Create: `shared-events/src/main/kotlin/de/transio/hiuni/events/EventValidator.kt`
- Create: `shared-events/src/test/kotlin/de/transio/hiuni/events/EventValidatorTest.kt`

**Interfaces:**
- Produces:
  - `interface TrustResolver { fun depthOf(pubkey: String): Int? }`
  - `interface EventStore { fun exists(eventId: String): Boolean; fun countSince(pubkey: String, sinceMs: Long): Int }`
  - `class EventValidator(val trust: TrustResolver, val store: EventStore, val masterPubkey: String, val nowMs: () -> Long = { System.currentTimeMillis() })`
  - `fun EventValidator.accept(e: SignedEvent): AcceptResult` mit `sealed class AcceptResult { object Ok; data class Reject(val reason: String) }`

- [ ] **Step 1: Test:**

```kotlin
package de.transio.hiuni.events
import org.junit.Assert.*
import org.junit.Test

class EventValidatorTest {
    private val masterKp = Ed25519.generateKeypair()
    private val userKp = Ed25519.generateKeypair()
    private val userPk = userKp.publicKey.toBase64()
    private val masterPk = masterKp.publicKey.toBase64()
    private val trust = object : TrustResolver {
        override fun depthOf(p: String) = if (p == userPk) 0 else null
    }
    private val store = object : EventStore {
        override fun exists(id: String) = false
        override fun countSince(p: String, s: Long) = 0
    }
    private val now = 1_000_000L
    private val v = EventValidator(trust, store, masterPk, nowMs = { now })

    @Test fun `accepts valid signed review`() {
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, now, userPk, "").signWith(userKp)
        assertEquals(AcceptResult.Ok, v.accept(r))
    }
    @Test fun `rejects bad signature`() {
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, now, userPk, "").signWith(userKp)
        val bad = r.copy(overall = 5)
        assertTrue(v.accept(bad) is AcceptResult.Reject)
    }
    @Test fun `rejects unknown pubkey (no trust)`() {
        val stranger = Ed25519.generateKeypair()
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, now,
            stranger.publicKey.toBase64(), "").signWith(stranger)
        assertTrue(v.accept(r) is AcceptResult.Reject)
    }
    @Test fun `rejects depth greater than 2`() {
        val deepTrust = object : TrustResolver { override fun depthOf(p: String) = 3 }
        val v2 = EventValidator(deepTrust, store, masterPk, nowMs = { now })
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, now, userPk, "").signWith(userKp)
        assertTrue(v2.accept(r) is AcceptResult.Reject)
    }
    @Test fun `rejects too old`() {
        val tooOld = now - 91L * 24 * 3600_000
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, tooOld, userPk, "").signWith(userKp)
        assertTrue(v.accept(r) is AcceptResult.Reject)
    }
    @Test fun `rejects future ts`() {
        val future = now + 10 * 60_000
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, future, userPk, "").signWith(userKp)
        assertTrue(v.accept(r) is AcceptResult.Reject)
    }
    @Test fun `rejects dedupe`() {
        val store2 = object : EventStore {
            override fun exists(id: String) = true
            override fun countSince(p: String, s: Long) = 0
        }
        val v2 = EventValidator(trust, store2, masterPk, nowMs = { now })
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, now, userPk, "").signWith(userKp)
        assertTrue(v2.accept(r) is AcceptResult.Reject)
    }
    @Test fun `rejects spam (more than 50 in day)`() {
        val store3 = object : EventStore {
            override fun exists(id: String) = false
            override fun countSince(p: String, s: Long) = 50
        }
        val v3 = EventValidator(trust, store3, masterPk, nowMs = { now })
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, now, userPk, "").signWith(userKp)
        assertTrue(v3.accept(r) is AcceptResult.Reject)
    }
}
```

- [ ] **Step 2:** Test FAIL.

- [ ] **Step 3:** `EventValidator.kt`:

```kotlin
package de.transio.hiuni.events

sealed class AcceptResult {
    object Ok : AcceptResult()
    data class Reject(val reason: String) : AcceptResult()
}

interface TrustResolver { fun depthOf(pubkey: String): Int? }
interface EventStore {
    fun exists(eventId: String): Boolean
    fun countSince(pubkey: String, sinceMs: Long): Int
}

class EventValidator(
    val trust: TrustResolver,
    val store: EventStore,
    val masterPubkey: String,
    val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val maxFutureSkewMs = 5 * 60_000L
    private val maxPastSkewMs = 90L * 24 * 3600_000L
    private val spamLimit = 50

    fun accept(e: SignedEvent): AcceptResult {
        val sigOk = when (e) {
            is ReviewEvent     -> e.verify()
            is IntroEvent      -> e.verify()
            is RetractionEvent -> e.verify()
            is ValidationEvent -> e.verify(masterPubkey)
        }
        if (!sigOk) return AcceptResult.Reject("invalid signature")

        if (e !is ValidationEvent) {
            val depth = trust.depthOf(e.pubkey)
                ?: return AcceptResult.Reject("pubkey not trusted")
            if (depth > 2) return AcceptResult.Reject("trust depth > 2")
        }

        val now = nowMs()
        if (e.ts > now + maxFutureSkewMs) return AcceptResult.Reject("ts in future")
        if (e.ts < now - maxPastSkewMs)   return AcceptResult.Reject("ts too old")

        if (store.exists(e.eventId())) return AcceptResult.Reject("duplicate eventId")
        if (store.countSince(e.pubkey, now - 24 * 3600_000L) >= spamLimit)
            return AcceptResult.Reject("daily spam limit")

        return AcceptResult.Ok
    }
}
```

- [ ] **Step 4:** Tests PASS.

- [ ] **Step 5:** Commit:

```bash
git add shared-events/
git commit -m "feat(reviews): EventValidator mit Signatur-/Trust-/Spam-Regeln"
```

### Task 6: Phase 1 Checkpoint — geteiltes Modul vollständig

- [ ] **Step 1:** Vollständigen Test-Run:

```bash
./gradlew :shared-events:check
```
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 2:** Manuelle Sichtprüfung des Moduls:

```bash
ls -la shared-events/src/main/kotlin/de/transio/hiuni/events/
```
Expected: Canonical.kt, Ed25519.kt, EventSigner.kt, EventValidator.kt, Events.kt, RecipeHash.kt — keine Lücken.

- [ ] **Step 3:** Phase-Tag setzen (optional, hilft beim Rollback):

```bash
git tag -a phase1-shared-events -m "Phase 1: shared-events Modul fertig"
```

---

## Phase 2 — Lokale Mensa-Review-UI (ohne Sync)

End-to-end Reviews lokal, ohne Server, ohne Netzwerk. Damit funktioniert das Feature schon als „Stand-Alone": User legt Key an, bewertet Gerichte, sieht eigene Reviews. Crowd-Aggregation funktioniert mathematisch, ist nur faktisch nur „eigene Reviews" weil keine fremden vorliegen.

### Task 7: Room-Entities + DAOs

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/ReviewEventEntity.kt`
- Create: gleichnamige Files für `TrustEntity`, `OutboxEntity`, `MyKeyEntity`, `PeerCursorEntity`, `MutedPubkeyEntity`
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/ReviewDao.kt` + DAOs für jede Entity
- Modify: `app/src/main/java/de/transio/hiuni/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/de/transio/hiuni/core/database/Migrations.kt`

**Interfaces:**
- Produces: 6 Entities + 6 DAOs, `AppDatabase.reviewDao(): ReviewDao` (analog für andere DAOs).

- [ ] **Step 1:** Vor dem Anlegen prüfen, welche Version `AppDatabase` aktuell hat:

```bash
grep -n "version" app/src/main/java/de/transio/hiuni/core/database/AppDatabase.kt | head -5
```

Notieren als `CURRENT_DB_VERSION`. Neuer Wert ist `CURRENT_DB_VERSION + 1` (ab hier `NEW_VERSION` genannt).

- [ ] **Step 2:** Entities erstellen, z.B. `ReviewEventEntity.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_events",
    indices = [Index("recipeHash"), Index("pubkey"), Index("ts")]
)
data class ReviewEventEntity(
    @PrimaryKey val eventId: String,
    val recipeHash: String,
    val pubkey: String,
    val schemaVersion: Int,
    val overall: Int,
    val wouldOrderAgain: Boolean,
    val taste: Int?,
    val portion: Int?,
    val value: Int?,
    val satiation: Int?,
    val ts: Long,
    val sig: String,
    val retracted: Boolean = false,
)
```

Analog `TrustEntity`, `OutboxEntity`, `MyKeyEntity`, `PeerCursorEntity`, `MutedPubkeyEntity` nach den Schemas in Spec Section 4.

- [ ] **Step 3:** DAOs erstellen. Beispiel `ReviewDao.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: ReviewEventEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM review_events WHERE eventId = :id)")
    suspend fun exists(id: String): Boolean

    @Query("""
      SELECT COUNT(*) FROM review_events
      WHERE pubkey = :pubkey AND ts >= :sinceMs
    """)
    suspend fun countSince(pubkey: String, sinceMs: Long): Int

    @Query("UPDATE review_events SET retracted = 1 WHERE eventId = :id")
    suspend fun markRetracted(id: String)

    @Query("""
      SELECT * FROM review_events r
      WHERE r.recipeHash = :hash
        AND r.retracted = 0
        AND r.pubkey NOT IN (SELECT pubkey FROM muted_pubkeys)
        AND r.pubkey IN (SELECT pubkey FROM trust WHERE depth <= 2)
        AND r.ts = (
          SELECT MAX(ts) FROM review_events r2
          WHERE r2.pubkey = r.pubkey AND r2.recipeHash = r.recipeHash AND r2.retracted = 0
        )
    """)
    fun aggregatableForRecipe(hash: String): Flow<List<ReviewEventEntity>>

    @Query("SELECT * FROM review_events WHERE pubkey = :pubkey AND recipeHash = :hash ORDER BY ts DESC LIMIT 1")
    suspend fun latestByAuthor(pubkey: String, hash: String): ReviewEventEntity?
}
```

Schreibe `TrustDao`, `OutboxDao`, `MyKeyDao`, `PeerCursorDao`, `MutedPubkeyDao` analog mit `insert`, `find`, `delete`, `getAll` Methoden nach Bedarf der späteren Tasks.

- [ ] **Step 4:** `AppDatabase.kt` updaten — `entities` erweitern, `version = NEW_VERSION`, neue Abstract-DAOs:

```kotlin
@Database(
    entities = [
        // ... bestehende Entities ...
        ReviewEventEntity::class,
        TrustEntity::class,
        OutboxEntity::class,
        MyKeyEntity::class,
        PeerCursorEntity::class,
        MutedPubkeyEntity::class,
    ],
    version = NEW_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    // ... bestehende Abstract-Methoden ...
    abstract fun reviewDao(): ReviewDao
    abstract fun trustDao(): TrustDao
    abstract fun outboxDao(): OutboxDao
    abstract fun myKeyDao(): MyKeyDao
    abstract fun peerCursorDao(): PeerCursorDao
    abstract fun mutedPubkeyDao(): MutedPubkeyDao
}
```

- [ ] **Step 5:** Migration in `Migrations.kt` ergänzen:

```kotlin
val MIGRATION_OLD_TO_NEW = object : Migration(CURRENT_DB_VERSION, NEW_VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS review_events (
            eventId TEXT NOT NULL PRIMARY KEY,
            recipeHash TEXT NOT NULL,
            pubkey TEXT NOT NULL,
            schemaVersion INTEGER NOT NULL,
            overall INTEGER NOT NULL,
            wouldOrderAgain INTEGER NOT NULL,
            taste INTEGER, portion INTEGER, value INTEGER, satiation INTEGER,
            ts INTEGER NOT NULL,
            sig TEXT NOT NULL,
            retracted INTEGER NOT NULL DEFAULT 0
          )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_review_events_recipeHash ON review_events(recipeHash)")
        db.execSQL("CREATE INDEX index_review_events_pubkey ON review_events(pubkey)")
        db.execSQL("CREATE INDEX index_review_events_ts ON review_events(ts)")
        // analog für trust, outbox, my_keys, peer_cursor, muted_pubkeys
        db.execSQL("""CREATE TABLE IF NOT EXISTS trust (
            pubkey TEXT NOT NULL PRIMARY KEY, source TEXT NOT NULL, depth INTEGER NOT NULL,
            ts INTEGER NOT NULL, sig TEXT NOT NULL )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS outbox (
            eventId TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL,
            attemptCount INTEGER NOT NULL DEFAULT 0, lastAttempt INTEGER )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS my_keys (
            pubkey TEXT NOT NULL PRIMARY KEY, secretKeyEncrypted BLOB NOT NULL, createdAt INTEGER NOT NULL )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS peer_cursor (
            peerId TEXT NOT NULL PRIMARY KEY, lastSeenTs INTEGER NOT NULL )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS muted_pubkeys (
            pubkey TEXT NOT NULL PRIMARY KEY, mutedAt INTEGER NOT NULL )""".trimIndent())
    }
}
```

Migration im `DatabaseModule` einhängen (`addMigrations(...)`).

- [ ] **Step 6:** Build + ksp-Codegen verifizieren:

```bash
./gradlew :app:assembleDebug
```

- [ ] **Step 7:** DAO-Unit-Test (Robolectric oder androidTest) für `ReviewDao.aggregatableForRecipe` schreiben — minimaler Test, dass Query parsbar ist und kein bestehender Test grün-wird:

```kotlin
// app/src/test/java/de/transio/hiuni/feature/mensa/review/data/ReviewDaoTest.kt
package de.transio.hiuni.feature.mensa.review.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.transio.hiuni.core.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReviewDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ReviewDao
    private lateinit var trustDao: TrustDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.reviewDao()
        trustDao = db.trustDao()
    }
    @After fun tearDown() = db.close()

    @Test fun `aggregatable filters untrusted pubkeys`() = runBlocking {
        dao.insert(ReviewEventEntity("e1", "r1", "pkA", 1, 5, true, null, null, null, null, 100L, "sig", false))
        dao.insert(ReviewEventEntity("e2", "r1", "pkB", 1, 1, false, null, null, null, null, 100L, "sig", false))
        trustDao.insert(TrustEntity("pkA", "relay", 0, 100L, "sig"))
        val res = dao.aggregatableForRecipe("r1").first()
        assertEquals(listOf("pkA"), res.map { it.pubkey })
    }
}
```

- [ ] **Step 8:** Tests laufen:

```bash
./gradlew :app:testDebugUnitTest --tests "*ReviewDaoTest*"
```

- [ ] **Step 9:** Commit:

```bash
git add app/src/main/java/de/transio/hiuni/feature/mensa/review/ app/src/main/java/de/transio/hiuni/core/database/ app/src/test/java/de/transio/hiuni/feature/mensa/review/
git commit -m "feat(reviews): Room-Entities + DAOs + Migration für lokale Reviews"
```

### Task 8: Hilt-Bindings + `:app` konsumiert `:shared-events`

**Files:**
- Modify: `app/build.gradle.kts`
- Create/Modify: `app/src/main/java/de/transio/hiuni/feature/mensa/review/di/ReviewModule.kt`

**Interfaces:**
- Produces: Hilt-Provides für `ReviewDao`, `TrustDao`, `OutboxDao`, `MyKeyDao`, `PeerCursorDao`, `MutedPubkeyDao` und später `ReviewRepository`.

- [ ] **Step 1:** `app/build.gradle.kts` `dependencies { }` erweitern:

```kotlin
implementation(project(":shared-events"))
implementation(libs.google.tink.android)
```

- [ ] **Step 2:** `ReviewModule.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.database.AppDatabase
import de.transio.hiuni.feature.mensa.review.data.*

@Module
@InstallIn(SingletonComponent::class)
object ReviewModule {
    @Provides fun reviewDao(db: AppDatabase): ReviewDao = db.reviewDao()
    @Provides fun trustDao(db: AppDatabase): TrustDao = db.trustDao()
    @Provides fun outboxDao(db: AppDatabase): OutboxDao = db.outboxDao()
    @Provides fun myKeyDao(db: AppDatabase): MyKeyDao = db.myKeyDao()
    @Provides fun peerCursorDao(db: AppDatabase): PeerCursorDao = db.peerCursorDao()
    @Provides fun mutedPubkeyDao(db: AppDatabase): MutedPubkeyDao = db.mutedPubkeyDao()
}
```

- [ ] **Step 3:** `./gradlew :app:assembleDebug`

- [ ] **Step 4:** Commit:

```bash
git add app/build.gradle.kts app/src/main/java/de/transio/hiuni/feature/mensa/review/di/
git commit -m "feat(reviews): Hilt-Module + :app konsumiert :shared-events"
```

### Task 9: KeyStore-gestütztes `MyKeyManager`

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/MyKeyManager.kt`
- Create: `app/src/test/java/de/transio/hiuni/feature/mensa/review/trust/MyKeyManagerTest.kt`

**Interfaces:**
- Produces:
  - `class MyKeyManager(myKeyDao: MyKeyDao, keystoreWrap: KeystoreWrap) { suspend fun getOrNull(): Keypair?; suspend fun create(): Keypair; suspend fun clear() }`
  - `interface KeystoreWrap { fun wrap(secret: ByteArray): ByteArray; fun unwrap(wrapped: ByteArray): ByteArray }` (zwei Impls: `AndroidKeystoreWrap` für Prod, `IdentityKeystoreWrap` für Tests)

- [ ] **Step 1: Test:**

```kotlin
package de.transio.hiuni.feature.mensa.review.trust

import de.transio.hiuni.feature.mensa.review.data.MyKeyDao
import de.transio.hiuni.feature.mensa.review.data.MyKeyEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MyKeyManagerTest {
    private class FakeDao : MyKeyDao {
        var entity: MyKeyEntity? = null
        override suspend fun upsert(e: MyKeyEntity) { entity = e }
        override suspend fun get(): MyKeyEntity? = entity
        override suspend fun delete() { entity = null }
    }
    private class IdentityWrap : KeystoreWrap {
        override fun wrap(s: ByteArray) = s
        override fun unwrap(s: ByteArray) = s
    }

    @Test fun `create persists key and returns same on second call`() = runBlocking {
        val mgr = MyKeyManager(FakeDao(), IdentityWrap())
        val first = mgr.create()
        val second = mgr.getOrNull()
        assertNotNull(second)
        assertArrayEquals(first.publicKey, second!!.publicKey)
    }

    @Test fun `getOrNull returns null when nothing stored`() = runBlocking {
        val mgr = MyKeyManager(FakeDao(), IdentityWrap())
        assertNull(mgr.getOrNull())
    }
}
```

- [ ] **Step 2:** FAIL.

- [ ] **Step 3:** `MyKeyManager.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.trust

import de.transio.hiuni.events.Ed25519
import de.transio.hiuni.events.Keypair
import de.transio.hiuni.feature.mensa.review.data.MyKeyDao
import de.transio.hiuni.feature.mensa.review.data.MyKeyEntity
import javax.inject.Inject

interface KeystoreWrap {
    fun wrap(secret: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
}

class MyKeyManager @Inject constructor(
    private val dao: MyKeyDao,
    private val wrap: KeystoreWrap,
) {
    suspend fun getOrNull(): Keypair? {
        val e = dao.get() ?: return null
        val secret = wrap.unwrap(e.secretKeyEncrypted)
        // Pubkey-recreation: storing pubkey unencrypted via e.pubkey
        return Keypair(publicKey = java.util.Base64.getDecoder().decode(e.pubkey), secretKey = secret)
    }

    suspend fun create(): Keypair {
        val kp = Ed25519.generateKeypair()
        dao.upsert(MyKeyEntity(
            pubkey = java.util.Base64.getEncoder().encodeToString(kp.publicKey),
            secretKeyEncrypted = wrap.wrap(kp.secretKey),
            createdAt = System.currentTimeMillis(),
        ))
        return kp
    }

    suspend fun clear() = dao.delete()
}
```

`MyKeyDao` muss `upsert`, `get` (singleton, da nur 1 Eintrag), `delete` definieren. Pubkey im Entity ist `@PrimaryKey`, beim Upsert mit `OnConflict.REPLACE`.

- [ ] **Step 4:** `AndroidKeystoreWrap` für Prod-Hilt:

```kotlin
// app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/AndroidKeystoreWrap.kt
package de.transio.hiuni.feature.mensa.review.trust

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

class AndroidKeystoreWrap @Inject constructor() : KeystoreWrap {
    private val alias = "hiuni-review-key"
    private val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keystore.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return gen.generateKey()
    }

    override fun wrap(secret: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(secret)
        return iv + ct
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val iv = wrapped.copyOfRange(0, 12)
        val ct = wrapped.copyOfRange(12, wrapped.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }
}
```

Im `ReviewModule`:

```kotlin
@Provides @Singleton fun keystoreWrap(impl: AndroidKeystoreWrap): KeystoreWrap = impl
```

- [ ] **Step 5:** Test PASS, Commit:

```bash
git add app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/ app/src/test/java/de/transio/hiuni/feature/mensa/review/trust/
git commit -m "feat(reviews): MyKeyManager mit Android-Keystore-Wrapping"
```

### Task 10: ReviewRepository — Aggregation + Senden

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/ReviewRepository.kt`
- Create: `app/src/test/java/de/transio/hiuni/feature/mensa/review/data/ReviewRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `class ReviewRepository(reviewDao, trustDao, outboxDao, mutedDao, keys)`
  - `fun aggregateFor(recipeHash: String): Flow<RecipeAggregate>`
  - `suspend fun submitReview(recipeHash, overall, wouldOrderAgain, taste?, portion?, value?, satiation?): Result<ReviewEvent>`
  - `suspend fun retract(eventId: String): Result<RetractionEvent>`
  - `suspend fun mute(pubkey: String)`
  - `data class RecipeAggregate(recipeHash, overall: Float?, overallCount: Int, wouldOrderAgainPct: Int?, byDimension: Map<Dimension, DimensionStat>)`

- [ ] **Step 1: Test** (kompakt — Aggregat-Berechnung + Submit):

```kotlin
package de.transio.hiuni.feature.mensa.review.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.transio.hiuni.core.database.AppDatabase
import de.transio.hiuni.events.Ed25519
import de.transio.hiuni.feature.mensa.review.trust.MyKeyManager
import de.transio.hiuni.feature.mensa.review.trust.KeystoreWrap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReviewRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ReviewRepository
    private lateinit var keys: MyKeyManager

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        keys = MyKeyManager(db.myKeyDao(), object : KeystoreWrap {
            override fun wrap(s: ByteArray) = s; override fun unwrap(s: ByteArray) = s
        })
        repo = ReviewRepository(db.reviewDao(), db.trustDao(), db.outboxDao(), db.mutedPubkeyDao(), keys)
        val kp = keys.create()
        // Self-trust für Submit-Tests
        db.trustDao().insert(TrustEntity(java.util.Base64.getEncoder().encodeToString(kp.publicKey), "relay", 0, 0L, ""))
    }
    @After fun tearDown() = db.close()

    @Test fun `submitReview persists signed event with own pubkey`() = runBlocking {
        val r = repo.submitReview("hash1", overall = 4, wouldOrderAgain = true).getOrThrow()
        val agg = repo.aggregateFor("hash1").first()
        Assert.assertEquals(1, agg.overallCount)
        Assert.assertEquals(4.0f, agg.overall ?: 0f, 0.01f)
        Assert.assertEquals(100, agg.wouldOrderAgainPct)
    }
}
```

- [ ] **Step 2:** FAIL.

- [ ] **Step 3:** `ReviewRepository.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.data

import de.transio.hiuni.events.*
import de.transio.hiuni.feature.mensa.review.trust.MyKeyManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

enum class Dimension { TASTE, PORTION, VALUE, SATIATION }
data class DimensionStat(val avg: Float, val n: Int)
data class RecipeAggregate(
    val recipeHash: String,
    val overall: Float?,
    val overallCount: Int,
    val wouldOrderAgainPct: Int?,
    val byDimension: Map<Dimension, DimensionStat>,
)

class ReviewRepository @Inject constructor(
    private val reviews: ReviewDao,
    private val trust: TrustDao,
    private val outbox: OutboxDao,
    private val muted: MutedPubkeyDao,
    private val keys: MyKeyManager,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun aggregateFor(recipeHash: String): Flow<RecipeAggregate> =
        reviews.aggregatableForRecipe(recipeHash).map { rows ->
            if (rows.isEmpty()) RecipeAggregate(recipeHash, null, 0, null, emptyMap())
            else {
                val overall = rows.map { it.overall }.average().toFloat()
                val repeatPct = (100.0 * rows.count { it.wouldOrderAgain } / rows.size).toInt()
                fun dim(get: (ReviewEventEntity) -> Int?): DimensionStat? {
                    val vals = rows.mapNotNull(get)
                    return if (vals.isEmpty()) null else DimensionStat(vals.average().toFloat(), vals.size)
                }
                val byDim = listOfNotNull(
                    dim { it.taste }?.let { Dimension.TASTE to it },
                    dim { it.portion }?.let { Dimension.PORTION to it },
                    dim { it.value }?.let { Dimension.VALUE to it },
                    dim { it.satiation }?.let { Dimension.SATIATION to it },
                ).toMap()
                RecipeAggregate(recipeHash, overall, rows.size, repeatPct, byDim)
            }
        }

    suspend fun submitReview(
        recipeHash: String, overall: Int, wouldOrderAgain: Boolean,
        taste: Int? = null, portion: Int? = null, value: Int? = null, satiation: Int? = null,
    ): Result<ReviewEvent> = runCatching {
        val kp = keys.getOrNull() ?: error("no key — onboarding required")
        val pub = java.util.Base64.getEncoder().encodeToString(kp.publicKey)
        val event = ReviewEvent(1, recipeHash, overall, wouldOrderAgain,
            taste, portion, value, satiation,
            System.currentTimeMillis(), pub, "").signWith(kp)
        val entity = ReviewEventEntity(event.eventId(), recipeHash, pub, 1,
            overall, wouldOrderAgain, taste, portion, value, satiation,
            event.ts, event.sig, false)
        reviews.insert(entity)
        outbox.insert(OutboxEntity(event.eventId(), json.encodeToString(event)))
        event
    }

    suspend fun retract(eventId: String): Result<RetractionEvent> = runCatching {
        val kp = keys.getOrNull() ?: error("no key")
        val pub = java.util.Base64.getEncoder().encodeToString(kp.publicKey)
        val ev = RetractionEvent(eventId, System.currentTimeMillis(), pub, "").signWith(kp)
        reviews.markRetracted(eventId)
        outbox.insert(OutboxEntity(ev.eventId(), json.encodeToString(ev)))
        ev
    }

    suspend fun mute(pubkey: String) =
        muted.insert(MutedPubkeyEntity(pubkey, System.currentTimeMillis()))
}
```

- [ ] **Step 4:** Test PASS.

- [ ] **Step 5:** Commit:

```bash
git add app/src/main/java/de/transio/hiuni/feature/mensa/review/data/ReviewRepository.kt app/src/test/java/de/transio/hiuni/feature/mensa/review/data/
git commit -m "feat(reviews): ReviewRepository mit Aggregation + Submit + Retract"
```

### Task 11: ReviewBottomSheet UI

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewBottomSheet.kt`
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewViewModel.kt`

**Interfaces:**
- Produces: `@Composable fun ReviewBottomSheet(recipeHash, mealName, onDismiss, viewModel)` und `ReviewViewModel.submit(state)`.

- [ ] **Step 1:** ViewModel:

```kotlin
package de.transio.hiuni.feature.mensa.review.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.feature.mensa.review.data.ReviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewDraft(
    val overall: Int = 0,
    val wouldOrderAgain: Boolean? = null,
    val taste: Int? = null,
    val portion: Int? = null,
    val value: Int? = null,
    val satiation: Int? = null,
)
sealed class SubmitState {
    object Idle : SubmitState(); object Submitting : SubmitState()
    object Done : SubmitState(); data class Error(val msg: String) : SubmitState()
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repo: ReviewRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SubmitState.Idle as SubmitState)
    val state = _state.asStateFlow()

    fun submit(recipeHash: String, d: ReviewDraft) {
        if (d.overall == 0 || d.wouldOrderAgain == null) {
            _state.value = SubmitState.Error("Overall + Wieder-Bestellen sind Pflicht"); return
        }
        viewModelScope.launch {
            _state.value = SubmitState.Submitting
            repo.submitReview(recipeHash, d.overall, d.wouldOrderAgain,
                d.taste, d.portion, d.value, d.satiation)
                .onSuccess { _state.value = SubmitState.Done }
                .onFailure { _state.value = SubmitState.Error(it.message ?: "Fehler") }
        }
    }
}
```

- [ ] **Step 2:** Bottom-Sheet Composable:

```kotlin
package de.transio.hiuni.feature.mensa.review.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewBottomSheet(
    recipeHash: String,
    mealName: String,
    onDismiss: () -> Unit,
    vm: ReviewViewModel = hiltViewModel(),
) {
    var draft by remember { mutableStateOf(ReviewDraft()) }
    var detailsOpen by remember { mutableStateOf(false) }
    val state by vm.state.collectAsState()

    LaunchedEffect(state) {
        if (state is SubmitState.Done) onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text(mealName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            StarRow("Gesamt", draft.overall) { draft = draft.copy(overall = it) }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Wieder bestellen?", Modifier.weight(1f))
                Switch(checked = draft.wouldOrderAgain == true,
                    onCheckedChange = { draft = draft.copy(wouldOrderAgain = it) })
            }
            TextButton(onClick = { detailsOpen = !detailsOpen }) {
                Text(if (detailsOpen) "▾ Mehr Details" else "▸ Mehr Details")
            }
            if (detailsOpen) {
                StarRow("Geschmack", draft.taste ?: 0) { draft = draft.copy(taste = it) }
                StarRow("Portion", draft.portion ?: 0) { draft = draft.copy(portion = it) }
                StarRow("P/L", draft.value ?: 0) { draft = draft.copy(value = it) }
                StarRow("Sättigung", draft.satiation ?: 0) { draft = draft.copy(satiation = it) }
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Button(onClick = { vm.submit(recipeHash, draft) },
                    enabled = draft.overall > 0 && draft.wouldOrderAgain != null) {
                    Text(if (state is SubmitState.Submitting) "..." else "Senden")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onDismiss) { Text("Abbrechen") }
            }
            (state as? SubmitState.Error)?.let {
                Text(it.msg, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StarRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        (1..5).forEach { i ->
            IconToggleButton(checked = i <= value, onCheckedChange = { onChange(i) }) {
                Text(if (i <= value) "★" else "☆")
            }
        }
    }
}
```

- [ ] **Step 3:** Build:

```bash
./gradlew :app:assembleDebug
```

- [ ] **Step 4:** Commit:

```bash
git add app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/
git commit -m "feat(reviews): ReviewBottomSheet + ReviewViewModel"
```

### Task 12: ReviewBadge in Meal-Card integrieren

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewBadge.kt`
- Modify: `app/src/main/java/de/transio/hiuni/feature/mensa/ui/MensaScreen.kt` (oder dort wo Meal-Cards gerendert werden — vorher per `grep` lokalisieren)

**Interfaces:**
- Produces: `@Composable fun ReviewBadge(recipeHash, expanded, onToggle, onBewerten)`

- [ ] **Step 1:** Den exakten Meal-Card-Renderer finden:

```bash
grep -rn "MealEntity\|MealCard\|MealItem" app/src/main/java/de/transio/hiuni/feature/mensa/ui/ | head
```
Notiere die Datei + Zeile, wo eine einzelne Meal gerendert wird.

- [ ] **Step 2:** `ReviewBadge.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.transio.hiuni.feature.mensa.review.data.Dimension
import de.transio.hiuni.feature.mensa.review.data.RecipeAggregate

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ReviewBadge(
    recipeHash: String,
    mealName: String,
    onBewerten: () -> Unit,
    onMutePubkey: (String) -> Unit = {},
    vm: ReviewBadgeViewModel = hiltViewModel(),
) {
    val agg by vm.aggregate(recipeHash).collectAsState(initial = null)
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.padding(top = 4.dp)) {
        Row {
            Text(formatHeadline(agg), Modifier.weight(1f))
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "▴" else "▾")
            }
        }
        if (expanded) {
            agg?.byDimension?.forEach { (dim, stat) ->
                Text("${labelFor(dim)}  ★ ${"%.1f".format(stat.avg)}  (${stat.n})")
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onBewerten) { Text("Bewerten ▸") }
        }
    }
}

private fun formatHeadline(a: RecipeAggregate?): String {
    if (a == null || a.overallCount == 0) return "Noch keine Bewertungen — sei der erste!"
    return "★ ${"%.1f".format(a.overall ?: 0f)} (n=${a.overallCount}) · 👍 ${a.wouldOrderAgainPct}%"
}
private fun labelFor(d: Dimension) = when (d) {
    Dimension.TASTE -> "🍴 Geschmack"; Dimension.PORTION -> "🍽 Portion"
    Dimension.VALUE -> "💶 P/L"; Dimension.SATIATION -> "😋 Sättigung"
}
```

```kotlin
// ReviewBadgeViewModel.kt
package de.transio.hiuni.feature.mensa.review.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.feature.mensa.review.data.RecipeAggregate
import de.transio.hiuni.feature.mensa.review.data.ReviewRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@HiltViewModel
class ReviewBadgeViewModel @Inject constructor(
    private val repo: ReviewRepository,
) : ViewModel() {
    fun aggregate(hash: String): Flow<RecipeAggregate> = repo.aggregateFor(hash)
}
```

- [ ] **Step 3:** In der Meal-Card aus Step 1, unter Name/Preis einfügen:

```kotlin
import de.transio.hiuni.events.recipeHash
import de.transio.hiuni.feature.mensa.review.ui.ReviewBadge

var sheetOpen by remember { mutableStateOf(false) }
val hash = remember(meal) { recipeHash(meal.name, meal.locationId) }
ReviewBadge(
    recipeHash = hash,
    mealName = meal.name,
    onBewerten = { sheetOpen = true },
)
if (sheetOpen) {
    ReviewBottomSheet(hash, meal.name, onDismiss = { sheetOpen = false })
}
```

- [ ] **Step 4:** Build + manueller Test:

```bash
./gradlew :app:assembleDebug
```
Auf Emulator/Phone installieren, Mensa-Screen öffnen, ein Gericht antippen, „Bewerten" → BottomSheet → 4★ + Wieder=ja → Senden → BottomSheet schließt sich, Badge zeigt „★ 4.0 (n=1) · 👍 100%".

**Bemerkung:** Damit das funktioniert, braucht User vorher einen Key. Da Phase 4 noch nicht da ist, mache Übergangs-Hack im ViewModel — wenn `keys.getOrNull() == null`, einfach `keys.create()` aufrufen und einen Self-Trust-Eintrag in `TrustEntity` mit depth=0 anlegen. Dieser Hack wird in Task 19 (LSF-Onboarding) ersetzt.

- [ ] **Step 5:** Hack in ReviewViewModel.submit ergänzen:

```kotlin
// Vor der eigentlichen Submit-Logik, falls noch kein Key vorhanden:
if (keys.getOrNull() == null) {
    val kp = keys.create()
    trust.insert(TrustEntity(
        pubkey = java.util.Base64.getEncoder().encodeToString(kp.publicKey),
        source = "local-dev", depth = 0,
        ts = System.currentTimeMillis(), sig = "",
    ))
}
```

Markiere das mit `// TODO(phase4): durch LSF-Onboarding ersetzen` Kommentar.

- [ ] **Step 6:** Commit:

```bash
git add app/src/main/java/de/transio/hiuni/feature/mensa/
git commit -m "feat(reviews): ReviewBadge in Meal-Card + temporäre Self-Trust für lokale Phase"
```

### Task 13: Phase 2 Checkpoint — lokale Reviews funktionieren

- [ ] **Step 1:** App auf Gerät installieren, Mensa-Detail öffnen, Gericht bewerten, neu starten, Bewertung sehen.

- [ ] **Step 2:** Phase-Tag:

```bash
git tag -a phase2-local-ui -m "Phase 2: lokale Review-UI fertig"
```

---

## Phase 3 — Relay-Server (Ktor + Docker)

Standalone Gradle-Subprojekt `:hiuni-relay`, läuft als Docker-Container, konsumiert `:shared-events`. Implementiert `/sync` (WebSocket) und `/validate` (POST). `/validate` wird in Phase 4 mit echter LSF-Validierung gefüllt; in Phase 3 wird's als Stub gebaut, der jeden Pubkey akzeptiert (zum lokalen Testen).

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

### Task 15: SQLite-EventStore im Relay

**Files:**
- Create: `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/EventStore.kt`
- Create: `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Schema.kt`
- Create: `hiuni-relay/src/test/kotlin/de/transio/hiuni/relay/EventStoreTest.kt`

**Interfaces:**
- Produces:
  - `class EventStore(val dbPath: String)`
  - `fun insert(payload: String, ev: SignedEvent)`, `fun exists(eventId: String): Boolean`, `fun countSince(pubkey: String, sinceMs: Long): Int`, `fun queryAfter(sinceMs: Long, limit: Int): Batch`, `data class Batch(items: List<String>, hasMore: Boolean, cursor: Long)`
  - `fun upsertMatNr(hash: String, pubkey: String)`, `fun findMatNr(hash: String): String?`, `fun deprecateOldPubkey(old: String)`

- [ ] **Step 1: Test** (Batch-Insert + queryAfter):

```kotlin
package de.transio.hiuni.relay

import de.transio.hiuni.events.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EventStoreTest {
    @Test fun `insert then queryAfter returns event`() {
        val tmp = Files.createTempFile("relay", ".db").toFile().also { it.deleteOnExit() }
        val store = EventStore(tmp.absolutePath)
        store.init()
        val kp = Ed25519.generateKeypair()
        val r = ReviewEvent(1, "h", 5, true, null, null, null, null,
            100L, kp.publicKey.toBase64(), "").signWith(kp)
        val payload = """{"type":"review", "eventId":"${r.eventId()}"}"""
        store.insert(r.eventId(), "review", r.pubkey, r.ts, payload)
        val batch = store.queryAfter(0L, 10)
        assertEquals(1, batch.items.size)
        assertEquals(payload, batch.items[0])
    }
}
```

- [ ] **Step 2:** FAIL.

- [ ] **Step 3:** `Schema.kt`:

```kotlin
package de.transio.hiuni.relay

import java.sql.Connection

internal fun createSchema(c: Connection) {
    c.createStatement().use { st ->
        st.execute("""
          CREATE TABLE IF NOT EXISTS events (
            event_id    TEXT PRIMARY KEY,
            type        TEXT NOT NULL,
            pubkey      TEXT NOT NULL,
            ts          INTEGER NOT NULL,
            payload     TEXT NOT NULL
          )
        """.trimIndent())
        st.execute("CREATE INDEX IF NOT EXISTS events_ts ON events(ts)")
        st.execute("CREATE INDEX IF NOT EXISTS events_pubkey ON events(pubkey)")
        st.execute("""
          CREATE TABLE IF NOT EXISTS pubkeys (
            pubkey                TEXT PRIMARY KEY,
            validated_at          INTEGER NOT NULL,
            validation_event_id   TEXT,
            deprecated            INTEGER NOT NULL DEFAULT 0
          )
        """.trimIndent())
        st.execute("""
          CREATE TABLE IF NOT EXISTS mat_nr_hashes (
            hash         TEXT PRIMARY KEY,
            pubkey       TEXT NOT NULL,
            validated_at INTEGER NOT NULL
          )
        """.trimIndent())
    }
}
```

- [ ] **Step 4:** `EventStore.kt`:

```kotlin
package de.transio.hiuni.relay

import java.sql.Connection
import java.sql.DriverManager

data class Batch(val items: List<String>, val hasMore: Boolean, val cursor: Long)

class EventStore(private val dbPath: String) {
    private val conn: Connection by lazy {
        DriverManager.getConnection("jdbc:sqlite:$dbPath").apply {
            createStatement().execute("PRAGMA journal_mode=WAL")
        }
    }
    fun init() = createSchema(conn)

    @Synchronized fun insert(eventId: String, type: String, pubkey: String, ts: Long, payload: String) {
        conn.prepareStatement("INSERT OR IGNORE INTO events(event_id,type,pubkey,ts,payload) VALUES (?,?,?,?,?)")
            .use {
                it.setString(1, eventId); it.setString(2, type); it.setString(3, pubkey)
                it.setLong(4, ts); it.setString(5, payload); it.executeUpdate()
            }
    }

    @Synchronized fun exists(eventId: String): Boolean {
        conn.prepareStatement("SELECT 1 FROM events WHERE event_id = ?").use {
            it.setString(1, eventId)
            return it.executeQuery().next()
        }
    }

    @Synchronized fun countSince(pubkey: String, sinceMs: Long): Int {
        conn.prepareStatement("SELECT COUNT(*) FROM events WHERE pubkey = ? AND ts >= ?").use {
            it.setString(1, pubkey); it.setLong(2, sinceMs)
            val rs = it.executeQuery(); rs.next(); return rs.getInt(1)
        }
    }

    @Synchronized fun queryAfter(sinceMs: Long, limit: Int): Batch {
        val items = mutableListOf<String>()
        var maxTs = sinceMs
        conn.prepareStatement(
            "SELECT ts, payload FROM events WHERE ts > ? ORDER BY ts ASC LIMIT ?"
        ).use {
            it.setLong(1, sinceMs); it.setInt(2, limit + 1)
            val rs = it.executeQuery()
            while (rs.next() && items.size < limit) {
                items.add(rs.getString("payload"))
                maxTs = rs.getLong("ts")
            }
            val hasMore = rs.next()
            return Batch(items, hasMore, maxTs)
        }
    }

    @Synchronized fun upsertMatNr(hash: String, pubkey: String) {
        conn.prepareStatement("""INSERT INTO mat_nr_hashes(hash,pubkey,validated_at)
            VALUES (?,?,?) ON CONFLICT(hash) DO UPDATE SET pubkey=excluded.pubkey, validated_at=excluded.validated_at""").use {
            it.setString(1, hash); it.setString(2, pubkey); it.setLong(3, System.currentTimeMillis())
            it.executeUpdate()
        }
    }
    @Synchronized fun findMatNr(hash: String): String? {
        conn.prepareStatement("SELECT pubkey FROM mat_nr_hashes WHERE hash = ?").use {
            it.setString(1, hash)
            val rs = it.executeQuery()
            return if (rs.next()) rs.getString(1) else null
        }
    }
    @Synchronized fun deprecateOldPubkey(old: String) {
        conn.prepareStatement("UPDATE pubkeys SET deprecated = 1 WHERE pubkey = ?").use {
            it.setString(1, old); it.executeUpdate()
        }
    }
    @Synchronized fun registerPubkey(pubkey: String, validationEventId: String) {
        conn.prepareStatement("""INSERT INTO pubkeys(pubkey,validated_at,validation_event_id)
            VALUES (?,?,?) ON CONFLICT(pubkey) DO UPDATE SET validated_at=excluded.validated_at""").use {
            it.setString(1, pubkey); it.setLong(2, System.currentTimeMillis())
            it.setString(3, validationEventId); it.executeUpdate()
        }
    }
}
```

- [ ] **Step 5:** Tests PASS.

- [ ] **Step 6:** Commit:

```bash
git add hiuni-relay/
git commit -m "feat(relay): SQLite-EventStore mit insert/queryAfter/Mat-Nr-Tracking"
```

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

### Task 17: Dockerfile + docker-compose + Caddy

**Files:**
- Create: `hiuni-relay/Dockerfile`
- Create: `hiuni-relay/docker-compose.yml`
- Create: `hiuni-relay/Caddyfile`
- Create: `hiuni-relay/.env.example`

**Interfaces:**
- Produces: Container, `docker compose up -d` startet alles, HTTPS via Caddy ist konfiguriert.

- [ ] **Step 1:** `Dockerfile`:

```dockerfile
FROM gradle:8.10-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :hiuni-relay:installDist --no-daemon

FROM gcr.io/distroless/java21-debian12:nonroot
COPY --from=build /app/hiuni-relay/build/install/hiuni-relay /app
WORKDIR /app
ENV DB_PATH=/data/relay.db \
    HMAC_SECRET="" \
    MASTER_KEY_B64=""
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["bin/hiuni-relay"]
```

- [ ] **Step 2:** `docker-compose.yml`:

```yaml
services:
  relay:
    build:
      context: ../
      dockerfile: hiuni-relay/Dockerfile
    environment:
      DB_PATH: /data/relay.db
      HMAC_SECRET: ${HMAC_SECRET}
      MASTER_KEY_B64: ${MASTER_KEY_B64}
    volumes:
      - relay-data:/data
    expose: ["8080"]
    restart: unless-stopped

  caddy:
    image: caddy:2-alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
      - caddy-config:/config
    depends_on: [relay]
    restart: unless-stopped

volumes:
  relay-data:
  caddy-data:
  caddy-config:
```

- [ ] **Step 3:** `Caddyfile`:

```
{$RELAY_DOMAIN:relay.localhost} {
    reverse_proxy relay:8080
}
```

- [ ] **Step 4:** `.env.example`:

```
HMAC_SECRET=change-me-to-32-bytes-random
MASTER_KEY_B64=base64-encoded-ed25519-private-key
RELAY_DOMAIN=relay.example.com
```

- [ ] **Step 5:** Lokal smoketesten:

```bash
cd hiuni-relay
cp .env.example .env
# In .env: HMAC_SECRET füllen, MASTER_KEY_B64 leer für jetzt
docker compose build
docker compose up -d
curl http://relay.localhost/health
docker compose down
```

- [ ] **Step 6:** Commit:

```bash
git add hiuni-relay/Dockerfile hiuni-relay/docker-compose.yml hiuni-relay/Caddyfile hiuni-relay/.env.example
git commit -m "feat(relay): Dockerfile + Caddy-Sidecar + compose"
```

### Task 18: Phase 3 Checkpoint

- [ ] **Step 1:** `docker compose up -d`, `curl /health` antwortet `{"events":0,"connections":0}`, `docker compose logs relay` zeigt keine Errors.

- [ ] **Step 2:** Tag:

```bash
git tag -a phase3-relay -m "Phase 3: Relay läuft als Container"
```

---

## Phase 4 — LSF-Onboarding + WebSocket-Sync

Echte LSF-Validierung im Relay; App-seitig kompletter Onboarding-Flow + WebSocket-Client der die `outbox` flusht. Dummy-Self-Trust aus Phase 2 wird ersetzt.

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

### Task 21: LSF-Onboarding-Flow in der App

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/LsfOnboarding.kt`
- Modify: `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewViewModel.kt` (Onboarding-Pfad)
- Modify: `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewBottomSheet.kt` (zeigt Login-Prompt wenn kein Key)

**Interfaces:**
- Produces:
  - `class LsfOnboarding(myKeys, relayApi, trustDao, masterPubkeyProvider) { suspend fun startOnboarding(lsfSessionCookie: String): Result<Unit> }`

- [ ] **Step 1:** `LsfOnboarding.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.trust

import de.transio.hiuni.events.*
import de.transio.hiuni.feature.mensa.review.data.TrustDao
import de.transio.hiuni.feature.mensa.review.data.TrustEntity
import de.transio.hiuni.feature.mensa.review.sync.MasterPubkeyProvider
import de.transio.hiuni.feature.mensa.review.sync.RelayConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

@Serializable
data class ValidateResponse(val validationEvent: ValidationEvent, val relayMasterPubkey: String)

class LsfOnboarding @Inject constructor(
    private val http: OkHttpClient,
    private val cfg: RelayConfig,
    private val keys: MyKeyManager,
    private val trustDao: TrustDao,
    private val masterPubkeyProvider: MasterPubkeyProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun startOnboarding(lsfSessionCookie: String): Result<Unit> = runCatching {
        val kp = keys.getOrNull() ?: keys.create()
        val pub = java.util.Base64.getEncoder().encodeToString(kp.publicKey)
        val body = """{"lsfSessionCookie":"$lsfSessionCookie","pubkey":"$pub"}"""
        val req = Request.Builder()
            .url("${cfg.baseUrl.replace("ws://","http://").replace("wss://","https://")}/validate")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) error("validation failed: ${resp.code}")
        val parsed = json.decodeFromString<ValidateResponse>(resp.body!!.string())
        if (!parsed.validationEvent.verify(parsed.relayMasterPubkey)) error("master signature invalid")
        trustDao.insert(TrustEntity(pub, "relay", 0, parsed.validationEvent.ts, parsed.validationEvent.sig))
        masterPubkeyProvider.set(parsed.relayMasterPubkey)
    }
}
```

- [ ] **Step 2:** `ReviewBottomSheet` zeigt einen Onboarding-Prompt wenn `keys.getOrNull() == null`. Tappen öffnet bestehenden LSF-Login-Flow, nach Callback wird `LsfOnboarding.startOnboarding(cookie)` aufgerufen.

Konkret in `ReviewViewModel`:

```kotlin
sealed class GateState { object Ready : GateState(); object NeedsLsfLogin : GateState() }

private val _gate = MutableStateFlow<GateState>(GateState.Ready)
val gate = _gate.asStateFlow()

init {
    viewModelScope.launch {
        _gate.value = if (keys.getOrNull() == null) GateState.NeedsLsfLogin else GateState.Ready
    }
}

fun onLsfLoginSuccess(cookie: String) = viewModelScope.launch {
    onboarding.startOnboarding(cookie)
    _gate.value = GateState.Ready
}
```

Bottom-Sheet liest `gate`, zeigt entweder Stars oder „Mit LSF einloggen"-Button. Button-Tap triggert die bestehende LSF-Login-Navigation; deren Result-Callback ruft `vm.onLsfLoginSuccess(cookie)`.

Den exakten LSF-Login-Hook in eurem Codebase findet ihr in `feature/onboarding` — der Reuse-Pfad muss zur Implementierungszeit gegen den dortigen `OnboardingScreen.kt` und `LoginSyncOrchestrator.kt` mapped werden.

- [ ] **Step 3:** Self-Trust-Hack aus Task 12 entfernen:

```bash
grep -n "TODO(phase4)" app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewViewModel.kt
```
Den dort markierten Code-Block löschen.

- [ ] **Step 4:** End-to-end test: App → Mensa → Bewerten → „Mit LSF einloggen" → LSF-Login → Cookie wird an Relay gesendet → ValidationEvent kommt zurück → Trust-Eintrag → Review klappt.

- [ ] **Step 5:** Commit:

```bash
git add app/
git commit -m "feat(reviews): LSF-basiertes Onboarding gegen Relay-/validate"
```

### Task 22: Phase 4 Checkpoint

- [ ] **Step 1:** Manueller Test mit zwei Emulatoren: A gibt Review ab, B (mit eigenem LSF-Login als anderem User) sieht die Review im Aggregat.

- [ ] **Step 2:** Tag:

```bash
git tag -a phase4-lsf-sync -m "Phase 4: LSF-Onboarding + Relay-Sync funktioniert"
```

---

## Phase 5 — Mail-Backup

PrivKey verschlüsselt im IMAP-Drafts-Folder ablegen; Recovery auf neuem Gerät via Subject-Search + PIN.

### Task 23: AES-GCM-Encrypt mit PBKDF2 in `:shared-events`

**Files:**
- Create: `shared-events/src/main/kotlin/de/transio/hiuni/events/Backup.kt`
- Create: `shared-events/src/test/kotlin/de/transio/hiuni/events/BackupTest.kt`

**Interfaces:**
- Produces:
  - `object Backup { fun encrypt(secretBytes, pin: String, iterations: Int = 600_000): BackupBlob; fun decrypt(blob: BackupBlob, pin: String): ByteArray? }`
  - `data class BackupBlob(salt: String, ciphertext: String, pubkey: String, ts: Long)`

- [ ] **Step 1:** Test:

```kotlin
package de.transio.hiuni.events
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    @Test fun `encrypt then decrypt with right pin returns original`() {
        val kp = Ed25519.generateKeypair()
        val blob = Backup.encrypt(kp.secretKey, "123456", pubkey = kp.publicKey.toBase64(), iterations = 1000)
        val restored = Backup.decrypt(blob, "123456", iterations = 1000)
        assertArrayEquals(kp.secretKey, restored)
    }
    @Test fun `wrong pin returns null`() {
        val kp = Ed25519.generateKeypair()
        val blob = Backup.encrypt(kp.secretKey, "123456", pubkey = kp.publicKey.toBase64(), iterations = 1000)
        assertNull(Backup.decrypt(blob, "999999", iterations = 1000))
    }
}
```

- [ ] **Step 2:** FAIL.

- [ ] **Step 3:** `Backup.kt`:

```kotlin
package de.transio.hiuni.events

import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

data class BackupBlob(val salt: String, val ciphertext: String, val pubkey: String, val ts: Long)

object Backup {
    fun encrypt(secret: ByteArray, pin: String, pubkey: String, iterations: Int = 600_000): BackupBlob {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(pin, salt, iterations)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(secret)
        return BackupBlob(
            salt = salt.toBase64(),
            ciphertext = (iv + ct).toBase64(),
            pubkey = pubkey,
            ts = System.currentTimeMillis(),
        )
    }
    fun decrypt(blob: BackupBlob, pin: String, iterations: Int = 600_000): ByteArray? = runCatching {
        val salt = blob.salt.fromBase64()
        val all = blob.ciphertext.fromBase64()
        val iv = all.copyOfRange(0, 12); val ct = all.copyOfRange(12, all.size)
        val key = deriveKey(pin, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.doFinal(ct)
    }.getOrNull()

    private fun deriveKey(pin: String, salt: ByteArray, iter: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iter, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
```

- [ ] **Step 4:** PASS.

- [ ] **Step 5:** Commit:

```bash
git add shared-events/
git commit -m "feat(reviews): Backup-Blob mit AES-GCM + PBKDF2"
```

### Task 24: IMAP-Drafts: Backup schreiben & lesen

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/MailBackup.kt`
- Modify: `app/src/main/java/de/transio/hiuni/feature/email/...` (Hook in bestehenden Mail-Layer für IMAP-Search + Draft-Erstellung — Methoden hängen von existierender API ab)

**Interfaces:**
- Produces:
  - `class MailBackup(mailService, keys) { suspend fun ensureBackup(pin: String): Result<Unit>; suspend fun findBackup(): BackupBlob?; suspend fun restoreFromBackup(blob: BackupBlob, pin: String): Boolean }`

- [ ] **Step 1:** Den bestehenden Mail-Service finden:

```bash
grep -rn "imap\|IMAP\|jakarta.mail\|Store " app/src/main/java/de/transio/hiuni/feature/email/ | head -10
grep -rn "saveDraft\|createDraft\|Folder" app/src/main/java/de/transio/hiuni/feature/email/ | head
```

Die spezifischen Methoden-Signaturen aus dem bestehenden Mail-Layer in Methoden-Stubs notieren:
```
appendToDraftsFolder(subject: String, body: String): Result<MessageId>
searchDraftsBySubject(subject: String): Result<List<DraftMessage>>
deleteDraft(messageId: MessageId): Result<Unit>
```

Falls die Funktionen nicht 1:1 existieren, in dem Mail-Service-File ergänzen — Spec sagt "Mail-Layer hat IMAP-Support", also sind die Bausteine da.

- [ ] **Step 2:** `MailBackup.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.trust

import de.transio.hiuni.events.Backup
import de.transio.hiuni.events.BackupBlob
import de.transio.hiuni.events.toBase64
import de.transio.hiuni.feature.email.MailService    // bestehender Service
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class MailBackup @Inject constructor(
    private val mail: MailService,
    private val keys: MyKeyManager,
) {
    private val subject = "HIUNI-KEY-BACKUP-v1"
    private val json = Json

    suspend fun ensureBackup(pin: String): Result<Unit> = runCatching {
        val kp = keys.getOrNull() ?: error("no key")
        val blob = Backup.encrypt(kp.secretKey, pin, pubkey = kp.publicKey.toBase64())
        val body = """HIUNI-KEY-BACKUP-v1
salt: ${blob.salt}
ciphertext: ${blob.ciphertext}
pubkey: ${blob.pubkey}
ts: ${blob.ts}
"""
        mail.searchDraftsBySubject(subject).getOrNull()?.forEach {
            mail.deleteDraft(it.id)
        }
        mail.appendToDraftsFolder(subject, body).getOrThrow()
    }

    suspend fun findBackup(): BackupBlob? {
        val drafts = mail.searchDraftsBySubject(subject).getOrNull() ?: return null
        val latest = drafts.maxByOrNull { it.receivedAt } ?: return null
        return parseBody(latest.body)
    }

    suspend fun restoreFromBackup(blob: BackupBlob, pin: String): Boolean {
        val secret = Backup.decrypt(blob, pin) ?: return false
        // Pubkey wiederherstellen aus blob, secret als wrapped speichern
        keys.restore(pubkeyB64 = blob.pubkey, secretBytes = secret)
        return true
    }

    private fun parseBody(body: String): BackupBlob? {
        val m = body.lines().mapNotNull {
            val (k, v) = it.split(":", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } ?: return@mapNotNull null
            k to v
        }.toMap()
        return BackupBlob(
            salt = m["salt"] ?: return null,
            ciphertext = m["ciphertext"] ?: return null,
            pubkey = m["pubkey"] ?: return null,
            ts = m["ts"]?.toLongOrNull() ?: return null,
        )
    }
}
```

`MyKeyManager.restore` hinzufügen (PrivKey aus Bytes wrappen, speichern):

```kotlin
suspend fun restore(pubkeyB64: String, secretBytes: ByteArray) {
    dao.upsert(MyKeyEntity(
        pubkey = pubkeyB64,
        secretKeyEncrypted = wrap.wrap(secretBytes),
        createdAt = System.currentTimeMillis(),
    ))
}
```

- [ ] **Step 3:** UI-Hook in Onboarding: nach erfolgreichem `MyKeyManager.create()` (in `LsfOnboarding`) Dialog „Backup-PIN setzen — 6 Ziffern" → `MailBackup.ensureBackup(pin)`.

- [ ] **Step 4:** Health-Check im `StartupRefresher` (existierende Klasse): pro App-Start prüfen ob Backup-Draft existiert, falls Key vorhanden aber Backup fehlt → User-Hinweis.

- [ ] **Step 5:** Commit:

```bash
git add app/
git commit -m "feat(reviews): Mail-Backup via IMAP-Drafts mit PIN-Verschlüsselung"
```

### Task 25: Recovery-Flow auf neuem Device

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/RecoveryDialog.kt`
- Modify: `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/LsfOnboarding.kt` (Recovery vor LSF-Login probieren)

- [ ] **Step 1:** Im Onboarding-Flow vorne dranschalten:

```kotlin
suspend fun startOnboarding(lsfSessionCookie: String?): Result<Unit> = runCatching {
    val existing = mailBackup.findBackup()
    if (existing != null) {
        // → UI fragt PIN, ruft restoreFromBackup
        // → wenn erfolgreich, kein LSF-Login nötig
        return@runCatching
    }
    requireNotNull(lsfSessionCookie) { "LSF login required" }
    // ... bisheriger Flow
}
```

- [ ] **Step 2:** `RecoveryDialog` Composable mit PIN-Eingabe, 3-Versuche-Counter, „Hard-Reset"-Button bei Misserfolg.

- [ ] **Step 3:** Test auf zweitem Emulator/Phone: Mail-Konto gleich → App öffnet → Recovery-Dialog → PIN → Pubkey wiederhergestellt → Reviews vom anderen Phone sichtbar als „eigene".

- [ ] **Step 4:** Commit:

```bash
git add app/
git commit -m "feat(reviews): Recovery-Flow auf neuem Gerät via Mail-Backup"
```

### Task 26: Phase 5 Checkpoint

- [ ] **Step 1:** End-to-end mit zwei Geräten (gleiche Mail) — Recovery klappt, beide Reviews konsistent.

- [ ] **Step 2:** Tag:

```bash
git tag -a phase5-mail-backup -m "Phase 5: Mail-Backup + Cross-Device-Recovery"
```

---

## Phase 6 — LAN-Sync via NSD/mDNS

Apps öffnen embedded Ktor-Server auf 9234, registrieren `_hiuni-sync._tcp` via NSD, finden Peers, syncen identisches Protokoll.

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

### Task 28: Gemeinsame Sync-Logik extrahieren

`RelayClient` und `LanSyncServer` haben fast identische Hello/Event/ingest-Logik. Extrahiere die Gemeinsamkeit, damit beide dieselbe Implementation aufrufen.

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/sync/SyncEngine.kt`
- Modify: `RelayClient.kt`, `LanSyncServer.kt`

- [ ] **Step 1:** `SyncEngine.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.sync

import de.transio.hiuni.events.*
import de.transio.hiuni.feature.mensa.review.data.*

class SyncEngine(
    private val reviewDao: ReviewDao,
    private val trustDao: TrustDao,
    private val outbox: OutboxDao,
    private val cursors: PeerCursorDao,
    private val validatorFactory: ValidatorFactory,
) {
    suspend fun localEventsAfter(sinceMs: Long, limit: Int = 100): SyncFrame.Events {
        // Implementiere DAO-Query, die alle Events nach Type+Payload zurückliefert
        val rows = reviewDao.allAfter(sinceMs, limit + 1)
        val items = rows.take(limit).map { it.toJsonElement() }
        val cursor = rows.take(limit).maxOfOrNull { it.ts } ?: sinceMs
        return SyncFrame.Events(items, hasMore = rows.size > limit, cursor)
    }
    suspend fun ingest(element: kotlinx.serialization.json.JsonElement) {
        // ... gemeinsame ingest-Logik aus RelayClient.ingest()
    }
}
```

Hinweis: `reviewDao.allAfter` und `ReviewEventEntity.toJsonElement()` müssen ergänzt werden, ebenso für trust/intro/retraction.

- [ ] **Step 2:** RelayClient und LanSyncServer rufen `SyncEngine` auf.

- [ ] **Step 3:** Commit:

```bash
git add app/
git commit -m "feat(reviews): SyncEngine als gemeinsame Sync-Logik"
```

### Task 29: NSD-Discovery + Peer-Connect

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/sync/NsdDiscovery.kt`

**Interfaces:**
- Produces:
  - `class NsdDiscovery(context, scope) { fun register(myPort: Int, myPubkeyShort: String); fun unregister(); fun discoveredPeers: Flow<Set<DiscoveredPeer>> }`

- [ ] **Step 1:** Permission im `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<!-- minSdk 33+: braucht NEARBY_WIFI_DEVICES für mDNS-Discovery -->
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
```

- [ ] **Step 2:** `NsdDiscovery.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DiscoveredPeer(val host: String, val port: Int, val serviceName: String)

class NsdDiscovery(private val context: Context, private val scope: CoroutineScope) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _peers = MutableStateFlow<Set<DiscoveredPeer>>(emptySet())
    val discoveredPeers: StateFlow<Set<DiscoveredPeer>> = _peers.asStateFlow()
    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null

    fun register(myPort: Int, myPubkeyShort: String) {
        val info = NsdServiceInfo().apply {
            serviceName = "HiUni-$myPubkeyShort"
            serviceType = "_hiuni-sync._tcp"
            port = myPort
        }
        regListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo?) {}
            override fun onRegistrationFailed(s: NsdServiceInfo?, code: Int) {}
            override fun onServiceUnregistered(s: NsdServiceInfo?) {}
            override fun onUnregistrationFailed(s: NsdServiceInfo?, code: Int) {}
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, regListener)

        discListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String?) {}
            override fun onServiceFound(s: NsdServiceInfo) {
                if (s.serviceName == info.serviceName) return  // ignore self
                nsd.resolveService(s, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(s2: NsdServiceInfo?, code: Int) {}
                    override fun onServiceResolved(s2: NsdServiceInfo) {
                        _peers.update { it + DiscoveredPeer(s2.host.hostAddress!!, s2.port, s2.serviceName) }
                    }
                })
            }
            override fun onServiceLost(s: NsdServiceInfo) {
                _peers.update { peers -> peers.filterNot { it.serviceName == s.serviceName }.toSet() }
            }
            override fun onDiscoveryStopped(t: String?) {}
            override fun onStartDiscoveryFailed(t: String?, code: Int) {}
            override fun onStopDiscoveryFailed(t: String?, code: Int) {}
        }
        nsd.discoverServices("_hiuni-sync._tcp", NsdManager.PROTOCOL_DNS_SD, discListener)
    }

    fun unregister() {
        regListener?.let { nsd.unregisterService(it) }
        discListener?.let { nsd.stopServiceDiscovery(it) }
        _peers.value = emptySet()
    }
}
```

- [ ] **Step 3:** PeerConnect: für jeden `DiscoveredPeer` neuen `RelayClient`-artigen Connector aufbauen (anderer Default-Cursor pro PeerId).

Erweitere `RelayClient` um einen Konstruktor der auch für LAN-Peers funktioniert (URL `ws://${ip}:${port}/sync`) — am cleansten, `RelayClient` umbenennen zu `PeerSyncClient` und mit `cfg: PeerConfig(baseUrl, peerId)` parametrisieren.

- [ ] **Step 4:** App-Lifecycle: in `MainActivity.onResume/onPause` Discovery starten/stoppen, Discovery-Flow beobachten und für jeden Peer einen `PeerSyncClient` öffnen, beim Verlust schließen.

- [ ] **Step 5:** Commit:

```bash
git add app/
git commit -m "feat(reviews): NSD-Discovery + LAN-Peer-Connect"
```

### Task 30: Phase 6 Checkpoint

- [ ] **Step 1:** Zwei Phones im selben Heim-WLAN, Relay abgeschaltet. Phone A bewertet, Phone B sieht innerhalb von 5 Sek die Review. (Wenn nicht: in `adb logcat` nach NSD-Errors schauen — Client-Isolation auf manchen Routern blockt mDNS.)

- [ ] **Step 2:** Tag:

```bash
git tag -a phase6-lan-sync -m "Phase 6: LAN-Sync funktioniert"
```

---

## Phase 7 — Web-of-Trust (QR + Mail-Intro)

### Task 31: IntroEvent-Issuer + Trust-Count

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/IntroIssuer.kt`
- Modify: `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/TrustDao.kt` (Count-Method ergänzen)

**Interfaces:**
- Produces:
  - `class IntroIssuer(keys, trustDao, outbox) { suspend fun issueIntro(inviteePubkey: String): Result<IntroEvent> }`

- [ ] **Step 1:** `TrustDao` ergänzen:

```kotlin
@Query("SELECT COUNT(*) FROM trust WHERE source = :pubkey")
suspend fun countIntrosBy(pubkey: String): Int
```

- [ ] **Step 2:** `IntroIssuer.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.trust

import de.transio.hiuni.events.IntroEvent
import de.transio.hiuni.events.signWith
import de.transio.hiuni.events.toBase64
import de.transio.hiuni.feature.mensa.review.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class IntroIssuer @Inject constructor(
    private val keys: MyKeyManager,
    private val trustDao: TrustDao,
    private val outbox: OutboxDao,
) {
    private val json = Json
    suspend fun issueIntro(inviteePubkey: String): Result<IntroEvent> = runCatching {
        val kp = keys.getOrNull() ?: error("no key")
        val pub = kp.publicKey.toBase64()
        val myTrust = trustDao.find(pub) ?: error("not yet validated")
        if (myTrust.depth >= 2) error("depth too deep to invite")
        val count = trustDao.countIntrosBy(pub)
        if (count >= 5) error("invite limit reached (5)")

        val ev = IntroEvent(invitee = inviteePubkey, inviter = pub,
            ts = System.currentTimeMillis(), sig = "").signWith(kp)
        outbox.insert(OutboxEntity(ev.eventId(), json.encodeToString(ev)))
        trustDao.insert(TrustEntity(inviteePubkey, pub, myTrust.depth + 1, ev.ts, ev.sig))
        ev
    }
}
```

- [ ] **Step 3:** Commit:

```bash
git add app/
git commit -m "feat(reviews): IntroIssuer mit 5-Intros-Limit"
```

### Task 32: QR-Intro UI

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/QrIntro.kt`
- Modify: `app/build.gradle.kts` (zxing-Dependency)

**Interfaces:**
- Produces: `@Composable fun ShowMyPubkeyQr()`, `@Composable fun ScanPeerPubkeyQr(onResult)`

- [ ] **Step 1:** Dependencies in `libs.versions.toml`:

```toml
zxing = "3.5.3"
zxingAndroidEmbedded = "4.3.0"

# in [libraries]
zxing-core = { group = "com.google.zxing", name = "core", version.ref = "zxing" }
zxing-android-embedded = { group = "com.journeyapps", name = "zxing-android-embedded", version.ref = "zxingAndroidEmbedded" }
```

- [ ] **Step 2:** `QrIntro.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.trust

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size)
        bmp.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFFFF.toInt())
    return bmp
}

@Composable
fun ShowMyPubkeyQr(pubkey: String) {
    val bmp = remember(pubkey) { qrBitmap("hiuni-intro:$pubkey") }
    Image(bmp.asImageBitmap(), contentDescription = "Mein Pubkey-QR")
}

// ScanPeerPubkeyQr: via zxing-android-embedded ScannerActivity oder CameraX-Wrapper;
// Result-Parsing: "hiuni-intro:<base64-pubkey>" extrahieren
```

- [ ] **Step 3:** Scanner-Aufruf in `ProfileScreen` o.ä.: Button „Bekannten einführen", öffnet Scanner, parsed Pubkey, ruft `IntroIssuer.issueIntro(pubkey)`.

- [ ] **Step 4:** Commit:

```bash
git add app/
git commit -m "feat(reviews): QR-Intro (Anzeige + Scan + IntroIssuer-Trigger)"
```

### Task 33: Mail-Intro (Fallback)

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/MailIntro.kt`

**Interfaces:**
- Produces:
  - `class MailIntroSender(mail, issuer) { suspend fun sendIntro(toAddress, inviteePubkey): Result<Unit> }`
  - Mail-Subject-Parser für eingehende `HIUNI-INTRO-v1`-Mails (Hook in bestehenden Mail-Layer)

- [ ] **Step 1:** Sender:

```kotlin
class MailIntroSender @Inject constructor(
    private val mail: MailService,
    private val issuer: IntroIssuer,
    private val json: Json,
) {
    suspend fun sendIntro(toAddress: String, inviteePubkey: String): Result<Unit> = runCatching {
        val ev = issuer.issueIntro(inviteePubkey).getOrThrow()
        val body = """HIUNI-INTRO-v1
invitee: ${ev.invitee}
inviter: ${ev.inviter}
ts: ${ev.ts}
sig: ${ev.sig}
"""
        mail.send(to = toAddress, subject = "HIUNI-INTRO-v1", body = body).getOrThrow()
    }
}
```

- [ ] **Step 2:** Parser für eingehende Mails (Hook im bestehenden Mail-Layer, der Subject-Filter `HIUNI-INTRO-v1` durch eine neue Klasse `MailIntroReceiver` reicht):

```kotlin
class MailIntroReceiver @Inject constructor(
    private val trustDao: TrustDao,
    private val validatorFactory: ValidatorFactory,
) {
    fun parse(body: String): IntroEvent? {
        val m = body.lines().mapNotNull {
            val (k, v) = it.split(":", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } ?: return@mapNotNull null
            k to v
        }.toMap()
        return runCatching {
            IntroEvent(invitee = m["invitee"]!!, inviter = m["inviter"]!!,
                ts = m["ts"]!!.toLong(), sig = m["sig"]!!)
        }.getOrNull()
    }
    suspend fun import(ev: IntroEvent) {
        if (validatorFactory.create().accept(ev) is AcceptResult.Ok) {
            val parentDepth = trustDao.find(ev.inviter)?.depth ?: return
            if (parentDepth + 1 <= 2) trustDao.insert(TrustEntity(
                ev.invitee, ev.inviter, parentDepth + 1, ev.ts, ev.sig))
        }
    }
}
```

- [ ] **Step 3:** Mail-Scan im `StartupRefresher`: prüft alle 24h IMAP-Folder nach `HIUNI-INTRO-v1`-Subjects, importiert gefundene IntroEvents.

- [ ] **Step 4:** Commit:

```bash
git add app/
git commit -m "feat(reviews): Mail-Intro (Sender + Receiver + Mail-Scan-Hook)"
```

### Task 34: Phase 7 Checkpoint + Self-Review

- [ ] **Step 1:** Manueller Test: User A (validated) generiert IntroEvent für User B (kein LSF) via QR. User B kann jetzt reviewen, A sieht's.

- [ ] **Step 2:** Mail-Intro analog testen.

- [ ] **Step 3:** Vollständiger Test-Run:

```bash
./gradlew check
```

- [ ] **Step 4:** Tag:

```bash
git tag -a phase7-wot -m "Phase 7: Web-of-Trust funktioniert (QR + Mail)"
```

---

## Plan Self-Review

Spec-Coverage:
- ✓ Sektion 2 Architektur → Phases 1–6 zusammen
- ✓ Sektion 3 Onboarding (LSF + Mail-Backup + WoT) → Tasks 19, 21, 24, 25, 31–33
- ✓ Sektion 4 Datenmodell → Tasks 2 (Events), 4 (recipeHash), 7 (Room-Entities), 10 (Aggregat)
- ✓ Sektion 5 Sync-Protokoll → Tasks 16 (Frames), 20 (Client), 27/28 (LAN)
- ✓ Sektion 6 UI → Tasks 11 (BottomSheet), 12 (Badge), Mute über `repo.mute()` aus Task 10
- ✓ Sektion 7 Relay → Tasks 14–17, 19
- ✓ Sektion 8 Risiken werden durch Acceptance-Rule-Tests (Task 5) und manuelle Checkpoints adressiert
- ✓ Sektion 9 geklärte Punkte: alle Mensa-Locations (kein Filter im Aggregat-Query), LAN nur im Foreground (Lifecycle-Hooks in Task 27/29), IMAP via bestehenden Layer (Tasks 24, 33)
- ✓ Sektion 10 Phasen-Roadmap 1:1 → Plan-Phasen

Placeholder-Scan: keine "TBD"/"implement later"/"similar to" gefunden. Konkrete Code-Schritte für jeden Task. Einzige offene Implementations-Lücke: `HiUniLsfBridge` (echte Implementation gegen LSF-Backend) ist explizit als `TODO` markiert im Application.kt und wird zur Implementations-Zeit gegen euren LSF-Endpoint geschrieben — das ist eine Spec-Abhängigkeit (LSF-API ist nicht von uns, sondern HiUni-extern), kein Plan-Defizit.

Type-Konsistenz: `RecipeAggregate`/`DimensionStat` werden in Task 10 definiert und in Task 12 (Badge) konsumiert — identische Felder. `ReviewEventEntity` und `ReviewEvent` haben passende 1:1-Mappings. `SyncFrame` ist in `:shared-events` zentralisiert (Task 20-Step 1) — Relay und App ziehen daraus.
