package com.renpytool

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.renpytool.ui.MainScreenContent
import com.renpytool.ui.theme.RenpytoolTheme
import java.io.File
import java.util.ArrayList

class MainActivity : ComponentActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    // ViewModel
    private val viewModel: MainViewModel by viewModels()

    // File picker launchers
    private lateinit var extractRpaPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var extractDirPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var createSourcePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var decompileDirPickerLauncher: ActivityResultLauncher<Intent>

    // Progress activity launcher for chaining operations
    private lateinit var progressActivityLauncher: ActivityResultLauncher<Intent>

    // Temporary storage for multi-step file picking
    private var selectedRpaPath: String? = null
    private var selectedRpaPaths: ArrayList<String>? = null  // For batch extraction
    private var selectedSourcePath: String? = null
    private var selectedSourcePaths: ArrayList<String>? = null  // For batch creation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Python (must be done before ViewModel access)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // Initialize file picker launchers
        initFilePickerLaunchers()

        // Set up Compose UI
        setContent {
            RenpytoolTheme {
                MainScreen()
            }
        }

        // Check permissions
        checkPermissions()

        // Check for updates
        checkForUpdates()
    }

    @Composable
    private fun MainScreen() {
        // Collect state from ViewModel
        val extractStatus by viewModel.extractStatus.collectAsState()
        val createStatus by viewModel.createStatus.collectAsState()
        val decompileStatus by viewModel.decompileStatus.collectAsState()
        val editStatus by viewModel.editStatus.collectAsState()
        val cardsEnabled by viewModel.cardsEnabled.collectAsState()

        MainScreenContent(
            extractStatus = extractStatus,
            createStatus = createStatus,
            decompileStatus = decompileStatus,
            editStatus = editStatus,
            cardsEnabled = cardsEnabled,
            onExtractClick = { startExtractFlow() },
            onCreateClick = { startCreateFlow() },
            onDecompileClick = { startDecompileFlow() },
            onEditClick = { startEditRpyFlow() },
            onMenuClick = { showOptionsMenu() },
            modifier = Modifier.fillMaxSize()
        )
    }

    private fun showOptionsMenu() {
        // Placeholder for options menu - can add GitHub link, about, etc.
        Toast.makeText(this, "Menu", Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 and above - use MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Storage Permission Required")
                    .setMessage("This app needs access to manage files for RPA operations.")
                    .setPositiveButton("Grant") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            // Android 10 and below
            val permissionsNeeded = mutableListOf<String>()

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            if (permissionsNeeded.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    permissionsNeeded.toTypedArray(),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(
                    this,
                    "Permissions are required for this app to work",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun initFilePickerLaunchers() {
        // Extract: Pick RPA file(s)
        extractRpaPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                // Check for multi-select first
                selectedRpaPaths = result.data?.getStringArrayListExtra(FilePickerActivity.EXTRA_SELECTED_PATHS)
                if (selectedRpaPaths == null || selectedRpaPaths!!.isEmpty()) {
                    // Single selection
                    selectedRpaPath = result.data?.getStringExtra(FilePickerActivity.EXTRA_SELECTED_PATH)
                    selectedRpaPaths = null
                } else {
                    // Multi-selection
                    selectedRpaPath = null
                }
                // Now pick extraction directory
                launchExtractDirectoryPicker()
            }
        }

        // Extract: Pick extraction directory
        extractDirPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val extractPath = result.data?.getStringExtra(FilePickerActivity.EXTRA_SELECTED_PATH)
                extractPath?.let { path ->
                    // Check if batch or single extraction
                    if (selectedRpaPaths != null && selectedRpaPaths!!.isNotEmpty()) {
                        // Batch extraction - delegate to ViewModel
                        launchProgressActivityForBatchExtract(path, selectedRpaPaths!!)
                        viewModel.performBatchExtraction(selectedRpaPaths!!, path)
                    } else {
                        // Single extraction - delegate to ViewModel
                        selectedRpaPath?.let { rpaPath ->
                            launchProgressActivityForExtract(path)
                            viewModel.performExtraction(rpaPath, path)
                        }
                    }
                }
            }
        }

        // Create: Pick source directory/directories
        createSourcePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                // Check for multi-select first
                selectedSourcePaths = result.data?.getStringArrayListExtra(FilePickerActivity.EXTRA_SELECTED_PATHS)
                if (selectedSourcePaths == null || selectedSourcePaths!!.isEmpty()) {
                    // Single selection
                    selectedSourcePath = result.data?.getStringExtra(FilePickerActivity.EXTRA_SELECTED_PATH)
                    selectedSourcePaths = null
                } else {
                    // Multi-selection
                    selectedSourcePath = null
                }
                // Now ask for output file name
                showOutputFileNameDialog()
            }
        }

        // Decompile: Pick source directory
        decompileDirPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                // Check for multi-select first (user may have accidentally entered multi-select mode)
                val selectedPaths = result.data?.getStringArrayListExtra(FilePickerActivity.EXTRA_SELECTED_PATHS)
                val sourcePath = if (selectedPaths != null && selectedPaths.isNotEmpty()) {
                    // If multi-select happened, just use the first path
                    selectedPaths[0]
                } else {
                    // Normal single selection
                    result.data?.getStringExtra(FilePickerActivity.EXTRA_SELECTED_PATH)
                }

                if (!sourcePath.isNullOrEmpty()) {
                    // Launch progress activity and delegate to ViewModel
                    val intent = Intent(this, ProgressActivity::class.java).apply {
                        putExtra("DECOMPILE_PATH", sourcePath)
                    }
                    startActivity(intent)
                    viewModel.performDecompile(sourcePath)
                } else {
                    Toast.makeText(this, "No directory selected", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Progress Activity: Handle completion and chaining
        progressActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                // Check if there's a chain operation requested
                val chainOperation = result.data?.getStringExtra("CHAIN_OPERATION")
                val chainPath = result.data?.getStringExtra("CHAIN_PATH")

                if (chainOperation == "decompile" && chainPath != null) {
                    // Start decompile with the extracted path - delegate to ViewModel
                    val intent = Intent(this, ProgressActivity::class.java).apply {
                        putExtra("DECOMPILE_PATH", chainPath)
                    }
                    startActivity(intent)
                    viewModel.performDecompile(chainPath)
                }
            }
        }
    }

    private fun launchProgressActivityForExtract(extractPath: String) {
        val intent = Intent(this, ProgressActivity::class.java).apply {
            putExtra("EXTRACT_PATH", extractPath)
        }
        progressActivityLauncher.launch(intent)
    }

    private fun launchProgressActivityForBatchExtract(extractPath: String, rpaFiles: ArrayList<String>) {
        val fileNames = ArrayList(rpaFiles.map { File(it).name })
        val intent = Intent(this, ProgressActivity::class.java).apply {
            putExtra("EXTRACT_PATH", extractPath)
            putExtra("BATCH_MODE", true)
            putExtra("BATCH_TOTAL", rpaFiles.size)
            putExtra("BATCH_FILES", fileNames)
        }
        progressActivityLauncher.launch(intent)
    }

    private fun startExtractFlow() {
        // Launch file picker for RPA file
        val intent = Intent(this, FilePickerActivity::class.java).apply {
            putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE)
            putExtra(FilePickerActivity.EXTRA_FILE_FILTER, ".rpa")
            putExtra(FilePickerActivity.EXTRA_TITLE, "Select RPA File")
        }
        extractRpaPickerLauncher.launch(intent)
    }

    private fun launchExtractDirectoryPicker() {
        val intent = Intent(this, FilePickerActivity::class.java).apply {
            putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIRECTORY)
            putExtra(FilePickerActivity.EXTRA_TITLE, "Select Extraction Folder")

            // Default to the directory containing the selected RPA file(s)
            val startDir = when {
                selectedRpaPaths != null && selectedRpaPaths!!.isNotEmpty() -> {
                    // Multi-select: use parent directory of first selected RPA
                    File(selectedRpaPaths!![0]).parent
                }
                selectedRpaPath != null -> {
                    // Single select: use parent directory of selected RPA
                    File(selectedRpaPath!!).parent
                }
                else -> null
            }

            startDir?.let { putExtra(FilePickerActivity.EXTRA_START_DIR, it) }
        }

        extractDirPickerLauncher.launch(intent)
    }

    private fun startCreateFlow() {
        // Launch file picker for source directory
        val intent = Intent(this, FilePickerActivity::class.java).apply {
            putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIRECTORY)
            putExtra(FilePickerActivity.EXTRA_TITLE, "Select Source Folder")
        }
        createSourcePickerLauncher.launch(intent)
    }

    private fun showOutputFileNameDialog() {
        // Inflate custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val etFileName = dialogView.findViewById<TextInputEditText>(R.id.editText)
        etFileName.setText("archive.rpa")

        // Determine the default output directory
        val defaultOutputDir = when {
            selectedSourcePaths != null && selectedSourcePaths!!.isNotEmpty() -> {
                // For batch, use parent directory of first selected item
                File(selectedSourcePaths!![0]).parent
            }
            else -> selectedSourcePath
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Output File Name")
            .setMessage("Enter the name for the output RPA file:")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val fileName = etFileName.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    val outputPath = "$defaultOutputDir/$fileName"

                    // Check if batch or single creation - delegate to ViewModel
                    if (selectedSourcePaths != null && selectedSourcePaths!!.isNotEmpty()) {
                        // Batch creation - launch with batch info
                        val sourceNames = ArrayList(selectedSourcePaths!!.map { File(it).name })
                        val intent = Intent(this, ProgressActivity::class.java).apply {
                            putExtra("BATCH_MODE", true)
                            putExtra("BATCH_TOTAL", selectedSourcePaths!!.size)
                            putExtra("BATCH_FILES", sourceNames)
                        }
                        startActivity(intent)
                        viewModel.performBatchCreation(selectedSourcePaths!!, outputPath)
                    } else {
                        // Single creation
                        val intent = Intent(this, ProgressActivity::class.java)
                        startActivity(intent)
                        selectedSourcePath?.let { viewModel.performCreation(it, outputPath) }
                    }
                } else {
                    Toast.makeText(this, "Please enter a file name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startDecompileFlow() {
        // Launch file picker for directory containing .rpyc files
        val intent = Intent(this, FilePickerActivity::class.java).apply {
            putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIRECTORY)
            putExtra(FilePickerActivity.EXTRA_TITLE, "Select Folder with RPYC Files")
        }
        decompileDirPickerLauncher.launch(intent)
    }

    /**
     * Get the current app version from PackageManager
     */
    private fun getAppVersion(): String {
        return try {
            val packageManager = packageManager
            val packageName = packageName

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ (API 33+) - Use PackageInfoFlags
                val packageInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
                packageInfo.versionName ?: "1.0"
            } else {
                // Older Android versions - deprecated but still works
                @Suppress("DEPRECATION")
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                packageInfo.versionName ?: "1.0"
            }
        } catch (e: PackageManager.NameNotFoundException) {
            android.util.Log.e("MainActivity", "Failed to get app version", e)
            "1.0"  // Fallback version
        }
    }

    /**
     * Check for app updates from GitHub releases
     */
    private fun checkForUpdates() {
        android.util.Log.d("MainActivity", "Starting update check...")
        val currentVersion = getAppVersion()
        android.util.Log.d("MainActivity", "Current app version: $currentVersion")

        // Use lifecycleScope for coroutine instead of ExecutorService
        lifecycleScope.launch {
            UpdateChecker.checkForUpdates(currentVersion, object : UpdateChecker.UpdateCheckCallback {
                override fun onUpdateAvailable(versionInfo: VersionInfo) {
                    android.util.Log.d("MainActivity", "Update available: ${versionInfo.versionTag}")
                    runOnUiThread { showUpdateDialog(versionInfo) }
                }

                override fun onNoUpdateAvailable() {
                    android.util.Log.d("MainActivity", "No update available")
                    // Silent - no action needed
                }

                override fun onCheckFailed(error: String) {
                    android.util.Log.e("MainActivity", "Update check failed: $error")
                    // Silent fail - don't bother user with network errors
                }
            })
        }
    }

    /**
     * Show update available dialog
     */
    private fun showUpdateDialog(versionInfo: VersionInfo) {
        val currentVersion = getAppVersion()
        val message = """
            Version ${versionInfo.versionNumber} is now available!

            You are currently using version $currentVersion.

            Would you like to download the update?
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("Update Available")
            .setMessage(message)
            .setPositiveButton("Update") { _, _ ->
                // Open GitHub releases page in browser
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(versionInfo.downloadUrl)
                )
                startActivity(browserIntent)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun startEditRpyFlow() {
        // Launch file picker to browse for .rpy files
        val intent = Intent(this, FilePickerActivity::class.java).apply {
            putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE)
            putExtra(FilePickerActivity.EXTRA_FILE_FILTER, ".rpy")
            putExtra(FilePickerActivity.EXTRA_TITLE, "Select .rpy File to Edit")
            putExtra("OPEN_EDITOR", true)  // Flag to open editor directly

            // Restore last folder location if available
            val prefs = getSharedPreferences("RentoolPrefs", MODE_PRIVATE)
            val lastFolder = prefs.getString("last_rpy_edit_folder", null)
            lastFolder?.let { putExtra(FilePickerActivity.EXTRA_START_DIR, it) }
        }

        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // Update edit status when returning from editor
        viewModel.updateEditStatus()
    }
}
