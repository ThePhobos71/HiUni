# Mensa Peer-to-Peer Reviews — Design

**Datum:** 2026-06-28
**Status:** Design — wartet auf Implementation-Plan
**Scope:** Crowd-Bewertungen für Mensa-Gerichte, Gun.js-Spirit, eigener Relay als „immer-online Peer", lebensfähig nach Server-Tod via Web-of-Trust + lokalem LAN-Sync

## 1. Kontext & Goals

### Status quo

`docs/FEATURES.md` plant Bewertungen aktuell als **rein lokal**: User-eigene `meal_ratings`-Room-Tabelle, Anzeige als persönlicher Durchschnitt („dein Schnitt für Pasta: 4.2"). Keine Crowd-Daten.

### Was wir bauen wollen

Bewertungen anderer HiUni-Studis sichtbar machen — aber ohne klassisches Backend, das ein Single-Point-of-Failure und ein Dauer-Kostenposten ist. Inspiration ist Gun.js: **jeder Client ist Speicher + Peer, der eigene Server ist nur ein Peer der zufällig immer online ist**. Wenn er irgendwann verschwindet, lebt das System über lokalen LAN-Sync und Web-of-Trust-Onboarding weiter.

### Erfolgsdefinition

- User sieht in der Mensa-Detail-Ansicht aggregierte Bewertungen anderer Studis pro Gericht
- Bewertungs-Abgabe ist 2-Tap-schnell (Overall + Wieder-Bestellen-Toggle), 4 Detail-Dimensionen sind optional aufklappbar
- App ist **vollständig offline-fähig** — eigene Reviews landen in Outbox und syncen bei nächster Verbindung
- Architektur erlaubt jederzeitiges Abschalten des Relays ohne Daten-Verlust; nur „Bootstrap neuer User" wird dann manueller via WoT-QR / Mail-Intro
- Cross-Device: Phone + Tablet teilen denselben Pubkey via Mail-Backup, eine User-Identität

### Explizit out-of-scope (V1)

- **Keine** Notifications zu Reviews (auch nicht „Heute ist hochbewertetes Gericht im Speiseplan")
- **Keine** Review-Anzeige im Home-/Tages-Screen — nur Mensa-Detail
- **Kein** Freitext-Inhalt in Reviews (verhindert Moderations-Aufwand)
- **Kein** Bluetooth / WiFi-Direct als Sync-Transport (nur LAN-mDNS)
- **Kein** Mehrfach-Pubkey pro Mat-Nr — eine User-Identität, geteilt über Geräte via Mail-Backup
- **Kein** Recovery ohne Mail-Backup — Phone-Verlust ohne Backup = neuer Pubkey, alte Reviews verwaisen
- **Kein** zentrales Moderations-Tooling — Mute ist client-lokal, Retraction ist signiert vom Autor

## 2. Architektur-Übersicht

```
┌─────────────┐  WS via Internet  ┌──────────────┐  WS  ┌─────────────┐
│  Phone A    │ ◀────────────────▶│ HiUni-Relay  │◀────▶│  Phone B    │
│             │                    │ (Docker)     │      │             │
│  embedded   │                    └──────────────┘      │  embedded   │
│  Ktor :9234 │ ◀────────── NSD / mDNS LAN ─────────────▶│  Ktor :9234 │
└─────────────┘    "_hiuni-sync._tcp" Service-Discovery  └─────────────┘
       │                                                          │
       │                                                          │
   Room DB                                                    Room DB
   (volle Replica)                                            (volle Replica)
```

**Drei Schichten:**

1. **App-Seite (Kotlin/Android):** `ReviewRepository` als neuer Bruder von `MensaRepository`. Lokale Room-Tabellen (`review_events`, `trust`, `outbox`, `my_keys`, `peer_cursor`, `muted_pubkeys`). Krypto via `tink-android`. Jedes Phone ist gleichzeitig WS-Client (zum Relay) UND eingebetteter WS-Server (Port 9234, nur wenn App im Vordergrund) für LAN-Sync.

2. **Wire-Format:** Vier signierte Event-Typen (`ReviewEvent`, `ValidationEvent`, `IntroEvent`, `RetractionEvent`), JSON, Ed25519. Append-only auf beiden Seiten. Aggregation passiert erst beim Anzeigen (LWW pro `(pubkey, recipeHash)`).

3. **Relay (Ktor 3 single-binary, Docker):** Implementiert exakt dasselbe Sync-Protokoll wie ein Phone. Sieht sich selbst als „ein Peer". Plus einen einmaligen `POST /validate`-Endpoint für initiales Onboarding (LSF-Cookie → ValidationEvent).

### Mesh-ready, nicht Mesh ab V1

V1 hat zwei Transports: WebSocket-zum-Relay (Internet) und WebSocket-zwischen-Phones (LAN, via mDNS). Beide sprechen dasselbe Sync-Protokoll. Wenn der Relay stirbt, läuft LAN-Sync weiter, neue User werden via Web-of-Trust onboarding (QR-Scan oder Mail-Intro). Kein BLE, kein WiFi-Direct, kein DHT.

## 3. Identität & Onboarding

### Schlüssel pro Device

- **Ed25519-Keypair** via `tink-android`, generiert beim ersten Mal Bewerten
- **PrivKey** im Android Keystore wrapped, gespeichert in `MyKeyEntity`
- **PubKey** ist die User-Identität für Reviews

### LSF-validiertes Onboarding (initialer Trust)

```
1. User tappt "Bewertung abgeben"
   ↓ kein Schlüssel vorhanden
2. App fragt: "Mit LSF einloggen, um Bewertungen abgeben zu können"
3. Bei erfolgreichem LSF-Login:
   a. App generiert Ed25519-Keypair
   b. PrivKey via Keystore wrap → MyKeyEntity
   c. App schickt POST /validate { lsfSessionCookie, pubkey }
   d. Relay: prüft LSF-Cookie, hasht Mat-Nr via HMAC(SECRET, matNr), prüft Eindeutigkeit
   e. Relay signiert ValidationEvent mit Master-Key, persistiert, broadcastet
4. Done — LSF-Login wird nie wieder gebraucht
```

**Privacy-Detail:** Mat-Nr lebt nur im Request-Scope, wird sofort gehashed (HMAC mit nur dem Relay-Owner bekanntem SECRET), dann verworfen. Keine Klartext-Mat-Nr in DB. Wer den Relay übernimmt, kann „Mat-Nr → Pubkey"-Mapping nicht rekonstruieren ohne den SECRET.

### Mail-Backup (Cross-Device-Recovery)

Direkt nach Key-Generierung:

```
1. App fragt User: "6-stellige Backup-PIN setzen"
2. encryptedKey = AES-GCM(privateKey, PBKDF2(pin, salt, iterations=600_000))
3. App legt Draft im Mail-Konto an:
   Subject: HIUNI-KEY-BACKUP-v1
   Body:    HIUNI-KEY-BACKUP-v1
            salt: <base64>
            ciphertext: <base64>
            pubkey: <base64>     # zum Wiedererkennen
            ts: <iso>
4. App markiert "Backup vorhanden ✓" in UI
```

**Recovery auf neuem Device:**

```
1. User installiert App, Mail-Konto schon eingerichtet (bestehender Onboarding-Step)
2. App IMAP-searcht im Drafts-Folder nach "HIUNI-KEY-BACKUP-v1"
3. Gefunden → "Wir haben dein Backup vom <Datum> — Backup-PIN eingeben?"
4. PIN ok → PrivKey wiederhergestellt → derselbe Pubkey wie zuvor
   PIN falsch → 3 Versuche, dann Hard-Reset (neuer Pubkey, alte Reviews bleiben verwaist)
5. Nicht gefunden → normaler LSF-Onboarding-Flow
```

**Backup-Health-Check:** Im `StartupRefresher` läuft pro App-Start ein idempotenter Check, ob der Draft noch existiert und der Ciphertext mit lokaler Berechnung übereinstimmt. Wenn nicht → neu anlegen. Kompensiert Drafts-Folder-Aufräum-Risiko.

**Threat-Modell:** Mail-Admin sieht nur verschlüsselten Blob. Brute-Force 6-stelliger PIN mit PBKDF2 600k Iterationen ≈ 6 Tage Worst-Case auf einer GPU pro User. Akzeptabel für Mensa-Reviews. User können 8-stellige PIN wählen für mehr Schutz.

### Cross-Device-Setup (Phone + Tablet)

User mit Mail-Backup:
- Tablet-Onboarding findet Backup automatisch → fragt nach PIN → importiert
- Beide Geräte haben **denselben Pubkey** → eine Identität → LWW funktioniert wie erwartet

User ohne Mail-Backup, der ein zweites Gerät will:
- Tablet erkennt: kein Backup vorhanden → zeigt „Erst auf deinem Phone Backup einrichten, oder neuen Pubkey starten (alter wird deprecated)"
- Soft-Force Richtung Mail-Backup-Lösung

### Web-of-Trust Onboarding (Post-Relay-Welt)

Wenn der Relay irgendwann tot ist, gibt's zwei Wege neue Pubkeys einzuführen:

**a) QR-Intro (Default):**

```
1. Neuer User installiert App → noch keine validated Pubkeys lokal
2. App zeigt QR-Code mit dem eigenen Pubkey
3. Bestehender validated User (depth ≤ 1) scannt QR
4. Sein Phone produziert IntroEvent { invitee, inviter, ts, sig }
5. IntroEvent verteilt sich via LAN-Sync
6. Newbie ist jetzt depth ≤ 2 → Reviews werden gezeigt
```

**b) Mail-Intro (Fallback für „nicht persönlich treffen"):**

```
1. Validated User tippt Mail-Adresse des Newbies ein
2. App schickt strukturierte Mail:
   Subject: HIUNI-INTRO-v1
   Body:    HIUNI-INTRO-v1
            invitee: <base64 pubkey>
            inviter: <base64 pubkey>
            sig: <base64>
            ts: <iso>
3. Newbie öffnet Mail in App → IntroEvent importiert → in Trust-Tabelle
```

**Sybil-Verteidigung im WoT:**
- WoT-Tiefe ≤ 2 (depth 0 = Relay-validiert, depth 1 = direkt eingeführt, depth 2 = enkel-eingeführt; depth > 2 wird ignoriert)
- Max 5 Intros pro Pubkey (lifetime, lokal pro Client gezählt; wer mehr versucht, wird beim 6. Intro lokal stumm geschaltet)
- Lokale Mute-Liste pro User: long-press → Reviewer mute

## 4. Datenmodell

### Wire-Events (signiert, append-only)

```kotlin
// Die eigentlichen Reviews
data class ReviewEvent(
    val type: "review",
    val schemaVersion: 1,
    val recipeHash: String,
    val overall: Int,                 // 1..5 REQUIRED
    val wouldOrderAgain: Boolean,     // REQUIRED
    val taste: Int?,                  // optional 1..5
    val portion: Int?,                // optional 1..5
    val value: Int?,                  // optional 1..5 (Preis/Leistung)
    val satiation: Int?,              // optional 1..5
    val ts: Long,                     // epoch ms
    val pubkey: String,               // base64 Ed25519 pubkey
    val sig: String                   // base64 sig über canonical(payload)
)

// "Diese Pubkey ist LSF-validiert" — vom Relay signiert
data class ValidationEvent(
    val type: "validation",
    val pubkey: String,
    val ts: Long,
    val issuer: "relay",
    val sig: String                   // mit Relay-Master-Key
)

// "Ich bürge für diesen neuen User" — Web-of-Trust
data class IntroEvent(
    val type: "intro",
    val invitee: String,
    val inviter: String,              // muss selbst validated sein
    val ts: Long,
    val sig: String                   // vom inviter
)

// "Ich nehme diese Review zurück" — kein Hard-Delete
data class RetractionEvent(
    val type: "retraction",
    val targetEventId: String,
    val ts: Long,
    val pubkey: String,               // gleich wie original-Review
    val sig: String
)
```

**Canonical-Form:** Beim Signieren werden Felder in fester Reihenfolge konkateniert (`type|schemaVersion|recipeHash|overall|wouldOrderAgain|taste|portion|value|satiation|ts|pubkey`), `null` → `""`. Kein JSON-Canonicalization-Streit.

**eventId:** SHA-256 vom Canonical-Payload. Dedupe-Key.

### Recipe-Hash

`MealEntity.sourceId` ändert sich täglich, deshalb rezeptbasiertes Matching:

```kotlin
fun recipeHash(meal: MealEntity): String {
    val normalized = meal.name
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\(.*?\\)"), "")  // "(A,G,V)" raus
        .trim()
    return sha256("$normalized|${meal.locationId}").base64()
}
```

„Pasta Bolognese" in Mensa Süd am Montag und Donnerstag → gleicher Hash → eine geteilte Review. Bolognese in Mensa Nord ≠ Mensa Süd → unterschiedliche Hashes (Zubereitung unterscheidet sich oft).

### Lokale Room-Tabellen

```kotlin
@Entity(tableName = "review_events")
data class ReviewEventEntity(
    @PrimaryKey val eventId: String,
    val recipeHash: String,             // indexed
    val pubkey: String,                 // indexed
    val schemaVersion: Int,
    val overall: Int,
    val wouldOrderAgain: Boolean,
    val taste: Int?,
    val portion: Int?,
    val value: Int?,
    val satiation: Int?,
    val ts: Long,
    val sig: String,
    val retracted: Boolean = false
)

@Entity(tableName = "trust")
data class TrustEntity(
    @PrimaryKey val pubkey: String,
    val source: String,                 // "relay" | inviter-pubkey
    val depth: Int,                     // 0..2 (>2 wird nicht gespeichert)
    val ts: Long,
    val sig: String
)

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val eventId: String,
    val payload: String,                // signiertes JSON
    val attemptCount: Int = 0,
    val lastAttempt: Long? = null
)

@Entity(tableName = "my_keys")
data class MyKeyEntity(
    @PrimaryKey val pubkey: String,
    val secretKeyEncrypted: ByteArray,  // via Android Keystore wrap
    val createdAt: Long
)

@Entity(tableName = "peer_cursor")
data class PeerCursorEntity(
    @PrimaryKey val peerId: String,     // "relay" | LAN-peer-pubkey
    val lastSeenTs: Long
)

@Entity(tableName = "muted_pubkeys")
data class MutedPubkeyEntity(
    @PrimaryKey val pubkey: String,
    val mutedAt: Long
)
```

### Aggregat (berechnet, nicht gespeichert)

```kotlin
data class RecipeAggregate(
    val recipeHash: String,
    val overall: Float?,                // null wenn n=0
    val overallCount: Int,
    val wouldOrderAgainPct: Int?,       // 0..100
    val byDimension: Map<Dimension, DimensionStat>
)
data class DimensionStat(val avg: Float, val n: Int)
```

Aggregat-Query (LWW-Subselect + Trust-Join + Mute-Filter):

```sql
SELECT
  r.recipeHash,
  AVG(CAST(r.overall AS REAL)) as overall,
  COUNT(r.overall) as overallCount,
  100.0 * SUM(CASE WHEN r.wouldOrderAgain THEN 1 ELSE 0 END) / COUNT(*) as repeatPct,
  AVG(r.taste) as tasteAvg, COUNT(r.taste) as tasteN,
  -- ... portion, value, satiation analog
FROM review_events r
INNER JOIN trust t ON r.pubkey = t.pubkey AND t.depth <= 2
WHERE r.recipeHash = :recipeHash
  AND r.retracted = 0
  AND r.pubkey NOT IN (SELECT pubkey FROM muted_pubkeys)
  AND r.ts = (
    SELECT MAX(ts) FROM review_events r2
    WHERE r2.pubkey = r.pubkey AND r2.recipeHash = r.recipeHash AND r2.retracted = 0
  )
GROUP BY r.recipeHash
```

LWW (Last-Writer-Wins) wird über `ts = MAX(ts)`-Subquery erzwungen — keine separate „aktiv"-Spalte.

## 5. Sync-Protokoll

### Wire-Frames (WebSocket-Text, JSON)

```
HELLO    { v: 1, since: <timestamp_ms> }
EVENT    { ...signed event... }
EVENTS   { items: [ ...event... ], hasMore: bool, cursor: <ts> }
PING     { ts: <ms> }
PONG     { ts: <ms> }
```

### Connection-Lifecycle

```
Phone A                              Peer (Relay oder LAN-Phone B)
   │
   ├─── open WS ───────────────────▶│
   ├─── HELLO { since: 1719500000 }─▶│
   │                                  │
   │◀─ EVENTS { items, hasMore=true, cursor: 1719700000 } ──┤
   │ (apply, save to Room, update peerCursor)
   │                                  │
   ├─── HELLO { since: 1719700000 }─▶│
   │◀─ EVENTS { items, hasMore=false } ─────────────────────┤
   │                                  │
   │ === Live phase ===                │
   │◀── EVENT { ... } (push) ─────────┤
   ├─── EVENT { ... } (eigene Review) ▶│  broadcast to others
   ├─── PING ──── every 30s ─────────▶│
   │◀── PONG ────────────────────────┤
```

**Eigenschaften:**
- `HELLO` ist idempotent — bei Reconnect schickst du wieder `HELLO { since: cursor }`, server keine Session-State-Memory nötig
- Pagination via `cursor` für Backfill: 100 Events pro Batch
- `PING`/`PONG` alle 30s, Dead-Connection-Cleanup nach 60s ohne Antwort

### Acceptance-Rules (identisch auf Phone und Relay)

```kotlin
fun acceptEvent(e: SignedEvent): Boolean {
    if (!e.matchesSchema()) return false
    if (!verifyEd25519(e.canonical(), e.sig, e.pubkey)) return false

    val trust = trustDao.find(e.pubkey) ?: return false  // unbekannt → drop
    if (trust.depth > 2) return false

    if (e.ts > now() + 5.minutes || e.ts < now() - 90.days) return false

    if (eventDao.exists(e.eventId)) return false  // dedupe

    // Anti-Spam: max 50 Events/Tag/Pubkey
    if (eventDao.countSince(e.pubkey, now() - 1.day) > 50) return false

    eventDao.insert(e)
    broadcastToOtherPeers(e)
    return true
}
```

Byte-identisch geteilt zwischen Relay-Backend und Phone-Client (gemeinsames Modul, idealerweise als KMP-Library mit JVM- und Android-Target).

### Multi-Peer-Sync

Phone hält pro Peer eine eigene `PeerCursorEntity`. Wenn beide Peers (Relay + LAN-Phone) online:
- App öffnet beide WebSockets
- Sendet eigenes `HELLO { since: cursor[peer].ts }` an jeden
- Doppelt empfangene Events werden via `eventId` deduped
- Eigene neue Reviews werden an **alle** verbundenen Sockets gesendet → maximale Verbreitung

**Fallback-Verhalten:**
- Relay weg, LAN da → Sync läuft langsamer
- Beide weg → Outbox sammelt, flush bei nächstem Connect

### LAN-Discovery (NSD/mDNS)

```kotlin
// Pro App-Vordergrund-Moment:
val service = NsdServiceInfo().apply {
    serviceName = "HiUni-${myPubkey.take(8)}"
    serviceType = "_hiuni-sync._tcp"
    port = 9234
}
nsdManager.registerService(service, NsdManager.PROTOCOL_DNS_SD, ...)

// Eingebetteter Ktor-Server auf 9234:
embeddedServer(Netty, port = 9234) {
    install(WebSockets)
    routing { webSocket("/sync") { /* gleicher Code wie Relay */ } }
}.start()

// Discovery anderer Peers:
nsdManager.discoverServices("_hiuni-sync._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
// On resolve → openWebSocket("ws://${ip}:${port}/sync")
```

Nur aktiv während App im Vordergrund (Battery-Schutz). Eduroam wird's wahrscheinlich blocken (Client-Isolation), Heim-WLAN/WG/Hotspot funktioniert verlässlich.

## 6. UI-Aggregation (Mensa-Detail-Screen)

### ReviewBadge (in bestehender Meal-Card)

```
Pasta Bolognese
Mensa Süd · 2,80 €
★ 4.3 (n=23) · 👍 87%  [▾]

[expanded:]
  🍴 Geschmack    ★ 4.5  (21)
  🍽 Portion      ★ 3.2  (18)
  💶 P/L          ★ 3.9  (20)
  😋 Sättigung    ★ 4.1  (15)

  [Bewerten ▸]
```

`n` darf pro Dimension unterschiedlich sein (manche User füllen nur Overall).

### Eigene-Review-BottomSheet

```
Pasta Bolognese

Gesamt:    ★★★★☆ (Pflicht)
Wieder?    ●─── 👍 (Pflicht)

▾ Mehr Details
  Geschmack:  ★★★★★
  Portion:    ★★★☆☆
  P/L:        ★★★★☆
  Sättigung:  ★★★★☆

[Senden]  [Abbrechen]
```

**Beim Senden:**
1. ReviewEvent gebaut, signiert mit `MyKeyEntity.privateKey`
2. In Outbox-Tabelle persistieren
3. `RelayClient` flusht Outbox sofort (wenn online) oder beim nächsten Connect
4. Optimistisches UI: Aggregat berechnet sich neu inkl. eigener Review

Re-Edit derselben Review öffnet BottomSheet mit aktuellen LWW-Werten — Edit ist „neues Event mit gleichem `(pubkey, recipeHash)` + höherem `ts`".

### Mute-Reaktion

Long-press auf eine fremde Review → „Diesen Reviewer stumm schalten" → Pubkey in `MutedPubkeyEntity`. Query oben filtert via `r.pubkey NOT IN (SELECT pubkey FROM muted_pubkeys)`. Komplett lokal.

## 7. Relay-Server (Ktor, Docker)

### Stack

- **Ktor 3** mit `Netty` + `WebSockets` + `ContentNegotiation/Json`
- **SQLite** via `xerial/sqlite-jdbc`
- **TLS** via Caddy als Sidecar-Container (Auto-LetsEncrypt)
- **Hosting:** Docker — Hetzner / Pi / wo auch immer

### Datei-Layout (~400 LoC)

```
hiuni-relay/
├── Dockerfile
├── docker-compose.yml          # Ktor + Caddy
├── build.gradle.kts
├── src/main/kotlin/
│   ├── Application.kt
│   ├── Routes.kt               # /validate, /sync, /health
│   ├── SyncSession.kt
│   ├── EventStore.kt           # SQLite-Wrapper
│   ├── TrustValidator.kt       # acceptEvent() — gemeinsam mit Phone
│   ├── LsfBridge.kt            # LSF-Cookie-Validierung
│   ├── MasterKey.kt            # Ed25519-MasterKey laden (ENV)
│   └── Hmac.kt
├── schema.sql
└── README.md
```

### Tabellen (Relay-side)

```sql
CREATE TABLE events (
  event_id     TEXT PRIMARY KEY,    -- sha256 vom Canonical-Payload
  recipe_hash  TEXT,
  pubkey       TEXT NOT NULL,
  payload      TEXT NOT NULL,       -- ganzes signiertes JSON
  ts           INTEGER NOT NULL,
  type         TEXT NOT NULL        -- 'review' | 'intro' | 'retraction' | 'validation'
);
CREATE INDEX events_ts ON events(ts);
CREATE INDEX events_pubkey ON events(pubkey);

CREATE TABLE pubkeys (
  pubkey               TEXT PRIMARY KEY,
  validated_at         INTEGER NOT NULL,
  validation_event_id  TEXT
);

CREATE TABLE mat_nr_hashes (
  hash          TEXT PRIMARY KEY,    -- HMAC(SECRET, matrikelnummer)
  pubkey        TEXT NOT NULL,
  validated_at  INTEGER NOT NULL
);
```

### POST /validate

```kotlin
post("/validate") {
    val req = call.receive<ValidateRequest>()  // { lsfSessionCookie, pubkey }

    val lsfUser = lsfBridge.whoami(req.lsfSessionCookie)
        ?: return@post call.respond(401)

    val matHash = hmac(SECRET, lsfUser.matrikelnummer)
    val existing = matNrDao.find(matHash)

    if (existing != null && existing.pubkey != req.pubkey) {
        // Phone-Wechsel ohne Mail-Backup → alten Pubkey deprecaten
        eventStore.markPubkeyDeprecated(existing.pubkey)
    }

    val validationEvent = signValidation(req.pubkey)
    eventStore.insert(validationEvent)
    matNrDao.upsert(matHash, req.pubkey)
    broadcastToAllSessions(validationEvent)

    call.respond(200, mapOf("validationEvent" to validationEvent))
}
```

### /sync WebSocket

Identisches Protokoll wie Phone-zu-Phone. Pseudocode in Sektion 5.

### Dockerfile

```dockerfile
FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle installDist --no-daemon

FROM gcr.io/distroless/java21-debian12:nonroot
COPY --from=build /app/build/install/hiuni-relay /app
ENV DB_PATH=/data/relay.db \
    HMAC_SECRET="" \
    MASTER_KEY_B64=""
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["/app/bin/hiuni-relay"]
```

### docker-compose.yml

```yaml
services:
  relay:
    build: .
    environment:
      HMAC_SECRET: ${HMAC_SECRET}
      MASTER_KEY_B64: ${MASTER_KEY_B64}
      DB_PATH: /data/relay.db
    volumes:
      - relay-data:/data
    expose:
      - "8080"

  caddy:
    image: caddy:2-alpine
    ports:
      - "443:443"
      - "80:80"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy-data:/data
    depends_on:
      - relay

volumes:
  relay-data:
  caddy-data:
```

### Operations

| Aspekt | Lösung |
|---|---|
| Logs | `docker compose logs -f relay` |
| Backup | `litestream` Sidecar → Cloudflare R2 (gratis bis 10 GB) |
| Monitoring | `/health` returns `{events, connections}`, simpler Uptime-Robot |
| Master-Key-Rotation | Neue Master-Key, alte ValidationEvents bleiben gültig, Clients halten beide Pubkeys whitelisted |

### Bewusst NICHT im Relay

- Kein User-Account-Konzept (nur Pubkeys)
- Kein Moderations-Endpoint (Mute lokal, Retraction signiert)
- Kein Mail-Versand (Backup/Intro über IMAP des Users)
- Kein Analytics-Endpoint (SQL-CLI für Stats)

## 8. Implementierungs-Risiken & Trade-Offs

| Risiko | Mitigation |
|---|---|
| **Eduroam blockt mDNS** | LAN-Sync ist „Nice-to-have", Relay bleibt Haupt-Sync-Pfad. Wer ohne Relay leben will, kann Heim-WLAN/Hotspot nutzen. |
| **User vergisst Backup-PIN** | 3 Versuche → Hard-Reset, neuer Pubkey. Alte Reviews bleiben sichtbar, sind nur nicht mehr „meine". |
| **Mat-Nr-Hash-DB leaked** | HMAC mit SECRET. Wer DB hat, aber nicht SECRET, kann nicht zurückrechnen. SECRET nur in ENV-Variable des Relays, nicht in DB. |
| **Master-Key des Relays kompromittiert** | Rotation möglich (alte Key bleibt valid, Clients akzeptieren beide). Im Worst Case: alle bestehenden ValidationEvents müssen re-issued werden. |
| **Sybil über mehrere Studi-Accounts** | LSF-Login + Mat-Nr-Hash-Eindeutigkeit. Wer mehrere Mat-Nrs hat, kann mehrere Pubkeys haben — kein perfekter Schutz, aber Aufwand hoch. |
| **5-Intros-Limit lokal gespielt** | Bewusste Soft-Limit. Komplett seq-genau ist nicht möglich ohne zentralen Counter — V1 akzeptiert das. |
| **`schemaVersion`-Bumps brechen Aggregation** | Aggregat-Query liest nur Felder die im aktuellen Schema definiert sind, neuere Schemas ignorieren bis App-Update. Append-only macht Migration trivial. |

## 9. Offene Punkte

- **Mail-IMAP-Support für Drafts-Folder-Search:** Hängt davon ab, wie der bestehende Mail-Layer (`feature/email/`) gebaut ist — muss in Implementation-Planung gegen-gecheckt werden, ob IMAP-Search verfügbar oder erst zu bauen ist.
- **Welche Mensa-Locations starten?** Default: alle. Wenn Anzahl an aktiven Reviewern initial niedrig, evtl. nur eine als „Pilot" via Feature-Flag.
- **Schedule für LAN-Discovery-Pause:** Battery-Studie nötig — vermutlich nur während Mensa-Detail-Screen offen, nicht durchgängig im Vordergrund.

## 10. Implementation-Phasen (high-level)

1. **Phase 1 — Datenmodell + Krypto-Layer:** Room-Tabellen, Event-Klassen, Ed25519-Sign/Verify, Canonical-Form, `acceptEvent()`-Validator (geteilt als gradle-Module zwischen App und späterem Relay-Build)
2. **Phase 2 — Mensa-UI:** ReviewBadge in Meal-Card, BottomSheet zum Bewerten, Aggregat-Query, Mute-Funktion. Lokal funktionsfähig, ohne Sync.
3. **Phase 3 — Relay:** Ktor-App, `/validate` + `/sync`, Dockerfile + Compose. Lokales Testing mit zwei Phones.
4. **Phase 4 — LSF-Integration:** Onboarding-Flow, ValidationEvent-Empfang, Trust-Tabelle.
5. **Phase 5 — Mail-Backup:** Encrypt/decrypt, IMAP-Search im Drafts, Recovery-Dialog.
6. **Phase 6 — LAN-Sync:** NSD/mDNS-Discovery, eingebetteter Ktor-Server, Multi-Peer-Sync-Logik.
7. **Phase 7 — Web-of-Trust:** QR-Intro + Mail-Intro, IntroEvent-Validierung, 5-Limit.

Detaillierter Implementation-Plan kommt separat via `writing-plans`.
