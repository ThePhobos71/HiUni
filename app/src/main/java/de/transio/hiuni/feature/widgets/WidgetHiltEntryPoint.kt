package de.transio.hiuni.feature.widgets

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.lsf.data.LsfExamsRepository
import de.transio.hiuni.feature.todos.data.TodosRepository

/**
 * Hilt-Zugriff für Glance-Widgets. `GlanceAppWidget` ist keine Hilt-Componente
 * (kein `@AndroidEntryPoint`), also holen wir Repositories über den Standard-
 * `EntryPoint`-Weg aus dem SingletonComponent.
 *
 * Nutzung im Widget:
 *   val entry = WidgetHiltEntryPoint.get(context)
 *   entry.todosRepository().observeOpen(limit = 8).collect { ... }
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetHiltEntryPoint {
    fun todosRepository(): TodosRepository
    fun calendarRepository(): CalendarRepository
    fun examsRepository(): LsfExamsRepository

    companion object {
        fun get(context: Context): WidgetHiltEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetHiltEntryPoint::class.java,
            )
    }
}
