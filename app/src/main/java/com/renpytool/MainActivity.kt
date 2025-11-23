package com.renpytool

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.renpytool.keystore.KeystoreInfo
import com.renpytool.keystore.KeystoreManager
import com.renpytool.keystore.SigningOption
import com.renpytool.ui.CompressionSettingsDialog
import com.renpytool.ui.KeystoreSelectionDialog
import com.renpytool.ui.MainScreenContent
import com.renpytool.ui.RandomKeyWarningDialog
import com.renpytool.ui.theme.RenpytoolTheme
import java.io.File
import java.util.ArrayList

class MainActivity : ComponentActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val PERMISSION_REQUEST_NOTIFICATION = 101
    }

    // ViewModel
    private val viewModel: MainViewModel by viewModels()

    // File picker launchers
    private lateinit var extractRpaPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var extractDirPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var createSourcePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var decompileDirPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var compressSourcePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var compressOutputPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var keystoreImportPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var keystoreExportPickerLauncher: ActivityResultLauncher<Intent>

    // Progress activity launcher for chaining operations
    private lateinit var progressActivityLauncher: ActivityResultLauncher<Intent>

    // Temporary storage for multi-step file picking
    private var selectedRpaPath: String? = null
    private var selectedRpaPaths: ArrayList<String>? = null  // For batch extraction
    private var selectedSourcePath: String? = null
    private var selectedSourcePaths: ArrayList<String>? = null  // For batch creation
    private var selectedCompressSourcePath: String? = null
    private var selectedCompressOutputPath: String? = null

    // Keystore management state
    private var selectedSigningOption: SigningOption? = null
    private var pendingKeystoreExport: KeystoreInfo? = null
    private var pendingCompressionSettings: CompressionSettings? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Python (must be done before ViewModel access)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // Initialize file picker launchers
        initFilePickerLaunchers()

        // Set up Compose UI
        setupMainUI()

        // Check permissions
        checkPermissions()

        // Check for updates
        checkForUpdates()
    }

    private fun setupMainUI() {
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                MainViewModel.ThemeMode.LIGHT -> false
                MainViewModel.ThemeMode.DARK -> true
                MainViewModel.ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            RenpytoolTheme(darkTheme = darkTheme) {
                MainScreen()
            }
        }
    }

    @Composable
    private fun MainScreen() {
        // Collect state from ViewModel
        val extractStatus by viewModel.extractStatus.collectAsState()
        val createStatus by viewModel.createStatus.collectAsState()
        val decompileStatus by viewModel.decompileStatus.collectAsState()
        val editStatus by viewModel.editStatus.collectAsState()
        val compressStatus by viewModel.compressStatus.collectAsState()
        val cardsEnabled by viewModel.cardsEnabled.collectAsState()

        MainScreenContent(
            extractStatus = extractStatus,
            createStatus = createStatus,
            decompileStatus = decompileStatus,
            editStatus = editStatus,
            compressStatus = compressStatus,
            cardsEnabled = cardsEnabled,
            onExtractClick = { startExtractFlow() },
            onCreateClick = { startCreateFlow() },
            onDecompileClick = { startDecompileFlow() },
            onEditClick = { startEditRpyFlow() },
            onCompressClick = { startCompressFlow() },
            onSettingsClick = { startSettingsActivity() },
            modifier = Modifier.fillMaxSize()
        )
    }

    private fun startSettingsActivity() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }


    private fun checkPermissions() {
        // Check notification permission for Android 13+ (for background operation notifications)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_NOTIFICATION
                )
            }
        }

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
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (!allGranted) {
                    Toast.makeText(
                        this,
                        "Permissions are required for this app to work",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            PERMISSION_REQUEST_NOTIFICATION -> {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    Toast.makeText(
                        this,
                        "Notification permission is recommended for background operations",
                        Toast.LENGTH_LONG
                    ).show()
                }
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
                        // Batch extraction - delegate to ViewModel (no validation for batch yet)
                        launchProgressActivityForBatchExtract(path, selectedRpaPaths!!)
                        viewModel.performBatchExtraction(selectedRpaPaths!!, path)
                    } else {
                        // Single extraction - validate storage first
                        selectedRpaPath?.let { rpaPath ->
                            validateAndExtract(rpaPath, path)
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
                    // Show decompile options dialog
                    showDecompileOptionsDialog(sourcePath)
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

        // Compress: Pick source directory
        compressSourcePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                selectedCompressSourcePath = result.data?.getStringExtra(FilePickerActivity.EXTRA_SELECTED_PATH)

                // If APK file selected, set output to "{name}-compressed.apk" in parent directory
                // If directory selected, use same folder (overwrite originals)
                selectedCompressOutputPath = selectedCompressSourcePath?.let { sourcePath ->
                    val sourceFile = File(sourcePath)
                    if (sourceFile.isFile && sourceFile.name.lowercase().endsWith(".apk")) {
                        // APK file: output in parent directory with "-compressed" suffix
                        val nameWithoutExt = sourceFile.nameWithoutExtension
                        val parentDir = sourceFile.parentFile
                        File(parentDir, "$nameWithoutExt-compressed.apk").absolutePath
                    } else {
                        // Directory: use same location
                        sourcePath
                    }
                }

                // Show compression settings dialog
                showCompressionSettingsDialog()
            }
        }

        // Compress: Pick output directory
        compressOutputPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                selectedCompressOutputPath = result.data?.getStringExtra(FilePickerActivity.EXTRA_SELECTED_PATH)
                // Show compression settings dialog
                showCompressionSettingsDialog()
            }
        }

        // Keystore: Import keystore file
        keystoreImportPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val keystorePath = result.data?.getStringExtra(FilePickerActivity.EXTRA_SELECTED_PATH)
                keystorePath?.let { path ->
                    handleKeystoreImport(File(path))
                }
            }
        }

        // Keystore: Export keystore file
        keystoreExportPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val exportPath = result.data?.getStringExtra(FilePickerActivity.EXTRA_SELECTED_PATH)
                exportPath?.let { path ->
                    pendingKeystoreExport?.let { keystoreInfo ->
                        handleKeystoreExport(keystoreInfo, File(path))
                    }
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

    private fun showDecompileOptionsDialog(sourcePath: String) {
        // Create custom dialog view with checkbox
        val dialogView = layoutInflater.inflate(android.R.layout.select_dialog_singlechoice, null)
        val checkboxView = android.widget.CheckBox(this).apply {
            text = "Try Harder Mode (slower, for obfuscated files)"
            setPadding(50, 30, 50, 30)
            isChecked = false
        }

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(android.widget.TextView(this@MainActivity).apply {
                text = "Decompile Options"
                textSize = 16f
                setPadding(0, 0, 0, 20)
            })
            addView(checkboxView)
            addView(android.widget.TextView(this@MainActivity).apply {
                text = "\nTry Harder Mode performs aggressive decompilation for heavily obfuscated files. " +
                       "This will take significantly longer.\n\n" +
                       "Use this only if default decompilation fails."
                textSize = 12f
                setPadding(0, 20, 0, 0)
                setTextColor(android.graphics.Color.GRAY)
            })
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Decompile Settings")
            .setView(layout)
            .setPositiveButton("Start") { _, _ ->
                val tryHarder = checkboxView.isChecked
                // Launch progress activity and delegate to ViewModel
                val intent = Intent(this, ProgressActivity::class.java).apply {
                    putExtra("DECOMPILE_PATH", sourcePath)
                }
                startActivity(intent)
                viewModel.performDecompile(sourcePath, tryHarder)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Get available storage space at the given path
     */
    private fun getAvailableSpace(path: String): Long {
        return try {
            val stat = StatFs(path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Format bytes to human-readable string (GB)
     */
    private fun formatSize(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return String.format("%.2f GB", gb)
    }

    /**
     * Validate storage before extraction and show warnings/errors
     */
    private fun validateAndExtract(rpaPath: String, extractPath: String) {
        lifecycleScope.launch {
            try {
                // Get archive info from Python
                val python = Python.getInstance()
                val rpaModule = python.getModule("rpa_wrapper")
                val result = rpaModule.callAttr("get_extraction_info", rpaPath)

                val success = result.callAttr("__getitem__", "success").toBoolean()
                if (!success) {
                    val errorMsg = result.callAttr("__getitem__", "message").toString()
                    Toast.makeText(this@MainActivity, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val totalSize = result.callAttr("__getitem__", "total_size").toLong()
                val fileCount = result.callAttr("__getitem__", "file_count").toInt()
                val archiveSize = java.io.File(rpaPath).length()

                // Check available storage
                val availableSpace = getAvailableSpace(extractPath)
                val expansionRatio = if (archiveSize > 0) totalSize.toDouble() / archiveSize.toDouble() else 0.0

                // CASE 1: Insufficient storage (blocking error)
                if (totalSize > availableSpace) {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("Insufficient Storage")
                        .setMessage(
                            "Cannot extract archive:\n\n" +
                            "Required: ${formatSize(totalSize)}\n" +
                            "Available: ${formatSize(availableSpace)}\n\n" +
                            "Free up ${formatSize(totalSize - availableSpace)} of storage and try again."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                    return@launch
                }

                // CASE 2: Huge extraction (>10GB or >20x expansion) - warning
                val sizeThresholdGB = 10L * 1024 * 1024 * 1024 // 10GB
                val expansionThreshold = 20.0

                if (totalSize > sizeThresholdGB || expansionRatio > expansionThreshold) {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("Large Extraction Warning")
                        .setMessage(
                            "This archive will extract to ${formatSize(totalSize)} ($fileCount files).\n\n" +
                            "Archive size: ${formatSize(archiveSize)}\n" +
                            "Extracted size: ${formatSize(totalSize)}\n" +
                            "Expansion: ${String.format("%.1fx", expansionRatio)}\n\n" +
                            "This may take a long time and use significant storage. Continue?"
                        )
                        .setPositiveButton("Continue") { _, _ ->
                            // Proceed with extraction
                            launchProgressActivityForExtract(extractPath)
                            viewModel.performExtraction(rpaPath, extractPath)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    // Normal extraction - proceed immediately
                    launchProgressActivityForExtract(extractPath)
                    viewModel.performExtraction(rpaPath, extractPath)
                }

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error checking archive: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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

        // Use lifecycleScope for coroutine
        lifecycleScope.launch {
            when (val result = UpdateChecker.checkForUpdates(currentVersion)) {
                is UpdateChecker.UpdateResult.UpdateAvailable -> {
                    android.util.Log.d("MainActivity", "Update available: ${result.versionInfo.versionTag}")
                    showUpdateDialog(result.versionInfo)
                }
                is UpdateChecker.UpdateResult.NoUpdateAvailable -> {
                    android.util.Log.d("MainActivity", "No update available")
                    // Silent - no action needed
                }
                is UpdateChecker.UpdateResult.CheckFailed -> {
                    android.util.Log.e("MainActivity", "Update check failed: ${result.error}")
                    // Silent fail - don't bother user with network errors
                }
            }
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

    private fun startCompressFlow() {
        // Launch file picker for game directory or APK file
        val intent = Intent(this, FilePickerActivity::class.java).apply {
            putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIRECTORY)
            putExtra(FilePickerActivity.EXTRA_FILE_FILTER, "compress_source")
            putExtra(FilePickerActivity.EXTRA_TITLE, "Select Game Folder or APK")
        }
        compressSourcePickerLauncher.launch(intent)
    }

    private fun launchCompressOutputPicker() {
        val intent = Intent(this, FilePickerActivity::class.java).apply {
            putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIRECTORY)
            putExtra(FilePickerActivity.EXTRA_TITLE, "Select Output Folder")
        }
        compressOutputPickerLauncher.launch(intent)
    }

    private fun showCompressionSettingsDialog() {
        val sourcePath = selectedCompressSourcePath
        val outputPath = selectedCompressOutputPath

        if (sourcePath == null || outputPath == null) {
            Toast.makeText(this, "Error: Missing source or output path", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if source is an APK file
        val sourceFile = File(sourcePath)
        val isApk = sourceFile.isFile && sourceFile.name.lowercase().endsWith(".apk")

        if (isApk) {
            // Show keystore selection dialog first for APKs
            showKeystoreSelectionDialog()
        } else {
            // Show compression settings directly for directories
            showCompressionSettingsDialogInternal(null)
        }
    }

    private fun showKeystoreSelectionDialog() {
        val keystoreManager = KeystoreManager(this)
        val availableKeystores = keystoreManager.listKeystores()

        setContent {
            RenpytoolTheme(
                darkTheme = when (viewModel.themeMode.value) {
                    MainViewModel.ThemeMode.LIGHT -> false
                    MainViewModel.ThemeMode.DARK -> true
                    MainViewModel.ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
            ) {
                KeystoreSelectionDialog(
                    availableKeystores = availableKeystores,
                    onOptionSelected = { signingOption ->
                        selectedSigningOption = signingOption

                        // Show warning for random key
                        if (signingOption is SigningOption.RandomKey) {
                            showRandomKeyWarningDialog()
                        } else {
                            // Proceed to compression settings
                            showCompressionSettingsDialogInternal(signingOption)
                        }
                    },
                    onDismiss = {
                        // User cancelled, reset to main UI
                        setupMainUI()
                    },
                    onImportKeystore = {
                        // Launch file picker for .bks file
                        val intent = Intent(this, FilePickerActivity::class.java).apply {
                            putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE)
                            putExtra(FilePickerActivity.EXTRA_FILE_FILTER, ".bks")
                            putExtra(FilePickerActivity.EXTRA_TITLE, "Select Keystore File")
                        }
                        keystoreImportPickerLauncher.launch(intent)
                    },
                    onExportKeystore = { keystoreInfo ->
                        pendingKeystoreExport = keystoreInfo
                        // Launch directory picker for export location
                        val intent = Intent(this, FilePickerActivity::class.java).apply {
                            putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIRECTORY)
                            putExtra(FilePickerActivity.EXTRA_TITLE, "Select Export Location")
                        }
                        keystoreExportPickerLauncher.launch(intent)
                    },
                    onDeleteKeystore = { keystoreInfo ->
                        handleKeystoreDelete(keystoreInfo)
                    }
                )
            }
        }
    }

    private fun showRandomKeyWarningDialog() {
        setContent {
            RenpytoolTheme(
                darkTheme = when (viewModel.themeMode.value) {
                    MainViewModel.ThemeMode.LIGHT -> false
                    MainViewModel.ThemeMode.DARK -> true
                    MainViewModel.ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
            ) {
                RandomKeyWarningDialog(
                    onContinue = {
                        // User confirmed, proceed to compression settings
                        showCompressionSettingsDialogInternal(selectedSigningOption)
                    },
                    onGoBack = {
                        // Go back to keystore selection
                        showKeystoreSelectionDialog()
                    }
                )
            }
        }
    }

    private fun showCompressionSettingsDialogInternal(signingOption: SigningOption?) {
        val sourcePath = selectedCompressSourcePath
        val outputPath = selectedCompressOutputPath

        if (sourcePath == null || outputPath == null) {
            Toast.makeText(this, "Error: Missing source or output path", Toast.LENGTH_SHORT).show()
            return
        }

        // Load saved settings
        val settings = CompressionSettings.load(this)

        // Show settings dialog using Compose
        setContent {
            RenpytoolTheme(
                darkTheme = when (viewModel.themeMode.value) {
                    MainViewModel.ThemeMode.LIGHT -> false
                    MainViewModel.ThemeMode.DARK -> true
                    MainViewModel.ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
            ) {
                CompressionSettingsDialog(
                    initialSettings = settings,
                    onConfirm = { newSettings ->
                        // Save settings
                        newSettings.save(this@MainActivity)
                        // Start compression
                        val intent = Intent(this@MainActivity, ProgressActivity::class.java)
                        startActivity(intent)
                        viewModel.performCompression(sourcePath, outputPath, newSettings, signingOption)
                        // Reset to main UI
                        setupMainUI()
                    },
                    onDismiss = {
                        // User cancelled, reset to main UI
                        setupMainUI()
                    }
                )
            }
        }
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
                    val keystoreManager = KeystoreManager(this@MainActivity)
                    val result = keystoreManager.importKeystore(keystoreFile, name, password)

                    if (result.isSuccess) {
                        Toast.makeText(this@MainActivity, "Keystore imported successfully", Toast.LENGTH_SHORT).show()
                        // Refresh keystore selection dialog
                        showKeystoreSelectionDialog()
                    } else {
                        val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                        Toast.makeText(this@MainActivity, "Import failed: $errorMsg", Toast.LENGTH_LONG).show()
                        showKeystoreSelectionDialog()
                    }
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                showKeystoreSelectionDialog()
            }
            .show()
    }

    private fun handleKeystoreExport(keystoreInfo: KeystoreInfo, exportDir: File) {
        lifecycleScope.launch {
            val keystoreManager = KeystoreManager(this@MainActivity)
            val exportFile = File(exportDir, "${keystoreInfo.name}.bks")
            val result = keystoreManager.exportKeystore(keystoreInfo, exportFile)

            if (result.isSuccess) {
                Toast.makeText(this@MainActivity, "Keystore exported to ${exportFile.absolutePath}", Toast.LENGTH_LONG).show()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                Toast.makeText(this@MainActivity, "Export failed: $errorMsg", Toast.LENGTH_LONG).show()
            }

            // Refresh keystore selection dialog
            showKeystoreSelectionDialog()
        }
    }

    private fun handleKeystoreDelete(keystoreInfo: KeystoreInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Keystore")
            .setMessage("Are you sure you want to delete keystore '${keystoreInfo.name}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val keystoreManager = KeystoreManager(this@MainActivity)
                    val result = keystoreManager.deleteKeystore(keystoreInfo)

                    if (result.isSuccess) {
                        Toast.makeText(this@MainActivity, "Keystore deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                        Toast.makeText(this@MainActivity, "Delete failed: $errorMsg", Toast.LENGTH_SHORT).show()
                    }

                    // Refresh keystore selection dialog
                    showKeystoreSelectionDialog()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                showKeystoreSelectionDialog()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Update edit status when returning from editor
        viewModel.updateEditStatus()
    }
}
