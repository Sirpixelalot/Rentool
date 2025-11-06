package com.renpytool;

/**
 * Data model for tracking RPA operation progress
 */
public class ProgressData {
    public String operation;           // "extract" or "create"
    public int totalFiles;             // Total files to process
    public int processedFiles;         // Files processed so far
    public String currentFile;         // Current file name being processed
    public long startTime;             // Timestamp when started (milliseconds)
    public long lastUpdateTime;        // Last update timestamp (milliseconds)
    public String status;              // "in_progress", "completed", "failed"
    public String errorMessage;        // If failed

    // Batch operation support
    public int currentBatchIndex;      // Current item in batch (1-based, 0 = no batch)
    public int totalBatchCount;        // Total items in batch (0 = no batch)
    public String currentBatchFileName; // Name of current batch file/folder

    // For smoothed ETA calculation
    private long lastEtaUpdateTime = 0;
    private long lastEtaValue = 0;

    /**
     * Get completion percentage (0-100)
     */
    public int getPercentage() {
        if (totalFiles == 0) return 0;
        return (int) ((processedFiles * 100.0) / totalFiles);
    }

    /**
     * Get elapsed time in milliseconds since operation started
     */
    public long getElapsedMs() {
        if (startTime == 0) return 0;
        long currentTime = lastUpdateTime > 0 ? lastUpdateTime : System.currentTimeMillis();
        return currentTime - startTime;
    }

    /**
     * Get processing speed in files per second
     */
    public double getFilesPerSecond() {
        long elapsedMs = getElapsedMs();
        if (elapsedMs == 0 || processedFiles == 0) return 0;
        return (processedFiles * 1000.0) / elapsedMs;
    }

    /**
     * Get estimated total time in milliseconds (smoothed)
     * Only updates if change is more than 10 seconds
     */
    public long getEstimatedTotalMs() {
        if (processedFiles == 0 || totalFiles == 0) return 0;

        long elapsedMs = getElapsedMs();
        long rawEstimate = (elapsedMs * totalFiles) / processedFiles;

        // Smooth the ETA - only update if change is significant (>10 seconds)
        long currentTime = System.currentTimeMillis();
        if (lastEtaUpdateTime == 0 || Math.abs(rawEstimate - lastEtaValue) > 10000) {
            lastEtaValue = rawEstimate;
            lastEtaUpdateTime = currentTime;
        }

        return lastEtaValue;
    }

    /**
     * Get estimated time remaining in milliseconds (smoothed)
     */
    public long getEtaMs() {
        long estimatedTotal = getEstimatedTotalMs();
        long elapsed = getElapsedMs();
        long remaining = estimatedTotal - elapsed;
        return remaining > 0 ? remaining : 0;
    }

    /**
     * Format milliseconds to human-readable time string
     * e.g., "2m 15s", "45s", "1h 5m"
     */
    public static String formatTime(long milliseconds) {
        if (milliseconds < 0) return "calculating...";

        long seconds = milliseconds / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }

        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;

        if (minutes < 60) {
            if (remainingSeconds > 0) {
                return minutes + "m " + remainingSeconds + "s";
            }
            return minutes + "m";
        }

        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        if (remainingMinutes > 0) {
            return hours + "h " + remainingMinutes + "m";
        }
        return hours + "h";
    }

    /**
     * Check if operation is still in progress
     */
    public boolean isInProgress() {
        return "in_progress".equals(status);
    }

    /**
     * Check if operation completed successfully
     */
    public boolean isCompleted() {
        return "completed".equals(status);
    }

    /**
     * Check if operation failed
     */
    public boolean isFailed() {
        return "failed".equals(status);
    }
}
