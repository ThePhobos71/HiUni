package de.transio.hiuni.feature.widgets.todos

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.transio.hiuni.MainActivity
import de.transio.hiuni.R
import de.transio.hiuni.feature.todos.data.TodoEntity
import de.transio.hiuni.feature.widgets.WidgetHiltEntryPoint
import de.transio.hiuni.feature.widgets.common.WidgetEmpty
import de.transio.hiuni.feature.widgets.common.WidgetHeader
import de.transio.hiuni.feature.widgets.common.WidgetSurface
import de.transio.hiuni.feature.widgets.common.WidgetTheme
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Home-Screen-Widget für offene Aufgaben. Zeigt Titel, Fälligkeit und einen
 * Icon-Toggle. Klick auf eine Zeile öffnet die App am Todo-Detail, das "+"
 * öffnet direkt das Neu-Aufgabe-Sheet. Nutzt das gemeinsame Design-Kit
 * (WidgetSurface/Header/Empty + WidgetTheme).
 */
class TodoWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(180.dp, 110.dp)   // 2×2 — nur Count
        private val MEDIUM = DpSize(250.dp, 180.dp)  // 3×3 — Top-4 mit Toggle
        private val LARGE = DpSize(320.dp, 280.dp)   // 4×5 — Top-8

        private const val MAX_ITEMS_LARGE = 8
        private const val MAX_ITEMS_MEDIUM = 4
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = WidgetHiltEntryPoint.get(context).todosRepository()
        provideContent {
            // Wir sammeln bis zu MAX_ITEMS_LARGE — die medium/small-Layouts
            // slicen sich davon lokal ab, damit wir nicht drei Flows brauchen.
            val todos by repo.observeOpen(limit = MAX_ITEMS_LARGE)
                .collectAsState(initial = emptyList())
            WidgetContent(todos)
        }
    }

    @Composable
    private fun WidgetContent(todos: List<TodoEntity>) {
        val context = LocalContext.current
        val size = LocalSize.current
        val today = LocalDate.now()

        val openApp = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_TODOS
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        val newTodoAction = actionRunCallback<NewTodoAction>()

        WidgetSurface(onClick = openApp) {
            WidgetHeader(
                iconRes = R.drawable.ic_widget_todo,
                title = "Aufgaben",
                context = if (todos.isNotEmpty()) "(${todos.size})" else null,
                actionIconRes = R.drawable.ic_widget_plus,
                onAction = newTodoAction,
            )
            Spacer(GlanceModifier.height(WidgetTheme.HeaderBottomSpacing))

            when {
                todos.isEmpty() -> WidgetEmpty(
                    iconRes = R.drawable.ic_widget_check_circle,
                    message = "Alles erledigt!",
                )
                size.width < MEDIUM.width -> CountOnly(count = todos.size)
                else -> {
                    val maxItems =
                        if (size.width >= LARGE.width) MAX_ITEMS_LARGE else MAX_ITEMS_MEDIUM
                    TodoList(todos.take(maxItems), today)
                }
            }
        }
    }

    @Composable
    private fun CountOnly(count: Int) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$count offen",
                style = TextStyle(
                    color = WidgetTheme.OnSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    @Composable
    private fun TodoList(todos: List<TodoEntity>, today: LocalDate) {
        val context = LocalContext.current
        LazyColumn {
            items(items = todos, itemId = { it.id }) { todo ->
                TodoRow(todo = todo, today = today, context = context)
            }
        }
    }

    @Composable
    private fun TodoRow(todo: TodoEntity, today: LocalDate, context: Context) {
        val overdue = todo.dueDate != null && todo.dueDate.isBefore(today) && !todo.isDone
        val titleColor = if (overdue) WidgetTheme.Red else WidgetTheme.OnSurface

        // Row-Tap öffnet Detail via Deep-Link. Extras direkt am Intent statt
        // via ActionParameters — dann hat MainActivity einen einheitlichen
        // `intent.getLongExtra(EXTRA_TODO_ID, -1)`-Pfad.
        val openDetailIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_TODOS
            putExtra(EXTRA_TODO_ID, todo.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(actionStartActivity(openDetailIntent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kein CheckBox-Composable mehr — Vektor-Icon aus dem Design-Kit.
            // Das Icon selbst ist der Tap-Target für den Toggle, damit die
            // Row-Klick-Aktion (Detail öffnen) unabhängig bleibt.
            val iconRes =
                if (todo.isDone) R.drawable.ic_widget_check_circle
                else R.drawable.ic_widget_circle
            val iconTint: ColorProvider =
                if (todo.isDone) WidgetTheme.Primary else WidgetTheme.OnSurfaceMuted
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier
                    .size(20.dp)
                    .clickable(
                        actionRunCallback<ToggleDoneAction>(
                            ToggleDoneAction.parameters(todo.id)
                        )
                    ),
                colorFilter = ColorFilter.tint(iconTint),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = todo.title,
                style = TextStyle(
                    color = titleColor,
                    fontSize = 13.sp
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            todo.dueDate?.let { due ->
                Spacer(GlanceModifier.width(6.dp))
                DueLabel(due = due, today = today)
            }
        }
    }

    @Composable
    private fun DueLabel(due: LocalDate, today: LocalDate) {
        val days = ChronoUnit.DAYS.between(today, due).toInt()
        val overdue = days < 0
        val label = when {
            days < 0 -> "überfällig"
            days == 0 -> "heute"
            days == 1 -> "morgen"
            else -> "in $days Tagen"
        }
        val tint = if (overdue) WidgetTheme.Red else WidgetTheme.OnSurfaceMuted
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_clock),
                contentDescription = null,
                modifier = GlanceModifier.size(14.dp),
                colorFilter = ColorFilter.tint(tint),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = label,
                style = TextStyle(
                    color = tint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}
