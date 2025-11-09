package com.renpytool;

import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * Activity for viewing and editing .rpy (Ren'Py script) files
 * Uses Sora Editor for professional code editing experience
 */
public class RpyEditorActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = "file_path";

    private CodeEditor editor;
    private MaterialToolbar toolbar;
    private ExtendedFloatingActionButton fabSave;
    private String filePath;
    private boolean isModified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rpy_editor);

        // Get file path from intent
        filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(this, "No file specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize views
        editor = findViewById(R.id.editor);
        toolbar = findViewById(R.id.toolbar);
        fabSave = findViewById(R.id.fab_save);

        // Setup toolbar
        File file = new File(filePath);
        toolbar.setTitle(file.getName());
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Configure editor
        configureEditor();

        // Load file content
        loadFile();

        // Setup save button
        fabSave.setOnClickListener(v -> saveFile());

        // Track modifications
        editor.subscribeAlways(io.github.rosemoe.sora.event.ContentChangeEvent.class, event -> {
            isModified = true;
        });
    }

    /**
     * Configure the Sora Editor with Material 3 colors
     */
    private void configureEditor() {
        // Apply Material 3 colors to editor
        EditorColorScheme scheme = editor.getColorScheme();

        // Get Material 3 colors from theme
        TypedValue typedValue = new TypedValue();

        // Background
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true);
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, typedValue.data);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, typedValue.data);

        // Text
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
        scheme.setColor(EditorColorScheme.TEXT_NORMAL, typedValue.data);
        scheme.setColor(EditorColorScheme.LINE_NUMBER, typedValue.data);

        // Current line
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true);
        scheme.setColor(EditorColorScheme.CURRENT_LINE, typedValue.data);

        // Selection
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true);
        scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, typedValue.data);

        // Enable basic features
        editor.setLineNumberEnabled(true);
        editor.setWordwrap(false);
        editor.setTypefaceText(android.graphics.Typeface.MONOSPACE);
    }

    /**
     * Load file content into editor
     */
    private void loadFile() {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                Toast.makeText(this, "File not found: " + file.getName(), Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            StringBuilder content = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;

            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();

            // Set content to editor
            editor.setText(content.toString());
            isModified = false;

            // Save the parent folder location for future use
            File parentDir = file.getParentFile();
            if (parentDir != null) {
                android.content.SharedPreferences prefs = getSharedPreferences("RentoolPrefs", MODE_PRIVATE);
                prefs.edit().putString("last_rpy_edit_folder", parentDir.getAbsolutePath()).apply();
            }

        } catch (IOException e) {
            Toast.makeText(this, "Error loading file: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    /**
     * Save file content from editor
     */
    private void saveFile() {
        try {
            File file = new File(filePath);
            FileWriter writer = new FileWriter(file);
            writer.write(editor.getText().toString());
            writer.close();

            isModified = false;
            Toast.makeText(this, "File saved", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            Toast.makeText(this, "Error saving file: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    /**
     * Warn user about unsaved changes
     */
    @Override
    public void onBackPressed() {
        if (isModified) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Unsaved Changes")
                    .setMessage("You have unsaved changes. Do you want to save before exiting?")
                    .setPositiveButton("Save", (dialog, which) -> {
                        saveFile();
                        super.onBackPressed();
                    })
                    .setNegativeButton("Discard", (dialog, which) -> super.onBackPressed())
                    .setNeutralButton("Cancel", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }
}
