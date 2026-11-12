# HiUni Release-Signing und Releases

> **Stand:** implementiert. `app/build.gradle.kts` signiert Release-Builds, sobald ein
> vollständiger Keystore konfiguriert ist, und leitet die Version aus dem Git-Tag ab.
> `.github/workflows/release.yml` baut bei jedem Tag-Push eine signierte APK und
> veröffentlicht sie als GitHub Release. Der In-App-Updater liest diese Releases.

## Überblick

| Wo | Woher kommen Keystore und Passwörter |
|---|---|
| CI (GitHub Actions) | Env-Variablen `HIUNI_KEYSTORE_*`, gefüttert aus Repo-Secrets |
| Lokal (Android Studio, CLI) | `keystore.properties` im Repo-Root (gitignored) |
| Kein (vollständiger) Keystore | Build läuft weiter, Release-APK bleibt **unsigniert**, Gradle loggt eine Warnung |

Env-Variablen haben Vorrang vor `keystore.properties`. `*.jks`, `*.keystore` und
`keystore.properties` stehen in `.gitignore`. Debug-Builds nutzen weiterhin den
Standard-Debug-Keystore.

---

## 1. Einen Release veröffentlichen

```bash
git switch main && git pull
git tag v1.0.0
git push origin v1.0.0
```

Der Tag-Push startet den Workflow **Release** (`.github/workflows/release.yml`). Alternativ
unter *Actions → Release → Run workflow* mit einer Version wie `1.0.0` manuell starten; der
Tag `v1.0.0` wird dann auf dem gewählten Commit angelegt, falls er noch fehlt.

Der Workflow macht der Reihe nach:

1. Checkout mit voller Historie (für den Changelog).
2. Version aus dem Tag bzw. der Eingabe ableiten und prüfen.
3. JDK 17 (Temurin) und Gradle-Cache einrichten. Das Android SDK bringt `ubuntu-latest` mit.
4. Keystore aus `HIUNI_KEYSTORE_BASE64` nach `$RUNNER_TEMP/hiuni-release.jks` schreiben und
   `HIUNI_KEYSTORE_FILE` setzen.
5. `app/google-services.json` aus `GOOGLE_SERVICES_JSON_BASE64` schreiben. Fehlt das Secret,
   bricht der Workflow ab, denn ein Release ohne Push wäre kaputt.
6. `./gradlew testDebugUnitTest assembleRelease -PHIUNI_VERSION_NAME=<version>`.
7. Signatur mit `apksigner verify` prüfen und den SHA-256 des Zertifikats ausgeben. Fehlt die
   signierte `app-release.apk`, scheitert der Job.
8. APK in `HiUni-<version>.apk` umbenennen.
9. Changelog aus `git log <vorheriger Tag>..HEAD --pretty='- %s'` erzeugen (erster Release:
   alle Commits, maximal 200).
10. GitHub Release `HiUni <version>` mit Tag `v<version>`, Changelog und APK anlegen.
    Versionen mit Suffix (`1.1.0-beta1`) werden als Prerelease markiert.
11. `mapping.txt` als Workflow-Artefakt sichern (für das Deobfuskieren von Stacktraces).
12. Keystore und `google-services.json` im `always()`-Step löschen.

### Benötigte Repo-Secrets

*Settings → Secrets and variables → Actions → Repository secrets*:

| Secret | Inhalt |
|---|---|
| `HIUNI_KEYSTORE_BASE64` | Keystore (PKCS12) als base64, eine Zeile |
| `HIUNI_KEYSTORE_PASSWORD` | Store-Passwort |
| `HIUNI_KEY_ALIAS` | `upload-key` |
| `HIUNI_KEY_PASSWORD` | Key-Passwort (bei PKCS12 identisch mit dem Store-Passwort) |
| `GOOGLE_SERVICES_JSON_BASE64` | `app/google-services.json` als base64 |
| `TICKLE_API_KEY` | API-Key des Tickle-Servers |
| `TMDB_API_KEY` | TMDB-Key für die Kino-Poster |

base64 ohne Zeilenumbrüche erzeugen:

```bash
base64 -i ~/keys/hiuni/hiuni-upload.jks | tr -d '\n' | pbcopy   # macOS
base64 -w0 ~/keys/hiuni/hiuni-upload.jks                         # Linux
```

Secrets werden nie ge`echo`t; GitHub maskiert sie zusätzlich in den Logs.

---

## 2. Versionsschema

Die Version kommt aus dem Git-Tag. CI übergibt sie als Gradle-Property
`HIUNI_VERSION_NAME` (alternativ gleichnamige Env-Variable):

| Tag | `versionName` | `versionCode` |
|---|---|---|
| `v0.1.0` | `0.1.0` | 100 |
| `v1.0.0` | `1.0.0` | 10000 |
| `v1.4.2` | `1.4.2` | 10402 |
| `v1.5.0-beta1` | `1.5.0-beta1` | 10500 |
| ohne Property (lokal) | `0.0.0-dev` | 1 |

Regeln:

- `versionCode = MAJOR * 10000 + MINOR * 100 + PATCH`. MINOR und PATCH müssen zwischen
  0 und 99 liegen, sonst bricht Gradle mit einer klaren Meldung ab. Gleiches gilt für
  Versionen, die nicht dem Muster `MAJOR.MINOR.PATCH[-suffix]` folgen.
- Ein Suffix ändert den `versionCode` nicht. `1.5.0-beta1` und `1.5.0` haben beide 10500;
  Android installiert das finale `1.5.0` über die Beta nur, wenn man sie vorher deinstalliert
  oder die finale Version eine höhere Nummer bekommt. Betas daher sparsam einsetzen.
- Tags nie wiederverwenden und nie zurückgehen: Android akzeptiert Updates nur mit
  höherem `versionCode`.
- Debug-Builds hängen weiterhin `-debug` an den `versionName` und `.debug` an die
  `applicationId`, Debug und Release liegen also parallel auf dem Gerät.
- Lokal mit fester Version bauen: `./gradlew assembleRelease -PHIUNI_VERSION_NAME=1.4.2`.

Die App liest die Werte über `BuildConfig.VERSION_CODE` und `BuildConfig.VERSION_NAME`.

---

## 3. Keystore erzeugen (einmalig)

Außerhalb des Repos, RSA 4096, rund 25 Jahre Gültigkeit, Alias `upload-key`:

```bash
mkdir -p ~/keys/hiuni && cd ~/keys/hiuni

keytool -genkeypair \
  -v \
  -keystore hiuni-upload.jks \
  -storetype PKCS12 \
  -alias upload-key \
  -keyalg RSA \
  -keysize 4096 \
  -validity 9125 \
  -dname "CN=Kjell Karstens, OU=HiUni, O=transio, L=Hildesheim, ST=Niedersachsen, C=DE"
```

Bei PKCS12 sind Store- und Key-Passwort identisch.

### Backup: der wichtigste Absatz dieser Datei

Der Keystore ist **nicht ersetzbar**. Geht er oder sein Passwort verloren, kann die App nie
wieder unter derselben Signatur aktualisiert werden: Android verweigert Updates mit anderem
Zertifikat, der In-App-Updater wäre damit tot.

- Keystore **und** Passwort in den Passwortmanager (Datei-Anhang oder base64-Text).
- Zweites, offline Backup (verschlüsselter USB-Stick oder verschlüsseltes Archiv).
- SHA-256-Fingerprint notieren, er erscheint auch im Log jedes Release-Workflows:

```bash
keytool -list -v -keystore hiuni-upload.jks -alias upload-key
```

> **Firebase:** Der SHA-256-Fingerprint des Release-Keys gehört in der Firebase-Console
> zum Android-App-Eintrag `de.transio.hiuni`.

---

## 4. Lokal signiert bauen

Datei `keystore.properties` im **Repo-Root** (nicht in `app/`), Pfad absolut oder relativ
zum Root:

```properties
# keystore.properties, gitignored, niemals committen
storeFile=/Users/kjell/keys/hiuni/hiuni-upload.jks
storePassword=…
keyAlias=upload-key
keyPassword=…
```

```bash
git check-ignore -v keystore.properties   # muss die .gitignore-Zeile ausgeben
./gradlew assembleRelease                 # app/build/outputs/apk/release/app-release.apk
```

Ohne Keystore heißt das Artefakt `app-release-unsigned.apk` und Gradle warnt
`HiUni: kein vollständiger Release-Keystore ...`. Das ist der gewollte Fallback.

Statt der Datei gehen auch die Env-Variablen `HIUNI_KEYSTORE_FILE`,
`HIUNI_KEYSTORE_PASSWORD`, `HIUNI_KEY_ALIAS` und `HIUNI_KEY_PASSWORD`.

---

## 5. Verifikation

```bash
"$ANDROID_HOME"/build-tools/36.0.0/apksigner verify --print-certs --verbose \
  app/build/outputs/apk/release/app-release.apk
```

Erwartet: `Verifies`, v3 `true` und derselbe SHA-256-Digest wie aus `keytool -list -v`.
v1 steht auf `false` (bei minSdk 28 nicht nötig). v2 wird mitsigniert, `apksigner` meldet es
bei minSdk 28 aber als `false`, weil ab API 28 nur die v3-Signatur geprüft wird.

Das R8-Mapping liegt nach jedem Release-Build unter
`app/build/outputs/mapping/release/mapping.txt` und im Workflow-Artefakt `mapping-<version>`.

---

## 6. Forgejo

`.forgejo/workflows/ci.yml` baut nur Debug. Ein Release-Job auf Forgejo bräuchte dieselben
Schritte wie `release.yml`, aber mit absoluten Action-URLs, `android-actions/setup-android`
und ohne `softprops/action-gh-release`. Aktuell veröffentlicht nur GitHub Releases.
