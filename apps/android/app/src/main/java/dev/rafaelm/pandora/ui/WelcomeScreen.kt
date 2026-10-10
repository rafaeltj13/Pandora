package dev.rafaelm.pandora.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.rafaelm.pandora.R
import dev.rafaelm.pandora.data.ServerStatus

@Composable
fun WelcomeScreen(state: WelcomeState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(PandoraSpacing.extraLarge),
    ) {
        Spacer(Modifier.height(PandoraSpacing.section))
        Text("P", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(PandoraSpacing.extraLarge))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(PandoraSpacing.medium))
        Text(stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(PandoraSpacing.section))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(PandoraSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.large)) {
                when (state) {
                    WelcomeState.Loading -> {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Text(stringResource(R.string.connecting))
                    }
                    is WelcomeState.Ready -> {
                        Row {
                            Box(Modifier.padding(top = 6.dp).size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape))
                            Spacer(Modifier.width(PandoraSpacing.medium))
                            Text(stringResource(R.string.connected),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge)
                        }
                        Text(state.status.message, style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.version, state.status.version),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onRetry) { Text(stringResource(R.string.refresh)) }
                    }
                    WelcomeState.Error -> {
                        Text(stringResource(R.string.connection_error),
                            style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
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
