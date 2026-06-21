# HiUni Entwickler-Guide

> Kochbuch für Phase 2+. Jedes Rezept ist ein Copy-Paste-Pattern mit File-Pfaden und Snippets.

## App-Architektur in 30 Sekunden

- **Feature-First Packages** in `:app` (keine Gradle-Module — siehe ADR-0001)
- Pro Feature: `feature/<name>/ui/<Name>Screen.kt`, `feature/<name>/<Name>ViewModel.kt`, `feature/<name>/data/*`
- **Shared Infra** in `core/*`: Theme, Database, Network, DataStore, Security, Notifications
- **Hilt** für alle Dependencies, **Room** als Single AppDatabase, **OkHttp** Singleton, **EncryptedSharedPreferences** für Credentials
- **Cross-Feature-Regel:** nur `feature.home` darf andere Feature-Repos injecten. `feature.email` darf `core.security.CredentialsManager` ziehen. Sonst gilt: kein Feature importiert ein anderes.

## Tool-Befehle

```bash
./gradlew assembleDebug    # APK bauen
./gradlew lintDebug        # Lint laufen lassen
./gradlew installDebug     # auf Device installieren
./gradlew test             # Unit-Tests
./gradlew connectedAndroidTest  # Instrumented-Tests (braucht Device/Emulator)

./gradlew :app:dependencies | head  # was zieht was rein
./gradlew --stop           # Daemon killen wenn Builds hängen
```

## Generische Schritte: Neues Feature anlegen

Egal ob Mensa, Movies, Bib, Email oder neu: das Schema ist immer gleich.

1. **Package erstellen** unter `app/src/main/java/de/transio/hiuni/feature/<name>/`
   - `ui/<Name>Screen.kt` — Compose Screen
   - `<Name>ViewModel.kt` — `@HiltViewModel`
   - `data/<Name>Repository.kt` — Interface + Impl + `@Module`
   - bei Persistenz: `data/<Name>Entity.kt`, `data/<Name>Dao.kt`
   - bei API/Scraper: `data/<Name>ApiService.kt` oder `data/<Name>Scraper.kt`
2. **Route registrieren** in `navigation/Destinations.kt`
3. **Composable hinzufügen** in `navigation/AppNavGraph.kt`
4. **Wenn Entity:** in `core/database/AppDatabase.kt` ans `entities`-Array hängen + DAO als abstract fun + DB-Version erhöhen + Migration schreiben
5. **Wenn DAO neu:** `@Provides` in `di/DatabaseModule.kt`
6. **Repository binden:** `@Module @Binds` neben dem Repository-Interface

---

## Recipe A — Feature mit Room-Persistenz (z.B. Mensa-Cache, Bib-Räume, Movies-Watchlist)

### 1. Entity + DAO

```kotlin
// feature/mensa/data/MealEntity.kt
@Entity(tableName = "meals", indices = [Index(value = ["date", "category"])])
data class MealEntity(
    @PrimaryKey val id: String,           // "STW-ON-meal-12345"
    val date: LocalDate,
    val category: String,                  // "Hauptgericht" | "Beilage" | ...
    val name: String,
    val description: String?,
    val priceCents: Int,
    val tags: String,                      // comma-separated z.B. "vegan,glutenfrei"
    val locationId: Int
)

// feature/mensa/data/MealDao.kt
@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE date = :date AND locationId = :locationId ORDER BY category")
    fun observeForDate(date: LocalDate, locationId: Int): Flow<List<MealEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(meals: List<MealEntity>)

    @Query("DELETE FROM meals WHERE locationId = :locationId AND date < :before")
    suspend fun pruneOlderThan(locationId: Int, before: LocalDate)
}
```

### 2. In AppDatabase eintragen

```kotlin
// core/database/AppDatabase.kt
@Database(
    entities = [
        CustomEventEntity::class,
        MealEntity::class,             // <-- neu
    ],
    version = 2,                       // <-- inkrement
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customEventDao(): CustomEventDao
    abstract fun mealDao(): MealDao    // <-- neu

    companion object { const val DATABASE_NAME = "hiuni.db" }
}
```

### 3. Migration schreiben (KEIN destructive Migration!)

```kotlin
// core/database/Migrations.kt
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS meals (
                id TEXT NOT NULL PRIMARY KEY,
                date INTEGER NOT NULL,
                category TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                priceCents INTEGER NOT NULL,
                tags TEXT NOT NULL,
                locationId INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_meals_date_category ON meals(date, category)")
    }
}
```

Dann in `DatabaseModule.kt` Builder-Call erweitern: `.addMigrations(MIGRATION_1_2)`.

### 4. DAO als `@Provides`

```kotlin
// di/DatabaseModule.kt
@Provides
fun provideMealDao(database: AppDatabase): MealDao = database.mealDao()
```

### 5. Repository

```kotlin
// feature/mensa/data/MensaRepository.kt
interface MensaRepository {
    fun observeForDate(date: LocalDate): Flow<List<MealEntity>>
    suspend fun refresh(): AppResult<Unit>
}

@Singleton
class MensaRepositoryImpl @Inject constructor(
    private val dao: MealDao,
    private val api: MensaApiService,
    private val settings: SettingsDataStore
) : MensaRepository {
    override fun observeForDate(date: LocalDate): Flow<List<MealEntity>> =
        settings.mensaLocationId.flatMapLatest { id -> dao.observeForDate(date, id) }

    override suspend fun refresh(): AppResult<Unit> = runCatchingApp {
        val locationId = settings.mensaLocationId.first()
        val meals = api.fetchUpcomingMeals(locationId)
        dao.upsertAll(meals.map { it.toEntity(locationId) })
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MensaRepositoryModule {
    @Binds @Singleton
    abstract fun bind(impl: MensaRepositoryImpl): MensaRepository
}
```

### 6. ViewModel

```kotlin
// feature/mensa/MensaViewModel.kt
@HiltViewModel
class MensaViewModel @Inject constructor(
    private val repo: MensaRepository
) : ViewModel() {
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val uiState: StateFlow<MensaUiState> = _selectedDate
        .flatMapLatest { date -> repo.observeForDate(date).map { MensaUiState(date, it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MensaUiState(LocalDate.now()))

    init { viewModelScope.launch { repo.refresh() } }
    fun selectDate(date: LocalDate) { _selectedDate.value = date }
}
```

### 7. Screen — Theme-Tokens nutzen

```kotlin
@Composable
fun MensaScreen(viewModel: MensaViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    Surface(color = semantics.amberSurface, shape = RoundedCornerShape(HiUniRadii.card)) {
        Text(state.meals.first().name, style = MaterialTheme.typography.titleLarge, color = semantics.amber)
    }
}
```

---

## Recipe B — Feature mit Web-Scraper (z.B. Movies, Bib)

OkHttp ist als `@Singleton` schon im `NetworkModule`. Jsoup ist im Stack.

```kotlin
// feature/movies/data/MovieScraper.kt
@Singleton
class MovieScraper @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun fetchMovies(city: String = "Hildesheim"): AppResult<List<Movie>> =
        withContext(Dispatchers.IO) {
            runCatchingApp {
                val request = Request.Builder()
                    .url("https://www.unifilm.de/studentenkinos/$city")
                    .build()
                val html = client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    resp.body?.string().orEmpty()
                }
                parseMovies(Jsoup.parse(html))
            }
        }

    private fun parseMovies(doc: Document): List<Movie> = doc.select("li.film").mapNotNull { /* ... */ }
}
```

**v1-Lessons im Kopf behalten** (siehe `HIUNI_KONZEPTE.md`):
- 30s Timeouts statt Default 10s (Bib brauchte das)
- ` ` (NBSP) → ` ` Cleanup vor String-Compare
- Hex-Farben als `enum class StatusColor(val hex: String)` statt String-Compare
- Fill-Forward für leere Slots wenn die Seite nur expliziten Status liefert

---

## Recipe C — Feature mit REST-API + kotlinx.serialization

JSON-Singleton ist in `NetworkModule.provideJson()`.

```kotlin
// feature/mensa/data/MensaDtos.kt
@Serializable
data class MensaResponseDto(val meals: List<MealDto>)

@Serializable
data class MealDto(
    val id: String,
    @SerialName("name_de") val name: String,
    val date: String,                 // "2026-05-24"
    val category: String,
    @SerialName("prices") val prices: PricesDto
)

// feature/mensa/data/MensaApiService.kt
@Singleton
class MensaApiService @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchUpcomingMeals(locationId: Int): List<MealDto> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://sls.api.stw-on.de/v1/locations/$locationId/menu")
            .build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
        json.decodeFromString<MensaResponseDto>(body).meals
    }
}
```

---

## Recipe D — Feature mit Credentials (Email)

`CredentialsManager` in `core/security/` ist bereits da, inkl. Self-Healing-Reset. Email-Feature darf das als einziges Cross-Core-Konsument injecten.

```kotlin
// feature/email/data/EmailRepository.kt
@Singleton
class EmailRepository @Inject constructor(
    private val credentials: CredentialsManager
) {
    private val refreshMutex = Mutex()        // v1-Pattern — verhindert parallele IMAP-Connects

    suspend fun refreshInbox(): AppResult<List<EmailMessage>> = refreshMutex.withLock {
        val user = credentials.getUsername() ?: return@withLock AppResult.Failure(IllegalStateException("Keine Credentials"))
        val pass = credentials.getPassword() ?: return@withLock AppResult.Failure(IllegalStateException("Keine Credentials"))
        // ... Jakarta-Mail-IMAP-Code (Properties → Session → store.connect(host, user, pass) → folder.getMessages())
        runCatchingApp { emptyList() }
    }
}
```

**Credentials speichern (in SettingsScreen z.B.):**

```kotlin
val ok = credentialsManager.saveCredentials(username = inputUser, password = inputPass)
// ok=false → User-feedback "Keychain nicht verfügbar"
```

---

## Recipe E — Feature mit Notifications (Calendar-Reminder)

`NotificationScheduler` + `NotificationReceiver` sind in `core/notifications/`. Channel ist in `HiUniApplication.onCreate()` registriert.

```kotlin
// Anywhere in a UseCase / Repo / ViewModel:
@Inject lateinit var scheduler: NotificationScheduler

fun scheduleReminder(event: CustomEventEntity) {
    val minutesBefore = event.reminderMinutesBefore ?: return
    val triggerAt = event.startTime.minus(Duration.ofMinutes(minutesBefore.toLong()))
    scheduler.schedule(event.id, event.title, triggerAt)
}

fun cancelReminder(event: CustomEventEntity) {
    scheduler.cancel(event.id)
}
```

**TODO Phase 2:** `NotificationReceiver` muss noch echte Notifications posten (aktuell nur Timber-Log). Pattern: `NotificationCompat.Builder(ctx, CHANNEL_ID_EVENTS)...build()` + `NotificationManagerCompat.notify(id, notification)`.

---

## Recipe F — Background-Sync mit WorkManager (Email-Polling, Mensa-Refresh)

WorkManager + Hilt-Work ist im Stack. Beispiel:

```kotlin
// feature/email/data/EmailSyncWorker.kt
@HiltWorker
class EmailSyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repository: EmailRepository
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = when (repository.refreshInbox()) {
        is AppResult.Success -> Result.success()
        is AppResult.Failure -> Result.retry()
    }

    companion object {
        const val WORK_NAME = "email_sync"
    }
}
```

**Enqueue (z.B. in HiUniApplication.onCreate oder via Settings-Toggle):**

```kotlin
val req = PeriodicWorkRequestBuilder<EmailSyncWorker>(15, TimeUnit.MINUTES)
    .setConstraints(Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build())
    .build()
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    EmailSyncWorker.WORK_NAME,
    ExistingPeriodicWorkPolicy.KEEP,
    req
)
```

**Hilt-Work-Setup** in `HiUniApplication`:

```kotlin
@HiltAndroidApp
class HiUniApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
```

---

## Recipe G — Cross-Feature-Read im Home (Aggregator)

Home darf alle Feature-Repos read-only injecten. Das ist die explizite Ausnahme von der Modul-Trennungs-Regel.

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    calendarRepo: CalendarRepository,
    mensaRepo: MensaRepository,
    emailRepo: EmailRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        calendarRepo.observeAll(),
        mensaRepo.observeForDate(LocalDate.now()),
        emailRepo.observeUnreadCount()
    ) { events, meals, unread ->
        HomeUiState(
            nextEvent = events.firstOrNull { it.startTime > Instant.now() },
            todaysMeals = meals,
            unreadEmails = unread
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
```

`WhileSubscribed(5000)` = 5s nach letztem Observer überleben → Rotation ohne DB-Reload.

---

## Recipe H — Settings-Toggle (DataStore)

`SettingsDataStore` ist in `core/datastore/`. Neuer Schlüssel = drei Zeilen.

```kotlin
// core/datastore/SettingsDataStore.kt — neuer Key z.B. für Sync-On-Wifi-Only
companion object {
    private val KEY_WIFI_ONLY_SYNC = booleanPreferencesKey("wifi_only_sync")
    const val DEFAULT_WIFI_ONLY_SYNC = false
}

val wifiOnlySync: Flow<Boolean> = dataStore.data.map { it[KEY_WIFI_ONLY_SYNC] ?: DEFAULT_WIFI_ONLY_SYNC }
suspend fun setWifiOnlySync(value: Boolean) { dataStore.edit { it[KEY_WIFI_ONLY_SYNC] = value } }
```

`SettingsViewModel` injectet `SettingsDataStore` direkt — kein extra Repository nötig.

---

## Recipe I — In-Calendar-Pin (Mensa/Movie als Custom-Event kopieren)

Snapshot-Pattern (siehe ADR-0006). Kein Pinned-Flag im Source-Feature.

```kotlin
// In MensaViewModel oder MovieViewModel:
@Inject lateinit var calendarRepo: CalendarRepository

fun pinToCalendar(meal: MealEntity) = viewModelScope.launch {
    calendarRepo.upsert(
        CustomEventEntity(
            title = meal.name,
            description = meal.description,
            location = "Mensa",
            startTime = meal.date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant(),
            endTime = meal.date.atTime(13, 0).atZone(ZoneId.systemDefault()).toInstant(),
            sourceKind = CustomEventEntity.SOURCE_MENSA_PIN,
            sourceReference = meal.id
        )
    )
}
```

**Achtung:** Mensa-Feature importiert `CalendarRepository` — das ist eine bewusste Cross-Feature-Schreib-Ausnahme. Lieber als Use-Case `PinMealToCalendarUseCase` extrahieren wenn das Pattern öfter kommt.

---

## Recipe J — Tests

Test-Stack: JUnit 4, MockK, Turbine, Robolectric, Coroutines-Test, Room-Testing.

```kotlin
// app/src/test/java/de/transio/hiuni/feature/calendar/CalendarRepositoryTest.kt
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarRepositoryImplTest {
    private val dao = mockk<CustomEventDao>(relaxed = true)
    private val repo = CalendarRepositoryImpl(dao)

    @Test
    fun `observeAll delegates to DAO`() = runTest {
        val events = listOf(/* fixture */)
        every { dao.observeAll() } returns flowOf(events)

        repo.observeAll().test {
            assertEquals(events, awaitItem())
            awaitComplete()
        }
    }
}
```

Room-Migration-Test mit `MigrationTestHelper` aus `androidx.room:room-testing`.

---

## Design-Tokens nutzen

| Was | Wie |
|---|---|
| Primärfarbe | `MaterialTheme.colorScheme.primary` |
| Primär-Container (Pillow-BG) | `MaterialTheme.colorScheme.primaryContainer` |
| Amber Akzent | `HiUniColors.semantics.amber` + `amberSurface` |
| Grün Status | `HiUniColors.semantics.green` + `greenSurface` |
| Rot Status / Error | `HiUniColors.semantics.red` + `redSurface` |
| Lila Status | `HiUniColors.semantics.purple` + `purpleSurface` |
| Surface alt (gestreiftes Hintergrund) | `HiUniColors.semantics.surfaceAlt` |
| Gedämpfter Text | `HiUniColors.semantics.onSurfaceMuted` |
| Card-Radius | `RoundedCornerShape(HiUniRadii.card)` (18.dp) |
| Tile-Radius (kleine Icons) | `RoundedCornerShape(HiUniRadii.tile)` (14.dp) |
| Big-Radius (Hero-Sections) | `RoundedCornerShape(HiUniRadii.big)` (24.dp) |
| ExtraBold-Headline | `MaterialTheme.typography.displayMedium` |
| Card-Title | `MaterialTheme.typography.titleMedium` |
| Section-Label | `MaterialTheme.typography.titleSmall` |
| Caption / Muted | `MaterialTheme.typography.labelMedium` mit `semantics.onSurfaceMuted` |

Schau `feature/home/ui/HomeScreen.kt` als lebende Referenz für alle Patterns (Header, QuickTile, Banner, Section-Card, LazyRow-Carousel).

---

## Hilt-Cheatsheet

| Brauche ich… | Annotation | Datei |
|---|---|---|
| ViewModel mit Repo | `@HiltViewModel` + `@Inject constructor(...)` | `feature/<x>/<X>ViewModel.kt` |
| Singleton-Service (Scraper, ApiService) | `@Singleton @Inject constructor(...)` | `feature/<x>/data/<X>Service.kt` |
| Interface auf Impl mappen | `@Module @Binds @Singleton` | `feature/<x>/data/<X>RepositoryModule.kt` |
| Drittlibrary konfigurieren (OkHttp, Json) | `@Module @Provides @Singleton` | `di/*Module.kt` |
| DAO bereitstellen | `@Provides` in `DatabaseModule` | `di/DatabaseModule.kt` |
| WorkManager-Worker | `@HiltWorker @AssistedInject` | beim Worker |

---

## Build-Errors-Survival-Guide

| Symptom | Ursache | Fix |
|---|---|---|
| `Unresolved reference: libs.xyz` | IDE noch nicht gesynct | Android Studio → Gradle Sync |
| `KSP failed: Cannot find symbol XxxModule_ProvidesYyy` | DAO/Repo-Provides fehlt | Check `di/DatabaseModule.kt` |
| `MissingBinding` zur Build-Zeit | Hilt findet kein `@Binds`/`@Provides` für Type X | Module mit `@InstallIn(SingletonComponent::class)` schreiben |
| `Could not create task ':app:kspDebugKotlin'` | KSP2 + Room < 2.7 inkompatibel | `ksp.useKSP2=false` in `gradle.properties` |
| `META-INF/NOTICE.md duplicate` | Library liefert eigene NOTICE | Exclude in `packaging.resources.excludes` in `app/build.gradle.kts` |
| `Theme.HiUni.Splash not found` | Theme XML rename vergessen | `res/values/themes.xml` und `values-night/themes.xml` checken |
| `Migration didn't properly handle` | Room schema-diff zur runtime | echte Migration schreiben, NIEMALS `fallbackToDestructiveMigration` |

---

## Wann lohnt sich ein neues ADR?

Schreibt ein neues `docs/adr/000X-name.md` wenn:
- Ihr eine neue Library aufnehmt, die nicht im `HIUNI_LIBRARIES.md`-Stack steht
- Ihr eine Architektur-Regel ändert (z.B. „doch ein neues `:feature:mensa`-Modul")
- Ihr ein Pattern aus v1 bewusst nicht übernehmt (mit Begründung)

Pro ADR: Kontext / Entscheidung / Begründung / Trade-offs. 1 Seite Markdown reicht.

---

## Pair-Defense-Vorbereitung

Beide müssen jeden Layer erklären können:

- **UI-Layer:** Wie funktioniert Compose Recomposition? Was macht `StateFlow.collectAsStateWithLifecycle`?
- **Domain/Repo-Layer:** Wie hängt der Flow von Room über Repo zum ViewModel? Was passiert bei `WhileSubscribed(5000)`?
- **Data-Layer:** Warum Single AppDatabase mit Feature-DAOs? Wie laufen die Migrations?
- **Architektur:** Warum Feature-First Packages statt Multi-Module? (ADR-0001)
- **AI-Workflow:** Was hat Claude generiert? Was habt ihr selbst gemacht? (`AI_USAGE.md`)
