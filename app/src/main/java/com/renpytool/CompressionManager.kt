package com.renpytool

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages game compression operations
 * Handles native Kotlin image compression and FFmpeg-Kit audio compression
 */
class CompressionManager(private val context: Context) {

    private val TAG = "CompressionManager"
    private val imageCompressor = ImageCompressor(context)

    /**
     * Scan directory for compressible files
     * Returns counts and sizes for images
     */
    suspend fun scanCompressibleFiles(directory: File): ScanResult = withContext(Dispatchers.IO) {
        try {
            // Scan images using native Kotlin
            val images = imageCompressor.scanImages(directory)
            val imageSizeBytes = images.sumOf { it.length() }

            // Scan audio files
            val audioFiles = AudioCompressor.scanAudioFiles(directory)
            val audioSizeBytes = audioFiles.sumOf { it.length() }

            // Scan video files
            val videoFiles = VideoCompressor.scanVideoFiles(directory)
            val videoSizeBytes = videoFiles.sumOf { it.length() }

            ScanResult(
                imageCount = images.size,
                imageSizeBytes = imageSizeBytes,
                audioCount = audioFiles.size,
                audioSizeBytes = audioSizeBytes,
                videoCount = videoFiles.size,
                videoSizeBytes = videoSizeBytes,
                totalSizeBytes = imageSizeBytes + audioSizeBytes + videoSizeBytes
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error scanning files", e)
            ScanResult(0, 0, 0, 0, 0, 0, 0)
        }
    }

    /**
     * Compress game files according to settings
     *
     * @param sourceDir Source game directory
     * @param outputDir Output directory for compressed files
     * @param settings Compression settings
     * @param progressTracker Progress tracker for updates
     * @return CompressionResult with statistics
     */
    suspend fun compressGame(
        sourceDir: File,
        outputDir: File,
        settings: CompressionSettings,
        progressTracker: ProgressTracker
    ): CompressionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // Ensure output directory exists
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            // Pre-scan: Calculate total original size before starting compression
            Log.i(TAG, "Scanning files to calculate total size...")
            val imageFiles = if (!settings.skipImages) imageCompressor.scanImages(sourceDir) else emptyList()
            val audioFiles = if (!settings.skipAudio) AudioCompressor.scanAudioFiles(sourceDir) else emptyList()
            val videoFiles = if (!settings.skipVideo) VideoCompressor.scanVideoFiles(sourceDir) else emptyList()

            val totalOriginalSize = imageFiles.sumOf { it.length() } +
                                   audioFiles.sumOf { it.length() } +
                                   videoFiles.sumOf { it.length() }

            val totalFileCount = imageFiles.size + audioFiles.size + videoFiles.size

            Log.i(TAG, "Total files: $totalFileCount, Total original size: ${totalOriginalSize / (1024 * 1024)} MB")

            var totalCompressedSize = 0L
            var totalProcessed = 0
            var totalFailed = 0

            // Phase 1: Compress Images (Native Kotlin with WebP, parallel)
            if (!settings.skipImages) {
                Log.i(TAG, "Starting image compression...")

                val result = imageCompressor.compressImages(
                    sourceDir,
                    outputDir,
                    settings,
                    progressTracker,
                    imageFiles
                )

                Log.d(TAG, "Image compression complete: success=${result.success}")

                // Always aggregate results, even if some/all files failed
                totalProcessed += result.filesProcessed
                totalFailed += result.filesFailed
                totalCompressedSize += result.compressedSizeBytes

                if (result.success) {
                    Log.i(TAG, "Image compression complete: ${result.filesProcessed} succeeded, ${result.filesFailed} failed")
                } else {
                    Log.w(TAG, "Image compression had issues: ${result.filesProcessed} succeeded, ${result.filesFailed} failed, error: ${result.error}")
                }
            }

            // Phase 2: Compress Audio (using FFmpeg-Kit, sequential)
            if (!settings.skipAudio) {
                Log.i(TAG, "Starting audio compression...")

                val totalAudio = audioFiles.size
                Log.i(TAG, "Found $totalAudio audio files")

                var audioProcessed = 0
                audioFiles.forEachIndexed { index, audioFile ->
                    val relativePath = audioFile.relativeTo(sourceDir).path
                    val outputFile = File(outputDir, relativePath)

                    Log.d(TAG, "Processing audio ${index + 1}/$totalAudio: ${audioFile.name}")

                    // Update progress
                    updateProgress(
                        progressTracker,
                        "compress_audio",
                        index + 1,
                        totalAudio,
                        audioFile.name,
                        startTime,
                        totalOriginalSize,
                        totalCompressedSize
                    )

                    val result = AudioCompressor.compressAudio(
                        audioFile,
                        outputFile,
                        settings.audioQuality,
                        settings.threads
                    )

                    if (result.success) {
                        audioProcessed++
                        totalProcessed++
                        totalCompressedSize += result.compressedSize
                    } else {
                        totalFailed++
                        // For failed files, count original size (no reduction)
                        totalCompressedSize += result.originalSize
                        Log.w(TAG, "Failed to compress audio: ${audioFile.name}")
                    }
                }

                Log.i(TAG, "Audio compression complete: $audioProcessed succeeded out of $totalAudio")
            }

            // Phase 3: Compress Video (using FFmpeg-Kit, sequential)
            if (!settings.skipVideo) {
                Log.i(TAG, "Starting video compression...")

                val totalVideo = videoFiles.size
                Log.i(TAG, "Found $totalVideo video files")

                var videoProcessed = 0
                videoFiles.forEachIndexed { index, videoFile ->
                    val relativePath = videoFile.relativeTo(sourceDir).path
                    val outputFile = File(outputDir, relativePath)

                    Log.d(TAG, "Processing video ${index + 1}/$totalVideo: ${videoFile.name}")

                    // Update progress
                    updateProgress(
                        progressTracker,
                        "compress_video",
                        index + 1,
                        totalVideo,
                        videoFile.name,
                        startTime,
                        totalOriginalSize,
                        totalCompressedSize
                    )

                    val result = VideoCompressor.compressVideo(
                        videoFile,
                        outputFile,
                        settings.videoQuality,
                        settings.threads
                    )

                    if (result.success) {
                        videoProcessed++
                        totalProcessed++
                        totalCompressedSize += result.compressedSize
                    } else {
                        totalFailed++
                        // For failed files, count original size (no reduction)
                        totalCompressedSize += result.originalSize
                        Log.w(TAG, "Failed to compress video: ${videoFile.name}")
                    }
                }

                Log.i(TAG, "Video compression complete: $videoProcessed succeeded out of $totalVideo")
            }

            // Calculate reduction
            val reductionPercent = if (totalOriginalSize > 0) {
                ((totalOriginalSize - totalCompressedSize).toDouble() / totalOriginalSize) * 100.0
            } else {
                0.0
            }

            // Final progress update
            Log.i(TAG, "All phases complete. Total: $totalProcessed processed, $totalFailed failed out of $totalFileCount")
            updateProgress(
                progressTracker,
                "compress",
                totalProcessed + totalFailed,
                totalFileCount,
                "Complete",
                startTime,
                totalOriginalSize,
                totalCompressedSize,
                status = "completed"
            )

            // Success if:
            // - At least one file was processed successfully, OR
            // - Nothing to do (zero files found) is graceful success
            val isSuccess = totalProcessed > 0 || totalFileCount == 0

            Log.d(TAG, "Returning compression result...")
            CompressionResult(
                success = isSuccess,
                filesProcessed = totalProcessed,
                filesFailed = totalFailed,
                originalSizeBytes = totalOriginalSize,
                compressedSizeBytes = totalCompressedSize,
                reductionPercent = reductionPercent
            )

        } catch (e: Exception) {
            Log.e(TAG, "Compression error: ${e.message}", e)
            e.printStackTrace()

            // Update progress with error
            updateProgress(
                progressTracker,
                "compress",
                0,
                0,
                "Error",
                startTime,
                0,
                0,
                status = "failed",
                errorMessage = e.message ?: "Unknown error"
            )

            CompressionResult(
                success = false,
                filesProcessed = 0,
                filesFailed = 0,
                originalSizeBytes = 0,
                compressedSizeBytes = 0,
                reductionPercent = 0.0,
                error = e.message
            )
        }
    }

    private fun updateProgress(
        tracker: ProgressTracker,
        operation: String,
        processed: Int,
        total: Int,
        currentFile: String,
        startTime: Long,
        originalSize: Long,
        compressedSize: Long,
        status: String = "in_progress",
        errorMessage: String? = null
    ) {
        try {
            val data = ProgressData().apply {
                this.operation = operation
                this.status = status
                this.totalFiles = total
                this.processedFiles = processed
                this.currentFile = currentFile
                this.startTime = startTime
                this.lastUpdateTime = System.currentTimeMillis()
                this.originalSizeBytes = originalSize
                this.compressedSizeBytes = compressedSize
                this.errorMessage = errorMessage
            }
            tracker.writeProgress(data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update progress", e)
        }
    }

    /**
     * Result of scanning for compressible files
     */
    data class ScanResult(
        val imageCount: Int,
        val imageSizeBytes: Long,
        val audioCount: Int,
        val audioSizeBytes: Long,
        val videoCount: Int,
        val videoSizeBytes: Long,
        val totalSizeBytes: Long
    ) {
        fun getTotalCount() = imageCount + audioCount + videoCount
    }

    /**
     * Result of compression operation
     */
    data class CompressionResult(
        val success: Boolean,
        val filesProcessed: Int,
        val filesFailed: Int,
        val originalSizeBytes: Long,
        val compressedSizeBytes: Long,
        val reductionPercent: Double,
        val error: String? = null
    )
}
