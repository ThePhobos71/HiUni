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

