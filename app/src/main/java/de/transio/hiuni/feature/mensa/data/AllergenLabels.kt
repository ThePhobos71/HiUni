package de.transio.hiuni.feature.mensa.data

/**
 * STW-ON allergen codes mapped to short German labels.
 * Full list from https://sls.api.stw-on.de/ — used in tags.allergens[].id.
 */
object AllergenLabels {

    private val LABELS: Map<String, String> = mapOf(
        // Glutenhaltige Getreide
        "GL"  to "Gluten",
        "GL1" to "Weizen",
        "GL2" to "Roggen",
        "GL3" to "Gerste",
        "GL4" to "Hafer",
        "GL5" to "Dinkel",
        "GL6" to "Kamut",

        // Hauptallergene
        "SO" to "Soja",
        "ML" to "Milch",
        "EI" to "Ei",
        "FI" to "Fisch",
        "SC" to "Krebstiere",
        "NU" to "Nüsse",
        "ER" to "Erdnüsse",
        "SE" to "Sellerie",
        "SN" to "Senf",
        "SA" to "Sesam",
        "LU" to "Lupinen",
        "WT" to "Weichtiere",
        "SU" to "Sulfite",

        // Nuss-Untergruppen (selten verwendet)
        "NU1" to "Mandeln",
        "NU2" to "Haselnüsse",
        "NU3" to "Walnüsse",
        "NU4" to "Cashew",
        "NU5" to "Pekan",
        "NU6" to "Paranüsse",
        "NU7" to "Pistazien",
        "NU8" to "Macadamia"
    )

    fun shortName(id: String): String = LABELS[id.uppercase()] ?: id

    /** True if the given tag label (after stripping the `*` prefix) refers to a known allergen. */
    fun isKnownAllergen(label: String): Boolean = LABELS.values.contains(label)
}
