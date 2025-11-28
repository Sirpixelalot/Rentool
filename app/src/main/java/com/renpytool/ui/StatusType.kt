package com.renpytool.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents different status states with associated colors and icons
 */
sealed class StatusType {
    abstract val icon: ImageVector?

    @Composable
    abstract fun backgroundColor(): Color

    @Composable
    abstract fun textColor(): Color

    object Empty : StatusType() {
        override val icon: ImageVector? = Icons.Filled.Info

        @Composable
        override fun backgroundColor() = MaterialTheme.colorScheme.surfaceVariant

        @Composable
        override fun textColor() = MaterialTheme.colorScheme.onSurfaceVariant
    }

    object Success : StatusType() {
        override val icon: ImageVector = Icons.Filled.CheckCircle

        @Composable
        override fun backgroundColor() = MaterialTheme.colorScheme.tertiaryContainer

        @Composable
        override fun textColor() = MaterialTheme.colorScheme.onTertiaryContainer
    }

    object Error : StatusType() {
        override val icon: ImageVector = Icons.Filled.Warning

        @Composable
        override fun backgroundColor() = MaterialTheme.colorScheme.errorContainer

        @Composable
        override fun textColor() = MaterialTheme.colorScheme.onErrorContainer
    }

    object Info : StatusType() {
        override val icon: ImageVector = Icons.Filled.Info

        @Composable
        override fun backgroundColor() = MaterialTheme.colorScheme.primaryContainer

        @Composable
        override fun textColor() = MaterialTheme.colorScheme.onPrimaryContainer
    }

    companion object {
        /**
         * Infer status type from status message text
         */
        fun fromMessage(message: String): StatusType {
            return when {
                message.contains("No ", ignoreCase = true) ||
                message.contains("not ", ignoreCase = true) -> Empty

                message.contains("success", ignoreCase = true) ||
                message.contains("extracted", ignoreCase = true) ||
                message.contains("created", ignoreCase = true) ||
                message.contains("decompiled", ignoreCase = true) ||
                message.contains("compressed", ignoreCase = true) ||
                message.contains("saved", ignoreCase = true) -> Success

                message.contains("error", ignoreCase = true) ||
                message.contains("failed", ignoreCase = true) -> Error

                else -> Info
            }
        }
    }
}
