package com.renpytool.keystore

/**
 * Represents the user's choice for APK signing
 */
sealed class SigningOption {
    /**
     * Use a randomly generated key (one-time use, cannot update APK later)
     */
    object RandomKey : SigningOption()

    /**
     * Create a new persistent keystore with the given name and password
     */
    data class CreateNew(val keystoreName: String, val password: String) : SigningOption()

    /**
     * Use an existing keystore
     */
    data class UseExisting(val keystoreInfo: KeystoreInfo) : SigningOption()
}
