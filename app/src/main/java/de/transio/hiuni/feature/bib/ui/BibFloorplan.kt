package de.transio.hiuni.feature.bib.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.transio.hiuni.R
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii

/**
 * Lageplan F-Gebäude. Optionaler [highlightRoomId] (101/102/103/105) malt
 * einen primary-Border um den jeweiligen Raum, so dass User direkt sehen
 * wo der Raum liegt. Box-Koordinaten sind als Fractions der Image-Größe
 * hinterlegt (Image-Native: 906×604) und skalieren mit der Container-Breite.
 *
 * [onRoomClick]: optional Tap-Handler pro Raum — leitet z.B. zur Buchungs-Sicht.
 */
@Composable
fun BibFloorplan(
    modifier: Modifier = Modifier,
    highlightRoomId: Int? = null,
    onRoomClick: ((Int) -> Unit)? = null,
    // A11y-Verb für den Tap-Handler. Startseite: "buchen"; Buchungs-Sicht,
    // wo ein Raum bereits offen ist: "wechseln".
    roomClickVerb: String = "buchen"
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "LAGEPLAN · F-GEBÄUDE",
                style = MaterialTheme.typography.labelSmall,
                color = semantics.onSurfaceMuted,
                fontWeight = FontWeight.Bold
            )
            highlightRoomId?.let { id ->
                Text(
                    text = if (onRoomClick != null) {
                        "F$id gewählt · tippe einen anderen Raum zum Wechseln"
                    } else {
                        "F$id ist hervorgehoben"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } ?: Text(
                text = if (onRoomClick != null) {
                    "Tippe einen Raum an, um ihn zu buchen"
                } else {
                    "Gruppenräume F101–F105 · Zugang von der Bibliothek"
                },
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted,
                modifier = Modifier.padding(top = 2.dp)
            )

            Box(modifier = Modifier.padding(top = 10.dp)) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(HiUniRadii.tile))
                        .background(Color(0xFFFBEFE4))
                ) {
                    val density = LocalDensity.current
                    val aspect = 906f / 604f
                    val widthPx = with(density) { maxWidth.toPx() }
                    val heightPx = widthPx / aspect
                    val heightDp = with(density) { heightPx.toDp() }

                    Image(
                        painter = painterResource(id = R.drawable.bib_floorplan),
                        contentDescription = "Lageplan F-Gebäude",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heightDp)
                    )

                    if (onRoomClick != null) {
                        ROOM_BOUNDS.forEach { (id, bounds) ->
                            val (xs, ys, xe, ye) = bounds
                            val left = with(density) { (widthPx * xs).toDp() }
                            val top = with(density) { (heightPx * ys).toDp() }
                            val w = with(density) { (widthPx * (xe - xs)).toDp() }
                            val h = with(density) { (heightPx * (ye - ys)).toDp() }
                            Box(
                                modifier = Modifier
                                    .offset(x = left, y = top)
                                    .size(width = w, height = h)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(onClickLabel = "Gruppenraum F$id $roomClickVerb") {
                                        onRoomClick(id)
                                    }
                                    .semantics { role = Role.Button }
                            )
                        }
                    }

                    highlightRoomId?.let { id ->
                        ROOM_BOUNDS[id]?.let { (xs, ys, xe, ye) ->
                            val left = with(density) { (widthPx * xs).toDp() }
                            val top = with(density) { (heightPx * ys).toDp() }
                            val w = with(density) { (widthPx * (xe - xs)).toDp() }
                            val h = with(density) { (heightPx * (ye - ys)).toDp() }
                            Box(
                                modifier = Modifier
                                    .offset(x = left, y = top)
                                    .size(width = w, height = h)
                                    .border(
                                        width = 3.dp,
                                        color = colors.primary,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .background(
                                        color = colors.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

private operator fun FloatArray.component1(): Float = this[0]
private operator fun FloatArray.component2(): Float = this[1]
private operator fun FloatArray.component3(): Float = this[2]
private operator fun FloatArray.component4(): Float = this[3]

/**
 * Bounding-Boxes pro Raum als Fractions des Original-Bildes (906×604).
 * (xStart, yStart, xEnd, yEnd) in [0..1].
 */
private val ROOM_BOUNDS: Map<Int, FloatArray> = mapOf(
    101 to floatArrayOf(0.087f, 0.595f, 0.245f, 0.880f),
    102 to floatArrayOf(0.290f, 0.595f, 0.500f, 0.880f),
    103 to floatArrayOf(0.495f, 0.595f, 0.750f, 0.880f),
    105 to floatArrayOf(0.745f, 0.500f, 0.900f, 0.880f),
)
