package com.developermx.lockly

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.lockly.vault.KeystoreHelper
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom

object VaultManager {

    private const val VAULT_DIR = "vault"
    private const val PASSWORD_FILE = "_vlt_pwd.bin"
    private const val SALT_FILE = "_vlt_slt.bin"
    private const val TAG = "VaultManager"

    init {
        try {
            AeadConfig.register()
        } catch (e: GeneralSecurityException) {
            // Ignorar si ya está registrado
        }
    }

    // --- Métodos de Contraseña ---

    private fun hashPassword(password: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        val hashed = md.digest(password.toByteArray())
        return hashed.joinToString("") { "%02x".format(it) }
    }

    fun hasPassword(context: Context): Boolean {
        val file = File(context.filesDir, PASSWORD_FILE)
        val saltFile = File(context.filesDir, SALT_FILE)
        return file.exists() && saltFile.exists()
    }

    fun savePassword(context: Context, password: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        File(context.filesDir, SALT_FILE).writeBytes(salt)
        val hash = hashPassword(password, salt)
        File(context.filesDir, PASSWORD_FILE).writeText(hash)
    }

    fun validatePassword(context: Context, password: String): Boolean {
        val file = File(context.filesDir, PASSWORD_FILE)
        val saltFile = File(context.filesDir, SALT_FILE)
        if (!file.exists() || !saltFile.exists()) return false
        val salt = saltFile.readBytes()
        val hash = hashPassword(password, salt)
        return file.readText() == hash
    }


    // --- Métodos de Cifrado de Archivos ---

    private fun getAead(context: Context): Aead {
        return KeystoreHelper.getOrCreateMasterKey(context).getPrimitive(Aead::class.java)
    }

    fun decryptStream(context: Context, inputStream: InputStream, outputStream: OutputStream) {
        val aead = getAead(context)
        val cipherText = inputStream.readBytes()
        val decryptedText = aead.decrypt(cipherText, ByteArray(0))
        outputStream.write(decryptedText)
    }

    private fun getMimeType(filePath: String): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(filePath)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension?.lowercase())
    }

    private fun getFileContentUri(context: Context, filePath: String): Uri? {
        val mimeType = getMimeType(filePath)
        val queryUri: Uri = when {
            mimeType?.startsWith("image/") == true -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mimeType?.startsWith("video/") == true -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            mimeType?.startsWith("audio/") == true -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri("external") // Fallback
        }

        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = "${MediaStore.Files.FileColumns.DATA} = ?"
        val selectionArgs = arrayOf(filePath)

        context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                return ContentUris.withAppendedId(queryUri, id)
            }
        }
        return null
    }

    /**
     * Proceso completo para importar, cifrar y verificar un archivo en la bóveda.
     * Devuelve un par con el archivo cifrado y un IntentSender si se necesita permiso para eliminar.
     */
    fun importAndEncryptFile(context: Context, originalFileUri: Uri, originalFileName: String): Pair<File?, IntentSender?> {
        Log.d(TAG, "Iniciando proceso de cifrado para: $originalFileName")
        val vaultDir = File(context.filesDir, VAULT_DIR).apply { mkdirs() }
        val encryptedFile = File(vaultDir, "$originalFileName.enc")

        try {
            val plainText = context.contentResolver.openInputStream(originalFileUri)?.use { it.readBytes() }
                ?: run {
                    Log.e(TAG, "No se pudo abrir el InputStream para el archivo original.")
                    return Pair(null, null)
                }

            val aead = getAead(context)
            val cipherText = aead.encrypt(plainText, ByteArray(0))
            val decryptedText = aead.decrypt(cipherText, ByteArray(0))

            if (!plainText.contentEquals(decryptedText)) {
                Log.e(TAG, "Error de verificación: El archivo descifrado no coincide con el original.")
                return Pair(null, null)
            }

            encryptedFile.writeBytes(cipherText)
            Log.d(TAG, "Archivo cifrado y guardado exitosamente: ${encryptedFile.name}")

            // Solicitar la eliminación usando MediaStore.createDeleteRequest.
            originalFileUri.path?.let { filePath ->
                val contentUri = getFileContentUri(context, filePath)
                if (contentUri != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(contentUri))
                        return Pair(encryptedFile, pendingIntent.intentSender)
                    } else {
                        // Para versiones más antiguas, intentar la eliminación directa
                        try {
                            val rowsDeleted = context.contentResolver.delete(contentUri, null, null)
                            if (rowsDeleted == 0) Log.w(TAG, "No se pudo eliminar el archivo original.")
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Error de seguridad al eliminar en una versión antigua de Android.", e)
                        }
                    }
                } else {
                    Log.w(TAG, "No se pudo encontrar el Content URI para '${filePath}'.")
                }
            }

            return Pair(encryptedFile, null)

        } catch (e: Exception) {
            Log.e(TAG, "Error durante el proceso de cifrado y guardado.", e)
            if (encryptedFile.exists()) {
                encryptedFile.delete()
            }
            return Pair(null, null)
        }
    }
}
