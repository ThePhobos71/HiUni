# Neuen Release erstellen

Kurze Anleitung für den Alltag. Wie Signing, Secrets und der Workflow im Detail
funktionieren, steht in [RELEASE_SIGNING.md](RELEASE_SIGNING.md).

**Kurzfassung:**

```bash
git switch main && git pull
./gradlew testDebugUnitTest
git push origin main
git tag v1.2.0
git push origin v1.2.0
```

Danach baut GitHub Actions die signierte APK und veröffentlicht sie als Release. Installierte
Apps finden das Update innerhalb von 6 bis 12 Stunden von selbst, oder sofort über
*Einstellungen → App-Updates → Nach Updates suchen*.

---

## 1. Versionsnummer wählen

Format `vMAJOR.MINOR.PATCH`, zum Beispiel `v1.2.0`. Welche Stelle erhöht wird:

| Änderung | Beispiel | Neue Version |
|---|---|---|
| Nur Fehlerbehebungen | Offline-Banner-Fix | `v1.2.0` → `v1.2.1` |
| Neue Funktionen | Lernplan-Generator | `v1.2.1` → `v1.3.0` |
| Großer Umbau, Daten oder Bedienung ändern sich grundlegend | neues Navigationskonzept | `v1.3.0` → `v2.0.0` |

Regeln:

- **Immer höher als der letzte Release.** Android installiert ein Update nur, wenn der
  `versionCode` steigt (`MAJOR * 10000 + MINOR * 100 + PATCH`).
- MINOR und PATCH gehen nur bis 99. Danach die nächsthöhere Stelle erhöhen.
- **Einen veröffentlichten Tag nie wiederverwenden**, auch nicht nach einem Rückzug.
- Den letzten Release findest du mit `git tag -l 'v*' --sort=-v:refname | head -1`.

## 2. Vor dem Taggen

1. Auf `main` sein und aktuell: `git switch main && git pull`.
2. Tests lokal grün: `./gradlew testDebugUnitTest`.
3. Bei Schema-Änderungen: neues Room-Schema-JSON unter `app/schemas/` ist committet.
4. Optional, aber empfohlen bei größeren Änderungen: Release-Build lokal durchlaufen lassen,
   denn R8 verkleinert und verschleiert nur dort:
   `./gradlew assembleRelease -PHIUNI_VERSION_NAME=1.2.0`
   (ohne `keystore.properties` entsteht eine unsignierte APK, das reicht als Build-Test).
5. **Erst `main` pushen, dann taggen.** Der Tag muss auf einen Commit zeigen, der auf GitHub
   existiert: `git push origin main`.

## 3. Release auslösen

```bash
git tag v1.2.0
git push origin v1.2.0
```

Alternativ ohne Terminal: GitHub → *Actions → Release → Run workflow*, Version `1.2.0`
eintragen. Der Tag wird dann auf dem gewählten Branch-Stand angelegt.

## 4. Lauf beobachten und prüfen

1. GitHub → *Actions → Release*: der Lauf dauert einige Minuten (Tests, R8, Signatur).
2. Bei Erfolg steht unter *Releases* der Eintrag **HiUni 1.2.0** mit der Datei
   `HiUni-1.2.0.apk` und dem automatisch erzeugten Changelog (alle Commits seit dem
   vorherigen Tag).
3. Kurzer Praxistest auf dem Handy: *Einstellungen → App-Updates → Nach Updates suchen*,
   Update laden und installieren, App starten, einmal Home, Mensa, Kalender und Mail öffnen.
   Gerade nach Abhängigkeits-Updates zeigt erst dieser Test, ob R8 etwas kaputt gemacht hat.
4. Den Changelog bei Bedarf auf GitHub nachbearbeiten (*Edit release*). Die App zeigt genau
   diesen Text an.

## 5. Wenn etwas schiefgeht

**Der Workflow ist gescheitert, es gibt noch keinen Release:** Fehler beheben, committen,
pushen, dann denselben Tag neu setzen:

```bash
git push --delete origin v1.2.0
git tag -d v1.2.0
git tag v1.2.0
git push origin v1.2.0
```

Das ist erlaubt, solange **kein** Release veröffentlicht wurde, denn noch hat kein Gerät
diese Version.

**Der Release ist draußen, aber kaputt:** nicht löschen und nicht neu taggen, sondern den
Fehler beheben und sofort einen neuen PATCH-Release nachschieben (`v1.2.1`). Optional den
kaputten Release auf GitHub als *Pre-release* markieren, dann bieten ihn Apps nicht mehr an
(der Updater ignoriert Vorabversionen).

**Typische Fehler im Lauf:**

| Meldung | Ursache | Lösung |
|---|---|---|
| Secret fehlt (`HIUNI_KEYSTORE_BASE64`, `GOOGLE_SERVICES_JSON_BASE64`) | Repo-Secret nicht angelegt oder falsch benannt | *Settings → Secrets and variables → Actions* prüfen |
| `apksigner`: keine signierte `app-release.apk` | Keystore-Passwort oder Alias falsch | Secrets `HIUNI_KEYSTORE_PASSWORD`, `HIUNI_KEY_ALIAS`, `HIUNI_KEY_PASSWORD` prüfen |
| Ungültige Version | Tag passt nicht zu `vMAJOR.MINOR.PATCH`, oder MINOR/PATCH über 99 | Tag löschen und korrekt neu setzen (siehe oben) |
| Unit-Tests rot | Fehler im Code | lokal `./gradlew testDebugUnitTest`, fixen, neu taggen |
| R8-Fehler (`Missing class ...`) | neue Bibliothek ohne Keep-Regel | Regel in `app/proguard-rules.pro` ergänzen, nicht Minify abschalten |

**Ein Nutzer meldet einen Absturz in einem Release-Build:** Die Stacktraces sind durch R8
verschleiert. Die passende `mapping.txt` liegt als Artefakt am jeweiligen Workflow-Lauf
(*Actions → Release → Lauf → Artifacts*) und lässt sich mit `retrace` aus den Android
Build-Tools zurückübersetzen.

## 6. Vorabversionen (Beta)

Tags mit Suffix, zum Beispiel `v1.3.0-beta1`, werden als *Pre-release* veröffentlicht. Der
In-App-Updater bietet sie **nicht** an, sie sind nur zum manuellen Installieren gedacht.
Achtung: `1.3.0-beta1` und `1.3.0` haben denselben `versionCode`. Wer die Beta installiert
hat, bekommt das finale `1.3.0` deshalb nicht als Update. Danach direkt `v1.3.1` veröffentlichen
oder Betas nur auf einem Testgerät nutzen.

## 7. Nach dem Release

- Größere Änderungen in `docs/FEATURES.md` und, wo KI beteiligt war, in `AI_USAGE.md`
  festhalten.
- Den Keystore niemals neu erzeugen: mit einem neuen Schlüssel ließen sich bestehende
  Installationen nicht mehr aktualisieren (siehe *Backup* in
  [RELEASE_SIGNING.md](RELEASE_SIGNING.md)).
