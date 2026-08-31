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

- [ ] **Step 3:** In der Meal-Card aus Step 1, unter Name/Preis einfügen. Nährwert-Fingerprint wird aus `meal.nutritionalValuesJson` extrahiert (existierender JSON-Stack, siehe `feature/mensa/data/MensaDtos.kt` für JSON-Struktur) — falls Nährwerte fehlen, fällt der Hash auf `name+locationId` zurück:

```kotlin
import de.transio.hiuni.events.Per100g
import de.transio.hiuni.events.nutritionFingerprint
import de.transio.hiuni.events.recipeHash
import de.transio.hiuni.feature.mensa.review.ui.ReviewBadge

var sheetOpen by remember { mutableStateOf(false) }
val hash = remember(meal) {
    val per100 = parseNutrition(meal.nutritionalValuesJson)
    recipeHash(meal.name, meal.locationId, nutritionFingerprint(per100))
}
ReviewBadge(recipeHash = hash, mealName = meal.name, onBewerten = { sheetOpen = true })
if (sheetOpen) {
    ReviewBottomSheet(hash, meal.name, onDismiss = { sheetOpen = false })
}
```

`parseNutrition`-Helper irgendwo in `feature/mensa/review/data/NutritionParser.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.data

import de.transio.hiuni.events.Per100g
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull

private val json = Json { ignoreUnknownKeys = true }

fun parseNutrition(jsonStr: String?): Per100g? {
    jsonStr ?: return null
    return runCatching {
        val root = json.parseToJsonElement(jsonStr).jsonObject
        val per100 = root["per_100_grams"]?.jsonObject ?: return@runCatching null
        Per100g(
            caloricValue = per100["caloric_value"]?.jsonPrimitive?.doubleOrNull,
            fat = per100["fat"]?.jsonPrimitive?.doubleOrNull,
            carbohydrates = per100["carbohydrates"]?.jsonPrimitive?.doubleOrNull,
            protein = per100["protein"]?.jsonPrimitive?.doubleOrNull,
            salt = per100["salt"]?.jsonPrimitive?.doubleOrNull,
        )
    }.getOrNull()
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

