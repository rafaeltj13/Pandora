package dev.rafaelm.pandora.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.rafaelm.pandora.R

@Composable
fun WelcomeScreen(
    onOpenSettings: (() -> Unit)? = null,
    showModulesHint: Boolean = false,
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
    }
}

@Preview(showBackground = true, name = "Welcome — light")
@Composable
private fun WelcomeLightPreview() {
    PandoraTheme(darkTheme = false) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            WelcomeScreen()
        }
    }
}

@Preview(showBackground = true, name = "Welcome — dark")
@Composable
private fun WelcomeDarkPreview() {
    PandoraTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            WelcomeScreen()
        }
    }
}
