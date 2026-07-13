package de.transio.hiuni.feature.widgets.todos

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
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
import de.transio.hiuni.feature.todos.data.TodoEntity
import de.transio.hiuni.feature.widgets.WidgetHiltEntryPoint
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Home-Screen-Widget für offene Aufgaben. Zeigt Titel, Fälligkeit und einen
 * inline-Checkbox-Toggle. Klick auf eine Zeile öffnet die App am Todo-Detail,
 * das "+" öffnet direkt das Neu-Aufgabe-Sheet.
 *
 * Warum drei Größen? Der Launcher-Grid ist auf verschiedenen Geräten
 * unterschiedlich fein — mit `SizeMode.Responsive` liefert Glance dem
 * AppWidgetHost mehrere Layouts, und der Launcher wählt selbstständig das
 * passende. Dadurch bleibt das Widget auch beim Resize scharf, ohne dass wir
 * einen Runtime-Branch auf `LocalSize` bauen (das würde nur Exact/Single-
 * Modus brauchen).
 */
class TodoWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(180.dp, 110.dp)   // 2×2 — nur Count
        private val MEDIUM = DpSize(250.dp, 180.dp)  // 3×3 — Top-4 mit Checkbox
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
        val size = LocalSize.current
        val today = LocalDate.now()

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetColors.surface)
                .cornerRadius(20.dp)
                .padding(12.dp)
        ) {
            Header()
            Spacer(GlanceModifier.height(8.dp))

            when {
                todos.isEmpty() -> EmptyState()
                size.width < MEDIUM.width -> CountOnly(count = todos.size)
                else -> {
                    val maxItems = if (size.width >= LARGE.width) MAX_ITEMS_LARGE else MAX_ITEMS_MEDIUM
                    TodoList(todos.take(maxItems), today)
                }
            }
        }
    }

    @Composable
    private fun Header() {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Aufgaben",
                style = TextStyle(
                    color = WidgetColors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            // Plus-Button. Als Text-Button ohne Icon-Ressource — Glance
            // Vector-Icons brauchen ImageProvider(...) auf einem Drawable-
            // Res, und für V1 wollen wir ohne zusätzliches XML auskommen.
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .background(WidgetColors.primaryContainer)
                    .cornerRadius(14.dp)
                    .clickable(actionRunCallback<NewTodoAction>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    style = TextStyle(
                        color = WidgetColors.onPrimaryContainer,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    @Composable
    private fun EmptyState() {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Keine offenen Aufgaben — bleib entspannt",
                style = TextStyle(
                    color = WidgetColors.onSurfaceVariant,
                    fontSize = 13.sp
                )
            )
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
                    color = WidgetColors.onSurface,
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
        val titleColor = if (overdue) WidgetColors.red else WidgetColors.onSurface

        // Row-Tap öffnet Detail via Deep-Link. actionStartActivity wickelt den
        // Intent in ein PendingIntent — wir setzen extras direkt am Intent
        // statt über ActionParameters, weil der Wrap-up-Task in MainActivity
        // dann einen einheitlichen `intent.getLongExtra(EXTRA_TODO_ID, -1)`-
        // Pfad hat (parameters landen zwar auch als Extras, aber der Key
        // wäre der Parameter-Name — unnötige Indirektion).
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
            CheckBox(
                checked = todo.isDone,
                onCheckedChange = actionRunCallback<ToggleDoneAction>(
                    ToggleDoneAction.parameters(todo.id)
                ),
                colors = CheckboxDefaults.colors(
                    checkedColor = WidgetColors.primary,
                    uncheckedColor = WidgetColors.onSurfaceVariant
                )
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
                DueChip(due = due, today = today)
            }
        }
    }

    @Composable
    private fun DueChip(due: LocalDate, today: LocalDate) {
        val days = ChronoUnit.DAYS.between(today, due).toInt()
        val (label, bg, fg) = when {
            days < 0 -> Triple("überfällig", WidgetColors.redSurface, WidgetColors.red)
            days == 0 -> Triple("heute", WidgetColors.redSurface, WidgetColors.red)
            days == 1 -> Triple("morgen", WidgetColors.amberSurface, WidgetColors.amber)
            days in 2..6 -> Triple("in $days Tagen", WidgetColors.neutralSurface, WidgetColors.onSurfaceVariant)
            else -> Triple("in $days Tagen", WidgetColors.neutralSurface, WidgetColors.onSurfaceVariant)
        }
        Box(
            modifier = GlanceModifier
                .background(bg)
                .cornerRadius(8.dp)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = fg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

/**
 * Widget-lokale Farbpalette. Wir spiegeln HiUniColors bewusst nicht 1:1 —
 * das Widget lebt außerhalb des Compose-Themes und braucht eigene
 * `ColorProvider`-Werte, die Light/Dark automatisch resolven. Werte
 * entsprechen den OKLCH-Tokens aus `core.design.Color.kt`.
 */
private object WidgetColors {
    private fun dn(day: Color, night: Color): ColorProvider =
        androidx.glance.color.ColorProvider(day = day, night = night)

    // Neutrals
    val surface: ColorProvider = dn(Color(0xFFFFFFFF), Color(0xFF1E1F28))
    val onSurface: ColorProvider = dn(Color(0xFF16161B), Color(0xFFEAEAEE))
    val onSurfaceVariant: ColorProvider = dn(Color(0xFF74757B), Color(0xFF9899A2))
    val neutralSurface: ColorProvider = dn(Color(0xFFF1F4F8), Color(0xFF272832))

    // Primary (Indigo hue 265)
    val primary: ColorProvider = dn(Color(0xFF3D3FBF), Color(0xFF9595FF))
    val primaryContainer: ColorProvider = dn(Color(0xFFE6E5F8), Color(0xFF26264F))
    val onPrimaryContainer: ColorProvider = dn(Color(0xFF3D3FBF), Color(0xFF9595FF))

    // Amber (hue 72)
    val amber: ColorProvider = dn(Color(0xFFB47817), Color(0xFFE4B056))
    val amberSurface: ColorProvider = dn(Color(0xFFF8EAD0), Color(0xFF2F2818))

    // Red (hue 25)
    val red: ColorProvider = dn(Color(0xFFC2342C), Color(0xFFF7766B))
    val redSurface: ColorProvider = dn(Color(0xFFFAD9D4), Color(0xFF2E1715))
}
