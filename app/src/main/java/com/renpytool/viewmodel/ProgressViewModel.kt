package com.renpytool.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.renpytool.ProgressData
import com.renpytool.ProgressTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

data class ProgressUiState(
    val operationType: String = "Initializing...",
    val percentage: Int = 0,
    val fileCount: String = "0/0",
    val currentFile: String = "Initializing...",
    val speed: String = "calculating...",
    val eta: String = "calculating...",
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null,
    val extractPath: String? = null,
    val rpycCount: Int = 0,
    val totalFiles: Int = 0,
    val elapsedMs: Long = 0,
    val operation: String = ""
)

class ProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    private val tracker = ProgressTracker(application)
    private var pollingJob: Job? = null

    // Batch information
    private var isBatchMode = false
    private var batchTotal = 0
    private var batchFileNames: List<String>? = null
    private var currentBatchIndex = 1
    private var lastStatus = "in_progress"

    private companion object {
        const val POLL_INTERVAL_MS = 500L
    }

    fun initialize(
        batchMode: Boolean,
        batchTotal: Int,
        batchFiles: List<String>?,
        extractPath: String?
    ) {
        isBatchMode = batchMode
        this.batchTotal = batchTotal
        batchFileNames = batchFiles
        startProgressPolling(extractPath)
    }

    private fun startProgressPolling(extractPath: String?) {
        pollingJob = viewModelScope.launch {
            while (true) {
                val data = tracker.readProgress()

                if (data != null) {
                    updateUiState(data, extractPath)

                    if (data.isCompleted() || data.isFailed()) {
                        // Operation complete, stop polling
                        break
                    }
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun updateUiState(data: ProgressData, extractPath: String?) {
        // Track batch progress independently
        if (isBatchMode && batchTotal > 0) {
            if ("completed" == data.status && "completed" != lastStatus) {
                if (currentBatchIndex < batchTotal) {
                    currentBatchIndex++
                }
            }
            lastStatus = data.status
        }

        // Determine operation type
        val isCopyingPhase = data.currentFile?.lowercase()?.let {
            it.contains("copy") || it.contains("starting copy")
        } ?: false

        var operationType = when {
            isCopyingPhase -> {
                if (data.operation == "extract") {
                    "Copying files to destination..."
                } else {
                    "Copying archive to destination..."
                }
            }
            data.operation == "extract" -> "Extracting RPA..."
            data.operation == "decompile" -> "Decompiling RPYC..."
            else -> "Creating RPA Archive..."
        }

        // Add batch information
        if (isBatchMode && batchTotal > 0) {
            val batchInfo = String.format(Locale.US, " (%d of %d)", currentBatchIndex, batchTotal)
            operationType += batchInfo

            if (batchFileNames != null && currentBatchIndex > 0 && currentBatchIndex <= batchFileNames!!.size) {
                operationType += "\n" + batchFileNames!![currentBatchIndex - 1]
            }
        }

        val percentage = data.getPercentage()
        val fileCount = String.format(Locale.US, "%d/%d", data.processedFiles, data.totalFiles)
        val currentFile = data.currentFile?.takeIf { it.isNotEmpty() } ?: "Initializing..."

        val speed = data.filesPerSecond.let {
            if (it > 0) {
                String.format(Locale.US, "%.1f files/sec", it)
            } else {
                "calculating..."
            }
        }

        val eta = data.etaMs.let {
            if (it > 0 && data.processedFiles > 0) {
                "~" + ProgressData.formatTime(it)
            } else {
                "calculating..."
            }
        }

        // Handle completion
        val isCompleted = data.isCompleted()
        val isFailed = data.isFailed()
        val rpycCount = if (isCompleted && "extract".equals(data.operation, ignoreCase = true) && extractPath != null) {
            countRpycFiles(File(extractPath))
        } else {
            0
        }

        _uiState.update {
            ProgressUiState(
                operationType = operationType,
                percentage = percentage,
                fileCount = fileCount,
                currentFile = currentFile,
                speed = speed,
                eta = eta,
                isCompleted = isCompleted,
                isFailed = isFailed,
                errorMessage = data.errorMessage?.takeIf { it.isNotEmpty() },
                extractPath = extractPath,
                rpycCount = rpycCount,
                totalFiles = data.totalFiles,
                elapsedMs = data.getElapsedMs(),
                operation = data.operation
            )
        }
    }

    private fun countRpycFiles(directory: File): Int {
        if (!directory.exists() || !directory.isDirectory) {
            return 0
        }

        var count = 0
        directory.listFiles()?.forEach { file ->
            count += when {
                file.isDirectory -> countRpycFiles(file)
                file.name.lowercase().endsWith(".rpyc") -> 1
                else -> 0
            }
        }
        return count
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
