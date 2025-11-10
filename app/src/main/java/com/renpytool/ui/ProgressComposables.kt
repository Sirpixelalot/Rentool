package com.renpytool.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renpytool.viewmodel.ProgressUiState
import java.util.Locale

/**
 * Main progress screen showing real-time operation progress
 */
@Composable
fun ProgressScreen(
    uiState: ProgressUiState,
    onDecompileClick: () -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Operation type
            Text(
                text = uiState.operationType,
                style = MaterialTheme.typography.headlineSmall,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Percentage display
            Text(
                text = "${uiState.percentage}%",
                style = MaterialTheme.typography.displayLarge,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Progress bar
            LinearProgressIndicator(
                progress = { uiState.percentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // File count
            ProgressInfoRow(
                label = "Files:",
                value = uiState.fileCount
            )

            // Current file
            ProgressInfoRow(
                label = "Current:",
                value = uiState.currentFile,
                maxLines = 2
            )

            // Speed
            ProgressInfoRow(
                label = "Speed:",
                value = uiState.speed
            )

            // ETA
            ProgressInfoRow(
                label = "ETA:",
                value = uiState.eta
            )
        }

        // Completion dialogs
        if (uiState.isCompleted) {
            if (uiState.operation == "extract" && uiState.rpycCount > 0 && uiState.extractPath != null) {
                ExtractCompletionDialog(
                    totalFiles = uiState.totalFiles,
                    extractPath = uiState.extractPath,
                    rpycCount = uiState.rpycCount,
                    onDecompileClick = onDecompileClick,
                    onDoneClick = onDoneClick
                )
            } else {
                SuccessDialog(
                    totalFiles = uiState.totalFiles,
                    elapsedMs = uiState.elapsedMs,
                    onDoneClick = onDoneClick
                )
            }
        } else if (uiState.isFailed) {
            ErrorDialog(
                errorMessage = uiState.errorMessage ?: "Operation failed",
                onDoneClick = onDoneClick
            )
        }
    }
}

@Composable
private fun ProgressInfoRow(
    label: String,
    value: String,
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = maxLines
        )
    }
}

@Composable
private fun ExtractCompletionDialog(
    totalFiles: Int,
    extractPath: String,
    rpycCount: Int,
    onDecompileClick: () -> Unit,
    onDoneClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text("Extraction Complete")
        },
        text = {
            Text(
                String.format(
                    Locale.US,
                    "Extracted %d files successfully!\n\n📁 %s\n\nFound %d .rpyc files ready to decompile.",
                    totalFiles,
                    extractPath,
                    rpycCount
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onDecompileClick) {
                Text("Decompile Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDoneClick) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun SuccessDialog(
    totalFiles: Int,
    elapsedMs: Long,
    onDoneClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text("Success")
        },
        text = {
            Text(
                String.format(
                    Locale.US,
                    "Operation completed successfully!\n\nProcessed %d files in %s",
                    totalFiles,
                    formatTime(elapsedMs)
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onDoneClick) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun ErrorDialog(
    errorMessage: String,
    onDoneClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text("Error")
        },
        text = {
            Text(errorMessage)
        },
        confirmButton = {
            TextButton(onClick = onDoneClick) {
                Text("OK")
            }
        }
    )
}

private fun formatTime(ms: Long): String {
    val seconds = ms / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
