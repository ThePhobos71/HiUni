package de.transio.hiuni.feature.widgets.todos

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import de.transio.hiuni.MainActivity
import de.transio.hiuni.feature.widgets.WidgetHiltEntryPoint
import kotlinx.coroutines.flow.first

/**
 * Deep-Link-Vertrag zwischen Widget und App. `MainActivity` muss diese
 * Action + Extras in seinem Intent-Handling verarbeiten (Wrap-up-Task).
 *
 * - `ACTION_OPEN_TODOS` mit `EXTRA_MODE = "new"` → Neu-Aufgabe-Sheet öffnen
 * - `ACTION_OPEN_TODOS` mit `EXTRA_TODO_ID`      → Aufgabe im Detail anzeigen
 * - `ACTION_OPEN_TODOS` ohne Extras              → Aufgaben-Liste öffnen
 */
internal const val ACTION_OPEN_TODOS = "de.transio.hiuni.OPEN_TODOS"
internal const val EXTRA_MODE = "mode"
internal const val EXTRA_TODO_ID = "todoId"

internal val TODO_ID_PARAM = ActionParameters.Key<Long>("todoId")

/**
 * Toggelt den Done-State einer Aufgabe. Wir lesen den aktuellen Zustand aus dem
 * Flow (`first()`) statt eine Getter-API zu bauen — das Widget aktualisiert
 * ohnehin gleich danach über den Flow, und ein separater DAO-Getter wäre nur
 * für diesen Callback-Pfad da.
 */
class ToggleDoneAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val todoId = parameters[TODO_ID_PARAM] ?: return
        val repo = WidgetHiltEntryPoint.get(context).todosRepository()
        // observeOpen zeigt nur offene Todos; wenn die Aufgabe schon done ist,
        // taucht sie hier nicht auf. Für den Widget-Use-Case reicht das: das
        // Widget zeigt nur offene, also ist der Toggle-Zielzustand immer `true`.
        val current = repo.observeOpen(limit = Int.MAX_VALUE).first().find { it.id == todoId }
        val newState = !(current?.isDone ?: false)
        repo.setDone(todoId, newState)
        TodoWidget().update(context, glanceId)
    }

    companion object {
        fun parameters(todoId: Long): ActionParameters =
            actionParametersOf(TODO_ID_PARAM to todoId)
    }
}

/**
 * Öffnet die App im "Neue Aufgabe"-Modus. Für Widget→Activity-Starts brauchen
 * wir `FLAG_ACTIVITY_NEW_TASK`, weil der Widget-Context kein Activity-Context
 * ist. `singleTask` an der MainActivity sorgt dafür, dass laufende Instanzen
 * wiederverwendet werden und den Intent via `onNewIntent` bekommen.
 */
class NewTodoAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_TODOS
            putExtra(EXTRA_MODE, "new")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
