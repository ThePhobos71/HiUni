### Task 8: Hilt-Bindings + `:app` konsumiert `:shared-events`

**Files:**
- Modify: `app/build.gradle.kts`
- Create/Modify: `app/src/main/java/de/transio/hiuni/feature/mensa/review/di/ReviewModule.kt`

**Interfaces:**
- Produces: Hilt-Provides für `ReviewDao`, `TrustDao`, `OutboxDao`, `MyKeyDao`, `PeerCursorDao`, `MutedPubkeyDao` und später `ReviewRepository`.

- [ ] **Step 1:** `app/build.gradle.kts` `dependencies { }` erweitern:

```kotlin
implementation(project(":shared-events"))
implementation(libs.google.tink.android)
```

- [ ] **Step 2:** `ReviewModule.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.database.AppDatabase
import de.transio.hiuni.feature.mensa.review.data.*

@Module
@InstallIn(SingletonComponent::class)
object ReviewModule {
    @Provides fun reviewDao(db: AppDatabase): ReviewDao = db.reviewDao()
    @Provides fun trustDao(db: AppDatabase): TrustDao = db.trustDao()
    @Provides fun outboxDao(db: AppDatabase): OutboxDao = db.outboxDao()
    @Provides fun myKeyDao(db: AppDatabase): MyKeyDao = db.myKeyDao()
    @Provides fun peerCursorDao(db: AppDatabase): PeerCursorDao = db.peerCursorDao()
    @Provides fun mutedPubkeyDao(db: AppDatabase): MutedPubkeyDao = db.mutedPubkeyDao()
}
```

- [ ] **Step 3:** `./gradlew :app:assembleDebug`

- [ ] **Step 4:** Commit:

```bash
git add app/build.gradle.kts app/src/main/java/de/transio/hiuni/feature/mensa/review/di/
git commit -m "feat(reviews): Hilt-Module + :app konsumiert :shared-events"
```

