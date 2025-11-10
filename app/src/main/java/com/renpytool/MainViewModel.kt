package com.renpytool

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * ViewModel for MainActivity
 * Handles all business logic and state management using coroutines and StateFlow
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("RentoolPrefs", Context.MODE_PRIVATE)

    // Python modules
    private val python: Python = Python.getInstance()
    private val rpaModule: PyObject = python.getModule("rpa_wrapper")
    private val decompileModule: PyObject = python.getModule("decompile_wrapper")

    // State flows for card statuses
    private val _extractStatus = MutableStateFlow("No files extracted yet")
    val extractStatus: StateFlow<String> = _extractStatus.asStateFlow()

    private val _createStatus = MutableStateFlow("No archive created yet")
    val createStatus: StateFlow<String> = _createStatus.asStateFlow()

    private val _decompileStatus = MutableStateFlow("No files decompiled yet")
    val decompileStatus: StateFlow<String> = _decompileStatus.asStateFlow()

    private val _editStatus = MutableStateFlow("No files edited yet")
    val editStatus: StateFlow<String> = _editStatus.asStateFlow()

    // Cards enabled state
    private val _cardsEnabled = MutableStateFlow(true)
    val cardsEnabled: StateFlow<Boolean> = _cardsEnabled.asStateFlow()

    init {
        // Load edit status from SharedPreferences
        updateEditStatus()
    }

    /**
     * Update edit status from SharedPreferences
     */
    fun updateEditStatus() {
        val lastFilePath = prefs.getString("last_rpy_edit_file", null)
        _editStatus.value = if (lastFilePath != null) {
            val file = File(lastFilePath)
            if (file.exists()) {
                "Last edited: ${file.name}"
            } else {
                "No files edited yet"
            }
        } else {
            "No files edited yet"
        }
    }

    /**
     * Perform batch extraction of RPA files
     */
    fun performBatchExtraction(rpaFilePaths: ArrayList<String>, extractDirPath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val tracker = ProgressTracker(context)
                tracker.clearProgress()

                val totalFiles = rpaFilePaths.size
                var currentIndex = 1
                val batchStartTime = System.currentTimeMillis()

                for (rpaFilePath in rpaFilePaths) {
                    try {
                        val fileName = File(rpaFilePath).name

                        // Update progress with batch info
                        val batchProgress = ProgressData().apply {
                            operation = "extract"
                            currentBatchIndex = currentIndex
                            totalBatchCount = totalFiles
                            currentBatchFileName = fileName
                            status = "in_progress"
                            startTime = batchStartTime
                            lastUpdateTime = System.currentTimeMillis()
                            this.totalFiles = 0
                            processedFiles = 0
                            currentFile = "Starting extraction..."
                        }
                        tracker.writeProgress(batchProgress)

                        // Call Python extraction
                        val result = rpaModule.callAttr(
                            "extract_rpa",
                            rpaFilePath,
                            extractDirPath,
                            tracker.progressFilePath
                        )

                        if (result == null) {
                            throw Exception("Python function returned null")
                        }

                        val successObj = result.callAttr("__getitem__", "success")
                        val success = successObj.toJava(Boolean::class.java)

                        if (!success) {
                            val messageObj = result.callAttr("__getitem__", "message")
                            val message = messageObj.toJava(String::class.java)
                            throw Exception(message)
                        }

                        currentIndex++

                    } catch (e: Exception) {
                        e.printStackTrace()

                        // Update progress with error
                        try {
                            val errorData = ProgressData().apply {
                                operation = "extract"
                                status = "failed"
                                errorMessage = "Error on file $currentIndex/$totalFiles: ${e.message}"
                                currentBatchIndex = currentIndex
                                totalBatchCount = totalFiles
                            }
                            tracker.writeProgress(errorData)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                        return@withContext
                    }
                }

                // Mark as completed
                try {
                    val completeData = ProgressData().apply {
                        operation = "extract"
                        status = "completed"
                        currentBatchIndex = totalFiles
                        totalBatchCount = totalFiles
                        this.totalFiles = totalFiles
                        processedFiles = totalFiles
                        currentFile = "Complete"
                        startTime = batchStartTime
                        lastUpdateTime = System.currentTimeMillis()
                    }
                    tracker.writeProgress(completeData)

                    _extractStatus.value = "Extracted $totalFiles archives"
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Perform single file extraction
     */
    fun performExtraction(rpaFilePath: String, extractDirPath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val tracker = ProgressTracker(context)
                tracker.clearProgress()

                try {
                    // Initialize progress
                    val initialData = ProgressData().apply {
                        operation = "extract"
                        status = "in_progress"
                        startTime = System.currentTimeMillis()
                        lastUpdateTime = System.currentTimeMillis()
                        totalFiles = 0
                        processedFiles = 0
                        currentFile = "Starting extraction..."
                    }
                    tracker.writeProgress(initialData)

                    // Call Python extraction
                    val result = rpaModule.callAttr(
                        "extract_rpa",
                        rpaFilePath,
                        extractDirPath,
                        tracker.progressFilePath
                    )

                    if (result == null) {
                        throw Exception("Python function returned null")
                    }

                    val successObj = result.callAttr("__getitem__", "success")
                    val filesObj = result.callAttr("__getitem__", "files")

                    val success = successObj.toJava(Boolean::class.java)
                    val fileCount = filesObj.asList().size

                    _extractStatus.value = if (success) {
                        "Extracted $fileCount files"
                    } else {
                        "Extraction failed"
                    }

                } catch (e: Exception) {
                    e.printStackTrace()

                    // Update progress with error
                    try {
                        val errorData = ProgressData().apply {
                            operation = "extract"
                            status = "failed"
                            errorMessage = "Error: ${e.message}"
                        }
                        tracker.writeProgress(errorData)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        }
    }

    /**
     * Perform batch creation from multiple sources
     */
    fun performBatchCreation(sourcePaths: ArrayList<String>, outputFilePath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val tracker = ProgressTracker(context)
                tracker.clearProgress()

                var tempDir: File? = null
                try {
                    val batchStartTime = System.currentTimeMillis()

                    // Create temporary directory
                    tempDir = File(context.cacheDir, "rpa_batch_temp_$batchStartTime")
                    if (!tempDir.mkdir()) {
                        throw Exception("Failed to create temporary directory")
                    }

                    val totalItems = sourcePaths.size

                    // Copy all items to temp directory
                    for (i in sourcePaths.indices) {
                        val sourcePath = sourcePaths[i]
                        val sourceFile = File(sourcePath)
                        val itemName = sourceFile.name

                        // Update progress
                        val copyProgress = ProgressData().apply {
                            operation = "create"
                            currentBatchIndex = i + 1
                            totalBatchCount = totalItems
                            currentBatchFileName = itemName
                            status = "in_progress"
                            startTime = batchStartTime
                            lastUpdateTime = System.currentTimeMillis()
                            totalFiles = 0
                            processedFiles = 0
                            currentFile = "Copying to temp: $itemName"
                        }
                        tracker.writeProgress(copyProgress)

                        // Copy to temp
                        val destFile = File(tempDir, itemName)
                        if (sourceFile.isDirectory) {
                            copyDirectory(sourceFile, destFile)
                        } else {
                            copyFile(sourceFile, destFile)
                        }
                    }

                    // Update progress for RPA creation
                    val createProgress = ProgressData().apply {
                        operation = "create"
                        currentBatchIndex = totalItems
                        totalBatchCount = totalItems
                        currentBatchFileName = "Creating final archive..."
                        status = "in_progress"
                        startTime = batchStartTime
                        lastUpdateTime = System.currentTimeMillis()
                        totalFiles = 0
                        processedFiles = 0
                        currentFile = "Building RPA from $totalItems items"
                    }
                    tracker.writeProgress(createProgress)

                    // Call Python creation
                    val result = rpaModule.callAttr(
                        "create_rpa",
                        tempDir.absolutePath,
                        outputFilePath,
                        3,
                        0xDEADBEEF.toInt(),
                        tracker.progressFilePath
                    )

                    if (result == null) {
                        throw Exception("Python function returned null")
                    }

                    val successObj = result.callAttr("__getitem__", "success")
                    val filesObj = result.callAttr("__getitem__", "files")

                    val success = successObj.toJava(Boolean::class.java)
                    val fileCount = filesObj.asList().size

                    _createStatus.value = if (success) {
                        "Created archive with $fileCount files from $totalItems sources"
                    } else {
                        "Creation failed"
                    }

                } catch (e: Exception) {
                    e.printStackTrace()

                    // Update progress with error
                    try {
                        val errorData = ProgressData().apply {
                            operation = "create"
                            status = "failed"
                            errorMessage = "Error: ${e.message}"
                        }
                        tracker.writeProgress(errorData)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                } finally {
                    // Clean up temp directory
                    tempDir?.let { if (it.exists()) deleteRecursive(it) }
                }
            }
        }
    }

    /**
     * Perform single archive creation
     */
    fun performCreation(sourceDirPath: String, outputFilePath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val tracker = ProgressTracker(context)
                tracker.clearProgress()

                try {
                    // Initialize progress
                    val initialData = ProgressData().apply {
                        operation = "create"
                        status = "in_progress"
                        startTime = System.currentTimeMillis()
                        lastUpdateTime = System.currentTimeMillis()
                        totalFiles = 0
                        processedFiles = 0
                        currentFile = "Starting creation..."
                    }
                    tracker.writeProgress(initialData)

                    // Call Python creation
                    val result = rpaModule.callAttr(
                        "create_rpa",
                        sourceDirPath,
                        outputFilePath,
                        3,
                        0xDEADBEEF.toInt(),
                        tracker.progressFilePath
                    )

                    if (result == null) {
                        throw Exception("Python function returned null")
                    }

                    val successObj = result.callAttr("__getitem__", "success")
                    val filesObj = result.callAttr("__getitem__", "files")

                    val success = successObj.toJava(Boolean::class.java)
                    val fileCount = filesObj.asList().size

                    _createStatus.value = if (success) {
                        "Created archive with $fileCount files"
                    } else {
                        "Creation failed"
                    }

                } catch (e: Exception) {
                    e.printStackTrace()

                    // Update progress with error
                    try {
                        val errorData = ProgressData().apply {
                            operation = "create"
                            status = "failed"
                            errorMessage = "Error: ${e.message}"
                        }
                        tracker.writeProgress(errorData)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        }
    }

    /**
     * Perform decompilation of RPYC files
     */
    fun performDecompile(sourceDirPath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val tracker = ProgressTracker(context)
                tracker.clearProgress()

                try {
                    // Initialize progress
                    val initialData = ProgressData().apply {
                        operation = "decompile"
                        status = "in_progress"
                        startTime = System.currentTimeMillis()
                        lastUpdateTime = System.currentTimeMillis()
                        totalFiles = 0
                        processedFiles = 0
                        currentFile = "Starting decompilation..."
                    }
                    tracker.writeProgress(initialData)

                    // Call Python decompilation
                    val result = decompileModule.callAttr(
                        "decompile_directory",
                        sourceDirPath,
                        tracker.progressFilePath
                    )

                    if (result == null) {
                        throw Exception("Python function returned null")
                    }

                    val successObj = result.callAttr("__getitem__", "success")
                    val messageObj = result.callAttr("__getitem__", "message")
                    val statsObj = result.callAttr("__getitem__", "stats")

                    val success = successObj.toJava(Boolean::class.java)
                    val message = messageObj.toJava(String::class.java)

                    // Extract stats
                    val totalObj = statsObj.callAttr("__getitem__", "total")
                    val successCountObj = statsObj.callAttr("__getitem__", "success")
                    val skippedObj = statsObj.callAttr("__getitem__", "skipped")
                    val failedObj = statsObj.callAttr("__getitem__", "failed")

                    val total = totalObj.toJava(Int::class.java)
                    val successCount = successCountObj.toJava(Int::class.java)
                    val skipped = skippedObj.toJava(Int::class.java)
                    val failed = failedObj.toJava(Int::class.java)

                    _decompileStatus.value = if (success) {
                        "Decompiled $total files ($successCount success, $skipped skipped, $failed failed)"
                    } else {
                        "Decompilation failed: $message"
                    }

                } catch (e: Exception) {
                    e.printStackTrace()

                    // Update progress with error
                    try {
                        val errorData = ProgressData().apply {
                            operation = "decompile"
                            status = "failed"
                            errorMessage = "Error: ${e.message}"
                        }
                        tracker.writeProgress(errorData)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }

                    _decompileStatus.value = "Decompilation error: ${e.message}"
                }
            }
        }
    }

    /**
     * Set cards enabled state
     */
    fun setCardsEnabled(enabled: Boolean) {
        _cardsEnabled.value = enabled
    }

    // Helper methods for file operations
    private fun copyFile(source: File, dest: File) {
        FileInputStream(source).use { fis ->
            FileOutputStream(dest).use { fos ->
                val buffer = ByteArray(8192)
                var length: Int
                while (fis.read(buffer).also { length = it } > 0) {
                    fos.write(buffer, 0, length)
                }
            }
        }
    }

    private fun copyDirectory(source: File, dest: File) {
        if (!dest.mkdir()) {
            throw Exception("Failed to create directory: ${dest.absolutePath}")
        }

        source.listFiles()?.forEach { file ->
            val destFile = File(dest, file.name)
            if (file.isDirectory) {
                copyDirectory(file, destFile)
            } else {
                copyFile(file, destFile)
            }
        }
    }

    private fun deleteRecursive(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        file.delete()
    }
}
