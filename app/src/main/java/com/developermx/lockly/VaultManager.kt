package com.developermx.lockly

import android.content.Context
import java.io.File
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object VaultManager {
    private const val VAULT_FILE = "_vlt.bin"
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val PASSWORD_FILE = "_vlt_pwd.bin"
    private const val SALT_FILE = "_vlt_slt.bin"

    private fun getDerivedKey(context: Context, password: String): ByteArray {
        val saltFile = File(context.filesDir, SALT_FILE)
        val salt = if (saltFile.exists()) saltFile.readBytes() else ByteArray(16).also { SecureRandom().nextBytes(it) }
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 10000, 128)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun saveFile(context: Context, data: String, password: String) {
        val keyBytes = getDerivedKey(context, password)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(16) { 0 }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        val encrypted = cipher.doFinal(data.toByteArray())
        val file = File(context.filesDir, VAULT_FILE)
        file.writeBytes(encrypted)
    }

    fun readFile(context: Context, password: String): String? {
        val file = File(context.filesDir, VAULT_FILE)
        if (!file.exists()) return null
        val keyBytes = getDerivedKey(context, password)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(16) { 0 }
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
        val decrypted = cipher.doFinal(file.readBytes())
        return String(decrypted)
    }

    fun isVaultAccessible(context: Context): Boolean {
        val file = File(context.filesDir, VAULT_FILE)
        return file.exists()
    }

    fun hasPassword(context: Context): Boolean {
        val file = File(context.filesDir, PASSWORD_FILE)
        val saltFile = File(context.filesDir, SALT_FILE)
        return file.exists() && saltFile.exists()
    }

    fun savePassword(context: Context, password: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val saltFile = File(context.filesDir, SALT_FILE)
        saltFile.writeBytes(salt)
        val hash = hashPassword(password, salt)
        val file = File(context.filesDir, PASSWORD_FILE)
        file.writeText(hash)
    }

    fun validatePassword(context: Context, password: String): Boolean {
        val file = File(context.filesDir, PASSWORD_FILE)
        val saltFile = File(context.filesDir, SALT_FILE)
        if (!file.exists() || !saltFile.exists()) return false
        val salt = saltFile.readBytes()
        val hash = hashPassword(password, salt)
        return file.readText() == hash
    }

    private fun hashPassword(password: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        val hashed = md.digest(password.toByteArray())
        return hashed.joinToString("") { "%02x".format(it) }
    }
}
