package com.renpytool.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.renpytool.MainViewModel

/**
 * Settings screen with theme selection and keystore management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: MainViewModel.ThemeMode,
    onThemeModeChange: (MainViewModel.ThemeMode) -> Unit,
    onNavigateBack: () -> Unit,
    onImportKeystore: () -> Unit,
    onExportKeystores: () -> Unit,
    showDecompileDialog: Boolean,
    onShowDecompileDialogChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown"
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Theme Section
            SettingsSectionHeader("Appearance")

            ThemeOption(
                title = "System Default",
                subtitle = "Follow system theme",
                selected = themeMode == MainViewModel.ThemeMode.SYSTEM,
                onClick = { onThemeModeChange(MainViewModel.ThemeMode.SYSTEM) }
            )

            ThemeOption(
                title = "Light Mode",
                subtitle = "Light theme",
                selected = themeMode == MainViewModel.ThemeMode.LIGHT,
                onClick = { onThemeModeChange(MainViewModel.ThemeMode.LIGHT) }
            )

            ThemeOption(
                title = "Dark Mode",
                subtitle = "Dark theme",
                selected = themeMode == MainViewModel.ThemeMode.DARK,
                onClick = { onThemeModeChange(MainViewModel.ThemeMode.DARK) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Dialogs Section
            SettingsSectionHeader("Dialogs")

            SettingsSwitchItem(
                title = "Show Decompile Options Dialog",
                subtitle = "Show options dialog before decompiling",
                checked = showDecompileDialog,
                onCheckedChange = onShowDecompileDialogChange
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Keystore Management Section
            SettingsSectionHeader("Keystore Management")

            SettingsActionItem(
                title = "Import Keystore",
                subtitle = "Import existing .bks keystore file",
                onClick = onImportKeystore
            )

            SettingsActionItem(
                title = "Export Keystores",
                subtitle = "Export all keystores to a folder",
                onClick = onExportKeystores
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // About Section
            SettingsSectionHeader("About")

            SettingsTextItem(
                title = "Version",
                subtitle = versionName
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun ThemeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SettingsActionItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsTextItem(
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
