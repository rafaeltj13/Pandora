package dev.rafaelm.pandora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.rafaelm.pandora.R

@Composable
fun ExploreScreen() {
    PandoraPage(stringResource(R.string.nav_explore), stringResource(R.string.explore_subtitle)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(PandoraSpacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(PandoraSpacing.large)) {
                Text(stringResource(R.string.explore_empty_title),
                    style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.explore_empty_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
