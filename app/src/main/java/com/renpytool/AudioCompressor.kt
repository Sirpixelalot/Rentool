package com.renpytool

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles audio compression using FFmpeg-Kit
 * Audio-only compression to Opus codec
 */
object AudioCompressor {

    private const val TAG = "AudioCompressor"

    // Audio quality presets (bitrates)
    enum class AudioQuality(val bitrate: String) {
        HIGH("128k"),
        MEDIUM("48k"),
        LOW("24k"),
        BAD("16k")
    }

    data class CompressResult(
        val success: Boolean,
        val originalSize: Long = 0,
        val compressedSize: Long = 0,
        val errorMessage: String? = null
    )

    /**
     * Scan directory for audio files
     */
    fun scanAudioFiles(directory: File): List<File> {
        val audioExtensions = setOf("ogg", "mp3", "wav", "flac", "m4a", "aac", "opus")
        val files = mutableListOf<File>()

        Log.i(TAG, "Scanning for audio files in: ${directory.absolutePath}")

        try {
            directory.walkTopDown()
                .filter { it.isFile }
                .filter { it.extension.lowercase() in audioExtensions }
                .forEach { file ->
                    files.add(file)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning audio files", e)
        }

        Log.i(TAG, "Found ${files.size} audio files")
        return files
    }

    /**
     * Compress audio file to Opus codec in OGG container
     */
    suspend fun compressAudio(
        inputFile: File,
        outputFile: File,
        quality: AudioQuality,
        threads: Int? = null
    ): CompressResult = withContext(Dispatchers.IO) {
        try {
            if (!inputFile.exists()) {
                return@withContext CompressResult(false, errorMessage = "Input file does not exist")
            }

            val originalSize = inputFile.length()

            // Create output directory if needed
            outputFile.parentFile?.mkdirs()

            // Temporary output file with .temp.ogg extension
            val tempOutput = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.temp.ogg")
            if (tempOutput.exists()) tempOutput.delete()

            // Build FFmpeg command for Opus audio compression
            val threadArg = threads?.let { "-threads ${it}" } ?: ""
            val command = "-i \"${inputFile.absolutePath}\" -c:a libopus -b:a ${quality.bitrate} -vbr on $threadArg -y \"${tempOutput.absolutePath}\""

            Log.i(TAG, "Compressing audio: ${inputFile.name} to Opus ${quality.bitrate}")

            // Execute FFmpeg command
            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode

            if (ReturnCode.isSuccess(returnCode)) {
                // Rename temp file to final output with original extension preserved
                val finalOutput = File(outputFile.parentFile, outputFile.name)
                if (finalOutput.exists()) finalOutput.delete()
                tempOutput.renameTo(finalOutput)

                val compressedSize = finalOutput.length()
                Log.i(TAG, "Audio compression succeeded: ${inputFile.name} (${originalSize}b -> ${compressedSize}b)")

                CompressResult(
                    success = true,
                    originalSize = originalSize,
                    compressedSize = compressedSize
                )
            } else {
                val error = "FFmpeg returned code ${returnCode.value}"
                Log.e(TAG, "Audio compression failed: $error")
                if (tempOutput.exists()) tempOutput.delete()
                CompressResult(false, errorMessage = error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio compression exception", e)
            CompressResult(false, errorMessage = e.message)
        }
    }
}
