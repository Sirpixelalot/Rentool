package com.renpytool.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renpytool.R
import com.renpytool.ui.theme.Purple80
import com.renpytool.viewmodel.FileItem
import com.renpytool.viewmodel.FilePickerUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File item row with icon, name, details, and selection state
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    item: FileItem,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = if (isSelected) {
            Purple80.copy(alpha = 0.2f)
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File/folder icon
            if (item.isParent) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Purple80
                )
            } else if (item.apkIcon != null) {
                // Display APK icon (same size as other icons)
                Image(
                    bitmap = item.apkIcon.asImageBitmap(),
                    contentDescription = "APK Icon",
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Icon(
                    painter = painterResource(id = getFileIcon(item)),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Purple80  // Same purple as main menu icons
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // File name and details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!item.isParent && item.file.exists()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val details = buildString {
                        if (!item.isDirectory) {
                            append(formatFileSize(item.file.length()))
                            append(" • ")
                        }
                        append(formatDate(item.file.lastModified()))
                    }
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            // Selection indicator
            if (isMultiSelectMode && isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = Purple80,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Main file picker screen with toolbar and file list
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerScreen(
    uiState: FilePickerUiState,
    title: String,
    onFileItemClick: (FileItem) -> Unit,
    onFileItemLongClick: (FileItem) -> Unit,
    onNavigationClick: () -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            if (uiState.isMultiSelectMode || uiState.mode == FilePickerUiState.MODE_DIRECTORY) {
                ExtendedFloatingActionButton(
                    onClick = onFabClick,
                    containerColor = Purple80,
                    contentColor = Color.Black
                ) {
                    val text = if (uiState.isMultiSelectMode) {
                        "Select (${uiState.selectedFiles.size})"
                    } else {
                        "Select Folder"
                    }
                    Text(text)
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Current path display
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = uiState.currentDirectory.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // File list
            if (uiState.fileItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No files found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.fileItems,
                        key = { it.file.absolutePath }
                    ) { item ->
                        val isSelected = item.file in uiState.selectedFiles
                        FileItemRow(
                            item = item,
                            isSelected = isSelected,
                            isMultiSelectMode = uiState.isMultiSelectMode,
                            onClick = { onFileItemClick(item) },
                            onLongClick = { onFileItemLongClick(item) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

/**
 * Get the appropriate icon resource for a file based on its type
 */
private fun getFileIcon(item: FileItem): Int {
    return if (item.isDirectory) {
        R.drawable.ic_folder
    } else {
        val fileName = item.name.lowercase()
        when {
            fileName.endsWith(".rpa") || fileName.endsWith(".arc") -> R.drawable.ic_rpa_file
            fileName.endsWith(".rpy") || fileName.endsWith(".rpyc") -> R.drawable.ic_script_file
            fileName.endsWith(".png") || fileName.endsWith(".jpg") ||
            fileName.endsWith(".jpeg") || fileName.endsWith(".webp") -> R.drawable.ic_png_file
            fileName.endsWith(".apk") -> R.drawable.ic_apk_file
            fileName.endsWith(".zip") -> R.drawable.ic_zip_file
            else -> R.drawable.ic_script_file  // Default to script icon for other files
        }
    }
}
