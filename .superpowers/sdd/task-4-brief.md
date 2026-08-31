### Task 4: recipeHash (Tuple-Key Name+Location+NutritionFingerprint) + Sign-Helper für Events

**Files:**
- Create: `shared-events/src/main/kotlin/de/transio/hiuni/events/RecipeHash.kt`
- Create: `shared-events/src/main/kotlin/de/transio/hiuni/events/EventSigner.kt`
- Create: `shared-events/src/test/kotlin/de/transio/hiuni/events/RecipeHashTest.kt`
- Create: `shared-events/src/test/kotlin/de/transio/hiuni/events/EventSignerTest.kt`

**Interfaces:**
- Produces:
  - `data class Per100g(val caloricValue: Double?, val fat: Double?, val carbohydrates: Double?, val protein: Double?, val salt: Double?)`
  - `fun recipeHash(mealName: String, locationId: Int, nutritionFingerprint: String? = null): String`
  - `fun nutritionFingerprint(per100g: Per100g?): String?`
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
    @Test fun `different nutrition fingerprint produces different hash`() {
        val fp1 = "350.0|10.5|45.0|15.2|1.0"
        val fp2 = "360.0|10.5|45.0|15.2|1.0"
        assertNotEquals(recipeHash("Pasta", 1, fp1), recipeHash("Pasta", 1, fp2))
    }
    @Test fun `null fingerprint falls back to name and location only`() {
        assertEquals(recipeHash("Pasta", 1, null), recipeHash("Pasta", 1))
    }

    @Test fun `nutritionFingerprint returns null when all values are null`() {
        assertNull(nutritionFingerprint(Per100g(null, null, null, null, null)))
        assertNull(nutritionFingerprint(null))
    }
    @Test fun `nutritionFingerprint pipes values in fixed order`() {
        val fp = nutritionFingerprint(Per100g(350.0, 10.5, 45.0, 15.2, 1.0))
        assertEquals("350.0|10.5|45.0|15.2|1.0", fp)
    }
    @Test fun `nutritionFingerprint preserves null slots as empty`() {
        val fp = nutritionFingerprint(Per100g(350.0, null, 45.0, null, 1.0))
        assertEquals("350.0||45.0||1.0", fp)
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

data class Per100g(
    val caloricValue: Double?,
    val fat: Double?,
    val carbohydrates: Double?,
    val protein: Double?,
    val salt: Double?,
)

/** Fingerprint aus den per-100g-Nährwerten — gleiche Gerichte haben exakt gleiche Werte.
 * Reihenfolge fix: kcal|fat|carbs|protein|salt. null-Slots werden als leere Strings beibehalten,
 * damit (350.0,null,45.0,null,1.0) und (350.0,5.0,45.0,5.0,1.0) verschiedene Fingerprints haben.
 */
fun nutritionFingerprint(per100g: Per100g?): String? {
    per100g ?: return null
    val vals = listOf(per100g.caloricValue, per100g.fat, per100g.carbohydrates, per100g.protein, per100g.salt)
    if (vals.all { it == null }) return null
    return vals.joinToString("|") { it?.toString() ?: "" }
}

fun recipeHash(mealName: String, locationId: Int, nutritionFingerprint: String? = null): String {
    val normalized = mealName.lowercase()
        .replace(Regex("\\(.*?\\)"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    val fp = nutritionFingerprint ?: ""
    return sha256("$normalized|$locationId|$fp").toBase64()
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

