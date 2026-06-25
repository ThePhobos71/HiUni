package de.transio.hiuni.feature.home.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import android.graphics.drawable.BitmapDrawable
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import de.transio.hiuni.core.common.DateTimeUtils
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.design.HiUniSemanticColors
import de.transio.hiuni.core.design.components.QuickTile
import de.transio.hiuni.core.design.components.QuickTileSpec
import de.transio.hiuni.core.design.components.SectionLabel
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.calendar.ui.courseColorFor
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.home.HomeSection
import de.transio.hiuni.feature.home.HomeSectionsViewModel
import de.transio.hiuni.feature.home.HomeUiState
import de.transio.hiuni.feature.home.HomeViewModel
import de.transio.hiuni.feature.home.QuickAccessTile
import de.transio.hiuni.feature.home.QuickAccessViewModel
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.feature.movies.data.MovieEntity
import de.transio.hiuni.feature.movies.data.isSurpriseScreening
import de.transio.hiuni.feature.todos.ui.CoursePill
import de.transio.hiuni.feature.todos.ui.courseShortLabel
import de.transio.hiuni.navigation.Destination
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class TodayLesson(
    val course: String,
    val room: String,
    val professor: String,
    val time: String,
    val accent: Color
)

private data class NewsItem(
    val title: String,
    val body: String,
    val date: String,
    val urgent: Boolean
)

@Composable
fun HomeScreen(
    onNavigate: (Destination) -> Unit = {},
    onOpenMovie: (filmId: String, sessionId: String) -> Unit = { _, _ -> },
    onOpenMensaCard: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    sectionsViewModel: HomeSectionsViewModel = hiltViewModel(),
    quickAccessViewModel: QuickAccessViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sections by sectionsViewModel.visible.collectAsStateWithLifecycle()
    val quickAccessTiles by quickAccessViewModel.visible.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val dateLineFmt = DateTimeFormatter.ofPattern("EEEE · d. MMMM yyyy", Locale.GERMAN)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        HomeHeader(
            greeting = "Hi",
            name = state.greetingName.ifBlank { "Studi" },
            dateLine = state.today.format(dateLineFmt).uppercase(Locale.GERMAN),
            unreadNotifications = state.unreadNotifications,
            nextLesson = state.nextEvent?.title ?: "Keine Termine",
            nextLessonMeta = formatNextEventMeta(state.nextEvent),
            onAvatarClick = { onNavigate(Destination.Profile) },
            onBellClick = { onNavigate(Destination.Notifications) },
            onNextEventClick = { onNavigate(Destination.Calendar) }
        )

        Spacer(Modifier.height(18.dp))

        ReorderableColumn(
            items = sections,
            itemKey = { it.id },
            onCommit = { ids -> sectionsViewModel.setOrder(ids) },
            modifier = Modifier.padding(horizontal = 18.dp),
            spacing = 18.dp
        ) { section, dragHandle, _ ->
            Box(modifier = dragHandle.fillMaxWidth()) {
                when (section) {
                    HomeSection.QuickAccess -> {
                        if (quickAccessTiles.isNotEmpty()) {
                            QuickAccessGrid(
                                tiles = quickAccessTiles,
                                onReorder = { ids -> quickAccessViewModel.setOrder(ids) },
                                buildSpec = { tile ->
                                    buildQuickTileSpec(
                                        tile = tile,
                                        state = state,
                                        onNavigate = onNavigate,
                                        onOpenMensaCard = onOpenMensaCard,
                                        colors = colors,
                                        semantics = semantics
                                    )
                                }
                            )
                        }
                    }

                    HomeSection.Today -> {
                        val todaysLessons = state.todaysMeals.take(2).map { meal ->
                            TodayLesson(
                                course = meal.name,
                                room = state.mensaLocation?.name?.removePrefix("Mensa Uni ") ?: "Mensa",
                                professor = meal.category,
                                time = if (meal.category.contains("Abend", ignoreCase = true)) "18:00" else "12:00",
                                accent = if (meal.category.contains("Abend", ignoreCase = true)) semantics.amber else colors.primary
                            )
                        }
                        if (todaysLessons.isNotEmpty()) {
                            TodaySection(
                                lessons = todaysLessons,
                                onShowAll = { onNavigate(Destination.Mensa) }
                            )
                        }
                    }

                    HomeSection.Films -> if (state.upcomingMovies.isNotEmpty()) {
                        FilmTeaserSection(
                            movies = state.upcomingMovies,
                            onShowAll = { onNavigate(Destination.Movies) },
                            onClickFilm = { movie -> onOpenMovie(movie.filmId, movie.sessionId) }
                        )
                    }

                    HomeSection.Todos -> OpenTodosSection(
                        todos = state.openTodos,
                        coursesById = state.openTodosCoursesById,
                        onShowAll = { onNavigate(Destination.Todos) },
                        onToggleDone = { todo -> viewModel.toggleTodoDone(todo) }
                    )

                    HomeSection.News -> NewsSection(
                        items = listOf(
                            NewsItem(
                                title = "Einschreibung läuft noch!",
                                body = "Bis 31. Mai können Kurse für das WS 2026/27 belegt werden.",
                                date = "17. Mai",
                                urgent = true
                            ),
                            NewsItem(
                                title = "Bibliothek Di geschlossen",
                                body = "Wegen Renovierungsarbeiten bleibt die Bib am 19. Mai zu.",
                                date = "16. Mai",
                                urgent = false
                            ),
                            NewsItem(
                                title = "Campusfest am 24. Mai",
                                body = "Sommerfest auf dem Campus — alle sind herzlich willkommen!",
                                date = "15. Mai",
                                urgent = false
                            )
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    greeting: String,
    name: String,
    dateLine: String,
    unreadNotifications: Int,
    nextLesson: String,
    nextLessonMeta: String,
    onAvatarClick: () -> Unit,
    onBellClick: () -> Unit,
    onNextEventClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    val headerShape = RoundedCornerShape(bottomStart = HiUniRadii.big, bottomEnd = HiUniRadii.big)
    Surface(
        color = colors.surface,
        shape = headerShape,
        // Weicher Schatten direkt unter der Rundung — kaschiert die harte Kante,
        // wo das dunkle Surface auf den Background trifft, und gibt dem Header
        // dezent Tiefe. Primary-getönt statt schwarz, damit es auf Dark-Theme
        // überhaupt sichtbar bleibt.
        modifier = Modifier.shadow(
            elevation = 12.dp,
            shape = headerShape,
            ambientColor = colors.primary.copy(alpha = 0.35f),
            spotColor = colors.primary.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dateLine,
                        style = MaterialTheme.typography.titleSmall,
                        color = semantics.onSurfaceMuted
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$greeting, ",
                            style = MaterialTheme.typography.displayMedium,
                            color = colors.onSurface
                        )
                        Text(
                            text = "$name!",
                            style = MaterialTheme.typography.displayMedium,
                            color = colors.primary
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarTile(initials = name.take(2).uppercase(), onClick = onAvatarClick)
                    NotificationTile(unread = unreadNotifications, onClick = onBellClick)
                }
            }

            Spacer(Modifier.height(18.dp))

            NextLessonBanner(
                title = nextLesson,
                meta = nextLessonMeta,
                onClick = onNextEventClick
            )
        }
    }
}

@Composable
private fun AvatarTile(initials: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.size(44.dp),
        color = colors.primaryContainer,
        shape = RoundedCornerShape(HiUniRadii.tile),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.primary
            )
        }
    }
}

@Composable
private fun NotificationTile(unread: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Box(modifier = Modifier.size(44.dp)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.primaryContainer,
            shape = RoundedCornerShape(HiUniRadii.tile),
            onClick = onClick
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Benachrichtigungen",
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .widthIn(min = 18.dp)
                    .height(18.dp)
                    .clip(CircleShape)
                    .background(semantics.red)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = unread.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun NextLessonBanner(title: String, meta: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.primaryContainer,
        shape = RoundedCornerShape(HiUniRadii.card),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile))
                    .background(colors.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = "NÄCHSTE VORLESUNG",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary.copy(alpha = 0.75f)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun QuickAccessGrid(
    tiles: List<QuickAccessTile>,
    onReorder: (List<String>) -> Unit,
    buildSpec: (QuickAccessTile) -> QuickTileSpec
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(text = "Schnellzugriff")
        // Long-press auf einer Kachel startet das Reordering; Single-Tap bleibt funktional,
        // weil detectDragGesturesAfterLongPress die Geste erst nach dem Long-Press claimed.
        ReorderableGrid(
            items = tiles,
            itemKey = { it.id },
            onCommit = onReorder,
            columns = 2,
            horizontalSpacing = 10.dp,
            verticalSpacing = 10.dp
        ) { tile, dragHandle, _ ->
            val spec = buildSpec(tile)
            QuickTile(
                modifier = dragHandle.fillMaxWidth(),
                icon = spec.icon,
                title = spec.title,
                subtitle = spec.subtitle,
                accent = spec.accent,
                surface = spec.surface,
                onClick = spec.onClick,
                badge = spec.badge
            )
        }
    }
}

private fun buildQuickTileSpec(
    tile: QuickAccessTile,
    state: HomeUiState,
    onNavigate: (Destination) -> Unit,
    onOpenMensaCard: () -> Unit,
    colors: androidx.compose.material3.ColorScheme,
    semantics: HiUniSemanticColors
): QuickTileSpec = when (tile) {
    QuickAccessTile.Mensa -> QuickTileSpec(
        icon = tile.icon,
        title = tile.label,
        subtitle = "${state.todaysMeals.size} Gerichte · ${if (state.isMensaOpen) "offen" else "geschlossen"}",
        accent = semantics.amber,
        surface = semantics.amberSurface,
        onClick = { onNavigate(Destination.Mensa) }
    )
    QuickAccessTile.Bib -> QuickTileSpec(
        icon = tile.icon,
        title = tile.label,
        subtitle = formatBibSubtitle(state.nextBibBooking),
        accent = semantics.green,
        surface = semantics.greenSurface,
        onClick = { onNavigate(Destination.Bib) }
    )
    QuickAccessTile.Email -> QuickTileSpec(
        icon = tile.icon,
        title = tile.label,
        subtitle = if (state.unreadEmails > 0) "${state.unreadEmails} ungelesen" else "Posteingang öffnen",
        accent = colors.primary,
        surface = colors.primaryContainer,
        badge = state.unreadEmails,
        onClick = { onNavigate(Destination.Email) }
    )
    QuickAccessTile.Tasks -> QuickTileSpec(
        icon = tile.icon,
        title = tile.label,
        subtitle = when (val n = state.openTodosCount) {
            0 -> "Keine offenen"
            1 -> "1 offen"
            else -> "$n offen"
        },
        accent = semantics.purple,
        surface = semantics.purpleSurface,
        onClick = { onNavigate(Destination.Todos) }
    )
    QuickAccessTile.Courses -> QuickTileSpec(
        icon = tile.icon,
        title = tile.label,
        subtitle = "LSF-Veranstaltungen",
        accent = colors.primary,
        surface = colors.primaryContainer,
        onClick = { onNavigate(Destination.Courses) }
    )
    QuickAccessTile.Movies -> QuickTileSpec(
        icon = tile.icon,
        title = tile.label,
        subtitle = if (state.upcomingMovies.isNotEmpty()) "${state.upcomingMovies.size} anstehend" else "Programm",
        accent = semantics.red,
        surface = semantics.redSurface,
        onClick = { onNavigate(Destination.Movies) }
    )
    QuickAccessTile.MensaCard -> QuickTileSpec(
        icon = tile.icon,
        title = tile.label,
        subtitle = "Guthaben scannen",
        accent = semantics.amber,
        surface = semantics.amberSurface,
        onClick = onOpenMensaCard
    )
    QuickAccessTile.Sport -> QuickTileSpec(
        icon = tile.icon,
        title = tile.label,
        subtitle = when (val n = state.upcomingSportCount) {
            0 -> "Plan ansehen"
            1 -> "1 Termin"
            else -> "$n Termine"
        },
        accent = semantics.green,
        surface = semantics.greenSurface,
        onClick = { onNavigate(Destination.Sport) }
    )
}

private fun formatNextEventMeta(event: CustomEventEntity?): String {
    if (event == null) return "Lege im Kalender deinen ersten Termin an"
    val now = Instant.now()
    val minutesUntil = Duration.between(now, event.startTime).toMinutes()
    val relative = when {
        minutesUntil < 0L -> "läuft"
        minutesUntil < 60L -> "In $minutesUntil Min"
        minutesUntil < 24L * 60 -> "In ${minutesUntil / 60} Std"
        else -> DateTimeUtils.formatRelativeDay(event.startTime)
    }
    val time = DateTimeUtils.formatTime(event.startTime)
    return buildString {
        append(relative)
        append(" · ")
        append(time)
        event.location?.takeIf { it.isNotBlank() }?.let {
            append(" · ")
            append(it)
        }
    }
}

private fun formatBibSubtitle(next: de.transio.hiuni.feature.bib.data.MyBooking?): String {
    if (next == null) return "Räume buchen"
    val today = LocalDate.now()
    val dayLabel = when (next.date) {
        today -> "Heute"
        today.plusDays(1) -> "Morgen"
        else -> next.date.format(DateTimeFormatter.ofPattern("EEE d. MMM", Locale.GERMAN))
    }
    val time = "%02d:%02d".format(next.startTime.hour, next.startTime.minute)
    return "$dayLabel · $time · ${next.roomLabel}"
}

@Composable
private fun TodaySection(lessons: List<TodayLesson>, onShowAll: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column {
        SectionLabel(text = "Heute", trailing = "Alle anzeigen", onTrailingClick = onShowAll)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lessons.forEach { lesson ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(HiUniRadii.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 15.dp, vertical = 13.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(lesson.accent)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = lesson.course,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = lesson.time,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = lesson.accent
                                )
                            }
                            Text(
                                text = "Raum ${lesson.room} · ${lesson.professor}",
                                style = MaterialTheme.typography.bodySmall,
                                color = semantics.onSurfaceMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmTeaserSection(
    movies: List<MovieEntity>,
    onShowAll: () -> Unit,
    onClickFilm: (MovieEntity) -> Unit
) {
    val semantics = HiUniColors.semantics
    val today = java.time.LocalDate.now()
    val firstDate = movies.firstOrNull()?.date
    val sectionTitle = when (firstDate) {
        today -> "Heute Abend"
        today.plusDays(1) -> "Morgen im Kino"
        else -> "Uni Kino"
    }
    Column {
        SectionLabel(text = sectionTitle, trailing = "Programm", onTrailingClick = onShowAll)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(movies, key = { it.filmId + "-" + it.sessionId }) { movie ->
                val isSurprise = movie.isSurpriseScreening()
                val fallbackColor = movie.toneColor()
                val accent = rememberCardDominantColor(
                    posterUrl = movie.posterUrl?.takeUnless { isSurprise },
                    fallback = fallbackColor
                )
                val dateFmt = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)
                val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
                val relativeDate = when (movie.date) {
                    today -> "Heute"
                    today.plusDays(1) -> "Morgen"
                    else -> movie.date?.format(dateFmt) ?: "Termin folgt"
                }
                val genreLabel = if (isSurprise) "ÜBERRASCHUNG" else movie.genre
                val titleLabel = if (isSurprise) "Überraschungsfilm" else movie.title
                val hasPoster = !movie.posterUrl.isNullOrBlank() && !isSurprise
                // 2:3 Aspect Ratio matcht das TMDB-Poster-Format (w780 = 520×780).
                val cardWidth = 140.dp
                val cardHeight = 210.dp
                Column(modifier = Modifier.width(cardWidth)) {
                    Surface(
                        color = accent,
                        shape = RoundedCornerShape(HiUniRadii.card),
                        onClick = { onClickFilm(movie) },
                        modifier = Modifier
                            .width(cardWidth)
                            .height(cardHeight)
                    ) {
                        Box {
                            // Hi-Res TMDB-Poster als Background
                            if (hasPoster) {
                                AsyncImage(
                                    model = movie.posterUrl,
                                    contentDescription = movie.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Gradient-Overlay damit Titel unten lesbar bleibt
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.55f),
                                                    Color.Black.copy(alpha = 0.85f)
                                                )
                                            )
                                        )
                                )
                            }
                            // Dekorativer Kreis oben rechts (nur ohne Poster, Mock-Look)
                            if (!hasPoster) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 18.dp, y = (-18).dp)
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.07f))
                                )
                            }
                            // Genre-Badge top-left: dunkler Backdrop für Lesbarkeit auf jedem Poster
                            if (genreLabel != null) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = genreLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                                Text(
                                    text = titleLabel,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White,
                                    maxLines = 2
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = relativeDate,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = buildList {
                            movie.time?.let { add(it.format(timeFmt) + " Uhr") }
                            movie.location?.let { add(it) }
                        }.joinToString(" · ").ifEmpty { "Programm" },
                        style = MaterialTheme.typography.labelMedium,
                        color = semantics.onSurfaceMuted
                    )
                }
            }
        }
    }
}

/**
 * Hash-basierte Hintergrundfarbe pro Film. Datum + Uhrzeit fließen mit ein damit Überraschungsfilme
 * (gleiche filmId, gleiches Placeholder-Poster) je nach Vorstellung unterschiedlich bunt werden.
 */
private fun MovieEntity.toneColor(): Color {
    val dateMix = (date?.toEpochDay()?.toInt() ?: 0)
    val timeMix = (time?.toSecondOfDay() ?: 0)
    val hash = (filmId.hashCode() xor sessionId.hashCode() xor dateMix xor timeMix).toLong() and 0xFFFFFFFFL
    val hue = (hash % 360).toFloat()
    return Color.hsl(hue = hue, saturation = 0.55f, lightness = 0.30f)
}

/**
 * Lädt das TMDB-Poster + extrahiert eine kräftige Akzent-Farbe (Palette).
 * Während des Ladens wird der Hash-Fallback genutzt; bei fehlendem Poster auch.
 */
@Composable
private fun rememberCardDominantColor(posterUrl: String?, fallback: Color): Color {
    val context = LocalContext.current
    var color by remember(posterUrl) { mutableStateOf(fallback) }
    LaunchedEffect(posterUrl) {
        if (posterUrl.isNullOrBlank()) return@LaunchedEffect
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(posterUrl)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            if (result !is SuccessResult) return@LaunchedEffect
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap).maximumColorCount(16).generate()
            }
            val swatch = palette.darkVibrantSwatch
                ?: palette.vibrantSwatch
                ?: palette.darkMutedSwatch
                ?: palette.mutedSwatch
                ?: palette.dominantSwatch
            swatch?.rgb?.let { rgb ->
                val hsl = FloatArray(3)
                androidx.core.graphics.ColorUtils.colorToHSL(Color(rgb).toArgb(), hsl)
                hsl[1] = hsl[1].coerceIn(0.35f, 0.7f)
                hsl[2] = hsl[2].coerceIn(0.22f, 0.34f)
                color = Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
            }
        }
    }
    return color
}

@Composable
private fun OpenTodosSection(
    todos: List<de.transio.hiuni.feature.todos.data.TodoEntity>,
    coursesById: Map<String, CourseEntity>,
    onShowAll: () -> Unit,
    onToggleDone: (de.transio.hiuni.feature.todos.data.TodoEntity) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column {
        SectionLabel(text = "Offene Aufgaben", trailing = "Alle anzeigen", onTrailingClick = onShowAll)
        Spacer(Modifier.height(10.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(HiUniRadii.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (todos.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowAll() }
                        .padding(horizontal = 15.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckBox,
                        contentDescription = null,
                        tint = semantics.purple,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keine offenen Aufgaben",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tippe um eine neue anzulegen.",
                            style = MaterialTheme.typography.labelMedium,
                            color = semantics.onSurfaceMuted
                        )
                    }
                }
            } else {
                Column {
                    todos.forEachIndexed { index, todo ->
                        TodoPreviewRow(
                            todo = todo,
                            course = todo.courseId?.let { coursesById[it] },
                            hasMissingCourse = todo.courseId != null && coursesById[todo.courseId] == null,
                            onToggleDone = { onToggleDone(todo) }
                        )
                        if (index < todos.lastIndex) {
                            HorizontalDivider(color = colors.outline.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoPreviewRow(
    todo: de.transio.hiuni.feature.todos.data.TodoEntity,
    course: CourseEntity?,
    hasMissingCourse: Boolean,
    onToggleDone: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val dueChip = de.transio.hiuni.feature.todos.ui.rememberDueChip(
        due = todo.dueDate,
        isDone = todo.isDone
    )
    val courseColor = course?.let { courseColorFor(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(22.dp),
            shape = CircleShape,
            color = if (todo.isDone) colors.primary else Color.Transparent,
            border = if (todo.isDone) null else androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                color = semantics.onSurfaceMuted.copy(alpha = 0.6f)
            ),
            onClick = onToggleDone
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (todo.isDone) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Erledigt",
                        tint = colors.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            if (course != null || hasMissingCourse || dueChip != null) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (course != null && courseColor != null) {
                        CoursePill(
                            label = courseShortLabel(course),
                            bg = courseColor.bg,
                            fg = courseColor.fg
                        )
                    } else if (hasMissingCourse) {
                        CoursePill(
                            label = "Kurs entfernt",
                            bg = semantics.onSurfaceMuted.copy(alpha = 0.12f),
                            fg = semantics.onSurfaceMuted
                        )
                    }
                    if (dueChip != null) {
                        Text(
                            text = dueChip.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = dueChip.accent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsSection(items: List<NewsItem>) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column {
        SectionLabel(text = "Neuigkeiten")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(HiUniRadii.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 15.dp, vertical = 13.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (item.urgent) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = semantics.red,
                                modifier = Modifier
                                    .size(16.dp)
                                    .wrapContentHeight(Alignment.Top)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Article,
                                contentDescription = null,
                                tint = semantics.onSurfaceMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .wrapContentHeight(Alignment.Top)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = item.date,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = semantics.onSurfaceMuted
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = item.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = semantics.onSurfaceMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

