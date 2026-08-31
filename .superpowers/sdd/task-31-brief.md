### Task 31: IntroEvent-Issuer + Trust-Count

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/IntroIssuer.kt`
- Modify: `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/TrustDao.kt` (Count-Method ergänzen)

**Interfaces:**
- Produces:
  - `class IntroIssuer(keys, trustDao, outbox) { suspend fun issueIntro(inviteePubkey: String): Result<IntroEvent> }`

- [ ] **Step 1:** `TrustDao` ergänzen:

```kotlin
@Query("SELECT COUNT(*) FROM trust WHERE source = :pubkey")
suspend fun countIntrosBy(pubkey: String): Int
```

- [ ] **Step 2:** `IntroIssuer.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.trust

import de.transio.hiuni.events.IntroEvent
import de.transio.hiuni.events.signWith
import de.transio.hiuni.events.toBase64
import de.transio.hiuni.feature.mensa.review.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class IntroIssuer @Inject constructor(
    private val keys: MyKeyManager,
    private val trustDao: TrustDao,
    private val outbox: OutboxDao,
) {
    private val json = Json
    suspend fun issueIntro(inviteePubkey: String): Result<IntroEvent> = runCatching {
        val kp = keys.getOrNull() ?: error("no key")
        val pub = kp.publicKey.toBase64()
        val myTrust = trustDao.find(pub) ?: error("not yet validated")
        if (myTrust.depth >= 2) error("depth too deep to invite")
        val count = trustDao.countIntrosBy(pub)
        if (count >= 5) error("invite limit reached (5)")

        val ev = IntroEvent(invitee = inviteePubkey, inviter = pub,
            ts = System.currentTimeMillis(), sig = "").signWith(kp)
        outbox.insert(OutboxEntity(ev.eventId(), json.encodeToString(ev)))
        trustDao.insert(TrustEntity(inviteePubkey, pub, myTrust.depth + 1, ev.ts, ev.sig))
        ev
    }
}
```

- [ ] **Step 3:** Commit:

```bash
git add app/
git commit -m "feat(reviews): IntroIssuer mit 5-Intros-Limit"
```

