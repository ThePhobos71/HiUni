# I7-Fix: peer_cursor.cursorId persistieren über App-Restart

Phase-3-Whole-Branch-Finding #2 + Final-Whole-Branch-Reviewer I7. Aktuell ist `RelayClient.lastCursorId` nur in-memory (`@Volatile`). Bei App-Restart geht es verloren, was an ts-Kollisions-Boundaries Events drop/duplicate kann.

Fix: `PeerCursorEntity` bekommt `lastSeenId: String?` Spalte, RelayClient liest+schreibt sie zusammen mit ts.

## Schritte

1. DB-Migration v34→v35 in `Migrations.kt`:
   ```sql
   ALTER TABLE peer_cursor ADD COLUMN lastSeenId TEXT
   ```
2. `PeerCursorEntity.kt` ergänzen um `val lastSeenId: String? = null`
3. `AppDatabase.kt`: `version = 35`
4. `PeerCursorDao.kt`: bestehende Methoden anpassen
5. `RelayClient.kt`: `cursors.upsert(peerId, ts, id)` statt `(peerId, ts)`. Initial-Read `cursors.find(peerId)` liefert beide Felder
6. Schema-JSON v35 wird beim nächsten Build auto-exported

## Tests
- MigrationListTest sollte automatisch über `ALL_MIGRATIONS`-Array funktionieren
- ReviewRepositoryTest sollte weiter grün bleiben (kein Test-Bruch erwartet)

## Files
- Modify: `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/PeerCursorEntity.kt`
- Modify: `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/PeerCursorDao.kt`
- Modify: `app/src/main/java/de/transio/hiuni/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/de/transio/hiuni/core/database/Migrations.kt`
- Modify: `app/src/main/java/de/transio/hiuni/feature/mensa/review/sync/RelayClient.kt`
- Auto-generated: `app/schemas/.../35.json`

## Commit
`fix(reviews): peer_cursor.cursorId über DB persistieren (I7-Fix)`

No `Co-Authored-By`.
