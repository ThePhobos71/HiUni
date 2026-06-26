package de.transio.hiuni.feature.profile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
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
 * Digitale Studi-Karte im Stil des echten Plastik-Ausweises der Uni Hildesheim:
 *  - Oben: Hintergrund-Bild der Uni-Fassade (Platzhalter: Blauton-Gradient mit
 *    Fenster-Andeutungen). User kann später ein echtes Foto in
 *    `res/drawable/studi_card_bg` ablegen → dann austauschen.
 *  - Weißes Info-Panel mit H-Logo links, Avatar-Initialen, Titel + Name +
 *    Matrikel-Nr. rechts. Genau wie auf der echten Karte.
 *  - Unten: Kulturticket-Streifen mit Gültigkeits-Zeitraum (Semester-bezogen),
 *    leicht transparent über dem Hintergrund.
 *  - Roter "Gültigkeit | validity | validité"-Streifen ganz unten.
 *  - **Holo-Shimmer**: animierter Regenbogen-Sweep + Tilt-driven Specular-Spot
 *    additiv (BlendMode.Plus) drüber gelegt — wirkt wie die echten Diffraktions-
 *    Sticker auf Studierendenausweisen.
 *  - Darunter (außerhalb des Karten-Frames): der Code-128-Barcode, weil der
 *    Scanner ihn braucht, aber auf der echten Karte ist er auf der Rückseite.
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StudiCardFace(profile = profile, matrikel = matrikel)
        BarcodeStrip(matrikel = matrikel, barcode = barcode)
    }
}

@Composable
private fun StudiCardFace(profile: UserProfile, matrikel: String) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    // Holo-Animation: zwei Sweeps in verschiedener Geschwindigkeit + Richtung,
    // erzeugen Moiré-artiges Diffraktions-Muster wie auf echten Holo-Stickern.
    val tiltState = rememberDeviceTilt()
    val tilt by tiltState
    val infTransition = rememberInfiniteTransition(label = "holo")
    val sweepA by infTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepA"
    )
    val sweepB by infTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweepB"
    )

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(HiUniRadii.big),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f) // Standard ID-1-Format (Kreditkarte/EC-Karte)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(HiUniRadii.big),
                clip = false
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(HiUniRadii.big))
        ) {
            // ── Layer 1: Campus-Hintergrund (Platzhalter-Gradient) ─────────
            // Cool-blauer Verlauf wie auf dem echten Foto (Sky + Fassade).
            // Faint horizontale Linien simulieren Fenster-Reihen.
            CampusBackground()

            // ── Layer 2: Info-Panel (weiß) — wie auf der echten Karte ─────
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.weight(0.32f)) // Top-Bild sichtbar lassen

                Surface(
                    color = Color.White.copy(alpha = 0.96f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.40f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UniHildesheimLogo(size = 56.dp)
                        AvatarInitials(profile = profile)
                        Spacer(Modifier.size(4.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Studierendenausweis",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = profile.fullName?.takeIf { it.isNotBlank() } ?: "Studi*in",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black,
                                maxLines = 2
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Matrikel-Nr.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Black.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = matrikel,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }

                // ── Layer 3: Kulturticket-Area ─────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.20f)
                ) {
                    KulturticketStrip()
                }

                // ── Layer 4: Roter "Gültigkeit"-Streifen ───────────────────
                Surface(
                    color = Color(0xFFE63946), // Uni-Rot (wie auf der echten Karte)
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.08f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Gültigkeit | validity | validité",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── Holo-Shimmer-Overlay — additiv über der ganzen Karte ──────
            HoloShimmer(sweepA = sweepA, sweepB = sweepB, tilt = tilt)
        }
    }
}

@Composable
private fun CampusBackground() {
    // Cool blue-gray gradient als Hintergrund — simuliert Himmel + Uni-Fassade.
    // Echtes Foto kann später unter dem Gradient als Image gelegt werden (drawable).
    val skyGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFB8C7D6), // light overcast sky
            Color(0xFF6F8294), // building shadow / facade
            Color(0xFF4A5A6B)
        )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(skyGradient)
            .drawBehind {
                // Faint horizontale Stripes — Fenster-Andeutung auf der Fassade.
                val rows = 4
                val stripeAlpha = 0.12f
                val stripeColor = Color.Black.copy(alpha = stripeAlpha)
                val height = size.height * 0.55f
                val startY = size.height * 0.25f
                for (i in 0 until rows) {
                    val y = startY + (height / rows) * (i + 0.5f)
                    drawRect(
                        color = stripeColor,
                        topLeft = Offset(0f, y),
                        size = Size(size.width, 6f)
                    )
                }
            }
    )
}

@Composable
private fun UniHildesheimLogo(size: androidx.compose.ui.unit.Dp) {
    // Stilisierte Variante des echten "H"-Logos: drei orange-rote Trapeze,
    // ansteigend wie auf dem echten Stiftungs-Siegel. Plus Kreis-Rahmen.
    // Echtes SVG/PNG kann später unter `res/drawable/uni_hildesheim_logo` rein.
    val logoColor = Color(0xFFE63946)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .drawBehind {
                val w = this.size.width
                val h = this.size.height
                val centerX = w / 2f
                val centerY = h / 2f
                val barWidth = w * 0.13f
                val barGap = w * 0.06f
                // Drei aufsteigende Trapeze
                val heights = listOf(0.45f, 0.65f, 0.85f) // relative Höhen
                heights.forEachIndexed { i, hf ->
                    val barH = h * hf * 0.55f
                    val totalSpan = barWidth * 3 + barGap * 2
                    val x = centerX - totalSpan / 2f + i * (barWidth + barGap)
                    drawRect(
                        color = logoColor,
                        topLeft = Offset(x, centerY - barH / 2f),
                        size = Size(barWidth, barH)
                    )
                }
            }
    )
}

@Composable
private fun AvatarInitials(profile: UserProfile) {
    val colors = MaterialTheme.colorScheme
    val initials = remember(profile) {
        val first = profile.firstName?.firstOrNull()?.toString().orEmpty()
        val last = profile.nachname?.firstOrNull()?.toString().orEmpty()
        (first + last).ifEmpty { "?" }
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = colors.primary
        )
    }
}

@Composable
private fun KulturticketStrip() {
    val semester = remember {
        // Sommersemester 2026: 1.4. – 30.9.; Wintersemester: 1.10. – 31.3.
        val today = java.time.LocalDate.now()
        val year = today.year
        if (today.monthValue in 4..9) {
            "vom 01.04.$year bis 30.09.$year"
        } else {
            val winterStart = if (today.monthValue < 4) year - 1 else year
            "vom 01.10.$winterStart bis 31.03.${winterStart + 1}"
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Studierendenausweis mit Kulturticket",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "unter asta-hildesheim.de/ticket/",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                text = semester,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun HoloShimmer(sweepA: Float, sweepB: Float, tilt: Offset) {
    // Volles Spektrum für den Hauptsweep
    val rainbowColors = remember {
        listOf(
            Color(0xFFFF3B7C), Color(0xFFFFA53B), Color(0xFFFFF53B),
            Color(0xFF3BFF8E), Color(0xFF3BD0FF), Color(0xFF7C3BFF),
            Color(0xFFFF3B7C)
        )
    }
    val pastelBand = remember {
        listOf(
            Color.Transparent,
            Color(0xFFFFB3D9),
            Color(0xFFB3F0FF),
            Color(0xFFFFF1B3),
            Color.Transparent
        )
    }
    val cardCenterFractionX = 0.5f + tilt.x * 0.6f
    val cardCenterFractionY = 0.5f + tilt.y * 0.6f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val diag = Math.hypot(w.toDouble(), h.toDouble()).toFloat()

                val phaseA = sweepA * 2f - 1f
                val tiltA = Offset(tilt.x * w * 0.4f, tilt.y * h * 0.4f)
                val startA = Offset(
                    x = phaseA * w - diag * 0.5f + tiltA.x,
                    y = phaseA * h - diag * 0.5f + tiltA.y
                )
                val endA = Offset(startA.x + diag * 1.4f, startA.y + diag * 0.9f)
                val rainbowBrush = Brush.linearGradient(rainbowColors, startA, endA)

                val phaseB = sweepB * 2f - 1f
                val tiltB = Offset(tilt.x * w * -0.3f, tilt.y * h * 0.3f)
                val startB = Offset(-phaseB * w + tiltB.x, phaseB * h * 0.6f + tiltB.y)
                val endB = Offset(startB.x + w * 0.8f, startB.y - h * 0.5f)
                val pastelBrush = Brush.linearGradient(pastelBand, startB, endB)

                val spotBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.45f),
                        Color.White.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(w * cardCenterFractionX, h * cardCenterFractionY),
                    radius = w * 0.55f
                )

                onDrawBehind {
                    drawRect(rainbowBrush, size = Size(w, h), alpha = 0.30f, blendMode = BlendMode.Plus)
                    drawRect(pastelBrush, size = Size(w, h), alpha = 0.35f, blendMode = BlendMode.Plus)
                    drawRect(spotBrush, size = Size(w, h), blendMode = BlendMode.Plus)
                }
            }
    )
}

@Composable
private fun BarcodeStrip(
    matrikel: String,
    barcode: androidx.compose.ui.graphics.ImageBitmap
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
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
