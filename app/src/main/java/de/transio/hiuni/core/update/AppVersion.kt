package de.transio.hiuni.core.update

/**
 * Semantische App-Version `major.minor.patch`, wie sie Release-Tags (`v1.4.2`) und
 * `versionName` tragen. [code] folgt derselben Formel wie die Release-Pipeline in
 * `app/build.gradle.kts`: `major * 10000 + minor * 100 + patch`. Deshalb sind minor
 * und patch auf zwei Stellen begrenzt, sonst wäre der Code nicht mehr eindeutig.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    val code: Int get() = major * 10_000 + minor * 100 + patch

    val name: String get() = "$major.$minor.$patch"

    override fun compareTo(other: AppVersion): Int = code.compareTo(other.code)

    companion object {
        // Optionales v-Präfix, genau drei Zahlen, optionaler Build-Suffix (`-debug`).
        private val PATTERN = Regex("""^[vV]?(\d{1,3})\.(\d{1,2})\.(\d{1,2})(?:[-+].*)?$""")

        /** Liest `v1.4.2`, `1.4.2` oder `1.4.2-debug`; alles andere ergibt `null`. */
        fun parse(raw: String): AppVersion? {
            val match = PATTERN.matchEntire(raw.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            return AppVersion(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}
