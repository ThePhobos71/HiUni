package de.transio.hiuni.feature.exams.studyplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.courses.data.CourseRepository
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.lsf.data.LsfExamsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Steuert das Lernplan-Sheet auf dem Klausuren-Screen: Auswahl der Klausuren,
 * Einstellungen (persistiert im [StudyPlanSettingsStore]), Live-Vorschau über den
 * [StudyPlanner] und das Übernehmen/Löschen über das [StudyPlanRepository].
 *
 * Die Vorschau wird nur berechnet, solange das Sheet offen ist. Beim Übernehmen
 * wird der Plan mit frisch gelesener Belegung noch einmal berechnet, damit ein in
 * der Zwischenzeit angelegter Termin nicht überplant wird.
 */
@HiltViewModel
class StudyPlanViewModel @Inject constructor(
    examsRepository: LsfExamsRepository,
    courseRepository: CourseRepository,
    private val repository: StudyPlanRepository,
    private val settingsStore: StudyPlanSettingsStore,
    private val clock: Clock
) : ViewModel() {

    private val zone: ZoneId get() = clock.zone

    private val _isOpen = MutableStateFlow(false)

    /** Explizite Auswahl; `null` = Vorauswahl (Klausuren der nächsten sechs Wochen). */
    private val _selection = MutableStateFlow<Set<Long>?>(null)

    /** Eigenes Startdatum aus dem Datepicker. Wird bewusst nicht gespeichert. */
    private val _customStart = MutableStateFlow<LocalDate?>(null)
    private val _preview = MutableStateFlow<StudyPlan?>(null)
    private val _isApplying = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    private val upcomingExams: StateFlow<List<StudyPlanExam>> = combine(
        examsRepository.observeAll(),
        courseRepository.observeAll()
    ) { exams, courses -> toPlannerExams(exams, courses) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val activeBlocks = repository.observeUpcomingBlocks(clock.instant())
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private data class Inputs(
        val isOpen: Boolean,
        val exams: List<StudyPlanExam>,
        val settings: StudyPlanSettings,
        val selection: Set<Long>,
        val customStart: LocalDate?
    ) {
        val selectedExams: List<StudyPlanExam> get() = exams.filter { it.examId in selection }
    }

    private val inputs: StateFlow<Inputs> = combine(
        _isOpen,
        upcomingExams,
        settingsStore.settings,
        _selection,
        _customStart
    ) { open, exams, settings, selection, customStart ->
        Inputs(open, exams, settings, selection ?: defaultSelection(exams), customStart)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        Inputs(false, emptyList(), StudyPlanSettings(), emptySet(), null)
    )

    val state: StateFlow<StudyPlanUiState> = combine(
        inputs,
        _preview,
        activeBlocks,
        _isApplying,
        _message
    ) { input, preview, active, applying, message ->
        StudyPlanUiState(
            isOpen = input.isOpen,
            settings = input.settings,
            candidates = input.exams.map { StudyPlanCandidate(it, it.examId in input.selection) },
            startDate = startDateFor(input),
            start = when {
                input.customStart != null -> StudyPlanStart.CUSTOM
                input.settings.startOffsetDays <= 0 -> StudyPlanStart.TODAY
                else -> StudyPlanStart.TOMORROW
            },
            preview = preview,
            activeBlockCount = active.size,
            activeUntil = active.maxOfOrNull { it.startTime }?.atZone(zone)?.toLocalDate(),
            isApplying = applying,
            message = message
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StudyPlanUiState())

    init {
        viewModelScope.launch {
            inputs.collectLatest { input ->
                // Alte Vorschau bleibt sichtbar, bis die neue fertig ist (kein Flackern).
                _preview.value = if (input.isOpen) compute(input) else null
            }
        }
    }

    /** Öffnet das Sheet. Mit [preselectExamId] ist nur diese Klausur vorausgewählt. */
    fun open(preselectExamId: Long? = null) {
        _selection.value = preselectExamId?.let { setOf(it) }
        _customStart.value = null
        _isOpen.value = true
    }

    fun dismiss() {
        _isOpen.value = false
    }

    fun toggleExam(examId: Long) {
        val current = _selection.value ?: defaultSelection(upcomingExams.value)
        _selection.value = if (examId in current) current - examId else current + examId
    }

    /** Heute/Morgen werden als Vorliebe gespeichert, ein eigenes Datum gilt nur für diesen Plan. */
    fun setStart(start: StudyPlanStart, customDate: LocalDate? = null) {
        when (start) {
            StudyPlanStart.CUSTOM -> _customStart.value = customDate ?: return
            StudyPlanStart.TODAY, StudyPlanStart.TOMORROW -> {
                _customStart.value = null
                val offset = if (start == StudyPlanStart.TODAY) 0 else 1
                updateSettings { it.copy(startOffsetDays = offset) }
            }
        }
    }

    fun updateSettings(transform: (StudyPlanSettings) -> StudyPlanSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    /** Berechnet den Plan mit aktueller Belegung neu und trägt ihn in den Kalender ein. */
    fun apply() {
        if (_isApplying.value) return
        viewModelScope.launch {
            _isApplying.value = true
            try {
                val input = inputs.value
                val plan = compute(input)
                _preview.value = plan
                if (plan.blocks.isEmpty()) return@launch
                val written = repository.replaceFutureBlocks(
                    blocks = plan.blocks,
                    reminderMinutesBefore = input.settings.reminderMinutesBefore,
                    now = clock.instant(),
                    zone = zone
                )
                _message.value = "${unitsLabel(written)} eingetragen"
                _isOpen.value = false
            } finally {
                _isApplying.value = false
            }
        }
    }

    /** Entfernt alle noch nicht begonnenen Lernblöcke aus dem Kalender. */
    fun deletePlan() {
        viewModelScope.launch {
            val removed = repository.deleteFutureBlocks(clock.instant())
            _message.value = if (removed == 0) {
                "Keine geplanten Lerneinheiten vorhanden"
            } else {
                "${unitsLabel(removed)} gelöscht"
            }
            _isOpen.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ── Interna ──────────────────────────────────────────────────────────────

    private suspend fun compute(input: Inputs): StudyPlan {
        val now = clock.instant()
        val startDate = startDateFor(input)
        val selected = input.selectedExams
        if (selected.isEmpty()) {
            return StudyPlanner.plan(emptyList(), emptyList(), input.settings, startDate, now, zone)
        }
        val today = LocalDate.now(clock)
        val from = maxOf(startDate, today).atStartOfDay(zone).toInstant()
        val to = selected.maxOf { it.examDate }.plusDays(1).atStartOfDay(zone).toInstant()
        val calendarBusy = if (from.isBefore(to)) repository.busyIntervals(from, to, now) else emptyList()
        // Nicht ausgewählte Klausuren belegen trotzdem ihre Zeit.
        val otherExams = input.exams
            .filter { it.examId !in input.selection }
            .map { StudyPlanner.examBusyInterval(it, zone) }
        return StudyPlanner.plan(selected, calendarBusy + otherExams, input.settings, startDate, now, zone)
    }

    private fun startDateFor(input: Inputs): LocalDate =
        input.customStart ?: LocalDate.now(clock).plusDays(input.settings.startOffsetDays.coerceAtLeast(0).toLong())

    private fun defaultSelection(exams: List<StudyPlanExam>): Set<Long> {
        val horizon = LocalDate.now(clock).plusDays(StudyPlanSettings.DEFAULT_HORIZON_DAYS)
        return exams.filter { !it.examDate.isAfter(horizon) }.map { it.examId }.toSet()
    }

    private fun toPlannerExams(exams: List<ExamEntity>, courses: List<CourseEntity>): List<StudyPlanExam> {
        val now = clock.instant()
        val byId = courses.associateBy { it.id }
        val byCode = courses.filter { !it.lsfCode.isNullOrBlank() }.associateBy { it.lsfCode }
        return exams.mapNotNull { exam ->
            val date = exam.examDate ?: return@mapNotNull null
            val start = date.atTime(exam.examTime ?: DEFAULT_EXAM_TIME)
            if (!start.atZone(zone).toInstant().isAfter(now)) return@mapNotNull null
            val course = exam.courseId?.let { byId[it] } ?: byCode[exam.veranstaltungsNumber]
            StudyPlanExam(
                examId = exam.rowId,
                title = exam.moduleName.ifBlank { exam.pruefungstext }.trim(),
                examStart = start,
                credits = course?.credits?.takeIf { it > 0 }
            )
        }.sortedWith(compareBy<StudyPlanExam>({ it.examStart }, { it.examId }))
    }

    companion object {
        /** Annahme, wenn LSF noch keine Uhrzeit kennt: Klausur am Vormittag. */
        val DEFAULT_EXAM_TIME: LocalTime = LocalTime.of(8, 0)

        fun unitsLabel(count: Int): String =
            if (count == 1) "1 Lerneinheit" else "$count Lerneinheiten"
    }
}
