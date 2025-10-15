package com.developermx.lockly

import android.content.Context
import android.provider.Settings
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object UserIdGenerator {

    private const val ITERATIONS = 100000
    private const val KEY_LENGTH = 256 // 256 bits for the key

    /**
     * Generates a deterministic user ID based on a master password and a fixed device salt.
     * This ensures the same password on the same device always produces the same user ID.
     */
    fun generate(context: Context, password: String): String {
        val salt = getFixedSalt(context)
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec)
        // Convert the 32-byte key to a 64-character hex string
        return key.encoded.joinToString("") { "%02x".format(it) }
    }

    /**
     * Retrieves a fixed salt unique to the device.
     * Uses ANDROID_ID, which is constant for the lifetime of an app's installation
     * on a device (it changes on factory reset, but is stable otherwise).
     */
    private fun getFixedSalt(context: Context): ByteArray {
        // Using ANDROID_ID as a salt makes the User ID deterministic for a given device and user password.
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).toByteArray()
    }
}
