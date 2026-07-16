# HiUni FCM-Tickle-Server

Ein sehr schlanker Server, der die HiUni-App periodisch per Firebase Cloud
Messaging (FCM) "anstupst" ("tickle"), damit sie im Hintergrund selbst nach
neuen Mails schaut.

## Das Tickle-Modell — und warum der Server keine Zugangsdaten kennt

Der Server hat **keinen Zugriff auf Postfächer, Passwörter oder Mail-Inhalte**.
Er kennt ausschließlich FCM-Device-Tokens — also nur "an welches Gerät kann
ich eine Push-Nachricht schicken", nicht "was steht in wessen Mails".

Alle 15 Minuten (konfigurierbar) schickt der Server an jedes registrierte
Gerät eine **stille FCM-Data-Message** ohne Notification-Payload:

```json
{"type": "sync_tickle"}
```

Der Typ ist über die Env `TICKLE_TYPE` konfigurierbar (Default `sync_tickle`):
Neue App-Versionen verstehen `sync_tickle` (Mail-Refresh **plus** gestaffelter
Feature-Prefetch für Noten/Kurse/Learnweb usw.), alte Versionen nur `mail_tickle`
(reiner Mail-Wecker) — bei Bestands-Deploys mit älteren Clients daher ggf.
`TICKLE_TYPE=mail_tickle` setzen.

Diese Nachricht wird mit `AndroidConfig(priority="high")` verschickt, damit
sie das Gerät auch aus dem Doze-Modus aufweckt. Die App empfängt sie in
ihrem `FirebaseMessagingService`, merkt "Zeit, nachzuschauen", und holt sich
die eigentlichen Mails direkt vom Mailserver/-provider — mit den
Zugangsdaten, die ausschließlich auf dem Gerät des Nutzers liegen.

Das ist bewusst so gebaut wie bei WhatsApp: die Push-Infrastruktur weiß nur
"jemand hat vermutlich eine neue Nachricht", nicht was drinsteht. Fällt der
Tickle-Server aus oder wird er kompromittiert, sind maximal Device-Tokens
betroffen — keine Postfach-Zugangsdaten, keine Mail-Inhalte.

## Setup auf einem VPS mit Docker

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

Diese Datei **niemals** ins Git-Repo committen (ist bereits über
`.gitignore` per `*service-account*.json` ausgeschlossen).

### 3. `.env` aus der Vorlage befüllen

```bash
cp .env.example .env
openssl rand -hex 32   # -> als API_KEY in .env eintragen
```

`.env` danach mit einem Editor öffnen und `API_KEY` setzen. Die restlichen
Werte (`TICKLE_INTERVAL_MINUTES`, `GOOGLE_APPLICATION_CREDENTIALS`,
`DB_PATH`) können normalerweise unverändert bleiben.

### 4. Container starten

```bash
docker compose up -d --build
```

### Alternative: Deployment mit Coolify (ohne Compose/Datei-Mount)

In Coolify die App als Dockerfile-Build auf das `server/`-Verzeichnis zeigen
lassen und die Konfiguration komplett über Environment-Variablen machen —
statt die Service-Account-Datei zu mounten, den **kompletten JSON-Inhalt**
als mehrzeilige Variable eintragen:

| Variable | Wert |
|---|---|
| `API_KEY` | `openssl rand -hex 32` |
| `GOOGLE_APPLICATION_CREDENTIALS_JSON` | kompletter Inhalt der Service-Account-JSON (mehrzeilig einfügen) |
| `TICKLE_INTERVAL_MINUTES` | z.B. `15` |
| `DB_PATH` | `/data/tokens.db` |

`GOOGLE_APPLICATION_CREDENTIALS_JSON` hat Vorrang vor dem Datei-Pfad in
`GOOGLE_APPLICATION_CREDENTIALS` — letzterer kann dann leer bleiben.
Wichtig: In Coolify für `/data` ein Persistent Volume anlegen (sonst sind
die registrierten Tokens nach jedem Redeploy weg) und den Port 8000 über
den Coolify-Proxy mit TLS exponieren.

Health-Check:

```bash
curl http://127.0.0.1:8000/healthz
# {"status":"ok"}
```

### 5. App auf den API-Key + Endpunkt konfigurieren

Die App braucht die Server-URL und denselben `API_KEY`, um sich per
`POST /register` mit ihrem FCM-Token einzutragen. Das erfolgt typischerweise
über einen Reverse-Proxy mit eigenem Domainnamen (siehe Sicherheitshinweise).

## API

Alle Endpunkte außer `/healthz` erfordern den Header `X-Api-Key: <API_KEY>`.

| Methode | Pfad          | Body                | Beschreibung                              |
|---------|---------------|----------------------|--------------------------------------------|
| GET     | `/healthz`    | –                    | Health-Check, kein Auth nötig              |
| POST    | `/register`   | `{"token": "..."}`  | Token speichern/aktualisieren (idempotent) |
| POST    | `/unregister` | `{"token": "..."}`  | Token entfernen                            |

## Sicherheitshinweise

- **API-Key geheim halten.** Er steht nur in `.env` (nicht im Repo, siehe
  `.gitignore`). Ohne gültigen `X-Api-Key` können weder Tokens registriert
  noch entfernt werden.
- **Immer hinter einem Reverse-Proxy mit TLS betreiben** (z.B. nginx/Caddy/
  Traefik), niemals den Port `8000` direkt ungeschützt ins Internet hängen.
  Der Container bindet standardmäßig nur an `127.0.0.1:8000`.
- **Service-Account-Datei read-only mounten** (siehe `docker-compose.yml`)
  und außerhalb des Git-Repos aufbewahren.
- Der Server speichert **ausschließlich** FCM-Tokens plus Zeitstempel
  (`created_at`, `last_seen`) in einer lokalen SQLite-Datei — keine
  Nutzerdaten, keine Mail-Inhalte, keine Zugangsdaten.
- Ungültige/abgelaufene Tokens (FCM meldet `UNREGISTERED` o.ä.) werden beim
  nächsten Tickle-Versuch automatisch aus der Datenbank entfernt.

## Lokale Entwicklung ohne Docker

```bash
cd server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # ausfüllen
export $(grep -v '^#' .env | xargs)
uvicorn app:app --reload
```
