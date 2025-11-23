package com.renpytool.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Environment
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class FilePickerUiState(
    val currentDirectory: File = Environment.getExternalStorageDirectory(),
    val fileItems: List<FileItem> = emptyList(),
    val selectedFiles: Set<File> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val mode: Int = MODE_FILE,
    val fileFilter: String? = null
) {
    companion object {
        const val MODE_FILE = 0
        const val MODE_DIRECTORY = 1
    }
}

data class FileItem(
    val name: String,
    val file: File,
    val isDirectory: Boolean = file.isDirectory,
    val isParent: Boolean = name == "..",
    val apkIcon: Bitmap? = null
) : Comparable<FileItem> {
    constructor(file: File, apkIcon: Bitmap? = null) : this(file.name, file, file.isDirectory, false, apkIcon)

    override fun compareTo(other: FileItem): Int {
        // Sort: parent (..) first, then directories, then files, all alphabetically
        return when {
            isParent && !other.isParent -> -1
            !isParent && other.isParent -> 1
            isDirectory && !other.isDirectory -> -1
            !isDirectory && other.isDirectory -> 1
            else -> name.compareTo(other.name, ignoreCase = true)
        }
    }
}

class FilePickerViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(FilePickerUiState())
    val uiState: StateFlow<FilePickerUiState> = _uiState.asStateFlow()
    private val context = application.applicationContext

    fun initialize(mode: Int, fileFilter: String?, startPath: String?) {
        val startDir = if (!startPath.isNullOrEmpty()) {
            val customStart = File(startPath)
            if (customStart.exists() && customStart.isDirectory) {
                customStart
            } else {
                Environment.getExternalStorageDirectory()
            }
        } else {
            Environment.getExternalStorageDirectory()
        }

        _uiState.update { it.copy(mode = mode, fileFilter = fileFilter) }
        navigateToDirectory(startDir)
    }

    fun navigateToDirectory(directory: File?) {
        if (directory == null || !directory.exists() || !directory.isDirectory) {
            return
        }

        val fileItems = mutableListOf<FileItem>()

        // Add parent navigation if not at root
        if (directory.parent != null) {
            fileItems.add(FileItem("..", directory.parentFile!!))
        }

        // List files and folders
        val files = directory.listFiles()
        if (files != null) {
            for (file in files) {
                // Skip hidden files
                if (file.name.startsWith(".")) {
                    continue
                }

                // Apply file filter if in FILE mode (case-insensitive)
                val state = _uiState.value

                // Special handling for compress mode - show directories AND .apk files
                if (state.mode == FilePickerUiState.MODE_DIRECTORY &&
                    state.fileFilter == "compress_source" &&
                    !file.isDirectory) {
                    // In compress mode, only show .apk files (skip other files)
                    if (!file.name.lowercase().endsWith(".apk")) {
                        continue
                    }
                } else if (state.mode == FilePickerUiState.MODE_FILE &&
                    !file.isDirectory &&
                    state.fileFilter != null) {
                    // Normal file filtering
                    // Special case: .rpa filter also accepts .arc files
                    if (state.fileFilter == ".rpa") {
                        val filename = file.name.lowercase()
                        if (!filename.endsWith(".rpa") && !filename.endsWith(".arc")) {
                            continue
                        }
                    } else if (!file.name.lowercase().endsWith(state.fileFilter.lowercase())) {
                        continue
                    }
                }

                // Extract APK icon if this is an APK file
                val apkIcon = if (file.name.lowercase().endsWith(".apk")) {
                    extractApkIcon(file)
                } else {
                    null
                }

                fileItems.add(FileItem(file, apkIcon))
            }
        }

        // Sort items
        fileItems.sort()

        _uiState.update {
            it.copy(
                currentDirectory = directory,
                fileItems = fileItems,
                selectedFiles = emptySet()
            )
        }
    }

    /**
     * Extract icon from APK file
     */
    private fun extractApkIcon(apkFile: File): Bitmap? {
        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)

            if (packageInfo != null) {
                packageInfo.applicationInfo.sourceDir = apkFile.absolutePath
                packageInfo.applicationInfo.publicSourceDir = apkFile.absolutePath
                val icon = packageInfo.applicationInfo.loadIcon(pm)
                icon.toBitmap()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun navigateUp() {
        val current = _uiState.value.currentDirectory
        current.parentFile?.let { navigateToDirectory(it) }
    }

    fun toggleSelection(file: File) {
        _uiState.update { state ->
            val newSelected = if (file in state.selectedFiles) {
                state.selectedFiles - file
            } else {
                state.selectedFiles + file
            }
            state.copy(selectedFiles = newSelected)
        }
    }

    fun enterMultiSelectMode() {
        _uiState.update { it.copy(isMultiSelectMode = true) }
    }

    fun exitMultiSelectMode() {
        _uiState.update {
            it.copy(
                isMultiSelectMode = false,
                selectedFiles = emptySet()
            )
        }
    }

    fun clearSelections() {
        _uiState.update { it.copy(selectedFiles = emptySet()) }
    }
}
