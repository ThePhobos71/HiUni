package de.transio.hiuni.feature.movies.ui

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.HiltViewModelFactory
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import de.transio.hiuni.core.common.DateTimeFormats
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.movies.MovieDetailViewModel
import de.transio.hiuni.feature.movies.MoviesViewModel
import de.transio.hiuni.feature.movies.data.MovieEntity
import de.transio.hiuni.feature.movies.data.isSurpriseScreening
import de.transio.hiuni.ui.responsive.FullWidthContent
import de.transio.hiuni.ui.responsive.LocalWindowSizeClass
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(
    onOpenMovie: (filmId: String, sessionId: String) -> Unit = { _, _ -> },
    viewModel: MoviesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme
    val isExpanded =
        LocalWindowSizeClass.current?.widthSizeClass == WindowWidthSizeClass.Expanded

    // Snackbar NUR bei vorhandenem Stale-Cache — bei leerem Cache übernimmt der
    // ErrorState (in MovieListContent), sonst käme der Fehler doppelt.
    LaunchedEffect(state.errorMessage, state.hasContent) {
        if (state.hasContent) {
            state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    if (isExpanded) {
        // Tablet-Multi-Pane: Liste links, Detail rechts. Selection bewusst NICHT
        // auto-initiieren — der Empty-State im Detail-Pane lädt den User aktiv
        // ein, einen Film zu wählen, und vermeidet "Lade…"-Flackern beim Tab-
        // Wechsel auf den Movies-Tab.
        FullWidthContent {
            Scaffold(
                containerColor = colors.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                MoviesTwoPane(
                    state = state,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }
        return
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
                MovieListContent(
                    state = state,
                    onRetry = viewModel::refresh,
                    onOpen = { m -> onOpenMovie(m.filmId, m.sessionId) }
                )
            }
        }
    }
}

/**
 * Tablet-Layout: Links Liste (~40%, min 380dp), rechts Detail (~60%, min 480dp),
 * dazwischen 1dp VerticalDivider. Selection ist via [rememberSaveable]
 * persistiert, damit ein Config-Change den ausgewählten Film nicht verliert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoviesTwoPane(
    state: de.transio.hiuni.feature.movies.MoviesUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var selectedFilmId: String? by rememberSaveable { mutableStateOf(null) }
    var selectedSessionId: String? by rememberSaveable { mutableStateOf(null) }

    // Wenn die Liste sich ändert und die aktuelle Selection nicht mehr existiert
    // (Film verschwand vom Server), Selection zurücksetzen — sonst zeigt der
    // Detail-Pane "Film nicht gefunden" mit einem Back-Button der nichts macht.
    val movies = state.movies
    val selectionValid by remember(movies, selectedFilmId, selectedSessionId) {
        derivedStateOf {
            selectedFilmId != null && selectedSessionId != null &&
                movies.any { it.filmId == selectedFilmId && it.sessionId == selectedSessionId }
        }
    }
    LaunchedEffect(selectionValid) {
        if (!selectionValid && selectedFilmId != null) {
            selectedFilmId = null
            selectedSessionId = null
        }
    }

    Row(modifier = modifier) {
        Column(
            modifier = Modifier
                .widthIn(min = 380.dp)
                .weight(0.4f)
                .fillMaxHeight()
        ) {
            MoviesHeader()
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                MovieListContent(
                    state = state,
                    onRetry = onRefresh,
                    selectedFilmId = selectedFilmId,
                    selectedSessionId = selectedSessionId,
                    onOpen = { m ->
                        selectedFilmId = m.filmId
                        selectedSessionId = m.sessionId
                    }
                )
            }
        }
        VerticalDivider(
            color = colors.outline.copy(alpha = 0.2f),
            thickness = 1.dp
        )
        Box(
            modifier = Modifier
                .widthIn(min = 480.dp)
                .weight(0.6f)
                .fillMaxHeight()
        ) {
            val filmId = selectedFilmId
            val sessionId = selectedSessionId
            if (filmId == null || sessionId == null) {
                MovieDetailEmptyPane()
            } else {
                MovieDetailEmbedded(filmId = filmId, sessionId = sessionId)
            }
        }
    }
}

@Composable
private fun MovieDetailEmptyPane() {
    val semantics = HiUniColors.semantics
    de.transio.hiuni.core.design.components.EmptyState(
        icon = Icons.Outlined.Movie,
        iconAccent = semantics.onSurfaceMuted,
        iconSurface = semantics.surfaceAlt,
        title = "Wähle einen Film",
        body = "Tippe links auf einen Film, um Trailer, Cast und Beschreibung zu sehen."
    )
}

/**
 * Detail-Renderer für den rechten Pane. Erzeugt pro `filmId+sessionId` einen
 * eigenen [MovieDetailViewModel], indem die Args über
 * [DEFAULT_ARGS_KEY] in eine neue SavedStateHandle injiziert werden — so
 * bleibt der ViewModel unverändert (er liest weiterhin via
 * `SavedStateHandle["filmId"]`) und Hilt liefert die Repository/TMDB-
 * Abhängigkeiten wie gewohnt.
 *
 * Der zusammengesetzte `key` triggert beim Wechsel der Selection einen
 * frischen VM-Lifecycle (`init { load() }`), inklusive TMDB-Refetch.
 */
@Composable
private fun MovieDetailEmbedded(filmId: String, sessionId: String) {
    val owner = checkNotNull(LocalViewModelStoreOwner.current) {
        "Kein ViewModelStoreOwner im Composition-Scope verfügbar"
    }
    val savedStateOwner = LocalSavedStateRegistryOwner.current
    val context = LocalContext.current
    val defaultProviderFactory =
        (owner as? HasDefaultViewModelProviderFactory)?.defaultViewModelProviderFactory
    val defaultExtras =
        (owner as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
    // CreationExtras enthält DEFAULT_ARGS_KEY → SavedStateHandleSupport macht daraus
    // die SavedStateHandle, die MovieDetailViewModel als ctor-Arg bekommt.
    val extras = remember(filmId, sessionId, owner, savedStateOwner) {
        MutableCreationExtras(defaultExtras ?: CreationExtras.Empty).apply {
            set(
                DEFAULT_ARGS_KEY,
                Bundle().apply {
                    putString("filmId", filmId)
                    putString("sessionId", sessionId)
                }
            )
            set(SAVED_STATE_REGISTRY_OWNER_KEY, savedStateOwner)
            set(VIEW_MODEL_STORE_OWNER_KEY, owner)
        }
    }
    val factory = remember(context, defaultProviderFactory) {
        defaultProviderFactory?.let { HiltViewModelFactory(context, it) }
    }
    val viewModel: MovieDetailViewModel = viewModel(
        viewModelStoreOwner = owner,
        key = "movie-detail-$filmId-$sessionId",
        factory = factory,
        extras = extras
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    MovieDetailBody(
        state = state,
        showBack = false,
        onBack = { /* unused im Multi-Pane */ },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun MoviesHeader() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
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

/**
 * Wählt zwischen ErrorState / Skeleton / [MovieList] nach dem Design-Kit-Muster
 * (siehe KDoc auf [de.transio.hiuni.core.design.components.ErrorState]).
 */
@Composable
private fun MovieListContent(
    state: de.transio.hiuni.feature.movies.MoviesUiState,
    onRetry: () -> Unit,
    onOpen: (MovieEntity) -> Unit,
    selectedFilmId: String? = null,
    selectedSessionId: String? = null
) {
    val semantics = HiUniColors.semantics
    when {
        // 1. Netzfehler UND kein Cache → Vollbild-ErrorState mit Retry.
        state.errorMessage != null && !state.hasContent -> {
            de.transio.hiuni.core.design.components.ErrorState(
                iconSurface = semantics.redSurface,
                iconAccent = semantics.red,
                title = "Filme nicht geladen",
                body = state.errorMessage,
                secondaryBody = "Prüfe deine Verbindung und versuch es erneut.",
                onRetry = onRetry
            )
        }
        // 2. Erster Load ohne Cache → Skeleton statt leerem Screen.
        state.isLoading && !state.hasContent -> {
            de.transio.hiuni.core.design.components.HiUniSkeletonList(
                modifier = Modifier.fillMaxSize(),
                showCircle = true
            )
        }
        // 3. Normalfall (inkl. „leer aber geladen" via MovieList-EmptyState).
        else -> {
            MovieList(
                movies = state.movies,
                onOpen = onOpen,
                selectedFilmId = selectedFilmId,
                selectedSessionId = selectedSessionId
            )
        }
    }
}

@Composable
private fun MovieList(
    movies: List<MovieEntity>,
    onOpen: (MovieEntity) -> Unit,
    selectedFilmId: String? = null,
    selectedSessionId: String? = null
) {
    val semantics = HiUniColors.semantics
    if (movies.isEmpty()) {
        de.transio.hiuni.core.design.components.EmptyState(
            icon = Icons.Outlined.Movie,
            iconAccent = semantics.onSurfaceMuted,
            containerColor = semantics.surfaceAlt,
            body = "Aktuell sind keine Filme verfügbar. Pull-to-Refresh versuchen."
        )
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
            item {
                val isSelected = featured.filmId == selectedFilmId &&
                    featured.sessionId == selectedSessionId
                FeaturedMovieCard(
                    movie = featured,
                    isSelected = isSelected,
                    onClick = { onOpen(featured) }
                )
            }
        }
        if (rest.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                SectionMiniLabel("NÄCHSTE VORSTELLUNGEN")
            }
            items(rest, key = { it.filmId + "-" + it.sessionId }) { movie ->
                val isSelected = movie.filmId == selectedFilmId &&
                    movie.sessionId == selectedSessionId
                MovieRow(
                    movie = movie,
                    isSelected = isSelected,
                    onClick = { onOpen(movie) }
                )
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
private fun FeaturedMovieCard(
    movie: MovieEntity,
    onClick: () -> Unit,
    isSelected: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val containerColor = if (isSelected) colors.primaryContainer else colors.surface
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(HiUniRadii.big),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 2.dp),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                onClick(label = "Film öffnen", action = null)
            }
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
private fun MovieRow(
    movie: MovieEntity,
    onClick: () -> Unit,
    isSelected: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val containerColor = if (isSelected) colors.primaryContainer else colors.surface
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(HiUniRadii.card),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                onClick(label = "Film öffnen", action = null)
            }
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
                    contentDescription = movie.displayTitle(),
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
    date?.let { add(it.format(DateTimeFormats.dayShort)) }
    time?.let { add(it.format(DateTimeFormats.time24) + " Uhr") }
    location?.let { add(it) }
    durationMinutes?.takeIf { it > 0 }?.let { add("$it Min") }
}.joinToString(" · ")

internal fun MovieEntity.displayTitle(): String =
    if (isSurpriseScreening()) "Überraschungsfilm" else title

internal fun MovieEntity.displaySubtitle(): String? =
    if (isSurpriseScreening()) "Wird am Abend bekannt gegeben" else subtitle
