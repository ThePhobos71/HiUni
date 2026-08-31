### Task 10: ReviewRepository — Aggregation + Senden

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/ReviewRepository.kt`
- Create: `app/src/test/java/de/transio/hiuni/feature/mensa/review/data/ReviewRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `class ReviewRepository(reviewDao, trustDao, outboxDao, mutedDao, keys)`
  - `fun aggregateFor(recipeHash: String): Flow<RecipeAggregate>`
  - `suspend fun submitReview(recipeHash, overall, wouldOrderAgain, taste?, portion?, value?, satiation?): Result<ReviewEvent>`
  - `suspend fun retract(eventId: String): Result<RetractionEvent>`
  - `suspend fun mute(pubkey: String)`
  - `data class RecipeAggregate(recipeHash, overall: Float?, overallCount: Int, wouldOrderAgainPct: Int?, byDimension: Map<Dimension, DimensionStat>)`

- [ ] **Step 1: Test** (kompakt — Aggregat-Berechnung + Submit):

```kotlin
package de.transio.hiuni.feature.mensa.review.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.transio.hiuni.core.database.AppDatabase
import de.transio.hiuni.events.Ed25519
import de.transio.hiuni.feature.mensa.review.trust.MyKeyManager
import de.transio.hiuni.feature.mensa.review.trust.KeystoreWrap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReviewRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ReviewRepository
    private lateinit var keys: MyKeyManager

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        keys = MyKeyManager(db.myKeyDao(), object : KeystoreWrap {
            override fun wrap(s: ByteArray) = s; override fun unwrap(s: ByteArray) = s
        })
        repo = ReviewRepository(db.reviewDao(), db.trustDao(), db.outboxDao(), db.mutedPubkeyDao(), keys)
        val kp = keys.create()
        // Self-trust für Submit-Tests
        db.trustDao().insert(TrustEntity(java.util.Base64.getEncoder().encodeToString(kp.publicKey), "relay", 0, 0L, ""))
    }
    @After fun tearDown() = db.close()

    @Test fun `submitReview persists signed event with own pubkey`() = runBlocking {
        val r = repo.submitReview("hash1", overall = 4, wouldOrderAgain = true).getOrThrow()
        val agg = repo.aggregateFor("hash1").first()
        Assert.assertEquals(1, agg.overallCount)
        Assert.assertEquals(4.0f, agg.overall ?: 0f, 0.01f)
        Assert.assertEquals(100, agg.wouldOrderAgainPct)
    }
}
```

- [ ] **Step 2:** FAIL.

- [ ] **Step 3:** `ReviewRepository.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.data

import de.transio.hiuni.events.*
import de.transio.hiuni.feature.mensa.review.trust.MyKeyManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

enum class Dimension { TASTE, PORTION, VALUE, SATIATION }
data class DimensionStat(val avg: Float, val n: Int)
data class RecipeAggregate(
    val recipeHash: String,
    val overall: Float?,
    val overallCount: Int,
    val wouldOrderAgainPct: Int?,
    val byDimension: Map<Dimension, DimensionStat>,
)

class ReviewRepository @Inject constructor(
    private val reviews: ReviewDao,
    private val trust: TrustDao,
    private val outbox: OutboxDao,
    private val muted: MutedPubkeyDao,
    private val keys: MyKeyManager,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun aggregateFor(recipeHash: String): Flow<RecipeAggregate> =
        reviews.aggregatableForRecipe(recipeHash).map { rows ->
            if (rows.isEmpty()) RecipeAggregate(recipeHash, null, 0, null, emptyMap())
            else {
                val overall = rows.map { it.overall }.average().toFloat()
                val repeatPct = (100.0 * rows.count { it.wouldOrderAgain } / rows.size).toInt()
                fun dim(get: (ReviewEventEntity) -> Int?): DimensionStat? {
                    val vals = rows.mapNotNull(get)
                    return if (vals.isEmpty()) null else DimensionStat(vals.average().toFloat(), vals.size)
                }
                val byDim = listOfNotNull(
                    dim { it.taste }?.let { Dimension.TASTE to it },
                    dim { it.portion }?.let { Dimension.PORTION to it },
                    dim { it.value }?.let { Dimension.VALUE to it },
                    dim { it.satiation }?.let { Dimension.SATIATION to it },
                ).toMap()
                RecipeAggregate(recipeHash, overall, rows.size, repeatPct, byDim)
            }
        }

    suspend fun submitReview(
        recipeHash: String, overall: Int, wouldOrderAgain: Boolean,
        taste: Int? = null, portion: Int? = null, value: Int? = null, satiation: Int? = null,
    ): Result<ReviewEvent> = runCatching {
        val kp = keys.getOrNull() ?: error("no key — onboarding required")
        val pub = java.util.Base64.getEncoder().encodeToString(kp.publicKey)
        val event = ReviewEvent(1, recipeHash, overall, wouldOrderAgain,
            taste, portion, value, satiation,
            System.currentTimeMillis(), pub, "").signWith(kp)
        val entity = ReviewEventEntity(event.eventId(), recipeHash, pub, 1,
            overall, wouldOrderAgain, taste, portion, value, satiation,
            event.ts, event.sig, false)
        reviews.insert(entity)
        outbox.insert(OutboxEntity(event.eventId(), json.encodeToString(event)))
        event
    }

    suspend fun retract(eventId: String): Result<RetractionEvent> = runCatching {
        val kp = keys.getOrNull() ?: error("no key")
        val pub = java.util.Base64.getEncoder().encodeToString(kp.publicKey)
        val ev = RetractionEvent(eventId, System.currentTimeMillis(), pub, "").signWith(kp)
        reviews.markRetracted(eventId)
        outbox.insert(OutboxEntity(ev.eventId(), json.encodeToString(ev)))
        ev
    }

    suspend fun mute(pubkey: String) =
        muted.insert(MutedPubkeyEntity(pubkey, System.currentTimeMillis()))
}
```

- [ ] **Step 4:** Test PASS.

- [ ] **Step 5:** Commit:

```bash
git add app/src/main/java/de/transio/hiuni/feature/mensa/review/data/ReviewRepository.kt app/src/test/java/de/transio/hiuni/feature/mensa/review/data/
git commit -m "feat(reviews): ReviewRepository mit Aggregation + Submit + Retract"
```

