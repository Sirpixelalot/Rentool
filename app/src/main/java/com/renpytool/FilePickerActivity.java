package com.renpytool;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * File picker activity that uses regular File APIs (non-SAF)
 * Returns selected file/folder path as a String
 */
public class FilePickerActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_FILE_FILTER = "file_filter";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_START_DIR = "start_dir";
    public static final String EXTRA_SELECTED_PATH = "selected_path";
    public static final String EXTRA_SELECTED_PATHS = "selected_paths"; // For multi-select

    public static final int MODE_FILE = 0;
    public static final int MODE_DIRECTORY = 1;

    private MaterialToolbar toolbar;
    private TextView tvCurrentPath;
    private RecyclerView rvFiles;
    private ExtendedFloatingActionButton fabSelect;

    private FilePickerAdapter adapter;
    private List<FileItem> fileItems;

    private File currentDirectory;
    private int mode;
    private String fileFilter; // e.g., ".rpa" or null for all files

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_picker);

        // Get intent extras
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_FILE);
        fileFilter = getIntent().getStringExtra(EXTRA_FILE_FILTER);
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        // Initialize views
        toolbar = findViewById(R.id.toolbar);
        tvCurrentPath = findViewById(R.id.tv_current_path);
        rvFiles = findViewById(R.id.rv_files);
        fabSelect = findViewById(R.id.fab_select);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        if (title != null) {
            toolbar.setTitle(title);
        }

        // Setup RecyclerView
        fileItems = new ArrayList<>();
        adapter = new FilePickerAdapter(fileItems, this::onFileItemClick, this::onFileItemLongClick);
        rvFiles.setLayoutManager(new LinearLayoutManager(this));
        rvFiles.setAdapter(adapter);

        // Setup FAB
        updateFabVisibility();
        fabSelect.setOnClickListener(v -> confirmSelection());

        // Setup toolbar back button
        toolbar.setNavigationOnClickListener(v -> {
            if (adapter.isMultiSelectMode()) {
                exitMultiSelectMode();
            } else {
                finish();
            }
        });

        // Start at specified directory or default to external storage
        String startPath = getIntent().getStringExtra(EXTRA_START_DIR);
        File startDir;
        if (startPath != null && !startPath.isEmpty()) {
            File customStart = new File(startPath);
            if (customStart.exists() && customStart.isDirectory()) {
                startDir = customStart;
            } else {
                startDir = Environment.getExternalStorageDirectory();
            }
        } else {
            startDir = Environment.getExternalStorageDirectory();
        }
        navigateToDirectory(startDir);
    }

    private void navigateToDirectory(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            Toast.makeText(this, "Cannot access directory", Toast.LENGTH_SHORT).show();
            return;
        }

        currentDirectory = directory;
        tvCurrentPath.setText(currentDirectory.getAbsolutePath());

        // Clear and populate file list
        fileItems.clear();

        // Add "Up" navigation if not at root
        if (currentDirectory.getParent() != null) {
            fileItems.add(new FileItem("..", currentDirectory.getParentFile()));
        }

        // List files and folders
        File[] files = currentDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                // Skip hidden files
                if (file.getName().startsWith(".")) {
                    continue;
                }

                // Apply file filter if in FILE mode
                if (mode == MODE_FILE && !file.isDirectory() && fileFilter != null) {
                    if (!file.getName().endsWith(fileFilter)) {
                        continue;
                    }
                }

                fileItems.add(new FileItem(file));
            }
        }

        // Sort items (directories first, then alphabetically)
        Collections.sort(fileItems);

        adapter.notifyDataSetChanged();
    }

    private void onFileItemClick(FileItem item) {
        if (adapter.isMultiSelectMode()) {
            // In multi-select mode, toggle selection
            if (!item.getName().equals("..")) {
                adapter.toggleSelection(item);
                updateFabVisibility();
            }
        } else {
            // Normal mode navigation
            if (item.getName().equals("..")) {
                // Navigate up
                navigateToDirectory(item.getFile());
            } else if (item.isDirectory()) {
                // Navigate into directory
                navigateToDirectory(item.getFile());
            } else {
                // File selected (only in FILE mode)
                if (mode == MODE_FILE) {
                    selectFile(item.getFile());
                }
            }
        }
    }

    private void onFileItemLongClick(FileItem item) {
        // Enter multi-select mode and select this item
        enterMultiSelectMode();
        adapter.toggleSelection(item);
        updateFabVisibility();
    }

    private void enterMultiSelectMode() {
        adapter.setMultiSelectMode(true);
        updateFabVisibility();
        Toast.makeText(this, "Multi-select mode enabled", Toast.LENGTH_SHORT).show();
    }

    private void exitMultiSelectMode() {
        adapter.setMultiSelectMode(false);
        updateFabVisibility();
    }

    private void updateFabVisibility() {
        if (adapter.isMultiSelectMode()) {
            int count = adapter.getSelectedCount();
            fabSelect.setVisibility(android.view.View.VISIBLE);
            fabSelect.setText("Select (" + count + ")");
            fabSelect.setEnabled(count > 0);
        } else if (mode == MODE_DIRECTORY) {
            fabSelect.setVisibility(android.view.View.VISIBLE);
            fabSelect.setText("Select Folder");
            fabSelect.setEnabled(true);
        } else {
            fabSelect.setVisibility(android.view.View.GONE);
        }
    }

    private void confirmSelection() {
        if (adapter.isMultiSelectMode()) {
            // Multi-select mode: return all selected paths
            ArrayList<String> paths = new ArrayList<>();
            for (File file : adapter.getSelectedFiles()) {
                paths.add(file.getAbsolutePath());
            }
            Intent resultIntent = new Intent();
            resultIntent.putStringArrayListExtra(EXTRA_SELECTED_PATHS, paths);
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        } else {
            // Single select mode (directory mode)
            selectCurrentDirectory();
        }
    }

    private void selectFile(File file) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_SELECTED_PATH, file.getAbsolutePath());
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    private void selectCurrentDirectory() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_SELECTED_PATH, currentDirectory.getAbsolutePath());
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Exit multi-select mode if active
            if (adapter.isMultiSelectMode()) {
                exitMultiSelectMode();
                return true;
            }
            // Navigate up if not at root, otherwise finish
            if (currentDirectory.getParent() != null) {
                navigateToDirectory(currentDirectory.getParentFile());
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
