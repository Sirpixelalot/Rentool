package com.renpytool

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.renpytool.editor.EditorManager
import com.renpytool.editor.ui.BottomBarView
import com.renpytool.editor.ui.CodeEditorView
import com.renpytool.editor.ui.InfoBar
import com.renpytool.editor.ui.JumpToPositionDialog
import com.renpytool.editor.ui.RecentFilesDialog
import com.renpytool.editor.ui.SearchPanel
import com.renpytool.editor.ui.ToolbarView
import com.renpytool.editor.ui.WarningDialog
import com.renpytool.ui.theme.RenpytoolTheme
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor

class RpyEditorActivityNew : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
    }

    private lateinit var editorManager: EditorManager
    private lateinit var codeEditor: CodeEditor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Get file path from intent
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(this, "No file specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize editor manager
        editorManager = EditorManager(this)
        editorManager.activeFilePath = filePath

        // Create code editor
        codeEditor = editorManager.createCodeEditorView()

        // Read file content
        editorManager.readActiveFileContent { content, newText, isSourceChanged ->
            codeEditor.setText(content)

            if (isSourceChanged) {
                editorManager.showSourceFileWarningDialog {
                    codeEditor.setText(Content(newText))
                }
            }

            // Save last edited file location to SharedPreferences
            saveLastEditedFile(filePath)
        }

        setContent {
            RenpytoolTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BackHandler(enabled = editorManager.showSearchPanel) {
                        editorManager.hideSearchPanel(codeEditor)
                    }

                    LaunchedEffect(Unit) {
                        // Any initialization logic
                    }

                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                        editorManager.checkActiveFileValidity(
                            onSourceReload = {
                                codeEditor.setText(Content(it))
                            },
                            onFileNotFound = {
                                editorManager.getFileInstance()?.let { instance ->
                                    editorManager.fileInstanceList.remove(instance)
                                }
                                Toast.makeText(
                                    this@RpyEditorActivityNew,
                                    "File no longer exists",
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            }
                        )
                    }

                    if (editorManager.warningDialogProperties.showWarningDialog) {
                        WarningDialog(editorManager.warningDialogProperties)
                    }

                    if (editorManager.showJumpToPositionDialog) {
                        JumpToPositionDialog(codeEditor) {
                            editorManager.showJumpToPositionDialog = false
                        }
                    }

                    RecentFilesDialog(editorManager, codeEditor)

                    ToolbarView(editorManager, codeEditor, onBackPressedDispatcher)
                    HorizontalDivider()
                    InfoBar(editorManager.activitySubtitle)
                    CodeEditorView(codeEditor)
                    HorizontalDivider()
                    BottomBarView(editorManager, codeEditor, editorManager.getSymbols())

                    AnimatedVisibility(
                        visible = editorManager.showSearchPanel && editorManager.getFileInstance() != null,
                        enter = expandIn(expandFrom = Alignment.TopCenter) + slideInVertically(
                            initialOffsetY = { it }),
                        exit = shrinkOut(shrinkTowards = Alignment.BottomCenter) + slideOutVertically(
                            targetOffsetY = { it })
                    ) {
                        SearchPanel(codeEditor, editorManager.getFileInstance()!!.searcher)
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        if (editorManager.requireSaveCurrentFile) {
            editorManager.warningDialogProperties.apply {
                title = "Unsaved Changes"
                message = "You have unsaved changes. Do you want to save before exiting?"
                confirmText = "Save"
                dismissText = "Discard"
                onConfirm = {
                    editorManager.save(
                        onSaved = {
                            showWarningDialog = false
                            this@RpyEditorActivityNew.finish()
                        },
                        onFailed = {
                            Toast.makeText(
                                this@RpyEditorActivityNew,
                                "Failed to save",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
                onDismiss = {
                    showWarningDialog = false
                    this@RpyEditorActivityNew.finish()
                }
                showWarningDialog = true
            }
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Save the last edited file path to SharedPreferences
     * This allows the main screen to show the last edited file
     * and the file picker to remember the last location
     */
    private fun saveLastEditedFile(filePath: String) {
        val file = java.io.File(filePath)
        val prefs = getSharedPreferences("RentoolPrefs", MODE_PRIVATE)
        val editor = prefs.edit()

        // Save parent folder if it exists
        file.parentFile?.let { parentDir ->
            editor.putString("last_rpy_edit_folder", parentDir.absolutePath)
        }

        // Save file path
        editor.putString("last_rpy_edit_file", file.absolutePath)
        editor.apply()
    }
}
