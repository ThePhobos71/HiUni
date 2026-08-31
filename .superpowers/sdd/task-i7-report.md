# I7-Fix Report — peer_cursor.cursorId DB-persistiert

## Files modified

- `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/PeerCursorEntity.kt` — neue Spalte `val lastSeenId: String? = null`
- `app/src/main/java/de/transio/hiuni/core/database/AppDatabase.kt` — `version = 35`
- `app/src/main/java/de/transio/hiuni/core/database/Migrations.kt` — neues `MIGRATION_34_35` (ALTER TABLE peer_cursor ADD COLUMN lastSeenId TEXT) + Eintrag in `ALL_MIGRATIONS`
- `app/src/main/java/de/transio/hiuni/feature/mensa/review/sync/RelayClient.kt` — `@Volatile var lastCursorId` entfernt; `sendHello` liest `existing?.lastSeenId`, `handleFrame` schreibt `lastSeenTs + lastSeenId` atomar in einem `cursors.insert(...)`
- `app/schemas/de.transio.hiuni.core.database.AppDatabase/35.json` — auto-exportiert (KSP roomSchemaArgProvider)

`PeerCursorDao` blieb unverändert: `@Insert(onConflict = REPLACE)` mit Entity-Parameter reicht — der neue Wert kommt automatisch über die Entity rein.

## Build + Test

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (8s)
- `./gradlew :app:testDebugUnitTest --tests "*Review*" --tests "MigrationListTest"` — BUILD SUCCESSFUL
  - `ReviewDaoTest`: 1 / 0 / 0 (tests / failures / errors)
  - `ReviewRepositoryTest`: 7 / 0 / 0
  - `MigrationListTest`: 2 / 0 / 0 (chain 1→35, alle Konstanten registriert)

## Self-review

### (a) Migration ALTER TABLE
`MIGRATION_34_35` führt exakt `ALTER TABLE peer_cursor ADD COLUMN lastSeenId TEXT` aus (in `runCatching` analog zu allen anderen ADD-COLUMN-Migrationen). Spalte ist nullable ohne DEFAULT — passt zu Entity-Default `null`. `MigrationListTest.all_migrations_form_unbroken_chain_to_db_version` würde bei Lücke fehlschlagen — bleibt grün, also Kette 1→35 lückenlos.

### (b) RelayClient ohne in-memory @Volatile
- Feld `@Volatile private var lastCursorId: String?` ist ersatzlos gestrichen (inklusive TODO-Kommentar).
- `sendHello` liest jetzt `existing?.lastSeenId` aus der DB-Row und schickt es als `sinceId` mit. Beim allerersten Run (Row noch nicht da) wird die Initial-Row mit `lastSeenId = null` eingesetzt → Server interpretiert das wie zuvor (Phase-3-Backward-Compat).
- In `handleFrame` werden `ts + id` zusammen in einem einzigen `cursors.insert(...)` geschrieben. Damit gibt's kein Window mehr, in dem ts schon committed ist aber id noch nicht (vorher: erst `cursors.insert(...)`, dann `lastCursorId = …` in zwei Schritten). `frame.cursorId` ist nullable im Wire-Protokoll, das wird in die nullable Spalte 1:1 durchgereicht.

### (c) Schema v35.json
`grep` auf `35.json` zeigt:
```
"createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`peerId` TEXT NOT NULL, `lastSeenTs` INTEGER NOT NULL, `lastSeenId` TEXT, PRIMARY KEY(`peerId`))"
```
plus `fieldPath: lastSeenId, affinity: TEXT, notNull: false` im fields-Block. Damit matched die ALTER-Definition (TEXT, NULLable, kein DEFAULT) exakt das Room-Schema, sodass die Migration auf einer aus v34 hochgezogenen DB den Room-Schema-Check besteht.

## Concerns

- **Test-Coverage für Restart-Persistenz nicht hinzugefügt.** Die Phase-Tests sind statisch (Migration-Chain) bzw. fachlich (ReviewRepository); es gibt aktuell keinen Unit-Test, der explizit verifiziert, dass `RelayClient.sendHello` nach DB-Reset den vorher persistierten `lastSeenId` zurückspielt. Wenn ein vollständiger Restart-Integration-Test gewünscht ist, müsste ein Room-In-Memory-DB-Test um den `PeerCursorDao` herum gebaut werden — das wäre eine separate Aufgabe, der I7-Brief verlangt das nicht.
- **Server-Verträglichkeit.** Setzt voraus, dass der Relay auf `sinceId = null` weiterhin gleichbedeutend zu „nur ts-Filter" reagiert (Phase-3-Backward-Compat-Garantie). Bestätigt durch das `String? = null`-Default in `SyncFrame.Hello.sinceId`.
- **Andere WIP-Änderungen im Workdir.** Der Commit ist sauber auf die fünf I7-Files beschränkt; learnweb/calendar/search-WIP bleibt unstaged liegen und gehört nicht zu diesem Fix.
- **Single peerId.** Aktuell hardcoded auf `"relay"`. Falls in Zukunft mehrere Relays adressiert werden, wäre ein Index oder ein zweiter PK-Teil zu überlegen — out-of-scope für I7.

## Commit

`b8eafd9 fix(reviews): peer_cursor.cursorId über DB persistieren (I7-Fix)`
