package de.transio.hiuni.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS meals (
                sourceId TEXT NOT NULL,
                locationId INTEGER NOT NULL,
                date INTEGER NOT NULL,
                category TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                priceStudentCents INTEGER,
                priceEmployeeCents INTEGER,
                priceGuestCents INTEGER,
                tags TEXT NOT NULL,
                co2Grams INTEGER,
                PRIMARY KEY(sourceId, locationId)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_meals_date_locationId ON meals(date, locationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_meals_locationId ON meals(locationId)")
    }
}
