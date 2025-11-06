package com.renpytool;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/**
 * Activity that displays real-time progress during RPA operations
 * Polls progress file every 500ms and updates UI
 */
public class ProgressActivity extends AppCompatActivity {

    private TextView tvOperation, tvPercentage, tvFileCount, tvCurrentFile, tvSpeed, tvEta;
    private ProgressBar progressBar;

    private ProgressTracker tracker;
    private Handler handler;
    private boolean isRunning = true;

    private static final int POLL_INTERVAL_MS = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress);

        // Keep screen on during operations to prevent Android from pausing/killing the app
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Initialize views
        tvOperation = findViewById(R.id.tv_operation);
        tvPercentage = findViewById(R.id.tv_percentage);
        tvFileCount = findViewById(R.id.tv_file_count);
        tvCurrentFile = findViewById(R.id.tv_current_file);
        tvSpeed = findViewById(R.id.tv_speed);
        tvEta = findViewById(R.id.tv_eta);
        progressBar = findViewById(R.id.progress_bar);

        // Initialize progress tracker
        tracker = new ProgressTracker(this);

        // Initialize handler for polling
        handler = new Handler(Looper.getMainLooper());

        // Start polling progress file
        startProgressPolling();
    }

    /**
     * Start polling the progress file every 500ms
     */
    private void startProgressPolling() {
        handler.postDelayed(() -> {
            if (isRunning) {
                ProgressData data = tracker.readProgress();

                if (data != null) {
                    updateUI(data);

                    // Check if operation is complete
                    if (data.isCompleted() || data.isFailed()) {
                        isRunning = false;
                        handleCompletion(data);
                        return;
                    }
                }

                // Continue polling
                startProgressPolling();
            }
        }, POLL_INTERVAL_MS);
    }

    /**
     * Update UI with current progress data
     */
    private void updateUI(ProgressData data) {
        // Update operation type - detect copying phase
        String operationType;
        boolean isCopyingPhase = data.currentFile != null &&
                (data.currentFile.toLowerCase().contains("copy") ||
                 data.currentFile.toLowerCase().contains("starting copy"));

        if (isCopyingPhase) {
            operationType = data.operation.equals("extract") ?
                    "Copying files to destination..." : "Copying archive to destination...";
        } else {
            operationType = data.operation.equals("extract") ?
                    "Extracting RPA..." : "Creating RPA Archive...";
        }

        // Add batch information if in batch mode
        if (data.totalBatchCount > 0 && data.currentBatchIndex > 0) {
            String batchInfo = String.format(Locale.US, " (%d of %d)",
                    data.currentBatchIndex, data.totalBatchCount);
            operationType += batchInfo;

            // Show batch file name if available
            if (data.currentBatchFileName != null && !data.currentBatchFileName.isEmpty()) {
                operationType += "\n" + data.currentBatchFileName;
            }
        }

        tvOperation.setText(operationType);

        // Update percentage
        int percentage = data.getPercentage();
        tvPercentage.setText(percentage + "%");
        progressBar.setProgress(percentage);

        // Update file count
        tvFileCount.setText(String.format(Locale.US, "%d/%d", data.processedFiles, data.totalFiles));

        // Update current file
        if (data.currentFile != null && !data.currentFile.isEmpty()) {
            tvCurrentFile.setText(data.currentFile);
        } else {
            tvCurrentFile.setText("Initializing...");
        }

        // Update processing speed
        double speed = data.getFilesPerSecond();
        if (speed > 0) {
            tvSpeed.setText(String.format(Locale.US, "%.1f files/sec", speed));
        } else {
            tvSpeed.setText("calculating...");
        }

        // Update ETA
        long etaMs = data.getEtaMs();
        if (etaMs > 0 && data.processedFiles > 0) {
            tvEta.setText("~" + ProgressData.formatTime(etaMs));
        } else {
            tvEta.setText("calculating...");
        }
    }

    /**
     * Handle operation completion or failure
     */
    private void handleCompletion(ProgressData data) {
        if (data.isCompleted()) {
            // Show success dialog
            new AlertDialog.Builder(this)
                    .setTitle("Success")
                    .setMessage(String.format(Locale.US,
                            "Operation completed successfully!\n\nProcessed %d files in %s",
                            data.totalFiles,
                            ProgressData.formatTime(data.getElapsedMs())))
                    .setPositiveButton("OK", (dialog, which) -> {
                        setResult(Activity.RESULT_OK);
                        finish();
                    })
                    .setCancelable(false)
                    .show();
        } else if (data.isFailed()) {
            // Show error dialog
            String errorMessage = data.errorMessage != null && !data.errorMessage.isEmpty()
                    ? data.errorMessage
                    : "Operation failed";

            new AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", (dialog, which) -> {
                        setResult(Activity.RESULT_CANCELED);
                        finish();
                    })
                    .setCancelable(false)
                    .show();
        }
    }

    /**
     * Disable back button - user must wait for operation to complete
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Ignore back button press
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Clean up resources when activity is destroyed
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}
