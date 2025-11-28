package com.renpytool

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.renpytool.ui.ProgressScreen
import com.renpytool.ui.theme.RenpytoolTheme
import com.renpytool.viewmodel.ProgressViewModel

/**
 * Activity that displays real-time progress during RPA operations using Compose UI
 * Polls progress file every 500ms and updates UI via ViewModel
 */
class ProgressActivity : ComponentActivity() {

    private val viewModel: ProgressViewModel by viewModels()
    private var currentThemeMode: MainViewModel.ThemeMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        enableEdgeToEdge()

        // Keep screen on during operations
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Get intent extras
        val batchMode = intent.getBooleanExtra("BATCH_MODE", false)
        val batchTotal = intent.getIntExtra("BATCH_TOTAL", 0)
        val batchFiles = intent.getStringArrayListExtra("BATCH_FILES")
        val extractPath = intent.getStringExtra("EXTRACT_PATH")
        val expectedOperation = intent.getStringExtra("OPERATION_TYPE")

        // Initialize ViewModel
        viewModel.initialize(batchMode, batchTotal, batchFiles, extractPath, expectedOperation)

        // Store initial theme mode
        currentThemeMode = ThemeUtils.getThemeMode(this)

        setContent {
            val themeMode = ThemeUtils.getThemeMode(this)
            val darkTheme = ThemeUtils.shouldUseDarkTheme(themeMode)

            RenpytoolTheme(darkTheme = darkTheme) {
                val uiState by viewModel.uiState.collectAsState()

                ProgressScreen(
                    uiState = uiState,
                    onDecompileClick = {
                        // Pass extraction path back to MainActivity for chaining
                        val resultIntent = Intent()
                        resultIntent.putExtra("CHAIN_OPERATION", "decompile")
                        resultIntent.putExtra("CHAIN_PATH", extractPath)
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                    onDoneClick = {
                        setResult(if (uiState.isFailed) Activity.RESULT_CANCELED else Activity.RESULT_OK)
                        finish()
                    },
                    onCancelClick = {
                        // Stop the background service
                        val stopIntent = Intent(this, OperationService::class.java).apply {
                            action = OperationService.ACTION_STOP
                        }
                        startService(stopIntent)

                        // Cancel the operation and close activity
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkThemeChange()
    }

    private fun checkThemeChange() {
        val newThemeMode = ThemeUtils.getThemeMode(this)
        if (currentThemeMode != null && currentThemeMode != newThemeMode) {
            recreate()
        }
        currentThemeMode = newThemeMode
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Ignore back button during progress - user must wait for completion
    }
}
