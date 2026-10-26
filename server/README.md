# HiUni FCM-Tickle-Server

Ein sehr schlanker Server, der die HiUni-App periodisch per Firebase Cloud
Messaging (FCM) "anstupst" ("tickle"), damit sie im Hintergrund selbst nach
neuen Mails und Daten schaut.

## Das Tickle-Modell: warum der Server keine Zugangsdaten kennt

Der Server hat **keinen Zugriff auf Postfächer, Passwörter oder Mail-Inhalte**.
Er kennt ausschließlich FCM-Device-Tokens, also nur "an welches Gerät kann
ich eine Push-Nachricht schicken", nicht "was steht in wessen Mails".

Alle 15 Minuten (konfigurierbar) schickt der Server an jedes registrierte
Gerät eine **stille FCM-Data-Message** ohne Notification-Payload:

```json
{"type": "sync_tickle"}
```

Der Typ ist über die Env `TICKLE_TYPE` konfigurierbar (Default `sync_tickle`):
Neue App-Versionen verstehen `sync_tickle` (Mail-Refresh **plus** gestaffelter
Feature-Prefetch für Noten/Kurse/Learnweb usw.), alte Versionen nur `mail_tickle`
(reiner Mail-Wecker). Bei Bestands-Deploys mit älteren Clients daher ggf.
`TICKLE_TYPE=mail_tickle` setzen.

Diese Nachricht wird mit `AndroidConfig(priority="high")` verschickt, damit
sie das Gerät auch aus dem Doze-Modus aufweckt. Die App empfängt sie in
ihrem `FirebaseMessagingService`, merkt "Zeit, nachzuschauen", und holt sich
die eigentlichen Daten direkt vom Mailserver bzw. den Uni-Diensten, mit den
Zugangsdaten, die ausschließlich auf dem Gerät des Nutzers liegen.

Das ist bewusst so gebaut wie bei WhatsApp: die Push-Infrastruktur weiß nur
"jemand hat vermutlich eine neue Nachricht", nicht was drinsteht. Fällt der
Tickle-Server aus oder wird er kompromittiert, sind maximal Device-Tokens
betroffen, keine Postfach-Zugangsdaten und keine Mail-Inhalte.

### Ablauf einer Tickle-Runde

1. Tokens mit `last_seen` älter als `TOKEN_MAX_AGE_DAYS` werden gelöscht.
2. Liegt die aktuelle Uhrzeit (in `TIMEZONE`) in `QUIET_HOURS`, wird nichts
   gesendet. Beginn und Ende einer Ruhephase werden je einmal geloggt.
3. Sonst gehen die Nachrichten per `messaging.send_each` in Batches zu je
   maximal 500 Tokens raus (FCM-Limit). Jede Einzelantwort wird ausgewertet:
   Tokens mit `UnregisteredError`, `SenderIdMismatchError` oder
   `InvalidArgumentError` werden gelöscht, bei allen anderen Fehlern (Quota,
   Server nicht erreichbar, ...) bleibt das Token erhalten. Pro Runde gibt es
   eine Log-Zeile `Tickle-Runde fertig: X gesendet, Y entfernt, Z fehlgeschlagen.`

## Konfiguration (Environment)

| Variable | Default | Bedeutung |
|---|---|---|
| `API_KEY` | (leer) | Muss im Header `X-Api-Key` mitkommen. Leer = `/register` und `/unregister` antworten immer `401`. |
| `GOOGLE_APPLICATION_CREDENTIALS_JSON` | (leer) | Kompletter Inhalt des Firebase-Service-Account-JSON. Hat Vorrang vor dem Datei-Pfad. |
| `GOOGLE_APPLICATION_CREDENTIALS` | (leer) | Pfad zur Service-Account-Datei im Container (docker-compose-Variante). |
| `DB_PATH` | `/data/tokens.db` | SQLite-Datei. Das Verzeichnis muss persistent sein (Volume auf `/data`). |
| `TICKLE_INTERVAL_MINUTES` | `15` | Abstand zwischen zwei Runden (Minimum 1). |
| `TICKLE_TYPE` | `sync_tickle` | Wert von `type` in der Data-Message. |
| `QUIET_HOURS` | `23:00-06:00` | Ruhezeit `HH:MM-HH:MM`, darf über Mitternacht gehen. Start inklusive, Ende exklusive. Leerer String = deaktiviert. |
| `TIMEZONE` | `Europe/Berlin` | IANA-Zeitzone, in der `QUIET_HOURS` gilt (Sommer-/Winterzeit wird berücksichtigt). |
| `TOKEN_MAX_AGE_DAYS` | `60` | Tokens ohne Re-Registrierung seit so vielen Tagen werden gelöscht. `0` = aus. |
| `RATE_LIMIT_PER_MINUTE` | `10` | Token-Bucket je Client-IP für `/register` und `/unregister`. `0` = aus. |
| `MAX_TOKENS` | `5000` | Obergrenze gespeicherter Tokens. Neue Tokens bekommen bei voller Tabelle `503`. `0` = unbegrenzt. |

Ungültige Werte für `QUIET_HOURS` (z.B. `23-06`, `25:00-06:00` oder Start
gleich Ende) und unbekannte `TIMEZONE`-Namen lassen den Server schon beim
Start mit einer klaren Fehlermeldung abbrechen, statt still falsch zu laufen.

## Deploy mit Coolify

Zielbild: `https://tickle.transio.de`, gebaut direkt aus diesem Repo.

1. **DNS:** A-Record (bzw. AAAA) für `tickle.transio.de` auf die IP des
   Coolify-Servers setzen.
2. **Ressource anlegen:** In Coolify *New Resource → Application* mit dem
   Git-Repo, dann:
   - **Build Pack:** `Dockerfile`
   - **Base Directory:** `/server` (Dockerfile-Pfad bleibt `/Dockerfile`)
   - **Ports Exposes:** `8000` (keine Port-Mappings auf den Host nötig)
   - **Domains:** `https://tickle.transio.de`. TLS (Let's Encrypt) erledigt
     der Coolify-Proxy (Traefik) automatisch.
3. **Persistent Storage:** unter *Storages* ein Volume mit Destination Path
   `/data` anlegen. Ohne Volume sind die registrierten Tokens nach jedem
   Redeploy weg. Der Container läuft als Nicht-root-User `app` (uid 1000);
   ein frisch angelegtes Volume übernimmt die Besitzrechte von `/data` aus dem
   Image. Wird stattdessen ein Host-Verzeichnis (Bind Mount) verwendet, muss es
   vorher `chown -R 1000:1000 <pfad>` bekommen, sonst bricht der Start mit
   einer Fehlermeldung zu `DB_PATH` ab.
4. **Environment Variables** (beide als Secret behandeln):

   | Variable | Wert |
   |---|---|
   | `API_KEY` | Ausgabe von `openssl rand -hex 32` |
   | `GOOGLE_APPLICATION_CREDENTIALS_JSON` | kompletter Inhalt der Service-Account-JSON, als mehrzeilige Variable einfügen |

   `GOOGLE_APPLICATION_CREDENTIALS` und `DB_PATH` nicht setzen (`DB_PATH`
   steht im Dockerfile schon auf `/data/tokens.db`). Optional:
   `QUIET_HOURS`, `TIMEZONE`, `MAX_TOKENS`, `TICKLE_INTERVAL_MINUTES`,
   `TOKEN_MAX_AGE_DAYS`, `RATE_LIMIT_PER_MINUTE` (siehe Tabelle oben).
5. **Health Check:** Pfad `/healthz`, Port `8000`. Das Dockerfile bringt
   einen eigenen `HEALTHCHECK` mit, der per Python (ohne curl/wget, die im
   slim-Image fehlen) `/healthz` abfragt. Sollte Coolify den Deploy trotzdem
   als unhealthy markieren, erwartet meist der UI-Health-Check curl/wget im
   Container: dann den Health-Check in der Coolify-UI ausschalten, der
   Dockerfile-`HEALTHCHECK` bleibt aktiv.
6. **Nur eine Instanz** betreiben (keine Replicas): SQLite, das
   In-Memory-Rate-Limit und der Tickle-Loop sind auf genau einen Prozess
   ausgelegt. Mehrere Instanzen würden jedes Gerät mehrfach anstupsen.
7. **Deploy** auslösen und im Log auf
   `FCM-Tickle-Server gestartet (...)` achten.

**Smoke-Test** nach dem Deploy:

```bash
curl -s https://tickle.transio.de/healthz
# {"status":"ok"}

curl -s -w '\n%{http_code}\n' -X POST https://tickle.transio.de/register \
  -H "X-Api-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{"token":"smoke-test-token"}'
# {"status":"registered"}
# 200

# Ohne bzw. mit falschem Key muss 401 kommen:
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://tickle.transio.de/register \
  -H "Content-Type: application/json" -d '{"token":"x"}'
# 401

# Aufräumen (sonst entfernt es spätestens die nächste Tickle-Runde, weil FCM
# das Fake-Token als ungültig meldet):
curl -s -X POST https://tickle.transio.de/unregister \
  -H "X-Api-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{"token":"smoke-test-token"}'
# {"status":"unregistered"}
```

**App-Seite:** Die App bekommt URL und Key zur Build-Zeit als `BuildConfig`
(`local.properties`: `tickle.server.url`, `tickle.api.key`, alternativ die
Env-Variablen `TICKLE_SERVER_URL` und `TICKLE_API_KEY`; die URL ist per Default
`https://tickle.transio.de`).

## Setup auf einem VPS mit Docker Compose

### 1. Repo/Server-Verzeichnis auf den VPS bringen

```bash
scp -r server/ user@vps:/opt/hiuni-tickle
ssh user@vps
cd /opt/hiuni-tickle
```

(Oder per `git clone` + `cd UniHi/server`.)

### 2. Firebase-Service-Account-Key besorgen

In der [Firebase Console](https://console.firebase.google.com/) im
HiUni-Projekt unter **Projekteinstellungen → Dienstkonten → Neuen privaten
Schlüssel generieren**. Die heruntergeladene JSON-Datei als
`service-account.json` neben die `docker-compose.yml` legen:

```bash
mv ~/Downloads/hiuni-firebase-adminsdk-xxxxx.json ./service-account.json
```

Diese Datei **niemals** ins Git-Repo committen (ist über `.gitignore`
ausgeschlossen).

### 3. `.env` aus der Vorlage befüllen

```bash
cp .env.example .env
openssl rand -hex 32   # -> als API_KEY in .env eintragen
```

`.env` danach mit einem Editor öffnen und `API_KEY` setzen. Die restlichen
Werte können normalerweise unverändert bleiben.

### 4. Container starten

```bash
docker compose up -d --build
curl http://127.0.0.1:8000/healthz
# {"status":"ok"}
```

Davor gehört ein Reverse-Proxy mit TLS (nginx/Caddy/Traefik), der
`X-Forwarded-For` setzt (siehe Sicherheitshinweise).

## API

Alle Endpunkte außer `/healthz` erfordern den Header `X-Api-Key: <API_KEY>`.

| Methode | Pfad          | Body                | Beschreibung                              |
|---------|---------------|---------------------|-------------------------------------------|
| GET     | `/healthz`    | –                   | Health-Check, kein Auth nötig             |
| POST    | `/register`   | `{"token": "..."}`  | Token speichern bzw. `last_seen` aktualisieren (idempotent) |
| POST    | `/unregister` | `{"token": "..."}`  | Token entfernen                           |

Statuscodes, auf die sich die App verlässt:

| Code | Bedeutung | Verhalten der App |
|---|---|---|
| `200` | Erfolg | fertig |
| `401` | API-Key fehlt oder ist falsch | dauerhafter Konfigurationsfehler, kein Retry |
| `422` | Body ungültig (z.B. leeres Token) | dauerhafter Fehler, kein Retry |
| `429` | Rate-Limit überschritten | Retry mit Backoff |
| `503` | `MAX_TOKENS` erreicht (nur für **neue** Tokens; bekannte Tokens dürfen sich immer re-registrieren) | Retry mit Backoff |
| `5xx` | sonstiger Serverfehler | Retry mit Backoff |

## Sicherheitshinweise

- **Der API-Key ist faktisch öffentlich.** Er wird zur Build-Zeit als
  `BuildConfig`-Feld in die APK eingebaut und lässt sich daraus mit wenig
  Aufwand auslesen. Er hält nur zufällige Scanner und Bots fern, die
  `/register` blind ansprechen. Der eigentliche Schutz liegt woanders:
  - Der Server speichert **ausschließlich** FCM-Tokens plus Zeitstempel
    (`created_at`, `last_seen`) in einer lokalen SQLite-Datei. Keine
    Nutzerdaten, keine Mail-Inhalte, keine Zugangsdaten. Es gibt nichts
    abzugreifen außer Tokens, und mit einem FCM-Token allein kann ohne den
    Service-Account-Key niemand Nachrichten senden.
  - Ungültige oder fremde Tokens (FCM meldet `UNREGISTERED`,
    `SENDER_ID_MISMATCH` oder `INVALID_ARGUMENT`) fliegen spätestens in der
    nächsten Tickle-Runde automatisch raus. Wer Fantasie-Tokens registriert,
    füllt die Datenbank also nur für höchstens eine Runde.
  - `RATE_LIMIT_PER_MINUTE` bremst Register-Fluten je IP, `MAX_TOKENS` deckelt
    die Tabellengröße. Ein Angreifer kann im schlimmsten Fall neue
    Registrierungen bis zur nächsten Runde blockieren (`503`, die App
    versucht es später erneut), bestehende Geräte bleiben unberührt.
  - Trotzdem: den Key nicht unnötig streuen und bei Bedarf rotieren (neuer
    Key im Server + neuer App-Build).
- **Service-Account-Key geheim halten.** Das ist das einzige echte Geheimnis:
  nur als Coolify-Secret bzw. read-only gemountete Datei, nie im Repo.
- **Immer hinter einem Reverse-Proxy mit TLS betreiben**, niemals den Port
  `8000` direkt ins Internet hängen. Docker Compose bindet standardmäßig nur
  an `127.0.0.1:8000`, unter Coolify gibt es ohne Port-Mapping ohnehin nur den
  Weg über Traefik.
- **X-Forwarded-For wird vertraut:** Für das Rate-Limit nimmt der Server die
  erste Adresse aus `X-Forwarded-For` als Client-IP. Das passt, weil der
  Coolify-Proxy (Traefik) den Header setzt; in der Standardkonfiguration
  vertraut Traefik vom Client mitgeschickten `X-Forwarded-*`-Headern nicht und
  trägt selbst die echte Client-IP ein. Wer Traefik mit
  `forwardedHeaders.insecure` betreibt oder ein CDN davorschaltet, sollte das
  prüfen. Ist der Container ohne Proxy direkt erreichbar, könnte ein Client
  den Header fälschen und das Rate-Limit umgehen; auch deshalb nie direkt
  exponieren.
- **Token-Purge:** Der Tickle-Loop räumt bei jeder Runde (auch in der
  Ruhezeit) Tokens auf, deren `last_seen` älter als `TOKEN_MAX_AGE_DAYS`
  (Default 60) ist. Die App re-registriert sich bei jedem Start und bei
  FCM-Token-Rotate idempotent, aktualisiert `last_seen` also weit häufiger;
  der Default fängt daher nur wirklich tote Geräte ab.
- **Rate-Limit:** In-Memory-Token-Bucket je Client-IP (kein Redis). Bei
  Überschreitung antwortet der Server mit `429`. Das Bucket lebt im
  Prozessspeicher, gilt also pro Instanz (es soll ohnehin nur eine geben).
- **Container läuft als Nicht-root-User** (`app`, uid 1000).

## Lokale Entwicklung und Tests

```bash
cd server
python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt -r requirements-dev.txt

# Tests (Firebase ist komplett gemockt, keine Credentials/kein Netz nötig)
pytest

# Server lokal starten
cp .env.example .env   # ausfüllen
export $(grep -v '^#' .env | xargs)
uvicorn app:app --reload
```
