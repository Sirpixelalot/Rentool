package com.renpytool.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.DrawableRes
import com.renpytool.ui.theme.Purple80

/**
 * Reusable operation card component matching the existing Material Design UI
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
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = "$title icon",
                modifier = Modifier.size(48.dp),
                tint = Purple80  // Fixed purple color for consistent icon tint
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Title and Status
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface  // White/high contrast
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),  // Slightly dimmed white
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
    cardsEnabled: Boolean,
    onExtractClick: () -> Unit,
    onCreateClick: () -> Unit,
    onDecompileClick: () -> Unit,
    onEditClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rentool") },
                actions = {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_more),
                            contentDescription = "Menu"
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
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Extract Card
            OperationCard(
                title = "Extract RPA",
                statusText = extractStatus,
                iconRes = com.renpytool.R.drawable.ic_extract,
                enabled = cardsEnabled,
                onClick = onExtractClick
            )

            // Create Card
            OperationCard(
                title = "Create RPA",
                statusText = createStatus,
                iconRes = com.renpytool.R.drawable.ic_create,
                enabled = cardsEnabled,
                onClick = onCreateClick
            )

            // Decompile Card
            OperationCard(
                title = "Decompile RPYC",
                statusText = decompileStatus,
                iconRes = com.renpytool.R.drawable.ic_decompile,
                enabled = cardsEnabled,
                onClick = onDecompileClick
            )

            // Edit RPY Card
            OperationCard(
                title = "Edit RPY",
                statusText = editStatus,
                iconRes = com.renpytool.R.drawable.ic_edit_rpy,
                enabled = cardsEnabled,
                onClick = onEditClick
            )
        }
    }
}
