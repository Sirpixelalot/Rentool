package com.renpytool

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.renpytool.keystore.KeystoreManager
import com.renpytool.ui.SettingsScreen
import com.renpytool.ui.theme.RenpytoolTheme
import kotlinx.coroutines.launch
import java.io.File

class SettingsActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var keystoreImportPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var keystoreExportDirPickerLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize file pickers
        initFilePickerLaunchers()

        // Set up Compose UI
        setupUI()
    }

    private fun initFilePickerLaunchers() {
        // Import keystore file
        keystoreImportPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val keystorePath = result.data?.getStringExtra(FilePickerActivity.EXTRA_SELECTED_PATH)
                keystorePath?.let { path ->
                    handleKeystoreImport(File(path))
                }
            }
        }

        // Export keystore directory
        keystoreExportDirPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val exportPath = result.data?.getStringExtra(FilePickerActivity.EXTRA_SELECTED_PATH)
                exportPath?.let { path ->
                    // Export all keystores
                    handleExportAllKeystores(File(path))
                }
            }
        }
    }

    private fun setupUI() {
        setContent {
            RenpytoolTheme(
                darkTheme = when (viewModel.themeMode.value) {
                    MainViewModel.ThemeMode.LIGHT -> false
                    MainViewModel.ThemeMode.DARK -> true
                    MainViewModel.ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
            ) {
                SettingsScreen(
                    themeMode = viewModel.themeMode.value,
                    onThemeModeChange = { newMode ->
                        viewModel.setThemeMode(newMode)
                    },
                    onNavigateBack = {
                        finish()
                    },
                    onImportKeystore = {
                        launchKeystoreImportPicker()
                    },
                    onExportKeystores = {
                        launchKeystoreExportPicker()
                    }
                )
            }
        }
    }

    private fun launchKeystoreImportPicker() {
        val intent = Intent(this, FilePickerActivity::class.java).apply {
            putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE)
            putExtra(FilePickerActivity.EXTRA_FILE_FILTER, ".bks")
            putExtra(FilePickerActivity.EXTRA_TITLE, "Select Keystore File")
        }
        keystoreImportPickerLauncher.launch(intent)
    }

    private fun launchKeystoreExportPicker() {
        val intent = Intent(this, FilePickerActivity::class.java).apply {
            putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIRECTORY)
            putExtra(FilePickerActivity.EXTRA_TITLE, "Select Export Location")
        }
        keystoreExportDirPickerLauncher.launch(intent)
    }

    private fun handleKeystoreImport(keystoreFile: File) {
        // Prefill name from filename (without extension)
        val defaultName = keystoreFile.nameWithoutExtension

        // Prompt user for keystore name and password
        val inputLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val nameInput = TextInputEditText(this).apply {
            hint = "Keystore name (e.g., my_key)"
            setText(defaultName)  // Prefill with filename
        }
        val passwordInput = TextInputEditText(this).apply {
            hint = "Keystore password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        inputLayout.addView(nameInput)
        inputLayout.addView(passwordInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("Import Keystore")
            .setMessage("Enter the keystore name and password:")
            .setView(inputLayout)
            .setPositiveButton("Import") { _, _ ->
                val name = nameInput.text?.toString()?.trim() ?: ""
                val password = passwordInput.text?.toString() ?: ""

                if (name.isBlank()) {
                    Toast.makeText(this, "Keystore name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (password.isBlank()) {
                    Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val keystoreManager = KeystoreManager(this@SettingsActivity)
                    val result = keystoreManager.importKeystore(keystoreFile, name, password)

                    if (result.isSuccess) {
                        Toast.makeText(this@SettingsActivity, "Keystore imported successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                        Toast.makeText(this@SettingsActivity, "Import failed: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleExportAllKeystores(exportDir: File) {
        lifecycleScope.launch {
            val keystoreManager = KeystoreManager(this@SettingsActivity)
            val keystores = keystoreManager.listKeystores()

            if (keystores.isEmpty()) {
                Toast.makeText(this@SettingsActivity, "No keystores to export", Toast.LENGTH_SHORT).show()
                return@launch
            }

            var successCount = 0
            var failCount = 0

            keystores.forEach { keystoreInfo ->
                val exportFile = File(exportDir, "${keystoreInfo.name}.bks")
                val result = keystoreManager.exportKeystore(keystoreInfo, exportFile)

                if (result.isSuccess) {
                    successCount++
                } else {
                    failCount++
                }
            }

            val message = buildString {
                append("Exported $successCount keystore(s)")
                if (failCount > 0) {
                    append(", $failCount failed")
                }
                append(" to ${exportDir.absolutePath}")
            }

            Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
        }
    }
}
