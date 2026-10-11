package dev.rafaelm.pandora.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.rafaelm.pandora.R

/** Slimmer than the Material default (64dp) so pages start closer to the status bar. */
private val TopBarHeight = 52.dp

/** Height of the floating dock drawn over the bottom of the page; 0 when it is hidden. */
internal val LocalDockClearance = staticCompositionLocalOf { 0.dp }

/**
 * Page frame shared by every screen: a top app bar over a rounded content sheet
 * that runs to the bottom edge, under the dock. [content] receives the bottom padding
 * its scrolling container needs to clear the dock and the navigation bar.
 * Top-level pages pass [onOpenSettings] to show the Settings button at the end of the bar.
 * Groups inside the sheet use [PandoraSection] and rows use [PandoraListItem].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PandoraScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    content: @Composable (bottom: Dp) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(title) }, navigationIcon = navigationIcon, expandedHeight = TopBarHeight, actions = {
                actions()
                if (onOpenSettings != null) {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings")) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.nav_settings))
                    }
                }
            })
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { insets ->
        val bottom = LocalDockClearance.current + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Surface(
            modifier = Modifier.fillMaxSize().padding(insets),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)),
        ) { content(bottom) }
    }
}

/** Scrollable [PandoraScaffold] for pages with a short, fixed set of sections. */
@Composable
internal fun PandoraPage(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    PandoraScaffold(title, modifier, navigationIcon, onOpenSettings = onOpenSettings) { bottom ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PandoraSpacing.large).padding(bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(PandoraSpacing.extraLarge),
            content = content,
        )
    }
}

/** A labelled group of rows on one tonal container, as in system settings. */
@Composable
internal fun PandoraSection(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.small)) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = PandoraSpacing.large))
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(Modifier.padding(vertical = PandoraSpacing.small), content = content)
        }
    }
}

/** A row inside [PandoraSection]. Clickable rows without trailing content show a chevron. */
@Composable
internal fun PandoraListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    @DrawableRes icon: Int? = null,
    leading: (@Composable () -> Unit)? = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(headline, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = leading,
        trailingContent = trailing ?: onClick?.let {
            { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

/** A tonal card with a title, an explanation, and its actions stacked below. */
@Composable
internal fun PandoraNotice(title: String, body: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(PandoraSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.medium)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
