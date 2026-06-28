package de.transio.hiuni.feature.mensa.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ApiDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

@Serializable
data class MensaMenuResponse(
    @SerialName("meals") val meals: List<MensaMealApi> = emptyList()
)

@Serializable
data class MensaMealApi(
    @SerialName("id") val id: Long? = null,
    @SerialName("date") val date: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("name_en") val nameEn: String? = null,
    @SerialName("price") val price: MensaPriceApi? = null,
    @SerialName("lane") val lane: MensaLaneApi? = null,
    @SerialName("time") val time: String? = null,
    @SerialName("tags") val tags: MensaTagsApi? = null,
    @SerialName("location") val location: MensaLocationApi? = null,
    @SerialName("nutritional_values") val nutritionalValues: MensaNutritionApi? = null,
    /**
     * Historisch hieß das Feld `special_tags` auf Meal-Top-Level und enthielt
     * `MensaTagItem`-Objekte. Seit Sommer 2026 ist es deprecated und liefert
     * nur noch `List<String>` mit einem Deprecation-Hinweis ("Deprecated.
     * Use tags→special"). Wir parsen es als List<String>, ignorieren den
     * Inhalt, und lesen die echten Daten jetzt aus [MensaTagsApi.special].
     */
    @SerialName("special_tags") val specialTagsDeprecated: List<String> = emptyList()
)

@Serializable
data class MensaNutritionApi(
    @SerialName("per_100_grams") val per100g: Map<String, String> = emptyMap()
)

@Serializable
data class MensaLocationApi(
    @SerialName("id") val id: Int? = null,
    @SerialName("opening_hours") val openingHours: List<MensaOpeningHourApi> = emptyList()
)

@Serializable
data class MensaOpeningHourApi(
    @SerialName("time") val time: String? = null,
    @SerialName("start_day") val startDay: Int? = null,
    @SerialName("end_day") val endDay: Int? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null
)

@Serializable
data class MensaPriceApi(
    @SerialName("student") val student: String? = null,
    @SerialName("employee") val employee: String? = null,
    @SerialName("guest") val guest: String? = null
)

@Serializable
data class MensaLaneApi(
    @SerialName("id") val id: Int? = null,
    @SerialName("name") val name: String? = null
)

@Serializable
data class MensaTagsApi(
    @SerialName("categories") val categories: List<MensaTagItem> = emptyList(),
    @SerialName("allergens") val allergens: List<MensaTagItem> = emptyList(),
    @SerialName("additives") val additives: List<MensaTagItem> = emptyList(),
    @SerialName("special") val special: List<MensaTagItem> = emptyList()
)

@Serializable
data class MensaTagItem(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null
)

internal fun MensaMealApi.parsedDate(): LocalDate? =
    date?.let { runCatching { LocalDate.parse(it, ApiDateFormatter) }.getOrNull() }

internal fun MensaMealApi.toEntity(locationId: Int, fallbackKey: String): MealEntity? {
    val parsedDate = parsedDate() ?: return null
    val cleanName = name?.trim().orEmpty()
    if (cleanName.isBlank()) return null

    // STW-ON API quirk: closure notices and operational updates ("Vom X bis Y bleibt die
    // Abendmensa geschlossen.") arrive inside the meals array but carry no prices.
    // Drop them here — they get classified separately via [toAnnouncement].
    val studentCents = price?.student?.toCents()
    val employeeCents = price?.employee?.toCents()
    val guestCents = price?.guest?.toCents()
    if (studentCents == null && employeeCents == null && guestCents == null) return null

    // Category derived from time (noon/evening) + lane (Essen 1, Essen 2 …).
    val mealtime = when (time?.lowercase()) {
        "evening" -> "Abend"
        "morning" -> "Frühstück"
        else -> "Mittag"
    }
    val laneLabel = lane?.name?.trim().orEmpty()
    val category = when {
        laneLabel.isBlank() -> mealtime
        time?.equals("noon", ignoreCase = true) == true -> laneLabel
        else -> "$mealtime · $laneLabel"
    }

    val dietTags = tags?.categories?.mapNotNull { it.name?.trim()?.takeIf { name -> name.isNotBlank() } }
        .orEmpty()
    // Allergen IDs mapped to German short labels and prefixed with `*` so the UI can
    // distinguish them from dietary tags (`*` = allergen pill, no prefix = dietary).
    val allergenTags = tags?.allergens
        ?.mapNotNull { it.id?.trim()?.takeIf { id -> id.isNotBlank() } }
        ?.map { "*" + AllergenLabels.shortName(it) }
        .orEmpty()
    val joinedTags = (dietTags + allergenTags).distinct().joinToString(",")

    // Additives + special_tags als Klartext-Namen — kein ID-Mapping, weil die API
    // beides bereits ausformuliert liefert ("mit Konservierungsstoff").
    val additiveNames = tags?.additives
        ?.mapNotNull { it.name?.trim()?.takeIf { n -> n.isNotBlank() } }
        ?.distinct()
        .orEmpty()
        .joinToString(",")
    val specialTagNames = specialTags
        .mapNotNull { it.name?.trim()?.takeIf { n -> n.isNotBlank() } }
        .distinct()
        .joinToString(",")

    // Nutritional Values: kompakt als JSON-Map persistieren — Keys variieren je
    // nach Gericht (manche haben kein `roughage`, manche kein `salt`), daher
    // kein festes Schema mit Spalten pro Nährwert. Nur befüllen wenn nicht leer.
    val nutritionJson = nutritionalValues?.per100g
        ?.takeIf { it.isNotEmpty() }
        ?.let { map ->
            // Manuelles JSON-Encoding für Map<String, String> — vermeidet
            // Generic-Reflection-Fummel mit Json.encodeToString<Map<...>>.
            kotlinx.serialization.json.buildJsonObject {
                map.forEach { (k, v) ->
                    put(k, kotlinx.serialization.json.JsonPrimitive(v))
                }
            }.toString()
        }

    return MealEntity(
        sourceId = id?.toString() ?: fallbackKey,
        locationId = locationId,
        date = parsedDate,
        category = category,
        name = cleanName,
        description = null,
        priceStudentCents = studentCents,
        priceEmployeeCents = employeeCents,
        priceGuestCents = guestCents,
        tags = joinedTags,
        co2Grams = null,
        nameEn = nameEn?.trim()?.takeIf { it.isNotBlank() },
        nutritionalValuesJson = nutritionJson,
        additives = additiveNames,
        specialTags = specialTagNames
    )
}

private fun String.toCents(): Int? = trim()
    .replace(',', '.')
    .takeIf { it.isNotBlank() && it != "0.00" }
    ?.toDoubleOrNull()
    ?.let { (it * 100).toInt() }

/** Konvertiert den API-DTO in das Domain-Modell, drop wenn essentielle Felder fehlen. */
internal fun MensaOpeningHourApi.toDomain(): OpeningHourBlock? {
    val time = time?.lowercase() ?: return null
    val startDay = startDay ?: return null
    val endDay = endDay ?: return null
    val start = runCatching { java.time.LocalTime.parse(startTime) }.getOrNull() ?: return null
    val end = runCatching { java.time.LocalTime.parse(endTime) }.getOrNull() ?: return null
    return OpeningHourBlock(
        time = time,
        startDay = startDay,
        endDay = endDay,
        startTime = start,
        endTime = end
    )
}

/**
 * Classify the meal payload as an [Announcement] when prices are missing/zero — that's how
 * STW-ON ships closure notices through the meals array.
 */
internal fun MensaMealApi.toAnnouncement(): Announcement? {
    val parsedDate = parsedDate() ?: return null
    val cleanName = name?.trim().orEmpty()
    if (cleanName.isBlank()) return null
    val anyPrice = listOfNotNull(price?.student, price?.employee, price?.guest)
        .any { it.toCents() != null }
    if (anyPrice) return null
    val timeBucket = when (time?.lowercase()) {
        "evening" -> AnnouncementTime.EVENING
        "morning" -> AnnouncementTime.MORNING
        else -> AnnouncementTime.NOON
    }
    return Announcement(
        date = parsedDate,
        text = cleanName,
        time = timeBucket,
        lane = lane?.name?.trim()?.takeIf { it.isNotBlank() }
    )
}

enum class AnnouncementTime { MORNING, NOON, EVENING }

data class Announcement(
    val date: java.time.LocalDate,
    val text: String,
    val time: AnnouncementTime,
    val lane: String?
)
