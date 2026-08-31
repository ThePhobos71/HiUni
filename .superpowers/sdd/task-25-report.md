# Task 25 Report — Recovery-Flow + LsfOnboarding-Update

## Scope-Entscheidung
**Voll umgesetzt — alle 3 Teile**, weil sie eng verzahnt sind und einzeln
ausgeliefert seltsame Zwischenzustände erzeugt hätten (z.B. Recovery-Pfad ohne
Backup-Setup hätte beim ersten Onboarding nie ein Backup angelegt, also bei
nächstem Device-Switch wieder von 0 begonnen).

## Files
**Created:**
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/RecoveryDialog.kt`
  (`RecoveryPinSection` + `BackupSetupSection` Composables)

**Modified:**
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/LsfOnboarding.kt`
  - `MailBackup`-Dependency injected
  - `tryMailBackupOrNull()` — Probe ohne Throw
  - `completeRecovery(blob, pin)` — Restore + Master-Pubkey-Check
  - Top-level: `WrongPinException`, `MasterPubkeyMissingException`
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewViewModel.kt`
  - GateStates: `NeedsRecoveryPin(blob, attemptsLeft, error)`, `NeedsBackupPin`
  - `refreshGate()` prüft jetzt zusätzlich Master-Pubkey-Präsenz
  - Probiert Mail-Backup BEVOR NeedsOnboarding emittiert wird
  - `tryRecover/skipRecoveryAndOnboardFresh/skipRecoveryAndStartLsfLogin`
  - `setupBackup/skipBackupSetup` für post-onboarding Backup-Setup
  - `transitionAfterOnboarding()` zentralisiert Backup-Folge-Logik
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewSubmitScreen.kt`
  - Rendert `NeedsRecoveryPin` + `NeedsBackupPin`

## Build
`./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL  
`./gradlew :app:compileDebugUnitTestKotlin` → BUILD SUCCESSFUL  
`./gradlew :app:testDebugUnitTest --tests "*mensa.review*"` → BUILD SUCCESSFUL

Commit: `5fda51b feat(reviews): Recovery-Flow auf neuem Gerät via Mail-Backup`

## Self-Review

### (a) 3-Versuche-Counter
- Im `NeedsRecoveryPin`-State, lokal als `attemptsLeft: Int` im ViewModel.
- Nicht persistiert → reset bei jedem App-Start (per Spec).
- UI deaktiviert Submit-Button + zeigt Hard-Reset-Pfad, sobald `attemptsLeft <= 0`.
- Counter wird nur bei `WrongPinException` dekrementiert, nicht bei
  `MasterPubkeyMissingException` (das ist kein PIN-Fehler).

### (b) `MasterPubkeyMissingException` → `NeedsOnboarding`
- `tryRecover()` fängt explizit `is MasterPubkeyMissingException` und setzt
  `_gate.value = GateState.NeedsOnboarding`. User klickt dann "Onboarding
  starten", der vorhandene Keypair wird beim LSF-Validate weiterverwendet
  (kein `keys.create()` weil `getOrNull()` ihn jetzt zurückgibt) → Master
  wird persistiert → Ready.

### (c) `RelayClient.start()` nach Recovery
- In `tryRecover().onSuccess` mit `runCatching` umschlossen (idempotent).
- Failure-Logging via Timber, blockiert Ready-Transition nicht (weil der
  Boot-Path beim nächsten App-Start die WS wieder hochfährt).

## Concerns

1. **`refreshGate()` macht IMAP-IO ohne Loading-State** — wenn Mail-Login
   langsam ist, hängt das Gate beim ersten Bewerten kurz auf altem Wert
   (zuerst Ready, dann NeedsRecoveryPin). `tryMailBackupOrNull()` ist
   blocking (Sek-Bereich auf IMAPS-Verbindung). Idee für Follow-up: ein
   `Probing` GateState rendern (Spinner "Backup wird geprüft …").

2. **`NeedsBackupPin` blockiert nicht den Submit-Button** — `gate is Ready`
   ist false in dem State, der Submit-Button ist disabled. Das ist gewollt
   (User soll Entscheidung treffen), aber kein klares Affordance — die
   Sektion ist ja optional. Falls wir wollen, dass User die Bewertung
   schon abgibt und das Backup verschiebt: Submit auch in `NeedsBackupPin`
   freigeben.

3. **Konto-Wechsel ist UX-mäßig "billig"** — `skipRecoveryAndStartLsfLogin()`
   setzt nur den State. Der User landet bei `NeedsLsfLogin` und klickt den
   Login-Button erneut, aber die alte CAS-Session ist noch da. In der Praxis
   müsste man hier auch `casSession.logout()` callen, damit der WebLogin
   wirklich ein anderes Konto auswählen kann. Brief sagt aber nur "transitions
   to NeedsLsfLogin", also so gehalten. Follow-up empfohlen.

4. **`MailBackup.findBackup()` in `LsfOnboarding.tryMailBackupOrNull()`**
   schluckt Exceptions still per `runCatching{}.getOrNull()`. Das ist
   intentional (keine Mail-Credentials = keine Recovery = normaler Flow),
   aber bei echtem IMAP-Failure (Netz, Auth) sieht der User auch nichts —
   nur die `Timber.w` Zeile in `MailBackup.findBackup()` selbst loggt das.
