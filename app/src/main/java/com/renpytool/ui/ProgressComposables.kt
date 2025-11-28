package com.renpytool.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renpytool.ProgressData
import com.renpytool.viewmodel.ProgressUiState
import java.util.Locale

/**
 * Format bytes to human-readable string
 */
private fun formatBytes(bytes: Long): String = ProgressData.formatFileSize(bytes)

/**
 * Main progress screen showing real-time operation progress
 */
@Composable
fun ProgressScreen(
    uiState: ProgressUiState,
    onDecompileClick: () -> Unit,
    onDoneClick: () -> Unit,
    onCancelClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
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

            // Percentage display with pulse animation
            val infiniteTransition = rememberInfiniteTransition(label = "percentage_pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.98f,
                targetValue = 1.02f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )

            Text(
                text = "${uiState.percentage}%",
                style = MaterialTheme.typography.displayLarge,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = if (!uiState.isCompleted && !uiState.isFailed) {
                    Modifier.scale(pulseScale)
                } else {
                    Modifier
                }
            )

            // Progress bar with animated progress
            val animatedProgress by animateFloatAsState(
                targetValue = uiState.percentage / 100f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "progress_animation"
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
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

            // Compression-specific stats
            if (uiState.operation.startsWith("compress")) {
                if (uiState.originalSizeBytes > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))

                    ProgressInfoRow(
                        label = "Original Size:",
                        value = formatBytes(uiState.originalSizeBytes)
                    )

                    ProgressInfoRow(
                        label = "Compressed:",
                        value = formatBytes(uiState.compressedSizeBytes)
                    )

                    if (uiState.compressedSizeBytes > 0) {
                        val ratio = ((uiState.originalSizeBytes - uiState.compressedSizeBytes).toDouble()
                            / uiState.originalSizeBytes * 100.0)
                        ProgressInfoRow(
                            label = "Reduction:",
                            value = String.format(Locale.US, "%.1f%%", ratio)
                        )
                    }
                }
            }

            // Cancel button (only shown when operation is in progress)
            if (!uiState.isCompleted && !uiState.isFailed && onCancelClick != null) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showCancelDialog = true },
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel Operation")
                }
            }
        }

        // Cancel confirmation dialog
        if (showCancelDialog) {
            AlertDialog(
                onDismissRequest = { showCancelDialog = false },
                title = { Text("Cancel Operation?") },
                text = { Text("Are you sure you want to cancel this operation? Progress will be lost.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showCancelDialog = false
                            onCancelClick?.invoke()
                        }
                    ) {
                        Text("Cancel Operation")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCancelDialog = false }) {
                        Text("Continue")
                    }
                }
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
            } else if (uiState.operation.startsWith("compress")) {
                CompressionSuccessDialog(
                    totalFiles = uiState.totalFiles,
                    elapsedMs = uiState.elapsedMs,
                    originalSizeBytes = uiState.originalSizeBytes,
                    compressedSizeBytes = uiState.compressedSizeBytes,
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
            modifier = Modifier.width(110.dp),
            maxLines = 1
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
private fun CompressionSuccessDialog(
    totalFiles: Int,
    elapsedMs: Long,
    originalSizeBytes: Long,
    compressedSizeBytes: Long,
    onDoneClick: () -> Unit
) {
    val reductionPercent = if (originalSizeBytes > 0) {
        ((originalSizeBytes - compressedSizeBytes).toDouble() / originalSizeBytes * 100.0)
    } else {
        0.0
    }

    AlertDialog(
        onDismissRequest = { },
        title = {
            Text("Compression Complete!")
        },
        text = {
            Column {
                Text(
                    String.format(
                        Locale.US,
                        "Successfully compressed %d files in %s",
                        totalFiles,
                        formatTime(elapsedMs)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Original Size: ${formatBytes(originalSizeBytes)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Compressed Size: ${formatBytes(compressedSizeBytes)}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    String.format(Locale.US, "Reduction: %.1f%%", reductionPercent),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDoneClick) {
                Text("OK")
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
