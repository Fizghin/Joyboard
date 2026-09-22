package com.joyboard.notchisland.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joyboard.notchisland.data.DEFAULT_UPDATE_MANIFEST_URL

/** Lets the update manifest live anywhere the phone can reach over HTTPS. */
@Composable
fun UrlDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update source") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "The address of a JSON manifest describing the newest build. A GitHub raw " +
                        "URL works only while the repository is public — otherwise point this at " +
                        "a gist, a release asset or your own host.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = false,
                    maxLines = 4,
                    label = { Text("Manifest URL") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                TextButton(
                    onClick = { value = DEFAULT_UPDATE_MANIFEST_URL },
                    modifier = Modifier.padding(top = 4.dp)
                ) { Text("Reset to default") }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
