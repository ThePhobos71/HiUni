package de.transio.hiuni.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import de.transio.hiuni.R

private val GoogleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val PlusJakartaSans = GoogleFont("Plus Jakarta Sans")

internal val HiUniFontFamily = FontFamily(
    Font(googleFont = PlusJakartaSans, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = PlusJakartaSans, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = PlusJakartaSans, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = PlusJakartaSans, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold),
    Font(googleFont = PlusJakartaSans, fontProvider = GoogleFontsProvider, weight = FontWeight.ExtraBold),
)

private val defaultStyle = TextStyle(fontFamily = HiUniFontFamily, fontStyle = FontStyle.Normal)

internal val HiUniTypography = Typography(
    displayLarge = defaultStyle.copy(
        fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp
    ),
    displayMedium = defaultStyle.copy(
        fontSize = 25.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp
    ),
    headlineLarge = defaultStyle.copy(
        fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold
    ),
    headlineMedium = defaultStyle.copy(
        fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold
    ),
    titleLarge = defaultStyle.copy(
        fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold
    ),
    titleMedium = defaultStyle.copy(
        fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold
    ),
    titleSmall = defaultStyle.copy(
        fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp
    ),
    bodyLarge = defaultStyle.copy(
        fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal
    ),
    bodyMedium = defaultStyle.copy(
        fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal
    ),
    bodySmall = defaultStyle.copy(
        fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal
    ),
    labelLarge = defaultStyle.copy(
        fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold
    ),
    labelMedium = defaultStyle.copy(
        fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold
    ),
    labelSmall = defaultStyle.copy(
        fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp
    )
)
