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

