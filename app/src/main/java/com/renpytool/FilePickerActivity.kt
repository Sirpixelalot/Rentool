package com.renpytool

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.renpytool.ui.FilePickerScreen
import com.renpytool.ui.theme.RenpytoolTheme
import com.renpytool.viewmodel.FileItem
import com.renpytool.viewmodel.FilePickerUiState
import com.renpytool.viewmodel.FilePickerViewModel

/**
 * File picker activity using Compose UI and ViewModel architecture
 * Returns selected file/folder path as a String
 */
class FilePickerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_FILE_FILTER = "file_filter"
        const val EXTRA_TITLE = "title"
        const val EXTRA_START_DIR = "start_dir"
        const val EXTRA_SELECTED_PATH = "selected_path"
        const val EXTRA_SELECTED_PATHS = "selected_paths" // For multi-select

        const val MODE_FILE = 0
        const val MODE_DIRECTORY = 1
    }

    private val viewModel: FilePickerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get intent extras
        val mode = intent.getIntExtra(EXTRA_MODE, MODE_FILE)
        val fileFilter = intent.getStringExtra(EXTRA_FILE_FILTER)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Select File"
        val startPath = intent.getStringExtra(EXTRA_START_DIR)

        // Initialize ViewModel
        viewModel.initialize(mode, fileFilter, startPath)

        setContent {
            RenpytoolTheme {
                val uiState by viewModel.uiState.collectAsState()

                FilePickerScreen(
                    uiState = uiState,
                    title = title,
                    onFileItemClick = { item -> onFileItemClick(item, uiState) },
                    onFileItemLongClick = { item -> onFileItemLongClick(item) },
                    onNavigationClick = { handleNavigationClick(uiState) },
                    onFabClick = { confirmSelection(uiState) }
                )
            }
        }
    }

    private fun onFileItemClick(item: FileItem, uiState: FilePickerUiState) {
        if (uiState.isMultiSelectMode) {
            // In multi-select mode, toggle selection
            if (!item.isParent) {
                viewModel.toggleSelection(item.file)
            }
        } else {
            // Normal mode navigation
            when {
                item.isParent -> {
                    // Navigate up
                    viewModel.navigateToDirectory(item.file)
                }
                item.isDirectory -> {
                    // Navigate into directory
                    viewModel.navigateToDirectory(item.file)
                }
                else -> {
                    // File selected (only in FILE mode)
                    if (uiState.mode == FilePickerUiState.MODE_FILE) {
                        selectFile(item.file)
                    }
                }
            }
        }
    }

    private fun onFileItemLongClick(item: FileItem) {
        if (!item.isParent) {
            viewModel.enterMultiSelectMode()
            viewModel.toggleSelection(item.file)
            Toast.makeText(this, "Multi-select mode enabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleNavigationClick(uiState: FilePickerUiState) {
        if (uiState.isMultiSelectMode) {
            viewModel.exitMultiSelectMode()
        } else {
            finish()
        }
    }

    private fun confirmSelection(uiState: FilePickerUiState) {
        if (uiState.isMultiSelectMode) {
            // Multi-select mode: return all selected paths
            val paths = ArrayList<String>()
            for (file in uiState.selectedFiles) {
                paths.add(file.absolutePath)
            }
            val resultIntent = Intent()
            resultIntent.putStringArrayListExtra(EXTRA_SELECTED_PATHS, paths)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        } else {
            // Single select mode (directory mode)
            selectCurrentDirectory(uiState)
        }
    }

    private fun selectFile(file: java.io.File) {
        // Check if we should open the editor instead of returning
        val openEditor = intent.getBooleanExtra("OPEN_EDITOR", false)

        if (openEditor && file.name.endsWith(".rpy")) {
            // Open .rpy file in editor
            val editorIntent = Intent(this, RpyEditorActivity::class.java)
            editorIntent.putExtra(RpyEditorActivity.EXTRA_FILE_PATH, file.absolutePath)
            startActivity(editorIntent)
            finish()
        } else {
            // Return selected file to calling activity
            val resultIntent = Intent()
            resultIntent.putExtra(EXTRA_SELECTED_PATH, file.absolutePath)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun selectCurrentDirectory(uiState: FilePickerUiState) {
        val resultIntent = Intent()
        resultIntent.putExtra(EXTRA_SELECTED_PATH, uiState.currentDirectory.absolutePath)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val uiState = viewModel.uiState.value

        // Exit multi-select mode if active
        if (uiState.isMultiSelectMode) {
            viewModel.exitMultiSelectMode()
            return
        }

        // Navigate up if not at root, otherwise finish
        if (uiState.currentDirectory.parent != null) {
            viewModel.navigateUp()
        } else {
            super.onBackPressed()
        }
    }
}
