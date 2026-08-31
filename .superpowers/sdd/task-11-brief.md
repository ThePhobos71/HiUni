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

