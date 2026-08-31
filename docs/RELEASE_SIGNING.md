# HiUni Release-Signing

> Kochbuch für den ersten signierten Release-Build. Jeder Schritt ist Copy-Paste-fertig.
> **Stand:** `app/build.gradle.kts` hat noch **keinen** `signingConfigs`-Block — der Release-Build
> ist derzeit `minify + shrinkResources + proguard`, aber **unsigniert**. Diese Doku beschreibt,
> was zu tun ist; sie ist absichtlich noch nicht im Build implementiert.

## Überblick

| Wo | Woher kommen Keystore + Passwörter |
|---|---|
| Lokal (Android Studio, CLI) | `keystore.properties` im Repo-Root (gitignored) |
| CI (Forgejo Actions) | Env-Variablen `HIUNI_KEYSTORE_*`, gefüttert aus Repo-Secrets |
| Kein Keystore vorhanden | Build läuft weiter, Release-APK bleibt **unsigniert** (kein Fail) |

`*.jks`, `*.keystore` und `keystore.properties` stehen in `.gitignore` — das bleibt so.

---

## 1. Keystore erzeugen

Einmalig, **außerhalb** des Repos (z.B. `~/keys/hiuni/`). RSA 4096, ~25 Jahre Gültigkeit,
Alias `upload-key`:

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

- `-validity 9125` = 25 Jahre. Google Play verlangt Gültigkeit mindestens bis 2033 —
  lieber deutlich darüber.
- `-storetype PKCS12` ist das moderne Format; das alte JKS-Format wirft bei jedem Aufruf
  eine Migrationswarnung.
- `keytool` fragt interaktiv nach Store- und Key-Passwort. Zwei **unterschiedliche**,
  lange Passwörter nehmen und in den Passwortmanager legen.

### Backup — der wichtigste Absatz dieser Datei

Der Keystore ist **nicht ersetzbar**. Geht er verloren (oder das Passwort), kann die App
nie wieder unter derselben Signatur aktualisiert werden: Android verweigert die Installation
eines Updates mit anderem Signaturzertifikat. Konsequenz wäre eine neue `applicationId` und
ein Neuanfang bei null Installationen.

Also:

- Keystore **und** Passwörter in den Passwortmanager (Keystore als Datei-Anhang oder base64-Text).
- Zweites, offline Backup (verschlüsselter USB-Stick / verschlüsseltes Archiv im Backup).
- SHA-Fingerprints notieren, damit man später prüfen kann, ob man den richtigen Keystore hat:

```bash
keytool -list -v -keystore hiuni-upload.jks -alias upload-key
```

> **Firebase-Nebeneffekt:** Das Projekt nutzt FCM (`google-services`-Plugin). Der
> SHA-256-Fingerprint des Release-Keys muss in der Firebase-Console beim Android-App-Eintrag
> hinterlegt werden, sonst funktionieren signaturgebundene Dienste im Release-Build nicht.

---

## 2. Lokal: `keystore.properties`

Datei im **Repo-Root** anlegen (nicht in `app/`), Pfad relativ zum Root oder absolut:

```properties
# keystore.properties — gitignored, niemals committen
storeFile=/Users/kjell/keys/hiuni/hiuni-upload.jks
storePassword=…
keyAlias=upload-key
keyPassword=…
```

Kurz prüfen, dass Git die Datei wirklich ignoriert:

```bash
git check-ignore -v keystore.properties   # muss die .gitignore-Zeile ausgeben
```

---

## 3. Snippet für `app/build.gradle.kts`

`import java.util.Properties` steht bereits oben in der Datei (wird für `local.properties`
und den TMDB-Key gebraucht). Der folgende Block kommt **vor** `android { … }`, direkt unter
den existierenden `localProperties`-Zeilen:

```kotlin
// === Release-Signing ===
// Reihenfolge: keystore.properties (lokal) → Env-Variablen (CI) → nichts (unsigniert).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val ksStoreFile = signingValue("storeFile", "HIUNI_KEYSTORE_FILE")
val ksStorePassword = signingValue("storePassword", "HIUNI_KEYSTORE_PASSWORD")
val ksKeyAlias = signingValue("keyAlias", "HIUNI_KEY_ALIAS")
val ksKeyPassword = signingValue("keyPassword", "HIUNI_KEY_PASSWORD")

val resolvedKeystore = ksStoreFile?.let { rootProject.file(it) }?.takeIf { it.exists() }
val hasReleaseSigning = resolvedKeystore != null &&
        ksStorePassword != null && ksKeyAlias != null && ksKeyPassword != null

if (!hasReleaseSigning) {
    logger.lifecycle("HiUni: kein Release-Keystore gefunden — Release-APK bleibt unsigniert.")
}
```

Dann in `android { … }` einen `signingConfigs`-Block **über** `buildTypes` einfügen:

```kotlin
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = resolvedKeystore
                storePassword = ksStorePassword
                keyAlias = ksKeyAlias
                keyPassword = ksKeyPassword
                // minSdk 28 ⇒ v1 (JAR-Signing) ist nicht mehr nötig
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
```

Und im bestehenden `release { … }`-Block eine Zeile ergänzen:

```kotlin
        release {
            // null, wenn kein Keystore da ist ⇒ unsignierter Build statt Fail
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
```

Bauen:

```bash
./gradlew assembleRelease   # → app/build/outputs/apk/release/app-release.apk
./gradlew bundleRelease     # → app/build/outputs/bundle/release/app-release.aab (für Play)
```

Ohne Keystore heißt das Artefakt `app-release-unsigned.apk` — das ist der gewollte Fallback,
kein Fehler.

---

## 4. CI auf Forgejo

### 4.1 Secrets hinterlegen

Keystore base64-kodieren (eine Zeile, ohne Umbrüche):

```bash
# macOS
base64 -i ~/keys/hiuni/hiuni-upload.jks | tr -d '\n' | pbcopy
# Linux
base64 -w0 ~/keys/hiuni/hiuni-upload.jks
```

In Forgejo: **Repo → Settings → Actions → Secrets → Add Secret**. Vier Secrets, Passwörter
bewusst getrennt vom Keystore:

| Secret | Inhalt |
|---|---|
| `KEYSTORE_B64` | base64-String von oben |
| `KEYSTORE_PASSWORD` | Store-Passwort |
| `KEY_ALIAS` | `upload-key` |
| `KEY_PASSWORD` | Key-Passwort |

Secrets sind in Forgejo nur in Workflow-Runs sichtbar und werden in Logs maskiert — trotzdem
**nie** `echo`en und keine Fork-PR-Trigger für den Release-Job verwenden.

### 4.2 Beispiel-Job (Snippet, noch kein Workflow im Repo)

Passt als zusätzlicher Job in `.forgejo/workflows/ci.yml` oder als eigene
`.forgejo/workflows/release.yml`. Läuft absichtlich nur auf Tags `v*`:

```yaml
  release:
    name: Release APK
    if: startsWith(github.ref, 'refs/tags/v')
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: https://code.forgejo.org/actions/checkout@v4

      - name: Set up JDK 17
        uses: https://github.com/actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Android SDK
        uses: https://github.com/android-actions/setup-android@v3
        with:
          accept-android-sdk-licenses: true

      - name: Keystore aus Secret dekodieren
        run: |
          echo "${{ secrets.KEYSTORE_B64 }}" | base64 -d > "$RUNNER_TEMP/hiuni-upload.jks"

      - name: Assemble Release
        env:
          HIUNI_KEYSTORE_FILE: ${{ runner.temp }}/hiuni-upload.jks
          HIUNI_KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          HIUNI_KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          HIUNI_KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          chmod +x ./gradlew
          ./gradlew assembleRelease --stacktrace

      - name: Keystore wieder löschen
        if: always()
        run: rm -f "$RUNNER_TEMP/hiuni-upload.jks"

      - name: Upload Release APK
        uses: https://code.forgejo.org/forgejo/upload-artifact@v4
        with:
          name: hiuni-release-apk
          path: app/build/outputs/apk/release/*.apk
```

Details, die man beim ersten Versuch falsch macht:

- `base64 -d` ist Linux/GNU (im Runner-Container korrekt). Auf macOS lokal heißt es `base64 -D`
  bzw. `base64 --decode`.
- `HIUNI_KEYSTORE_FILE` ist ein **absoluter** Pfad. `rootProject.file(…)` akzeptiert das,
  relative Pfade würden gegen das Repo-Root aufgelöst.
- Der Release-Build braucht `app/google-services.json`. Die Datei ist gitignored, muss im CI
  also ebenfalls aus einem Secret geschrieben werden (siehe Kommentar in
  `.forgejo/workflows/ci.yml`).
- Aufräum-Step mit `if: always()`, damit der Keystore auch nach einem Build-Fehler nicht im
  Runner-Workspace liegen bleibt.

---

## 5. Verifikation

`apksigner` liegt in den Build-Tools:

```bash
"$ANDROID_HOME"/build-tools/36.0.0/apksigner verify --print-certs --verbose \
  app/build/outputs/apk/release/app-release.apk
```

Erwartete Ausgabe (gekürzt):

```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Signer #1 certificate DN: CN=Kjell Karstens, OU=HiUni, …
Signer #1 certificate SHA-256 digest: …
```

Der SHA-256-Digest muss mit dem aus `keytool -list -v` (Schritt 1) übereinstimmen. Für AABs
gibt es `apksigner` nicht — dort verifiziert Play selbst, lokal geht
`jarsigner -verify -verbose -certs app-release.aab`.

Zusätzlich sinnvoll:

```bash
# Sitzt das Minify-Mapping da, wo man es für Crash-Deobfuskierung braucht?
ls app/build/outputs/mapping/release/mapping.txt
```

---

## 6. versionCode / versionName pflegen

Aktueller Stand in `app/build.gradle.kts`:

```kotlin
versionCode = 1
versionName = "0.1.0-foundation"
```

Regeln für jeden Release:

- `versionCode` ist die **monoton steigende Ganzzahl**, an der Android Updates erkennt. Vor
  jedem Release +1, niemals zurückdrehen, niemals einen Wert zweimal veröffentlichen.
- `versionName` ist der Anzeigestring (SemVer). Der `-foundation`-Suffix ist ein
  Entwicklungsmarker und sollte beim ersten echten Release fallen (z.B. `0.1.0`).
- Der Debug-Build hängt via `applicationIdSuffix = ".debug"` und
  `versionNameSuffix = "-debug"` an — Debug und Release können also parallel auf dem Gerät
  liegen, und der Debug-Build braucht diesen Keystore nicht.
- Beim Bump auch `CHANGELOG.md` fortschreiben und den Git-Tag passend setzen
  (`git tag -a v0.1.0 -m "…"`), damit der Release-Job aus Schritt 4.2 greift.
