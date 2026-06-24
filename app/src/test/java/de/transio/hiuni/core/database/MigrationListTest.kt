package de.transio.hiuni.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hält die Migration-Liste mit der Datenbank-Version und der Migrations-Datei
 * synchron. Wir hatten in der Vergangenheit den Fall, dass eine Migration-Konstante
 * angelegt aber nicht in `addMigrations(…)` registriert wurde — der Build war grün,
 * Room hat das aber erst beim ersten DB-Open zur Laufzeit bemerkt und die App
 * gekillt. Dieser Test fängt beide Klassen von Fehler statisch ab.
 */
class MigrationListTest {

    @Test
    fun all_migrations_form_unbroken_chain_to_db_version() {
        // @Database hat BINARY-Retention — der `version`-Wert ist zur Test-Laufzeit
        // nicht reflektierbar. Wir lesen ihn statt dessen aus dem Source-File.
        val dbVersion = readDbVersionFromSource()
        val sorted = ALL_MIGRATIONS.sortedBy { it.startVersion }

        assertTrue("ALL_MIGRATIONS ist leer — DB-Version ist $dbVersion", sorted.isNotEmpty())
        assertEquals(
            "Erste Migration muss bei Version 1 starten",
            1,
            sorted.first().startVersion
        )

        var expectedStart = 1
        for (m in sorted) {
            assertEquals(
                "Lücke vor MIGRATION_${m.startVersion}_${m.endVersion}: " +
                    "erwartete Start-Version $expectedStart",
                expectedStart,
                m.startVersion
            )
            assertEquals(
                "MIGRATION_${m.startVersion}_${m.endVersion} darf nur eine Version " +
                    "überspringen (start+1 ≠ end)",
                m.startVersion + 1,
                m.endVersion
            )
            expectedStart = m.endVersion
        }
        assertEquals(
            "Migrations-Kette endet bei Version $expectedStart, AppDatabase ist aber bei $dbVersion " +
                "— neue MIGRATION_${expectedStart}_$dbVersion in ALL_MIGRATIONS eintragen",
            dbVersion,
            expectedStart
        )
    }

    @Test
    fun every_migration_constant_in_source_is_registered_in_ALL_MIGRATIONS() {
        // Wir lesen Migrations.kt als Text und matchen alle `val MIGRATION_X_Y = ...`-
        // Top-Level-Deklarationen. Anschließend prüfen wir per Reflection, dass jede
        // davon im ALL_MIGRATIONS-Array enthalten ist. So fällt eine vergessene Eintragung
        // sofort auf, statt erst beim Schema-Upgrade in Production.
        val sourceFile = File("src/main/java/de/transio/hiuni/core/database/Migrations.kt")
        assertTrue(
            "Migrations.kt nicht gefunden — Test muss aus dem :app-Modul laufen",
            sourceFile.exists()
        )
        val regex = Regex("""^val\s+(MIGRATION_\d+_\d+)\s*=""", RegexOption.MULTILINE)
        val declared = regex.findAll(sourceFile.readText())
            .map { it.groupValues[1] }
            .toSet()

        // Reflection: die top-level vals landen als statische Getter auf der
        // synthetischen Klasse `MigrationsKt`.
        val container = Class.forName("de.transio.hiuni.core.database.MigrationsKt")
        val registeredSet = ALL_MIGRATIONS.toHashSet()
        val missing = declared.filter { name ->
            val getter = container.getMethod("get$name")
            val migration = getter.invoke(null) as androidx.room.migration.Migration
            migration !in registeredSet
        }
        assertTrue(
            "Folgende Migration-Konstanten existieren in Migrations.kt, sind aber nicht in " +
                "ALL_MIGRATIONS eingetragen: $missing",
            missing.isEmpty()
        )
    }

    private fun readDbVersionFromSource(): Int {
        val file = File("src/main/java/de/transio/hiuni/core/database/AppDatabase.kt")
        assertTrue("AppDatabase.kt nicht gefunden", file.exists())
        val match = Regex("""version\s*=\s*(\d+)""").find(file.readText())
        assertTrue("Konnte version-Feld in AppDatabase.kt nicht parsen", match != null)
        return match!!.groupValues[1].toInt()
    }
}
