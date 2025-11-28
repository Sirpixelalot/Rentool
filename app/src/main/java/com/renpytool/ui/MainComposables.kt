package com.renpytool.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.DrawableRes
import kotlinx.coroutines.launch

/**
 * Decompile options dialog with Try Harder mode checkbox
 */
@Composable
fun DecompileOptionsDialogContent(
    onStart: (tryHarder: Boolean, dontShowAgain: Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tryHarderChecked by remember { mutableStateOf(false) }
    var dontShowAgainChecked by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            // Title
            Text(
                text = "Decompile Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Try Harder checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = tryHarderChecked,
                    onCheckedChange = { tryHarderChecked = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Try Harder Mode (slower, for obfuscated files)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            Text(
                text = "Try Harder Mode performs aggressive decompilation for heavily obfuscated files. " +
                       "This will take significantly longer.\n\n" +
                       "Use this only if default decompilation fails.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Don't show again checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = dontShowAgainChecked,
                    onCheckedChange = { dontShowAgainChecked = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Don't show this dialog again",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onStart(tryHarderChecked, dontShowAgainChecked) }) {
                    Text("Start")
                }
            }
        }
    }
}

/**
 * Reusable operation card component with color-coded status
 */
@Composable
fun OperationCard(
    title: String,
    statusText: String,
    @DrawableRes iconRes: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusType = StatusType.fromMessage(statusText)

    // Press animation state
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_scale"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Operation Icon
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = "$title icon",
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Status with color-coded background and icon
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = MaterialTheme.shapes.small,
                color = statusType.backgroundColor()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    statusType.icon?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = statusType.textColor()
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusType.textColor(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Main screen content with scrollable operation cards
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    extractStatus: String,
    createStatus: String,
    decompileStatus: String,
    editStatus: String,
    compressStatus: String,
    cardsEnabled: Boolean,
    onExtractClick: () -> Unit,
    onCreateClick: () -> Unit,
    onDecompileClick: () -> Unit,
    onEditClick: () -> Unit,
    onCompressClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rentool") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Staggered enter animation for each card
                val cards = listOf(
                    Triple("Extract RPA", extractStatus, com.renpytool.R.drawable.ic_extract to onExtractClick),
                    Triple("Create RPA", createStatus, com.renpytool.R.drawable.ic_create to onCreateClick),
                    Triple("Decompile RPYC", decompileStatus, com.renpytool.R.drawable.ic_decompile to onDecompileClick),
                    Triple("Edit RPY", editStatus, com.renpytool.R.drawable.ic_edit_rpy to onEditClick),
                    Triple("Compress Game", compressStatus, com.renpytool.R.drawable.ic_compress to onCompressClick)
                )

                cards.forEachIndexed { index, (title, status, iconWithClick) ->
                    var visible by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(index * 80L)
                        visible = true
                    }

                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = 400,
                                easing = FastOutSlowInEasing
                            )
                        ) + slideInVertically(
                            animationSpec = tween(
                                durationMillis = 400,
                                easing = FastOutSlowInEasing
                            ),
                            initialOffsetY = { it / 4 }
                        )
                    ) {
                        OperationCard(
                            title = title,
                            statusText = status,
                            iconRes = iconWithClick.first,
                            enabled = cardsEnabled,
                            onClick = iconWithClick.second
                        )
                    }
                }
            }

            // Scrollbar indicator (only shows when content is scrollable)
            if (scrollState.maxValue > 0) {
                BoxWithConstraints(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 6.dp, top = 8.dp, bottom = 8.dp)
                        .width(4.dp)
                ) {
                    val trackHeight = maxHeight

                    // Background track
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.small
                            )
                    )

                    // Calculate thumb size and position proportionally
                    val contentSize = scrollState.maxValue + scrollState.viewportSize
                    val thumbHeightRatio = (scrollState.viewportSize.toFloat() / contentSize.toFloat()).coerceIn(0.1f, 1f)
                    val thumbHeight = trackHeight * thumbHeightRatio

                    val scrollProgress = if (scrollState.maxValue > 0) {
                        scrollState.value.toFloat() / scrollState.maxValue.toFloat()
                    } else {
                        0f
                    }
                    val availableTravel = trackHeight - thumbHeight
                    val thumbOffset = availableTravel * scrollProgress

                    // Scrollbar thumb
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = thumbOffset)
                            .width(4.dp)
                            .height(thumbHeight)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                shape = MaterialTheme.shapes.small
                            )
                    )
                }
            }
        }
    }
}
