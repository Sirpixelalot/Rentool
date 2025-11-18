package com.renpytool;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Utility class for reading and writing progress tracking data
 */
public class ProgressTracker {
    private static final String PROGRESS_FILE = "operation_progress.json";
    private final File progressFile;

    public ProgressTracker(Context context) {
        this.progressFile = new File(context.getCacheDir(), PROGRESS_FILE);
    }

    /**
     * Get the path to the progress file for Python to write to
     */
    public String getProgressFilePath() {
        return progressFile.getAbsolutePath();
    }

    /**
     * Write progress data to file
     * Called from Java side if needed
     */
    public void writeProgress(ProgressData data) throws IOException, JSONException {
        JSONObject json = new JSONObject();
        json.put("operation", data.operation);
        json.put("totalFiles", data.totalFiles);
        json.put("processedFiles", data.processedFiles);
        json.put("currentFile", data.currentFile != null ? data.currentFile : "");
        json.put("startTime", data.startTime);
        json.put("lastUpdateTime", data.lastUpdateTime);
        json.put("status", data.status);
        json.put("errorMessage", data.errorMessage != null ? data.errorMessage : "");

        // Batch operation fields
        json.put("currentBatchIndex", data.currentBatchIndex);
        json.put("totalBatchCount", data.totalBatchCount);
        json.put("currentBatchFileName", data.currentBatchFileName != null ? data.currentBatchFileName : "");

        // Compression fields
        json.put("originalSizeBytes", data.originalSizeBytes);
        json.put("compressedSizeBytes", data.compressedSizeBytes);

        try (FileOutputStream out = new FileOutputStream(progressFile)) {
            out.write(json.toString().getBytes());
        }
    }

    /**
     * Read progress data from file
     * Returns null if file doesn't exist or can't be read
     */
    public ProgressData readProgress() {
        if (!progressFile.exists()) {
            return null;
        }

        try (FileInputStream in = new FileInputStream(progressFile)) {
            byte[] buffer = new byte[(int) progressFile.length()];
            int bytesRead = in.read(buffer);
            if (bytesRead == -1) {
                return null;
            }

            String jsonString = new String(buffer, 0, bytesRead);
            JSONObject json = new JSONObject(jsonString);

            ProgressData data = new ProgressData();
            data.operation = json.optString("operation", "");
            data.totalFiles = json.optInt("totalFiles", 0);
            data.processedFiles = json.optInt("processedFiles", 0);
            data.currentFile = json.optString("currentFile", "");
            data.startTime = json.optLong("startTime", 0);
            data.lastUpdateTime = json.optLong("lastUpdateTime", 0);
            data.status = json.optString("status", "in_progress");
            data.errorMessage = json.optString("errorMessage", "");

            // Batch operation fields
            data.currentBatchIndex = json.optInt("currentBatchIndex", 0);
            data.totalBatchCount = json.optInt("totalBatchCount", 0);
            data.currentBatchFileName = json.optString("currentBatchFileName", "");

            // Compression fields
            data.originalSizeBytes = json.optLong("originalSizeBytes", 0);
            data.compressedSizeBytes = json.optLong("compressedSizeBytes", 0);

            return data;

        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Check if progress file exists
     */
    public boolean progressFileExists() {
        return progressFile.exists();
    }

    /**
     * Clear/delete the progress file
     */
    public void clearProgress() {
        if (progressFile.exists()) {
            progressFile.delete();
        }
    }

    /**
     * Initialize progress file with starting state
     * Called before launching Python operation
     */
    public void initializeProgress(String operation, int totalFiles) throws IOException, JSONException {
        ProgressData data = new ProgressData();
        data.operation = operation;
        data.totalFiles = totalFiles;
        data.processedFiles = 0;
        data.currentFile = "Initializing...";
        data.startTime = System.currentTimeMillis();
        data.lastUpdateTime = System.currentTimeMillis();
        data.status = "in_progress";
        data.errorMessage = "";

        writeProgress(data);
    }
}
