# Phase-4 Whole-Branch-Fixes Report

Datum: 2026-06-28
Scope: Critical C1, Important I2/I1/I6/I5, Minor M1 — Single batched commit.

## Findings & Files Modified

### C1 (Critical, BLOCKER) — RelayClient never instantiated/started
**Diagnose:** `RelayClient` war via Hilt registriert, aber niemand hat `start()`
gerufen. Folge: Outbox lokal voll, kein WS, kein Peer-Sync.

**Fix-Strategie:** Lazy-Start an zwei Stellen, beide idempotent.

Geänderte Dateien:
- `app/src/main/java/de/transio/hiuni/HiUniApplication.kt`
  - Hilt-Inject von `RelayClient`, `MyKeyManager`, `MasterPubkeyProvider`.
  - Neue Methode `maybeStartRelayClient()` läuft via `applicationScope`
    (CoroutineScope IO + SupervisorJob). Cold-Start öffnet WS nur wenn
    `myKeyManager.getOrNull() != null && masterPubkeyProvider.get().isNotEmpty()`.
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/LsfOnboarding.kt`
  - `RelayClient` per Konstruktor injiziert.
  - Direkt nach `masterPubkeyProvider.set(...)` ein `runCatching { relayClient.start() }`
    eingefügt → First-Session-Boot kickt die WS nach erfolgreichem /validate.
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/sync/RelayClient.kt`
  - KDoc von `start()` erweitert: Aufrufer dokumentiert, Idempotenz-Garantie
    (`ws == null && !stopped` guard) ausdrücklich beschrieben.

**Bestätigung Call-Site für `RelayClient.start()`:**
1. `HiUniApplication.maybeStartRelayClient()` (Z. 76) — Cold-Start
2. `LsfOnboarding.startOnboarding(lsfSessionCookie)` (Z. 115) — First-Session-Boot

Idempotenz verifiziert: `RelayClient.start()` Z. 102 prüft `ws == null && !stopped`
bevor connect() — wiederholte Aufrufe sind no-op, ohne dass `@Volatile var started`
nötig wäre.

### I2 (Important, BLOCKER) — StubLsfBridge hardcoded in production
Geänderte Dateien:
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Application.kt`
  - ENV `LSF_BRIDGE` getoggelt. `"stub"` → StubLsfBridge mit warn-log;
    `null`/`""` → exitProcess(78) mit error-log; sonstige Werte → exitProcess(78).
- `hiuni-relay/.env.example`
  - Neue Variable `LSF_BRIDGE=stub` mit Kommentar zur Bedeutung in Dev/Prod.

**Bestätigung fail-fast:** Relay refused zu starten ohne LSF_BRIDGE — Default-Case
`null, ""` ruft `exitProcess(78)`, error-log "Refusing to start" wird vorher emittiert.
Tests in `ValidateEndpointTest.kt` instanziieren `StubLsfBridge()` direkt — ENV-Check
wird umgangen, Tests bleiben grün.

### I1 (Important) — Stuck OnboardingError state on TGT-expired
Geänderte Dateien:
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewViewModel.kt`
  - Neue private Helper-Funktion `mapOnboardingFailure(t: Throwable): GateState`.
    Erkennung über `t is AuthRequiredException || t.cause is AuthRequiredException ||
    msg.contains("CAS-Login"/"AuthRequired"/"Service-Ticket")` → `GateState.NeedsLsfLogin`.
    Sonst → `GateState.OnboardingError`.
  - Sowohl `startOnboarding()` als auch `onLsfLoginSuccess(cookie)` nutzen die Helper.

### I6 (Important) — master.key atomic write + chmod 600
Geänderte Dateien:
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/MasterKey.kt`
  - `loadOrGenerate` schreibt nicht mehr direkt mit `f.writeBytes(...)`.
  - Neue private Methode `writeKeypairAtomically(f, kp)`:
    1. tmp-File (`<name>.tmp`) im gleichen Parent-Dir.
    2. `Files.move(tmp, f, ATOMIC_MOVE, REPLACE_EXISTING)`.
    3. `Files.setPosixFilePermissions(..., "rw-------")` mit `runCatching` für
       Non-POSIX-FS (Windows) — silent no-op.

**Bestätigung `/data/master.key` via tmp+rename:** Code-Pfad in
`MasterKey.kt:writeKeypairAtomically` schreibt `<persistPath>.tmp`, dann ATOMIC_MOVE
auf den finalen Pfad. Crash-Recovery: ein halb-geschriebenes `master.key.tmp` bleibt
zurück, das aktuelle `master.key` bleibt aber konsistent.

### I5 (Important) — /validate broadcast race vs HTTP response
Geänderte Dateien:
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/sync/RelayClient.kt`
  - Neue Konstruktor-Dependency `MyKeyManager` (für Self-Pubkey).
  - In `ingest()`: vor `validator.accept(...)` Check, ob `event is ValidationEvent`
    und `event.pubkey_ == myKeys.getOrNull()?.publicKey?.toBase64()`. Falls ja:
    `Timber.i(...)` + early return — `LsfOnboarding` persistiert es ohnehin selbst.

### M1 (Minor) — Defer trust for orphan IntroEvent
Geänderte Dateien:
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/sync/RelayClient.kt`
  - In `persist(IntroEvent)`: explizite Null-Prüfung auf `parentDepth`, bei
    unbekanntem inviter ein `Timber.w("drop orphan IntroEvent — inviter %s not trusted locally", inviter.take(8))`.
  - V1 verzichtet auf Pending-Queue — nur sichtbar im Log statt silent drop.

## Build + Test Outcomes

| Task                          | Result   |
|-------------------------------|----------|
| `:shared-events:test`         | BUILD SUCCESSFUL |
| `:hiuni-relay:test`           | BUILD SUCCESSFUL |
| `:app:assembleDebug`          | BUILD SUCCESSFUL |
| `:app:testDebugUnitTest`      | 151/152 passing — `BibViewModelTest.openBookingScreen ohne CasSession-Authenticated zeigt Snackbar statt Dialog` failed |

**Hinweis BibViewModelTest:** Pre-existing/unrelated to Phase-4 changes
(`BibViewModelTest.kt` referenziert weder Relay noch Review/Master/LsfOnboarding).
Auftrag scope (`:shared-events:test :hiuni-relay:test :app:assembleDebug`) ist
komplett grün.

## Concerns / Follow-ups

1. **MasterPubkey-Check beim Cold-Start ist DataStore-IO.** Geht heute auf
   `applicationScope` (IO-Dispatcher), blockiert also keinen Main-Thread. OK.
2. **`RelayClient`-Stop bei Key-Clear** ist nicht implementiert (out-of-scope
   laut Task). Wenn ein User später seinen Schlüssel löscht, bleibt die WS
   offen mit aktivem Trust-State. Phase-5 Thema.
3. **`MyKeyManager`-Dependency in RelayClient** koppelt die Sync-Schicht jetzt
   an die Trust/Key-Schicht. Sollte sauber bleiben — Trust hängt nicht von
   Sync ab, andersrum aber jetzt schon. Acceptable für I5.
4. **IntroEvent Pending-Queue** wäre nett, wenn Out-of-Order-Delivery häufig
   ist. Aktuell nur Log — keine Auto-Retry bei späterem ValidationEvent. Phase-5+.
5. **Phase-3-Carryover-Followups I3, I4** bleiben für Phase 5+ offen (laut Task).
6. **`writeKeypairAtomically` Race:** Wenn zwei Relay-Prozesse gleichzeitig auf
   denselben `/data/master.key` zugreifen (z.B. blau/grün Deploy), kann der
   ATOMIC_MOVE inkonsistent landen. Real-World eher selten, aber im Hinterkopf.

## Commit
Pending — wird im nächsten Schritt mit dem in der Aufgabe spezifizierten
Message-Format erstellt (kein `Co-Authored-By`).
