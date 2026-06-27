package de.transio.hiuni.feature.movies.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import android.graphics.drawable.BitmapDrawable
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import de.transio.hiuni.core.common.DateTimeFormats
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.movies.MovieDetailUiState
import de.transio.hiuni.feature.movies.MovieDetailViewModel
import de.transio.hiuni.feature.movies.data.MovieEntity
import de.transio.hiuni.feature.movies.data.isSurpriseScreening
import java.util.Locale

// Mock-spezifische Title-Typografie (28sp, ExtraBold, lineHeight 1.1, leichtes negatives Letter-Spacing)
private val HeroTitleStyle = TextStyle(
    fontSize = 28.sp,
    lineHeight = 31.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.3).sp
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    onBack: () -> Unit,
    viewModel: MovieDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        MovieDetailBody(
            state = state,
            showBack = true,
            onBack = onBack,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

/**
 * Pure-UI Renderer für den Movie-Detail. Wird sowohl vom Standalone
 * [MovieDetailScreen] (Push-Navigation auf Compact/Medium) als auch vom
 * embedded Detail-Pane in `MoviesScreen` auf Expanded benutzt.
 *
 * `showBack=false` blendet den Zurück-Button im Hero aus — auf Tablet braucht
 * man ihn nicht, weil der Detail-Pane immer sichtbar bleibt.
 */
@Composable
internal fun MovieDetailBody(
    state: MovieDetailUiState,
    showBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        val movie = state.movie
        if (movie == null) {
            if (state.isLoading) {
                Text(
                    text = "Lade …",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                EmptyDetail(onBack = onBack)
            }
            return@Column
        }

        Hero(
            movie = movie,
            rating = state.rating,
            voteCount = state.voteCount,
            backdropUrl = state.backdropUrl ?: movie.posterUrl,
            onBack = onBack,
            showBack = showBack
        )
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            InfoStripe(movie = movie)
            if (!movie.specialInfo.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                SpecialInfoBanner(text = movie.specialInfo)
            }
            Spacer(Modifier.height(18.dp))
            Handlung(movie = movie)
            if (!movie.isSurpriseScreening()) {
                if (!movie.awards.isNullOrBlank() || !movie.nominations.isNullOrBlank()) {
                    Spacer(Modifier.height(20.dp))
                    Auszeichnungen(awards = movie.awards, nominations = movie.nominations)
                }
                Spacer(Modifier.height(20.dp))
                CastCrew(director = state.crewDirector, cast = state.cast)
                if (!movie.country.isNullOrBlank() || !movie.genre.isNullOrBlank()) {
                    Spacer(Modifier.height(20.dp))
                    MetaRow(country = movie.country, genre = movie.genre)
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(
    movie: MovieEntity,
    rating: Double?,
    voteCount: Int?,
    backdropUrl: String?,
    onBack: () -> Unit,
    showBack: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val isSurprise = movie.isSurpriseScreening()
    val heroImage = backdropUrl?.takeUnless { isSurprise }
    val heroDerived = rememberDominantColor(
        posterUrl = (heroImage ?: movie.posterUrl)?.takeUnless { isSurprise },
        fallback = movie.toneColor(colors.primary)
    )
    val accent = heroDerived
    val accentDeep = accent.darker(0.25f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clipToBounds()
            .background(accent)
    ) {
        // Backdrop dominiert das Hero, nur unten ein Color-Fade für Text-Kontrast
        if (heroImage != null) {
            AsyncImage(
                model = heroImage,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Starker Vertikal-Gradient: oben transparent, unten Accent-Color
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1.0f to accentDeep.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
        }
        // Decorative circles — Mock-Look, immer sichtbar
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 50.dp, y = (-50).dp)
                .size(160.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = 40.dp)
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.04f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Back-Button (im Multi-Pane-Modus ausgeblendet, Detail-Pane bleibt
            // immer sichtbar — Spacer hält das SpaceBetween-Layout konsistent).
            if (showBack) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.offset(x = (-12).dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Zurück",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Zurück", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Spacer(Modifier.size(0.dp))
            }

            // Bottom: Title block
            Column {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val genreOrFallback = if (isSurprise) "ÜBERRASCHUNG" else movie.genre
                    genreOrFallback?.let { Badge(text = it) }
                    movie.durationMinutes?.takeIf { it > 0 }?.let { Badge(text = "$it Min") }
                    movie.fsk?.let { Badge(text = it) }
                    movie.languageVersion?.let { Badge(text = it) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isSurprise) "Überraschungsfilm" else movie.title,
                    style = HeroTitleStyle,
                    color = Color.White
                )
                if (isSurprise) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Wird am Abend bekannt gegeben",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                } else if (rating != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = "%.1f".format(Locale.GERMAN, rating / 2.0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        if (voteCount != null) {
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = "($voteCount Bewertungen)",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(
        color = Color.White.copy(alpha = 0.22f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun InfoStripe(movie: MovieEntity) {
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(HiUniRadii.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            InfoCell(label = "DATUM", value = movie.date?.format(DateTimeFormats.dayShort) ?: "—")
            InfoCell(label = "ZEIT", value = movie.time?.format(DateTimeFormats.time24) ?: "—")
            InfoCell(label = "SAAL", value = movie.location ?: "—")
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = semantics.onSurfaceMuted
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = colors.onSurface
        )
    }
}

@Composable
private fun SpecialInfoBanner(text: String) {
    val semantics = HiUniColors.semantics
    Surface(
        color = semantics.amberSurface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = semantics.amber,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val semantics = HiUniColors.semantics
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = semantics.onSurfaceMuted
    )
}

@Composable
private fun Handlung(movie: MovieEntity) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    SectionHeader(text = "HANDLUNG")
    Spacer(Modifier.height(10.dp))
    val description = movie.description?.trim()
    if (description.isNullOrBlank()) {
        if (movie.isSurpriseScreening()) {
            Text(
                text = "Welcher Film an diesem Abend gezeigt wird, wird kurz vor Beginn bekannt gegeben.",
                style = MaterialTheme.typography.bodyMedium,
                color = semantics.onSurfaceMuted
            )
        } else {
            Text(
                text = "Keine Beschreibung verfügbar.",
                style = MaterialTheme.typography.bodyMedium,
                color = semantics.onSurfaceMuted
            )
        }
        return
    }
    val paragraphs = description.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
    paragraphs.forEachIndexed { idx, paragraph ->
        if (idx > 0) Spacer(Modifier.height(8.dp))
        Text(
            text = paragraph,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = if (idx == 0) colors.onSurface else semantics.onSurfaceMuted
        )
    }
}

@Composable
private fun Auszeichnungen(awards: String?, nominations: String?) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    SectionHeader(text = "AUSZEICHNUNGEN")
    Spacer(Modifier.height(10.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(HiUniRadii.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (!awards.isNullOrBlank()) {
                AwardRow(icon = Icons.Outlined.EmojiEvents, tint = semantics.amber, label = "Preise", value = awards)
                if (!nominations.isNullOrBlank()) {
                    HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                }
            }
            if (!nominations.isNullOrBlank()) {
                AwardRow(icon = Icons.Outlined.EmojiEvents, tint = colors.primary, label = "Nominierungen", value = nominations)
            }
        }
    }
}

@Composable
private fun AwardRow(icon: ImageVector, tint: Color, label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = semantics.onSurfaceMuted
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface
            )
        }
    }
}

@Composable
private fun CastCrew(director: String?, cast: List<String>) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val hasAny = !director.isNullOrBlank() || cast.isNotEmpty()
    SectionHeader(text = "CAST & CREW")
    Spacer(Modifier.height(10.dp))
    if (!hasAny) {
        Text(
            text = "Keine Cast-Informationen verfügbar.",
            style = MaterialTheme.typography.bodyMedium,
            color = semantics.onSurfaceMuted
        )
        return
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(HiUniRadii.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (!director.isNullOrBlank()) {
                CastRow(label = "Regie", value = director)
                if (cast.isNotEmpty()) {
                    HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                }
            }
            if (cast.isNotEmpty()) {
                CastRow(label = "Cast", value = cast.joinToString(", "))
            }
        }
    }
}

@Composable
private fun CastRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = semantics.onSurfaceMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}

@Composable
private fun MetaRow(country: String?, genre: String?) {
    val semantics = HiUniColors.semantics
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        country?.takeIf { it.isNotBlank() }?.let {
            Column {
                Text("LAND", style = MaterialTheme.typography.labelSmall, color = semantics.onSurfaceMuted)
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        genre?.takeIf { it.isNotBlank() }?.let {
            Column {
                Text("GENRE", style = MaterialTheme.typography.labelSmall, color = semantics.onSurfaceMuted)
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun EmptyDetail(onBack: () -> Unit) {
    val semantics = HiUniColors.semantics
    de.transio.hiuni.core.design.components.EmptyState(
        icon = Icons.Outlined.Movie,
        iconAccent = semantics.onSurfaceMuted,
        body = "Film nicht gefunden.",
        action = {
            TextButton(onClick = onBack) { Text("Zurück") }
        }
    )
}

/**
 * Fallback-Farbe deterministisch aus filmId+sessionId+date+time (während Palette lädt oder kein Poster).
 * Datum + Uhrzeit mischen mit, damit Überraschungsfilme an gleichen Tag aber unterschiedlicher
 * Vorstellung (z.B. 18:30 vs 20:30) verschiedene Farben bekommen.
 */
private fun MovieEntity.toneColor(@Suppress("UNUSED_PARAMETER") fallback: Color): Color {
    val dateMix = (date?.toEpochDay()?.toInt() ?: 0)
    val timeMix = (time?.toSecondOfDay() ?: 0)
    val hash = (filmId.hashCode() xor sessionId.hashCode() xor dateMix xor timeMix).toLong() and 0xFFFFFFFFL
    val hue = (hash % 360).toFloat()
    return Color.hsl(hue = hue, saturation = 0.55f, lightness = 0.30f)
}

private fun Color.darker(amount: Float): Color {
    fun mix(c: Float) = (c * (1f - amount)).coerceIn(0f, 1f)
    return Color(mix(red), mix(green), mix(blue), alpha)
}

/**
 * Lädt das TMDB-Poster via Coil + extrahiert die dominante kräftige Farbe per AndroidX-Palette.
 * Ergebnis wird auf Lightness ~30% normalisiert damit weißer Text drauf gut lesbar bleibt.
 */
@Composable
private fun rememberDominantColor(posterUrl: String?, fallback: Color): Color {
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
                color = Color(rgb).normalizeForHero()
            }
        }
    }
    return color
}

/** Sättigt die Farbe leicht ab und drückt die Helligkeit auf ~28% — damit weißer Text passt. */
private fun Color.normalizeForHero(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(0.35f, 0.7f)
    hsl[2] = hsl[2].coerceIn(0.22f, 0.34f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}
