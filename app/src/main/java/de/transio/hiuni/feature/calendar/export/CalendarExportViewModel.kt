package de.transio.hiuni.feature.calendar.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.transio.hiuni.di.IoDispatcher
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.lsf.data.LsfExamsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class CalendarExportUiState(
    val categories: Set<IcsExportCategory> = IcsExportCategory.DEFAULT,
    val range: IcsExportRange = IcsExportRange.SEMESTER,
    val isWorking: Boolean = false,
    /** Hinweis im Sheet, z.B. wenn im Zeitraum nichts liegt. */
    val message: String? = null
)

sealed interface CalendarExportAction {
    /** Datei liegt im Cache und ist per FileProvider freigegeben. */
    data class Share(val uri: Uri, val fileName: String) : CalendarExportAction

    /** UI soll ACTION_CREATE_DOCUMENT mit diesem Vorschlag öffnen. */
    data class PickSaveTarget(val fileName: String) : CalendarExportAction
}

/**
 * Steuert das Export-Sheet: Auswahl halten, Export über [IcsExportPlanner] bauen,
 * Datei für das Teilen in den Cache schreiben bzw. in ein vom User gewähltes
 * Dokument speichern. Reiner Export ohne Sync (ADR-0006): nichts wird
 * zurückgelesen oder abgeglichen.
 *
 * Eigenes ViewModel statt Erweiterung des [de.transio.hiuni.feature.calendar.CalendarViewModel],
 * damit der Kalender selbst unangetastet bleibt.
 */
@HiltViewModel
class CalendarExportViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val examsRepository: LsfExamsRepository,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarExportUiState())
    val state: StateFlow<CalendarExportUiState> = _state.asStateFlow()

    private val _actions = Channel<CalendarExportAction>(Channel.BUFFERED)
    val actions: Flow<CalendarExportAction> = _actions.receiveAsFlow()

    /** Fertiger Inhalt zwischen „Speichern" und der Dokument-Auswahl des Users. */
    private var pendingSave: String? = null

    fun toggleCategory(category: IcsExportCategory) {
        _state.update { s ->
            val next = if (category in s.categories) s.categories - category else s.categories + category
            s.copy(categories = next, message = null)
        }
    }

    fun selectRange(range: IcsExportRange) {
        _state.update { it.copy(range = range, message = null) }
    }

    fun share() = runExport { ready ->
        val file = withContext(io) {
            val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
            // Alte Exporte wegräumen, der Cache soll nicht volllaufen.
            dir.listFiles()?.forEach { it.delete() }
            File(dir, ready.fileName).apply { writeText(ready.ics, Charsets.UTF_8) }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + PROVIDER_SUFFIX, file)
        _actions.send(CalendarExportAction.Share(uri, ready.fileName))
    }

    fun save() = runExport { ready ->
        pendingSave = ready.ics
        _actions.send(CalendarExportAction.PickSaveTarget(ready.fileName))
    }

    /** Ergebnis von ACTION_CREATE_DOCUMENT; `null` = User hat abgebrochen. */
    fun writeToDocument(uri: Uri?) {
        val content = pendingSave ?: return
        pendingSave = null
        if (uri == null) return
        viewModelScope.launch {
            val ok = withContext(io) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("Kein OutputStream für $uri")
                }.onFailure { Timber.w(it, "ICS speichern fehlgeschlagen") }.isSuccess
            }
            _state.update {
                it.copy(message = if (ok) "Gespeichert." else "Speichern hat nicht geklappt.")
            }
        }
    }

    private fun runExport(onReady: suspend (IcsExportResult.Ready) -> Unit) {
        if (_state.value.isWorking) return
        val snapshot = _state.value
        if (snapshot.categories.isEmpty()) {
            _state.update { it.copy(message = "Bitte mindestens eine Kategorie wählen.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = null) }
            try {
                val result = withContext(io) {
                    val events = calendarRepository.observeAll().first()
                    val exams = if (IcsExportCategory.EXAMS in snapshot.categories) {
                        examsRepository.observeAll().first()
                    } else {
                        emptyList()
                    }
                    IcsExportPlanner.build(
                        events = events,
                        exams = exams,
                        categories = snapshot.categories,
                        range = snapshot.range,
                        today = LocalDate.now(IcsExporter.ZONE),
                        now = Instant.now()
                    )
                }
                when (result) {
                    IcsExportResult.Empty -> _state.update {
                        it.copy(message = "Im gewählten Zeitraum gibt es keine passenden Termine.")
                    }
                    is IcsExportResult.Ready -> onReady(result)
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Timber.w(t, "ICS-Export fehlgeschlagen")
                _state.update { it.copy(message = "Export hat nicht geklappt.") }
            } finally {
                _state.update { it.copy(isWorking = false) }
            }
        }
    }

    private companion object {
        /** Unterordner im cacheDir; über `cache-path` in res/xml/file_paths.xml freigegeben. */
        const val EXPORT_DIR = "ics_export"
        const val PROVIDER_SUFFIX = ".provider"
    }
}
