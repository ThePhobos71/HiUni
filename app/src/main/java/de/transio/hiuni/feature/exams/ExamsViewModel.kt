package de.transio.hiuni.feature.exams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.common.LoadStatus
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.network.ConnectivityObserver
import de.transio.hiuni.core.sync.LsfSyncScheduler
import de.transio.hiuni.feature.courses.data.CourseRepository
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.lsf.data.LsfExamsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class ExamsViewModel @Inject constructor(
    private val repository: LsfExamsRepository,
    private val lsfSyncScheduler: LsfSyncScheduler,
    connectivity: ConnectivityObserver,
    settings: SettingsDataStore,
    courseRepository: CourseRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _editing = MutableStateFlow<ExamEntity?>(null)

    private val connectivityStateFlow = combine(
        connectivity.isOnline,
        settings.lastLsfExamsRefreshEpoch
    ) { online, epoch -> online to epoch }

    val state: StateFlow<ExamsUiState> = combine(
        repository.observeAll(),
        _isRefreshing,
        courseRepository.observeAll(),
        _editing,
        connectivityStateFlow
    ) { exams, refreshing, courses, editing, conn ->
        // Defensiv: das DAO sortiert bereits, aber wir wollen pro UI-Refactor
        // garantieren, dass „mit Datum aufsteigend, dann ohne Datum ans Ende"
        // stabil bleibt — auch wenn jemand später das DAO-Query anpasst.
        val sorted = exams.sortedWith(
            compareBy(
                { it.examDate == null },
                { it.examDate }
            )
        )
        ExamsUiState(
            exams = sorted,
            load = LoadStatus(isRefreshing = refreshing),
            courses = courses,
            editing = editing,
            isOnline = conn.first,
            lastRefreshEpoch = conn.second
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(60_000),
        ExamsUiState(load = LoadStatus.loading())
    )

    /**
     * Pull-to-Refresh / Empty-State-Button: triggert den LSF-Sync (der die
     * Klausur-Refresh in seiner Phase mitnimmt). Worker liefert kein synchrones
     * Completion-Signal — Indicator bleibt 3 s sichtbar, danach kommt der
     * eigentliche Datenfluss über `observeAll()` automatisch nach.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            lsfSyncScheduler.triggerNow()
            delay(3000L)
            _isRefreshing.value = false
        }
    }

    // ── Manuelles Klausur-Eintragen ─────────────────────────────────────────

    /** Öffnet das Sheet für eine neue manuelle Klausur (leerer Entwurf, rowId == 0). */
    fun startAdd() {
        _editing.update { blankManualExam() }
    }

    /** Öffnet das Sheet zum Bearbeiten — NUR für manuelle Einträge sinnvoll. */
    fun startEdit(exam: ExamEntity) {
        if (!exam.isManual) return
        _editing.update { exam }
    }

    fun dismissSheet() {
        _editing.update { null }
    }

    /** Speichert den (neuen oder bearbeiteten) manuellen Eintrag und schließt das Sheet. */
    fun save(exam: ExamEntity) {
        viewModelScope.launch {
            repository.saveManual(exam)
            dismissSheet()
        }
    }

    /** Löscht einen manuellen Eintrag und schließt das Sheet. */
    fun delete(exam: ExamEntity) {
        if (!exam.isManual) return
        viewModelScope.launch {
            repository.deleteManual(exam.rowId)
            dismissSheet()
        }
    }

    private fun blankManualExam(): ExamEntity = ExamEntity(
        rowId = 0L,
        veranstaltungsNumber = "",
        pruefungstext = "",
        moduleName = "",
        parentModule = null,
        examDate = null,
        examTime = null,
        rooms = emptyList(),
        semester = "",
        semesterCode = MANUAL_SEMESTER_CODE,
        registrationDate = null,
        cancellationDeadline = null,
        pruefer = null,
        courseId = null,
        lsfPublishId = null,
        fetchedAt = Instant.now(),
        source = ExamEntity.SOURCE_MANUAL
    )

    companion object {
        /**
         * Fixer Semester-Code-Bucket für manuelle Einträge. Sie brauchen keinen
         * echten LSF-Semester-Code (der Prune filtert ohnehin auf source='LSF'),
         * aber ein nicht-leerer, LSF-fremder Wert hält sie sauber vom
         * Semester-basierten LSF-Prune getrennt.
         */
        const val MANUAL_SEMESTER_CODE = "MANUAL"
    }
}
