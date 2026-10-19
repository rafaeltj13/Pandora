package dev.rafaelm.pandora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import java.net.URLEncoder

/** Token entry is transient, masked, and excluded from screenshots and saved instance state. */
@Composable
internal fun GitHubUploadTokenDialog(
    state: DevelopmentState,
    actions: DevelopmentActions,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var token by remember { mutableStateOf("") }
    var replacing by remember { mutableStateOf(false) }
    val editing = !state.uploadTokenConfigured || replacing
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.uploadTokenVersion) {
        token = ""
        replacing = false
        if (state.uploadTokenConfigured) { focusManager.clearFocus(); keyboard?.hide() }
    }
    AlertDialog(
        onDismissRequest = { if (!state.uploadTokenSaving) onDismiss() },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(if (replacing) "Replace attachment token" else "Attachment token") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.medium)) {
                if (editing) {
                    Text("Image uploads need a separate fine-grained personal access token. Your GitHub connection continues to handle issues.")
                    Text("Choose your account as resource owner, select only the repositories you use in Pandora, and give Issues read and write access. Set an expiration date.")
                    TextButton(onClick = {
                        val owner = state.profile?.login?.let { "&target_name=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
                        onOpenUrl("https://github.com/settings/personal-access-tokens/new?name=Pandora%20image%20uploads&expires_in=30&issues=write$owner")
                    }) { Text("Create token on GitHub") }
                    if (!state.uploadTokenConfigured) Text("No attachment token saved.", style = MaterialTheme.typography.bodySmall)
                    if (!state.sessionAvailable) Text("Connect GitHub in Development before saving an attachment token.")
                    OutlinedTextField(token, { token = it }, label = { Text("Fine-grained token") },
                        singleLine = true, enabled = state.sessionAvailable && !state.uploadTokenSaving,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().testTag("attachment_token_input"))
                } else {
                    Text("Token saved", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Pandora will use the saved token for image uploads.")
                    TextButton(onClick = { replacing = true }, enabled = !state.uploadTokenSaving) { Text("Replace token") }
                }
                Text("Encrypted on this device and used only for image uploads. Removing it here does not revoke it on GitHub.",
                    style = MaterialTheme.typography.bodySmall)
                if (state.uploadTokenSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.uploadTokenError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.uploadTokenConfigured) TextButton(onClick = { token = ""; actions.clearUploadToken() },
                    enabled = !state.uploadTokenSaving, modifier = Modifier.testTag("remove_attachment_token")) { Text("Remove token") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (editing) actions.setUploadToken(token) else onDismiss()
            }, enabled = !state.uploadTokenSaving && (!editing || (token.isNotBlank() && state.sessionAvailable)), modifier = Modifier.testTag("save_attachment_token")) {
                Text(if (editing) "Save token" else "Done")
            }
        },
        dismissButton = {
            if (editing) TextButton(onClick = {
                if (state.uploadTokenConfigured) {
                    token = ""; replacing = false; focusManager.clearFocus(); keyboard?.hide()
                } else onDismiss()
            }, enabled = !state.uploadTokenSaving) { Text("Cancel") }
        },
    )
}
