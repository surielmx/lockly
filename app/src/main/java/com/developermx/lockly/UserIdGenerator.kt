package com.developermx.lockly

import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object UserIdGenerator {

    private const val ITERATIONS = 100000
    private const val KEY_LENGTH = 256 // 256 bits for the key

    // This is a static, global salt (or "pepper"). It's the same for all users and all devices.
    // It should be a long, random string, unique to this application.
    // IMPORTANT: Changing this value will change all existing user IDs,
    // effectively disconnecting users from their cloud files.
    private const val GLOBAL_SALT = "d8f3b4c1e5a6f2d7c8b9a0d1e2f3g4h5i6j7k8l9m0n1o2p3q4r5s6t7u8v9w0x"

    /**
     * Generates a deterministic user ID based on a master password.
     * This ID is the same for the same password across all devices.
     */
    fun generate(password: String): String {
        val salt = GLOBAL_SALT.toByteArray()
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec)
        // Convert the 32-byte key to a 64-character hex string
        return key.encoded.joinToString("") { "%02x".format(it) }
    }
}
