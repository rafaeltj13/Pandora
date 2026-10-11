package dev.rafaelm.pandora.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import dev.rafaelm.pandora.R

/**
 * Page frame shared by every screen: a top app bar over a rounded content sheet.
 * Groups inside the sheet use [PandoraSection] and rows use [PandoraListItem].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PandoraScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = navigationIcon, actions = actions) },
    ) { insets ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(insets),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.extraLarge,
            content = content,
        )
    }
}

/** Scrollable [PandoraScaffold] for pages with a short, fixed set of sections. */
@Composable
internal fun PandoraPage(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    PandoraScaffold(title, modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PandoraSpacing.large),
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
