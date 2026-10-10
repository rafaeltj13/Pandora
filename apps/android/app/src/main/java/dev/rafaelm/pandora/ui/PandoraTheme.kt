package dev.rafaelm.pandora.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rafaelm.pandora.data.ThemeMode
import dev.rafaelm.pandora.data.CatTheme

// Cream coat, taupe markings, blue eyes. Each appearance owns its role values;
// screens consume MaterialTheme rather than choosing colors themselves.
internal val PandoraLightColors = lightColorScheme(
    primary = Color(0xFF315F85),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0E5F8),
    onPrimaryContainer = Color(0xFF10344F),
    inversePrimary = Color(0xFFA0CBEF),
    secondary = Color(0xFF6F5D4D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1DFCD),
    onSecondaryContainer = Color(0xFF514133),
    tertiary = Color(0xFF62634B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8E5C9),
    onTertiaryContainer = Color(0xFF454630),
    background = Color(0xFFFFF8EF),
    onBackground = Color(0xFF302B27),
    surface = Color(0xFFFFF8EF),
    onSurface = Color(0xFF302B27),
    surfaceVariant = Color(0xFFECE1D5),
    onSurfaceVariant = Color(0xFF665B51),
    surfaceTint = Color(0xFF315F85),
    inverseSurface = Color(0xFF35302C),
    inverseOnSurface = Color(0xFFF9EFE5),
    outline = Color(0xFF82766B),
    outlineVariant = Color(0xFFD2C5B8),
    scrim = Color(0xFF000000),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceBright = Color(0xFFFFF8EF),
    surfaceDim = Color(0xFFE3D8CD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF2E7),
    surfaceContainer = Color(0xFFF6EBDD),
    surfaceContainerHigh = Color(0xFFF0E5D8),
    surfaceContainerHighest = Color(0xFFEADFD2),
)

internal val PandoraDarkColors = darkColorScheme(
    primary = Color(0xFFA0CBEF),
    onPrimary = Color(0xFF12334C),
    primaryContainer = Color(0xFF294B66),
    onPrimaryContainer = Color(0xFFD0E5F8),
    inversePrimary = Color(0xFF315F85),
    secondary = Color(0xFFD5BFA9),
    onSecondary = Color(0xFF3D2E21),
    secondaryContainer = Color(0xFF554435),
    onSecondaryContainer = Color(0xFFF1DFCD),
    tertiary = Color(0xFFCBC9AD),
    onTertiary = Color(0xFF333520),
    tertiaryContainer = Color(0xFF4B4D36),
    onTertiaryContainer = Color(0xFFE8E5C9),
    background = Color(0xFF1A1714),
    onBackground = Color(0xFFF0E5DA),
    surface = Color(0xFF1A1714),
    onSurface = Color(0xFFF0E5DA),
    surfaceVariant = Color(0xFF4E443B),
    onSurfaceVariant = Color(0xFFD3C5B8),
    surfaceTint = Color(0xFFA0CBEF),
    inverseSurface = Color(0xFFF0E5DA),
    inverseOnSurface = Color(0xFF35302C),
    outline = Color(0xFF9C8F82),
    outlineVariant = Color(0xFF4E443B),
    scrim = Color(0xFF000000),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceBright = Color(0xFF413A33),
    surfaceDim = Color(0xFF1A1714),
    surfaceContainerLowest = Color(0xFF14110F),
    surfaceContainerLow = Color(0xFF221D19),
    surfaceContainer = Color(0xFF27221D),
    surfaceContainerHigh = Color(0xFF322B25),
    surfaceContainerHighest = Color(0xFF3D342C),
)

// Otto’s black-and-white coat gives both appearances a neutral palette.
internal val OttoLightColors = PandoraLightColors.copy(
    primary = Color(0xFF202020),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E2E2),
    onPrimaryContainer = Color(0xFF171717),
    inversePrimary = Color(0xFFEEEEEE),
    secondary = Color(0xFF555555),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E8E8),
    onSecondaryContainer = Color(0xFF242424),
    tertiary = Color(0xFF606060),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFECECEC),
    onTertiaryContainer = Color(0xFF292929),
    background = Color(0xFFFCFCFC),
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFFCFCFC),
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFE4E4E4),
    onSurfaceVariant = Color(0xFF555555),
    surfaceTint = Color(0xFF202020),
    inverseSurface = Color(0xFF303030),
    inverseOnSurface = Color(0xFFF4F4F4),
    outline = Color(0xFF747474),
    outlineVariant = Color(0xFFC6C6C6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFCFCFC),
    surfaceDim = Color(0xFFDADADA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F6F6),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFE4E4E4),
)

internal val OttoDarkColors = PandoraDarkColors.copy(
    primary = Color(0xFFF0F0F0),
    onPrimary = Color(0xFF171717),
    primaryContainer = Color(0xFF404040),
    onPrimaryContainer = Color(0xFFF0F0F0),
    inversePrimary = Color(0xFF202020),
    secondary = Color(0xFFC6C6C6),
    onSecondary = Color(0xFF292929),
    secondaryContainer = Color(0xFF414141),
    onSecondaryContainer = Color(0xFFE8E8E8),
    tertiary = Color(0xFFCDCDCD),
    onTertiary = Color(0xFF303030),
    tertiaryContainer = Color(0xFF464646),
    onTertiaryContainer = Color(0xFFECECEC),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF404040),
    onSurfaceVariant = Color(0xFFC6C6C6),
    surfaceTint = Color(0xFFF0F0F0),
    inverseSurface = Color(0xFFE4E4E4),
    inverseOnSurface = Color(0xFF303030),
    outline = Color(0xFF949494),
    outlineVariant = Color(0xFF404040),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF383838),
    surfaceDim = Color(0xFF0A0A0A),
    surfaceContainerLowest = Color(0xFF050505),
    surfaceContainerLow = Color(0xFF111111),
    surfaceContainer = Color(0xFF171717),
    surfaceContainerHigh = Color(0xFF202020),
    surfaceContainerHighest = Color(0xFF2A2A2A),
)

internal fun CatTheme.colors(dark: Boolean): ColorScheme = when (this) {
    CatTheme.PANDORA -> if (dark) PandoraDarkColors else PandoraLightColors
    CatTheme.OTTO -> if (dark) OttoDarkColors else OttoLightColors
}

internal val PandoraTypography = Typography().let { defaults ->
    defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontSize = 56.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold),
        displayMedium = defaults.displayMedium.copy(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold),
        headlineLarge = defaults.headlineLarge.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    )
}

internal val PandoraShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Layout scale in dp; small visual details may still use their own dimensions. */
object PandoraSpacing {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 24.dp
    val section = 32.dp
}

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PandoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    theme: CatTheme = CatTheme.PANDORA,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = theme.colors(darkTheme),
        typography = PandoraTypography,
        shapes = PandoraShapes,
        content = content,
    )
}
