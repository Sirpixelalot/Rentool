package com.renpytool.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Warning dialog shown when user selects random key option
 */
@Composable
fun RandomKeyWarningDialog(
    onContinue: () -> Unit,
    onGoBack: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onGoBack,
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Warning",
                tint = Color(0xFFFFA726) // Orange warning color
            )
        },
        title = {
            Text("Warning: Random Key")
        },
        text = {
            Text(
                "You are using a one-time random key. Apps signed with random keys cannot be updated later.\n\n" +
                        "Users will have to uninstall and reinstall to get new versions, losing all app data.\n\n" +
                        "Consider creating a persistent key instead if you plan to release updates."
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text("Continue Anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onGoBack) {
                Text("Go Back")
            }
        }
    )
}
