package com.renpytool

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Handles image compression using Android's native Bitmap APIs with WebP support
 * Replaces Python/Pillow implementation for better performance and smaller files
 */
class ImageCompressor(private val context: Context) {

    companion object {
        private const val TAG = "ImageCompressor"
        private val SUPPORTED_FORMATS = setOf("png", "jpg", "jpeg", "bmp", "webp")
    }

    data class CompressResult(
        val success: Boolean,
        val originalSize: Long = 0,
        val compressedSize: Long = 0,
        val errorMessage: String? = null
    )

    /**
     * Scan directory for compressible images
     */
    fun scanImages(directory: File): List<File> {
        Log.i(TAG, "Scanning for images in: ${directory.absolutePath}")

        val images = mutableListOf<File>()
        try {
            directory.walkTopDown()
                .filter { it.isFile }
                .filter { it.extension.lowercase() in SUPPORTED_FORMATS }
                .forEach { file ->
                    images.add(file)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning images", e)
        }

        Log.i(TAG, "Found ${images.size} images")
        return images
    }

    /**
     * Compress images in parallel using coroutines and WebP format
     */
    suspend fun compressImages(
        sourceDir: File,
        outputDir: File,
        settings: CompressionSettings,
        progressTracker: ProgressTracker,
        preScannedFiles: List<File>? = null
    ): CompressionManager.CompressionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val semaphore = Semaphore(settings.threads)

        try {
            // Use pre-scanned files if provided, otherwise scan
            val images = preScannedFiles ?: scanImages(sourceDir)
            val totalImages = images.size
            Log.i(TAG, "Starting compression of $totalImages images")

            // Calculate total original size upfront
            val totalOriginalSize = images.sumOf { it.length() }
            Log.i(TAG, "Total original size: ${totalOriginalSize / (1024 * 1024)} MB")

            // Initialize progress
            updateProgress(
                progressTracker,
                0,
                totalImages,
                "Starting compression...",
                startTime,
                totalOriginalSize,
                0
            )

            // Track progress with atomic counters
            var processedCount = 0
            var totalCompressed = 0L
            val lock = Any()

            // Process images in parallel
            val results = images.mapIndexed { index, imageFile ->
                async {
                    semaphore.withPermit {
                        val result = compressSingleImage(imageFile, sourceDir, outputDir, settings)

                        // Update progress after each image (synchronized)
                        synchronized(lock) {
                            processedCount++
                            // For failed files, count original size (no reduction)
                            totalCompressed += if (result.success) {
                                result.compressedSize
                            } else {
                                result.originalSize
                            }

                            updateProgress(
                                progressTracker,
                                processedCount,
                                totalImages,
                                imageFile.name,
                                startTime,
                                totalOriginalSize,
                                totalCompressed
                            )
                        }

                        result
                    }
                }
            }.awaitAll()

            // Aggregate results
            val successful = results.count { it.success }
            val failed = results.count { !it.success }
            val reductionPercent = if (totalOriginalSize > 0) {
                ((totalOriginalSize - totalCompressed).toDouble() / totalOriginalSize) * 100.0
            } else {
                0.0
            }

            Log.i(TAG, "Image compression complete: $successful succeeded, $failed failed")
            Log.i(TAG, "Size: $totalOriginalSize -> $totalCompressed (${String.format("%.1f", reductionPercent)}% reduction)")

            // Final progress update
            updateProgress(
                progressTracker,
                totalImages,
                totalImages,
                "Complete",
                startTime,
                totalOriginalSize,
                totalCompressed,
                status = "in_progress"  // Let CompressionManager handle final status
            )

            CompressionManager.CompressionResult(
                success = successful > 0 || totalImages == 0,  // Success if we processed files or nothing to do
                filesProcessed = successful,
                filesFailed = failed,
                originalSizeBytes = totalOriginalSize,
                compressedSizeBytes = totalCompressed,
                reductionPercent = reductionPercent
            )

        } catch (e: Exception) {
            Log.e(TAG, "Image compression error", e)

            updateProgress(
                progressTracker,
                0,
                0,
                "Error",
                startTime,
                0,
                0,
                status = "failed",
                errorMessage = e.message ?: "Unknown error"
            )

            CompressionManager.CompressionResult(
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

    /**
     * Compress a single image file to WebP format
     */
    private fun compressSingleImage(
        imageFile: File,
        sourceDir: File,
        outputDir: File,
        settings: CompressionSettings
    ): CompressResult {
        var inputBitmap: Bitmap? = null
        try {
            val originalSize = imageFile.length()

            // Decode image safely
            inputBitmap = decodeSafely(imageFile)
            if (inputBitmap == null) {
                Log.w(TAG, "Failed to decode: ${imageFile.name}")
                return CompressResult(false, originalSize, 0, "Failed to decode image")
            }

            // Determine output format based on settings
            val format = if (settings.imageLossless) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                Bitmap.CompressFormat.WEBP_LOSSY
            }

            // Prepare output file with original extension preserved
            val relativePath = imageFile.relativeTo(sourceDir).path
            val outputFile = File(outputDir, relativePath)
            outputFile.parentFile?.mkdirs()

            val originalExt = imageFile.extension
            val tempFile = File(
                outputFile.parentFile,
                "${outputFile.nameWithoutExtension}.temp.${originalExt}"
            )

            // Compress to WebP
            FileOutputStream(tempFile).use { out ->
                val quality = if (settings.imageLossless) {
                    // For lossless, quality controls compression effort (speed vs file size)
                    // Map imageMethod (0=fast, 4=average, 6=slow) to quality
                    when {
                        settings.imageMethod <= 2 -> 80  // Fast: less compression, faster
                        settings.imageMethod in 3..5 -> 90  // Average: balanced
                        else -> 100  // Slow: best compression, slower
                    }
                } else {
                    settings.imageQuality  // For lossy, quality 1-100
                }

                val success = inputBitmap.compress(format, quality, out)
                if (!success) {
                    Log.w(TAG, "Bitmap.compress() returned false for: ${imageFile.name}")
                    return CompressResult(false, originalSize, 0, "Compression failed")
                }
            }

            val compressedSize = tempFile.length()

            // Safety check: ensure compressed file is not empty
            if (compressedSize == 0L) {
                tempFile.delete()
                Log.w(TAG, "Compressed file is 0 bytes: ${imageFile.name}")
                return CompressResult(false, originalSize, 0, "Compressed file is 0 bytes")
            }

            // Rename temp file to final output
            val finalFile = File(outputFile.parentFile, outputFile.name)
            if (finalFile.exists()) finalFile.delete()

            if (!tempFile.renameTo(finalFile)) {
                tempFile.delete()
                return CompressResult(false, originalSize, 0, "Failed to rename temp file")
            }

            Log.d(TAG, "Compressed: ${imageFile.name} ($originalSize -> $compressedSize bytes)")

            return CompressResult(true, originalSize, compressedSize)

        } catch (e: Exception) {
            Log.e(TAG, "Error compressing ${imageFile.name}", e)
            return CompressResult(false, imageFile.length(), 0, e.message)
        } finally {
            inputBitmap?.recycle()
        }
    }

    /**
     * Decode image file safely with proper bitmap configuration
     */
    private fun decodeSafely(file: File): Bitmap? {
        try {
            // First pass: get image dimensions and type
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            // Second pass: decode with proper configuration
            options.inJustDecodeBounds = false

            // Use RGB_565 for opaque images (50% less memory), ARGB_8888 for transparent
            options.inPreferredConfig = if (options.outMimeType == "image/jpeg") {
                Bitmap.Config.RGB_565  // JPEG never has transparency
            } else {
                Bitmap.Config.ARGB_8888  // Might have transparency (PNG, WebP, BMP)
            }

            return BitmapFactory.decodeFile(file.absolutePath, options)

        } catch (e: Exception) {
            Log.e(TAG, "Error decoding ${file.name}", e)
            return null
        }
    }

    /**
     * Update compression progress
     */
    private fun updateProgress(
        tracker: ProgressTracker,
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
                this.operation = "compress_images"
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
}
