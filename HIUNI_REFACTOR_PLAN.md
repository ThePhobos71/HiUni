# HiUni — Architektur-Refactoring-Plan

> Konkreter, phasierter Plan zur Verbesserung der Architektur. Jeder Punkt mit Files, Schritten, Akzeptanzkriterien, Risiken und Aufwandsschätzung.

**Erstellt:** 2026-05-18
**Branch-Strategie:** Eine Feature-Branch pro Punkt, mergen via PR
**Zielzustand:** Produktionsreife App, die OEM-Updates und Scraping-Targets-Änderungen ohne Datenverlust übersteht

---

## Übersicht & Phasierung

| Phase | Fokus | Dauer | Punkte |
|---|---|---|---|
| **1** | Production-Risiken | ~5 Tage | 1, 2, 3 |
| **2** | Architektur-Cleanup | ~7 Tage | 4, 5, 6, 7 |
| **3** | Polish & Skalierung | ~5 Tage | 8, 9, 10, 11, 12, 13 |

**Gesamt:** ~17 Tage Single-Dev-Vollzeit. Realistisch parallel zu Feature-Arbeit: 4-6 Wochen.

---

# PHASE 1 — Production-Risiken (zuerst!)

## 1. Room-Migrations schreiben

**Problem:** `.fallbackToDestructiveMigration()` löscht User-Daten bei jedem Schema-Update.

### Files
- `app/src/main/java/de/transio/hiuni/data/AppDatabase.kt`
- Neu: `app/src/main/java/de/transio/hiuni/data/migrations/Migrations.kt`
- Neu: `app/schemas/de.transio.hiuni.data.AppDatabase/4.json` (auto-generiert)

### Schritte
1. **Schema-Export aktivieren** in `build.gradle.kts`:
   ```kotlin
   ksp {
       arg("room.schemaLocation", "$projectDir/schemas")
   }
   defaultConfig {
       javaCompileOptions {
           annotationProcessorOptions {
               arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
           }
       }
   }
   ```
2. **`exportSchema = true`** in `AppDatabase` setzen
3. **Build laufen lassen** → `schemas/4.json` wird erzeugt
4. **Backfill-Migration** für alle vergangenen Versionen schreiben:
   ```kotlin
   val MIGRATION_3_4 = object : Migration(3, 4) {
       override fun migrate(db: SupportSQLiteDatabase) {
           // endTime war nullable, wird jetzt NOT NULL
           db.execSQL("UPDATE calendar_events SET endTime = startTime + 3600000 WHERE endTime IS NULL")
           // Neue Tabelle mit NOT NULL Constraint
           db.execSQL("""
               CREATE TABLE calendar_events_new (
                   id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                   title TEXT NOT NULL,
                   description TEXT,
                   startTime INTEGER NOT NULL,
                   endTime INTEGER NOT NULL,
                   /* ... */
               )
           """)
           db.execSQL("INSERT INTO calendar_events_new SELECT * FROM calendar_events")
           db.execSQL("DROP TABLE calendar_events")
           db.execSQL("ALTER TABLE calendar_events_new RENAME TO calendar_events")
           db.execSQL("CREATE UNIQUE INDEX index_sourceOrigin_sourceIdentifier ON calendar_events(sourceOrigin, sourceIdentifier)")
       }
   }
   ```
5. **`fallbackToDestructiveMigration()` entfernen**, durch `.addMigrations(MIGRATION_3_4, ...)` ersetzen
6. **Tests:** `MigrationTestHelper` mit Test-DB v3 → migrate to v4 → verify rows preserved

### Akzeptanzkriterien
- [ ] `schemas/` Ordner committed
- [ ] Migration-Tests grün (`./gradlew test`)
- [ ] APK lokal von alter Version installieren → updaten → Custom Events da
- [ ] `fallbackToDestructiveMigration()` ist weg

### Risiken
- Echte v1/v2/v3-Schemas müssen rekonstruiert werden (Git-Archäologie)
- Falls produktive Nutzer auf v1 oder v2 hängen, brauchen wir Migration-Ketten

### Aufwand: **1 Tag**

---

## 2. Scraper-Tests + Health-Checks

**Problem:** `MovieScraper` und `BibScraper` brechen still bei DOM-Änderungen. Niemand merkt es.

### Files
- Neu: `app/src/test/java/de/transio/hiuni/networking/MovieScraperTest.kt`
- Neu: `app/src/test/java/de/transio/hiuni/networking/BibScraperTest.kt`
- Neu: `app/src/test/resources/fixtures/unifilm_2026_05.html`
- Neu: `app/src/test/resources/fixtures/bib_2026_05.html`
- Modifiziert: `MovieScraper.kt`, `BibScraper.kt` — Parsing aus HTTP-Layer extrahieren

### Schritte
1. **Parsing von HTTP entkoppeln:**
   ```kotlin
   class MovieScraper(private val client: OkHttpClient = OkHttpClient()) {
       suspend fun scrapeMovies(city: String): Result<List<Movie>> {
           val html = getPageContent(url) ?: return Result.failure(...)
           return parseHtml(html)  // ← jetzt testbar
       }
       
       internal fun parseHtml(html: String): Result<List<Movie>> { /* ... */ }
   }
   ```
2. **HTML-Fixtures committen:**
   ```bash
   curl https://www.unifilm.de/studentenkinos/Hildesheim > app/src/test/resources/fixtures/unifilm_2026_05.html
   curl https://ubwww.uni-hildesheim.de/gruppenraumbuchung/ > app/src/test/resources/fixtures/bib_2026_05.html
   ```
3. **Snapshot-Tests schreiben:**
   ```kotlin
   class MovieScraperTest {
       @Test
       fun `parses real fixture HTML correctly`() {
           val html = javaClass.getResource("/fixtures/unifilm_2026_05.html")!!.readText()
           val result = MovieScraper().parseHtml(html)
           assertTrue(result.isSuccess)
           val movies = result.getOrThrow()
           assertTrue("Expected >= 3 movies", movies.size >= 3)
           assertTrue("All have titles", movies.all { it.title.isNotBlank() })
           assertTrue("All have date+time", movies.all { it.date != null && it.time != null })
       }
       
       @Test
       fun `handles empty page gracefully`() {
           val result = MovieScraper().parseHtml("<html></html>")
           assertEquals(emptyList<Movie>(), result.getOrThrow())
       }
   }
   ```
4. **Health-Check-Logik** in den Scrapern:
   ```kotlin
   sealed class ScraperHealth {
       data class Healthy(val itemCount: Int) : ScraperHealth()
       data class Degraded(val itemCount: Int, val expectedMin: Int) : ScraperHealth()
       data class Broken(val reason: String) : ScraperHealth()
   }
   
   suspend fun scrapeWithHealthCheck(): Pair<Result<List<Movie>>, ScraperHealth> {
       val result = scrapeMovies()
       val health = when {
           result.isFailure -> ScraperHealth.Broken(result.exceptionOrNull()?.message ?: "Unknown")
           result.getOrNull()?.size ?: 0 < 2 -> ScraperHealth.Degraded(result.getOrNull()?.size ?: 0, 2)
           else -> ScraperHealth.Healthy(result.getOrThrow().size)
       }
       return result to health
   }
   ```
5. **BibScraper-Spezial:** Hex-Farben in Enum auslagern (testbar):
   ```kotlin
   enum class SlotStatus(val cssHex: String) {
       FREE("#92CD00"),
       OCCUPIED("#DF2E3B"),
       BOOKED("#999999");
       companion object {
           fun fromCss(style: String): SlotStatus? = entries.firstOrNull { style.contains(it.cssHex) }
       }
   }
   ```

### Akzeptanzkriterien
- [ ] `./gradlew test` läuft Scraper-Tests aus
- [ ] Mindestens 5 Tests pro Scraper (happy + edge cases + leerer Input)
- [ ] Health-Check kann von einer Admin-Funktion abgefragt werden
- [ ] Hex-Farben sind nicht mehr inline-string-vergleich

### Risiken
- Fixtures veralten — sollte alle 6 Monate aktualisiert werden (kann via CI Cron)
- Unifilm/Bib könnten Rate-Limits haben → langsame Fixture-Updates

### Aufwand: **1.5 Tage**

---

## 3. AlarmManager + WorkManager Hybrid

**Problem:** Aggressive OEM-ROMs (Xiaomi, Huawei) killen Background-Alarms.

### Files
- Modifiziert: `app/src/main/java/de/transio/hiuni/util/NotificationScheduler.kt`
- Neu: `app/src/main/java/de/transio/hiuni/util/EventReminderWorker.kt`
- Modifiziert: `app/build.gradle.kts` (WorkManager Dependency)

### Schritte
1. **WorkManager-Dependency:**
   ```kotlin
   implementation("androidx.work:work-runtime-ktx:2.9.0")
   ```
2. **Worker schreiben:**
   ```kotlin
   class EventReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
       override suspend fun doWork(): Result {
           val eventId = inputData.getLong("event_id", -1)
           val title = inputData.getString("event_title") ?: return Result.failure()
           NotificationHelper.showEventNotification(applicationContext, eventId, title)
           return Result.success()
       }
   }
   ```
3. **Hybrid-Logik in Scheduler:**
   ```kotlin
   fun scheduleNotification(context: Context, event: UnifiedCalendarEvent, minutesBefore: Int) {
       val triggerTimeMs = event.startTime.toEpochMilli() - minutesBefore * 60_000L
       val delayMs = triggerTimeMs - System.currentTimeMillis()
       
       if (delayMs <= 15 * 60_000L) {
           // <15min: AlarmManager (genau, aber Risiko OEM-Killer)
           scheduleViaAlarmManager(context, event, triggerTimeMs)
       } else {
           // >15min: WorkManager (robust, ±5min Toleranz)
           val workRequest = OneTimeWorkRequestBuilder<EventReminderWorker>()
               .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
               .setInputData(workDataOf("event_id" to event.id, "event_title" to event.title))
               .addTag("event_${event.id}")
               .build()
           WorkManager.getInstance(context).enqueue(workRequest)
       }
   }
   
   fun cancelNotification(context: Context, eventId: Long) {
       // Beide canceln, eine wird no-op sein
       cancelAlarmManager(context, eventId)
       WorkManager.getInstance(context).cancelAllWorkByTag("event_$eventId")
   }
   ```
4. **Settings-Hint** für User: Card "App von Akkuoptimierung ausnehmen" mit Deeplink zu Settings (nur auf Xiaomi/Huawei/Oppo zeigen via `Build.MANUFACTURER`)

### Akzeptanzkriterien
- [ ] Notifications >15min vor Event triggern auch nach 24h+ Background
- [ ] Test auf Xiaomi/Samsung-Gerät: App in Akku-Optimierung lassen, Notification kommt
- [ ] Settings-Hint sichtbar auf Xiaomi-Gerät
- [ ] Cancel räumt beide Mechanismen ab

### Risiken
- WorkManager hat min. 5min Toleranz — bei "15 min before" könnten User-Erwartungen leiden
- Doppelte Notifications wenn beide feuern → muss durch deterministischen Cancel verhindert werden

### Aufwand: **1.5 Tage**

---

# PHASE 2 — Architektur-Cleanup

## 4. `CalendarRepository` aufsplitten

**Problem:** Ein Repo macht 3 Quellen — verletzt Single Responsibility, schwer testbar.

### Files
- Modifiziert: `app/src/main/java/de/transio/hiuni/data/CalendarRepository.kt` (wird zu Aggregator)
- Neu: `app/src/main/java/de/transio/hiuni/data/MensaRepository.kt`
- Neu: `app/src/main/java/de/transio/hiuni/data/MovieRepository.kt`
- Neu: `app/src/main/java/de/transio/hiuni/data/CustomEventRepository.kt`
- Modifiziert: `HiUniApplication.kt` (DI)
- Modifiziert: alle ViewModels, die `CalendarRepository` nutzen

### Schritte
1. **Neue Repos extrahieren:**
   ```kotlin
   class MensaRepository(
       private val api: MensaApiService,
       private val eventDao: CalendarEventDao,
       private val context: Context
   ) {
       suspend fun refresh(): Result<Unit> { /* aus CalendarRepository.refreshMensaData */ }
       fun getMealsForDay(date: LocalDate): Flow<List<UnifiedCalendarEvent>> { /* ... */ }
   }
   
   class MovieRepository(
       private val scraper: MovieScraper,
       private val eventDao: CalendarEventDao
   ) { /* analog */ }
   
   class CustomEventRepository(private val eventDao: CalendarEventDao) {
       suspend fun addEvent(event: UnifiedCalendarEvent) = eventDao.upsertAll(listOf(event))
       suspend fun deleteEvent(id: Long) = eventDao.deleteById(id)
       fun getUserEvents(): Flow<List<UnifiedCalendarEvent>> = eventDao.getByEventTypes(listOf("CUSTOM_USER"))
   }
   ```
2. **`CalendarRepository` wird Facade:**
   ```kotlin
   class CalendarRepository(
       private val mensaRepo: MensaRepository,
       private val movieRepo: MovieRepository,
       private val customRepo: CustomEventRepository,
       private val eventDao: CalendarEventDao
   ) {
       fun getAllEventsForPeriod(from: Instant, to: Instant): Flow<List<UnifiedCalendarEvent>>
           = eventDao.getEventsForPeriod(from, to)
       
       suspend fun refreshAllSources() = coroutineScope {
           awaitAll(
               async { mensaRepo.refresh() },
               async { movieRepo.refresh() }
           )
       }
   }
   ```
3. **ViewModels migrieren:** Jeder ViewModel nimmt nur die Repos, die er wirklich braucht.

### Akzeptanzkriterien
- [ ] `MensaViewModel` hat keine Movie-Dependencies mehr
- [ ] `MovieViewModel` hat keine Mensa-Dependencies mehr
- [ ] Bestehende Funktionalität unverändert (smoke test alle Screens)
- [ ] Unit-Tests pro Repo möglich (vorher: nur via Calendar-God-Repo)

### Risiken
- Parallel-Refreshes via `coroutineScope` brauchen ggf. Throttling (sonst 3 gleichzeitige Netzwerk-Hits)
- Existing call-sites müssen alle gefunden werden (`grep -r calendarRepository` durch ganzes Projekt)

### Aufwand: **2 Tage**

---

## 5. Hilt-Migration

**Problem:** `HiUniApplication` wird God-Class, jeder Screen hat eigene Factory.

### Files
- Modifiziert: `app/build.gradle.kts` (Hilt-Dependencies)
- Modifiziert: `HiUniApplication.kt` (`@HiltAndroidApp`)
- Modifiziert: `MainActivity.kt` (`@AndroidEntryPoint`)
- Neu: `di/DatabaseModule.kt`, `di/NetworkModule.kt`, `di/RepositoryModule.kt`
- Gelöscht: Alle `*ViewModelFactory.kt`-Files
- Modifiziert: Alle ViewModels (`@HiltViewModel` + `@Inject`)

### Schritte
1. **Dependencies:**
   ```kotlin
   plugins {
       id("com.google.dagger.hilt.android")
       id("com.google.devtools.ksp")
   }
   dependencies {
       implementation("com.google.dagger:hilt-android:2.51")
       ksp("com.google.dagger:hilt-compiler:2.51")
       implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
   }
   ```
2. **Application + MainActivity annotieren**
3. **Module schreiben:**
   ```kotlin
   @Module
   @InstallIn(SingletonComponent::class)
   object DatabaseModule {
       @Provides @Singleton
       fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
           AppDatabase.getInstance(ctx)
       
       @Provides
       fun provideCalendarEventDao(db: AppDatabase): CalendarEventDao = db.calendarEventDao()
   }
   
   @Module
   @InstallIn(SingletonComponent::class)
   object NetworkModule {
       @Provides @Singleton
       fun provideOkHttpClient(@ApplicationContext ctx: Context): OkHttpClient =
           OkHttpClient.Builder()
               .cache(Cache(File(ctx.cacheDir, "http"), 10 * 1024 * 1024))
               .build()
       
       @Provides @Singleton
       fun provideMensaApiService(client: OkHttpClient) = MensaApiService(client)
   }
   ```
4. **ViewModels umstellen:**
   ```kotlin
   @HiltViewModel
   class HomeViewModel @Inject constructor(
       private val calendarRepo: CalendarRepository,
       private val emailRepo: EmailRepository
   ) : ViewModel() { /* ... */ }
   
   // In Compose:
   val viewModel: HomeViewModel = hiltViewModel()
   ```
5. **Factory-Files löschen** (kollektive Befreiung)

### Akzeptanzkriterien
- [ ] Keine `*ViewModelFactory.kt` mehr im Projekt
- [ ] `HiUniApplication` enthält nur noch `@HiltAndroidApp` + `easterEggColorScheme` (siehe Punkt 10)
- [ ] Build ist grün, alle Screens öffnen ohne Crash
- [ ] Smoke-Test: Email-Login + Mensa-Refresh + Calendar-Add-Event

### Risiken
- KSP-Konflikte (Room + Hilt beide nutzen KSP) — meistens unproblematisch
- Easter-Egg-Color-State-Sharing — siehe Punkt 10
- Test-Effort: alle Screens müssen einmal angetippt werden

### Aufwand: **2 Tage**

---

## 6. Domain-Layer + UI-Models

**Problem:** ViewModels exponieren `UnifiedCalendarEvent` (Room-Entity) direkt an UI.

### Files
- Neu: `app/src/main/java/de/transio/hiuni/domain/model/CalendarEvent.kt`
- Neu: `app/src/main/java/de/transio/hiuni/domain/model/MensaMeal.kt`
- Neu: `app/src/main/java/de/transio/hiuni/domain/mapper/EventMapper.kt`
- Modifiziert: alle Repositories (returnen Domain-Models)
- Modifiziert: alle ViewModels

### Schritte
1. **Domain-Models definieren:**
   ```kotlin
   // Pure Kotlin, keine Room/Compose-Imports
   data class CalendarEvent(
       val id: Long,
       val title: String,
       val description: String?,
       val timeRange: TimeRange,
       val location: String?,
       val source: EventSource,
       val origin: EventOrigin
   )
   
   data class TimeRange(val start: Instant, val end: Instant) {
       val duration: Duration get() = Duration.between(start, end)
   }
   
   sealed class EventSource {
       data class Mensa(val price: String?, val lane: String?, val timeOfDay: TimeOfDay) : EventSource()
       data object Movie : EventSource()
       data object Custom : EventSource()
   }
   
   enum class EventOrigin { API_SYNCED, USER_ADDED }
   ```
2. **Mapper:**
   ```kotlin
   object EventMapper {
       fun fromEntity(entity: UnifiedCalendarEvent): CalendarEvent { /* ... */ }
       fun toEntity(domain: CalendarEvent): UnifiedCalendarEvent { /* ... */ }
   }
   ```
3. **Repos mappen Entity → Domain:**
   ```kotlin
   fun getEvents(): Flow<List<CalendarEvent>> =
       eventDao.getEventsForPeriod(...).map { entities ->
           entities.map { EventMapper.fromEntity(it) }
       }
   ```
4. **`metadata: String?` JSON-Blob auspacken** (oder als `Map<String, String>` per Type-Converter)

### Akzeptanzkriterien
- [ ] Kein ViewModel importiert mehr `de.transio.hiuni.model.UnifiedCalendarEvent`
- [ ] Room-Entities sind `internal` zum `data`-Package
- [ ] `eventType: String` ist ersetzt durch type-safe Enum/Sealed Class

### Risiken
- Boilerplate-Heavy — viele kleine Mapper-Functions
- Versuchung, in 3 Layern dieselben Felder zu doppeln

### Aufwand: **2 Tage**

---

## 7. Typed Error States

**Problem:** `lastError: Throwable?` — UI weiß nicht, was zu zeigen ist.

### Files
- Neu: `app/src/main/java/de/transio/hiuni/domain/error/AppError.kt`
- Modifiziert: `EmailRepository.kt`, `BibRepository.kt`, `CalendarRepository.kt`
- Modifiziert: alle ViewModels, die Error-States haben

### Schritte
1. **Error-Hierarchie:**
   ```kotlin
   sealed class AppError {
       data object NetworkUnavailable : AppError()
       data class ServerError(val statusCode: Int) : AppError()
       data object Timeout : AppError()
       
       sealed class Auth : AppError() {
           data object InvalidCredentials : Auth()
           data object NoCredentialsStored : Auth()
       }
       
       sealed class Scraper : AppError() {
           data class Degraded(val itemCount: Int) : Scraper()
           data class ParseError(val details: String) : Scraper()
       }
       
       data class Unknown(val cause: Throwable) : AppError()
   }
   ```
2. **`Result<T, AppError>` durchziehen** (Kotlin hat kein eingebautes — entweder `Arrow.kt` oder eigenes Either)
3. **Repository-Conversion:**
   ```kotlin
   private fun Throwable.toAppError(): AppError = when (this) {
       is AuthenticationFailedException -> AppError.Auth.InvalidCredentials
       is SocketTimeoutException -> AppError.Timeout
       is UnknownHostException -> AppError.NetworkUnavailable
       is IOException -> AppError.NetworkUnavailable
       else -> AppError.Unknown(this)
   }
   ```
4. **UI-Mapping:**
   ```kotlin
   @Composable
   fun ErrorBanner(error: AppError) = when (error) {
       AppError.Auth.InvalidCredentials -> ErrorCard("Passwort falsch", action = "Login")
       AppError.NetworkUnavailable -> ErrorCard("Keine Verbindung", action = "Retry")
       /* ... */
   }
   ```

### Akzeptanzkriterien
- [ ] Email-Screen zeigt "Falsches Passwort" statt generischen Spinner-bleibt-stehen
- [ ] BibScraper-Degradation triggert UI-Warnung
- [ ] Crashlytics bekommt nur `Unknown`-Errors gemeldet, nicht jeden Network-Timeout

### Risiken
- Skopusumfang wächst — Versuchung, jeden Edge-Case zu typisieren
- Schema-Evolution: neue Error-Cases brauchen UI-Updates

### Aufwand: **1.5 Tage**

---

# PHASE 3 — Polish & Skalierung

## 8. Shared OkHttpClient + HTTP-Cache

**Problem:** Jeder Scraper baut eigenen Client. Kein Connection-Pooling, kein Cache.

### Files
- Modifiziert: `di/NetworkModule.kt` (Hilt) oder `HiUniApplication.kt` (Manual)
- Modifiziert: `MovieScraper.kt`, `BibScraper.kt`, `MensaApiService.kt`, `WebScraper.kt`

### Schritte
1. **Single Client mit Cache:**
   ```kotlin
   @Provides @Singleton
   fun provideOkHttpClient(@ApplicationContext ctx: Context): OkHttpClient =
       OkHttpClient.Builder()
           .cache(Cache(File(ctx.cacheDir, "http_cache"), 20 * 1024 * 1024))
           .connectTimeout(30, TimeUnit.SECONDS)
           .readTimeout(30, TimeUnit.SECONDS)
           .addInterceptor(HttpLoggingInterceptor().apply {
               level = if (BuildConfig.DEBUG) Level.HEADERS else Level.NONE
           })
           .build()
   ```
2. **Cache-Control-Interceptor** für Quellen ohne `Cache-Control`-Header:
   ```kotlin
   class StaleWhileRevalidateInterceptor(private val maxAgeMinutes: Int) : Interceptor {
       override fun intercept(chain: Interceptor.Chain): Response {
           val response = chain.proceed(chain.request())
           return response.newBuilder()
               .header("Cache-Control", "public, max-age=${maxAgeMinutes * 60}")
               .removeHeader("Pragma")
               .build()
       }
   }
   ```
3. **In Scrapern Konstruktor-Injection:**
   ```kotlin
   class MovieScraper @Inject constructor(private val client: OkHttpClient) { /* ... */ }
   ```

### Akzeptanzkriterien
- [ ] Mensa-API zweiter Refresh innerhalb 30min trifft Disk-Cache (im Charles/Proxy beobachtbar)
- [ ] App-Cache-Size in Settings sichtbar
- [ ] Nur ein `OkHttpClient` im Memory (Heap-Dump zeigt 1 statt N)

### Risiken
- Cache invalidiert nicht automatisch — User muss "Pull to Refresh" für aktuelle Daten
- Lösung: Force-Network bei explizitem Refresh:
  ```kotlin
  Request.Builder().cacheControl(CacheControl.FORCE_NETWORK).build()
  ```

### Aufwand: **1 Tag**

---

## 9. `SettingsManager` → DataStore

**Problem:** Static `object` mit Context-Parametern. Untestbar, blocking I/O.

### Files
- Modifiziert: `app/src/main/java/de/transio/hiuni/data/SettingsManager.kt`
- Modifiziert: alle Callers

### Schritte
1. **DataStore-Dependency:**
   ```kotlin
   implementation("androidx.datastore:datastore-preferences:1.0.0")
   ```
2. **Preferences-DataStore Setup:**
   ```kotlin
   private val Context.dataStore by preferencesDataStore("hiuni_settings")
   
   class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
       private object Keys {
           val MENSA_LOCATION_ID = intPreferencesKey("mensa_location_id")
           val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
           val NOTIFICATION_MINUTES_BEFORE = intPreferencesKey("notification_minutes")
           val BOTTOM_NAV_ROUTES = stringPreferencesKey("bottom_nav_routes")
           val EASTER_EGG_SCHEME = intPreferencesKey("easter_egg_scheme")
       }
       
       val mensaLocationId: Flow<Int> = ctx.dataStore.data.map { it[Keys.MENSA_LOCATION_ID] ?: 150 }
       
       suspend fun setMensaLocationId(id: Int) {
           ctx.dataStore.edit { it[Keys.MENSA_LOCATION_ID] = id }
       }
   }
   ```
3. **ViewModels collecten Flow direkt:**
   ```kotlin
   val mensaLocationId = settings.mensaLocationId.stateIn(viewModelScope, ...)
   ```

### Akzeptanzkriterien
- [ ] Keine blocking `SharedPreferences`-Calls mehr
- [ ] SettingsRepository ist mock-bar in Tests
- [ ] StateFlow basiert auf DataStore-Flow, kein eigener `MutableStateFlow` mehr für "navigationChanged"-Trigger

### Risiken
- Migration von alten SharedPreferences zu DataStore: einmaliger Migrate-Block
  ```kotlin
  preferencesDataStore(
      name = "hiuni_settings",
      produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, "old_prefs_name")) }
  )
  ```

### Aufwand: **1 Tag**

---

## 10. Easter-Egg-State aus `HiUniApplication` raus

**Problem:** UI-State im Application-Singleton. Verloren nach App-Restart. Application-Klasse soll keine UI-Logik kennen.

### Schritte
1. **In Punkt 9 hineinmergen:** `EASTER_EGG_SCHEME` als DataStore-Key
2. **`MainActivity` collected:**
   ```kotlin
   val easterEggScheme by settingsRepository.easterEggScheme.collectAsState(initial = 0)
   HiUniTheme(easterEggScheme = easterEggScheme) { /* ... */ }
   ```
3. **About-Screen Triple-Tap:**
   ```kotlin
   var tapCount by remember { mutableStateOf(0) }
   val scope = rememberCoroutineScope()
   Icon(
       modifier = Modifier.clickable {
           tapCount++
           if (tapCount >= 3) {
               scope.launch { settingsRepository.cycleEasterEggScheme() }
               tapCount = 0
           }
       }
   )
   ```

### Akzeptanzkriterien
- [ ] `HiUniApplication.kt` ist max. 20 Zeilen
- [ ] Theme bleibt nach App-Restart erhalten

### Aufwand: **0.5 Tage** (zusammen mit Punkt 9 erledigt)

---

## 11. Timber-Logging

**Problem:** `Log.d/e` überall, läuft auch in Release. String-Konkat in Hot-Paths.

### Files
- Modifiziert: `app/build.gradle.kts`
- Modifiziert: `HiUniApplication.kt` (Timber plant)
- **Bulk-Rename:** `Log.d(` → `Timber.d(`, etc. via Sed/Android Studio Find-Replace

### Schritte
1. **Dependency:**
   ```kotlin
   implementation("com.jakewharton.timber:timber:5.0.1")
   ```
2. **Plant in onCreate:**
   ```kotlin
   override fun onCreate() {
       super.onCreate()
       if (BuildConfig.DEBUG) {
           Timber.plant(Timber.DebugTree())
       } else {
           Timber.plant(CrashlyticsTree())  // Only WARN+ zu Crashlytics
       }
   }
   ```
3. **Crashlytics-Tree:**
   ```kotlin
   class CrashlyticsTree : Timber.Tree() {
       override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
           if (priority < Log.WARN) return
           FirebaseCrashlytics.getInstance().log("$tag: $message")
           t?.let { FirebaseCrashlytics.getInstance().recordException(it) }
       }
   }
   ```
4. **Migration:** Android Studio "Replace in Path":
   - `Log.d("([^"]+)",\s*` → `Timber.tag("$1").d(`
   - Plus: alle `import android.util.Log` raus

### Akzeptanzkriterien
- [ ] Release-APK loggt nichts unter WARN
- [ ] BibScraper-Iterations-Logs sind nur in Debug aktiv
- [ ] Crashlytics bekommt strukturierte Exceptions

### Aufwand: **0.5 Tage**

---

## 12. `WebScraper.login()` entfernen (oder benutzen)

**Problem:** Dead Code rottet.

### Schritte
**Option A — Löschen:**
1. `WebScraper.kt` löschen
2. Falls jemand drauf importiert → finden und entfernen (sollte niemand)

**Option B — Aktivieren (wenn Shibboleth-Scraper geplant):**
1. Shibboleth-Flow implementieren (CSRF, IdP-Redirect, SAML-Response)
2. Login-Status verifizieren via geschützte Test-URL, nicht via HTTP 200

**Empfehlung:** Option A. Wenn LSF-Scraping kommt, das dann sauber neu bauen.

### Aufwand: **0.25 Tage**

---

## 13. Background-Sync mit WorkManager

**Problem:** Daten nur bei App-Start frisch. User sieht alten Speiseplan.

### Files
- Neu: `app/src/main/java/de/transio/hiuni/background/DataSyncWorker.kt`
- Modifiziert: `HiUniApplication.kt` (Schedule beim ersten Start)

### Schritte
1. **Worker:**
   ```kotlin
   @HiltWorker
   class DataSyncWorker @AssistedInject constructor(
       @Assisted ctx: Context,
       @Assisted params: WorkerParameters,
       private val mensaRepo: MensaRepository,
       private val movieRepo: MovieRepository
   ) : CoroutineWorker(ctx, params) {
       override suspend fun doWork(): Result {
           val results = listOf(
               runCatching { mensaRepo.refresh() },
               runCatching { movieRepo.refresh() }
           )
           return if (results.all { it.isSuccess }) Result.success() else Result.retry()
       }
   }
   ```
2. **Schedule:**
   ```kotlin
   // In Application.onCreate
   val request = PeriodicWorkRequestBuilder<DataSyncWorker>(6, TimeUnit.HOURS)
       .setConstraints(Constraints.Builder()
           .setRequiredNetworkType(NetworkType.UNMETERED)  // nur WLAN
           .build())
       .build()
   WorkManager.getInstance(this).enqueueUniquePeriodicWork(
       "data_sync",
       ExistingPeriodicWorkPolicy.KEEP,
       request
   )
   ```

### Akzeptanzkriterien
- [ ] User sieht beim ersten Öffnen der App keine Loading-Spinner mehr (Daten schon da)
- [ ] WorkManager-DB zeigt periodische Tasks in `adb shell dumpsys jobscheduler`
- [ ] Mobile-Daten-Verbrauch unverändert (nur UNMETERED-Constraint)

### Aufwand: **1 Tag**

---

# Cross-Cutting Concerns (parallel zu allen Phasen)

## Test-Setup

Sollte VOR Phase 1 stehen, damit Phase 1 sicher refactored werden kann:

- [ ] `androidTest`-Setup mit `MigrationTestHelper`
- [ ] `test`-Setup mit MockK statt Mockito
- [ ] `gradle test` läuft in CI
- [ ] Code-Coverage-Reports (Jacoco) auf Repos & Mappers

## CI/CD wieder aktivieren

Die `.github/workflows/` sind aktuell leer. Sollte parallel laufen:

- [ ] `pr-check.yml`: lint + test + assembleDebug auf jeden PR
- [ ] `release.yml`: signed APK bei Tag-Push
- [ ] `scraper-health.yml`: Cron, läuft Scraper-Tests gegen Live-Sites

## Documentation

- [ ] `CONTRIBUTING.md` schreiben
- [ ] Architecture Decision Records (ADRs) in `docs/adr/` — pro Punkt aus diesem Plan eine ADR
- [ ] `CHANGELOG.md` ab nächstem Release pflegen

---

# Empfohlene Reihenfolge & Abhängigkeiten

```
Test-Setup (cross-cutting) ──┬─→ 1. Migrations ──→ 2. Scraper-Tests ──→ 3. WorkManager-Hybrid
                             │
                             └─→ 4. Repo-Split ──→ 5. Hilt ──→ 6. Domain ──→ 7. Errors
                                                              │
                                                              └─→ 8. HTTP-Cache
                                                              │
                                                              └─→ 9. DataStore ──→ 10. EasterEgg
                                                              │
                                                              └─→ 11. Timber
                                                              │
                                                              └─→ 12. Dead-Code
                                                              │
                                                              └─→ 13. Background-Sync
```

**Kritischer Pfad:** Hilt (Punkt 5) blockiert die meisten Phase-3-Punkte. Wenn Hilt zu groß ist, mit `androidx.lifecycle.viewmodel.viewModelFactory { initializer { } }` arbeiten — dann ist Phase 3 unabhängig.

---

# Was NICHT auf den Plan gehört

Bewusst NICHT in diesem Plan:
- **Kotlin Multiplatform** — schicker Hype, aber kein Business-Value bevor iOS-Variante geplant
- **Compose Multiplatform** — gleicher Grund
- **Modularisierung in Feature-Module** — kann später, wenn Build-Zeit zum Problem wird (>2min)
- **GraphQL/REST-API-Wrapper** — die Mensa-API ist zu klein, der Aufwand lohnt nicht
- **Retrofit statt OkHttp** — funktional gleich, aber API-Surface wechseln ohne Grund

---

# KPIs nach Refactoring

Vorher → Nachher (Schätzungen):

| Metrik | Vorher | Nachher |
|---|---|---|
| Crash-free Sessions | ? (kein Tracking) | >99% (Crashlytics aktiv) |
| `HiUniApplication.kt` LOC | 55 | <20 |
| Anzahl `*Factory.kt` Files | 12 | 0 |
| Test-Coverage Repos | 0% | >70% |
| Cold-Start zur Daten-Anzeige | ~3s (Network) | <500ms (Cache) |
| User-Data bei App-Update | manchmal weg | nie weg |
| Scraper-Breaks unbemerkt | passieren | < 24h dank Health-Check |

---

# Erste Schritte (Sofort umsetzbar)

Wenn du jetzt anfangen willst:

1. **Branch:** `git checkout -b chore/test-infrastructure`
2. **Test-Setup committen** (~30 Min):
   - `androidTest`-MigrationTestHelper-Vorlage
   - JUnit-Konfig in `build.gradle.kts`
3. **PR mergen, dann Branch `feat/room-migrations`** für Punkt 1

Soll ich aus Phase 1 (Punkte 1-3) einen konkreten Pull-Request-Plan ausarbeiten mit Branch-Namen, Commit-Messages und PR-Beschreibungen? Oder direkt mit der Implementierung von Punkt 1 anfangen?
