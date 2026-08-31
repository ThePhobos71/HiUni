# Task 2 Report: Event-Datenklassen + Canonical-Form

## Files Changed

- `shared-events/src/main/kotlin/de/transio/hiuni/events/Canonical.kt` (new)
- `shared-events/src/main/kotlin/de/transio/hiuni/events/Events.kt` (new)
- `shared-events/src/test/kotlin/de/transio/hiuni/events/CanonicalTest.kt` (new)

## TDD Evidence

### RED (Step 2)

```
./gradlew :shared-events:test
...
e: .../CanonicalTest.kt:9:17 Unresolved reference 'ReviewEvent'.
e: .../CanonicalTest.kt:23:17 Unresolved reference 'ReviewEvent'.
e: .../CanonicalTest.kt:24:24 Unresolved reference 'sha256'.
BUILD FAILED in 888ms
```

### GREEN (Step 4)

```
./gradlew :shared-events:test
...
BUILD SUCCESSFUL in 1s
4 actionable tasks: 4 executed
```

Both tests passed: `review canonical concatenates fields with pipe, null becomes empty` and `eventId is base64 sha256 of canonical`.

## Self-Review Findings

- `ReviewEvent.canonical()` field order: `type|schemaVersion|recipeHash|overall|wouldOrderAgain|taste|portion|value|satiation|ts|pubkey`. Matches expected `"review|1|abc|4|true|5||3||1719500000|pk"` exactly.
- `null.canon()` → `""`, `Boolean.canon()` → `"true"`/`"false"`, integers/strings → `.toString()`. No deviation from spec.
- `ValidationEvent.pubkey_` trailing-underscore preserved as specified; `pubkey` accessor delegates to it correctly.
- `IntroEvent.pubkey` delegates to `inviter`, not a separate field.
- `eventId()` is a default method on `SignedEvent` using `sha256(canonical()).toBase64()` — no per-class override needed.
- `@Serializable` on all four data classes; `sealed interface` is not annotated (correct for kotlinx.serialization polymorphism — serializer registration happens later).

## Concerns

- None. The `@Serializable` annotations on data classes without a `@Serializable` sealed interface will require explicit polymorphic registration later when JSON encode/decode is added. This is expected and noted in the brief ("Serialization-Name-Annotation könnten wir später hinzufügen wenn nötig").

## Commit

`fadf3ac` — `feat(reviews): Event-Datenklassen + Canonical-Form mit Tests`
