# Phase-5 Whole-Branch-Findings — Fix-Report

## I1 (Important — data-loss prevention) — MailBackup append-first

**File:** `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/MailBackup.kt`

**Change:** Reihenfolge in `ensureBackup` umgekehrt — vorher `search → setFlag(DELETED) → expunge() → appendMessages()`, jetzt `search → appendMessages() → setFlag(DELETED) → expunge()`. Falls der `appendMessages`-Call mitten im Flow scheitert (network-drop, MIME-reject, IMAP-timeout), bleibt der alte Backup-Draft erhalten — kein lautloser Recovery-Verlust mehr.

**Append-first Ordering (bestätigt, neue Zeilen-Nummern):**

- Zeile 67: `val existing = folder.search(SubjectTerm(SUBJECT))` — alte Drafts erfasst, aber NICHT angefasst
- Zeile 69–77: `MimeMessage(session)` gebaut
- Zeile 78: `folder.appendMessages(arrayOf(msg))` — **APPEND-FIRST**, wirft Exception falls fehlschlägt → alte bleiben
- Zeile 83: `existing.forEach { it.setFlag(Flags.Flag.DELETED, true) }` — **DELETE-AFTER**
- Zeile 84: `folder.expunge()` — **EXPUNGE-AFTER**

**Transient state:** Zwischen Append (Z. 78) und Expunge (Z. 84) liegen kurz zwei Backups im Drafts-Folder. `findBackup()` (unverändert, Z. 108) löst das via `maxByOrNull(receivedDate)` korrekt zugunsten des neuen.

**Edge case:** Wenn Z. 78 success, aber Z. 83/84 fail (z.B. expunge-fail) → 2 Backups bleiben. Nächster `ensureBackup`-Call macht den Cleanup. Acceptable — keine Datenverlust-Gefahr mehr.

## I4 (Important — Submit-button flash race) — Gate fail-closed

**File:** `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewViewModel.kt`

**Change:** `_gate.value = GateState.Onboarding` als initialer Wert statt `GateState.Ready` (Zeile 85–88). Submit-Button (`gate is GateState.Ready`-Check) ist während des initialen 1-2s IMAP-Probes via `refreshGate()` → `onboarding.tryMailBackupOrNull()` jetzt fail-closed disabled.

**UI-Rendering geprüft:** `ReviewSubmitScreen.kt:162-164` rendert für `GateState.Onboarding` bereits `Text("Onboarding läuft …", style = MaterialTheme.typography.bodySmall)`. Keine zusätzliche Anpassung nötig — Wording ist während des initialen Gate-Probes nicht ganz präzise ("Gate-Check läuft …" wäre semantisch ehrlicher), aber kein leerer Bildschirm und kein Phantom-Submit. Falls cosmetisches Polish gewünscht → Phase 6.

## M6 (Minor — Hard-Reset Semantic-Gap, Task-25 Carry-Over) — keys.clear() vor fresh-onboard

**File:** `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewViewModel.kt`

**Change:** `skipRecoveryAndOnboardFresh()` (Zeile 192–202) wrappt jetzt in `viewModelScope.launch { keys.clear(); _gate.value = GateState.NeedsOnboarding }`. Garantiert, dass ein nachfolgendes `LsfOnboarding.startOnboarding()` (das intern `keys.getOrNull() ?: keys.create()` macht) wirklich einen frischen Keypair erzeugt — auch wenn der Caller-Path mal von einem teils-restored Zustand käme. `MyKeyManager.clear()` ist `suspend` (siehe `MyKeyManager.kt:40`), daher der Launch-Wrap.

## Build outcome

`./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL in 8s`. Keine Warnungen außer den üblichen Hilt/KSP-Tasks.

## Concerns

- **I4 cosmetic:** Der initiale Gate-Probe rendert `"Onboarding läuft …"` — das ist während des ersten 1-2s technisch nicht "Onboarding" sondern "Gate-Check". User wird das in der Praxis kaum sehen (1-2s + die Strings sind unauffällig bodySmall), aber ein dedizierter `GateState.Probing`-State wäre semantisch sauberer. Aufgehoben für Phase 6.
- **I1 transient 2-backup-Zustand:** Falls Z. 83/84 zwischen Append und Expunge fail, bleibt der alte Draft als Müll bis zum nächsten Backup-Setup. Kein Datenverlust, nur Mailbox-clutter. Acceptable.
- **I2/I3 nicht angefasst:** Redundant-recovery-prompt und blocking-IMAP in `refreshGate` — Refactor-Followups für Phase 6, kein Datenverlust-Risiko.

## Phase-5 Status

3/3 Findings adressiert, Build grün, ein Commit.
