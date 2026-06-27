package de.transio.hiuni.core.design

/**
 * User-Override für das App-Theme. Default `SYSTEM` folgt dem OS-Setting,
 * `LIGHT`/`DARK` erzwingen unabhängig vom System ein Theme.
 */
enum class ThemeMode(val storageKey: String, val displayLabel: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Hell"),
    DARK("dark", "Dunkel");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == key } ?: SYSTEM
    }
}
