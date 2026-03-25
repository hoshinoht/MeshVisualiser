package com.meshvisualiser.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.meshvisualiser.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// Display/Headline/Title font
private val SpaceGrotesk = GoogleFont("Space Grotesk")
val DisplayFontFamily = FontFamily(
    Font(googleFont = SpaceGrotesk, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = SpaceGrotesk, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = SpaceGrotesk, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = SpaceGrotesk, fontProvider = provider, weight = FontWeight.Normal),
)

// Body option 1: JetBrains Mono (technical feel)
private val JetBrainsMono = GoogleFont("JetBrains Mono")
val JetBrainsMonoFamily = FontFamily(
    Font(googleFont = JetBrainsMono, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = JetBrainsMono, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = JetBrainsMono, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = JetBrainsMono, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = JetBrainsMono, fontProvider = provider, weight = FontWeight.Light),
)

// Body option 2: IBM Plex Sans (precision)
private val IbmPlexSans = GoogleFont("IBM Plex Sans")
val IbmPlexSansFamily = FontFamily(
    Font(googleFont = IbmPlexSans, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = IbmPlexSans, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = IbmPlexSans, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = IbmPlexSans, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = IbmPlexSans, fontProvider = provider, weight = FontWeight.Light),
)

// ── Toggle this line to compare fonts on device ──
val BodyFontFamily = JetBrainsMonoFamily
// val BodyFontFamily = IbmPlexSansFamily

val Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
