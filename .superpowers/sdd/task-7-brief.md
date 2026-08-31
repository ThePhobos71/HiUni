### Task 7: Room-Entities + DAOs

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/ReviewEventEntity.kt`
- Create: gleichnamige Files für `TrustEntity`, `OutboxEntity`, `MyKeyEntity`, `PeerCursorEntity`, `MutedPubkeyEntity`
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/ReviewDao.kt` + DAOs für jede Entity
- Modify: `app/src/main/java/de/transio/hiuni/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/de/transio/hiuni/core/database/Migrations.kt`

**Interfaces:**
- Produces: 6 Entities + 6 DAOs, `AppDatabase.reviewDao(): ReviewDao` (analog für andere DAOs).

- [ ] **Step 1:** Vor dem Anlegen prüfen, welche Version `AppDatabase` aktuell hat:

```bash
grep -n "version" app/src/main/java/de/transio/hiuni/core/database/AppDatabase.kt | head -5
```

Notieren als `CURRENT_DB_VERSION`. Neuer Wert ist `CURRENT_DB_VERSION + 1` (ab hier `NEW_VERSION` genannt).

- [ ] **Step 2:** Entities erstellen, z.B. `ReviewEventEntity.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_events",
    indices = [Index("recipeHash"), Index("pubkey"), Index("ts")]
)
data class ReviewEventEntity(
    @PrimaryKey val eventId: String,
    val recipeHash: String,
    val pubkey: String,
    val schemaVersion: Int,
    val overall: Int,
    val wouldOrderAgain: Boolean,
    val taste: Int?,
    val portion: Int?,
    val value: Int?,
    val satiation: Int?,
    val ts: Long,
    val sig: String,
    val retracted: Boolean = false,
)
```

Analog `TrustEntity`, `OutboxEntity`, `MyKeyEntity`, `PeerCursorEntity`, `MutedPubkeyEntity` nach den Schemas in Spec Section 4.

- [ ] **Step 3:** DAOs erstellen. Beispiel `ReviewDao.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: ReviewEventEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM review_events WHERE eventId = :id)")
    suspend fun exists(id: String): Boolean

    @Query("""
      SELECT COUNT(*) FROM review_events
      WHERE pubkey = :pubkey AND ts >= :sinceMs
    """)
    suspend fun countSince(pubkey: String, sinceMs: Long): Int

    @Query("UPDATE review_events SET retracted = 1 WHERE eventId = :id")
    suspend fun markRetracted(id: String)

    @Query("""
      SELECT * FROM review_events r
      WHERE r.recipeHash = :hash
        AND r.retracted = 0
        AND r.pubkey NOT IN (SELECT pubkey FROM muted_pubkeys)
        AND r.pubkey IN (SELECT pubkey FROM trust WHERE depth <= 2)
        AND r.ts = (
          SELECT MAX(ts) FROM review_events r2
          WHERE r2.pubkey = r.pubkey AND r2.recipeHash = r.recipeHash AND r2.retracted = 0
        )
    """)
    fun aggregatableForRecipe(hash: String): Flow<List<ReviewEventEntity>>

    @Query("SELECT * FROM review_events WHERE pubkey = :pubkey AND recipeHash = :hash ORDER BY ts DESC LIMIT 1")
    suspend fun latestByAuthor(pubkey: String, hash: String): ReviewEventEntity?
}
```

Schreibe `TrustDao`, `OutboxDao`, `MyKeyDao`, `PeerCursorDao`, `MutedPubkeyDao` analog mit `insert`, `find`, `delete`, `getAll` Methoden nach Bedarf der späteren Tasks.

- [ ] **Step 4:** `AppDatabase.kt` updaten — `entities` erweitern, `version = NEW_VERSION`, neue Abstract-DAOs:

```kotlin
@Database(
    entities = [
        // ... bestehende Entities ...
        ReviewEventEntity::class,
        TrustEntity::class,
        OutboxEntity::class,
        MyKeyEntity::class,
        PeerCursorEntity::class,
        MutedPubkeyEntity::class,
    ],
    version = NEW_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    // ... bestehende Abstract-Methoden ...
    abstract fun reviewDao(): ReviewDao
    abstract fun trustDao(): TrustDao
    abstract fun outboxDao(): OutboxDao
    abstract fun myKeyDao(): MyKeyDao
    abstract fun peerCursorDao(): PeerCursorDao
    abstract fun mutedPubkeyDao(): MutedPubkeyDao
}
```

- [ ] **Step 5:** Migration in `Migrations.kt` ergänzen:

```kotlin
val MIGRATION_OLD_TO_NEW = object : Migration(CURRENT_DB_VERSION, NEW_VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS review_events (
            eventId TEXT NOT NULL PRIMARY KEY,
            recipeHash TEXT NOT NULL,
            pubkey TEXT NOT NULL,
            schemaVersion INTEGER NOT NULL,
            overall INTEGER NOT NULL,
            wouldOrderAgain INTEGER NOT NULL,
            taste INTEGER, portion INTEGER, value INTEGER, satiation INTEGER,
            ts INTEGER NOT NULL,
            sig TEXT NOT NULL,
            retracted INTEGER NOT NULL DEFAULT 0
          )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_review_events_recipeHash ON review_events(recipeHash)")
        db.execSQL("CREATE INDEX index_review_events_pubkey ON review_events(pubkey)")
        db.execSQL("CREATE INDEX index_review_events_ts ON review_events(ts)")
        // analog für trust, outbox, my_keys, peer_cursor, muted_pubkeys
        db.execSQL("""CREATE TABLE IF NOT EXISTS trust (
            pubkey TEXT NOT NULL PRIMARY KEY, source TEXT NOT NULL, depth INTEGER NOT NULL,
            ts INTEGER NOT NULL, sig TEXT NOT NULL )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS outbox (
            eventId TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL,
            attemptCount INTEGER NOT NULL DEFAULT 0, lastAttempt INTEGER )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS my_keys (
            pubkey TEXT NOT NULL PRIMARY KEY, secretKeyEncrypted BLOB NOT NULL, createdAt INTEGER NOT NULL )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS peer_cursor (
            peerId TEXT NOT NULL PRIMARY KEY, lastSeenTs INTEGER NOT NULL )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS muted_pubkeys (
            pubkey TEXT NOT NULL PRIMARY KEY, mutedAt INTEGER NOT NULL )""".trimIndent())
    }
}
```

Migration im `DatabaseModule` einhängen (`addMigrations(...)`).

- [ ] **Step 6:** Build + ksp-Codegen verifizieren:

```bash
./gradlew :app:assembleDebug
```

- [ ] **Step 7:** DAO-Unit-Test (Robolectric oder androidTest) für `ReviewDao.aggregatableForRecipe` schreiben — minimaler Test, dass Query parsbar ist und kein bestehender Test grün-wird:

```kotlin
// app/src/test/java/de/transio/hiuni/feature/mensa/review/data/ReviewDaoTest.kt
package de.transio.hiuni.feature.mensa.review.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.transio.hiuni.core.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReviewDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ReviewDao
    private lateinit var trustDao: TrustDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.reviewDao()
        trustDao = db.trustDao()
    }
    @After fun tearDown() = db.close()

    @Test fun `aggregatable filters untrusted pubkeys`() = runBlocking {
        dao.insert(ReviewEventEntity("e1", "r1", "pkA", 1, 5, true, null, null, null, null, 100L, "sig", false))
        dao.insert(ReviewEventEntity("e2", "r1", "pkB", 1, 1, false, null, null, null, null, 100L, "sig", false))
        trustDao.insert(TrustEntity("pkA", "relay", 0, 100L, "sig"))
        val res = dao.aggregatableForRecipe("r1").first()
        assertEquals(listOf("pkA"), res.map { it.pubkey })
    }
}
```

- [ ] **Step 8:** Tests laufen:

```bash
./gradlew :app:testDebugUnitTest --tests "*ReviewDaoTest*"
```

- [ ] **Step 9:** Commit:

```bash
git add app/src/main/java/de/transio/hiuni/feature/mensa/review/ app/src/main/java/de/transio/hiuni/core/database/ app/src/test/java/de/transio/hiuni/feature/mensa/review/
git commit -m "feat(reviews): Room-Entities + DAOs + Migration für lokale Reviews"
```

