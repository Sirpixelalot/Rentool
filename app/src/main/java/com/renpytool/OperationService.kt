package com.renpytool

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File

/**
 * Foreground service that runs RPA operations in the background
 * and shows progress notifications
 */
class OperationService : Service() {

    companion object {
        private const val TAG = "OperationService"
        private const val CHANNEL_ID = "operation_progress"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START_EXTRACTION = "com.renpytool.START_EXTRACTION"
        const val ACTION_START_DECOMPILATION = "com.renpytool.START_DECOMPILATION"
        const val ACTION_START_COMPRESSION = "com.renpytool.START_COMPRESSION"
        const val ACTION_START_CREATION = "com.renpytool.START_CREATION"
        const val ACTION_STOP = "com.renpytool.STOP"

        const val EXTRA_SOURCE_PATH = "source_path"
        const val EXTRA_OUTPUT_PATH = "output_path"
        const val EXTRA_RPA_PATHS = "rpa_paths"
        const val EXTRA_SETTINGS = "settings"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var progressPollingJob: Job? = null
    private val tracker by lazy { ProgressTracker(this) }
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_EXTRACTION -> {
                val rpaPath = intent.getStringExtra(EXTRA_SOURCE_PATH)
                val outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH)
                if (rpaPath != null && outputPath != null) {
                    startForeground(NOTIFICATION_ID, createNotification("Starting extraction...", 0))
                    startProgressMonitoring("Extracting RPA")
                }
            }
            ACTION_START_DECOMPILATION -> {
                val sourcePath = intent.getStringExtra(EXTRA_SOURCE_PATH)
                if (sourcePath != null) {
                    startForeground(NOTIFICATION_ID, createNotification("Starting decompilation...", 0))
                    startProgressMonitoring("Decompiling RPYC")
                }
            }
            ACTION_START_COMPRESSION -> {
                val sourcePath = intent.getStringExtra(EXTRA_SOURCE_PATH)
                val outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH)
                if (sourcePath != null && outputPath != null) {
                    startForeground(NOTIFICATION_ID, createNotification("Starting compression...", 0))
                    startProgressMonitoring("Compressing Game")
                }
            }
            ACTION_START_CREATION -> {
                val sourcePath = intent.getStringExtra(EXTRA_SOURCE_PATH)
                val outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH)
                if (sourcePath != null && outputPath != null) {
                    startForeground(NOTIFICATION_ID, createNotification("Starting RPA creation...", 0))
                    startProgressMonitoring("Creating RPA")
                }
            }
            ACTION_STOP -> {
                stopForegroundService()
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Operation Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of RPA operations"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String, progress: Int, maxProgress: Int = 100): Notification {
        // Create explicit intent for security - specifies exact component
        val intent = Intent(this, ProgressActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Explicitly set the component to make this a fully explicit intent
            component = android.content.ComponentName(this@OperationService, ProgressActivity::class.java)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rentool")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_compress)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(maxProgress, progress, progress == 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startProgressMonitoring(operationName: String) {
        progressPollingJob?.cancel()
        progressPollingJob = serviceScope.launch(Dispatchers.IO) {
            var lastProgress = -1
            var lastText = ""
            var noDataCount = 0

            while (isActive) {
                try {
                    val data = tracker.readProgress()

                    if (data != null) {
                        noDataCount = 0 // Reset counter when we get data
                        val progress = data.getPercentage()

                        // Get operation-specific name
                        val currentOperationName = when (data.operation) {
                            "extract_apk" -> "Extracting APK"
                            "compress_assets" -> "Compressing Assets"
                            "repackage_apk" -> "Repackaging APK"
                            "align_apk" -> "Aligning APK"
                            "sign_apk" -> "Signing APK"
                            "compress_apk" -> "Compressing APK"
                            else -> operationName
                        }

                        val text = buildString {
                            append(currentOperationName)
                            if (data.totalFiles > 0) {
                                append(": ${data.processedFiles}/${data.totalFiles} files")
                            }
                            if (data.currentFile?.isNotEmpty() == true && !data.currentFile.contains("Complete")) {
                                append(" - ")
                                val fileName = File(data.currentFile).name
                                append(fileName.take(30))
                            }
                        }

                        // Only update notification if progress or text changed
                        if (progress != lastProgress || text != lastText) {
                            notificationManager.notify(
                                NOTIFICATION_ID,
                                createNotification(text, progress)
                            )
                            lastProgress = progress
                            lastText = text
                        }

                        // Check if operation completed or failed
                        if (data.isCompleted() || data.isFailed()) {
                            val finalText = if (data.isCompleted()) {
                                "$currentOperationName completed"
                            } else {
                                "$currentOperationName failed: ${data.errorMessage ?: "Unknown error"}"
                            }

                            // Update notification one last time for completion
                            notificationManager.notify(
                                NOTIFICATION_ID,
                                createNotification(finalText, 100).apply {
                                    // Make notification dismissible when done
                                    flags = flags and Notification.FLAG_ONGOING_EVENT.inv()
                                }
                            )

                            // Stop service but keep notification visible
                            stopForegroundService(keepNotification = true)
                            break
                        }
                    } else {
                        // No progress data yet - show initializing state
                        noDataCount++
                        if (noDataCount == 1) {
                            // First time with no data - update notification to show we're preparing
                            notificationManager.notify(
                                NOTIFICATION_ID,
                                createNotification("$operationName: Preparing...", 0)
                            )
                        }
                    }

                    delay(500) // Poll every 500ms
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring progress", e)
                }
            }
        }
    }

    private fun stopForegroundService(keepNotification: Boolean = false) {
        progressPollingJob?.cancel()

        // Cancel the running operation in MainViewModel
        // This stops the Python backend from continuing to write progress
        MainViewModel.cancelActiveOperation()

        // Clear progress file to prevent stale data on next operation
        tracker.clearProgress()

        if (keepNotification) {
            // Detach notification so it stays visible after service stops
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            // Remove notification when service stops
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        progressPollingJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
