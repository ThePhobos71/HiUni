package de.transio.hiuni.feature.home.ui

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
import androidx.compose.material.icons.outlined.Article
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.design.HiUniSemanticColors
import de.transio.hiuni.feature.home.HomeViewModel

private data class TodayLesson(
    val course: String,
    val room: String,
    val professor: String,
    val time: String,
    val accent: Color
)

private data class FilmTeaser(
    val title: String,
    val genre: String,
    val date: String,
    val timeRoom: String,
    val color: Color
)

private data class TodoPreview(
    val title: String,
    val due: String?,
    val dueAccent: Color?
)

private data class NewsItem(
    val title: String,
    val body: String,
    val date: String,
    val urgent: Boolean
)

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        HomeHeader(
            greeting = "Hi",
            name = "Kjell",
            dateLine = "MONTAG · 18. MAI 2026",
            unreadNotifications = 4,
            nextLesson = "Lineare Algebra",
            nextLessonMeta = "In 47 Min · 8:00 Uhr · Raum B 201"
        )

        Spacer(Modifier.height(18.dp))

        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            QuickAccessGrid(semantics = semantics)

            TodaySection(
                lessons = listOf(
                    TodayLesson("Lineare Algebra", "B 201", "Prof. Dr. Müller", "8:00", colors.primary),
                    TodayLesson("VWL Einführung", "HS 3", "Prof. Dr. Weiß", "12:00", semantics.amber)
                )
            )

            FilmTeaserSection(
                films = listOf(
                    FilmTeaser("Stadt am Meer", "Drama", "Di, 19. Mai", "20:00 · Audimax", Color(0xFF2E4F8C)),
                    FilmTeaser("Tausend Sterne", "Sci-Fi", "Mi, 20. Mai", "21:00 · HS 1", Color(0xFF563A8C)),
                    FilmTeaser("Nachtschicht", "Thriller", "Do, 21. Mai", "20:30 · Audimax", Color(0xFF7C2E33)),
                    FilmTeaser("Die Reise", "Doku", "Fr, 22. Mai", "19:30 · HS 3", Color(0xFF1F6B45))
                )
            )

            OpenTodosSection(
                todos = listOf(
                    TodoPreview("Aufgabenblatt 4 — Lineare Algebra", "Morgen", semantics.red),
                    TodoPreview("Hausarbeit VWL einreichen", "In 3 Tagen", semantics.amber),
                    TodoPreview("Mensa-Karte aufladen", "Heute", semantics.red)
                )
            )

            NewsSection(
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

@Composable
private fun HomeHeader(
    greeting: String,
    name: String,
    dateLine: String,
    unreadNotifications: Int,
    nextLesson: String,
    nextLessonMeta: String
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(bottomStart = HiUniRadii.big, bottomEnd = HiUniRadii.big)
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
                    AvatarTile(initials = name.take(2).uppercase())
                    NotificationTile(unread = unreadNotifications)
                }
            }

            Spacer(Modifier.height(18.dp))

            NextLessonBanner(
                title = nextLesson,
                meta = nextLessonMeta
            )
        }
    }
}

@Composable
private fun AvatarTile(initials: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(HiUniRadii.tile))
            .background(colors.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = colors.primary
        )
    }
}

@Composable
private fun NotificationTile(unread: Int) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Box(modifier = Modifier.size(44.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(HiUniRadii.tile))
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = "Benachrichtigungen",
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 0.dp, top = 0.dp)
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
private fun NextLessonBanner(title: String, meta: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.primary,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = "NÄCHSTE VORLESUNG",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }
        }
    }
}

@Composable
private fun QuickAccessGrid(semantics: HiUniSemanticColors) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(text = "Schnellzugriff")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.LocalDining,
                title = "Mensa heute",
                subtitle = "5 Gerichte verfügbar",
                accent = semantics.amber,
                surface = semantics.amberSurface
            )
            QuickTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.LocalLibrary,
                title = "Bibliothek",
                subtitle = "4 von 6 Räumen frei",
                accent = semantics.green,
                surface = semantics.greenSurface
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Email,
                title = "Mails",
                subtitle = "2 ungelesen",
                accent = colors.primary,
                surface = colors.primaryContainer,
                badge = 2
            )
            QuickTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.CheckBox,
                title = "Aufgaben",
                subtitle = "5 offen",
                accent = semantics.purple,
                surface = semantics.purpleSurface
            )
        }
    }
}

@Composable
private fun QuickTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    surface: Color,
    badge: Int? = null
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = surface,
        shape = RoundedCornerShape(HiUniRadii.card)
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            Column {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(HiUniRadii.tile))
                        .background(colors.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = HiUniColors.semantics.onSurfaceMuted
                )
            }
            if (badge != null && badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .widthIn(min = 18.dp)
                        .height(18.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badge.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun TodaySection(lessons: List<TodayLesson>) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column {
        SectionLabel(text = "Heute", trailing = "Alle anzeigen")
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
private fun FilmTeaserSection(films: List<FilmTeaser>) {
    val semantics = HiUniColors.semantics
    Column {
        SectionLabel(text = "Uni Kino", trailing = "Programm")
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(films) { film ->
                Column(modifier = Modifier.width(160.dp)) {
                    Surface(
                        color = film.color,
                        shape = RoundedCornerShape(HiUniRadii.card),
                        modifier = Modifier
                            .width(160.dp)
                            .height(200.dp)
                    ) {
                        Box(modifier = Modifier.padding(14.dp)) {
                            Surface(
                                color = Color.White.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = film.genre,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.align(Alignment.BottomStart)
                            ) {
                                Text(
                                    text = film.title,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = film.date,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = film.timeRoom,
                        style = MaterialTheme.typography.labelMedium,
                        color = semantics.onSurfaceMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenTodosSection(todos: List<TodoPreview>) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column {
        SectionLabel(text = "Offene Aufgaben", trailing = "Alle")
        Spacer(Modifier.height(10.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(HiUniRadii.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                todos.forEachIndexed { index, todo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(colors.surface)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(colors.surface),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(colors.surface)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = todo.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (todo.due != null) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = todo.due,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = todo.dueAccent ?: semantics.onSurfaceMuted
                                )
                            }
                        }
                    }
                    if (index < todos.lastIndex) {
                        HorizontalDivider(color = colors.outline.copy(alpha = 0.5f))
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
                                imageVector = Icons.Outlined.Article,
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

@Composable
private fun SectionLabel(text: String, trailing: String? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                modifier = Modifier.clickable { }
            )
        }
    }
}
