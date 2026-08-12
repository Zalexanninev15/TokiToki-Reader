package io.github.zalexanninev15.tokitoki.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

enum class FontSize(val scale: Float) {
    SMALL(0.85f),
    NORMAL(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f),
}

private val Lilac = Color(0xFF7B5CD6)
private val Blossom = Color(0xFFFFB4C7)
private val Straw = Color(0xFFFFE9A8)

private val LightScheme = lightColorScheme(
    primary = Lilac,
    secondary = Blossom,
    tertiary = Color(0xFFB08900),
    background = Color(0xFFFDFBFF),
    surface = Color(0xFFFDFBFF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFC7B4FF),
    secondary = Blossom,
    tertiary = Straw,
    background = Color(0xFF12101A),
    surface = Color(0xFF12101A),
    surfaceVariant = Color(0xFF241E33),
)

/**
 * True black, not "dark grey with the lights off".
 *
 * The point of an AMOLED theme is that a #000000 pixel is switched off, so surfaces and
 * background are both pinned to black rather than to Material's elevated greys. Card
 * separation then has to come from outline colour instead of elevation tint.
 */
private val AmoledScheme = DarkScheme.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainer = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerHigh = Color(0xFF0A0A0A),
    surfaceContainerHighest = Color(0xFF121212),
    surfaceVariant = Color(0xFF141414),
    outline = Color(0xFF4A4458),
    outlineVariant = Color(0xFF2A2632),
)

private fun scaledTypography(scale: Float): Typography {
    fun style(size: Float, height: Float, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontSize = (size * scale).sp,
        lineHeight = (height * scale).sp,
        fontWeight = weight,
    )
    return Typography(
        // Only the text styles used for post content scale; titles and labels stay put so
        // that a larger reading size does not push the top bar or tabs out of shape.
        bodyLarge = style(16f, 24f),
        bodyMedium = style(14f, 20f),
        bodySmall = style(12f, 16f),
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun TokiTokiTheme(
    themeMode: ThemeMode,
    fontSize: FontSize,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }

    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val scheme: ColorScheme = when {
        // Dynamic colour never applies to AMOLED: the whole point is a black background,
        // and the wallpaper palette would put a tinted surface back.
        themeMode == ThemeMode.AMOLED -> AmoledScheme
        dynamicColor && supportsDynamic && dark -> dynamicDarkColorScheme(context)
        dynamicColor && supportsDynamic -> dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = scaledTypography(fontSize.scale),
        content = content,
    )
}
