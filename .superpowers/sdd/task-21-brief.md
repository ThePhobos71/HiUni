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

