package de.transio.hiuni.feature.movies.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.movies.MoviesViewModel
import de.transio.hiuni.feature.movies.data.MovieEntity
import de.transio.hiuni.feature.movies.data.isSurpriseScreening
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(
    onOpenMovie: (filmId: String, sessionId: String) -> Unit = { _, _ -> },
    viewModel: MoviesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MoviesHeader()
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                MovieList(
                    movies = state.movies,
                    onOpen = { m -> onOpenMovie(m.filmId, m.sessionId) }
                )
            }
        }
    }
}

@Composable
private fun MoviesHeader() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Uni Kino",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onSurface
        )
    }
}

@Composable
private fun MovieList(
    movies: List<MovieEntity>,
    onOpen: (MovieEntity) -> Unit
) {
    val semantics = HiUniColors.semantics
    if (movies.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(
                color = semantics.surfaceAlt,
                shape = RoundedCornerShape(HiUniRadii.card),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Movie,
                        contentDescription = null,
                        tint = semantics.onSurfaceMuted
                    )
                    Text(
                        text = "Aktuell sind keine Filme verfügbar. Pull-to-Refresh versuchen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = semantics.onSurfaceMuted
                    )
                }
            }
        }
        return
    }

    val featured = movies.firstOrNull()
    val rest = movies.drop(1)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (featured != null) {
            val today = LocalDate.now()
            val featuredLabel = when {
                featured.date == today -> "HEUTE ABEND"
                featured.date == today.plusDays(1) -> "MORGEN"
                else -> "NÄCHSTER FILM"
            }
            item { SectionMiniLabel(featuredLabel) }
            item { FeaturedMovieCard(movie = featured, onClick = { onOpen(featured) }) }
        }
        if (rest.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                SectionMiniLabel("NÄCHSTE VORSTELLUNGEN")
            }
            items(rest, key = { it.filmId + "-" + it.sessionId }) { movie ->
                MovieRow(movie = movie, onClick = { onOpen(movie) })
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun SectionMiniLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = HiUniColors.semantics.onSurfaceMuted,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun FeaturedMovieCard(movie: MovieEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(HiUniRadii.big),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box {
                if (movie.posterUrl != null) {
                    AsyncImage(
                        model = movie.posterUrl,
                        contentDescription = movie.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .background(colors.primary.copy(alpha = 0.6f))
                    )
                }
                movie.genre?.let { genre ->
                    Surface(
                        color = Color.White.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = movie.displayTitle(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onSurface
                )
                movie.displaySubtitle()?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = semantics.onSurfaceMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = movie.metaLine(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary
                )
                movie.description?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = semantics.onSurfaceMuted,
                        maxLines = 6
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieRow(movie: MovieEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(HiUniRadii.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (movie.posterUrl != null) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(64.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(HiUniRadii.tile)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(HiUniRadii.tile))
                        .background(semantics.surfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Movie,
                        contentDescription = null,
                        tint = semantics.onSurfaceMuted
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                val genreLabel = if (movie.isSurpriseScreening()) "ÜBERRASCHUNG" else movie.genre
                genreLabel?.let { genre ->
                    Text(
                        text = genre.uppercase(Locale.GERMAN),
                        style = MaterialTheme.typography.labelSmall,
                        color = semantics.onSurfaceMuted
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = movie.displayTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = movie.metaLine(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary
                )
            }
        }
    }
}

private fun MovieEntity.metaLine(): String = buildList {
    date?.let { add(it.format(dateFmt)) }
    time?.let { add(it.format(timeFmt) + " Uhr") }
    location?.let { add(it) }
    durationMinutes?.takeIf { it > 0 }?.let { add("$it Min") }
}.joinToString(" · ")

internal fun MovieEntity.displayTitle(): String =
    if (isSurpriseScreening()) "Überraschungsfilm" else title

internal fun MovieEntity.displaySubtitle(): String? =
    if (isSurpriseScreening()) "Wird am Abend bekannt gegeben" else subtitle
