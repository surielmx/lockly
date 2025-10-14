package com.developermx.lockly

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.lockly.vault.KeystoreHelper
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object VaultManager {

    private const val VAULT_DIR = "vault"
    private const val PASSWORD_FILE = "_vlt_pwd.bin"
    private const val SALT_FILE = "_vlt_slt.bin"
    private const val USER_ID_FILE = "_vlt_uid.bin"
    private const val TAG = "VaultManager"

    init {
        try {
            AeadConfig.register()
        } catch (e: GeneralSecurityException) {
            // Ignorar si ya está registrado
        }
    }

    // --- Métodos de Contraseña y UserID ---

    private fun hashPassword(password: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        val hashed = md.digest(password.toByteArray())
        return hashed.joinToString("") { "%02x".format(it) }
    }

    private fun generateUserId(password: String, salt: ByteArray): String {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, 65536, 256) // 256 bits for SHA-256
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec)
        // Convert to hex and take a substring to get 40-64 chars
        return key.encoded.joinToString("") { "%02x".format(it) }.substring(0, 50)
    }

    fun hasPassword(context: Context): Boolean {
        val file = File(context.filesDir, PASSWORD_FILE)
        val saltFile = File(context.filesDir, SALT_FILE)
        return file.exists() && saltFile.exists()
    }

    fun savePasswordAndUserId(context: Context, password: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        File(context.filesDir, SALT_FILE).writeBytes(salt)
        val hash = hashPassword(password, salt)
        File(context.filesDir, PASSWORD_FILE).writeText(hash)

        val userId = generateUserId(password, salt)
        File(context.filesDir, USER_ID_FILE).writeText(userId)
    }

    fun validatePassword(context: Context, password: String): Boolean {
        val file = File(context.filesDir, PASSWORD_FILE)
        val saltFile = File(context.filesDir, SALT_FILE)
        if (!file.exists() || !saltFile.exists()) return false
        val salt = saltFile.readBytes()
        val hash = hashPassword(password, salt)
        return file.readText() == hash
    }
    
    fun getUserId(context: Context): String? {
        val file = File(context.filesDir, USER_ID_FILE)
        return if (file.exists()) file.readText() else null
    }


    // --- Métodos de Cifrado de Archivos ---

    private fun getAead(context: Context): Aead {
        return KeystoreHelper.getOrCreateMasterKey(context).getPrimitive(Aead::class.java)
    }

    fun getOriginalFileName(encryptedFile: File): String {
        return encryptedFile.name.removeSuffix(".enc")
    }


    fun decryptStream(context: Context, inputStream: InputStream, outputStream: OutputStream) {
        val aead = getAead(context)
        val cipherText = inputStream.readBytes()
        val decryptedText = aead.decrypt(cipherText, ByteArray(0))
        outputStream.write(decryptedText)
    }

    fun createTempFileForSharing(context: Context, encryptedFile: File): Uri? {
        return try {
            val tempFile = File(context.cacheDir, getOriginalFileName(encryptedFile))
            encryptedFile.inputStream().use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    decryptStream(context, inputStream, outputStream)
                }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error creando archivo temporal para compartir", e)
            null
        }
    }


    fun importAndEncryptFile(context: Context, originalFile: File): File? {
        Log.d(TAG, "Iniciando proceso de cifrado para: ${originalFile.name}")

        val rootVaultDir = File(context.filesDir, VAULT_DIR)
        val rootStorageDir = Environment.getExternalStorageDirectory().absolutePath
        val originalFileParentPath = originalFile.parent

        val destinationDir = if (originalFileParentPath != null && originalFileParentPath.startsWith(rootStorageDir)) {
            val relativeParentPath = originalFileParentPath.removePrefix(rootStorageDir).removePrefix("/")
            File(rootVaultDir, relativeParentPath)
        } else {
            rootVaultDir
        }

        destinationDir.mkdirs() // Crea la estructura de carpetas si no existe

        val encryptedFile = File(destinationDir, "${originalFile.name}.enc")

        try {
            val plainText = context.contentResolver.openInputStream(originalFile.toUri())?.use { it.readBytes() }
                ?: run {
                    Log.e(TAG, "No se pudo abrir el InputStream para el archivo original.")
                    return null
                }

            val aead = getAead(context)
            val cipherText = aead.encrypt(plainText, ByteArray(0))

            encryptedFile.writeBytes(cipherText)
            Log.d(TAG, "Archivo cifrado y guardado exitosamente en: ${encryptedFile.path}")

            return encryptedFile

        } catch (e: Exception) {
            Log.e(TAG, "Error durante el proceso de cifrado y guardado.", e)
            if (encryptedFile.exists()) {
                encryptedFile.delete()
            }
            return null
        }
    }
}
