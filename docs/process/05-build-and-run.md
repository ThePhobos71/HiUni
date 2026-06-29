# 05 — Build and Run

> Anleitung für Hesenius oder ein anderes Drittes, das Repo auf einem fremden Rechner zu bauen und zu starten. Ziel: Wir müssen die App auf mehreren Devices laufen lassen können (siehe Kursanforderung).

## Voraussetzungen

| Werkzeug | Version | Wie installieren |
|---|---|---|
| JDK | 17 | macOS: `brew install --cask temurin@17` · Win: AdoptOpenJDK 17 |
| Android Studio | Ladybug (2024.2.x) oder neuer | [developer.android.com/studio](https://developer.android.com/studio) |
| Android SDK Platform | 36 (compile + target) | Android Studio → SDK Manager |
| Android Emulator oder Device | API 26+ (Android 8.0+) | Pixel 7a empfohlen, Tablets ab API 30 |

## Repo klonen + bauen

```bash
git clone https://github.com/<owner>/UniHi.git
cd UniHi

# Debug-APK bauen
./gradlew assembleDebug

# Lint-Check (sollte grün sein)
./gradlew lintDebug

# Tests
./gradlew test

# Auf angeschlossenes Device installieren
./gradlew installDebug
```

Debug-APK landet unter `app/build/outputs/apk/debug/app-debug.apk`.

## Erste-Start-Checkliste

1. App startet im **Demo-Modus** — kein Login erforderlich für die meisten Features.
2. **Settings → Mensa-Standort:** "Mensa Uni Hildesheim" auswählen (Default).
3. **Settings → Email (optional):** Eigene IMAP-Credentials für Mail-Feature. Ohne Credentials bleibt Mail-Screen leer.
4. **Settings → Learnweb (optional):** Uni-Hi-Login für Kurs- und Assignment-Sync. Ohne Login funktioniert die App ansonsten vollständig.

## Häufige Probleme

| Problem | Ursache | Fix |
|---|---|---|
| Gradle-Sync schlägt fehl mit "KSP2 requires …" | KSP2 aktiviert | `gradle.properties` muss `ksp.useKSP2=false` enthalten (Default, nicht ändern). Siehe ADR-0007. |
| `assembleDebug` schlägt fehl mit "compileSdk 36 missing" | SDK Platform 36 nicht installiert | Android Studio → SDK Manager → "Android 14 (UpsideDownCake)" und neuere installieren |
| Room-Migration-Test schlägt fehl | `app/schemas/` nicht committet oder neue Migration unvollständig | `./gradlew assembleDebug` einmal lokal laufen lassen, dann `app/schemas/de.transio.hiuni.core.database.AppDatabase/` prüfen |
| App crasht beim Start auf älteren Devices | Notification-Permission auf Android 13+ nicht gewährt | App fragt Permission an, akzeptieren — oder Settings → Apps → HiUni → Permissions |
| Mensa zeigt keine Daten | Kein Netzwerk oder STW-ON-API down | Pull-to-Refresh, sonst zeigt Repository den Cache (falls vorhanden) |

## Build-Verifikation auf fremdem Mac (anekdotisch)

_TODO_: Wenn jemand außer Kjell das Repo getestet hat (Johann auf seinem Device?), kurz die Diff-Erfahrungen hier.

## Backend-Komponente — `hiuni-relay`

Optional und nur für das Reviews-Federation-Feature relevant. Standalone Ktor-Server, läuft per Docker:

```bash
cd hiuni-relay
cp .env.example .env  # Werte ausfüllen
docker compose up --build
```

Health-Check: `curl http://localhost:8080/health`

Ohne Relay funktioniert die App lokal vollständig (Reviews sind dann nur auf dem eigenen Device sichtbar — gewünschtes Fail-Soft-Verhalten).

## Tested-On (geplant)

_TODO_: Liste der Devices, auf denen wir final getestet haben.

- Pixel 7a (Android 14, Phone) — Kjell
- _Tablet/Phone Johann ergänzen_
- Emulator: Pixel 8 Pro API 34
- _TODO_ Hesenius' Device-Pool (was bekommen wir gestellt?)
