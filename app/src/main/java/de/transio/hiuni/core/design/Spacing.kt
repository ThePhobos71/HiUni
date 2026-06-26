package de.transio.hiuni.core.design

import androidx.compose.ui.unit.dp

/**
 * Spacing-Skala für HiUni. Spiegelt die Radii-Größen, damit Padding und Corner
 * visuell harmonieren (z.B. xl = 22 als Screen-Padding zu HiUniRadii.big = 24).
 *
 * Faustregel:
 *   xs (8)  — eng beieinander liegende Elemente, Icon-zu-Text
 *   sm (10) — Stack-Spacing in Listen, Row-Items
 *   md (14) — Karten-Innenpadding, Section-Abstände
 *   lg (18) — Karten-Außenpadding, Block-Trennung
 *   xl (22) — Screen-Padding, Hero-Innenpadding
 */
object HiUniSpacing {
    val xs = 8.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 18.dp
    val xl = 22.dp
}
