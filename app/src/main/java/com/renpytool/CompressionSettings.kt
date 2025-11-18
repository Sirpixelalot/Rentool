package com.renpytool

import android.content.Context
import android.content.SharedPreferences

/**
 * Compression settings data class
 * Stores user preferences for game compression
 */
data class CompressionSettings(
    // Image settings
    val imageQuality: Int = 80,  // 1-100
    val imageLossless: Boolean = false,
    val imageMethod: Int = 4,  // 0=fast, 4=default, 6=slow
    val skipImages: Boolean = false,

    // Audio settings (using FFmpeg-Kit)
    val audioQuality: AudioCompressor.AudioQuality = AudioCompressor.AudioQuality.MEDIUM,
    val skipAudio: Boolean = false,

    // Video settings (using FFmpeg-Kit)
    val videoQuality: VideoCompressor.VideoQuality = VideoCompressor.VideoQuality.MEDIUM,
    val skipVideo: Boolean = false,

    // General settings
    val threads: Int = Runtime.getRuntime().availableProcessors(),
    val createRpaAfter: Boolean = false
) {
    companion object {
        private const val PREFS_NAME = "compression_settings"
        private const val KEY_IMAGE_QUALITY = "image_quality"
        private const val KEY_IMAGE_LOSSLESS = "image_lossless"
        private const val KEY_IMAGE_METHOD = "image_method"
        private const val KEY_SKIP_IMAGES = "skip_images"
        private const val KEY_AUDIO_QUALITY = "audio_quality"
        private const val KEY_SKIP_AUDIO = "skip_audio"
        private const val KEY_VIDEO_QUALITY = "video_quality"
        private const val KEY_SKIP_VIDEO = "skip_video"
        private const val KEY_THREADS = "threads"
        private const val KEY_CREATE_RPA = "create_rpa_after"

        /**
         * Load settings from SharedPreferences
         */
        fun load(context: Context): CompressionSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val defaultThreads = Runtime.getRuntime().availableProcessors()

            return CompressionSettings(
                imageQuality = prefs.getInt(KEY_IMAGE_QUALITY, 80),
                imageLossless = prefs.getBoolean(KEY_IMAGE_LOSSLESS, false),
                imageMethod = prefs.getInt(KEY_IMAGE_METHOD, 4),
                skipImages = prefs.getBoolean(KEY_SKIP_IMAGES, false),
                audioQuality = AudioCompressor.AudioQuality.valueOf(
                    prefs.getString(KEY_AUDIO_QUALITY, AudioCompressor.AudioQuality.MEDIUM.name)
                        ?: AudioCompressor.AudioQuality.MEDIUM.name
                ),
                skipAudio = prefs.getBoolean(KEY_SKIP_AUDIO, false),
                videoQuality = VideoCompressor.VideoQuality.valueOf(
                    prefs.getString(KEY_VIDEO_QUALITY, VideoCompressor.VideoQuality.MEDIUM.name)
                        ?: VideoCompressor.VideoQuality.MEDIUM.name
                ),
                skipVideo = prefs.getBoolean(KEY_SKIP_VIDEO, false),
                threads = prefs.getInt(KEY_THREADS, defaultThreads),
                createRpaAfter = prefs.getBoolean(KEY_CREATE_RPA, false)
            )
        }
    }

    /**
     * Save settings to SharedPreferences
     */
    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_IMAGE_QUALITY, imageQuality)
            putBoolean(KEY_IMAGE_LOSSLESS, imageLossless)
            putInt(KEY_IMAGE_METHOD, imageMethod)
            putBoolean(KEY_SKIP_IMAGES, skipImages)
            putString(KEY_AUDIO_QUALITY, audioQuality.name)
            putBoolean(KEY_SKIP_AUDIO, skipAudio)
            putString(KEY_VIDEO_QUALITY, videoQuality.name)
            putBoolean(KEY_SKIP_VIDEO, skipVideo)
            putInt(KEY_THREADS, threads)
            putBoolean(KEY_CREATE_RPA, createRpaAfter)
            apply()
        }
    }

    /**
     * Get image compression method label
     */
    fun getImageMethodLabel(): String = when (imageMethod) {
        in 0..2 -> "Fast"
        in 3..5 -> "Average"
        else -> "Slow"
    }

    /**
     * Check if any media types are enabled
     */
    fun hasEnabledTypes(): Boolean = !skipImages || !skipAudio || !skipVideo
}
