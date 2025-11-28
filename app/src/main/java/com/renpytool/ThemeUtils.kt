package com.renpytool

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/**
 * Utility functions for theme management across activities
 */
object ThemeUtils {
    private const val PREFS_NAME = "RentoolPrefs"
    private const val KEY_THEME_MODE = "theme_mode"

    /**
     * Get the current theme mode from SharedPreferences
     */
    fun getThemeMode(context: Context): MainViewModel.ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeName = prefs.getString(KEY_THEME_MODE, MainViewModel.ThemeMode.SYSTEM.name)
        return MainViewModel.ThemeMode.fromString(themeName ?: MainViewModel.ThemeMode.SYSTEM.name)
    }

    /**
     * Determine if dark theme should be used based on theme mode
     */
    @Composable
    fun shouldUseDarkTheme(themeMode: MainViewModel.ThemeMode): Boolean {
        return when (themeMode) {
            MainViewModel.ThemeMode.LIGHT -> false
            MainViewModel.ThemeMode.DARK -> true
            MainViewModel.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    }
}
