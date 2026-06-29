package de.transio.hiuni.feature.home.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.transio.hiuni.core.design.HiUniMotion
import kotlinx.coroutines.launch

/**
 * Long-press startet Drag, vertikaler Swap mit Nachbar sobald Offset > halbe Item-Höhe.
 * Während des Drags wird nur die lokale Reihenfolge mutiert; der finale Order-Commit
 * erfolgt erst in [onCommit] beim Loslassen, damit DataStore-Persistenz nicht im
 * Drag-Loop hängt.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    itemKey: (T) -> String,
    onCommit: (orderedIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 18.dp,
    content: @Composable (item: T, dragHandle: Modifier, isDragging: Boolean) -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val spacingPx = with(density) { spacing.toPx() }

    // Lokaler Mirror — wird beim externen Update zurückgesetzt, während Drags
    // ausschließlich diesen Mirror anfassen. So fühlt sich der Drag flüssig an.
    var localItems by remember(items) { mutableStateOf(items) }

    val liveItems = rememberUpdatedState(localItems)
    val liveOnCommit by rememberUpdatedState(onCommit)
    val liveItemKey by rememberUpdatedState(itemKey)

    val itemHeights = remember { mutableStateMapOf<String, Int>() }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    // Synchroner Offset für die Swap-Mathematik (Animatable ist suspend → kann onDrag
    // nicht direkt schreiben). Das `visualOffset` Animatable spiegelt diesen Wert während
    // des Drags und federt bei Drag-Ende auf 0 zurück.
    var logicalOffsetPx by remember { mutableFloatStateOf(0f) }
    val visualOffset = remember { Animatable(0f) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        localItems.forEach { item ->
            val key = itemKey(item)
            val isDragging = draggedKey == key

            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .onSizeChanged { itemHeights[key] = it.height }
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = visualOffset.value
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
                                logicalOffsetPx = 0f
                                scope.launch { visualOffset.snapTo(0f) }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                liveOnCommit(liveItems.value.map { liveItemKey(it) })
                                draggedKey = null
                                logicalOffsetPx = 0f
                                scope.launch {
                                    visualOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = HiUniMotion.reorderSpring()
                                    )
                                }
                            },
                            onDragCancel = {
                                draggedKey = null
                                logicalOffsetPx = 0f
                                scope.launch {
                                    visualOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = HiUniMotion.reorderSpring()
                                    )
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val currentList = liveItems.value
                                val currentIdx = currentList.indexOfFirst { liveItemKey(it) == draggedKey }
                                if (currentIdx < 0) return@detectDragGesturesAfterLongPress

                                var newOffset = logicalOffsetPx + dragAmount.y

                                // Swap nach unten?
                                if (newOffset > 0 && currentIdx < currentList.lastIndex) {
                                    val belowKey = liveItemKey(currentList[currentIdx + 1])
                                    val belowH = itemHeights[belowKey] ?: 0
                                    val threshold = (belowH + spacingPx) / 2f
                                    if (newOffset > threshold) {
                                        val reordered = currentList.toMutableList()
                                        val moved = reordered.removeAt(currentIdx)
                                        reordered.add(currentIdx + 1, moved)
                                        localItems = reordered
                                        newOffset -= (belowH + spacingPx)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                } else if (newOffset < 0 && currentIdx > 0) {
                                    // Swap nach oben?
                                    val aboveKey = liveItemKey(currentList[currentIdx - 1])
                                    val aboveH = itemHeights[aboveKey] ?: 0
                                    val threshold = -(aboveH + spacingPx) / 2f
                                    if (newOffset < threshold) {
                                        val reordered = currentList.toMutableList()
                                        val moved = reordered.removeAt(currentIdx)
                                        reordered.add(currentIdx - 1, moved)
                                        localItems = reordered
                                        newOffset += (aboveH + spacingPx)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }

                                logicalOffsetPx = newOffset
                                scope.launch { visualOffset.snapTo(newOffset) }
                            }
                        )
                    },
                    isDragging
                )
            }
        }
    }
}
