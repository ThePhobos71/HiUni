# Lieblingsgericht-Alarm Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die App meldet einmal, sobald ein gemerktes Stichwort (zum Beispiel "Currywurst") im Speiseplan der gewählten Mensa auftaucht.

**Architecture:** Reine Entscheidungslogik (`FavoriteMealNotifier`) nach dem Muster von `BibFreeSlotNotifier`, ein DataStore-Store für Stichwörter und bereits Gemeldetes, ein Watcher, der nach jedem erfolgreichen Mensa-Refresh prüft und über den vorhandenen `NotificationPresenter` meldet. UI: Stern im Gericht-Detail und eine Liste in den Mensa-Einstellungen.

**Tech Stack:** Kotlin, Hilt, Preferences-DataStore, Room (nur lesend, `MealDao`), Compose, JUnit-JVM-Tests.

**Spec:** `docs/superpowers/specs/2026-11-14-lieblingsgericht-alarm-design.md`

Alle Pfade relativ zu `app/src/main/java/de/transio/hiuni/` (Tests: `app/src/test/java/de/transio/hiuni/`).

## Global Constraints

- Keine Emojis in Mitteilungs- und UI-Texten. Sterne (★/☆) sind erlaubt.
- Keine Gedankenstriche in Fließtexten, Kommentaren und Doku.
- UI handgestylt (Surface und Text als Pills) wie in `feature/mensa/ui/MealDetailSheet.kt`, keine Standard-M3-Chips. Vor UI-Arbeit die passende JSX-Funktion im Design-Mock lesen.
- Es zählt nur die in den Einstellungen gewählte Mensa.
- Kein eigener Hintergrund-Abruf: geprüft wird nur im Anschluss an einen Mensa-Refresh.
- Dieselbe Mitteilung darf nicht bei jedem Aktualisieren erneut kommen.
- Kalender-Feature nicht anfassen. Kein `git config`. Commits direkt auf main, deutsch, ohne Co-Authored-By-Zeile, nur eigene Dateien per Pfad stagen (parallel arbeitet ein zweiter Agent am Raumfinder im selben Arbeitsverzeichnis).
- Prüfen mit `./gradlew :app:testDebugUnitTest`, keine APK bauen oder kopieren.

## Review Focus

1. Sehr kurze Stichwörter ("ei") würden fast jedes Gericht treffen: Stichwörter unter 3 Zeichen werden abgelehnt (Test in Task 2).
2. Groß- und Kleinschreibung sowie Umlaute: "currywurst" trifft "Currywurst", "kaese" trifft "Käse" nicht (Test in Task 1).
3. Mensa-Wechsel: ein für Mensa A gemeldeter Treffer darf denselben Treffer in Mensa B nicht unterdrücken (Test in Task 1, Schlüssel enthält `locationId`).
4. Gericht verschwindet aus dem Plan und kommt am selben Tag wieder: keine zweite Mitteilung (Test in Task 1).
5. Unlesbarer Store-Inhalt (kaputtes JSON): gilt als leere Liste, kein Absturz (Test in Task 2).

---

### Task 1: Entscheidungslogik `FavoriteMealNotifier`

**Files:**
- Create: `feature/mensa/data/FavoriteMealNotifier.kt`
- Test: `feature/mensa/data/FavoriteMealNotifierTest.kt`

**Interfaces:**
- Consumes: `MealEntity` (`name`, `date`, `locationId`).
- Produces:
  ```kotlin
  object FavoriteMealNotifier {
      const val MIN_KEYWORD_LENGTH = 3
      data class Hit(val keyword: String, val meal: MealEntity)
      data class Push(val title: String, val body: String, val refKey: String)
      data class Decision(val push: Push?, val newHits: List<Hit>, val notified: Set<String>)
      fun matches(keyword: String, meal: MealEntity): Boolean
      fun hitKey(locationId: Int, keyword: String, date: LocalDate): String   // "152|currywurst|2026-11-19"
      fun suggestKeyword(mealName: String): String
      fun nextHits(keywords: List<String>, meals: List<MealEntity>): Map<String, MealEntity>
      fun decide(
          keywords: List<String>, meals: List<MealEntity>, notified: Set<String>,
          today: LocalDate, notify: Boolean = true
      ): Decision
  }
  ```

Regeln, die Signatur und Tests nicht allein festlegen:
- `matches`: `meal.name.contains(keyword.trim(), ignoreCase = true)`, nur der Name.
- Höchstens ein Treffer je Stichwort und Tag (das erste Gericht nach Namen sortiert).
- `Decision.notified` = alte Schlüssel, deren Datum `>= today` ist und deren Stichwort noch existiert, plus die Schlüssel aller aktuellen Treffer. Mit `notify = false` ist `push` immer `null`, `notified` wird trotzdem fortgeschrieben.
- Tagesbezeichnung: `heute`, `morgen`, sonst Wochentag ausgeschrieben; ab 7 Tagen Abstand mit Datum (`Donnerstag, 26.11.`).
- Ein neuer Treffer: Titel `"<Stichwort> <Tag-Phrase>"` mit Tag-Phrase `heute`, `morgen` oder `am Donnerstag`; Text ist der volle Gerichtsname. Mehrere: Titel `"<n> Lieblingsgerichte im Speiseplan"`, Text `"Currywurst am Donnerstag, Lasagne morgen"` (nach Datum sortiert).
- `refKey` = `"mensa_fav_" + ` sortierte neue Schlüssel, per `hashCode()` gekürzt.
- `suggestKeyword`: Text vor dem ersten ` mit `, ` auf `, ` an `, `,` oder `(`, getrimmt.
- `nextHits`: je Stichwort das früheste treffende Gericht (für die Listenanzeige).

- [ ] **Step 1: Failing Tests schreiben**

```kotlin
private fun meal(name: String, date: LocalDate, loc: Int = 152) = MealEntity(
    sourceId = "$name-$date", locationId = loc, date = date, category = "Mittag",
    name = name, description = null, priceStudentCents = null,
    priceEmployeeCents = null, priceGuestCents = null, tags = ""
)
private val today = LocalDate.of(2026, 11, 16) // Montag
private val thu = LocalDate.of(2026, 11, 19)

@Test fun `neuer Treffer ergibt eine Mitteilung mit Stichwort und Tag`() {
    val d = FavoriteMealNotifier.decide(listOf("Currywurst"), listOf(meal("Currywurst mit Pommes frites", thu)), emptySet(), today)
    assertEquals("Currywurst am Donnerstag", d.push!!.title)
    assertEquals("Currywurst mit Pommes frites", d.push!!.body)
    assertEquals(setOf("152|currywurst|2026-11-19"), d.notified)
}
@Test fun `bereits gemeldeter Treffer meldet nicht erneut`() {
    val d = FavoriteMealNotifier.decide(listOf("Currywurst"), listOf(meal("Currywurst", thu)), setOf("152|currywurst|2026-11-19"), today)
    assertNull(d.push)
}
@Test fun `Gericht verschwindet und kommt am selben Tag wieder ohne zweite Mitteilung`() {
    val gone = FavoriteMealNotifier.decide(listOf("Currywurst"), emptyList(), setOf("152|currywurst|2026-11-19"), today)
    assertTrue("152|currywurst|2026-11-19" in gone.notified)
    assertNull(FavoriteMealNotifier.decide(listOf("Currywurst"), listOf(meal("Currywurst", thu)), gone.notified, today).push)
}
@Test fun `andere Mensa gilt als neuer Treffer`() {
    val d = FavoriteMealNotifier.decide(listOf("Currywurst"), listOf(meal("Currywurst", thu, loc = 160)), setOf("152|currywurst|2026-11-19"), today)
    assertNotNull(d.push)
}
@Test fun `Gross und Kleinschreibung egal, Umlaut wird nicht aufgeloest`() {
    assertTrue(FavoriteMealNotifier.matches("currywurst", meal("CURRYWURST vom Schwein", thu)))
    assertFalse(FavoriteMealNotifier.matches("kaese", meal("Käsespätzle", thu)))
}
@Test fun `mehrere neue Treffer werden gebuendelt`() {
    val d = FavoriteMealNotifier.decide(
        listOf("Currywurst", "Lasagne"),
        listOf(meal("Currywurst", thu), meal("Lasagne Bolognese", today.plusDays(1))), emptySet(), today)
    assertEquals("2 Lieblingsgerichte im Speiseplan", d.push!!.title)
    assertEquals("Lasagne morgen, Currywurst am Donnerstag", d.push!!.body)
}
@Test fun `zwei Gerichte am selben Tag zum selben Stichwort zaehlen einmal`() {
    val d = FavoriteMealNotifier.decide(listOf("Currywurst"), listOf(meal("Currywurst", thu), meal("Vegane Currywurst", thu)), emptySet(), today)
    assertEquals(1, d.newHits.size)
}
@Test fun `vergangene Schluessel und geloeschte Stichwoerter werden aufgeraeumt`() {
    val d = FavoriteMealNotifier.decide(listOf("Lasagne"), emptyList(), setOf("152|lasagne|2026-11-10", "152|currywurst|2026-11-19"), today)
    assertTrue(d.notified.isEmpty())
}
@Test fun `notify false schreibt nur fort`() {
    val d = FavoriteMealNotifier.decide(listOf("Currywurst"), listOf(meal("Currywurst", thu)), emptySet(), today, notify = false)
    assertNull(d.push); assertEquals(1, d.notified.size)
}
@Test fun `Treffer in 8 Tagen nennt das Datum`() {
    val d = FavoriteMealNotifier.decide(listOf("Currywurst"), listOf(meal("Currywurst", LocalDate.of(2026, 11, 24))), emptySet(), today)
    assertEquals("Currywurst am Dienstag, 24.11.", d.push!!.title)
}
@Test fun `Vorschlag kuerzt auf das Hauptgericht`() {
    assertEquals("Currywurst", FavoriteMealNotifier.suggestKeyword("Currywurst mit Pommes frites"))
    assertEquals("Lasagne Bolognese", FavoriteMealNotifier.suggestKeyword("Lasagne Bolognese (Rind)"))
}
```

- [ ] **Step 2: Test laufen lassen, muss fehlschlagen**

Run: `./gradlew :app:testDebugUnitTest --tests "*FavoriteMealNotifierTest"`
Expected: FAIL (Klasse fehlt).

- [ ] **Step 3: `FavoriteMealNotifier` nach Interface und Regeln oben implementieren**

- [ ] **Step 4: Test läuft durch**

Run: wie Step 2. Expected: PASS.

- [ ] **Step 5: Commit** `feat(mensa): Entscheidungslogik für Lieblingsgericht-Alarm`

---

### Task 2: `FavoriteMealStore`

**Files:**
- Create: `feature/mensa/data/FavoriteMealStore.kt`
- Test: `feature/mensa/data/FavoriteMealStoreTest.kt` (Aufbau wie `feature/bib/data/BibFavoritesStoreTest.kt`)

**Interfaces:**
- Consumes: `DataStore<Preferences>` (wie `MensaExtrasStore`), `FavoriteMealNotifier.MIN_KEYWORD_LENGTH`.
- Produces:
  ```kotlin
  @Singleton class FavoriteMealStore @Inject constructor(dataStore: DataStore<Preferences>) {
      fun observeKeywords(): Flow<List<String>>          // Reihenfolge des Hinzufügens
      suspend fun currentKeywords(): List<String>
      suspend fun addKeyword(raw: String): Boolean       // false = abgelehnt
      suspend fun removeKeyword(keyword: String)
      suspend fun notifiedKeys(): Set<String>
      suspend fun setNotifiedKeys(keys: Set<String>)
      companion object { const val MAX_KEYWORD_LENGTH = 40; const val MAX_KEYWORDS = 20 }
  }
  ```
- Schlüssel: `mensa_favorite_keywords` (JSON-Liste als String), `mensa_favorite_notified` (String-Set).
- `addKeyword`: trimmen, Mehrfach-Leerzeichen zu einem; ablehnen bei weniger als 3 Zeichen, mehr als 40 Zeichen, Duplikat ohne Beachtung der Großschreibung oder schon 20 Einträgen.

- [ ] **Step 1: Failing Tests**

```kotlin
@Test fun `hinzufuegen trimmt und behaelt Reihenfolge`() = runTest {
    assertTrue(store.addKeyword("  Currywurst ")); assertTrue(store.addKeyword("Lasagne"))
    assertEquals(listOf("Currywurst", "Lasagne"), store.currentKeywords())
}
@Test fun `zu kurz, Duplikat und zu lang werden abgelehnt`() = runTest {
    assertFalse(store.addKeyword("ei"))
    store.addKeyword("Currywurst"); assertFalse(store.addKeyword("currywurst"))
    assertFalse(store.addKeyword("x".repeat(41)))
}
@Test fun `entfernen loescht ohne Beachtung der Grossschreibung`() = runTest {
    store.addKeyword("Currywurst"); store.removeKeyword("CURRYWURST")
    assertTrue(store.currentKeywords().isEmpty())
}
@Test fun `kaputtes JSON gilt als leere Liste`() = runTest {
    dataStore.edit { it[stringPreferencesKey("mensa_favorite_keywords")] = "{kaputt" }
    assertTrue(store.currentKeywords().isEmpty())
}
@Test fun `gemeldete Schluessel werden ersetzt`() = runTest {
    store.setNotifiedKeys(setOf("a")); store.setNotifiedKeys(setOf("b"))
    assertEquals(setOf("b"), store.notifiedKeys())
}
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*FavoriteMealStoreTest"`, Expected: FAIL.
- [ ] **Step 3: Store implementieren** (JSON per kotlinx.serialization wie `MensaExtrasStore.decode`).
- [ ] **Step 4: Run**, Expected: PASS.
- [ ] **Step 5: Commit** `feat(mensa): Store für Lieblingsgericht-Stichwörter`

---

### Task 3: Eigene Mitteilungs-Kategorie "Mensa" mit Sprung in den Mensa-Tab

**Files:**
- Modify: `core/notifications/data/NotificationCategory.kt` (neuer Eintrag `MENSA("hiuni_mensa")` vor `SYSTEM`; `NotificationKind.MENSA -> MENSA`; Kommentar am Sammelkanal anpassen)
- Modify: `feature/notifications/ui/NotificationCategoriesCard.kt` (Label `"Mensa"`, Beschreibung `"Lieblingsgericht steht im Speiseplan"`)
- Modify: `HiUniApplication.kt` und alle weiteren `when (category)`-Stellen, die der Compiler als nicht erschöpfend meldet (Channel-Name `"Mensa"`)
- Modify: `core/notifications/NotificationPresenter.kt` (`NAV_TARGET_MENSA = "mensa"`, `NotificationKind.MENSA -> NAV_TARGET_MENSA`)
- Modify: `core/notifications/NotificationDeepLinkController.kt` (`val openMensa: Flow<Unit>`, `fun signalOpenMensa()`)
- Modify: `MainActivity.kt:149-157` (`NAV_TARGET_MENSA -> notificationDeepLink.signalOpenMensa()`)
- Modify: `navigation/AppNavGraph.kt:134` (`openMensa = merge(widgetDeepLink.openMensa, notificationDeepLink.openMensa)`)
- Test: `core/notifications/data/NotificationCategoryTest.kt`, `core/notifications/NotificationNavTargetTest.kt`, `core/notifications/NotificationDeepLinkControllerTest.kt`

**Interfaces:**
- Produces: `NotificationCategory.MENSA`, `NotificationPresenter.NAV_TARGET_MENSA`, `NotificationDeepLinkController.openMensa` / `signalOpenMensa()`.

Abweichung vom Spec, bewusst: Tippen öffnet den Mensa-Tab, nicht den Tag des Treffers (der Tag steht im Mitteilungstext). Die Deep-Link-Signale tragen heute keine Nutzdaten; das nur hierfür umzubauen lohnt nicht.

- [ ] **Step 1: Failing Tests ergänzen**

```kotlin
// NotificationCategoryTest
assertEquals(NotificationCategory.MENSA, NotificationCategory.of(NotificationKind.MENSA))
assertEquals("hiuni_mensa", NotificationCategory.channelIdFor(NotificationKind.MENSA))
assertEquals(NotificationCategory.SYSTEM, NotificationCategory.of(NotificationKind.MOVIE))
// NotificationNavTargetTest
assertEquals("mensa", NotificationPresenter.navTargetFor(NotificationKind.MENSA))
// NotificationDeepLinkControllerTest: signalOpenMensa() liefert genau ein Ereignis auf openMensa
// (Form wie der vorhandene Bib-Test)
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*NotificationCategoryTest" --tests "*NotificationNavTargetTest" --tests "*NotificationDeepLinkControllerTest"`, Expected: FAIL.
- [ ] **Step 3: Änderungen aus der Dateiliste umsetzen.** Vorhandene Tests, die alle Kategorien oder Channel-IDs aufzählen, mit anpassen.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`, Expected: PASS (ganze Suite, weil `when`-Stellen betroffen sind).
- [ ] **Step 5: Commit** `feat(notifications): eigene Kategorie Mensa mit Sprung in den Mensa-Tab`

---

### Task 4: `FavoriteMealWatcher` und Anbindung an den Refresh

**Files:**
- Create: `feature/mensa/data/FavoriteMealWatcher.kt`
- Modify: `feature/mensa/data/MensaRepository.kt` (`MensaRepositoryImpl` bekommt `FavoriteMealWatcher`; Aufruf am Ende von `refresh`, nach `setLastMensaRefreshEpoch`, nur wenn wirklich geladen wurde)
- Test: `feature/mensa/data/FavoriteMealWatcherTest.kt` (Aufbau wie `feature/bib/data/BibFavoriteWatcherTest.kt`), `feature/mensa/data/MensaRepositoryImplTest.kt` anpassen

**Interfaces:**
- Consumes: `FavoriteMealNotifier.decide`, `FavoriteMealStore`, `MealDao.observeRange(from, to, locationId)`, `SettingsDataStore.mensaLocationId`, `NotificationPresenter.present(kind, title, body, refKey, systemId)`, `java.time.Clock` (wie `BibFavoriteWatcher`).
- Produces:
  ```kotlin
  @Singleton class FavoriteMealWatcher @Inject constructor(...) {
      /** Prüft das 14-Tage-Fenster der gewählten Mensa. notify=false schreibt nur fort. */
      suspend fun check(notify: Boolean = true)
      companion object { const val NOTIFICATION_ID = -7301; const val WINDOW_DAYS = 13L }
  }
  ```
- Vor dem Festlegen von `-7301` per `grep -rn "systemId = -\|_NOTIFICATION_ID = -" app/src/main` sicherstellen, dass die ID frei ist; sonst die nächste freie negative ID nehmen.
- `check` läuft unter einem `Mutex`. Ohne Stichwörter: gemeldete Schlüssel leeren und zurück. Fehler im Watcher dürfen `refresh` nicht scheitern lassen (`runCatching` um den Aufruf im Repository, Fehler per Timber loggen).

- [ ] **Step 1: Failing Tests**

```kotlin
@Test fun `neuer Treffer wird einmal gemeldet`() = runTest {
    store.addKeyword("Currywurst"); dao.upsertAll(listOf(meal("Currywurst", thu)))
    watcher.check(); watcher.check()
    coVerify(exactly = 1) { presenter.present(NotificationKind.MENSA, "Currywurst am Donnerstag", any(), any(), FavoriteMealWatcher.NOTIFICATION_ID, any()) }
}
@Test fun `notify false meldet nicht und unterdrueckt die spaetere Meldung`() = runTest {
    store.addKeyword("Currywurst"); dao.upsertAll(listOf(meal("Currywurst", thu)))
    watcher.check(notify = false); watcher.check()
    coVerify(exactly = 0) { presenter.present(any(), any(), any(), any(), any(), any()) }
}
@Test fun `ohne Stichwoerter passiert nichts und Gemeldetes wird geleert`() = runTest {
    store.setNotifiedKeys(setOf("x")); watcher.check()
    assertTrue(store.notifiedKeys().isEmpty())
}
// MensaRepositoryImplTest
@Test fun `erfolgreicher Refresh stoesst den Watcher an, gedrosselter nicht`() = runTest { ... coVerify(exactly = 1) { watcher.check() } }
@Test fun `Fehler im Watcher laesst refresh erfolgreich`() = runTest { coEvery { watcher.check() } throws IllegalStateException(); assertTrue(repo.refresh(force = true) is AppResult.Success) }
```

(Mock-Bibliothek und DAO-Fake wie in den vorhandenen Watcher- und Repository-Tests; `AppResult`-Erfolgsfall so prüfen, wie `MensaRepositoryImplTest` es schon tut.)

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*FavoriteMealWatcherTest" --tests "*MensaRepositoryImplTest"`, Expected: FAIL.
- [ ] **Step 3: Watcher schreiben, Repository anbinden.**
- [ ] **Step 4: Run**, Expected: PASS.
- [ ] **Step 5: Commit** `feat(mensa): Lieblingsgericht-Alarm nach jedem Speiseplan-Refresh`

---

### Task 5: UI, Stern im Gericht-Detail und Liste in den Mensa-Einstellungen

**Files:**
- Modify: `feature/mensa/MensaUiState.kt` (`favoriteKeywords: List<String> = emptyList()`)
- Modify: `feature/mensa/MensaViewModel.kt` (Store und Watcher injizieren; `fun addFavoriteKeyword(raw: String)`, `fun removeFavoriteKeywordsFor(meal: MealEntity)`)
- Modify: `feature/mensa/ui/MealDetailSheet.kt` (Parameter `isFavorite: Boolean`, `suggestedKeyword: String`, `onAddFavorite: (String) -> Unit`, `onRemoveFavorite: () -> Unit`), Aufrufer `feature/mensa/ui/MensaScreen.kt:180`
- Modify: `feature/settings/ui/MensaSettingsScreen.kt` und das zugehörige ViewModel (Karte "Lieblingsgerichte")
- Test: `feature/mensa/MensaViewModelTest.kt`

**Interfaces:**
- Consumes: `FavoriteMealStore.observeKeywords/addKeyword/removeKeyword`, `FavoriteMealWatcher.check(notify = false)`, `FavoriteMealNotifier.matches/suggestKeyword/nextHits`.

Verhalten:
- Gericht-Detail: Stern-Zeile unter dem Titel. Leerer Stern (☆) plus Text "Als Lieblingsgericht merken": Tippen klappt ein Textfeld auf, vorbelegt mit `suggestKeyword(meal.name)`, daneben "Merken". Gefüllter Stern (★) plus "Lieblingsgericht: <Stichwort>", wenn ein Stichwort das Gericht trifft: Tippen entfernt alle treffenden Stichwörter.
- Nach jedem Hinzufügen ruft das ViewModel `watcher.check(notify = false)`: Treffer, die schon im Plan stehen, lösen keine Mitteilung aus (man sitzt ja gerade in der App), sie erscheinen in der Liste.
- Abgelehntes Stichwort (`addKeyword` liefert `false`): Hinweistext "Mindestens 3 Zeichen, noch nicht vorhanden" unter dem Feld.
- Mensa-Einstellungen, Karte "Lieblingsgerichte": Erklärzeile "Du bekommst eine Mitteilung, sobald eines davon im Speiseplan steht.", je Stichwort eine Pill mit Entfernen (✕ als Text) und darunter, falls vorhanden, "Nächster Treffer: Do 19.11." aus `nextHits`; Textfeld plus "Hinzufügen". Leerzustand: "Noch keine Lieblingsgerichte. Tippe im Speiseplan auf ein Gericht und dann auf den Stern."

- [ ] **Step 1: Failing ViewModel-Tests**

```kotlin
@Test fun `addFavoriteKeyword speichert und prueft still`() = runTest {
    vm.addFavoriteKeyword("Currywurst"); advanceUntilIdle()
    assertEquals(listOf("Currywurst"), vm.uiState.value.favoriteKeywords)
    coVerify { watcher.check(notify = false) }
}
@Test fun `removeFavoriteKeywordsFor entfernt alle treffenden Stichwoerter`() = runTest {
    vm.addFavoriteKeyword("Currywurst"); vm.addFavoriteKeyword("Wurst"); vm.addFavoriteKeyword("Lasagne")
    vm.removeFavoriteKeywordsFor(meal("Currywurst mit Pommes", thu)); advanceUntilIdle()
    assertEquals(listOf("Lasagne"), vm.uiState.value.favoriteKeywords)
}
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*MensaViewModelTest"`, Expected: FAIL.
- [ ] **Step 3: ViewModel, Detail-Sheet und Einstellungs-Karte umsetzen.**
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest` und `./gradlew :app:compileDebugKotlin`, Expected: beides erfolgreich.
- [ ] **Step 5: `docs/FEATURES.md` im Mensa-Abschnitt um den Lieblingsgericht-Alarm ergänzen.**
- [ ] **Step 6: Commit** `feat(mensa): Lieblingsgerichte merken und verwalten`
