package dev.rafaelm.pandora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.rafaelm.pandora.R

@Composable
fun ExploreScreen() {
    PandoraPage(stringResource(R.string.nav_explore)) {
        Column(Modifier.fillMaxWidth().padding(vertical = PandoraSpacing.section),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PandoraSpacing.medium)) {
            Icon(painterResource(R.drawable.ic_explore), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text(stringResource(R.string.explore_empty_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.explore_empty_body), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
