package dev.rafaelm.pandora.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.rafaelm.pandora.data.CatTheme
import org.junit.Assert.assertTrue
import org.junit.Test

class PandoraPaletteTest {
    @Test
    fun themeTextMeetsNormalTextContrastAcrossSurfacesAndActions() {
        for (colors in CatTheme.entries.flatMap { listOf(it.colors(false), it.colors(true)) }) {
            val pairs = listOf(
                colors.onBackground to colors.background,
                colors.onSurface to colors.surface,
                colors.onSurfaceVariant to colors.surface,
                colors.onSurfaceVariant to colors.surfaceVariant,
                colors.onPrimary to colors.primary,
                colors.onPrimaryContainer to colors.primaryContainer,
                colors.onSecondary to colors.secondary,
                colors.onSecondaryContainer to colors.secondaryContainer,
                colors.onTertiary to colors.tertiary,
                colors.onTertiaryContainer to colors.tertiaryContainer,
                colors.onError to colors.error,
                colors.onErrorContainer to colors.errorContainer,
                colors.inverseOnSurface to colors.inverseSurface,
            ) + listOf(colors.surfaceContainerLowest, colors.surfaceContainerLow,
                colors.surfaceContainer, colors.surfaceContainerHigh, colors.surfaceContainerHighest)
                .flatMap { listOf(colors.onSurface to it, colors.onSurfaceVariant to it) }
            for ((text, surface) in pairs) {
                val ratio = contrast(text, surface)
                assertTrue("$text on $surface has contrast $ratio (requires 4.5)", ratio >= 4.5)
            }
            assertTrue("Primary actions must remain visible on the surface", contrast(colors.primary, colors.surface) >= 3)
            assertTrue("Input outlines must remain visible on the surface", contrast(colors.outline, colors.surface) >= 3)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val first = a.luminance().toDouble()
        val second = b.luminance().toDouble()
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }
}
