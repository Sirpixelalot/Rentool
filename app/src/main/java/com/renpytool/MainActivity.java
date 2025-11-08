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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private MaterialCardView cardExtract, cardCreate, cardDecompile;
    private TextView tvExtractStatus, tvCreateStatus, tvDecompileStatus;

    private Python python;
    private PyObject rpaModule;
    private PyObject decompileModule;

    private ExecutorService executorService;

    // File picker launchers
    private ActivityResultLauncher<Intent> extractRpaPickerLauncher;
    private ActivityResultLauncher<Intent> extractDirPickerLauncher;
    private ActivityResultLauncher<Intent> createSourcePickerLauncher;
    private ActivityResultLauncher<Intent> createOutputPickerLauncher;
    private ActivityResultLauncher<Intent> decompileDirPickerLauncher;

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
        decompileModule = python.getModule("decompile_wrapper");

        // Initialize executor service
        executorService = Executors.newSingleThreadExecutor();

        // Initialize views
        initViews();

        // Initialize file picker launchers
        initFilePickerLaunchers();

        // Check permissions
        checkPermissions();

        // Check for updates
        checkForUpdates();
    }

    private void initViews() {
        cardExtract = findViewById(R.id.card_extract);
        cardCreate = findViewById(R.id.card_create);
        cardDecompile = findViewById(R.id.card_decompile);
        tvExtractStatus = findViewById(R.id.tv_extract_status);
        tvCreateStatus = findViewById(R.id.tv_create_status);
        tvDecompileStatus = findViewById(R.id.tv_decompile_status);

        // Set up click listeners
        cardExtract.setOnClickListener(v -> startExtractFlow());
        cardCreate.setOnClickListener(v -> startCreateFlow());
        cardDecompile.setOnClickListener(v -> startDecompileFlow());
    }


    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 and above - use MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                new MaterialAlertDialogBuilder(this)
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

        // Decompile: Pick source directory
        decompileDirPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String sourcePath = null;

                        // Check for multi-select first (user may have accidentally entered multi-select mode)
                        ArrayList<String> selectedPaths = result.getData().getStringArrayListExtra(FilePickerActivity.EXTRA_SELECTED_PATHS);
                        if (selectedPaths != null && !selectedPaths.isEmpty()) {
                            // If multi-select happened, just use the first path
                            sourcePath = selectedPaths.get(0);
                        } else {
                            // Normal single selection
                            sourcePath = result.getData().getStringExtra(FilePickerActivity.EXTRA_SELECTED_PATH);
                        }

                        if (sourcePath != null && !sourcePath.isEmpty()) {
                            performDecompile(sourcePath);
                        } else {
                            Toast.makeText(this, "No directory selected", Toast.LENGTH_SHORT).show();
                        }
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
        // Inflate custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_input, null);
        TextInputEditText etFileName = dialogView.findViewById(R.id.editText);
        etFileName.setText("archive.rpa");

        // Determine the default output directory
        String defaultOutputDir;
        if (selectedSourcePaths != null && !selectedSourcePaths.isEmpty()) {
            // For batch, use parent directory of first selected item
            defaultOutputDir = new java.io.File(selectedSourcePaths.get(0)).getParent();
        } else {
            defaultOutputDir = selectedSourcePath;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Output File Name")
                .setMessage("Enter the name for the output RPA file:")
                .setView(dialogView)
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

            // Capture the batch start time - use this for all files
            long batchStartTime = System.currentTimeMillis();

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
                    batchProgress.startTime = batchStartTime;  // Use batch start time, not current time
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
                completeData.totalFiles = totalFiles;  // Show actual number of RPA files extracted
                completeData.processedFiles = totalFiles;
                completeData.currentFile = "Complete";
                completeData.startTime = batchStartTime;  // Use batch start time for accurate elapsed time
                completeData.lastUpdateTime = System.currentTimeMillis();
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
                // Initialize progress with start time
                ProgressData initialData = new ProgressData();
                initialData.operation = "extract";
                initialData.status = "in_progress";
                initialData.startTime = System.currentTimeMillis();
                initialData.lastUpdateTime = System.currentTimeMillis();
                initialData.totalFiles = 0;
                initialData.processedFiles = 0;
                initialData.currentFile = "Starting extraction...";
                tracker.writeProgress(initialData);

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
                // Capture the batch start time - use this for all phases
                long batchStartTime = System.currentTimeMillis();

                // Create temporary directory for combining all sources
                tempDir = new java.io.File(getCacheDir(), "rpa_batch_temp_" + batchStartTime);
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
                    copyProgress.startTime = batchStartTime;  // Use batch start time, not current time
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
                createProgress.startTime = batchStartTime;  // Use batch start time for accurate elapsed time
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
                // Initialize progress with start time
                ProgressData initialData = new ProgressData();
                initialData.operation = "create";
                initialData.status = "in_progress";
                initialData.startTime = System.currentTimeMillis();
                initialData.lastUpdateTime = System.currentTimeMillis();
                initialData.totalFiles = 0;
                initialData.processedFiles = 0;
                initialData.currentFile = "Starting creation...";
                tracker.writeProgress(initialData);

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

    private void startDecompileFlow() {
        // Launch file picker for directory containing .rpyc files
        Intent intent = new Intent(this, FilePickerActivity.class);
        intent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIRECTORY);
        intent.putExtra(FilePickerActivity.EXTRA_TITLE, "Select Folder with RPYC Files");
        decompileDirPickerLauncher.launch(intent);
    }

    private void performDecompile(String sourceDirPath) {
        // Clear any old progress data before starting
        ProgressTracker tracker = new ProgressTracker(MainActivity.this);
        tracker.clearProgress();

        // Launch progress activity
        Intent intent = new Intent(this, ProgressActivity.class);
        startActivity(intent);

        executorService.execute(() -> {
            try {
                // Initialize progress with start time
                ProgressData initialData = new ProgressData();
                initialData.operation = "decompile";
                initialData.status = "in_progress";
                initialData.startTime = System.currentTimeMillis();
                initialData.lastUpdateTime = System.currentTimeMillis();
                initialData.totalFiles = 0;
                initialData.processedFiles = 0;
                initialData.currentFile = "Starting decompilation...";
                tracker.writeProgress(initialData);

                // Get progress file path
                String progressFilePath = tracker.getProgressFilePath();

                // Call Python decompilation
                PyObject result = decompileModule.callAttr("decompile_directory",
                    sourceDirPath,
                    progressFilePath);

                // Check if result is valid
                if (result == null) {
                    throw new Exception("Python function returned null");
                }

                // Access dictionary items using __getitem__
                PyObject successObj = result.callAttr("__getitem__", "success");
                PyObject messageObj = result.callAttr("__getitem__", "message");
                PyObject statsObj = result.callAttr("__getitem__", "stats");

                final boolean success = successObj.toJava(Boolean.class);
                final String message = messageObj.toJava(String.class);

                // Extract stats
                PyObject totalObj = statsObj.callAttr("__getitem__", "total");
                PyObject successCountObj = statsObj.callAttr("__getitem__", "success");
                PyObject skippedObj = statsObj.callAttr("__getitem__", "skipped");
                PyObject failedObj = statsObj.callAttr("__getitem__", "failed");

                final int total = totalObj.toJava(Integer.class);
                final int successCount = successCountObj.toJava(Integer.class);
                final int skipped = skippedObj.toJava(Integer.class);
                final int failed = failedObj.toJava(Integer.class);

                runOnUiThread(() -> {
                    if (success) {
                        tvDecompileStatus.setText(String.format(
                            "Decompiled %d files (%d success, %d skipped, %d failed)",
                            total, successCount, skipped, failed
                        ));
                    } else {
                        tvDecompileStatus.setText("Decompilation failed: " + message);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();

                // Update progress file with error
                try {
                    ProgressData errorData = new ProgressData();
                    errorData.operation = "decompile";
                    errorData.status = "failed";
                    errorData.errorMessage = "Error: " + e.getMessage();
                    tracker.writeProgress(errorData);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                runOnUiThread(() -> {
                    tvDecompileStatus.setText("Decompilation error: " + e.getMessage());
                });
            }
        });
    }

    private void showProgress(String message) {
        cardExtract.setEnabled(false);
        cardCreate.setEnabled(false);
        cardDecompile.setEnabled(false);
    }

    private void hideProgress() {
        cardExtract.setEnabled(true);
        cardCreate.setEnabled(true);
        cardDecompile.setEnabled(true);
    }

    private void showSuccess(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Get the current app version from PackageManager
     */
    private String getAppVersion() {
        try {
            PackageManager packageManager = getPackageManager();
            String packageName = getPackageName();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ (API 33+) - Use PackageInfoFlags
                android.content.pm.PackageInfo packageInfo = packageManager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(0)
                );
                return packageInfo.versionName;
            } else {
                // Older Android versions - deprecated but still works
                android.content.pm.PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
                return packageInfo.versionName;
            }
        } catch (PackageManager.NameNotFoundException e) {
            android.util.Log.e("MainActivity", "Failed to get app version", e);
            return "1.0";  // Fallback version
        }
    }

    /**
     * Check for app updates from GitHub releases
     */
    private void checkForUpdates() {
        android.util.Log.d("MainActivity", "Starting update check...");
        String currentVersion = getAppVersion();
        android.util.Log.d("MainActivity", "Current app version: " + currentVersion);
        executorService.execute(() -> {
            UpdateChecker.checkForUpdates(currentVersion, new UpdateChecker.UpdateCheckCallback() {
                @Override
                public void onUpdateAvailable(VersionInfo versionInfo) {
                    android.util.Log.d("MainActivity", "Update available: " + versionInfo.getVersionTag());
                    runOnUiThread(() -> showUpdateDialog(versionInfo));
                }

                @Override
                public void onNoUpdateAvailable() {
                    android.util.Log.d("MainActivity", "No update available");
                    // Silent - no action needed
                }

                @Override
                public void onCheckFailed(String error) {
                    android.util.Log.e("MainActivity", "Update check failed: " + error);
                    // Silent fail - don't bother user with network errors
                }
            });
        });
    }

    /**
     * Show update available dialog
     */
    private void showUpdateDialog(VersionInfo versionInfo) {
        String currentVersion = getAppVersion();
        String message = String.format(
                "Version %s is now available!\n\nYou are currently using version %s.\n\nWould you like to download the update?",
                versionInfo.getVersionNumber(),
                currentVersion
        );

        new MaterialAlertDialogBuilder(this)
                .setTitle("Update Available")
                .setMessage(message)
                .setPositiveButton("Update", (dialog, which) -> {
                    // Open GitHub releases page in browser
                    android.content.Intent browserIntent = new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(versionInfo.getDownloadUrl())
                    );
                    startActivity(browserIntent);
                })
                .setNegativeButton("Later", null)
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
