package de.transio.hiuni.core.common

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Wiederverwendbare DateTimeFormatter-Konstanten. Zentralisiert ~30 dupliziert verstreute
 * `DateTimeFormatter.ofPattern(...)`-Aufrufe und macht Format-Anpassungen single-source.
 *
 * Naming-Konvention:
 *   - `day*`   → Datum + Wochentag
 *   - `date*`  → Datum ohne Wochentag
 *   - `time24` → reine Uhrzeit
 *   - `dateTime*` → Datum + Uhrzeit kombiniert
 *   - `iso*`   → ISO-/Maschinen-Formate (kein Locale)
 */
object DateTimeFormats {
    /** "Mo, 7. Apr" — kompakte Listen-Zeile */
    val dayShort: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)

    /** "Mo 7. Apr" — ohne Komma, für sehr enge Layouts */
    val dayShortNoComma: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d. MMM", Locale.GERMAN)

    /** "Mo 7. Apr 2026" — kompakt mit Jahr */
    val dayShortWithYear: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d. MMM yyyy", Locale.GERMAN)

    /** "Montag, 7. April" — Hero-Datum ohne Jahr */
    val dayFull: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

    /** "Montag, 7. April 2026" — vollständiges Hero-Datum mit Jahr */
    val dayFullWithYear: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)

    /** "7. Apr" — Datum ohne Wochentag, ohne Jahr */
    val dateShort: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)

    /** "7. Apr 2026" — Datum ohne Wochentag, mit Jahr */
    val dateWithYear: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)

    /** "April 2026" — Monat und Jahr (Calendar-Header) */
    val monthYear: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)

    /** "Mo" — nur Wochentag (3-Letter-Abk.) */
    val weekdayShort: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE", Locale.GERMAN)

    /** "14:30" — 24h, German Locale */
    val time24: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

    /** "Mo, 7. Apr · 14:30" — kompakte Date+Time-Zeile */
    val dayShortWithTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, d. MMM · HH:mm", Locale.GERMAN)

    /** "7. Apr 2026, 14:30" — Datum mit Jahr + Uhrzeit */
    val dateTimeWithYear: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d. MMM yyyy, HH:mm", Locale.GERMAN)
}
