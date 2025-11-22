package com.renpytool.keystore

import java.io.File

/**
 * Information about a saved keystore
 */
data class KeystoreInfo(
    val name: String,
    val filePath: String,
    val alias: String,
    val createdDate: Long,
    val certificateInfo: String
) {
    val file: File
        get() = File(filePath)

    /**
     * Format creation date for display
     */
    fun getFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(createdDate))
    }

    /**
     * Check if keystore file still exists
     */
    fun exists(): Boolean = file.exists()
}
