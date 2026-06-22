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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS movies (
                rowId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                filmId TEXT NOT NULL,
                sessionId TEXT NOT NULL,
                title TEXT NOT NULL,
                subtitle TEXT,
                description TEXT,
                date INTEGER,
                time INTEGER,
                location TEXT,
                posterUrl TEXT,
                trailerUrl TEXT,
                director TEXT,
                country TEXT,
                genre TEXT,
                durationMinutes INTEGER,
                fsk TEXT,
                isPast INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_movies_date ON movies(date)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_movies_filmId_sessionId ON movies(filmId, sessionId)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE movies ADD COLUMN posterSlug TEXT")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE movies ADD COLUMN awards TEXT")
        db.execSQL("ALTER TABLE movies ADD COLUMN nominations TEXT")
        db.execSQL("ALTER TABLE movies ADD COLUMN specialInfo TEXT")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE movies ADD COLUMN languageVersion TEXT")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS emails (
                rowId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                uid INTEGER NOT NULL,
                folder TEXT NOT NULL,
                fromAddress TEXT NOT NULL,
                fromName TEXT,
                subject TEXT NOT NULL,
                snippet TEXT NOT NULL,
                bodyPlain TEXT,
                receivedAt INTEGER NOT NULL,
                isRead INTEGER NOT NULL DEFAULT 0,
                isStarred INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_emails_folder_receivedAt ON emails(folder, receivedAt)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_emails_folder_uid ON emails(folder, uid)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Mails brauchten zwei zusätzliche Spalten — HTML-Body und Attachment-Metadaten.
        // ALTER ist safe weil emails brandneu und höchstens leer ist.
        runCatching { db.execSQL("ALTER TABLE emails ADD COLUMN bodyHtml TEXT") }
        runCatching { db.execSQL("ALTER TABLE emails ADD COLUMN attachmentsJson TEXT") }
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        runCatching { db.execSQL("ALTER TABLE emails ADD COLUMN toAddresses TEXT") }
        runCatching { db.execSQL("ALTER TABLE emails ADD COLUMN ccAddresses TEXT") }
        runCatching { db.execSQL("ALTER TABLE emails ADD COLUMN bccAddresses TEXT") }
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        runCatching { db.execSQL("ALTER TABLE emails ADD COLUMN hasAttachments INTEGER NOT NULL DEFAULT 0") }
        runCatching { db.execSQL("ALTER TABLE emails ADD COLUMN hasCalendarInvite INTEGER NOT NULL DEFAULT 0") }
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS courses (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                professor TEXT NOT NULL,
                credits INTEGER NOT NULL,
                semester TEXT NOT NULL,
                nextExamDate INTEGER,
                attendedSessions INTEGER NOT NULL DEFAULT 0,
                totalSessions INTEGER NOT NULL DEFAULT 0,
                grade TEXT,
                notes TEXT,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
