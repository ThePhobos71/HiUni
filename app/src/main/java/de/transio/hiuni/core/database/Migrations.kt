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
                isRead INTEGER NOT NULL,
                isStarred INTEGER NOT NULL
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

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Klausurtermine aus LSF "Meine POS-Anmeldungen". Logischer Primärschlüssel ist
        // (veranstaltungsNumber, semesterCode) via Unique-Index — rowId ist Room-Autogenerate.
        // examTime ist Nanos-of-day, examDate/registrationDate/cancellationDeadline sind EpochDay,
        // rooms ist newline-joined TEXT (siehe Converters.stringListToString).
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exams (
                rowId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                veranstaltungsNumber TEXT NOT NULL,
                pruefungstext TEXT NOT NULL,
                moduleName TEXT NOT NULL,
                parentModule TEXT,
                examDate INTEGER,
                examTime INTEGER,
                rooms TEXT NOT NULL,
                semester TEXT NOT NULL,
                semesterCode TEXT NOT NULL,
                registrationDate INTEGER,
                cancellationDeadline INTEGER,
                pruefer TEXT,
                courseId TEXT,
                fetchedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exams_examDate ON exams(examDate)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_exams_veranstaltungsNumber_semesterCode " +
                "ON exams(veranstaltungsNumber, semesterCode)"
        )
    }
}

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // LSF publishid pro Klausur-Eintrag, falls die POS-Anmeldungs-Tabelle einen
        // direkten Veranstaltungs-Link liefert. Bevorzugte Match-Spalte gegen
        // courses.lsfId — Number-Prefix-Heuristik bleibt Fallback. Existing Rows
        // bleiben null bis der nächste Scrape die ID nachzieht.
        runCatching { db.execSQL("ALTER TABLE exams ADD COLUMN lsfPublishId TEXT") }
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // LSF-Veranstaltungs-Nummer (4–5-stellig, "5395") als eigenes Feld, damit
        // das Klausur→Kurs-Matching nicht mehr den Kursnamen parsen muss (LSF
        // formatiert Namen uneinheitlich, mal mit "(Code)"-Suffix, mal ohne).
        // Bestands-Rows kriegen den Code beim nächsten LsfMyCoursesRepository-Sync
        // nachgereicht.
        runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN lsfCode TEXT") }
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // RFC 5322 Message-ID + References-Header für Reply-Threading.
        // Beide nullable: Bestands-Mails haben den Header bisher nicht persistiert,
        // der nächste IMAP-Sync (force=true via Pull-to-Refresh) füllt sie nach.
        runCatching { db.execSQL("ALTER TABLE emails ADD COLUMN messageId TEXT") }
        runCatching { db.execSQL("ALTER TABLE emails ADD COLUMN referencesHeader TEXT") }
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Recurrence-Rule als JSON-String (RFC 5545 light). NULL = einmaliges Event.
        // Bestands-Events bleiben single-shot; neue Recurring-Events werden vom
        // CalendarRepository in-memory zu virtuellen Occurrences expandiert.
        runCatching { db.execSQL("ALTER TABLE custom_events ADD COLUMN recurrenceRule TEXT") }
    }
}

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Mensa-Pin-Feature wurde entfernt — die in custom_events noch liegenden
        // MENSA_PIN-Snapshots tauchten sonst weiter im Home-Banner als "nächste
        // Vorlesung" auf ("Afrikanischer Pfeffertopf mit Couscous · Mensa").
        // SOURCE_MENSA_PIN bleibt als Konstante stehen, falls man's mal wieder
        // einbauen will — Bestandsdaten sind aber weg.
        runCatching { db.execSQL("DELETE FROM custom_events WHERE sourceKind = 'MENSA_PIN'") }
    }
}

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Meal-Detail-Sheet: STW-API liefert deutlich mehr als bisher persistiert
        // wurde. Spalten für englische Übersetzung, Nährwerte (JSON-Map pro 100g),
        // Zusatzstoffe und Special-Tags. Bestandsmeals bleiben mit leeren Werten —
        // der nächste Refresh (Pull-to-Refresh oder Auto-Sync) füllt sie nach.
        runCatching { db.execSQL("ALTER TABLE meals ADD COLUMN nameEn TEXT") }
        runCatching { db.execSQL("ALTER TABLE meals ADD COLUMN nutritionalValuesJson TEXT") }
        runCatching { db.execSQL("ALTER TABLE meals ADD COLUMN additives TEXT NOT NULL DEFAULT ''") }
        runCatching { db.execSQL("ALTER TABLE meals ADD COLUMN specialTags TEXT NOT NULL DEFAULT ''") }
    }
}

val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Learnweb (Moodle) Assignment-Deadlines aus dem Dashboard-Calendar.
        // `eventId` ist die Moodle-Calendar-Event-ID (logischer Primärschlüssel
        // via Unique-Index), `rowId` Room-Autogenerate damit Upserts FK-stabil
        // bleiben. `dueEpoch` ist Millis; Zeitzone bleibt lokale Berlin-Zeit
        // (siehe LearnwebScraper.parseAssignments für Time-Extraction).
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS learnweb_assignments (
                rowId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                eventId INTEGER NOT NULL,
                title TEXT NOT NULL,
                dueEpoch INTEGER NOT NULL,
                url TEXT NOT NULL,
                syncedAt INTEGER NOT NULL,
                submissionStatus TEXT NOT NULL DEFAULT 'unknown',
                lastSubmittedEpoch INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_learnweb_assignments_eventId " +
                "ON learnweb_assignments(eventId)"
        )
    }
}

val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Learnweb (Moodle) eingeschriebene Kurse aus dem Dashboard-Scrape.
        // `courseId` ist der logische Schlüssel (Moodle-Course-ID), `rowId`
        // Room-Autogenerate damit REPLACE-Upserts FK-stabil bleiben (es gibt
        // aktuell keine FKs, aber Pattern matched die anderen Tables).
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS learnweb_courses (
                rowId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                courseId INTEGER NOT NULL,
                name TEXT NOT NULL,
                url TEXT NOT NULL,
                syncedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_learnweb_courses_courseId " +
                "ON learnweb_courses(courseId)"
        )
    }
}

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Lokales Soft-Delete-Flag: bei aktiviertem „nur lokal löschen"-Setting
        // setzen wir das Flag statt die Row zu löschen. Damit zieht der nächste
        // IMAP-Sync die Mail nicht wieder rein. DAO-Queries filtern auf
        // `isHiddenLocally = 0`.
        runCatching {
            db.execSQL("ALTER TABLE emails ADD COLUMN isHiddenLocally INTEGER NOT NULL DEFAULT 0")
        }
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Hochschulsport-Feature (supersaas-Scraping). `supersaasSlotId` ist
        // logischer Primärschlüssel via unique index; `rowId` ist Room-Autogenerate.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sport_events (
                rowId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                supersaasSlotId INTEGER NOT NULL,
                startTime INTEGER NOT NULL,
                endTime INTEGER NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                location TEXT,
                capacity INTEGER NOT NULL,
                currentBookings INTEGER NOT NULL,
                waitlistCount INTEGER NOT NULL,
                isCancelled INTEGER NOT NULL,
                isPaidOnly INTEGER NOT NULL,
                fetchedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sport_events_startTime ON sport_events(startTime)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_sport_events_supersaasSlotId " +
                "ON sport_events(supersaasSlotId)"
        )
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Push-Center: Log aller gefeuerten Benachrichtigungen. `kind` ist als
        // TEXT gespeichert (NotificationKind.name), `firedAt` als Instant-Millis,
        // `isRead` als 0/1.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notifications (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                kind TEXT NOT NULL,
                title TEXT NOT NULL,
                body TEXT,
                firedAt INTEGER NOT NULL,
                isRead INTEGER NOT NULL DEFAULT 0,
                refKey TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_notifications_isRead_firedAt " +
                "ON notifications(isRead, firedAt)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_notifications_firedAt " +
                "ON notifications(firedAt)"
        )
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

/**
 * Single source of truth — wird von [DatabaseModule.provideAppDatabase] via
 * `addMigrations(*ALL_MIGRATIONS)` durchgereicht. Reihenfolge nach `startVersion`
 * sortieren, damit jede neue Migration einfach ans Ende kommt.
 *
 * Wer eine `MIGRATION_X_Y`-Konstante anlegt, MUSS sie hier eintragen — sonst
 * schlägt `MigrationListTest` fehl, weil die Kette an der falschen Stelle endet.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
    MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
    MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
    MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24,
    MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27,
    MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30,
    MIGRATION_30_31, MIGRATION_31_32
)
