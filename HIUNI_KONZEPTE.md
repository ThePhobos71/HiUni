# HiUni — Master-Dokumentation: Ideen, Konzepte & Code

> Eine vollständige Sammlung aller Features, Architektur-Entscheidungen, Code-Patterns und Zukunftsideen des HiUni-Projekts an einem Ort.

**Stand:** 2026-05-18
**Branch:** `main`
**Repository:** `de.transio.hiuni`

---

## Inhaltsverzeichnis

1. [Vision & Zweck](#1-vision--zweck)
2. [Feature-Übersicht (alle Screens)](#2-feature-übersicht-alle-screens)
3. [Architektur-Diagramm](#3-architektur-diagramm)
4. [Manual DI (HiUniApplication)](#4-manual-di-hiuniapplication)
5. [Room Database & Unified Calendar](#5-room-database--unified-calendar)
6. [Web Scraping Konzepte](#6-web-scraping-konzepte)
7. [Email Client (IMAP)](#7-email-client-imap)
8. [Mensa API Integration](#8-mensa-api-integration)
9. [Responsive Navigation (3 Layouts)](#9-responsive-navigation-3-layouts)
10. [Theming & Easter Eggs](#10-theming--easter-eggs)
11. [Notifications (AlarmManager)](#11-notifications-alarmmanager)
12. [Sicherheit (EncryptedSharedPreferences)](#12-sicherheit-encryptedsharedpreferences)
13. [Aktuelle Refaktorierung: RSS raus, Email rein](#13-aktuelle-refaktorierung-rss-raus-email-rein)
14. [Coole Code-Patterns (Highlights)](#14-coole-code-patterns-highlights)
15. [Tech-Stack & Dependencies](#15-tech-stack--dependencies)
16. [TODOs & Zukunftsideen](#16-todos--zukunftsideen)
17. [Dateistruktur](#17-dateistruktur)

---

## 1. Vision & Zweck

**HiUni** ist eine **Begleit-App für Studierende der Uni Hildesheim**, die verschiedene universitäre Services in einer einheitlichen, modernen Android-App bündelt:

- **Speiseplan** (Mensa STW-ON API)
- **Filmprogramm** (unifilm.de Scraper)
- **Bibliotheks-Raumbuchung** (Gruppenräume)
- **Uni-Mail** (IMAP via Jakarta Mail)
- **Persönlicher Kalender** mit Aggregation aller Quellen
- **Lokale Benachrichtigungen** vor wichtigen Terminen

Alles in einer App, alles offline-fähig (Room DB), alles responsiv (Phone / Tablet / Landscape).

---

## 2. Feature-Übersicht (alle Screens)

| Screen | Status | Quelle | Highlights |
|---|---|---|---|
| **Home** | Aktiv | Calendar + Email | Nächster Termin, heutige Mensa, ungelesene E-Mails |
| **Calendar** | Aktiv | Room DB | 4 Ansichten (Liste/Tag/Woche/Stundenplan) |
| **Mensa** | Aktiv | STW-ON REST API | 14 Tage Plan, Allergene, Nährwerte, "in Kalender" |
| **Email** | Aktiv | IMAP (Uni-Hildesheim) | Master-Detail, Pull-to-Refresh, Anhänge |
| **Movies** | Aktiv | unifilm.de (Jsoup) | Poster, FSK, Trailer-Links, "in Kalender" |
| **Bib** | Aktiv (NEU) | ubwww.uni-hildesheim.de | Gruppenraum-Verfügbarkeit (30-Min-Slots) |
| **Settings** | Aktiv | SharedPreferences | Mensa-Standort, Notifications, Nav-Reordering |
| **About** | Aktiv | — | Triple-Tap Easter Egg → 3 Themes |
| **Welcome** | First Launch | — | Onboarding mit Feature-Übersicht |
| **Profile** | Stub | — | Auskommentiert, vorbereitet |
| **Notifications** | Stub | — | Screen existiert, nicht navigiert |
| **Timetable** | Stub | — | Eigene CalendarViewType, kein echter Stundenplan |

---

## 3. Architektur-Diagramm

```
┌──────────────────────────────────────────────────────────────┐
│                    UI Layer (Jetpack Compose)                │
│  ┌─────────┐ ┌──────────┐ ┌────────┐ ┌───────┐ ┌─────────┐  │
│  │  Home   │ │ Calendar │ │ Mensa  │ │ Email │ │ Movies  │  │
│  └────┬────┘ └────┬─────┘ └───┬────┘ └───┬───┘ └────┬────┘  │
│       │           │           │           │          │       │
│  ┌────┴───────────┴───────────┴───────────┴──────────┴────┐ │
│  │              ViewModels (StateFlow + combine)           │ │
│  └────────────────────────┬────────────────────────────────┘ │
└───────────────────────────┼──────────────────────────────────┘
                            │
┌───────────────────────────┴──────────────────────────────────┐
│                     Repository Layer                          │
│  CalendarRepository  EmailRepository  BibRepository           │
│  (Mensa+Movies merged into Room)                              │
└───┬──────────────┬─────────────┬─────────────┬───────────────┘
    │              │             │             │
┌───┴────┐  ┌──────┴──────┐ ┌────┴────┐ ┌─────┴──────────────┐
│ Room   │  │ MensaApi    │ │ Movie/  │ │ Jakarta Mail IMAP  │
│ (SQLite│  │ (REST JSON) │ │ Bib     │ │ + EncryptedPrefs   │
│ + DAO) │  │             │ │ Scraper │ │                    │
└────────┘  └─────────────┘ └─────────┘ └────────────────────┘
```

**Pattern:** MVVM + Repository + Single Source of Truth (Room als Cache)

---

## 4. Manual DI (HiUniApplication)

Kein Hilt, kein Koin — **bewusst manuell** gehalten für minimale Build-Zeit:

```kotlin
// HiUniApplication.kt
class HiUniApplication : Application() {

    var easterEggColorScheme by mutableStateOf(0)
        private set

    fun cycleEasterEggColors() {
        easterEggColorScheme = (easterEggColorScheme + 1) % 4
    }

    private val database by lazy { AppDatabase.getInstance(this) }
    private val mensaApiService by lazy { MensaApiService() }
    private val movieScraper by lazy { MovieScraper() }
    private val bibScraper by lazy { BibScraper() }

    val calendarRepository by lazy {
        CalendarRepository(
            context = applicationContext,
            eventDao = database.calendarEventDao(),
            mensaApi = mensaApiService,
            movieScraper = movieScraper
        )
    }

    val emailRepository by lazy { EmailRepository(applicationContext) }
    val bibRepository by lazy { BibRepository(bibScraper) }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            calendarRepository.rescheduleAllNotifications()
        }
    }
}
```

**Cooler Trick:** Easter-Egg-Color-Scheme als `mutableStateOf` im Application-Singleton → triggert Compose-Recomposition app-weit.

---

## 5. Room Database & Unified Calendar

**Kernidee:** Alle Event-Quellen (Mensa-API, Movie-Scraper, User-Custom) landen in **EINER** Tabelle mit Diskriminator `sourceOrigin`.

```kotlin
@Entity(
    tableName = "calendar_events",
    indices = [Index(value = ["sourceOrigin", "sourceIdentifier"], unique = true)]
)
data class UnifiedCalendarEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String?,
    val startTime: Instant,
    val endTime: Instant,
    val location: String?,
    val eventType: String,      // MENSA_API, MENSA_USER, MOVIE_USER, CUSTOM_USER
    val timeOfDay: String?,     // "noon" | "evening"
    val price: String?,
    val lane: String?,
    val sourceIdentifier: String,
    val sourceOrigin: String,   // MENSA_API, MOVIE_SCRAPER, ...
    val metadata: String? = null // JSON
)
```

**Atomic Refresh-Pattern** im DAO:

```kotlin
@Transaction
suspend fun refreshSourceData(origin: String, newEvents: List<UnifiedCalendarEvent>) {
    deleteBySourceOrigin(origin)
    upsertAll(newEvents)
}
```

→ Verhindert, dass die App zwischen "alt gelöscht, neu noch nicht da" leer wird.

**Filter im CalendarViewModel** (nur User-Events anzeigen):

```kotlin
val userCalendarEvents = allEvents.filter { event ->
    event.eventType == "MENSA_USER" ||
    event.eventType == "MOVIE_USER" ||
    event.eventType == "CUSTOM_USER"
}
```

Mensa-API-Events sind **immer** in der DB (für Home-Screen "heute"), aber im Kalender nur die vom User explizit gemerkten.

---

## 6. Web Scraping Konzepte

### 6.0 Authentifizierung — Klarstellung: **kein SSO**

Wichtig vorweg: HiUni nutzt **kein** echtes SSO (Shibboleth, OAuth, OIDC, CAS). Es gibt:

| Service | Auth-Modell | Realisierung |
|---|---|---|
| Mensa API (STW-ON) | **Keine** | Öffentliche REST API |
| Movies (unifilm.de) | **Keine** | Öffentlicher Scraper |
| Bib-Räume (ubwww) | **Keine** | Öffentlicher Scraper |
| Email (Uni-Hildesheim) | **IMAP user/pass** | Direkt-Login mit RZ-Kennung |

Es existiert eine **generische Login-Routine** in `WebScraper.kt`, die aktuell **nirgendwo verwendet** wird — sie ist ein Vorbereitung für zukünftige Scraper (z.B. LSF/Stud.IP-Stundenplan), die hinter Shibboleth liegen.

#### Generischer Form-Login (vorbereitet, ungenutzt)

```kotlin
// WebScraper.kt — Konstruktor: nimmt User-Agent und baut OkHttpClient mit CookieJar
class WebScraper(private val userAgent: String) {
    private val client: OkHttpClient

    init {
        client = OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                private val cookieStore = mutableMapOf<String, List<Cookie>>()
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookieStore[url.host] = cookies
                }
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return cookieStore[url.host] ?: emptyList()
                }
            })
            .followRedirects(true)
            .build()
    }

    suspend fun login(
        loginUrl: String,
        usernameField: String,
        usernameValue: String,
        passwordField: String,
        passwordValue: String,
        additionalFormData: Map<String, String> = emptyMap()
    ): Boolean = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add(usernameField, usernameValue)
            .add(passwordField, passwordValue)
            .apply { additionalFormData.forEach { (k, v) -> add(k, v) } }
            .build()

        val request = Request.Builder()
            .url(loginUrl)
            .post(formBody)
            .header("User-Agent", userAgent)
            .build()

        client.newCall(request).execute().use { response ->
            response.isSuccessful  // ← naiv: Status 200 ≠ Login erfolgreich!
        }
    }

    suspend fun fetchProtectedPage(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url).get()
            .header("User-Agent", userAgent)
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    }
}
```

**In-Memory Cookie Store:** Die Cookie-Map ist `mutableMapOf` — keine Persistenz. Beim App-Neustart müsste neu eingeloggt werden.

**Known Limitation:** Der Code prüft nur HTTP 200 — viele Webseiten antworten bei falschem Login auch mit 200 und einer Error-Page. Für echtes Shibboleth-SSO bräuchte man:
1. GET Login-Seite → CSRF-Token extrahieren
2. POST Credentials + Token → Folge Redirect-Kette (IdP → SP → Service)
3. Validieren via Test-Request auf geschützte Ressource

#### Email-Auth (das einzige aktive Login)

E-Mail nutzt **direkte IMAP-Authentifizierung** mit AES-256 verschlüsselt gespeicherten Credentials:

```kotlin
// EmailRepository.connect() — vereinfacht
val username = credentialsManager.getUsername(context)
val password = credentialsManager.getPassword(context)

val session = Session.getInstance(props, null)
val store = session.getStore("imaps")

try {
    store.connect(IMAP_HOST, username, password)
    Result.success(store)
} catch (authEx: AuthenticationFailedException) {
    Log.e("EmailRepository", "Authentication failed", authEx)
    Result.failure(authEx)
}
```

Die User gibt Username/Passwort einmal in den Settings ein → wird via `CredentialsManager.saveCredentials()` AES-256-GCM-verschlüsselt → bei jedem IMAP-Request entschlüsselt.

---

### 6.1 MovieScraper (unifilm.de)

**URL-Pattern:** `https://www.unifilm.de/studentenkinos/{city}` (Default: `Hildesheim`)

**Auth:** Keine — komplett öffentliche Seite.

**Browser-Spoofing:**
```kotlin
.header("User-Agent",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
```

#### DOM-Struktur die geparst wird

Unifilm.de rendert für jeden Film **zwei DOM-Knoten**:
1. `<li class="film" data-id="123" data-sid="456">` — Listen-Eintrag mit Poster
2. `<div class="film-showcase" data-id="123" data-sid="456">` — Detail-Block mit Beschreibung

Beide werden über `data-id` + `data-sid` korreliert:

```kotlin
val filmElements = document.select("li.film")

for (filmElem in filmElements) {
    val filmId = filmElem.attr("data-id")
    val sessionId = filmElem.attr("data-sid")
    if (filmId.isNullOrBlank() || sessionId.isNullOrBlank()) continue

    // Cross-Reference auf Showcase
    val showcase = document.selectFirst(
        "div.film-showcase[data-id=$filmId][data-sid=$sessionId]"
    ) ?: continue

    val movie = parseSingleFilm(filmElem, showcase)
}
```

#### Vollständige CSS-Selector-Map

| Feld | Selector | Element |
|---|---|---|
| Titel | `h1.headline-h3 > span` | showcase |
| Untertitel | `h1.headline-h3 span.headline-normalcase` | showcase |
| Special Info | `h1.headline-h3` (ownText) | showcase |
| Poster | `img` (`absUrl("src")`) | filmElem |
| Trailer | `video.film-trailer source` (`absUrl("src")`) | showcase |
| Datum | `ul.film-info-termin li.datum` | showcase, Fallback: `.filmtermin` in filmElem |
| Uhrzeit | `ul.film-info-termin li.uhrzeit` | showcase, Fallback: `.filmuhrzeit` |
| Ort | `ul.film-info-termin li.raum` | showcase, Fallback: `.filmraum` |
| Beschreibung | `div.film-info-text > p` (joinToString `"\n\n"`) | showcase |
| Filmdaten | `ul.film-info-filmdaten li` (siehe Heuristik unten) | showcase |
| Auszeichnungen | `li.film-nominierungen:contains(Preise:)` | showcase |
| Nominierungen | `li.film-nominierungen:contains(Nominierungen:)` | showcase |
| Past-Marker | `filmElem.hasClass("film-past")` | filmElem |

#### Heuristik für unstrukturierte Filmdaten

Die `ul.film-info-filmdaten` enthält Regie, FSK, Land, Dauer, Genre — **alle als reine `<li>`-Texte ohne Klassen**. Workaround: String-Pattern-Matching:

```kotlin
showcase.select("ul.film-info-filmdaten li").forEach { li ->
    val text = li.text()
    when {
        text.startsWith("R:") -> director = text.substringAfter(":").trim()
        text.startsWith("FSK") -> fsk = text
        text.matches(Regex("[A-Z, ]+")) && text.length < 20 -> country = text
        text.endsWith("Min.") -> duration = text
        else -> genre = text  // Fallback-Klassifizierung
    }
}
```

**Fragil:** Wenn unifilm.de das Format ändert (z.B. "Regie:" statt "R:"), bricht der Director-Extract.

#### Komplettes Movie-Modell

```kotlin
data class Movie(
    val filmId: String,
    val sessionId: String,
    val cinemaId: String,          // filmElem.attr("data-cid")
    val posterUrl: String?,
    val trailerUrl: String?,
    val title: String,
    val subtitle: String?,
    val date: String?,
    val time: String?,
    val location: String?,
    val description: String,
    val director: String?,
    val country: String?,
    val fsk: String?,
    val genre: String?,
    val duration: String?,
    val awards: String?,
    val nominations: String?,
    val specialInfo: String?,
    val isPast: Boolean            // filmElem.hasClass("film-past")
)
```

---

### 6.2 BibScraper (Gruppenräume)

**Pain Points gelöst:**
- `SocketTimeoutException` → 30s Timeouts statt Default
- ` ` (non-breaking space) im HTML → ersetzt durch normales Space
- Datums-Strings multilingual: `"heute"`, `"Montag, 15.04.2024"`

```kotlin
private val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

val roomNames = document.select(".arbeitsplatz")
    .map { it.text().replace(' ', ' ').trim() }
    .toSortedSet()

// Datum parsen
val date = when {
    text.equals("heute", ignoreCase = true) -> LocalDate.now()
    text.length >= 10 -> LocalDate.parse(
        text.takeLast(10),
        DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)
    )
    else -> null
}
```

**Datenmodell:**

```kotlin
typealias BibOverview = Map<LocalDate, BibDayData>

data class BibDayData(val dayTag: String, val rooms: Map<String, List<BibBookingInfo>>)
data class BibBookingInfo(val time: String, val occupied: Boolean, val booked: Boolean, val bookable: Boolean)
```

#### Vollständiger Algorithmus

**Schritt A — Raumnamen extrahieren (NBSP-Cleanup):**

```kotlin
val roomNames = document.select(".arbeitsplatz")
    .map { it.text().replace(' ', ' ').trim() }
    .toSortedSet()
```

**Schritt B — Datum parsen (3 Formate):**

```kotlin
val (dateCompact, dayTag) = when {
    text.equals("heute", ignoreCase = true) ->
        today.format(DateTimeFormatter.ofPattern("yyyyMMdd")) to "Heute"
    text.length >= 10 -> {
        val dateString = text.takeLast(10)        // "15.04.2024"
        val dayOfWeek  = if (text.length > 13) text.dropLast(13) else text
        val parsed = LocalDate.parse(dateString,
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN))
        parsed.format(DateTimeFormatter.ofPattern("yyyyMMdd")) to dayOfWeek
    }
    else -> null to null
}
dayTagsByCompactDate.remove("umDa")  // bekannter Garbage-Wert
```

**Schritt C — Time-Slots Konstante (8:00 - 21:00 / 30 Min):**

```kotlin
val timeSlots = listOf(
    "800",  "830",  "900",  "930",  "1000", "1030", "1100",
    "1130", "1200", "1230", "1300", "1330", "1400", "1430",
    "1500", "1530", "1600", "1630", "1700", "1730", "1800",
    "1830", "1900", "1930", "2000", "2030", "2100"
)
```

**Schritt D — Cell-IDs parsen (Format `cell-{yyyyMMdd}-{room}-{HHmm}`):**

```kotlin
val cells = document.select("td[id^=cell-]")
for (cell in cells) {
    val id = cell.id()                           // "cell-20240625-F101-0800"
    val style = cell.attr("style")
    if (id.length < 18) continue

    val extractedRoom = "F" + id.takeLast(3)
    val date = id.substring(5, 13)
    val time = id.substring(14, id.length - 4)

    val bookingInfo = when (style) {
        "background-color: #92CD00" -> BibBookingInfo(occupied = false, booked = false, bookable = true)  // Grün = frei
        "background-color: #DF2E3B" -> BibBookingInfo(occupied = true,  booked = false, bookable = true)  // Rot = besetzt
        "background-color: #999999" -> BibBookingInfo(occupied = true,  booked = true,  bookable = true)  // Grau = gebucht
        else -> null
    }
    // ... in tempRoomsByDate[date][room][time] eintragen
}
```

**Status-Encoding läuft über Inline-CSS-Hex-Farben** — keine Klassen, keine data-Attribute. Wenn die Webseite ihre Farben ändert (z.B. zu `#92CD0A`), bricht der Scraper still.

**Schritt E — Fill-Down-Logik (clever!)**

Die Webseite gibt **nur explizit gefärbte Zellen** zurück — leere Zellen erben den Status der vorhergehenden. Daher Fill-Forward:

```kotlin
val finalRoomsByDate = tempRoomsByDate.mapValues { (_, roomData) ->
    roomData.mapValues { (_, timeSlotMap) ->
        var lastOccupied: Boolean? = null
        var lastBooked:   Boolean? = null
        timeSlots.associateWith { time ->
            val currentInfo = timeSlotMap[time]
            if (currentInfo == null) {
                BibBookingInfo(
                    occupied = lastOccupied ?: false,
                    booked   = lastBooked   ?: false,
                    bookable = false           // geerbte Slots sind nicht buchbar
                )
            } else {
                lastOccupied = currentInfo.occupied
                lastBooked   = currentInfo.booked
                currentInfo
            }
        }
    }
}
```

→ Klassisches "Fill-Forward" aus Pandas-Welt: wenn 10:00 "besetzt" und 10:30 leer → 10:30 ebenfalls besetzt bis nächster expliziter Wechsel.

---

## 7. Email Client (IMAP)

**Server:** `imap.uni-hildesheim.de:993` (IMAPS, SSL)
**Library:** Jakarta Mail / Angus Mail
**Storage:** EncryptedSharedPreferences (AES-256 GCM)

### Connection Setup

```kotlin
val props = Properties().apply {
    put("mail.store.protocol", "imaps")
    put("mail.imaps.host", "imap.uni-hildesheim.de")
    put("mail.imaps.port", 993)
    put("mail.imaps.ssl.enable", "true")
    put("mail.imaps.connectiontimeout", "10000")
    put("mail.imaps.timeout", "10000")
}

val session = Session.getInstance(props, null)
val store = session.getStore("imaps")
store.connect(username, password)
```

### Thread-Safe Refresh mit Mutex

```kotlin
private val refreshMutex = Mutex()

suspend fun refreshInbox(count: Int = 25): Result<List<EmailMessage>> =
    refreshMutex.withLock {
        _state.update { it.copy(isLoading = true, lastError = null) }
        val result = fetchInboxMessages(count)
        result.fold(
            onSuccess = { messages ->
                _state.update {
                    it.copy(messages = messages, isLoading = false, hasLoadedOnce = true)
                }
            },
            onFailure = { err ->
                _state.update { it.copy(isLoading = false, lastError = err) }
            }
        )
        result
    }
```

→ Verhindert, dass parallele Pull-to-Refresh-Aktionen zwei IMAP-Connections aufbauen.

### Email Parsing

- HTML-Body wird mit **Jsoup** in Plain-Text konvertiert (`Jsoup.parse(html).text()`)
- Anhänge werden ins App-Cache-Directory gespeichert
- `FileProvider` öffnet sie via Intent.ACTION_VIEW

---

## 8. Mensa API Integration

**Base URL:** `https://sls.api.stw-on.de/v1`

```kotlin
val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true  // null → Default für non-nullable Felder
}

val response = json.decodeFromString<MensaResponse>(body)
```

**Endpoints:**
- `GET /locations` — alle STW-ON Standorte
- `GET /locations/{id}/menu/{from}/{to}` — Speiseplan (max. 14 Tage)

**Settings-Filter:** Standardmäßig Standort-ID 150 (Hildesheim), Settings-Dropdown filtert auf Hildesheim-Orte.

**Mapping zu Calendar-Events** (CalendarRepository):

```kotlin
suspend fun refreshMensaData() {
    val locationId = SettingsManager.getSelectedLocationId(context)
    try {
        val mensaResponse = mensaApiService.getFutureMenu(locationId, 14).getOrThrow()
        val apiEvents = mensaResponse.meals.mapNotNull { meal -> mealToApiEvent(meal) }
        eventDao.refreshSourceData("MENSA_API", apiEvents)
    } catch (e: Exception) {
        Log.e("CalendarRepository", "Failed to refresh Mensa data", e)
    }
}
```

---

## 9. Responsive Navigation (3 Layouts)

**Window Size Classes** (Material 3) entscheiden zur Laufzeit:

| Größe | Layout | Gerät |
|---|---|---|
| `COMPACT` (<600dp) | Bottom Navigation + Modal Drawer | Phone Portrait |
| `MEDIUM` (600-840dp) | Navigation Rail | Phone Landscape / Small Tablet |
| `EXPANDED` (>840dp) | Permanent Drawer | Tablet / Foldable |

```kotlin
when (navigationType) {
    NavigationType.BOTTOM_NAVIGATION -> {
        ModalNavigationDrawer(...) {
            Scaffold(
                topBar = { AppTopBar(...) },
                bottomBar = { AdaptiveBottomNavigation(...) }
            ) { padding -> AppNavHost(modifier = Modifier.padding(padding)) }
        }
    }
    NavigationType.NAVIGATION_RAIL -> {
        Row {
            AdaptiveNavigationRail(...)
            Scaffold(...) { AppNavHost(...) }
        }
    }
    NavigationType.PERMANENT_DRAWER -> {
        AdaptivePermanentDrawer(...) {
            Scaffold(...) { AppNavHost(...) }
        }
    }
}
```

**Bonus:** Nutzer kann in Settings die Bottom-Nav-Reihenfolge **per Drag & Drop** umsortieren (`NavigationCustomizationScreen.kt`). Persistiert via SettingsManager + StateFlow trigger.

---

## 10. Theming & Easter Eggs

### Hidden Feature: Triple-Tap auf About-Logo

```kotlin
// AboutScreen.kt
var tapCount by remember { mutableStateOf(0) }

Icon(
    modifier = Modifier.clickable {
        tapCount++
        if (tapCount >= 3) {
            (context.applicationContext as HiUniApplication).cycleEasterEggColors()
            tapCount = 0
        }
    }
)
```

### 3 Easter-Egg-Themes

| # | Name | Primary | Vibe |
|---|---|---|---|
| 1 | Purple-Blue | `#4A148C` Deep Purple | Lila Galaxie |
| 2 | Orange | `#E65100` Deep Orange | Sonnenuntergang |
| 3 | Black OLED | `#000000` Pure Black | OLED Battery Saver |

Theme 3 ist **funktional**: Pure Black spart auf OLED-Displays Akku, da schwarze Pixel ausgeschaltet sind.

```kotlin
// EasterEggColors.kt — Theme 3 (OLED)
val EasterEgg3DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2C2C2C),
    background = Color(0xFF000000),   // True Black
    surface = Color(0xFF000000),
    onBackground = Color.White
)
```

---

## 11. Notifications (AlarmManager)

**Lokale Notifications** vor Kalender-Events. Server-frei.

```kotlin
// NotificationScheduler.kt
val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
val triggerAtMillis = event.startTime.toEpochMilli() - (minutesBefore * 60_000L)

val intent = Intent(context, NotificationReceiver::class.java).apply {
    putExtra("event_id", event.id)
    putExtra("event_title", event.title)
}
val pendingIntent = PendingIntent.getBroadcast(
    context, event.id.toInt(), intent, PendingIntent.FLAG_IMMUTABLE
)

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    && alarmManager.canScheduleExactAlarms()) {
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
} else {
    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
}
```

**Smart Detail:** `setExactAndAllowWhileIdle` durchbricht Doze-Mode — wichtig damit Notifications auch nachts feuern. Permission `SCHEDULE_EXACT_ALARM` (Android 12+) wird gecheckt.

**App-Start Re-Schedule:** Bei jedem App-Start lädt `HiUniApplication.onCreate()` alle Alarms neu, da der OS sie bei Reboot vergisst.

---

## 12. Sicherheit (EncryptedSharedPreferences)

Email-Credentials werden mit **AES-256 GCM** verschlüsselt gespeichert:

```kotlin
// CredentialsManager.kt
private fun getEncryptedSharedPreferences(context: Context): EncryptedSharedPreferences? {
    return try {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            "secure_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    } catch (e: Exception) {
        // Fallback: Keystore zurücksetzen wenn korrumpiert
        null
    }
}
```

**Zwei Encryption Schemes:**
- Keys → AES256-SIV (deterministisch, ermöglicht Lookup)
- Values → AES256-GCM (authenticated, jeder Write hat neuen IV)

#### Self-Healing Reset-Pattern

Wenn der Android-Keystore korrumpiert (z.B. nach App-Datenwipe, OEM-Update, Backup-Restore), wirft `EncryptedSharedPreferences.create()` Exceptions. `CredentialsManager` hat dafür **automatischen Recovery**:

```kotlin
fun resetEncryptedPreferences(context: Context): Boolean {
    val sharedPreferencesFile = context.applicationContext
        .getSharedPreferences(PREFERENCE_FILE_KEY, Context.MODE_PRIVATE)
    val masterKeyFile = context.applicationContext
        .getSharedPreferences("_androidx_security_master_key_", Context.MODE_PRIVATE)

    sharedPreferencesFile.edit().clear().apply()
    masterKeyFile.edit().clear().apply()
    return true
}

fun saveCredentials(context: Context, username: String, password: String): Boolean {
    var sharedPreferences = getEncryptedSharedPreferences(context)
    if (sharedPreferences == null) {
        // Erste Recovery-Versuch
        if (resetEncryptedPreferences(context)) {
            sharedPreferences = getEncryptedSharedPreferences(context)
        }
    }
    try {
        sharedPreferences?.edit()?.apply {
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
            apply()
        }
        true
    } catch (e: Exception) {
        // Zweiter Recovery-Versuch nach Save-Fehler
        if (resetEncryptedPreferences(context)) { /* retry */ }
        false
    }
}
```

Plus eine `diagnoseCredentials()`-Funktion für Support-Debug:

```kotlin
fun diagnoseCredentials(context: Context): String {
    return "Credentials Status:\n" +
        "- Can create encrypted preferences: ${getEncryptedSharedPreferences(context) != null}\n" +
        "- Username stored: ${getUsername(context) != null}\n" +
        "- Password stored: ${getPassword(context) != null}"
}
```

#### Konstanten

```kotlin
private const val PREFERENCE_FILE_KEY = "de.transio.hiuni.secure_prefs"
private const val KEY_USERNAME = "username"
private const val KEY_PASSWORD = "password"
```

---

## 12.1 IMAP-Connection-Details

Die `EmailRepository.connect()`-Methode ist die einzige Stelle, an der Credentials *außerhalb* von `CredentialsManager` auftauchen:

```kotlin
private suspend fun connect(): Result<Store> = withContext(Dispatchers.IO) {
    val username = credentialsManager.getUsername(context)
    val password = credentialsManager.getPassword(context)

    if (username.isNullOrBlank() || password.isNullOrBlank()) {
        return@withContext Result.failure(
            IllegalStateException("Benutzername oder Passwort nicht gesetzt.")
        )
    }

    val props = Properties().apply {
        put("mail.store.protocol", "imaps")
        put("mail.imaps.host", "imap.uni-hildesheim.de")
        put("mail.imaps.port", 993)
        put("mail.imaps.ssl.enable", "true")
        put("mail.imaps.connectiontimeout", "10000")
        put("mail.imaps.timeout", "10000")
    }

    val session = Session.getInstance(props, null)
    try {
        val store = session.getStore("imaps")
        store.connect("imap.uni-hildesheim.de", username, password)
        Result.success(store)
    } catch (authEx: AuthenticationFailedException) {
        Result.failure(authEx)  // Falsches Passwort
    } catch (e: Exception) {
        Result.failure(e)        // Network / SSL Errors
    }
}
```

**MIME-Parsing (Multipart-Mails):**

```kotlin
private fun parseMessageContent(message: Message): Pair<String, List<Attachment>> {
    val bodyText = StringBuilder()
    val attachments = mutableListOf<Attachment>()

    when {
        message.isMimeType("text/*") -> bodyText.append(message.content.toString())
        message.isMimeType("multipart/*") -> {
            val multipart = message.content as MimeMultipart
            for (i in 0 until multipart.count) {
                val bodyPart = multipart.getBodyPart(i)
                when {
                    Part.ATTACHMENT.equals(bodyPart.disposition, true) &&
                    !bodyPart.fileName.isNullOrBlank() -> {
                        attachments.add(Attachment(
                            fileName = MimeUtility.decodeText(bodyPart.fileName),
                            sizeBytes = bodyPart.size
                        ))
                    }
                    bodyPart.isMimeType("text/html") -> {
                        if (bodyText.isEmpty()) bodyText.append(bodyPart.content.toString())
                    }
                    bodyPart.isMimeType("text/plain") -> {
                        if (bodyText.isEmpty())
                            bodyText.append(bodyPart.content.toString().replace("\n", "<br>"))
                    }
                }
            }
        }
    }
    return Pair(bodyText.toString(), attachments)
}
```

**MIME-Encoded Subject decoden:**

```kotlin
val decodedSubject = try {
    MimeUtility.decodeText(message.subject ?: "")
} catch (e: Exception) {
    message.subject ?: "(Ohne Betreff)"
}
```

→ Wichtig für deutsche Umlaute in Subjects (`=?UTF-8?B?...?=`-Encoding).

**Attachment-Download zu FileProvider-URI:**

```kotlin
suspend fun saveAttachmentToCache(emailId: Int, attachment: Attachment): Result<Uri> {
    // 1. IMAP-Connection neu öffnen
    // 2. Message via emailId holen
    // 3. Matching BodyPart finden (Vergleich über decodeText(fileName))
    // 4. Stream nach context.cacheDir/{fileName}
    // 5. FileProvider.getUriForFile(context, "${packageName}.provider", file)
}
```

→ Braucht im `AndroidManifest.xml` einen `<provider>`-Eintrag mit Authority `${applicationId}.provider`.

---

### Was für einen Rebuild noch nötig wäre (Auth/Scraper-spezifisch)

| Komponente | Was fehlt in der Doku |
|---|---|
| `AndroidManifest.xml` | `INTERNET`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, FileProvider mit `file_paths.xml` |
| `file_paths.xml` | Pfade für FileProvider (`cache-path` für Anhänge) |
| Mensa-API-Modelle | `Meal`, `Allergen`, `Additive`, `NutritionalValues`, `Price`, `Location` — alle `@Serializable` |
| `BibScraper` Style-Strings | Die exakten Hex-Codes (#92CD00, #DF2E3B, #999999) reverse-engineered |
| `MovieScraper` data-Attribute | `data-cid` (Cinema ID) wird gesetzt, aber nicht in dieser Doku erwähnt |
| Shibboleth-SSO | **Nicht implementiert** — wenn man LSF/Stud.IP-Scraping will, müsste man IdP-Flow neu bauen |
| Encrypted-Prefs Reset | Self-Healing-Pattern muss exakt repliziert werden, sonst krasht App nach Restore |

---

## 13. Aktuelle Refaktorierung: RSS raus, Email rein

### Was wurde entfernt (komplett gelöscht)

- `data/RssRepository.kt` (57 Zeilen)
- `model/RssModels.kt` (25 Zeilen)
- `networking/RssScraper.kt` (196 Zeilen)

→ **Insgesamt 278 Zeilen RSS-Code weg.**

### Was wurde ersetzt

**Home-Screen** zeigte vorher Uni-News (RSS), jetzt **ungelesene E-Mails**:

```kotlin
HomeSectionCard(
    title = "Ungelesene E-Mails",
    icon = Icons.Default.Email,
    badge = if (unreadCount > 0) unreadCount.toString() else null,
    onClick = onNavigateToEmail
) {
    UnreadEmailsContent(unreadEmails, unreadCount)
}
```

**HomeViewModel** lädt jetzt aus zwei Quellen:

```kotlin
val nextEvent: StateFlow<UnifiedCalendarEvent?>     // Calendar
val todaysMensa: StateFlow<List<UnifiedCalendarEvent>>  // Calendar (gefiltert)
val unreadEmails: StateFlow<List<EmailMessage>>     // Email (max. 3)
val unreadEmailsCount: StateFlow<Int>               // Total Counter für Badge
```

### Navigation-Icon Update

```kotlin
// NavigationItems.kt
// VORHER: Icons.Filled.Newspaper / "Uni News"
// NACHHER:
NavItem(
    route = AppDestinations.HOME_ROUTE,
    title = "Home",
    icon = Icons.Filled.Home
)
```

### EmailRepository State Pattern (NEU)

```kotlin
data class EmailRepoState(
    val messages: List<EmailMessage> = emptyList(),
    val isLoading: Boolean = false,
    val lastError: Throwable? = null,
    val hasLoadedOnce: Boolean = false
)

private val _state = MutableStateFlow(EmailRepoState())
val state: StateFlow<EmailRepoState> = _state.asStateFlow()
```

→ Repository hält State, mehrere ViewModels können denselben State observen (Home + Email-Screen sehen synchrone Daten).

---

## 14. Coole Code-Patterns (Highlights)

### Pattern A: StateFlow `combine` für Multi-Source UI-State

```kotlin
// CalendarViewModel.kt
val uiState: StateFlow<CalendarUiState> = combine(
    repository.getEvents(from, to),
    _currentView,
    _selectedDate
) { allEvents, view, date ->
    val userCalendarEvents = allEvents.filter { it.eventType.endsWith("_USER") }
    CalendarUiState(
        events = userCalendarEvents,
        currentView = view,
        selectedDate = date
    )
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = CalendarUiState(isLoading = true)
)
```

`WhileSubscribed(5000)` = bleibt 5s aktiv nach letztem Observer → überlebt Rotation ohne neuen DB-Read.

### Pattern B: Pull-to-Refresh ohne extra Lib

```kotlin
// EmailScreen.kt
val pullToRefreshState = rememberPullToRefreshState()

LaunchedEffect(pullToRefreshState.isRefreshing) {
    if (pullToRefreshState.isRefreshing) viewModel.loadEmails()
}

LaunchedEffect(uiState.isLoading) {
    if (!uiState.isLoading) pullToRefreshState.endRefresh()
}
```

### Pattern C: Adaptive Time-Label "Heute / Morgen / Datum"

```kotlin
private fun formatEventTime(event: UnifiedCalendarEvent): String {
    val zone = TimeZone.getDefault().toZoneId()
    val start = event.startTime.atZone(zone)
    val today = LocalDate.now(zone)
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
    val dayLabel = when (ChronoUnit.DAYS.between(today, start.toLocalDate())) {
        0L -> "Heute"
        1L -> "Morgen"
        else -> start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.GERMAN))
    }
    return "$dayLabel · ${start.format(timeFormatter)} Uhr"
}
```

### Pattern D: `AnimatedContent` für View-Switching

```kotlin
AnimatedContent(targetState = uiState.currentView) { view ->
    when (view) {
        CalendarViewType.LIST -> ListView(...)
        CalendarViewType.DAY -> DayView(...)
        CalendarViewType.WEEK -> WeekView(...)
        CalendarViewType.TIMETABLE -> TimetableView(...)
    }
}
```

### Pattern E: `Result<T>`-Returns für klare Fehler-Propagation

```kotlin
suspend fun fetchOverview(): Result<BibOverview> = runCatching {
    val doc = client.newCall(request).execute().use { resp ->
        Jsoup.parse(resp.body?.string() ?: "")
    }
    parseDocument(doc)
}
```

→ Caller entscheidet via `.fold {}` oder `.getOrNull()` was passiert.

---

## 15. Tech-Stack & Dependencies

| Kategorie | Library | Zweck |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Deklarative UI |
| Navigation | Navigation Compose | Single-Activity Architecture |
| Async | Kotlin Coroutines + Flow | StateFlow, suspend funs |
| DB | Room + KSP | SQLite ORM, Code-Gen |
| HTTP | OkHttp | Connection Pooling, Cookies |
| HTML | Jsoup | Web Scraping, Email HTML→Text |
| JSON | kotlinx.serialization | Mensa API Models |
| Email | Angus Mail (Jakarta Mail) | IMAP Client |
| Security | androidx.security.crypto | EncryptedSharedPreferences |
| Images | Coil | Async Image Loading (Movie Posters) |
| Lifecycle | Lifecycle ViewModel Compose | viewModel() factory |

**Build:** Gradle 8 mit Kotlin DSL, KSP für Room.

---

## 16. TODOs & Zukunftsideen

### Im Code markiert

- `CalendarScreen.kt:705` — Date Picker im Add-Event-Dialog
- `CalendarScreen.kt:717` — Time Picker im Add-Event-Dialog

### Vorbereitet, aber nicht aktiv

- `ProfileScreen.kt` — existiert, auskommentiert
- `NotificationsScreen.kt` — existiert, nicht in Navigation
- `TimetableViewModel.kt` — kein echter Stundenplan-Scraper

### Brainstorm: was wäre als Nächstes cool?

1. **iCal / CalDAV Export** — User kann den Kalender in Google/Apple Calendar importieren
2. **Push-Notifications** statt nur lokale AlarmManager — z.B. via FCM für "Bib-Raum X frei geworden"
3. **Offline-Cache** für Mensa-API (aktuell jeder API-Call live)
4. **Geofencing** — Push, wenn man die Mensa betritt
5. **Widget** — Home-Screen-Widget mit heutigem Speiseplan
6. **Wear OS Companion** — Speiseplan auf der Uhr
7. **Dark Mode Auto** auf Sunrise/Sunset basiert
8. **Stundenplan-Scraper** für LSF / Stud.IP
9. **Sharing** — Event als ICS-Datei teilen
10. **Notification Channels** pro Feature (Mensa, Termine, Bib separat)
11. **Material You Dynamic Color** voll integrieren (Android 12+)
12. **Multiplatform** — KMP für iOS-Variante (Repository-Layer wäre wiederverwendbar)
13. **Cache-Invalidierung BibScraper** — aktuell jeder Aufruf live
14. **Mensa-Empfehlungen** — basierend auf "User hat 5x das Schnitzel in Kalender gepackt"

### Schema-Migrations zu beachten

Aktuelle Room-Version: **4**, mit `fallbackToDestructiveMigration()`. Für Production-Release sollten echte Migrations geschrieben werden — sonst verliert jeder Update-User seine Custom Events.

---

## 17. Dateistruktur

```
app/src/main/java/de/transio/hiuni/
├── HiUniApplication.kt           # Manual DI, App-Singleton
├── MainActivity.kt               # NavHost, Window Size Class Setup
│
├── ui/
│   ├── NavigationItems.kt        # Routes + Items (Bottom + Drawer)
│   ├── home/                     # Home Screen (NEU: Email-Card)
│   ├── calendar/                 # 4-View Kalender
│   ├── mensa/                    # Speiseplan + In-Kalender
│   ├── email/                    # IMAP Client + Detail
│   ├── movies/                   # Kino-Programm
│   ├── bib/                      # Bibliothek Raumbuchung
│   ├── settings/                 # Settings + Nav-Reorder
│   ├── welcome/                  # First Launch
│   ├── about/                    # Easter Egg
│   ├── timetable/                # (Stub)
│   ├── profile/                  # (auskommentiert)
│   ├── notifications/            # (Stub)
│   ├── responsive/               # Adaptive Components
│   └── theme/                    # Material 3 + 3 EasterEgg Themes
│
├── model/
│   ├── UnifiedCalendarEvent.kt   # Room Entity (Single Source)
│   ├── EmailModels.kt
│   ├── MensaModels.kt
│   ├── MovieModels.kt
│   └── BibModels.kt
│
├── data/
│   ├── AppDatabase.kt            # Room v4
│   ├── CalendarEventDao.kt
│   ├── CalendarRepository.kt     # Mensa + Movies + Events merged
│   ├── EmailRepository.kt        # IMAP + State (NEU: EmailRepoState)
│   ├── BibRepository.kt          # Scraper-Wrapper
│   ├── MensaApiService.kt        # REST Client
│   ├── CredentialsManager.kt     # AES-256 GCM
│   └── SettingsManager.kt        # SharedPreferences + StateFlow
│
├── networking/
│   ├── WebScraper.kt             # Base mit Cookies/Login
│   ├── MovieScraper.kt           # unifilm.de
│   └── BibScraper.kt             # ubwww.uni-hildesheim.de
│
└── util/
    ├── FirstLaunchManager.kt
    ├── NotificationScheduler.kt  # AlarmManager
    ├── NotificationReceiver.kt   # BroadcastReceiver
    └── NotificationManager.kt
```

**Gelöscht in aktueller Branch:**
- `data/RssRepository.kt`
- `model/RssModels.kt`
- `networking/RssScraper.kt`

---

## Schlusswort

HiUni ist ein erstaunlich kompletter Showcase für **modernes Android Development**:
- Single-Activity + Compose
- MVVM + Repository + Room
- StateFlow überall, kein LiveData
- Manual DI ohne Hilt-Overhead
- Responsive bis zum Tablet
- Echte Security (Encrypted Prefs)
- Hidden Easter Eggs

Die Refaktorierung von RSS → Email zeigt: Das Projekt ist kein toter Code, sondern wird aktiv verbessert. Die Architektur hat den Austausch problemlos überlebt — genau dafür ist sie gebaut.

**Next Up:** RSS-Loslösung committen, Bib-Feature komplett testen, dann TODOs in CalendarScreen abarbeiten.
