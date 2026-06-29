package de.transio.hiuni.feature.home.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.transio.hiuni.core.design.HiUniMotion
import kotlinx.coroutines.launch

/**
 * 2D-Gegenstück zu [ReorderableColumn]: Long-Press startet Drag, hit-test-basierter Swap
 * sobald der Finger über einer anderen Kachel landet. Während des Drags wird der lokale
 * Mirror sofort mutiert, `onCommit(orderedIds)` feuert erst beim Loslassen (Spring-Bounce
 * zurück auf 0,0 — verhindert DataStore-Latenz im Drag-Loop).
 *
 * Layout: items.chunked(columns) in Rows. Per-Tile-Bounds werden in Root-Koordinaten
 * via `onGloballyPositioned.positionInRoot()` gemerkt; das `graphicsLayer` mit dem
 * visuellen Drag-Offset sitzt INNERHALB der Position-Beobachtung, damit Bounds
 * untranslated bleiben.
 */
@Composable
fun <T> ReorderableGrid(
    items: List<T>,
    itemKey: (T) -> String,
    onCommit: (orderedIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    horizontalSpacing: Dp = 10.dp,
    verticalSpacing: Dp = 10.dp,
    content: @Composable (item: T, dragHandle: Modifier, isDragging: Boolean) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Lokaler Mirror — externe Updates ersetzen ihn (key=items), Drags mutieren
    // ausschließlich diese Liste; finale Persistenz erst in onDragEnd via onCommit.
    var localItems by remember(items) { mutableStateOf(items) }

    val liveItems = rememberUpdatedState(localItems)
    val liveOnCommit by rememberUpdatedState(onCommit)
    val liveItemKey by rememberUpdatedState(itemKey)

    // Root-Koordinaten-basierte Bounding-Boxen pro Tile-Key.
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }

    var draggedKey by remember { mutableStateOf<String?>(null) }
    // Synchroner kumulierter Drag-Offset für Hit-Tests (Animatable ist suspend).
    var logicalOffset by remember { mutableStateOf(Offset.Zero) }
    val visualOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
        localItems.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)) {
                row.forEach { item ->
                    val key = itemKey(item)
                    val isDragging = draggedKey == key

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .zIndex(if (isDragging) 1f else 0f)
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInRoot()
                                val size = coords.size
                                itemBounds[key] = Rect(
                                    offset = pos,
                                    size = androidx.compose.ui.geometry.Size(
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    )
                                )
                            }
                            .graphicsLayer {
                                if (isDragging) {
                                    translationX = visualOffset.value.x
                                    translationY = visualOffset.value.y
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                    alpha = 0.95f
                                }
                            }
                    ) {
                        content(
                            item,
                            Modifier.pointerInput(key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedKey = key
                                        logicalOffset = Offset.Zero
                                        scope.launch { visualOffset.snapTo(Offset.Zero) }
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragEnd = {
                                        liveOnCommit(liveItems.value.map { liveItemKey(it) })
                                        draggedKey = null
                                        logicalOffset = Offset.Zero
                                        scope.launch {
                                            visualOffset.animateTo(
                                                targetValue = Offset.Zero,
                                                animationSpec = HiUniMotion.reorderSpring()
                                            )
                                        }
                                    },
                                    onDragCancel = {
                                        draggedKey = null
                                        logicalOffset = Offset.Zero
                                        scope.launch {
                                            visualOffset.animateTo(
                                                targetValue = Offset.Zero,
                                                animationSpec = HiUniMotion.reorderSpring()
                                            )
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val draggingKey = draggedKey ?: return@detectDragGesturesAfterLongPress
                                        val currentList = liveItems.value
                                        val currentIdx = currentList.indexOfFirst { liveItemKey(it) == draggingKey }
                                        if (currentIdx < 0) return@detectDragGesturesAfterLongPress

                                        var newOffset = logicalOffset + dragAmount

                                        // Aktuelle Bounds der gezogenen Kachel (untranslated, weil
                                        // onGloballyPositioned vor graphicsLayer sitzt).
                                        val draggedBounds = itemBounds[draggingKey]
                                        if (draggedBounds != null) {
                                            // Visuelles Zentrum des Fingers = original-Center + Drag-Delta.
                                            val fingerPos = draggedBounds.center + newOffset

                                            // Hit-Test gegen alle anderen Tiles.
                                            val targetEntry = itemBounds.entries.firstOrNull { (otherKey, bounds) ->
                                                otherKey != draggingKey &&
                                                    currentList.any { liveItemKey(it) == otherKey } &&
                                                    bounds.contains(fingerPos)
                                            }
                                            if (targetEntry != null) {
                                                val targetKey = targetEntry.key
                                                val targetBounds = targetEntry.value
                                                val targetIdx = currentList.indexOfFirst { liveItemKey(it) == targetKey }
                                                if (targetIdx >= 0 && targetIdx != currentIdx) {
                                                    val reordered = currentList.toMutableList()
                                                    val moved = reordered.removeAt(currentIdx)
                                                    reordered.add(targetIdx, moved)
                                                    localItems = reordered

                                                    // Nach dem Swap rutscht die gezogene Kachel optisch in
                                                    // die Slot-Position des Targets — wir korrigieren den
                                                    // logischen Offset, damit das visuelle Center weiter
                                                    // unter dem Finger bleibt.
                                                    val slotDelta = targetBounds.center - draggedBounds.center
                                                    newOffset -= slotDelta

                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            }
                                        }

                                        logicalOffset = newOffset
                                        scope.launch { visualOffset.snapTo(newOffset) }
                                    }
                                )
                            },
                            isDragging
                        )
                    }
                }
                // Spacer für odd last-row-Slots, damit die letzte Kachel die halbe Breite behält.
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
