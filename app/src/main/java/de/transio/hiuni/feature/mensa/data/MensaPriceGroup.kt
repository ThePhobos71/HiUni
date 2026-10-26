package de.transio.hiuni.feature.mensa.data

import de.transio.hiuni.core.auth.UserProfile

/**
 * Preisgruppen des Studentenwerks OstNiedersachsen. Die STW-API liefert pro Gericht
 * `student`, `employee` und `guest`; welcher Preis angezeigt wird, hängt an dieser Gruppe.
 *
 * @property label Anzeigename in Einstellungen und Preiszeilen.
 * @property priceCaption Beschriftung der großen Preis-Kachel im Gerichtsdetail.
 */
enum class PriceGroup(val label: String, val priceCaption: String) {
    STUDENT("Studierende", "Studi-Preis"),
    EMPLOYEE("Bedienstete", "Bedienstetenpreis"),
    GUEST("Gäste", "Gästepreis")
}

/**
 * Gespeicherte Wahl aus den Mensa-Einstellungen: entweder [AUTO] (Gruppe aus dem
 * Uni-Login ableiten) oder eine feste Gruppe als Override.
 */
enum class PriceGroupPreference(val storageKey: String, val fixedGroup: PriceGroup?) {
    AUTO("auto", null),
    STUDENT("student", PriceGroup.STUDENT),
    EMPLOYEE("employee", PriceGroup.EMPLOYEE),
    GUEST("guest", PriceGroup.GUEST);

    companion object {
        /** Unbekannte oder leere Keys landen bei [AUTO], damit alte Installationen nichts verlieren. */
        fun fromKey(key: String?): PriceGroupPreference =
            entries.firstOrNull { it.storageKey == key } ?: AUTO
    }
}

/** Woran die automatische Erkennung die Gruppe festgemacht hat. */
enum class PriceGroupSource(val reason: String) {
    AFFILIATION("Status laut Uni-Login"),
    MATRIKEL("Matrikelnummer im Uni-Login vorhanden"),
    GRADES("Notenspiegel im LSF vorhanden"),
    LOGIN_WITHOUT_MATRIKEL("Uni-Login ohne Matrikelnummer und ohne Notenspiegel"),
    DEFAULT("Ohne Uni-Login gilt der Studierendenpreis")
}

data class PriceGroupDetection(val group: PriceGroup, val source: PriceGroupSource)

/** Ein konkreter Preis samt der Gruppe, aus der er tatsächlich stammt (wichtig beim Rückfall). */
data class MealPrice(val cents: Int, val group: PriceGroup) {
    val label: String get() = formatPriceCents(cents)
}

object PriceGroupRules {

    private val STUDENT_AFFILIATIONS = setOf("student")
    private val EMPLOYEE_AFFILIATIONS = setOf("employee", "staff", "faculty")
    private val GUEST_AFFILIATIONS = setOf("affiliate", "library-walk-in")

    /**
     * Leitet die Preisgruppe aus dem ab, was der Uni-Login hergibt. Die CAS-Attributseite
     * der Uni Hildesheim liefert heute uid, Namen, Mail und `mtknr`; eine eduPerson-
     * Affiliation wird mitgelesen, falls CAS sie irgendwann ausliefert.
     *
     * Reihenfolge (erste Regel gewinnt):
     *  1. Affiliation `student` → Studierende (auch bei zusätzlichem `employee`,
     *     studentische Hilfskräfte zahlen den Studierendenpreis).
     *  2. Matrikelnummer vorhanden → Studierende.
     *  3. Notenspiegel aus dem LSF vorhanden → Studierende (nur Immatrikulierte haben einen).
     *  4. Affiliation `employee`/`staff`/`faculty` → Bedienstete.
     *  5. Affiliation `affiliate`/`library-walk-in` → Gäste.
     *  6. Eingeloggt (uid bekannt), aber weder Matrikelnummer noch Noten → Bedienstete.
     *     CAS zeigt `mtknr` für jedes studentische Konto; fehlt sie, ist es ein
     *     Beschäftigten-Konto.
     *  7. Sonst (nicht eingeloggt, Profil leer) → Studierende als Standard, weil die
     *     App sich in erster Linie an Studierende richtet.
     */
    fun detect(profile: UserProfile, hasGrades: Boolean): PriceGroupDetection {
        val affiliations = profile.affiliations
            .map { it.substringBefore('@').trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        return when {
            affiliations.any { it in STUDENT_AFFILIATIONS } ->
                PriceGroupDetection(PriceGroup.STUDENT, PriceGroupSource.AFFILIATION)
            !profile.matrikel.isNullOrBlank() ->
                PriceGroupDetection(PriceGroup.STUDENT, PriceGroupSource.MATRIKEL)
            hasGrades ->
                PriceGroupDetection(PriceGroup.STUDENT, PriceGroupSource.GRADES)
            affiliations.any { it in EMPLOYEE_AFFILIATIONS } ->
                PriceGroupDetection(PriceGroup.EMPLOYEE, PriceGroupSource.AFFILIATION)
            affiliations.any { it in GUEST_AFFILIATIONS } ->
                PriceGroupDetection(PriceGroup.GUEST, PriceGroupSource.AFFILIATION)
            !profile.uid.isNullOrBlank() ->
                PriceGroupDetection(PriceGroup.EMPLOYEE, PriceGroupSource.LOGIN_WITHOUT_MATRIKEL)
            else ->
                PriceGroupDetection(PriceGroup.STUDENT, PriceGroupSource.DEFAULT)
        }
    }

    /** Fester Override aus den Einstellungen schlägt die Erkennung, [PriceGroupPreference.AUTO] übernimmt sie. */
    fun effective(preference: PriceGroupPreference, detected: PriceGroup): PriceGroup =
        preference.fixedGroup ?: detected

    /** Beschriftung der Automatik-Option, z.B. „Automatisch (aus Uni-Login: Studierende)". */
    fun autoLabel(detection: PriceGroupDetection): String =
        if (detection.source == PriceGroupSource.DEFAULT) {
            "Automatisch (kein Uni-Login: ${detection.group.label})"
        } else {
            "Automatisch (aus Uni-Login: ${detection.group.label})"
        }
}

private fun MealEntity.ownPriceCents(group: PriceGroup): Int? = when (group) {
    PriceGroup.STUDENT -> priceStudentCents
    PriceGroup.EMPLOYEE -> priceEmployeeCents
    PriceGroup.GUEST -> priceGuestCents
}

/**
 * Preis für [group]. Fehlt der Gruppenpreis (STW liefert bei manchen Beilagen nur den
 * Studierendenpreis), fällt die Anzeige auf den Studierendenpreis zurück; [MealPrice.group]
 * verrät dann, woher der Wert stammt. Ohne Studierendenpreis gibt es keinen Rückfall.
 */
fun MealEntity.priceFor(group: PriceGroup): MealPrice? {
    ownPriceCents(group)?.let { return MealPrice(it, group) }
    return priceStudentCents?.let { MealPrice(it, PriceGroup.STUDENT) }
}

/** Formatierter Preis für [group] (mit Rückfall, siehe [priceFor]); leer, wenn es keinen gibt. */
fun MealEntity.priceLabelFor(group: PriceGroup): String = priceFor(group)?.label.orEmpty()

/** Preise der übrigen Gruppen, die einen eigenen Preis haben (für die Zusatzzeile im Detail). */
fun MealEntity.otherPrices(group: PriceGroup): List<MealPrice> =
    PriceGroup.entries
        .filter { it != group }
        .mapNotNull { other -> ownPriceCents(other)?.let { MealPrice(it, other) } }

internal fun formatPriceCents(cents: Int): String = "%.2f €".format(cents / 100.0)
