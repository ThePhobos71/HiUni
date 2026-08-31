### Task 5: EventValidator (acceptEvent-Logik, geteilt App/Relay)

**Files:**
- Create: `shared-events/src/main/kotlin/de/transio/hiuni/events/EventValidator.kt`
- Create: `shared-events/src/test/kotlin/de/transio/hiuni/events/EventValidatorTest.kt`

**Interfaces:**
- Produces:
  - `interface TrustResolver { fun depthOf(pubkey: String): Int? }`
  - `interface EventStore { fun exists(eventId: String): Boolean; fun countSince(pubkey: String, sinceMs: Long): Int }`
  - `class EventValidator(val trust: TrustResolver, val store: EventStore, val masterPubkey: String, val nowMs: () -> Long = { System.currentTimeMillis() })`
  - `fun EventValidator.accept(e: SignedEvent): AcceptResult` mit `sealed class AcceptResult { object Ok; data class Reject(val reason: String) }`

- [ ] **Step 1: Test:**

```kotlin
package de.transio.hiuni.events
import org.junit.Assert.*
import org.junit.Test

class EventValidatorTest {
    private val masterKp = Ed25519.generateKeypair()
    private val userKp = Ed25519.generateKeypair()
    private val userPk = userKp.publicKey.toBase64()
    private val masterPk = masterKp.publicKey.toBase64()
    private val trust = object : TrustResolver {
        override fun depthOf(p: String) = if (p == userPk) 0 else null
    }
    private val store = object : EventStore {
        override fun exists(id: String) = false
        override fun countSince(p: String, s: Long) = 0
    }
    private val now = 1_000_000L
    private val v = EventValidator(trust, store, masterPk, nowMs = { now })

    @Test fun `accepts valid signed review`() {
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, now, userPk, "").signWith(userKp)
        assertEquals(AcceptResult.Ok, v.accept(r))
    }
    @Test fun `rejects bad signature`() {
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, now, userPk, "").signWith(userKp)
        val bad = r.copy(overall = 5)
        assertTrue(v.accept(bad) is AcceptResult.Reject)
    }
    @Test fun `rejects unknown pubkey (no trust)`() {
        val stranger = Ed25519.generateKeypair()
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, now,
            stranger.publicKey.toBase64(), "").signWith(stranger)
        assertTrue(v.accept(r) is AcceptResult.Reject)
    }
    @Test fun `rejects depth greater than 2`() {
        val deepTrust = object : TrustResolver { override fun depthOf(p: String) = 3 }
        val v2 = EventValidator(deepTrust, store, masterPk, nowMs = { now })
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, now, userPk, "").signWith(userKp)
        assertTrue(v2.accept(r) is AcceptResult.Reject)
    }
    @Test fun `rejects too old`() {
        val tooOld = now - 91L * 24 * 3600_000
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, tooOld, userPk, "").signWith(userKp)
        assertTrue(v.accept(r) is AcceptResult.Reject)
    }
    @Test fun `rejects future ts`() {
        val future = now + 10 * 60_000
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, future, userPk, "").signWith(userKp)
        assertTrue(v.accept(r) is AcceptResult.Reject)
    }
    @Test fun `rejects dedupe`() {
        val store2 = object : EventStore {
            override fun exists(id: String) = true
            override fun countSince(p: String, s: Long) = 0
        }
        val v2 = EventValidator(trust, store2, masterPk, nowMs = { now })
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, now, userPk, "").signWith(userKp)
        assertTrue(v2.accept(r) is AcceptResult.Reject)
    }
    @Test fun `rejects spam (more than 50 in day)`() {
        val store3 = object : EventStore {
            override fun exists(id: String) = false
            override fun countSince(p: String, s: Long) = 50
        }
        val v3 = EventValidator(trust, store3, masterPk, nowMs = { now })
        val r = ReviewEvent(1, "h", 4, true, null, null, null, null, now, userPk, "").signWith(userKp)
        assertTrue(v3.accept(r) is AcceptResult.Reject)
    }
}
```

- [ ] **Step 2:** Test FAIL.

- [ ] **Step 3:** `EventValidator.kt`:

```kotlin
package de.transio.hiuni.events

sealed class AcceptResult {
    object Ok : AcceptResult()
    data class Reject(val reason: String) : AcceptResult()
}

interface TrustResolver { fun depthOf(pubkey: String): Int? }
interface EventStore {
    fun exists(eventId: String): Boolean
    fun countSince(pubkey: String, sinceMs: Long): Int
}

class EventValidator(
    val trust: TrustResolver,
    val store: EventStore,
    val masterPubkey: String,
    val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val maxFutureSkewMs = 5 * 60_000L
    private val maxPastSkewMs = 90L * 24 * 3600_000L
    private val spamLimit = 50

    fun accept(e: SignedEvent): AcceptResult {
        val sigOk = when (e) {
            is ReviewEvent     -> e.verify()
            is IntroEvent      -> e.verify()
            is RetractionEvent -> e.verify()
            is ValidationEvent -> e.verify(masterPubkey)
        }
        if (!sigOk) return AcceptResult.Reject("invalid signature")

        if (e !is ValidationEvent) {
            val depth = trust.depthOf(e.pubkey)
                ?: return AcceptResult.Reject("pubkey not trusted")
            if (depth > 2) return AcceptResult.Reject("trust depth > 2")
        }

        val now = nowMs()
        if (e.ts > now + maxFutureSkewMs) return AcceptResult.Reject("ts in future")
        if (e.ts < now - maxPastSkewMs)   return AcceptResult.Reject("ts too old")

        if (store.exists(e.eventId())) return AcceptResult.Reject("duplicate eventId")
        if (store.countSince(e.pubkey, now - 24 * 3600_000L) >= spamLimit)
            return AcceptResult.Reject("daily spam limit")

        return AcceptResult.Ok
    }
}
```

- [ ] **Step 4:** Tests PASS.

- [ ] **Step 5:** Commit:

```bash
git add shared-events/
git commit -m "feat(reviews): EventValidator mit Signatur-/Trust-/Spam-Regeln"
```

