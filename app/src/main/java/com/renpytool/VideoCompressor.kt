package com.renpytool

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles video compression using FFmpeg-Kit with Android MediaCodec
 * Compresses videos to H.264/MP4 format using hardware acceleration
 */
object VideoCompressor {

    private const val TAG = "VideoCompressor"

    // Video quality presets (bitrate values for MediaCodec H.264)
    enum class VideoQuality(val crf: Int, val preset: String) {
        HIGH(18, "medium"),      // 8Mbps - High quality, larger files
        MEDIUM(23, "medium"),    // 4Mbps - Balanced quality/size
        LOW(28, "fast"),         // 2Mbps - Lower quality, smaller files
        VERYLOW(32, "veryfast")  // 1Mbps - Very compressed
    }

    data class CompressResult(
        val success: Boolean,
        val originalSize: Long = 0,
        val compressedSize: Long = 0,
        val errorMessage: String? = null
    )

    /**
     * Scan directory for video files
     */
    fun scanVideoFiles(directory: File): List<File> {
        val videoExtensions = setOf("mp4", "avi", "mkv", "webm", "mov", "ogv", "mpg", "mpeg", "flv", "wmv")
        val files = mutableListOf<File>()

        Log.i(TAG, "Scanning for video files in: ${directory.absolutePath}")

        try {
            directory.walkTopDown()
                .filter { it.isFile }
                .filter { it.extension.lowercase() in videoExtensions }
                .forEach { file ->
                    files.add(file)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning video files", e)
        }

        Log.i(TAG, "Found ${files.size} video files")
        return files
    }

    /**
     * Compress video file to H.264/MP4 format
     */
    suspend fun compressVideo(
        inputFile: File,
        outputFile: File,
        quality: VideoQuality,
        threads: Int? = null
    ): CompressResult = withContext(Dispatchers.IO) {
        try {
            if (!inputFile.exists()) {
                return@withContext CompressResult(false, errorMessage = "Input file does not exist")
            }

            val originalSize = inputFile.length()

            // Create output directory if needed
            outputFile.parentFile?.mkdirs()

            // Temporary output file with .temp.mp4 extension
            val tempOutput = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.temp.mp4")
            if (tempOutput.exists()) tempOutput.delete()

            // Build FFmpeg command for H.264 video compression using Android MediaCodec
            // -c:v h264_mediacodec: Use Android's hardware H.264 encoder
            // -b:v: Video bitrate (calculated from quality)
            // -c:a aac: Re-encode audio to AAC (compatible with MP4)
            // -b:a 128k: Audio bitrate
            // -movflags +faststart: Optimize for web streaming

            // Convert CRF-based quality to bitrate for MediaCodec
            // MediaCodec doesn't support CRF, so we use bitrate instead
            val videoBitrate = when (quality) {
                VideoQuality.HIGH -> "8M"      // High quality
                VideoQuality.MEDIUM -> "4M"    // Medium quality
                VideoQuality.LOW -> "2M"       // Low quality
                VideoQuality.VERYLOW -> "1M"   // Very compressed
            }

            val threadArg = threads?.let { "-threads $it" } ?: ""
            val command = buildString {
                append("-i \"${inputFile.absolutePath}\" ")
                append("-c:v h264_mediacodec ")
                append("-b:v $videoBitrate ")
                append("-c:a aac ")
                append("-b:a 128k ")
                append("-movflags +faststart ")
                if (threadArg.isNotEmpty()) append("$threadArg ")
                append("-y \"${tempOutput.absolutePath}\"")
            }

            Log.i(TAG, "Compressing video: ${inputFile.name} (bitrate=$videoBitrate, quality=${quality.name})")

            // Execute FFmpeg command
            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode

            if (ReturnCode.isSuccess(returnCode)) {
                // Rename temp file to final output with original extension
                val finalOutput = File(outputFile.parentFile, outputFile.name)
                if (finalOutput.exists()) finalOutput.delete()
                tempOutput.renameTo(finalOutput)

                val compressedSize = finalOutput.length()
                Log.i(TAG, "Video compression succeeded: ${inputFile.name} (${originalSize}b -> ${compressedSize}b)")

                CompressResult(
                    success = true,
                    originalSize = originalSize,
                    compressedSize = compressedSize
                )
            } else {
                val error = "FFmpeg returned code ${returnCode.value}"
                Log.e(TAG, "Video compression failed: $error")
                Log.e(TAG, "FFmpeg output: ${session.output}")
                if (tempOutput.exists()) tempOutput.delete()
                CompressResult(false, errorMessage = error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video compression exception", e)
            CompressResult(false, errorMessage = e.message)
        }
    }
}
