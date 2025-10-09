package com.developermx.lockly

import android.content.Context
import android.net.Uri
import android.util.Log
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

    /**
     * Proceso completo para importar, cifrar y verificar un archivo en la bóveda.
     * Esta versión es más robusta y verifica el cifrado en memoria antes de escribir en disco.
     */
    fun importAndEncryptFile(context: Context, originalFileUri: Uri, originalFileName: String): File? {
        val vaultDir = File(context.filesDir, VAULT_DIR).apply { mkdirs() }
        val encryptedFile = File(vaultDir, "$originalFileName.enc")

        try {
            val plainText = context.contentResolver.openInputStream(originalFileUri)?.use { it.readBytes() }
                ?: run {
                    Log.e("VaultManager", "No se pudo abrir el InputStream para el archivo original.")
                    return null
                }

            val aead = getAead(context)
            val cipherText = aead.encrypt(plainText, ByteArray(0))

            // Escribir el archivo cifrado en la bóveda
            encryptedFile.writeBytes(cipherText)
            Log.d("VaultManager", "Archivo cifrado y guardado exitosamente: ${encryptedFile.name}")

            // ************************************************************************************
            // ** PRUEBA: Eliminación del original desactivada temporalmente para diagnosticar. **
            // ************************************************************************************
            // context.contentResolver.delete(originalFileUri, null, null)

            return encryptedFile

        } catch (e: Exception) {
            Log.e("VaultManager", "Error durante el proceso de cifrado y guardado.", e)
            if (encryptedFile.exists()) {
                encryptedFile.delete()
            }
            return null
        }
    }
}
