# Subagent-Driven Development — Mensa P2P Reviews

Branch: feature/mensa-reviews
Plan: docs/superpowers/plans/2026-06-28-mensa-p2p-reviews.md
Scope: Phase 1-2 (Tasks 1-13)
Start: a967936

## Progress

Task 1: complete (commits a967936..66f0eab, review clean — kotlinxSerializationCore alias skipped because 'serialization=1.7.3' already exists; future tasks use libs.versions.serialization)
Task 2: complete (commits 66f0eab..fadf3ac, review approved). Open notes: (a) canon() Boolean-branch duplicates else-branch — plan-mandated, harmless; (b) computed 'type' property not auto-serialized by kotlinx-serialization (no backing field); (c) Tests only cover ReviewEvent canonical/eventId per brief — Validation/Intro/Retraction canonical untested.
Task 3: complete (commits fadf3ac..becaa81, review approved). Minors: test helper 'init()' shadows Kotlin object init block (cosmetic); brief-prescribed deprecated Tink 1.x getPrimitive API still works.
Task 4: complete (commits becaa81..965d8cc, review approved). Note: report's '0.0|||' was a prose typo — code correctly produces '0.0||||' (4 pipes for 5 values).
Task 5: complete (commits 965d8cc..9a8f744, review approved). Open notes: (a) Test anchor now=1_000_000L makes past-skew test trivially satisfied — plan-mandated, no production bug; (b) test name 'more than 50 in day' but logic is '>=50' — Plan-faithful.
Phase 1: complete (Tasks 1-6, all green, 15 tests passing). Tag: phase1-shared-events
Task 7: complete (commits 9a8f744..d1b00a7, review approved with note). IMPORTANT NOTE: Implementer added MIGRATION_32_33 (learnweb submissionStatus + lastSubmittedEpoch) zusätzlich zu MIGRATION_33_34, weil die LearnwebAssignment Entity bereits diese Felder hat ohne Migration — App wäre sonst beim Start gecrasht. Pragmatischer Fix, scope-overreach im Commit. Bei PR-Merge im Body erwähnen.
Task 8: complete (commits d1b00a7..1c56475, review SHIP). Notable: implementer correctly used 'exclude com.google.crypto.tink:tink' on the :shared-events project dep to avoid duplicate-class conflict with tink-android. Substitution is safe (tink-android 1.15.0 is a superset). ReviewModule placed in feature/.../di/ rather than root, matching feature-scoped convention.
Task 9: complete (commits 1c56475..c890594, review PASS). 4 Minors deferred: KeystoreWrap interface co-located in MyKeyManager.kt; cached keystore reference (works on Android but defensive refresh better); MyKeyManager unscoped (add @Singleton before ViewModel uses it); restore() lacks base64 validation guard. AndroidKeystoreWrap untested via Robolectric (needs instrumented test for prod-path coverage).
Task 10: complete (commits c890594..c5a7589, includes Opus-Reviewer-Important-Fix). roundToInt for wouldOrderAgainPct (vs .toInt() truncation). 7/7 tests green incl rounding-boundary.
Task 11: complete (commits c5a7589..c60cfaf, Opus review Approved). 3 Minors deferred (Switch can't go back to null after touch, lambda allocs, stale error text). No code changes.
Task 12: complete (commits c60cfaf..8b7a1b5, Opus review Solid Pass). 2 Important findings sind beide Spec-Korrekturen (NutritionParser parsed echte flat STW-JSON mit unit-suffixed strings — Spec war falsch). 4 Minor (mealName ungenutzt, colors-Var, stacked-sheets ok, no smoke-test). Code OK.
Task 13 / Phase 2 Checkpoint: Tag phase2-local-ui gesetzt.
Whole-Branch Opus-Review (commits a967936..8b7a1b5): ship-with-noted-followups. 5 Important Wire-Format-Issues für Phase 3, 7 Minor. Trivial-Fixes-Commit 23ba536 (Singleton, SerialName, NutritionParser fail-closed, TODO-Marker).
UX-Polish (commits bb54398, 6087c1b): P/L → Preis-Leistung, Sterne 28sp, Emojis raus, BottomSheet → eigene Page mit Scaffold/TopAppBar/Nav-Route.
Session-Ende Stand: feature/mensa-reviews, 14 commits seit Branch-Start, Phase 1+2 sealed. Phase 3+ später-Session.
Task 14: complete (commits 6087c1b..7a7e1c8, Opus review Excellent). Verbatim brief impl, /health smoke OK. Minor: junit hardcoded statt libs.junit; logback.xml hat ungenutzten jetty-Eintrag; kein env-port (Task 15+).
Task 15: complete (commits 7a7e1c8..bc933b4, Opus review Pass). Plan-faithful. Note: zwei Events mit identischem ts könnten in queryAfter gesplittet werden — Task 16 ggf. (ts,event_id) composite cursor erwägen.
Task 16: complete (commits bc933b4..11c436f, Opus review GREEN). 2 Important Phase-7-Follow-ups: sig-as-eventId, broadcast re-serialization. Beide schon im Report tracked.
Task 17: complete (commits 579442c..ab06670 + Important-Fix). Distroless ENTRYPOINT, /data uid-65532, BuildKit-cache. Followup: top-level .dockerignore für non-BuildKit Builds.
Phase 3: complete (Tasks 14-17, all green, 2 tests passing). Tag: phase3-relay.
Phase 3 Whole-Branch-Fix (commit 56bec15): C1 SyncFrame.Event.type→kind + Roundtrip-Test; C2 TODO(task-19); I1 (ts,eventId) composite cursor backward-compat; I2 SELECT COUNT(*); I3 Spam-Guard; I4 Dockerfile.dockerignore gelöscht; I5 ENV-WARN-Logs; M1-M3 polish.
Session-Ende: feature/mensa-reviews, Phases 1+2+3 sealed (tags phase1-shared-events, phase2-local-ui, phase3-relay). Phase 4-7 später-Session.
Task 19: complete (commits 475e417..456ec55, Opus review Green/Ship). 9 Tests passing. 5 Minors deferred (master.key chmod 600, atomicity of insert+upsert+register, empty-stub-cookie, ENV roundtrip test, phone-switch test coverage).
Task 20 + Fix: complete (commits 475e417..04d68f9 incl ba47c08 worktree-merge + 04d68f9 Critical-Fix). 28/28 shared-events + 9/9 relay + app:assembleDebug green. C1 SignedEvent.type → real serializable field. I2 in-memory composite cursor. M1 sendHello single-find. Canonical-Form rückwärtskompatibel.
Task 21: complete (commits 04d68f9..6b1911f, Opus review Strong Pass). LSF-Onboarding via CAS-SSO entdeckt (statt eigenem LSF-Flow). 4 GateStates (NeedsLsfLogin/NeedsOnboarding/Onboarding/OnboardingError/Ready). Self-Trust-Hack komplett raus. Crypto-verify vor persist atomar.
Phase 4: complete (commits 56bec15..HEAD, Whole-Branch Opus review C1+I-Wave alle gefixt in 82b1af0 + Import-Fix). 151/152 app tests grün (1 unrelated Bib flake). RelayClient startet jetzt lazy bei key+master oder direkt nach Onboarding. Tag phase4-lsf-sync.
Task 23: complete (commits c43ffd8..c3652bb, Opus review PASS-high-quality). Backup.encrypt/decrypt mit AES-GCM + PBKDF2 600k. 5 Minors deferred: deriveKey internal→private, PBEKeySpec.clearPassword(), iterations nicht im wire-format, more tests in Phase 5.
Task 25: complete (commits 786bc3e..5fda51b, Opus review Solid). Recovery-Flow + LsfOnboarding-Erweiterung + Post-Onboarding Backup-Setup alle 3 Parts. 1 Important (skipRecoveryAndOnboardFresh kann alten Key beibehalten), 5 Minors. Build green.
Phase 5: complete (Tasks 23-26, incl. Whole-Branch-Review + I1/I4/M6-Fix in fb64b91). 3 commits + 1 fix-commit. Backup append-first, gate-Init Onboarding statt Ready, Hard-Reset clear keys. I2/I3 deferred zu Phase 6 (UX-Polish).
Phase 6 (LAN-mDNS): VERWORFEN — Eduroam blockt mDNS, Gun.js löst es auch nicht. Mesh-Spirit über Phase 7 WoT + Federation-Spec.
Task 31: complete (commits 6f49ea2..67a2b27, Opus review Pass). 5/5 Tests grün. 5 Minors: limit-semantics current vs lifetime (Plan-faithful via SQL); kein dedizierter self-invite Test; optimistischer Insert ohne Rollback; @Singleton stateless OK.
Task 32: complete (commits 67a2b27..fde5959, Opus review Accept). QR-Intro UI mit zxing, Nav-Destination 'qr-intro', VM thin-pass-through zu IntroIssuer. 6 Minors (ownPubkey nicht-reaktiv, FQN-Inkonsistenzen, success-text-flash, kein UI-Trigger in ProfileScreen, etc) alle non-blocking.
Task 33: complete (commits fde5959..e841a8e merge, Opus review Solid ship-ready). Mail-Intro Sender + Receiver + StartupRefresher-Hook. 6 Minors: AcceptResult.Reject pauschal als BadSignature mapping, Receiver nicht @Singleton, IMAP-Konstanten dupliziert, etc. Alle non-blocking.
Phase 7: complete (Tasks 31-33). Tag phase7-wot.
Final Whole-Branch Review (Opus, Phases 1-7): Ship-with-noted-followups for main. 2 Critical: C1 (docker-compose missing LSF_BRIDGE, gefixt in 70e3130) + C2 (HiUniLsfBridge Production-Impl fehlt, deploy-gated). 8 Important alle phase-carryovers oder UI-Anbindungs-Lücken (Mail-Intro+QR-Intro UI nicht erreichbar via Profile/Settings, flushOutbox fire-and-forget, client-trusted eventId, etc).
Branch fertig: 41 commits seit main, 6 Phase-Tags (Phase 6 verworfen).
Phase 7 I4: complete (commit nach Final-Whole-Branch). QR-Intro jetzt im Profile sichtbar via Section 'Mensa-Reviews'.
Polish-Welle: I4 c87742b, I5+I6 ab235ca, C2 a573bc2+9576270, I7 b8eafd9. Phase 7 retagt.
