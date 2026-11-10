package com.crintsoft.zikiplayer.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.crintsoft.zikiplayer.model.Track

@Composable
fun DeleteSongDialog(
    track: Track,
    onDismiss: () -> Unit,
    onConfirmDelete: (deletePhysicalFile: Boolean) -> Unit
) {
    var deletePhysicalFile by remember { mutableStateOf(false) }

    val dialogTitle = if (deletePhysicalFile) {
        "Permanently Delete Audio File?"
    } else {
        "Remove from MiniLyrics?"
    }

    val actionDescription = if (deletePhysicalFile) {
        "The actual audio file will be permanently deleted from internal storage. This action cannot be undone."
    } else {
        "The song will be removed from the MiniLyrics library but its audio file will remain in internal storage."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = if (deletePhysicalFile) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = dialogTitle,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = actionDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Select deletion option:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option A: Remove from MiniLyrics (keep audio file)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { deletePhysicalFile = false }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !deletePhysicalFile,
                        onClick = { deletePhysicalFile = false },
                        modifier = Modifier.testTag("delete_library_only_radio")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Remove from MiniLyrics",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Removes the song and its records from MiniLyrics. Audio file remains in internal storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Option B: Permanently Delete from Internal Storage
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !track.isDemo) { deletePhysicalFile = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = deletePhysicalFile,
                        onClick = { if (!track.isDemo) deletePhysicalFile = true },
                        enabled = !track.isDemo,
                        modifier = Modifier.testTag("delete_physical_file_radio")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (track.isDemo) "Permanently Delete from Storage (Demo track)" else "Permanently Delete from Internal Storage",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (track.isDemo) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = if (track.isDemo) {
                                "Demo tracks are built into the app and cannot be deleted from disk. Use \"Remove from MiniLyrics\" instead."
                            } else {
                                "Deletes the physical audio file from your device. Cannot be recovered."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmDelete(deletePhysicalFile) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (deletePhysicalFile) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (deletePhysicalFile) "Permanently Delete" else "Remove from MiniLyrics")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
