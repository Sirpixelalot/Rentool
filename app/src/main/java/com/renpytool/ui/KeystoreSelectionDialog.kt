package com.renpytool.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renpytool.keystore.KeystoreInfo
import com.renpytool.keystore.SigningOption

enum class SigningMode {
    RANDOM,
    CREATE_NEW,
    USE_EXISTING
}

/**
 * Dialog for selecting APK signing keystore option
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeystoreSelectionDialog(
    availableKeystores: List<KeystoreInfo>,
    onOptionSelected: (SigningOption) -> Unit,
    onDismiss: () -> Unit,
    onImportKeystore: () -> Unit,
    onExportKeystore: (KeystoreInfo) -> Unit,
    onDeleteKeystore: (KeystoreInfo) -> Unit
) {
    var selectedMode by remember { mutableStateOf(SigningMode.RANDOM) }
    var newKeystoreName by remember { mutableStateOf("") }
    var newKeystorePassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedKeystore by remember { mutableStateOf<KeystoreInfo?>(availableKeystores.firstOrNull()) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("APK Signing Key")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Choose how to sign the APK:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Option 1: Random Key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMode == SigningMode.RANDOM,
                        onClick = { selectedMode = SigningMode.RANDOM }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Use Random Key",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Warning",
                                tint = Color(0xFFFFA726),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "One-time use only (cannot update APK)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                // Option 2: Create New Key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    RadioButton(
                        selected = selectedMode == SigningMode.CREATE_NEW,
                        onClick = { selectedMode = SigningMode.CREATE_NEW }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Create New Persistent Key",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Reusable for app updates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (selectedMode == SigningMode.CREATE_NEW) {
                            OutlinedTextField(
                                value = newKeystoreName,
                                onValueChange = {
                                    newKeystoreName = it
                                    nameError = null
                                },
                                label = { Text("Keystore Name") },
                                placeholder = { Text("my_key") },
                                isError = nameError != null,
                                supportingText = {
                                    if (nameError != null) {
                                        Text(nameError!!, color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = newKeystorePassword,
                                onValueChange = {
                                    newKeystorePassword = it
                                    passwordError = null
                                },
                                label = { Text("Password") },
                                placeholder = { Text("Enter a secure password") },
                                isError = passwordError != null,
                                supportingText = {
                                    if (passwordError != null) {
                                        Text(passwordError!!, color = MaterialTheme.colorScheme.error)
                                    } else {
                                        Text("Save this password - you'll need it for importing", fontSize = 11.sp)
                                    }
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
                            )
                        }
                    }
                }

                // Option 3: Use Existing Key
                if (availableKeystores.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = selectedMode == SigningMode.USE_EXISTING,
                            onClick = { selectedMode = SigningMode.USE_EXISTING }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use Existing Key",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            if (selectedMode == SigningMode.USE_EXISTING) {
                                // Keystore dropdown
                                ExposedDropdownMenuBox(
                                    expanded = expandedDropdown,
                                    onExpandedChange = { expandedDropdown = it },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = selectedKeystore?.name ?: "",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Select Keystore") },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor()
                                    )

                                    ExposedDropdownMenu(
                                        expanded = expandedDropdown,
                                        onDismissRequest = { expandedDropdown = false }
                                    ) {
                                        availableKeystores.forEach { keystore ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            text = keystore.name,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = keystore.getFormattedDate(),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    selectedKeystore = keystore
                                                    expandedDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Management buttons
                                if (selectedKeystore != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onExportKeystore(selectedKeystore!!) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Export", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { onDeleteKeystore(selectedKeystore!!) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text("Delete", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Import button (always visible)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onImportKeystore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import Keystore")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val option = when (selectedMode) {
                        SigningMode.RANDOM -> {
                            SigningOption.RandomKey
                        }
                        SigningMode.CREATE_NEW -> {
                            if (newKeystoreName.isBlank()) {
                                nameError = "Name cannot be empty"
                                return@TextButton
                            }
                            if (!newKeystoreName.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                                nameError = "Use only letters, numbers, _ and -"
                                return@TextButton
                            }
                            if (newKeystorePassword.isBlank()) {
                                passwordError = "Password cannot be empty"
                                return@TextButton
                            }
                            if (newKeystorePassword.length < 6) {
                                passwordError = "Password must be at least 6 characters"
                                return@TextButton
                            }
                            SigningOption.CreateNew(newKeystoreName, newKeystorePassword)
                        }
                        SigningMode.USE_EXISTING -> {
                            selectedKeystore?.let { SigningOption.UseExisting(it) }
                                ?: return@TextButton
                        }
                    }
                    onOptionSelected(option)
                }
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
