package com.renpytool;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private MaterialCardView cardExtract, cardCreate;
    private TextView tvStatus, tvExtractStatus, tvCreateStatus;
    private ProgressBar progressBar;

    private Python python;
    private PyObject rpaModule;

    private ExecutorService executorService;

    // File picker launchers
    private ActivityResultLauncher<Intent> extractRpaPickerLauncher;
    private ActivityResultLauncher<Intent> extractDirPickerLauncher;
    private ActivityResultLauncher<Intent> createSourcePickerLauncher;
    private ActivityResultLauncher<Intent> createOutputPickerLauncher;

    // Temporary storage for multi-step file picking
    private String selectedRpaPath;
    private ArrayList<String> selectedRpaPaths;  // For batch extraction
    private String selectedSourcePath;
    private ArrayList<String> selectedSourcePaths;  // For batch creation

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Python
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        python = Python.getInstance();
        rpaModule = python.getModule("rpa_wrapper");

        // Initialize executor service
        executorService = Executors.newSingleThreadExecutor();

        // Initialize views
        initViews();

        // Initialize file picker launchers
        initFilePickerLaunchers();

        // Check permissions
        checkPermissions();
    }

    private void initViews() {
        cardExtract = findViewById(R.id.card_extract);
        cardCreate = findViewById(R.id.card_create);
        tvStatus = findViewById(R.id.tv_status);
        tvExtractStatus = findViewById(R.id.tv_extract_status);
        tvCreateStatus = findViewById(R.id.tv_create_status);
        progressBar = findViewById(R.id.progress_bar);

        // Set up click listeners
        cardExtract.setOnClickListener(v -> startExtractFlow());
        cardCreate.setOnClickListener(v -> startCreateFlow());
    }


    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 and above - use MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                        .setTitle("Storage Permission Required")
                        .setMessage("This app needs access to manage files for RPA operations.")
                        .setPositiveButton("Grant", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        } else {
            // Android 10 and below
            List<String> permissionsNeeded = new ArrayList<>();

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }

            if (!permissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        permissionsNeeded.toArray(new String[0]),
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "Permissions are required for this app to work",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initFilePickerLaunchers() {
        // Extract: Pick RPA file(s)
        extractRpaPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        // Check for multi-select first
                        selectedRpaPaths = result.getData().getStringArrayListExtra(FilePickerActivity.EXTRA_SELECTED_PATHS);
                        if (selectedRpaPaths == null || selectedRpaPaths.isEmpty()) {
                            // Single selection
                            selectedRpaPath = result.getData().getStringExtra(FilePickerActivity.EXTRA_SELECTED_PATH);
                            selectedRpaPaths = null;
                        } else {
                            // Multi-selection
                            selectedRpaPath = null;
                        }
                        // Now pick extraction directory
                        launchExtractDirectoryPicker();
                    }
                });

        // Extract: Pick extraction directory
        extractDirPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String extractPath = result.getData().getStringExtra(FilePickerActivity.EXTRA_SELECTED_PATH);
                        // Check if batch or single extraction
                        if (selectedRpaPaths != null && !selectedRpaPaths.isEmpty()) {
                            // Batch extraction
                            performBatchExtraction(selectedRpaPaths, extractPath);
                        } else {
                            // Single extraction
                            performExtraction(selectedRpaPath, extractPath);
                        }
                    }
                });

        // Create: Pick source directory/directories
        createSourcePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        // Check for multi-select first
                        selectedSourcePaths = result.getData().getStringArrayListExtra(FilePickerActivity.EXTRA_SELECTED_PATHS);
                        if (selectedSourcePaths == null || selectedSourcePaths.isEmpty()) {
                            // Single selection
                            selectedSourcePath = result.getData().getStringExtra(FilePickerActivity.EXTRA_SELECTED_PATH);
                            selectedSourcePaths = null;
                        } else {
                            // Multi-selection
                            selectedSourcePath = null;
                        }
                        // Now ask for output file name
                        showOutputFileNameDialog();
                    }
                });
    }

    private void startExtractFlow() {
        // Launch file picker for RPA file
        Intent intent = new Intent(this, FilePickerActivity.class);
        intent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
        intent.putExtra(FilePickerActivity.EXTRA_FILE_FILTER, ".rpa");
        intent.putExtra(FilePickerActivity.EXTRA_TITLE, "Select RPA File");
        extractRpaPickerLauncher.launch(intent);
    }

    private void launchExtractDirectoryPicker() {
        Intent intent = new Intent(this, FilePickerActivity.class);
        intent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIRECTORY);
        intent.putExtra(FilePickerActivity.EXTRA_TITLE, "Select Extraction Folder");

        // Default to the directory containing the selected RPA file(s)
        String startDir = null;
        if (selectedRpaPaths != null && !selectedRpaPaths.isEmpty()) {
            // Multi-select: use parent directory of first selected RPA
            java.io.File firstRpa = new java.io.File(selectedRpaPaths.get(0));
            startDir = firstRpa.getParent();
        } else if (selectedRpaPath != null) {
            // Single select: use parent directory of selected RPA
            java.io.File rpaFile = new java.io.File(selectedRpaPath);
            startDir = rpaFile.getParent();
        }

        if (startDir != null) {
            intent.putExtra(FilePickerActivity.EXTRA_START_DIR, startDir);
        }

        extractDirPickerLauncher.launch(intent);
    }

    private void startCreateFlow() {
        // Launch file picker for source directory
        Intent intent = new Intent(this, FilePickerActivity.class);
        intent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIRECTORY);
        intent.putExtra(FilePickerActivity.EXTRA_TITLE, "Select Source Folder");
        createSourcePickerLauncher.launch(intent);
    }

    private void showOutputFileNameDialog() {
        android.widget.EditText etFileName = new android.widget.EditText(this);
        etFileName.setHint("archive.rpa");
        etFileName.setText("archive.rpa");

        // Determine the default output directory
        String defaultOutputDir;
        if (selectedSourcePaths != null && !selectedSourcePaths.isEmpty()) {
            // For batch, use parent directory of first selected item
            defaultOutputDir = new java.io.File(selectedSourcePaths.get(0)).getParent();
        } else {
            defaultOutputDir = selectedSourcePath;
        }

        new AlertDialog.Builder(this)
                .setTitle("Output File Name")
                .setMessage("Enter the name for the output RPA file:")
                .setView(etFileName)
                .setPositiveButton("Create", (dialog, which) -> {
                    String fileName = etFileName.getText().toString().trim();
                    if (!fileName.isEmpty()) {
                        String outputPath = defaultOutputDir + "/" + fileName;

                        // Check if batch or single creation
                        if (selectedSourcePaths != null && !selectedSourcePaths.isEmpty()) {
                            // Batch creation
                            performBatchCreation(selectedSourcePaths, outputPath);
                        } else {
                            // Single creation
                            performCreation(selectedSourcePath, outputPath);
                        }
                    } else {
                        Toast.makeText(this, "Please enter a file name", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performBatchExtraction(ArrayList<String> rpaFilePaths, String extractDirPath) {
        // Clear any old progress data before starting
        ProgressTracker tracker = new ProgressTracker(MainActivity.this);
        tracker.clearProgress();

        // Launch progress activity
        Intent intent = new Intent(this, ProgressActivity.class);
        startActivity(intent);

        executorService.execute(() -> {
            int totalFiles = rpaFilePaths.size();
            int currentIndex = 1;

            for (String rpaFilePath : rpaFilePaths) {
                try {
                    // Get file name for display
                    String fileName = new java.io.File(rpaFilePath).getName();

                    // Update progress with batch info (before extraction starts)
                    ProgressData batchProgress = new ProgressData();
                    batchProgress.operation = "extract";
                    batchProgress.currentBatchIndex = currentIndex;
                    batchProgress.totalBatchCount = totalFiles;
                    batchProgress.currentBatchFileName = fileName;
                    batchProgress.status = "in_progress";
                    batchProgress.startTime = System.currentTimeMillis();
                    batchProgress.lastUpdateTime = System.currentTimeMillis();
                    batchProgress.totalFiles = 0;
                    batchProgress.processedFiles = 0;
                    batchProgress.currentFile = "Starting extraction...";
                    tracker.writeProgress(batchProgress);

                    // Get progress file path
                    String progressFilePath = tracker.getProgressFilePath();

                    // Call Python extraction for this RPA
                    PyObject result = rpaModule.callAttr("extract_rpa",
                        rpaFilePath,
                        extractDirPath,
                        progressFilePath);

                    // Check if result is valid
                    if (result == null) {
                        throw new Exception("Python function returned null");
                    }

                    // Access dictionary items
                    PyObject successObj = result.callAttr("__getitem__", "success");
                    boolean success = successObj.toJava(Boolean.class);

                    if (!success) {
                        PyObject messageObj = result.callAttr("__getitem__", "message");
                        String message = messageObj.toJava(String.class);
                        throw new Exception(message);
                    }

                    // Move to next file
                    currentIndex++;

                } catch (Exception e) {
                    e.printStackTrace();

                    // Update progress file with error
                    try {
                        ProgressData errorData = new ProgressData();
                        errorData.operation = "extract";
                        errorData.status = "failed";
                        errorData.errorMessage = "Error on file " + currentIndex + "/" + totalFiles + ": " + e.getMessage();
                        errorData.currentBatchIndex = currentIndex;
                        errorData.totalBatchCount = totalFiles;
                        tracker.writeProgress(errorData);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    return; // Stop on error
                }
            }

            // Mark all as completed
            try {
                ProgressData completeData = new ProgressData();
                completeData.operation = "extract";
                completeData.status = "completed";
                completeData.currentBatchIndex = totalFiles;
                completeData.totalBatchCount = totalFiles;
                completeData.totalFiles = 1;
                completeData.processedFiles = 1;
                completeData.currentFile = "Complete";
                tracker.writeProgress(completeData);

                runOnUiThread(() -> {
                    tvExtractStatus.setText("Extracted " + totalFiles + " archives");
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void performExtraction(String rpaFilePath, String extractDirPath) {
        // Clear any old progress data before starting
        ProgressTracker tracker = new ProgressTracker(MainActivity.this);
        tracker.clearProgress();

        // Launch progress activity
        Intent intent = new Intent(this, ProgressActivity.class);
        startActivity(intent);

        executorService.execute(() -> {
            try {
                // Get progress file path
                String progressFilePath = tracker.getProgressFilePath();

                // Call Python extraction directly to destination - NO COPYING!
                PyObject result = rpaModule.callAttr("extract_rpa",
                    rpaFilePath,
                    extractDirPath,
                    progressFilePath);

                // Check if result is valid
                if (result == null) {
                    throw new Exception("Python function returned null");
                }

                // Access dictionary items using __getitem__
                PyObject successObj = result.callAttr("__getitem__", "success");
                PyObject messageObj = result.callAttr("__getitem__", "message");
                PyObject filesObj = result.callAttr("__getitem__", "files");

                final boolean success = successObj.toJava(Boolean.class);
                final String message = messageObj.toJava(String.class);
                final int fileCount = filesObj.asList().size();

                runOnUiThread(() -> {
                    if (success) {
                        tvExtractStatus.setText("Extracted " + fileCount + " files");
                    } else {
                        tvExtractStatus.setText("Extraction failed");
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();

                // Update progress file with error
                try {
                    ProgressData errorData = new ProgressData();
                    errorData.operation = "extract";
                    errorData.status = "failed";
                    errorData.errorMessage = "Error: " + e.getMessage();
                    tracker.writeProgress(errorData);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private void performBatchCreation(ArrayList<String> sourcePaths, String outputFilePath) {
        // Clear any old progress data before starting
        ProgressTracker tracker = new ProgressTracker(MainActivity.this);
        tracker.clearProgress();

        // Launch progress activity
        Intent intent = new Intent(this, ProgressActivity.class);
        startActivity(intent);

        executorService.execute(() -> {
            java.io.File tempDir = null;
            try {
                // Create temporary directory for combining all sources
                tempDir = new java.io.File(getCacheDir(), "rpa_batch_temp_" + System.currentTimeMillis());
                if (!tempDir.mkdir()) {
                    throw new Exception("Failed to create temporary directory");
                }

                final java.io.File finalTempDir = tempDir;
                int totalItems = sourcePaths.size();

                // Copy all selected items to temp directory
                for (int i = 0; i < sourcePaths.size(); i++) {
                    String sourcePath = sourcePaths.get(i);
                    java.io.File sourceFile = new java.io.File(sourcePath);
                    String itemName = sourceFile.getName();

                    // Update progress
                    ProgressData copyProgress = new ProgressData();
                    copyProgress.operation = "create";
                    copyProgress.currentBatchIndex = i + 1;
                    copyProgress.totalBatchCount = totalItems;
                    copyProgress.currentBatchFileName = itemName;
                    copyProgress.status = "in_progress";
                    copyProgress.startTime = System.currentTimeMillis();
                    copyProgress.lastUpdateTime = System.currentTimeMillis();
                    copyProgress.totalFiles = 0;
                    copyProgress.processedFiles = 0;
                    copyProgress.currentFile = "Copying to temp: " + itemName;
                    tracker.writeProgress(copyProgress);

                    // Copy to temp directory
                    java.io.File destFile = new java.io.File(finalTempDir, itemName);
                    if (sourceFile.isDirectory()) {
                        copyDirectory(sourceFile, destFile);
                    } else {
                        copyFile(sourceFile, destFile);
                    }
                }

                // Now create RPA from temp directory
                String progressFilePath = tracker.getProgressFilePath();

                // Update progress to show RPA creation starting
                ProgressData createProgress = new ProgressData();
                createProgress.operation = "create";
                createProgress.currentBatchIndex = totalItems;
                createProgress.totalBatchCount = totalItems;
                createProgress.currentBatchFileName = "Creating final archive...";
                createProgress.status = "in_progress";
                createProgress.startTime = System.currentTimeMillis();
                createProgress.lastUpdateTime = System.currentTimeMillis();
                createProgress.totalFiles = 0;
                createProgress.processedFiles = 0;
                createProgress.currentFile = "Building RPA from " + totalItems + " items";
                tracker.writeProgress(createProgress);

                // Call Python creation
                PyObject result = rpaModule.callAttr("create_rpa",
                    finalTempDir.getAbsolutePath(),
                    outputFilePath,
                    3, 0xDEADBEEF,
                    progressFilePath);

                // Check if result is valid
                if (result == null) {
                    throw new Exception("Python function returned null");
                }

                // Access dictionary items
                PyObject successObj = result.callAttr("__getitem__", "success");
                PyObject messageObj = result.callAttr("__getitem__", "message");
                PyObject filesObj = result.callAttr("__getitem__", "files");

                final boolean success = successObj.toJava(Boolean.class);
                final String message = messageObj.toJava(String.class);
                final int fileCount = filesObj.asList().size();

                runOnUiThread(() -> {
                    if (success) {
                        tvCreateStatus.setText("Created archive with " + fileCount + " files from " + totalItems + " sources");
                    } else {
                        tvCreateStatus.setText("Creation failed");
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();

                // Update progress file with error
                try {
                    ProgressData errorData = new ProgressData();
                    errorData.operation = "create";
                    errorData.status = "failed";
                    errorData.errorMessage = "Error: " + e.getMessage();
                    tracker.writeProgress(errorData);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } finally {
                // Clean up temp directory
                if (tempDir != null && tempDir.exists()) {
                    deleteRecursive(tempDir);
                }
            }
        });
    }

    // Helper method to copy a file
    private void copyFile(java.io.File source, java.io.File dest) throws Exception {
        java.io.FileInputStream fis = new java.io.FileInputStream(source);
        java.io.FileOutputStream fos = new java.io.FileOutputStream(dest);
        byte[] buffer = new byte[8192];
        int length;
        while ((length = fis.read(buffer)) > 0) {
            fos.write(buffer, 0, length);
        }
        fis.close();
        fos.close();
    }

    // Helper method to copy a directory recursively
    private void copyDirectory(java.io.File source, java.io.File dest) throws Exception {
        if (!dest.mkdir()) {
            throw new Exception("Failed to create directory: " + dest.getAbsolutePath());
        }

        java.io.File[] files = source.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                java.io.File destFile = new java.io.File(dest, file.getName());
                if (file.isDirectory()) {
                    copyDirectory(file, destFile);
                } else {
                    copyFile(file, destFile);
                }
            }
        }
    }

    // Helper method to delete a directory recursively
    private void deleteRecursive(java.io.File file) {
        if (file.isDirectory()) {
            java.io.File[] files = file.listFiles();
            if (files != null) {
                for (java.io.File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    private void performCreation(String sourceDirPath, String outputFilePath) {
        // Clear any old progress data before starting
        ProgressTracker tracker = new ProgressTracker(MainActivity.this);
        tracker.clearProgress();

        // Launch progress activity
        Intent intent = new Intent(this, ProgressActivity.class);
        startActivity(intent);

        executorService.execute(() -> {
            try {
                // Get progress file path
                String progressFilePath = tracker.getProgressFilePath();

                // Call Python creation directly to destination - NO COPYING!
                PyObject result = rpaModule.callAttr("create_rpa",
                    sourceDirPath,
                    outputFilePath,
                    3, 0xDEADBEEF,
                    progressFilePath);

                // Check if result is valid
                if (result == null) {
                    throw new Exception("Python function returned null");
                }

                // Access dictionary items using __getitem__
                PyObject successObj = result.callAttr("__getitem__", "success");
                PyObject messageObj = result.callAttr("__getitem__", "message");
                PyObject filesObj = result.callAttr("__getitem__", "files");

                final boolean success = successObj.toJava(Boolean.class);
                final String message = messageObj.toJava(String.class);
                final int fileCount = filesObj.asList().size();

                runOnUiThread(() -> {
                    if (success) {
                        tvCreateStatus.setText("Created archive with " + fileCount + " files");
                    } else {
                        tvCreateStatus.setText("Creation failed");
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();

                // Update progress file with error
                try {
                    ProgressData errorData = new ProgressData();
                    errorData.operation = "create";
                    errorData.status = "failed";
                    errorData.errorMessage = "Error: " + e.getMessage();
                    tracker.writeProgress(errorData);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private void showProgress(String message) {
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText(message);
        cardExtract.setEnabled(false);
        cardCreate.setEnabled(false);
    }

    private void hideProgress() {
        progressBar.setVisibility(View.GONE);
        tvStatus.setText("Ready");
        cardExtract.setEnabled(true);
        cardCreate.setEnabled(true);
    }

    private void showSuccess(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
