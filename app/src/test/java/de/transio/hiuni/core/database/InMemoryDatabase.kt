package de.transio.hiuni.core.database

import androidx.room.Room
import org.robolectric.RuntimeEnvironment

/**
 * Baut eine frische In-Memory-[AppDatabase] auf Framework-SQLite (ohne SQLCipher).
 * Wir bauen die abstrakte Room-Klasse hier direkt — nicht über [DatabaseModule] /
 * SupportFactory — weil SQLCipher unter Robolectric keine native Lib hat und die
 * DAO-Query-Semantik ohnehin identisch ist.
 *
 * `allowMainThreadQueries` ist in Tests unbedenklich: `runTest` läuft die
 * suspend-DAOs auf dem Test-Dispatcher, und die synchronen Reads brauchen keinen
 * Hintergrund-Executor.
 */
internal fun newInMemoryDatabase(): AppDatabase =
    Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        AppDatabase::class.java
    )
        .allowMainThreadQueries()
        .build()
