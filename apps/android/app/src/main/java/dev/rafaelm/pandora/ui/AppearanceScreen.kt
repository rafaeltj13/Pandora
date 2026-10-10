package dev.rafaelm.pandora.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.rafaelm.pandora.R
import dev.rafaelm.pandora.data.AppearancePreferences
import dev.rafaelm.pandora.data.CatTheme
import dev.rafaelm.pandora.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    appearance: AppearancePreferences,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemeChange: (CatTheme, Boolean) -> Unit,
    onBothThemesChange: (CatTheme) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("appearance_back")) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back_to_settings))
                    }
                },
            )
        },
    ) { insets ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(insets),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            BoxWithConstraints {
                // Three visual tiles and a two-column theme grid at normal phone sizes.
                // Larger text and narrow windows get a vertical layout instead.
                val compact = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.5f
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PandoraSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(PandoraSpacing.extraLarge),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(PandoraSpacing.medium)) {
                        SectionLabel(stringResource(R.string.color_scheme_title))
                        if (compact) {
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.small)) {
                                ThemeMode.entries.forEach { mode ->
                                    ModeCard(mode, appearance, onThemeModeChange, Modifier.fillMaxWidth(), compact = true)
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.small)) {
                                ThemeMode.entries.forEach { mode ->
                                    ModeCard(mode, appearance, onThemeModeChange, Modifier.weight(1f))
                                }
                            }
                        }
                        Text(stringResource(R.string.theme_system_description),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(PandoraSpacing.medium)) {
                        SectionLabel(stringResource(R.string.themes_title))
                        if (compact) {
                            CatTheme.entries.forEach { theme ->
                                ThemeCard(theme, appearance, onThemeChange, onBothThemesChange, Modifier.fillMaxWidth())
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.medium)) {
                                CatTheme.entries.forEach { theme ->
                                    ThemeCard(theme, appearance, onThemeChange, onBothThemesChange, Modifier.weight(1f))
                                }
                            }
                        }
                        Text(stringResource(R.string.themes_description), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(PandoraSpacing.medium)) {
                        SectionLabel(stringResource(R.string.text_preview_title), accent = true)
                        Card(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(PandoraSpacing.large), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.small)) {
                                Text(stringResource(R.string.text_preview_sample), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.text_preview_description), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, accent: Boolean = false) {
    Text(text, style = MaterialTheme.typography.labelLarge,
        color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PandoraSpacing.extraSmall))
}

@Composable
private fun ModeCard(
    mode: ThemeMode,
    appearance: AppearancePreferences,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier,
    compact: Boolean = false,
) {
    val selected = mode == appearance.mode
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(mode) })
            .testTag("theme_${mode.name.lowercase()}"),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        val label: @Composable () -> Unit = {
            Text(stringResource(mode.label()), style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (compact) {
            Row(Modifier.padding(PandoraSpacing.large), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.large)) {
                PhonePreview(mode, appearance, Modifier.width(32.dp).height(56.dp))
                label()
            }
        } else {
            Column(Modifier.padding(vertical = PandoraSpacing.large, horizontal = PandoraSpacing.small),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PandoraSpacing.medium)) {
                PhonePreview(mode, appearance, Modifier.width(48.dp).height(84.dp))
                label()
            }
        }
    }
}

/** Decorative miniature screens use the actual selected palettes. */
@Composable
private fun PhonePreview(mode: ThemeMode, appearance: AppearancePreferences, modifier: Modifier) {
    val light = appearance.lightTheme.colors(false)
    val dark = appearance.darkTheme.colors(true)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color.Black, cornerRadius = CornerRadius(w * 0.3f))
        val screen = Path().apply {
            addRoundRect(RoundRect(w * 0.07f, h * 0.045f, w * 0.93f, h * 0.955f, CornerRadius(w * 0.23f)))
        }
        clipPath(screen) {
            when (mode) {
                ThemeMode.LIGHT -> homePreviewContent(light)
                ThemeMode.DARK -> homePreviewContent(dark)
                ThemeMode.SYSTEM -> {
                    clipRect(right = w / 2) { homePreviewContent(light) }
                    clipRect(left = w / 2) { homePreviewContent(dark) }
                }
            }
        }
        drawRoundRect(Color.Gray.copy(alpha = 0.3f), cornerRadius = CornerRadius(w * 0.3f), style = Stroke(1.dp.toPx()))
    }
}

/** Home's monogram, heading, connection card, and navigation at miniature scale. */
private fun DrawScope.homePreviewContent(colors: ColorScheme) {
    val w = size.width
    val h = size.height
    drawRect(colors.background)

    // The P monogram above the Home heading.
    drawRect(colors.primary, Offset(w * 0.2f, h * 0.15f), Size(w * 0.035f, h * 0.12f))
    drawRoundRect(colors.primary, Offset(w * 0.2f, h * 0.15f), Size(w * 0.16f, h * 0.075f), CornerRadius(w * 0.045f))
    drawRoundRect(colors.background, Offset(w * 0.235f, h * 0.17f), Size(w * 0.09f, h * 0.035f), CornerRadius(w * 0.02f))
    drawRoundRect(colors.onBackground, Offset(w * 0.2f, h * 0.32f), Size(w * 0.46f, h * 0.04f), CornerRadius(w * 0.01f))
    drawRoundRect(colors.onSurfaceVariant, Offset(w * 0.2f, h * 0.39f), Size(w * 0.57f, h * 0.018f), CornerRadius(w * 0.01f))

    // One connection card with status, welcome message, version, and Refresh.
    drawRoundRect(colors.surfaceContainerHighest, Offset(w * 0.16f, h * 0.47f), Size(w * 0.68f, h * 0.3f), CornerRadius(w * 0.07f))
    drawCircle(colors.primary, radius = w * 0.018f, center = Offset(w * 0.24f, h * 0.515f))
    drawRoundRect(colors.primary, Offset(w * 0.29f, h * 0.505f), Size(w * 0.26f, h * 0.018f), CornerRadius(w * 0.01f))
    drawRoundRect(colors.onSurface, Offset(w * 0.22f, h * 0.56f), Size(w * 0.5f, h * 0.027f), CornerRadius(w * 0.01f))
    drawRoundRect(colors.onSurfaceVariant, Offset(w * 0.22f, h * 0.615f), Size(w * 0.22f, h * 0.016f), CornerRadius(w * 0.01f))
    drawRoundRect(colors.primary, Offset(w * 0.22f, h * 0.665f), Size(w * 0.3f, h * 0.06f), CornerRadius(w * 0.06f))
    drawRoundRect(colors.onPrimary, Offset(w * 0.27f, h * 0.69f), Size(w * 0.2f, h * 0.01f), CornerRadius(w * 0.01f))

    drawRect(colors.surfaceContainer, Offset(w * 0.07f, h * 0.83f), Size(w * 0.86f, h * 0.09f))
    drawRoundRect(colors.secondaryContainer, Offset(w * 0.135f, h * 0.84f), Size(w * 0.15f, h * 0.045f), CornerRadius(w * 0.06f))
    for (index in 0..3) {
        drawCircle(if (index == 0) colors.onSecondaryContainer else colors.onSurfaceVariant,
            radius = w * 0.025f, center = Offset(w * (0.21f + index * 0.19f), h * 0.863f))
    }
    drawRoundRect(colors.onSurfaceVariant, Offset(w * 0.36f, h * 0.935f), Size(w * 0.28f, h * 0.012f), CornerRadius(w * 0.02f))
}

@Composable
private fun ThemeCard(
    theme: CatTheme,
    appearance: AppearancePreferences,
    onThemeChange: (CatTheme, Boolean) -> Unit,
    onBothThemesChange: (CatTheme) -> Unit,
    modifier: Modifier,
) {
    Card(onClick = { onBothThemesChange(theme) }, modifier = modifier.testTag("palette_${theme.name.lowercase()}"),
        shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(PandoraSpacing.large), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.large)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.medium)) {
                ThemeSwatch(theme, false, appearance.lightTheme == theme, { onThemeChange(theme, false) }, Modifier.weight(1f))
                ThemeSwatch(theme, true, appearance.darkTheme == theme, { onThemeChange(theme, true) }, Modifier.weight(1f))
            }
            Text(stringResource(theme.label()), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ThemeSwatch(theme: CatTheme, dark: Boolean, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = theme.colors(dark)
    val description = stringResource(R.string.theme_preview, stringResource(theme.label()),
        stringResource(if (dark) R.string.theme_dark else R.string.theme_light))
    val badgeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val badgeForeground = MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier.aspectRatio(1f).selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag("palette_${theme.name.lowercase()}_${if (dark) "dark" else "light"}"),
        shape = CircleShape, color = Color.Transparent,
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box(Modifier.padding(6.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(colors.surfaceContainerHighest)
                drawCircle(Brush.radialGradient(listOf(colors.primary.copy(alpha = 0.85f), colors.primary.copy(alpha = 0f)),
                    center = Offset(size.width * 0.3f, size.height * 0.3f), radius = size.width * 0.8f))
                drawCircle(Brush.radialGradient(listOf(colors.secondaryContainer, colors.secondaryContainer.copy(alpha = 0f)),
                    center = Offset(size.width * 0.85f, size.height * 0.9f), radius = size.width * 0.7f))
                drawCircle(colors.outlineVariant, style = Stroke(1.dp.toPx()))
            }
            Canvas(Modifier.align(Alignment.BottomEnd).size(22.dp)) {
                drawCircle(badgeBackground)
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.width * 0.24f
                if (dark) {
                    drawCircle(badgeForeground, radius, center)
                    drawCircle(badgeBackground, radius * 0.9f, center + Offset(radius * 0.6f, -radius * 0.35f))
                } else {
                    drawCircle(badgeForeground, radius * 0.55f, center, style = Stroke(1.2.dp.toPx()))
                    for (ray in 0..7) {
                        val angle = ray * Math.PI / 4
                        val direction = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
                        drawLine(badgeForeground, center + direction * radius * 0.85f,
                            center + direction * radius * 1.2f, strokeWidth = 1.2.dp.toPx())
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Appearance — Pandora")
@Composable
private fun PandoraAppearancePreview() {
    PandoraTheme(darkTheme = true) {
        AppearanceScreen(AppearancePreferences(mode = ThemeMode.DARK), {}, { _, _ -> }, {}, {})
    }
}

@Preview(showBackground = true, name = "Appearance — Otto")
@Composable
private fun OttoAppearancePreview() {
    PandoraTheme(darkTheme = false, theme = CatTheme.OTTO) {
        AppearanceScreen(AppearancePreferences(mode = ThemeMode.LIGHT, lightTheme = CatTheme.OTTO, darkTheme = CatTheme.OTTO),
            {}, { _, _ -> }, {}, {})
    }
}
