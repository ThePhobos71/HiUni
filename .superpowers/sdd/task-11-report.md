# Task 11 Report: ReviewBottomSheet + ReviewViewModel

## Files changed
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewViewModel.kt` (created)
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewBottomSheet.kt` (created)

## Build outcome
`./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL in 7s** (no warnings related to new files).

## Self-review

### (a) Send button correctly disabled while `overall == 0` OR `wouldOrderAgain == null`
Confirmed. The Button uses `enabled = draft.overall > 0 && draft.wouldOrderAgain != null`.
- Initial draft has `overall = 0` and `wouldOrderAgain = null` → button disabled.
- User taps a star → `overall > 0` becomes true, but `wouldOrderAgain` still null → still disabled.
- User toggles Switch → `wouldOrderAgain` becomes non-null (either `true` from on, `false` from off; `Switch.onCheckedChange { draft = draft.copy(wouldOrderAgain = it) }` always sets a non-null value). At that point both predicates pass → button enabled.
- Note: once the Switch is touched, `wouldOrderAgain` cannot return to `null` from the UI, so re-disabling is not possible after first interaction. That matches the brief.

### (b) BottomSheet auto-closes on `Done` state via LaunchedEffect
Confirmed. `LaunchedEffect(state) { if (state is SubmitState.Done) onDismiss() }` runs whenever `state` changes. When the repo's `submitReview` succeeds, `_state.value = SubmitState.Done` flips the flow; the composable recomposes with the new state, the `LaunchedEffect` keys on the new value and invokes `onDismiss()`, dismissing the sheet.

## Concerns
- The brief inverts the up/down chevrons (`▾ Mehr Details` when open, `▸ Mehr Details` when closed). `▾` conventionally means "expanded down". Implemented verbatim per brief; cosmetic only.
- Once a user toggles the Switch, `wouldOrderAgain` cannot go back to `null` from this UI — irrelevant for Send validation but worth noting if a "reset" gesture is wanted later.
- No Compose UI tests were written (brief: not required).
- No `@Preview` was added; not requested.

## Commit
`c60cfaf` on branch `feature/mensa-reviews` — `feat(reviews): ReviewBottomSheet + ReviewViewModel` (no Co-Authored-By trailer).
