package com.renpytool.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.renpytool.AudioCompressor
import com.renpytool.VideoCompressor
import com.renpytool.CompressionSettings
import androidx.compose.foundation.layout.FlowRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompressionSettingsDialog(
    initialSettings: CompressionSettings,
    onConfirm: (CompressionSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var imageQuality by remember { mutableIntStateOf(initialSettings.imageQuality) }
    var imageLossless by remember { mutableStateOf(initialSettings.imageLossless) }
    var imageMethod by remember { mutableIntStateOf(initialSettings.imageMethod) }
    var skipImages by remember { mutableStateOf(initialSettings.skipImages) }

    var audioQuality by remember { mutableStateOf(initialSettings.audioQuality) }
    var skipAudio by remember { mutableStateOf(initialSettings.skipAudio) }

    var videoQuality by remember { mutableStateOf(initialSettings.videoQuality) }
    var skipVideo by remember { mutableStateOf(initialSettings.skipVideo) }

    var threads by remember { mutableIntStateOf(initialSettings.threads) }
    var createRpaAfter by remember { mutableStateOf(initialSettings.createRpaAfter) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compression Settings") },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(end = 12.dp) // Make room for scrollbar
                ) {
                // Image Settings Section
                Text(
                    "Image Compression",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = skipImages,
                        onCheckedChange = {
                            skipImages = it
                            errorMessage = null
                        }
                    )
                    Text("Skip Images")
                }

                if (!skipImages) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = imageLossless,
                            onCheckedChange = { imageLossless = it }
                        )
                        Text("Lossless")
                    }

                    if (!imageLossless) {
                        Text("Quality: $imageQuality", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = imageQuality.toFloat(),
                            onValueChange = { imageQuality = it.toInt() },
                            valueRange = 1f..100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text("Speed:", style = MaterialTheme.typography.bodyMedium)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = imageMethod <= 2,
                            onClick = { imageMethod = 0 },
                            label = { Text("Fast") },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        FilterChip(
                            selected = imageMethod in 3..5,
                            onClick = { imageMethod = 4 },
                            label = { Text("Average") },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        FilterChip(
                            selected = imageMethod >= 6,
                            onClick = { imageMethod = 6 },
                            label = { Text("Slow") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Audio Settings Section
                Text(
                    "Audio Compression",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = skipAudio,
                        onCheckedChange = {
                            skipAudio = it
                            errorMessage = null
                        }
                    )
                    Text("Skip Audio")
                }

                if (!skipAudio) {
                    Text("Quality:", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AudioCompressor.AudioQuality.values().forEach { quality ->
                            FilterChip(
                                selected = audioQuality == quality,
                                onClick = { audioQuality = quality },
                                label = { Text(quality.name) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Video Settings Section
                Text(
                    "Video Compression",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = skipVideo,
                        onCheckedChange = {
                            skipVideo = it
                            errorMessage = null
                        }
                    )
                    Text("Skip Video")
                }

                if (!skipVideo) {
                    Text("Quality:", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        VideoCompressor.VideoQuality.values().forEach { quality ->
                            FilterChip(
                                selected = videoQuality == quality,
                                onClick = { videoQuality = quality },
                                label = { Text(quality.name) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // General Settings
                Text(
                    "General",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text("Threads: $threads", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = threads.toFloat(),
                    onValueChange = { threads = it.toInt() },
                    valueRange = 1f..Runtime.getRuntime().availableProcessors().toFloat(),
                    steps = Runtime.getRuntime().availableProcessors() - 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = createRpaAfter,
                        onCheckedChange = { createRpaAfter = it }
                    )
                    Text("Create RPA after compression")
                }

                // Error message display
                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Custom scrollbar indicator
            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                scrollState = scrollState
            )
        }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val settings = CompressionSettings(
                        imageQuality = imageQuality,
                        imageLossless = imageLossless,
                        imageMethod = imageMethod,
                        skipImages = skipImages,
                        audioQuality = audioQuality,
                        skipAudio = skipAudio,
                        videoQuality = videoQuality,
                        skipVideo = skipVideo,
                        threads = threads,
                        createRpaAfter = createRpaAfter
                    )

                    // Validate that at least one media type is enabled
                    if (!settings.hasEnabledTypes()) {
                        errorMessage = "Please enable at least one media type (Images, Audio, or Video)"
                    } else {
                        errorMessage = null
                        onConfirm(settings)
                    }
                }
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun VerticalScrollbar(
    modifier: Modifier = Modifier,
    scrollState: ScrollState
) {
    // Only show scrollbar if content is scrollable
    if (scrollState.maxValue > 0) {
        Box(
            modifier = modifier
                .width(8.dp)
                .drawWithContent {
                    drawContent()

                    val scrollbarHeight = 120f  // Increased for better visibility
                    val scrollbarWidth = 6f
                    val viewportHeight = size.height

                    // Calculate scrollbar position
                    val scrollFraction = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
                    val scrollbarY = scrollFraction * (viewportHeight - scrollbarHeight)

                    // Draw scrollbar
                    drawRoundRect(
                        color = Color.Gray.copy(alpha = 0.5f),
                        topLeft = Offset(size.width - scrollbarWidth - 2f, scrollbarY),
                        size = Size(scrollbarWidth, scrollbarHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(scrollbarWidth / 2)
                    )
                }
        )
    }
}
