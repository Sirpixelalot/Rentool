package com.renpytool.keystore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages persistent keystores for APK signing
 * Uses Android Keystore System to securely encrypt keystore passwords
 */
class KeystoreManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ENCRYPTION_KEY_ALIAS = "rentool_keystore_encryption_key"
        private const val PREFS_NAME = "keystore_manager"
        private const val KEYSTORE_DIR = "keystores"
        private const val KEYSTORE_ALIAS = "rentool_key"
        private const val KEYSTORE_TYPE = "BKS"  // Use BouncyCastle KeyStore (better Android support)
        private const val KEYSTORE_PROVIDER = "BC"  // BouncyCastle provider

        // Certificate details (fixed as per user preference)
        private const val CERT_CN = "Rentool"
        private const val CERT_OU = "Dev"
        private const val CERT_O = "Rentool"
        private const val CERT_C = "US"
        private const val CERT_VALIDITY_YEARS = 25
        private const val KEY_SIZE = 2048
    }

    init {
        // Ensure BouncyCastle provider is registered
        try {
            if (java.security.Security.getProvider(KEYSTORE_PROVIDER) == null) {
                java.security.Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
            }
        } catch (e: Exception) {
            // Provider might already be registered
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keystoresDir = File(context.filesDir, KEYSTORE_DIR).apply { mkdirs() }

    /**
     * Create a new keystore with a generated key pair
     */
    fun createKeystore(name: String, password: String): Result<KeystoreInfo> {
        return try {
            val keystoreFile = File(keystoresDir, "$name.bks")
            if (keystoreFile.exists()) {
                return Result.failure(Exception("Keystore with name '$name' already exists"))
            }

            // Generate RSA key pair
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(KEY_SIZE)
            val keyPair = keyPairGenerator.generateKeyPair()

            // Create self-signed certificate
            val now = Date()
            val calendar = Calendar.getInstance()
            calendar.time = now
            calendar.add(Calendar.YEAR, CERT_VALIDITY_YEARS)
            val expiryDate = calendar.time

            val issuer = X500Name("CN=$CERT_CN, OU=$CERT_OU, O=$CERT_O, C=$CERT_C")
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

            // Create and save keystore
            val keyStore = KeyStore.getInstance(KEYSTORE_TYPE, KEYSTORE_PROVIDER)
            keyStore.load(null, password.toCharArray())
            keyStore.setKeyEntry(
                KEYSTORE_ALIAS,
                keyPair.private,
                password.toCharArray(),
                arrayOf(certificate)
            )

            FileOutputStream(keystoreFile).use { fos ->
                keyStore.store(fos, password.toCharArray())
            }

            // Save encrypted password
            saveEncryptedPassword(name, password)

            // Create and return KeystoreInfo
            val keystoreInfo = KeystoreInfo(
                name = name,
                filePath = keystoreFile.absolutePath,
                alias = KEYSTORE_ALIAS,
                createdDate = System.currentTimeMillis(),
                certificateInfo = "CN=$CERT_CN, OU=$CERT_OU, O=$CERT_O, C=$CERT_C"
            )

            saveKeystoreMetadata(keystoreInfo)

            Result.success(keystoreInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load a keystore and return the private key and certificate
     */
    fun loadKeystore(keystoreInfo: KeystoreInfo): Result<Pair<PrivateKey, X509Certificate>> {
        return try {
            if (!keystoreInfo.exists()) {
                return Result.failure(Exception("Keystore file not found: ${keystoreInfo.name}"))
            }

            val password = getDecryptedPassword(keystoreInfo.name)
                ?: return Result.failure(Exception("Password not found for keystore: ${keystoreInfo.name}"))

            val keyStore = KeyStore.getInstance(KEYSTORE_TYPE, KEYSTORE_PROVIDER)
            FileInputStream(keystoreInfo.file).use { fis ->
                keyStore.load(fis, password.toCharArray())
            }

            val privateKey = keyStore.getKey(keystoreInfo.alias, password.toCharArray()) as PrivateKey
            val certificate = keyStore.getCertificate(keystoreInfo.alias) as X509Certificate

            Result.success(Pair(privateKey, certificate))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * List all available keystores
     */
    fun listKeystores(): List<KeystoreInfo> {
        val keystores = mutableListOf<KeystoreInfo>()
        val metadataKeys = prefs.all.keys.filter { it.startsWith("metadata_") }

        for (key in metadataKeys) {
            try {
                val name = key.removePrefix("metadata_")
                val metadata = prefs.getString(key, null) ?: continue
                val parts = metadata.split("|")
                if (parts.size >= 4) {
                    val keystoreInfo = KeystoreInfo(
                        name = name,
                        filePath = parts[0],
                        alias = parts[1],
                        createdDate = parts[2].toLongOrNull() ?: 0L,
                        certificateInfo = parts[3]
                    )
                    if (keystoreInfo.exists()) {
                        keystores.add(keystoreInfo)
                    } else {
                        // Clean up metadata for missing file
                        deleteKeystoreMetadata(name)
                    }
                }
            } catch (e: Exception) {
                // Skip invalid entries
            }
        }

        return keystores.sortedByDescending { it.createdDate }
    }

    /**
     * Delete a keystore
     */
    fun deleteKeystore(keystoreInfo: KeystoreInfo): Result<Unit> {
        return try {
            keystoreInfo.file.delete()
            deleteKeystoreMetadata(keystoreInfo.name)
            deleteEncryptedPassword(keystoreInfo.name)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export a keystore to a destination file
     */
    fun exportKeystore(keystoreInfo: KeystoreInfo, destinationFile: File): Result<Unit> {
        return try {
            if (!keystoreInfo.exists()) {
                return Result.failure(Exception("Keystore file not found"))
            }
            keystoreInfo.file.copyTo(destinationFile, overwrite = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import a keystore from a file
     */
    fun importKeystore(sourceFile: File, name: String, password: String): Result<KeystoreInfo> {
        return try {
            val keystoreFile = File(keystoresDir, "$name.bks")
            if (keystoreFile.exists()) {
                return Result.failure(Exception("Keystore with name '$name' already exists"))
            }

            // Validate the keystore file with provided password
            val keyStore = KeyStore.getInstance(KEYSTORE_TYPE, KEYSTORE_PROVIDER)
            FileInputStream(sourceFile).use { fis ->
                keyStore.load(fis, password.toCharArray())
            }

            // Get alias (use first alias if multiple exist)
            val alias = keyStore.aliases().toList().firstOrNull()
                ?: return Result.failure(Exception("No keys found in keystore"))

            // Get certificate info
            val cert = keyStore.getCertificate(alias) as? X509Certificate
                ?: return Result.failure(Exception("Certificate not found in keystore"))

            val certInfo = cert.subjectX500Principal.name

            // Copy file
            sourceFile.copyTo(keystoreFile, overwrite = false)

            // Save encrypted password
            saveEncryptedPassword(name, password)

            // Create and save metadata
            val keystoreInfo = KeystoreInfo(
                name = name,
                filePath = keystoreFile.absolutePath,
                alias = alias,
                createdDate = System.currentTimeMillis(),
                certificateInfo = certInfo
            )

            saveKeystoreMetadata(keystoreInfo)

            Result.success(keystoreInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Private helper methods ---

    private fun saveKeystoreMetadata(keystoreInfo: KeystoreInfo) {
        val metadata = "${keystoreInfo.filePath}|${keystoreInfo.alias}|${keystoreInfo.createdDate}|${keystoreInfo.certificateInfo}"
        prefs.edit().putString("metadata_${keystoreInfo.name}", metadata).apply()
    }

    private fun deleteKeystoreMetadata(name: String) {
        prefs.edit().remove("metadata_$name").apply()
    }

    private fun saveEncryptedPassword(keystoreName: String, password: String) {
        val encryptedPassword = encryptPassword(password)
        prefs.edit().putString("password_$keystoreName", encryptedPassword).apply()
    }

    private fun getDecryptedPassword(keystoreName: String): String? {
        val encryptedPassword = prefs.getString("password_$keystoreName", null) ?: return null
        return decryptPassword(encryptedPassword)
    }

    private fun deleteEncryptedPassword(keystoreName: String) {
        prefs.edit().remove("password_$keystoreName").apply()
    }

    private fun generateSecurePassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        return (1..32)
            .map { chars.random() }
            .joinToString("")
    }

    /**
     * Encrypt password using Android Keystore System
     */
    private fun encryptPassword(password: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateEncryptionKey())

        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(password.toByteArray())

        // Combine IV and encrypted bytes
        val combined = iv + encryptedBytes
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt password using Android Keystore System
     */
    private fun decryptPassword(encryptedData: String): String? {
        return try {
            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)

            // Extract IV (first 12 bytes for GCM)
            val iv = combined.copyOfRange(0, 12)
            val encryptedBytes = combined.copyOfRange(12, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateEncryptionKey(), spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get or create the encryption key in Android Keystore
     */
    private fun getOrCreateEncryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(ENCRYPTION_KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                ENCRYPTION_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }

        return keyStore.getKey(ENCRYPTION_KEY_ALIAS, null) as SecretKey
    }
}
