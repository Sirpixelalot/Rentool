package com.renpytool

import android.content.Context
import android.util.Log
import com.android.apksig.ApkSigner
import com.renpytool.keystore.KeystoreManager
import com.renpytool.keystore.SigningOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Handles compression of Ren'Py APK files
 * Extracts x- prefixed assets, compresses them, repackages and signs the APK
 */
class ApkCompressor(private val context: Context) {

    companion object {
        private const val TAG = "ApkCompressor"
        private const val RENPY_ASSET_PREFIX = "x-"
        private const val ASSETS_PATH = "assets/"
    }

    /**
     * Detect if an APK is a Ren'Py game by checking for x- prefixed assets
     */
    fun detectRenpyApk(apkFile: File): Boolean {
        return try {
            ZipFile(apkFile).use { zip ->
                zip.entries().asSequence()
                    .any { entry ->
                        !entry.isDirectory &&
                        entry.name.startsWith(ASSETS_PATH) &&
                        entry.name.contains("/$RENPY_ASSET_PREFIX")
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting Ren'Py APK", e)
            false
        }
    }

    /**
     * Main APK compression orchestrator
     */
    suspend fun compressApk(
        apkFile: File,
        outputApk: File,
        settings: CompressionSettings,
        progressTracker: ProgressTracker,
        signingOption: SigningOption? = null
    ): CompressionManager.CompressionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        val tempExtractDir = File(context.cacheDir, "apk_extract_${System.currentTimeMillis()}")
        val tempCompressDir = File(context.cacheDir, "apk_compress_${System.currentTimeMillis()}")
        val tempUnsignedApk = File(context.cacheDir, "unsigned_${outputApk.name}")

        try {
            // 1. Extract Ren'Py assets
            updateProgress(progressTracker, "extract_apk", "Extracting APK assets...", startTime)
            val extractedFiles = extractApkAssets(apkFile, tempExtractDir)

            if (extractedFiles.isEmpty()) {
                throw Exception("No Ren'Py assets found in APK")
            }

            Log.i(TAG, "Extracted ${extractedFiles.size} assets from APK")

            // 2. Compress extracted assets using existing CompressionManager
            updateProgress(progressTracker, "compress_assets", "Compressing game assets...", startTime)
            val compressionManager = CompressionManager(context)
            val compressionResult = compressionManager.compressGame(
                tempExtractDir,
                tempCompressDir,
                settings,
                progressTracker
            )

            // Override the "completed" status from CompressionManager - we still have APK steps to do
            updateProgress(progressTracker, "compress_assets", "Compression complete, continuing...", startTime)

            // 2.5. Copy skipped media files unchanged
            // When media types are disabled, CompressionManager doesn't copy them
            // For APK compression we need to ensure they're still in the output
            copySkippedFiles(tempExtractDir, tempCompressDir, settings)

            // 3. Repackage APK with compressed assets
            updateProgress(progressTracker, "repackage_apk", "Repackaging APK...", startTime)
            repackageApk(apkFile, tempCompressDir, tempUnsignedApk)

            // 3.5. Align the APK (required for Android R+)
            updateProgress(progressTracker, "align_apk", "Aligning APK...", startTime)
            val alignedApk = File(context.cacheDir, "aligned_${outputApk.name}")
            alignApk(tempUnsignedApk, alignedApk)

            // 4. Sign the APK
            updateProgress(progressTracker, "sign_apk", "Signing APK...", startTime)
            signApk(alignedApk, outputApk, signingOption)

            // Clean up aligned APK
            alignedApk.delete()

            Log.i(TAG, "APK compression complete: ${apkFile.name} -> ${outputApk.name}")

            // Calculate APK size reduction
            val originalApkSize = apkFile.length()
            val compressedApkSize = outputApk.length()
            val apkReduction = if (originalApkSize > 0) {
                ((originalApkSize - compressedApkSize).toDouble() / originalApkSize) * 100.0
            } else {
                0.0
            }

            // Final progress update
            val finalProgress = ProgressData().apply {
                operation = "compress_apk"
                status = "completed"
                this.startTime = startTime
                lastUpdateTime = System.currentTimeMillis()
                totalFiles = compressionResult.filesProcessed + compressionResult.filesFailed
                processedFiles = totalFiles
                currentFile = "Complete"
                originalSizeBytes = originalApkSize
                compressedSizeBytes = compressedApkSize
            }
            progressTracker.writeProgress(finalProgress)

            Log.i(TAG, "APK size: ${originalApkSize / (1024 * 1024)}MB -> ${compressedApkSize / (1024 * 1024)}MB (${String.format("%.1f", apkReduction)}% reduction)")

            compressionResult.copy(
                originalSizeBytes = originalApkSize,
                compressedSizeBytes = compressedApkSize,
                reductionPercent = apkReduction
            )

        } catch (e: Exception) {
            Log.e(TAG, "APK compression failed", e)

            // Error progress update
            val errorProgress = ProgressData().apply {
                operation = "compress_apk"
                status = "failed"
                this.startTime = startTime
                lastUpdateTime = System.currentTimeMillis()
                errorMessage = e.message ?: "Unknown error"
            }
            progressTracker.writeProgress(errorProgress)

            throw e
        } finally {
            // Always cleanup temp directories
            tempExtractDir.deleteRecursively()
            tempCompressDir.deleteRecursively()
            tempUnsignedApk.delete()
            Log.d(TAG, "Cleaned up temporary files")
        }
    }

    /**
     * Extract x- prefixed assets from APK, normalizing paths by removing x- prefix
     */
    private fun extractApkAssets(apkFile: File, outputDir: File): List<File> {
        val extractedFiles = mutableListOf<File>()

        ZipFile(apkFile).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { it.name.startsWith(ASSETS_PATH) }
                .filter { hasRenpyPrefix(it.name) }
                .forEach { entry ->
                    val normalizedPath = normalizeRenpyPath(entry.name)
                    val outputFile = File(outputDir, normalizedPath)

                    // Create parent directories
                    outputFile.parentFile?.mkdirs()

                    // Extract file
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    extractedFiles.add(outputFile)
                    Log.d(TAG, "Extracted: ${entry.name} -> $normalizedPath")
                }
        }

        return extractedFiles
    }

    /**
     * Check if path contains Ren'Py x- prefix
     */
    private fun hasRenpyPrefix(path: String): Boolean {
        return path.split("/").any { it.startsWith(RENPY_ASSET_PREFIX) }
    }

    /**
     * Normalize Ren'Py path by removing x- prefix and assets/ prefix
     * Example: "assets/x-game/x-images/x-bg.png" -> "game/images/bg.png"
     */
    private fun normalizeRenpyPath(path: String): String {
        return path
            .removePrefix(ASSETS_PATH)
            .split("/")
            .joinToString("/") { part ->
                if (part.startsWith(RENPY_ASSET_PREFIX)) {
                    part.removePrefix(RENPY_ASSET_PREFIX)
                } else {
                    part
                }
            }
    }

    /**
     * Restore Ren'Py x- prefix to path
     * Example: "game/images/bg.png" -> "assets/x-game/x-images/x-bg.png"
     */
    private fun restoreRenpyPrefix(path: String): String {
        val parts = path.split("/")
        return ASSETS_PATH + parts.joinToString("/") { "${RENPY_ASSET_PREFIX}$it" }
    }

    /**
     * Copy files that were skipped during compression
     * This includes:
     * 1. Disabled media types (e.g., if images are disabled, copy original images)
     * 2. Non-media files (e.g., .rpy, .rpyc, .txt, .json, fonts, etc.)
     */
    private fun copySkippedFiles(sourceDir: File, destDir: File, settings: CompressionSettings) {
        val imageExtensions = listOf("png", "jpg", "jpeg", "webp")
        val audioExtensions = listOf("ogg", "opus", "mp3", "wav")
        val videoExtensions = listOf("webm", "ogv", "mp4", "avi", "mkv")

        sourceDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val ext = file.extension.lowercase()

                // Determine if this file should be copied
                val isImage = imageExtensions.contains(ext)
                val isAudio = audioExtensions.contains(ext)
                val isVideo = videoExtensions.contains(ext)
                val isMedia = isImage || isAudio || isVideo

                val shouldCopy = when {
                    // Copy disabled media types
                    settings.skipImages && isImage -> true
                    settings.skipAudio && isAudio -> true
                    settings.skipVideo && isVideo -> true
                    // Copy all non-media files (scripts, fonts, text files, etc.)
                    !isMedia -> true
                    else -> false
                }

                if (shouldCopy) {
                    val relativePath = file.relativeTo(sourceDir).path
                    val destFile = File(destDir, relativePath)

                    // Only copy if not already in destination (compression may have failed and copied original)
                    if (!destFile.exists()) {
                        destFile.parentFile?.mkdirs()
                        file.copyTo(destFile, overwrite = false)
                        Log.d(TAG, "Copied non-media/skipped file: $relativePath")
                    }
                }
            }
    }

    /**
     * Repackage APK with compressed assets
     */
    private fun repackageApk(
        originalApk: File,
        compressedAssetsDir: File,
        outputApk: File
    ) {
        ZipOutputStream(FileOutputStream(outputApk)).use { out ->
            // Copy all entries from original APK EXCEPT Ren'Py assets (which we'll replace with compressed versions)
            ZipFile(originalApk).use { zip ->
                zip.entries().asSequence()
                    .filter { entry ->
                        // Exclude Ren'Py assets (anything in assets/ with x- prefix)
                        // Keep everything else (manifests, dex files, resources, libs, etc.)
                        !(entry.name.startsWith(ASSETS_PATH) && hasRenpyPrefix(entry.name))
                    }
                    .forEach { entry ->
                        if (!entry.isDirectory) {
                            copyZipEntry(zip, entry, out)
                            Log.d(TAG, "Copied: ${entry.name}")
                        }
                    }
            }

            // Add compressed assets with x- prefix restored
            compressedAssetsDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val relativePath = file.relativeTo(compressedAssetsDir).path
                    val renpyPath = restoreRenpyPrefix(relativePath)

                    addZipEntry(out, renpyPath, file)
                    Log.d(TAG, "Added: $relativePath -> $renpyPath")
                }
        }
    }

    /**
     * Copy a ZIP entry from input to output, preserving all metadata and handling alignment
     */
    private fun copyZipEntry(zip: ZipFile, entry: ZipEntry, out: ZipOutputStream) {
        val newEntry = ZipEntry(entry.name).apply {
            time = entry.time
            comment = entry.comment
            method = entry.method

            // For STORED entries, we must set size and CRC
            if (entry.method == ZipEntry.STORED) {
                size = entry.size
                compressedSize = entry.size
                crc = entry.crc

                // For resources.arsc on Android R+, ensure 4-byte alignment
                // by adding padding to the extra field
                if (entry.name == "resources.arsc") {
                    val originalExtra = entry.extra ?: ByteArray(0)
                    // ZIP local file header = 30 bytes + filename length + extra length
                    // We need file data to start at 4-byte boundary
                    val headerSize = 30 + entry.name.toByteArray().size
                    val dataOffset = headerSize + originalExtra.size
                    val padding = (4 - (dataOffset % 4)) % 4

                    if (padding > 0) {
                        Log.d(TAG, "Adding $padding bytes padding to align resources.arsc")
                        extra = ByteArray(originalExtra.size + padding).apply {
                            System.arraycopy(originalExtra, 0, this, 0, originalExtra.size)
                        }
                    } else {
                        extra = originalExtra
                    }
                } else {
                    extra = entry.extra
                }
            } else {
                extra = entry.extra
            }
        }

        out.putNextEntry(newEntry)
        zip.getInputStream(entry).use { input ->
            input.copyTo(out)
        }
        out.closeEntry()
    }

    /**
     * Add a file to ZIP output stream
     */
    private fun addZipEntry(out: ZipOutputStream, entryName: String, file: File) {
        out.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { input ->
            input.copyTo(out)
        }
        out.closeEntry()
    }

    /**
     * Align APK to 4-byte boundaries (required for Android R+)
     * This ensures resources.arsc and other uncompressed files are properly aligned
     */
    private fun alignApk(inputApk: File, outputApk: File) {
        try {
            ZipFile(inputApk).use { zipFile ->
                ZipOutputStream(FileOutputStream(outputApk)).use { outZip ->
                    val entries = zipFile.entries().toList()

                    entries.forEach { entry ->
                        if (!entry.isDirectory) {
                            val newEntry = ZipEntry(entry.name).apply {
                                time = entry.time
                                comment = entry.comment
                                method = entry.method

                                if (entry.method == ZipEntry.STORED) {
                                    size = entry.size
                                    compressedSize = entry.size
                                    crc = entry.crc

                                    // Calculate alignment padding
                                    val alignment = 4
                                    val headerSize = 30 + entry.name.toByteArray().size
                                    var extraSize = 0

                                    // Calculate required padding
                                    var padding = 0
                                    var currentOffset = headerSize + extraSize
                                    while ((currentOffset % alignment) != 0) {
                                        padding++
                                        currentOffset++
                                    }

                                    if (padding > 0) {
                                        extra = ByteArray(padding)
                                        Log.d(TAG, "Aligned ${entry.name}: added $padding bytes padding")
                                    }
                                } else {
                                    extra = entry.extra
                                }
                            }

                            outZip.putNextEntry(newEntry)
                            zipFile.getInputStream(entry).use { input ->
                                input.copyTo(outZip)
                            }
                            outZip.closeEntry()
                        }
                    }
                }
            }
            Log.i(TAG, "APK aligned successfully")
        } catch (e: Exception) {
            Log.e(TAG, "APK alignment failed", e)
            throw Exception("Failed to align APK: ${e.message}", e)
        }
    }

    /**
     * Sign APK using either a random key or a persistent keystore
     */
    private fun signApk(unsignedApk: File, signedApk: File, signingOption: SigningOption?) {
        try {
            val (privateKey, certificate) = when (signingOption) {
                is SigningOption.CreateNew -> {
                    // Create new persistent keystore
                    Log.i(TAG, "Creating new persistent keystore: ${signingOption.keystoreName}")
                    val keystoreManager = KeystoreManager(context)
                    val result = keystoreManager.createKeystore(signingOption.keystoreName, signingOption.password)

                    if (result.isFailure) {
                        throw Exception("Failed to create keystore: ${result.exceptionOrNull()?.message}")
                    }

                    val keystoreInfo = result.getOrThrow()
                    val loadResult = keystoreManager.loadKeystore(keystoreInfo)

                    if (loadResult.isFailure) {
                        throw Exception("Failed to load created keystore: ${loadResult.exceptionOrNull()?.message}")
                    }

                    loadResult.getOrThrow()
                }

                is SigningOption.UseExisting -> {
                    // Use existing keystore
                    Log.i(TAG, "Using existing keystore: ${signingOption.keystoreInfo.name}")
                    val keystoreManager = KeystoreManager(context)
                    val result = keystoreManager.loadKeystore(signingOption.keystoreInfo)

                    if (result.isFailure) {
                        throw Exception("Failed to load keystore: ${result.exceptionOrNull()?.message}")
                    }

                    result.getOrThrow()
                }

                else -> {
                    // Random key (default behavior)
                    Log.i(TAG, "Generating random signing key")
                    generateRandomKey()
                }
            }

            // Configure APK signer
            val signerConfig = ApkSigner.SignerConfig.Builder(
                "rentool",
                privateKey,
                listOf(certificate)
            ).build()

            // Sign APK with v1, v2, and v3 signature schemes
            ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsignedApk)
                .setOutputApk(signedApk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setV4SigningEnabled(false) // V4 is for streaming, not needed
                .build()
                .sign()

            Log.i(TAG, "APK signed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "APK signing failed", e)
            throw Exception("Failed to sign APK: ${e.message}", e)
        }
    }

    /**
     * Generate a random RSA key pair and self-signed certificate
     */
    private fun generateRandomKey(): Pair<PrivateKey, X509Certificate> {
        // Generate RSA key pair
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()

        // Create self-signed certificate
        val now = Date()
        val calendar = Calendar.getInstance()
        calendar.time = now
        calendar.add(Calendar.YEAR, 25) // Valid for 25 years
        val expiryDate = calendar.time

        val issuer = X500Name("CN=Rentool, OU=Dev, O=Rentool, C=US")
        val serialNumber = BigInteger.valueOf(System.currentTimeMillis())

        val certBuilder = JcaX509v3CertificateBuilder(
            issuer,
            serialNumber,
            now,
            expiryDate,
            issuer,
            keyPair.public
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(certBuilder.build(signer))

        return Pair(keyPair.private, certificate)
    }

    /**
     * Update progress tracker with current operation
     */
    private fun updateProgress(
        tracker: ProgressTracker,
        operation: String,
        message: String,
        startTime: Long
    ) {
        val progress = ProgressData().apply {
            this.operation = operation
            status = "in_progress"
            this.startTime = startTime
            lastUpdateTime = System.currentTimeMillis()
            currentFile = message
            // Set file counts to show indeterminate progress (50%)
            totalFiles = 2
            processedFiles = 1
        }
        tracker.writeProgress(progress)
    }
}
