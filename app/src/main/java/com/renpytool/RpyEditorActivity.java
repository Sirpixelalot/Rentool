package com.renpytool;

import android.os.Bundle;
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
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import org.eclipse.tm4e.core.registry.IThemeSource;

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

        // Initialize TextMate (one-time setup)
        initializeTextMate();

        // Configure editor (colors and basic settings only)
        configureEditor();

        // Load file content FIRST
        loadFile();

        // Apply syntax highlighting with custom theme AFTER content is loaded
        applySyntaxHighlighting();

        // Setup save button
        fabSave.setOnClickListener(v -> saveFile());

        // Track modifications
        editor.subscribeAlways(io.github.rosemoe.sora.event.ContentChangeEvent.class, event -> {
            isModified = true;
        });
    }

    /**
     * Initialize TextMate language system with theme (one-time setup)
     */
    private void initializeTextMate() {
        try {
            // Register assets file provider
            FileProviderRegistry.getInstance().addFileProvider(
                new AssetsFileResolver(getApplicationContext().getAssets())
            );

            // Load Ren'Py grammar from assets
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json");

            // Load our custom dark theme
            String themeName = "renpy-dark";
            String themeAssetsPath = "textmate/renpy-dark-theme.json";
            ThemeModel themeModel = new ThemeModel(
                IThemeSource.fromInputStream(
                    FileProviderRegistry.getInstance().tryGetInputStream(themeAssetsPath),
                    themeAssetsPath,
                    null
                ),
                themeName
            );
            themeModel.setDark(true);
            ThemeRegistry.getInstance().loadTheme(themeModel);
            ThemeRegistry.getInstance().setTheme(themeName);
        } catch (Exception e) {
            Toast.makeText(this, "Error loading syntax highlighting: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    /**
     * Configure the Sora Editor with basic settings
     * Color scheme is applied by TextMateColorScheme after language setup
     */
    private void configureEditor() {
        // Enable basic features
        editor.setLineNumberEnabled(true);
        editor.setWordwrap(false);
        editor.setTypefaceText(android.graphics.Typeface.MONOSPACE);
    }

    /**
     * Apply Ren'Py syntax highlighting to the editor
     * Must be called AFTER file content is loaded
     */
    private void applySyntaxHighlighting() {
        try {
            // Create TextMate language for Ren'Py
            TextMateLanguage language = TextMateLanguage.create("source.renpy", true);
            editor.setEditorLanguage(language);

            // Apply TextMate color scheme for proper syntax highlighting
            editor.setColorScheme(TextMateColorScheme.create(ThemeRegistry.getInstance()));
        } catch (Exception e) {
            Toast.makeText(this, "Error applying syntax highlighting: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
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

            // Save the parent folder location and file path for future use
            File parentDir = file.getParentFile();
            android.content.SharedPreferences prefs = getSharedPreferences("RentoolPrefs", MODE_PRIVATE);
            android.content.SharedPreferences.Editor prefsEditor = prefs.edit();

            if (parentDir != null) {
                prefsEditor.putString("last_rpy_edit_folder", parentDir.getAbsolutePath());
            }
            prefsEditor.putString("last_rpy_edit_file", file.getAbsolutePath());
            prefsEditor.apply();

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
