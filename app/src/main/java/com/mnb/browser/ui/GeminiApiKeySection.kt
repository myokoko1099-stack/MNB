package com.mnb.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mnb.browser.settings.GeminiKeyMessage
import com.mnb.browser.settings.GeminiKeySettingsController
import com.mnb.browser.settings.GeminiKeyStatus
import com.mnb.browser.settings.isError

/**
 * Null (the default) means the settings panel has no Gemini key section and looks exactly as before
 * (Phase 06-A2).
 */
val LocalGeminiKeySettings = staticCompositionLocalOf<GeminiKeySettingsController?> { null }

/**
 * Gemini API key section of the settings panel (Phase 06-A2): shows whether a key is stored and
 * lets the user save, update, delete it, and show or hide what they are typing.
 *
 * Privacy rules this composable follows:
 * - The typed text is held in plain `remember`, never `rememberSaveable`, so it is not copied
 *   into saved instance state. It is cleared after every successful change.
 * - The stored key is never read back into the UI: the field always starts empty and the status
 *   line only says whether a key exists.
 * - Messages are chosen from fixed texts by code, so no error text (which could contain key
 *   material) is ever shown.
 */
@Composable
fun GeminiApiKeySection(controller: GeminiKeySettingsController, modifier: Modifier = Modifier) {
    val state = controller.state
    var input by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Every time the section appears (the panel is opened), check the secure storage again.
    LaunchedEffect(controller) { controller.refresh() }
    // After a successful save / update / delete the typed text goes away and is hidden again.
    LaunchedEffect(state.completedChanges) {
        input = ""
        visible = false
    }

    val hasKey = state.status == GeminiKeyStatus.SAVED || state.status == GeminiKeyStatus.UNREADABLE
    val replacing = state.status == GeminiKeyStatus.SAVED

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Gemini API Key", style = MaterialTheme.typography.titleSmall)
        Text(
            statusText(state.status),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.status == GeminiKeyStatus.UNREADABLE) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                controller.clearMessage()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
            singleLine = true,
            label = { Text(if (replacing) "New API key" else "API key") },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") }
            },
        )
        state.message?.let { message ->
            Text(
                messageText(message),
                style = MaterialTheme.typography.bodySmall,
                color = if (message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { controller.save(input) },
                enabled = input.isNotBlank() && !state.busy,
            ) { Text(if (replacing) "Update" else "Save") }
            OutlinedButton(
                onClick = { confirmDelete = true },
                enabled = hasKey && !state.busy,
            ) { Text("Delete") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Gemini API key?") },
            text = { Text("The saved key will be removed from this device. You can enter it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    controller.delete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun statusText(status: GeminiKeyStatus): String = when (status) {
    GeminiKeyStatus.CHECKING -> "Checking…"
    GeminiKeyStatus.NOT_SET -> "No key saved"
    GeminiKeyStatus.SAVED -> "Key saved (stored encrypted on this device)"
    GeminiKeyStatus.UNREADABLE ->
        "A key is stored but cannot be read. Enter the key again to replace it, or delete it."
}

private fun messageText(message: GeminiKeyMessage): String = when (message) {
    GeminiKeyMessage.SAVED -> "Key saved."
    GeminiKeyMessage.UPDATED -> "Key updated."
    GeminiKeyMessage.DELETED -> "Key deleted."
    GeminiKeyMessage.INVALID_INPUT -> "That is not a valid key. Paste it without spaces or line breaks."
    GeminiKeyMessage.SAVE_FAILED -> "The key could not be saved. Try again."
    GeminiKeyMessage.DELETE_FAILED -> "The key could not be deleted. Try again."
}
