package dev.rafaelm.pandora.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.rafaelm.pandora.R
import dev.rafaelm.pandora.data.ServerStatus

@Composable
fun WelcomeScreen(
    state: WelcomeState,
    onOpenSettings: (() -> Unit)? = null,
    showModulesHint: Boolean = false,
    onRetry: () -> Unit,
) {
    PandoraPage(stringResource(R.string.nav_home), onOpenSettings = onOpenSettings) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.padding(PandoraSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.small)) {
                Image(
                    painter = painterResource(R.drawable.ic_pandora),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(PandoraSpacing.small))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium)
                Text(stringResource(R.string.welcome_subtitle), style = MaterialTheme.typography.bodyLarge)
            }
        }
        // With every module off, Home is the only page, so point new users to the switches.
        if (showModulesHint && onOpenSettings != null) {
            PandoraNotice(stringResource(R.string.modules_hint_title), stringResource(R.string.modules_hint_body)) {
                FilledTonalButton(onClick = onOpenSettings, modifier = Modifier.testTag("modules_hint_open_settings")) {
                    Text(stringResource(R.string.open_settings))
                }
            }
        }
        PandoraSection(stringResource(R.string.server_title)) {
            when (state) {
                WelcomeState.Loading -> PandoraListItem(stringResource(R.string.connecting),
                    leading = { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp) })
                is WelcomeState.Ready -> {
                    ListItem(
                        overlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.small)) {
                                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                Text(stringResource(R.string.connected), color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        headlineContent = { Text(state.status.message, style = MaterialTheme.typography.titleLarge) },
                        supportingContent = { Text(stringResource(R.string.version, state.status.version)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    FilledTonalButton(onClick = onRetry, modifier = Modifier.padding(horizontal = PandoraSpacing.large)) {
                        Text(stringResource(R.string.refresh))
                    }
                }
                WelcomeState.Error -> {
                    PandoraListItem(stringResource(R.string.connection_error))
                    Button(onClick = onRetry, modifier = Modifier.padding(horizontal = PandoraSpacing.large)) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Welcome — light")
@Composable
private fun WelcomeLightPreview() {
    PandoraTheme(darkTheme = false) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            WelcomeScreen(WelcomeState.Ready(ServerStatus("Pandora", "Welcome to Pandora", "0.1.0"))) {}
        }
    }
}

@Preview(showBackground = true, name = "Welcome — dark")
@Composable
private fun WelcomeDarkPreview() {
    PandoraTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            WelcomeScreen(WelcomeState.Ready(ServerStatus("Pandora", "Welcome to Pandora", "0.1.0"))) {}
        }
    }
}
