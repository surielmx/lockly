package com.developermx.lockly

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.subtle.AesGcmJce
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object VaultManager {

    private const val VAULT_DIR = "vault"
    private const val PASSWORD_FILE = "_vlt_pwd.bin"
    private const val TAG = "VaultManager"
    private const val PREFS_FILE = "secure_vault_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_PASSWORD_HASH = "password_hash"

    private fun getEncryptedPrefs(context: Context): EncryptedSharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }

    fun getUserId(context: Context): String? {
        return getEncryptedPrefs(context).getString(KEY_USER_ID, null)
    }

    private fun hashPassword(password: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        val hashed = md.digest(password.toByteArray())
        return hashed.joinToString("") { "%02x".format(it) }
    }

    fun hasPassword(context: Context): Boolean {
        return getEncryptedPrefs(context).contains(KEY_PASSWORD_HASH)
    }

    fun savePasswordAndUserId(context: Context, password: String) {
        val userId = UserIdGenerator.generate(password)
        val passwordHash = hashPassword(password, userId.toByteArray()) // Use userId as salt for the hash

        getEncryptedPrefs(context).edit {
            putString(KEY_USER_ID, userId)
            putString(KEY_PASSWORD_HASH, passwordHash)
        }
    }

    fun validatePassword(context: Context, password: String): Boolean {
        val prefs = getEncryptedPrefs(context)
        val userId = prefs.getString(KEY_USER_ID, null) ?: return false
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false

        val passwordHash = hashPassword(password, userId.toByteArray())
        return storedHash == passwordHash
    }

    fun ensureUserIdExists(context: Context, password: String) {
        val prefs = getEncryptedPrefs(context)
        if (prefs.contains(KEY_USER_ID)) {
            return // All good
        }

        Log.w(TAG, "User ID is missing from EncryptedSharedPreferences. Attempting to repair.")
        try {
            val userId = UserIdGenerator.generate(password)
            prefs.edit {
                putString(KEY_USER_ID, userId)
            }
            Log.i(TAG, "User ID successfully repaired and saved to prefs.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to repair User ID.", e)
        }
    }

    // --- Métodos de Cifrado de Archivos ---

    init {
        // Inicializar Tink
        AeadConfig.register()
    }

    /**
     * Deriva una clave de cifrado desde la contraseña maestra usando PBKDF2.
     * Esta clave es la misma en todos los dispositivos con la misma contraseña.
     */
    private fun deriveKeyFromPassword(password: String): ByteArray {
        if (password.isBlank()) {
            throw IllegalArgumentException("Password for key derivation must not be empty or blank.")
        }
        val salt = GLOBAL_SALT.toByteArray()
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Obtiene un AEAD (Authenticated Encryption with Associated Data) desde la contraseña.
     * Usa AES-GCM de 256 bits derivado desde la contraseña maestra.
     */
    private fun getAead(password: String): Aead {
        val keyBytes = deriveKeyFromPassword(password)
        return AesGcmJce(keyBytes)
    }

    // Constantes para derivación de clave (las mismas que UserIdGenerator para consistencia)
    private const val ITERATIONS = 100000
    private const val KEY_LENGTH = 256
    private const val GLOBAL_SALT = "d8f3b4c1e5a6f2d7c8b9a0d1e2f3g4h5i6j7k8l9m0n1o2p3q4r5s6t7u8v9w0x"

    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        var fileName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    fileName = cursor.getString(displayNameIndex)
                }
            }
        }
        return fileName ?: "${UUID.randomUUID()}"
    }

    fun encryptFileToTemp(context: Context, fileUri: Uri, password: String): File? {
        return try {
            val originalFileName = getFileNameFromUri(context, fileUri)
            val tempEncryptedFile = File(context.cacheDir, "$originalFileName.enc")

            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                val plainText = inputStream.readBytes()
                val aead = getAead(password)
                val cipherText = aead.encrypt(plainText, ByteArray(0))
                tempEncryptedFile.writeBytes(cipherText)
            } ?: return null // Devuelve null si no se puede abrir el stream

            tempEncryptedFile
        } catch (e: Exception) {
            Log.e(TAG, "Error al cifrar el archivo en un directorio temporal", e)
            null
        }
    }

    fun getOriginalFileName(encryptedFile: File): String {
        return encryptedFile.name.removeSuffix(".enc")
    }


    fun decryptStream(password: String, inputStream: InputStream, outputStream: OutputStream) {
        val aead = getAead(password)
        val cipherText = inputStream.readBytes()
        val decryptedText = aead.decrypt(cipherText, ByteArray(0))
        outputStream.write(decryptedText)
    }

    fun createTempFileForSharing(context: Context, encryptedFile: File, password: String): Uri? {
        return try {
            val tempFile = File(context.cacheDir, getOriginalFileName(encryptedFile))
            encryptedFile.inputStream().use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    decryptStream(password, inputStream, outputStream)
                }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error creando archivo temporal para compartir", e)
            null
        }
    }


    fun importAndEncryptFile(context: Context, originalFile: File, password: String): File? {
        Log.d(TAG, "Iniciando proceso de cifrado para: ${originalFile.name}")

        if (password.isBlank()) {
            Log.e(TAG, "La contraseña para el cifrado está vacía.")
            return null
        }

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

            val aead = getAead(password)
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
