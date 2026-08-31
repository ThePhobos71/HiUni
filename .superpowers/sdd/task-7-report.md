# Task 7 Report — Room Entities + DAOs + Migration

## Files Created / Modified

**Created (entities):**
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/ReviewEventEntity.kt`
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/TrustEntity.kt`
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/OutboxEntity.kt`
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/MyKeyEntity.kt`
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/PeerCursorEntity.kt`
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/MutedPubkeyEntity.kt`

**Created (DAOs):**
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/ReviewDao.kt`
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/TrustDao.kt`
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/OutboxDao.kt`
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/MyKeyDao.kt`
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/PeerCursorDao.kt`
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/MutedPubkeyDao.kt`

**Created (test):**
- `app/src/test/java/de/transio/hiuni/feature/mensa/review/data/ReviewDaoTest.kt`

**Created (schemas):**
- `app/schemas/de.transio.hiuni.core.database.AppDatabase/33.json`
- `app/schemas/de.transio.hiuni.core.database.AppDatabase/34.json`

**Modified:**
- `app/src/main/java/de/transio/hiuni/core/database/AppDatabase.kt` — version 33→34, 6 new entity imports + abstract DAO methods
- `app/src/main/java/de/transio/hiuni/core/database/Migrations.kt` — added MIGRATION_33_34, appended to ALL_MIGRATIONS
- `app/build.gradle.kts` — added `testImplementation(libs.androidx.test.core)`
- `gradle/libs.versions.toml` — added `androidxTestCore = "1.6.1"` and `androidx-test-core` library entry

## Migration SQL Summary (MIGRATION_33_34)

- `review_events` — 13 columns, TEXT PK eventId, wouldOrderAgain/retracted INTEGER, nullable taste/portion/value/satiation
- `index_review_events_recipeHash` — on review_events(recipeHash)
- `index_review_events_pubkey` — on review_events(pubkey)
- `index_review_events_ts` — on review_events(ts)
- `trust` — 5 columns, TEXT PK pubkey
- `outbox` — 4 columns, TEXT PK eventId, lastAttempt nullable
- `my_keys` — 3 columns, TEXT PK pubkey, secretKeyEncrypted BLOB
- `peer_cursor` — 2 columns, TEXT PK peerId
- `muted_pubkeys` — 2 columns, TEXT PK pubkey

## `:app:assembleDebug` Outcome

BUILD SUCCESSFUL — KSP generated Room impl without errors; schema 34.json emitted.
Note: encountered a transient KSP incremental-cache daemon crash on the first `--rerun-tasks` run (AssertionError in `updateCachesAndOutputs`); second run was clean. This is a known KSP 1.0.27 flakiness with `--rerun-tasks` + daemon restart, not caused by this change.

## `ReviewDaoTest` Outcome (Robolectric)

BUILD SUCCESSFUL — `aggregatable filters untrusted pubkeys` passed: inserted pkA (trusted, depth=0) and pkB (untrusted), verified `aggregatableForRecipe("r1")` returns only `[pkA]`.

`MigrationListTest` also passes — chain is unbroken 1→34.

## Self-Review

**(a) DB version 33→34 consistency:**
- `AppDatabase.kt`: `version = 34` ✓
- `MIGRATION_33_34`: `Migration(33, 34)` ✓
- `ALL_MIGRATIONS`: MIGRATION_33_34 appended ✓
- `MigrationListTest.all_migrations_form_unbroken_chain_to_db_version`: GREEN ✓

**(b) Schema JSON for v34:**
- Emitted at `app/schemas/de.transio.hiuni.core.database.AppDatabase/34.json` ✓
- Contains version=34, all 18 tables including 6 new review tables ✓

## Concerns

1. **`androidx.test.core` not listed in catalog** — added `androidxTestCore = "1.6.1"` manually; version is compatible with espresso 3.6.1 / robolectric 4.13 but not pinned via BOM. If the catalog is later cleaned up this may drift.
2. **KSP incremental crash** — transient; not caused by this change; re-running without `--rerun-tasks` is stable.
3. **`MyKeyEntity.secretKeyEncrypted: ByteArray`** — requires manual `equals`/`hashCode`; implemented. Room stores BLOB natively.
4. **`DatabaseModule` not yet wired for new DAOs** — per brief, this is intentionally deferred to Task 8 (ReviewModule). `AppDatabase` abstract methods are present; Hilt wiring comes next.
