package de.transio.hiuni.core.search

import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.courses.data.CourseRepository
import de.transio.hiuni.feature.email.EmailFolder
import de.transio.hiuni.feature.email.data.EmailEntity
import de.transio.hiuni.feature.email.data.EmailRepository
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.lsf.data.LsfExamsRepository
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.feature.mensa.data.MensaRepository
import de.transio.hiuni.feature.sport.data.SportEventEntity
import de.transio.hiuni.feature.sport.data.SportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resultat einer App-weiten Suche, gebündelt nach Inhaltstyp. Jede Kategorie ist
 * pre-capped auf max [GlobalSearchRepository.MAX_PER_CATEGORY] Treffer — der
 * Aufrufer (UI) entscheidet nicht selbst über Cut-off, damit Tablet- und Phone-
 * Layout identisch begrenzt sind.
 *
 * `isEmpty` ignoriert den Query-State bewusst — es spiegelt nur, ob etwas
 * gefunden wurde. Der „Tippe um zu suchen"-vs-„Keine Treffer"-State lebt im
 * ViewModel.
 */
data class GlobalSearchResults(
    val emails: List<EmailEntity> = emptyList(),
    val events: List<CustomEventEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val exams: List<ExamEntity> = emptyList(),
    val mensaMeals: List<MealEntity> = emptyList(),
    val sportEvents: List<SportEventEntity> = emptyList()
) {
    val totalCount: Int
        get() = emails.size + events.size + courses.size + exams.size +
            mensaMeals.size + sportEvents.size

    val isEmpty: Boolean get() = totalCount == 0

    companion object {
        val Empty = GlobalSearchResults()
    }
}

/**
 * Aggregator-Repository für die Spotlight-Suche. Sammelt pro-Feature-Streams
 * über `combine` ein, kappt pro Kategorie auf [MAX_PER_CATEGORY] und gibt ein
 * Snapshot-Bündel zurück. Bei leerem Query fallen alle Quellen auf
 * [GlobalSearchResults.Empty] zurück — wir vermeiden so unnötige DB-Roundtrips,
 * wenn der User die Suche nur geöffnet, aber noch nichts getippt hat.
 *
 * AND-Token-Match: jeder Whitespace-getrennte Token muss in mindestens einem
 * der definierten Felder des Items vorkommen (case-insensitive). Damit fühlen
 * sich kombinierte Eingaben wie „logik klausur" intuitiv an.
 */
interface GlobalSearchRepository {
    fun search(query: String): Flow<GlobalSearchResults>

    companion object {
        const val MAX_PER_CATEGORY = 5
    }
}

@Singleton
class GlobalSearchRepositoryImpl @Inject constructor(
    private val emailRepo: EmailRepository,
    private val calendarRepo: CalendarRepository,
    private val coursesRepo: CourseRepository,
    private val examsRepo: LsfExamsRepository,
    private val mensaRepo: MensaRepository,
    private val sportRepo: SportRepository
) : GlobalSearchRepository {

    override fun search(query: String): Flow<GlobalSearchResults> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) {
            // Leerer Query → wir wollen explizit nichts anzeigen. Würden wir hier
            // die Feature-Flows abonnieren, würde die UI für eine Microsekunde alle
            // Mails/Kurse als „Treffer" zeigen, bevor das ViewModel den nächsten
            // Tick gefiltert hat.
            return flowOf(GlobalSearchResults.Empty)
        }

        // Mail hat eine native, indexierte LIKE-Search — die nutzen wir direkt
        // statt das ganze Postfach in den Speicher zu ziehen.
        val emailsFlow = emailRepo.observeSearch(EmailFolder.INBOX, query)
            .map { list -> list.take(GlobalSearchRepository.MAX_PER_CATEGORY) }

        val eventsFlow = calendarRepo.observeAll().map { all ->
            all.asSequence()
                .filter { matchesEvent(it, tokens) }
                .sortedByDescending { it.startTime }
                .take(GlobalSearchRepository.MAX_PER_CATEGORY)
                .toList()
        }

        val coursesFlow = coursesRepo.observeAll().map { all ->
            all.asSequence()
                .filter { matchesCourse(it, tokens) }
                .sortedBy { it.name.lowercase() }
                .take(GlobalSearchRepository.MAX_PER_CATEGORY)
                .toList()
        }

        // Exams: alle Einträge (kommendes + vergangenes Semester) durchsuchen —
        // der User erwartet, dass „statistik" auch alte Klausuren findet.
        val examsFlow = examsRepo.observeAll().map { all ->
            all.asSequence()
                .filter { matchesExam(it, tokens) }
                .sortedByDescending { it.examDate?.toEpochDay() ?: Long.MIN_VALUE }
                .take(GlobalSearchRepository.MAX_PER_CATEGORY)
                .toList()
        }

        val mensaFlow = mensaRepo.observeSearchWindow().map { all ->
            all.asSequence()
                .filter { matchesMeal(it, tokens) }
                .sortedBy { it.date }
                .take(GlobalSearchRepository.MAX_PER_CATEGORY)
                .toList()
        }

        val sportFlow = sportRepo.observeUpcoming().map { all ->
            all.asSequence()
                .filter { matchesSport(it, tokens) }
                .sortedBy { it.startTime }
                .take(GlobalSearchRepository.MAX_PER_CATEGORY)
                .toList()
        }

        // 5er-combine ist die letzte typisierte Overload — den 6. Stream (Sport)
        // bündeln wir vorher mit Mensa zu einem Pair, damit die Outer-Combine
        // wieder in den 5er-Slot passt. Alternativ wäre `combine(vararg)` mit
        // Array<Any?>-Cast möglich, das verliert aber die Statictypes.
        val mealsAndSportsFlow = combine(mensaFlow, sportFlow) { meals, sports -> meals to sports }
        return combine(
            emailsFlow,
            eventsFlow,
            coursesFlow,
            examsFlow,
            mealsAndSportsFlow
        ) { emails, events, courses, exams, mealsAndSports ->
            val (meals, sports) = mealsAndSports
            GlobalSearchResults(
                emails = emails,
                events = events,
                courses = courses,
                exams = exams,
                mensaMeals = meals,
                sportEvents = sports
            )
        }
    }

    private fun tokenize(query: String): List<String> =
        query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

    /** Jeder Token muss im Heuhaufen-String enthalten sein (AND-Semantik). */
    private fun matches(haystack: String, tokens: List<String>): Boolean =
        tokens.all { it in haystack }

    private fun matchesEvent(event: CustomEventEntity, tokens: List<String>): Boolean {
        val hay = buildString {
            append(event.title.lowercase()).append(' ')
            event.description?.let { append(it.lowercase()).append(' ') }
            event.location?.let { append(it.lowercase()) }
        }
        return matches(hay, tokens)
    }

    private fun matchesCourse(course: CourseEntity, tokens: List<String>): Boolean {
        val hay = buildString {
            append(course.name.lowercase()).append(' ')
            course.description?.let { append(it.lowercase()).append(' ') }
            course.lsfCode?.let { append(it.lowercase()).append(' ') }
            course.moduleAbbreviation?.let { append(it.lowercase()).append(' ') }
            append(course.professor.lowercase())
        }
        return matches(hay, tokens)
    }

    private fun matchesExam(exam: ExamEntity, tokens: List<String>): Boolean {
        val hay = buildString {
            append(exam.pruefungstext.lowercase()).append(' ')
            append(exam.moduleName.lowercase()).append(' ')
            append(exam.veranstaltungsNumber.lowercase()).append(' ')
            exam.parentModule?.let { append(it.lowercase()) }
        }
        return matches(hay, tokens)
    }

    private fun matchesMeal(meal: MealEntity, tokens: List<String>): Boolean {
        val hay = buildString {
            append(meal.name.lowercase()).append(' ')
            append(meal.category.lowercase()).append(' ')
            meal.description?.let { append(it.lowercase()) }
        }
        return matches(hay, tokens)
    }

    private fun matchesSport(slot: SportEventEntity, tokens: List<String>): Boolean {
        val hay = buildString {
            append(slot.title.lowercase()).append(' ')
            slot.description?.let { append(it.lowercase()).append(' ') }
            slot.location?.let { append(it.lowercase()) }
        }
        return matches(hay, tokens)
    }
}
