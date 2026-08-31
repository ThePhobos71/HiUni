# Task 33 Report — Mail-Intro (Sender + Receiver + Scan-Hook)

## Files created
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/MailIntro.kt`
  — `MailIntroSender`, `MailIntroReceiver`, `ImportResult`, `MAIL_INTRO_SUBJECT` const
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/MailIntroScanner.kt`
  — periodic `scanOnce()` runner

## Files modified
- `app/src/main/java/de/transio/hiuni/core/startup/StartupRefresher.kt`
  — wires `MailIntroScanner` into the existing fire-and-forget cold-start trigger

## Files NOT touched (per brief)
- `:hiuni-relay/`, `:shared-events/`
- `QrIntro.kt`, `QrIntroScreen.kt` (Task 32 territory)
- `MailBackup.kt` (Task 24 sealed)

## SmtpClient API contract
The existing API is:
```kotlin
suspend fun send(
    to: List<String>,
    cc: List<String> = emptyList(),
    bcc: List<String> = emptyList(),
    subject: String,
    bodyPlain: String,
    fromDisplayName: String? = null,
    host: String = DEFAULT_SMTP_HOST,
    port: Int = DEFAULT_SMTP_PORT,
    inReplyTo: String? = null,
    references: String? = null,
): SendResult  // sealed: Success | Failure(Throwable)
```

Adapted: passed `to = listOf(toAddress)` and `bodyPlain = body`. Failure-branch throws so `runCatching` outer wrap turns it into `Result.failure`.

## StartupRefresher integration
**Real hook** — not TODO. The existing `StartupRefresher.trigger()` already follows the "launch fire-and-forget coroutine + runCatching + Timber.w" pattern; I added a fourth `scope.launch { mailIntroScanner.scanOnce() }` block in the same shape. The scanner is credential-gated internally (no-op if `CredentialsManager.hasCredentials()` is false), so onboarding flows aren't impacted.

The brief mentioned "every 24h or on each app-start" — I chose **each cold-start** because that matches the existing `triggered` AtomicBoolean idempotency in StartupRefresher, and the app is typically opened more than once per day. No new periodic scheduler / WorkManager needed.

## Build outcome
`./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL in 37s**. Only pre-existing deprecation warnings for `Icons.Outlined.MenuBook` / `DirectionsRun` etc. — none of mine.

## Self-review

**(a) Subject is exactly `HIUNI-INTRO-v1`?**
Yes — one `const val MAIL_INTRO_SUBJECT = "HIUNI-INTRO-v1"` used everywhere: SMTP send subject, body line 1, parse magic-header check, IMAP `SubjectTerm`.

**(b) Parse roundtrips correctly?**
- `formatBody` writes 5 lines: magic header + `invitee:`, `inviter:`, `ts:`, `sig:`
- `parse` requires first line equals magic, then splits remaining lines on first `:`, builds map, extracts the 4 keys, returns `null` on any missing field or bad ts
- Pattern mirrors `MailBackup.parseBody` exactly

**(c) Import respects depth ≤ 2 + bad-signature rejection?**
- Step 1: `validatorFactory.create().accept(event)` → for `IntroEvent`, `EventValidator` calls `IntroEvent.verify()` (Ed25519 over canonical with `inviter` pubkey). Rejected reason "invalid signature" → `ImportResult.BadSignature`.
- Step 2: `trustDao.find(event.inviter)` — if null → `InviterNotTrusted`.
- Step 3: `newDepth = inviterRow.depth + 1; if (newDepth > 2) return DepthTooDeep`. Matches the inviter rule from `IntroIssuer` (which already gates at `mine.depth >= 2`).
- Step 4: Idempotency — if `invitee` already in trust table → `AlreadyTrusted` (no overwrite).

## Concerns
1. **Validator reuse**: `MailIntroReceiver.import` runs the full `EventValidator` including spam-limit/`countSince`. For Intro events that uses `reviewDao.countSince` on `e.pubkey = inviter` — the `EventValidator.kt` has a TODO comment noting this scope is review-only. Not my concern to fix in this task, but if intros ever get spammy the limit currently only watches review-event counts.

2. **Scan-Hook timing**: scanner runs on every cold-start, no 24h debounce. If the user kills + restarts the app 50× in a minute (e.g. debugging), that's 50 IMAP connections. Existing repos in StartupRefresher have the same issue (the `triggered` AtomicBoolean only blocks intra-process re-trigger, not re-launches). Acceptable for now; if it bites later, a `Settings.lastIntroScanEpoch` could gate it like `LoginSyncOrchestrator` does.

3. **Mail not deleted after import** — by design (user gets audit trail). Brief said "Optionally: delete imported intro mails after success — but ONLY if the existing pattern in StartupRefresher does similar (don't introduce a new pattern)". StartupRefresher has no mail-delete pattern → I only set `\Seen` flag.

4. **No `MailService` abstraction**: brief mentioned `mail: MailService` in the issuer DI signature, but no such class exists in the codebase — the existing layer is `SmtpClient` + `ImapClient` directly. I wired against `SmtpClient` directly, matching the project's actual architecture.

## Commit
`c69d865 feat(reviews): Mail-Intro (Sender + Receiver + Mail-Scan-Hook)`

## Worktree path
`/Users/kjell/AndroidStudioProjects/UniHi/.claude/worktrees/agent-a9f1ce1c8f7aecc45`
Branch: `worktree-agent-a9f1ce1c8f7aecc45` (off `feature/mensa-reviews @ 67a2b27`)
