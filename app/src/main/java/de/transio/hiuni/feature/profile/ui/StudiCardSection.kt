package de.transio.hiuni.feature.profile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.transio.hiuni.core.auth.UserProfile
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii

/**
 * Digitale Studi-Karte — soll sich anfühlen wie ein echter Plastikausweis im Wallet.
 *
 * Layout:
 *  - Außen: Surface mit großzügig gerundeten Ecken + Soft-Shadow, Hintergrund als
 *    diagonaler Gradient von `colors.primary` (oben links) zu einer leicht abgedunkelten
 *    Variante (unten rechts). Plus dezente Deko-Kreise (Disc-Pattern) hinter dem Inhalt
 *    für Brand-Look — kein Bild-Asset nötig.
 *  - Oben: zweizeiliger Header: "UNI HILDESHEIM" (klein, gespacet) + "Studierendenausweis"
 *    in titleMedium, gegenüber eine grüne OFFLINE-Pill als Status.
 *  - Mitte: voller Name (headlineMedium, fett), darunter Matrikel-Label + Matrikelnummer
 *    monospaced in 4er-Gruppen.
 *  - Unten: weißer Barcode-Streifen auf eigenem Surface (für Scan-Kontrast), darüber
 *    der Code-128-Barcode + die wiederholte Matrikel-Klartext-Zeile.
 *  - Ganz unten: kleine Footnote "Funktioniert ohne Internet".
 *
 * Edge-Cases:
 *  - Keine Matrikel → kompakter "nicht verfügbar"-Hinweis.
 *  - Barcode-Encode scheitert → ganze Section wird ausgeblendet (selten).
 */
@Composable
fun StudiCardSection(
    profile: UserProfile,
    modifier: Modifier = Modifier
) {
    val matrikel = profile.matrikel?.trim().orEmpty()
    if (matrikel.isBlank()) {
        EmptyStudiCard(modifier = modifier)
        return
    }

    val density = LocalDensity.current
    val barcodeHeightPx = with(density) { 64.dp.roundToPx() }
    val barcodeWidthPx = 720

    val barcode = rememberCode128Bitmap(
        content = matrikel,
        widthPx = barcodeWidthPx,
        heightPx = barcodeHeightPx
    ) ?: return

    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    val gradient = Brush.linearGradient(
        colors = listOf(colors.primary, colors.primary.copy(alpha = 0.78f)),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    // Geräte-Neigung für Holo-Effekt + Parallax. Auf Emulator/Sensorless: Offset.Zero.
    val tiltState = rememberDeviceTilt()
    val tilt by tiltState

    // Zwei Infinite-Sweeps in verschiedener Geschwindigkeit + Richtung. Zusammen
    // mit dem Tilt-Versatz ergibt das ein "Diffraktions-Gitter"-Feeling wie auf
    // echten Holo-Stickern: die Bänder überlagern sich und erzeugen Moiré-artige
    // Farbverläufe statt einer einzelnen Lichtbahn.
    val infTransition = rememberInfiniteTransition(label = "holo")
    val sweepA by infTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "holo-sweepA"
    )
    val sweepB by infTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "holo-sweepB"
    )

    // Volles Regenbogen-Spektrum, hohe Sättigung. Wird per BlendMode.Plus
    // additiv über den primary-Gradient gemalt — auf dunklem Bg wirkt das wie
    // ein leuchtendes Diffraktionsmuster, ungefähr wie eine CD-Reflexion.
    val rainbowColors = remember {
        listOf(
            Color(0xFFFF3B7C),  // pink
            Color(0xFFFFA53B),  // orange
            Color(0xFFFFF53B),  // yellow
            Color(0xFF3BFF8E),  // green
            Color(0xFF3BD0FF),  // cyan
            Color(0xFF7C3BFF),  // purple
            Color(0xFFFF3B7C)   // pink (loop)
        )
    }
    val rainbowColorsThin = remember {
        listOf(
            Color.Transparent,
            Color(0xFFFFB3D9),
            Color(0xFFB3F0FF),
            Color(0xFFFFF1B3),
            Color.Transparent
        )
    }

    // Specular-Highlight: heller "Spot" der dem Tilt folgt. Modelliert das
    // Lichtreflex-Verhalten echter Holos.
    val cardCenterFractionX = 0.5f + tilt.x * 0.6f
    val cardCenterFractionY = 0.5f + tilt.y * 0.6f

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(HiUniRadii.big),
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(HiUniRadii.big),
                clip = false
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(HiUniRadii.big))
                .background(gradient)
        ) {
            // ── Holo-Diffraktion: drei überlagerte Layer mit BlendMode.Plus ─────
            // Layer 1: breiter Regenbogen-Sweep diagonal, mittlere Geschwindigkeit.
            // Layer 2: schmaleres pastellfarbenes Band, andere Richtung + Speed.
            // Layer 3: heller Specular-Spot (radial), folgt dem Tilt.
            // Alle drei werden additiv (Plus/Screen) auf den primary-Gradient gemalt.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val diag = Math.hypot(w.toDouble(), h.toDouble()).toFloat()

                        // Layer 1: breiter Regenbogen, läuft diagonal über die ganze Karte
                        val phaseA = sweepA * 2f - 1f  // -1..1
                        val tiltOffsetA = Offset(tilt.x * w * 0.4f, tilt.y * h * 0.4f)
                        val startA = Offset(
                            x = phaseA * w - diag * 0.5f + tiltOffsetA.x,
                            y = phaseA * h - diag * 0.5f + tiltOffsetA.y
                        )
                        val endA = Offset(
                            x = startA.x + diag * 1.4f,
                            y = startA.y + diag * 0.9f
                        )
                        val rainbowBrush = Brush.linearGradient(
                            colors = rainbowColors,
                            start = startA,
                            end = endA
                        )

                        // Layer 2: schmaleres pastellfarbenes Diffraktions-Band, gegenläufig
                        val phaseB = sweepB * 2f - 1f
                        val tiltOffsetB = Offset(tilt.x * w * -0.3f, tilt.y * h * 0.3f)
                        val startB = Offset(
                            x = -phaseB * w + tiltOffsetB.x,
                            y = phaseB * h * 0.6f + tiltOffsetB.y
                        )
                        val endB = Offset(
                            x = startB.x + w * 0.8f,
                            y = startB.y - h * 0.5f
                        )
                        val pastelBrush = Brush.linearGradient(
                            colors = rainbowColorsThin,
                            start = startB,
                            end = endB
                        )

                        // Layer 3: weicher heller Spot, folgt dem Tilt
                        val spotBrush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            center = Offset(w * cardCenterFractionX, h * cardCenterFractionY),
                            radius = w * 0.55f
                        )

                        onDrawBehind {
                            // BlendMode.Plus addiert die RGB-Werte → wirkt wie
                            // emittiertes Licht auf dem dunklen Gradient.
                            drawRect(
                                brush = rainbowBrush,
                                size = Size(w, h),
                                alpha = 0.38f,
                                blendMode = BlendMode.Plus
                            )
                            drawRect(
                                brush = pastelBrush,
                                size = Size(w, h),
                                alpha = 0.45f,
                                blendMode = BlendMode.Plus
                            )
                            drawRect(
                                brush = spotBrush,
                                size = Size(w, h),
                                blendMode = BlendMode.Plus
                            )
                        }
                    }
            )

            // Dezente Deko-Kreise im Hintergrund (Brand-Akzent ohne Asset)
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
            )
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .align(Alignment.BottomStart)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // Sanftes 3D-Parallax-Tilten — visuell only, kein Touch-Versatz.
                        // Multiplikator 8° = subtil, max ~5° bei normalem Halten.
                        rotationX = -tilt.y * 8f
                        rotationY = tilt.x * 8f
                        cameraDistance = 12f * this.density
                    }
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                // ── Header ─────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "UNIVERSITÄT HILDESHEIM",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onPrimary.copy(alpha = 0.7f),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Studierendenausweis",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(HiUniRadii.pill)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(semantics.green)
                            )
                            Text(
                                text = "OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ── Name + Matrikel ────────────────────────────────────
                Text(
                    text = profile.fullName?.takeIf { it.isNotBlank() } ?: "Studi*in",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.onPrimary,
                    maxLines = 2
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "MATRIKEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimary.copy(alpha = 0.6f),
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = matrikel,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 3.sp
                    ),
                    color = colors.onPrimary
                )

                Spacer(Modifier.height(22.dp))

                // ── Barcode-Streifen (eigenes weißes Surface für Scan-Kontrast) ──
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(HiUniRadii.card),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Image(
                            bitmap = barcode,
                            contentDescription = "Code-128-Barcode mit Matrikelnummer",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            contentScale = ContentScale.FillBounds,
                            filterQuality = FilterQuality.None
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = matrikel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 4.sp
                            ),
                            color = Color.Black.copy(alpha = 0.75f),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Funktioniert ohne Internet · Halte den Code unter den Scanner",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimary.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun EmptyStudiCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile))
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Badge,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Studierendenausweis",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Matrikelnummer nicht verfügbar — bitte erst einloggen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

