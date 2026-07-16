package de.transio.hiuni.core.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Zentrale Motion-Konstanten. Wer eine Animation neu schreibt, soll hier den
 * passenden Token finden statt eigene Durationen auszuwürfeln.
 *
 * Skala:
 *   - [tabFadeMs] (150) — Tab-Wechsel via Bottom-Nav, ganz schnell, damit
 *     der User die App-Welt-Lokationen nicht „durch eine Animation" wahrnimmt.
 *   - [contentSwitchMs] (200) — In-Screen-Content-Wechsel (Calendar Day/Week/
 *     Month-Switch, Onboarding-Skip-Button etc.). Snappy aber sichtbar.
 *   - [pushMs] (220) — Push-Navigation hinein (Detail-/Sub-Screens), inklusive
 *     Horizontal-Slide. Etwas träger als ein Tab-Wechsel, damit klar wird
 *     dass eine neue „Tiefe" entstanden ist.
 *   - [pushFadeOutMs] (120) — Detail-Screens beim Pop schnell ausfaden, damit
 *     der Zurück-Tap sofort responsive wirkt.
 */
object HiUniMotion {
    const val tabFadeMs = 150
    const val contentSwitchMs = 200
    const val pushMs = 220
    const val pushFadeOutMs = 120

    /**
     * Puls-Halbperiode für Skeleton-Platzhalter (900ms). Bewusst langsam und
     * mit RepeatMode.Reverse — es soll „atmen", nicht blinken, damit während des
     * ersten Ladens keine Unruhe entsteht. Genutzt von den HiUniSkeleton-Bausteinen.
     */
    const val skeletonPulseMs = 900

    /** Standard-Tween für In-Screen-Content-Swaps (Calendar, Onboarding etc.). */
    fun contentSwitchTween() = tween<Float>(durationMillis = contentSwitchMs)

    /**
     * Bounce-Spring für Drag-Reorder-Snap-Back. Bewusst etwas „spielerisch"
     * (MediumBouncy + MediumLow), damit der User spürt dass das Item den
     * Snap-Slot gefunden hat.
     */
    fun <T> reorderSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
