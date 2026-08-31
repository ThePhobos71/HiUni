# HiUni Entwickler-Guide

> Kochbuch für Phase 2+. Jedes Rezept ist ein Copy-Paste-Pattern mit File-Pfaden und Snippets.
>
> Stand: 2026-08-25

## App-Architektur in 30 Sekunden

- **Feature-First Packages** in `:app` (keine Gradle-Module — siehe ADR-0001)
- Pro Feature: `feature/<name>/ui/<Name>Screen.kt`, `feature/<name>/<Name>ViewModel.kt`, `feature/<name>/data/*`
- **Shared Infra** in `core/*`: `design` (Theme/Tokens/Components), `database`, `network`, `datastore`, `security`, `notifications`, `auth`, `push`, `sync`, `search`, `nfc`, `icon`, `startup`, `common`
- **Hilt** für alle Dependencies, **Room** als Single AppDatabase, **OkHttp** Singleton, **EncryptedSharedPreferences** für Credentials
- **Cross-Feature-Regel:** nur `feature.home` darf andere Feature-Repos injecten. `feature.email` darf `core.security.CredentialsManager` ziehen. Sonst gilt: kein Feature importiert ein anderes.
  - **Zweite Ausnahme:** `feature.widgets` liest Feature-Repos read-only über den `WidgetHiltEntryPoint` (Glance-Widgets sind keine Hilt-Componenten, siehe Recipe J).

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

**CI + Release:** die Pipeline liegt in `.forgejo/workflows/ci.yml` — bei jedem Push und PR laufen
`testDebugUnitTest`, `assembleDebug` und `lintDebug`, Lint- und Test-Reports landen als Artefakte.
Signierte Release-Builds sind in [`RELEASE_SIGNING.md`](RELEASE_SIGNING.md) beschrieben — Keystore-Werte kommen
aus `local.properties` bzw. CI-Secrets, nie ins Repo.

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

> Die `version = 2` oben ist nur das Beispiel-Delta. Der echte Stand ist inzwischen deutlich höher
> (aktuell `version = 35`) — immer den Wert aus `AppDatabase.kt` lesen, `+1` rechnen und die passende
> `MIGRATION_<n>_<n+1>` in `core/database/Migrations.kt` anhängen.

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

`NotificationScheduler`, `NotificationReceiver`, `NotificationPresenter` und `NotificationDeepLinkController`
liegen in `core/notifications/`. Pro `NotificationCategory` wird in `HiUniApplication.onCreate()` ein eigener
Android-Channel registriert (idempotent).

```kotlin
// Anywhere in a UseCase / Repo / ViewModel:
@Inject lateinit var scheduler: NotificationScheduler

fun scheduleReminder(event: CustomEventEntity) {
    val minutesBefore = event.reminderMinutesBefore ?: return
    val triggerAt = event.startTime.minus(Duration.ofMinutes(minutesBefore.toLong()))
    scheduler.schedule(
        eventId = event.id,
        title = event.title,
        triggerAt = triggerAt,
        kind = NotificationKind.EVENT,   // steuert Icon + Filter im Push-Center
        body = event.location            // optional, sonst Default-String
    )
}

fun cancelReminder(event: CustomEventEntity) {
    scheduler.cancel(event.id)
}
```

`NotificationReceiver` postet die Notification selbst nicht — es delegiert an `NotificationPresenter`
(`NotificationCompat.Builder` + `NotificationManagerCompat.notify`) und schreibt den Eintrag zusätzlich ins
Push-Center. Bei wiederkehrenden Events plant der Receiver direkt den nächsten Termin nach. Ein Tap landet über
`NotificationDeepLinkController` im richtigen Screen — dasselbe Muster wie beim `WidgetDeepLinkController`
(Recipe J).

---

## Recipe F — Background-Sync mit WorkManager (Email-Polling, Mensa-Refresh)

WorkManager + Hilt-Work ist im Stack. Lebende Beispiele: `core/sync/LsfSyncWorker.kt`,
`core/sync/SportSyncWorker.kt`, `core/push/MailPushSyncWorker.kt`, `core/push/PushRegistrationWorker.kt`.
Schema (hier mit einem fiktiven Email-Worker):

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

## Recipe J — Neues Glance-Widget hinzufügen

Die App hat aktuell **fünf** Home-Screen-Widgets, alle unter `feature/widgets/`:

| Widget | Package | Datenquelle | Deep-Link-Action |
|---|---|---|---|
| Stundenplan (Tag) | `widgets/schedule/StundenplanWidget.kt` | `CalendarRepository.observeRange` | `ACTION_OPEN_CALENDAR` |
| Stundenplan (Woche) | `widgets/scheduleweek/SchedulaWeekWidget.kt` | `CalendarRepository.observeRange` | `ACTION_OPEN_CALENDAR_WEEK` |
| Mensa | `widgets/mensa/MensaWidget.kt` | `MensaRepository` | `ACTION_OPEN_MENSA` |
| Aufgaben | `widgets/todos/TodoWidget.kt` | `TodosRepository.observeOpen` | `ACTION_OPEN_TODOS` |
| Klausur-Countdown | `widgets/exams/ExamCountdownWidget.kt` | `LsfExamsRepository.observeAll` | `ACTION_OPEN_EXAMS` |

Stack: `androidx.glance:glance-appwidget` + `glance-material3` (Version in `libs.versions.toml`, Key `glance`).
Gemeinsames Design-Kit in `feature/widgets/common/`. Ein neues Widget sind acht Schritte.

> **ACHTUNG vorab — Glance-Limit von 10 Kindern pro Container**
>
> Glance rendert nach `RemoteViews`. Die generierten Container-Layouts (`Row`/`Column`/`Box`) haben
> eine **harte Obergrenze von 10 direkten Kindern**. Bei 11+ kappt
> `LayoutSelection.insertContainerView` stillschweigend auf 10 und loggt nur einen
> `IllegalArgumentException`-Stacktrace — das 11. Kind verschwindet einfach, kein sichtbarer Crash-Dialog.
>
> **Bug-Historie:** genau daran ist das Klausur-Countdown-Widget gestorben (Commit `1a39710`). Die
> `MetaRow` emittierte Datum/Zeit/Raum als je drei Kinder (`Image` + `Spacer` + `Text`) plus zwei
> Trenn-`Spacer` → 3·3 + 2 = **11 Kinder**. Fix: jeder Chip bekam einen eigenen `Row`-Wrapper, damit er
> als *ein* Kind zählt.
>
> Regeln daraus:
> - **Jedes** emittierte Element zählt — jeder `Spacer`, jedes `Image`, jedes `Text`.
> - Eine verschachtelte Composable-Funktion zählt mit ihrer **entpackten** Kinderzahl, wenn sie
>   nicht selbst einen Container aufmacht. Chip-/Row-Helper deshalb immer in ein eigenes
>   `Row`/`Column` wickeln.
> - **Dynamische Listen gehören in `LazyColumn`** (`androidx.glance.appwidget.lazy`) — die ist von der
>   Grenze nicht betroffen. Nur statische Container-Kinder zählen.
> - Wenn eine gemappte Liste doch in einem `Column`/`Row` landen muss, den Cap mit
>   `capForContainer()` aus `common/WidgetLimits.kt` berechnen statt eine Magic Number hinzuschreiben:
>
> ```kotlin
> // Header + Footer sind feste Geschwister, dazu kommt eine "+N"-Overflow-Zeile:
> val max = capForContainer(fixedSiblings = 2, reserveForOverflow = true)  // → 7
> items.take(max).forEach { Chip(it) }
> if (items.size > max) Text("+${items.size - max}")
> ```
>
> `GLANCE_MAX_CONTAINER_CHILDREN` (= 10) und `capForContainer()` sind bewusst Glance-frei und damit
> unit-testbar — siehe `app/src/test/.../widgets/common/WidgetLimitsTest.kt`.

### 1. Widget-Klasse

```kotlin
// feature/widgets/bib/BibWidget.kt
class BibWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(180.dp, 110.dp)   // ~2×2
        private val MEDIUM = DpSize(250.dp, 180.dp)  // ~3×3
        private val LARGE = DpSize(320.dp, 280.dp)   // ~4×5
    }

    // Responsive statt Exact: der Launcher bekommt drei Layouts, wir lesen im
    // Composable `LocalSize.current` und entscheiden pro Breakpoint, was rein passt.
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = WidgetHiltEntryPoint.get(context).bibRepository()
        provideContent {
            val rooms by repo.observeFreeRooms().collectAsState(initial = emptyList())
            Content(rooms)
        }
    }

    @Composable
    private fun Content(rooms: List<RoomEntity>) {
        val context = LocalContext.current
        val size = LocalSize.current

        val openApp = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                action = WidgetDeepLinkController.ACTION_OPEN_BIB
                flags = Intent.FLAG_ACTIVITY_NEW_TASK   // Widget-Context ist kein Activity-Context
            }
        )

        WidgetSurface(onClick = openApp) {
            WidgetHeader(
                iconRes = R.drawable.ic_widget_place,
                title = "Gruppenräume",
                context = if (rooms.isNotEmpty()) "(${rooms.size})" else null,
            )
            Spacer(GlanceModifier.height(WidgetTheme.HeaderBottomSpacing))

            if (rooms.isEmpty()) {
                WidgetEmpty(iconRes = R.drawable.ic_widget_place, message = "Alles belegt")
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(items = rooms, itemId = { it.id }) { room -> RoomRow(room) }
                }
            }
        }
    }
}
```

Konventionen, die alle fünf Widgets teilen:
- `remember { LocalDate.now() }` / `remember(context) { … }` für alles, was pro Recomposition stabil
  bleiben soll — Glance recomposed bei jedem Flow-Emit.
- Repository-Flows mit `collectAsState(initial = emptyList())` einsammeln. Kein ViewModel — Glance hat
  keinen ViewModelStore.
- Filtern/Sortieren passiert im Widget (`remember(data, today) { … }`), damit kein Widget-spezifisches
  Repo-API nötig ist.

### 2. Receiver

Reine Boilerplate, aber Pflicht: der Manifest-`<receiver>` braucht eine konkrete
`BroadcastReceiver`-Subklasse, und Glance bietet kein Receiver-Alias.

```kotlin
// feature/widgets/bib/BibWidgetReceiver.kt
class BibWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BibWidget()
}
```

### 3. Daten via `WidgetHiltEntryPoint`

`GlanceAppWidget` ist **keine** Hilt-Componente (kein `@AndroidEntryPoint` möglich), also gehen
Dependencies über einen `@EntryPoint` am `SingletonComponent`. Neues Repo = eine Zeile in
`feature/widgets/WidgetHiltEntryPoint.kt`:

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetHiltEntryPoint {
    fun todosRepository(): TodosRepository
    fun calendarRepository(): CalendarRepository
    fun mensaRepository(): MensaRepository
    fun examsRepository(): LsfExamsRepository
    fun bibRepository(): BibRepository        // <-- neu

    companion object {
        fun get(context: Context): WidgetHiltEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, WidgetHiltEntryPoint::class.java)
    }
}
```

Das ist die zweite bewusste Ausnahme von der Cross-Feature-Regel (die erste ist `feature.home`) —
Widgets lesen fremde Repos, schreiben aber nur über deren offizielle Suspend-APIs.

### 4. Design-Kit nutzen, nicht neu erfinden

Es gibt **kein** `MaterialTheme` im Widget-Prozess. Statt Theme-Tokens gilt `common/`:

| Baustein | Datei | Wofür |
|---|---|---|
| `WidgetTheme` | `common/WidgetTheme.kt` | Farben (`Surface`, `OnSurface`, `OnSurfaceMuted`, `OnSurfaceFaint`, `Primary`/`PrimaryContainer`, `Green`/`Amber`/`Red` + `*Surface`) und Layout-Werte (`CardCornerRadius`, `CardPadding*`, `RowSpacing`, `HeaderBottomSpacing`) |
| `WidgetSurface(onClick) { … }` | `common/WidgetSurface.kt` | Card-Rahmen: `fillMaxSize` + Radius + Background + Padding, ganze Fläche optional klickbar |
| `WidgetHeader(iconRes, title, context, actionIconRes, onAction)` | `common/WidgetHeader.kt` | Icon + Titel + optionaler Kontext-Text rechts + optionales Action-Icon |
| `WidgetEmpty(iconRes, message)` | `common/WidgetEmpty.kt` | Einheitlicher Empty-State (großes muted Icon + Text, zentriert) |
| `WidgetPalette.colorFor(key)` | `common/WidgetPalette.kt` | Deterministische Kurs-Farbe (`bg`/`fg`/`dot`), spiegelt `feature/calendar/ui/CourseColor.kt` → gleiche Vorlesung, gleicher Akzent in App und Widget |
| `capForContainer()` / `GLANCE_MAX_CONTAINER_CHILDREN` | `common/WidgetLimits.kt` | Der 10-Kinder-Cap (siehe Warnbox oben) |

Farben sind `androidx.glance.color.ColorProvider(day = …, night = …)` — der Launcher wählt selbst. Nie
`Color(...)` direkt in ein Widget schreiben, sonst bricht der Dark-Mode.

Icons: eigene monochrome Vektoren `res/drawable/ic_widget_*.xml` (`calendar`, `check_circle`, `circle`,
`clock`, `exam`, `place`, `plus`, `schedule`, `todo`, `utensils`), immer via
`ColorFilter.tint(WidgetTheme.…)` eingefärbt. Kein `Icons.Outlined.*` — Compose-Material-Icons gibt's
in Glance nicht.

### 5. `widget_info.xml` + Description-String

```xml
<!-- res/xml/bib_widget_info.xml -->
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_bib_description"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:targetCellWidth="3"
    android:targetCellHeight="2"
    android:minResizeWidth="180dp"
    android:minResizeHeight="110dp"
    android:previewLayout="@layout/glance_default_loading_layout"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:widgetCategory="home_screen" />
```

- `targetCellWidth/Height` (API 31+) verankert das Widget am Launcher-Grid, `minWidth/minHeight` sind
  der Fallback für Launcher, die Cells ignorieren.
- `glance_default_loading_layout` kommt aus der Glance-Library — kein eigenes Layout nötig.
- `updatePeriodMillis="0"` ist Absicht: wir aktualisieren über Room-Flows, nicht über den
  AppWidget-Timer (siehe Schritt 7).
- Die `minWidth/minHeight` sollten zum kleinsten `DpSize` aus Schritt 1 passen.

Description in `res/values/strings.xml` (wird im Widget-Picker angezeigt, also nutzerlesbar formulieren):

```xml
<string name="widget_bib_description">Freie Gruppenräume in der Bib</string>
```

### 6. Manifest-Eintrag

```xml
<!-- app/src/main/AndroidManifest.xml, zu den anderen Widget-Receivern -->
<receiver
    android:name=".feature.widgets.bib.BibWidgetReceiver"
    android:exported="true"
    android:label="@string/widget_bib_description">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/bib_widget_info" />
</receiver>
```

`android:exported="true"` ist Pflicht (der Launcher schickt den Broadcast von außen).

### 7. Update-Trigger

Drei Wege, in dieser Reihenfolge bevorzugt:

1. **Room-Flow** — Standardfall. `collectAsState` auf einem DAO-Flow: schreibt irgendwo in der App ein
   Repo in die Tabelle, rendert Glance von selbst neu. Deshalb `updatePeriodMillis="0"`.
2. **Explizit nach einer Widget-Aktion** — im `ActionCallback` nach dem Schreiben
   `MeinWidget().update(context, glanceId)` aufrufen (siehe `TodoWidgetActions.kt`).
3. **`GlanceAppWidgetManager` / `updateAll`** — für Daten, die nicht in Room hängen (reine
   Netzwerk-Snapshots). Braucht dann einen Trigger aus einem WorkManager-Job (Recipe F).

Interaktive Buttons im Widget laufen über `ActionCallback` + `actionRunCallback<T>()`:

```kotlin
// feature/widgets/todos/TodoWidgetActions.kt (verkürzt)
internal val TODO_ID_PARAM = ActionParameters.Key<Long>("todoId")

class ToggleDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val todoId = parameters[TODO_ID_PARAM] ?: return
        val repo = WidgetHiltEntryPoint.get(context).todosRepository()
        repo.setDone(todoId, true)
        TodoWidget().update(context, glanceId)   // Flow zieht eh nach, aber so ist es sofort
    }

    companion object {
        fun parameters(todoId: Long): ActionParameters = actionParametersOf(TODO_ID_PARAM to todoId)
    }
}
```

Im Composable: `.clickable(actionRunCallback<ToggleDoneAction>(ToggleDoneAction.parameters(todo.id)))`.
Wichtig: Row-Klick (Deep-Link) und Icon-Klick (Toggle) sind zwei getrennte `clickable`, sonst frisst der
äußere Container den inneren Tap.

### 8. Deep-Link ins richtige Ziel wiren

Drei Code-Stellen — plus eine, an der man bewusst *nichts* tut.

**(a) Action-Konstante + SharedFlow** in `feature/widgets/WidgetDeepLinkController.kt`:

```kotlin
private val _openBib = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
val openBib: SharedFlow<Unit> = _openBib.asSharedFlow()

// in handleIntent(intent):
ACTION_OPEN_BIB -> { Timber.i("WidgetDeepLink: OPEN_BIB"); _openBib.tryEmit(Unit); true }

// companion:
const val ACTION_OPEN_BIB = "de.transio.hiuni.OPEN_BIB"
```

**(b) `MainActivity`: nichts tun.** Die ruft in `onCreate` *und* `onNewIntent` schon
`if (widgetDeepLink.handleIntent(intent)) intent.action = null` auf. Das Null-Setzen verhindert, dass der
Deep-Link bei jedem Config-Change erneut feuert.

**(c) Bridge-ViewModel** `NfcNavViewModel` in `navigation/AppNavGraph.kt` (trägt trotz des Namens auch
die Notification- und Widget-Flows): `val openBib: SharedFlow<Unit> = widgetDeepLink.openBib`.

**(d) Collector** im `AppNavGraph`:

```kotlin
LaunchedEffect(Unit) {
    nfcNav.openBib.collect {
        navController.navigate(Destination.Bib.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}
```

`MainActivity` ist `singleTask`, deshalb landet ein Tap bei laufender App in `onNewIntent` statt eine
zweite Instanz zu starten. Widget-Intents brauchen immer `FLAG_ACTIVITY_NEW_TASK`.

**Scope-Konvention (V1):** ein Widget-Tap öffnet nur den *Ziel-Tab*, keine Sub-Deeplinks und kein
automatisch geöffnetes Sheet. Extras wie `EXTRA_TODO_ID` sind am Intent schon vorgesehen, werden aber
noch nicht ausgewertet — Follow-Up.

### Testen

- Unit-testbar ist nur reine Logik (Cap-Rechnung, Countdown-Formatter). Glance-Composables selbst
  testen wir nicht — siehe `WidgetLimitsTest.kt` als Vorbild: Helper Glance-frei halten, dann testen.
- Manuell: `./gradlew installDebug`, Widget aufs Home legen, in **allen drei** Größen resizen (die
  `SizeMode.Responsive`-Breakpoints greifen erst beim Resize) und Dark-Mode umschalten.
- Nach Änderungen am Layout `adb logcat | grep -i glance` mitlaufen lassen — das 10-Kinder-Problem
  zeigt sich *nur* dort, nicht im UI.

---

## Recipe K — Tests

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

**Achtung Widgets:** in Glance gibt es kein `MaterialTheme` und keine `HiUniColors` — dort gilt
`WidgetTheme` / `WidgetPalette` aus `feature/widgets/common/` (Recipe J). Die Werte sind absichtlich
Kopien derselben Palette, damit App und Home-Screen gleich aussehen; wer hier eine Farbe ändert, muss
sie dort mitziehen.

---

## Optionale Drittanbieter-APIs (TMDB)

Filme werden aus unifilm.de gescraped. Die Poster dort sind oft niedrig aufgelöst, daher reichern wir mit **TMDB** (The Movie Database) an — bessere Poster + deutsche Overviews.

Aktivierung:
1. Auf https://www.themoviedb.org/settings/api API-Key holen (kostenlos, sofort verfügbar)
2. In `local.properties` (git-ignored) hinzufügen:
   ```
   tmdb.api.key=DEIN_KEY
   ```
3. Rebuild — `MoviesRepository` enricht jeden gescrapeten Film parallel via `coroutineScope { async { ... }.awaitAll() }`

Ohne Key: TMDB-Service ist `isConfigured = false`, Repository nutzt unifilm-Daten unverändert. Build + Lint + Tests laufen trotzdem grün.

---

## Wiederverwendbare UI-Bausteine

In `core/design/components/` liegen Composables, die jeder Screen frei nutzen kann.
Diese Trennung wäre normalerweise eine ADR-0001-Verletzung, aber Design-Primitives
gehören explizit ins `core/design/` (siehe ADR-0001 Cross-Feature-Regeln).

| Composable | Datei | Wofür |
|---|---|---|
| `SectionLabel(text, trailing, onTrailingClick)` | `SectionLabel.kt` | Titel + optionale klickbare „Alle anzeigen"-Action; Standard für Listen-Sections |
| `QuickTile(icon, title, subtitle, accent, surface, onClick, badge)` | `QuickTile.kt` | 2x2-Grid-Kachel à la Mensa/Bib/Mails/Aufgaben mit Badge-Support |
| `HiUniTopBar(title, onBack, subtitle, roundedBottom, trailing)` | `HiUniTopBar.kt` | Standard-Kopfzeile für Detail-Screens |
| `HiUniSearchBar(query, onQueryChange, onClose, placeholder, autoFocus)` | `HiUniSearchBar.kt` | Einklappbares Suchfeld |
| `EmptyState(icon, title, body, …)` | `EmptyState.kt` | „Nichts da"-Zustand mit Icon-Pillow |
| `ErrorState(onRetry, retryLabel, icon, …)` | `ErrorState.kt` | Fehlerzustand inkl. Retry-Button |
| `OfflineBanner(visible)` | `OfflineBanner.kt` | Animiertes Offline-Hinweisband |
| `StalenessLabel(lastRefreshEpoch)` + `shouldShowStaleness(...)` | `StalenessLabel.kt` | „Daten von vor X" bei alten Caches; Schwellwert-Logik separat testbar |
| `SkeletonLine(...)` / `rememberSkeletonColor()` | `HiUniSkeleton.kt` | Shimmer-Platzhalter während Ladephasen |

**Erweiterung-Pattern** für eine neue Section auf der Home:
1. Composable `private fun NeueSection(...)` in `feature/home/ui/HomeScreen.kt` anlegen
2. `SectionLabel(text = "Mein Bereich", trailing = "Alle", onTrailingClick = ...)` für den Header
3. `Card` oder `QuickTile`-Reihen für den Inhalt
4. Aufruf in der `HomeScreen` Column zwischen den existierenden Sections; `Spacer(18.dp)` als Trenner gibt es schon automatisch via `Arrangement.spacedBy(18.dp)`

**Erweiterung-Pattern** für einen neuen Screen mit Quick-Access-Kacheln:
```kotlin
@Composable
fun ProfilScreen(onNavigate: (Destination) -> Unit) {
    Column { ...
        SectionLabel(text = "Schnellzugriff")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Email,
                title = "Mails",
                subtitle = "5 ungelesen",
                accent = MaterialTheme.colorScheme.primary,
                surface = MaterialTheme.colorScheme.primaryContainer,
                onClick = { onNavigate(Destination.Email) },
                badge = 5
            )
            QuickTile(...)
        }
    }
}
```

**Pattern für Navigation aus Feature-Screens:** Akzeptiere `onNavigate: (Destination) -> Unit` als ersten Parameter (Default `= {}` für Composable-Previews). In `navigation/AppNavGraph.kt` wird der Callback einmal zentral gewired: `navController.navigate(dest.route) { popUpTo(start) { saveState = true }; launchSingleTop = true; restoreState = true }`.

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
| Dependency in einem Glance-Widget | `@EntryPoint @InstallIn(SingletonComponent::class)` | `feature/widgets/WidgetHiltEntryPoint.kt` |

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
| Widget rendert das 11. Element nicht, Logcat zeigt „container cannot have more than 10 elements" | Glance-Limit von 10 direkten Kindern pro `Row`/`Column`/`Box` | Liste in `LazyColumn`, Chips in eigenen `Row`-Wrapper, Cap via `capForContainer()` — siehe Recipe J |
| Widget bleibt auf „Loading" stehen | `WidgetHiltEntryPoint` kennt das Repo nicht oder `provideGlance` wirft | Repo-Methode im EntryPoint ergänzen; `adb logcat \| grep -i glance` |

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
- **Widgets:** Warum kein ViewModel in Glance? Wie kommen die Daten rein (`WidgetHiltEntryPoint`), und
  warum landen dynamische Listen in einer `LazyColumn`? (Recipe J)
- **AI-Workflow:** Was hat Claude generiert? Was habt ihr selbst gemacht? (`AI_USAGE.md`)
