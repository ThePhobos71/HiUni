### Task 15: SQLite-EventStore im Relay

**Files:**
- Create: `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/EventStore.kt`
- Create: `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Schema.kt`
- Create: `hiuni-relay/src/test/kotlin/de/transio/hiuni/relay/EventStoreTest.kt`

**Interfaces:**
- Produces:
  - `class EventStore(val dbPath: String)`
  - `fun insert(payload: String, ev: SignedEvent)`, `fun exists(eventId: String): Boolean`, `fun countSince(pubkey: String, sinceMs: Long): Int`, `fun queryAfter(sinceMs: Long, limit: Int): Batch`, `data class Batch(items: List<String>, hasMore: Boolean, cursor: Long)`
  - `fun upsertMatNr(hash: String, pubkey: String)`, `fun findMatNr(hash: String): String?`, `fun deprecateOldPubkey(old: String)`

- [ ] **Step 1: Test** (Batch-Insert + queryAfter):

```kotlin
package de.transio.hiuni.relay

import de.transio.hiuni.events.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EventStoreTest {
    @Test fun `insert then queryAfter returns event`() {
        val tmp = Files.createTempFile("relay", ".db").toFile().also { it.deleteOnExit() }
        val store = EventStore(tmp.absolutePath)
        store.init()
        val kp = Ed25519.generateKeypair()
        val r = ReviewEvent(1, "h", 5, true, null, null, null, null,
            100L, kp.publicKey.toBase64(), "").signWith(kp)
        val payload = """{"type":"review", "eventId":"${r.eventId()}"}"""
        store.insert(r.eventId(), "review", r.pubkey, r.ts, payload)
        val batch = store.queryAfter(0L, 10)
        assertEquals(1, batch.items.size)
        assertEquals(payload, batch.items[0])
    }
}
```

- [ ] **Step 2:** FAIL.

- [ ] **Step 3:** `Schema.kt`:

```kotlin
package de.transio.hiuni.relay

import java.sql.Connection

internal fun createSchema(c: Connection) {
    c.createStatement().use { st ->
        st.execute("""
          CREATE TABLE IF NOT EXISTS events (
            event_id    TEXT PRIMARY KEY,
            type        TEXT NOT NULL,
            pubkey      TEXT NOT NULL,
            ts          INTEGER NOT NULL,
            payload     TEXT NOT NULL
          )
        """.trimIndent())
        st.execute("CREATE INDEX IF NOT EXISTS events_ts ON events(ts)")
        st.execute("CREATE INDEX IF NOT EXISTS events_pubkey ON events(pubkey)")
        st.execute("""
          CREATE TABLE IF NOT EXISTS pubkeys (
            pubkey                TEXT PRIMARY KEY,
            validated_at          INTEGER NOT NULL,
            validation_event_id   TEXT,
            deprecated            INTEGER NOT NULL DEFAULT 0
          )
        """.trimIndent())
        st.execute("""
          CREATE TABLE IF NOT EXISTS mat_nr_hashes (
            hash         TEXT PRIMARY KEY,
            pubkey       TEXT NOT NULL,
            validated_at INTEGER NOT NULL
          )
        """.trimIndent())
    }
}
```

- [ ] **Step 4:** `EventStore.kt`:

```kotlin
package de.transio.hiuni.relay

import java.sql.Connection
import java.sql.DriverManager

data class Batch(val items: List<String>, val hasMore: Boolean, val cursor: Long)

class EventStore(private val dbPath: String) {
    private val conn: Connection by lazy {
        DriverManager.getConnection("jdbc:sqlite:$dbPath").apply {
            createStatement().execute("PRAGMA journal_mode=WAL")
        }
    }
    fun init() = createSchema(conn)

    @Synchronized fun insert(eventId: String, type: String, pubkey: String, ts: Long, payload: String) {
        conn.prepareStatement("INSERT OR IGNORE INTO events(event_id,type,pubkey,ts,payload) VALUES (?,?,?,?,?)")
            .use {
                it.setString(1, eventId); it.setString(2, type); it.setString(3, pubkey)
                it.setLong(4, ts); it.setString(5, payload); it.executeUpdate()
            }
    }

    @Synchronized fun exists(eventId: String): Boolean {
        conn.prepareStatement("SELECT 1 FROM events WHERE event_id = ?").use {
            it.setString(1, eventId)
            return it.executeQuery().next()
        }
    }

    @Synchronized fun countSince(pubkey: String, sinceMs: Long): Int {
        conn.prepareStatement("SELECT COUNT(*) FROM events WHERE pubkey = ? AND ts >= ?").use {
            it.setString(1, pubkey); it.setLong(2, sinceMs)
            val rs = it.executeQuery(); rs.next(); return rs.getInt(1)
        }
    }

    @Synchronized fun queryAfter(sinceMs: Long, limit: Int): Batch {
        val items = mutableListOf<String>()
        var maxTs = sinceMs
        conn.prepareStatement(
            "SELECT ts, payload FROM events WHERE ts > ? ORDER BY ts ASC LIMIT ?"
        ).use {
            it.setLong(1, sinceMs); it.setInt(2, limit + 1)
            val rs = it.executeQuery()
            while (rs.next() && items.size < limit) {
                items.add(rs.getString("payload"))
                maxTs = rs.getLong("ts")
            }
            val hasMore = rs.next()
            return Batch(items, hasMore, maxTs)
        }
    }

    @Synchronized fun upsertMatNr(hash: String, pubkey: String) {
        conn.prepareStatement("""INSERT INTO mat_nr_hashes(hash,pubkey,validated_at)
            VALUES (?,?,?) ON CONFLICT(hash) DO UPDATE SET pubkey=excluded.pubkey, validated_at=excluded.validated_at""").use {
            it.setString(1, hash); it.setString(2, pubkey); it.setLong(3, System.currentTimeMillis())
            it.executeUpdate()
        }
    }
    @Synchronized fun findMatNr(hash: String): String? {
        conn.prepareStatement("SELECT pubkey FROM mat_nr_hashes WHERE hash = ?").use {
            it.setString(1, hash)
            val rs = it.executeQuery()
            return if (rs.next()) rs.getString(1) else null
        }
    }
    @Synchronized fun deprecateOldPubkey(old: String) {
        conn.prepareStatement("UPDATE pubkeys SET deprecated = 1 WHERE pubkey = ?").use {
            it.setString(1, old); it.executeUpdate()
        }
    }
    @Synchronized fun registerPubkey(pubkey: String, validationEventId: String) {
        conn.prepareStatement("""INSERT INTO pubkeys(pubkey,validated_at,validation_event_id)
            VALUES (?,?,?) ON CONFLICT(pubkey) DO UPDATE SET validated_at=excluded.validated_at""").use {
            it.setString(1, pubkey); it.setLong(2, System.currentTimeMillis())
            it.setString(3, validationEventId); it.executeUpdate()
        }
    }
}
```

- [ ] **Step 5:** Tests PASS.

- [ ] **Step 6:** Commit:

```bash
git add hiuni-relay/
git commit -m "feat(relay): SQLite-EventStore mit insert/queryAfter/Mat-Nr-Tracking"
```

