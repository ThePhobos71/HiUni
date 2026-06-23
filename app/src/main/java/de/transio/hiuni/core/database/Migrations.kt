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

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS mensa_card_transactions (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                uid TEXT NOT NULL,
                balanceMilliEuro INTEGER NOT NULL,
                deltaMilliEuro INTEGER NOT NULL,
                scannedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_mensa_card_transactions_uid_scannedAt " +
                "ON mensa_card_transactions(uid, scannedAt)"
        )
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        runCatching {
            db.execSQL(
                "ALTER TABLE mensa_card_transactions " +
                    "ADD COLUMN cardLastDebitMilliEuro INTEGER"
            )
        }
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // LSF-Kurs-Import: source unterscheidet USER/LSF, lsfId/room/lsfStatus
        // sind nur bei automatisch importierten Kursen gesetzt.
        runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN source TEXT NOT NULL DEFAULT 'USER'") }
        runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN lsfId TEXT") }
        runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN room TEXT") }
        runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN lsfStatus TEXT") }
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SWS + Lerninhalte aus der LSF-Veranstaltungs-Detailseite.
        runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN sws INTEGER") }
        runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN description TEXT") }
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Kalender-Events können jetzt explizit zu einem LSF-Kurs gehören.
        runCatching { db.execSQL("ALTER TABLE custom_events ADD COLUMN courseLsfId TEXT") }
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Eigenständige Aufgaben-Feature. dueDate als EpochDay (LocalDate),
        // createdAt/completedAt als Millis (Instant).
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS todos (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                dueDate INTEGER,
                isDone INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                completedAt INTEGER,
                sortIndex INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_isDone_dueDate ON todos(isDone, dueDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_dueDate ON todos(dueDate)")
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Aufgaben können jetzt optional einem Kurs zugeordnet werden.
        runCatching { db.execSQL("ALTER TABLE todos ADD COLUMN courseId TEXT") }
        db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_courseId ON todos(courseId)")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Veranstaltungsart + Verknüpfung Tutorium → Mutter-Vorlesung.
        runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN courseType TEXT") }
        runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN parentLsfId TEXT") }
        // Existierende LSF-Kurse müssen neu detail-gefetcht werden, damit
        // courseType + Parent-Mapping befüllt werden.
        runCatching {
            db.execSQL(
                "UPDATE courses SET credits = 0, description = NULL " +
                    "WHERE source = 'LSF'"
            )
        }
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Weitere Felder aus der LSF-Detailseite: Bemerkung, Zielgruppe, Modulkürzel.
        runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN remark TEXT") }
        runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN targetAudience TEXT") }
        runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN moduleAbbreviation TEXT") }
        // Vorhandene LSF-Kurse haben die neuen Felder noch nicht — wir resetten
        // credits + description als "noch nie detail-gefetcht"-Marker, sodass der
        // nächste Sync alle Veranstaltungen einmal komplett nachzieht.
        runCatching {
            db.execSQL(
                "UPDATE courses SET credits = 0, description = NULL " +
                    "WHERE source = 'LSF'"
            )
        }
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
