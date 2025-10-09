package com.lockly.vault

import android.content.Context
import android.net.Uri
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

object VaultManager {
    private lateinit var masterKeysetHandle: KeysetHandle
    private lateinit var aead: Aead

    fun init(context: Context) {
        AeadConfig.register()
        // Use AndroidKeysetManager for robust key management
        masterKeysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, "master_key_pref", "master_keyset")
            .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
            .withMasterKeyUri("android-keystore://master_key_alias")
            .build()
            .keysetHandle
        aead = masterKeysetHandle.getPrimitive(Aead::class.java)
    }

    fun encryptFile(context: Context, fileUri: Uri, outputStream: OutputStream, fileName: String, mimeType: String): EncryptedFileMetadata {
        val contentKeysetHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
        val contentAead = contentKeysetHandle.getPrimitive(Aead::class.java)
        val inputStream: InputStream? = context.contentResolver.openInputStream(fileUri)
        val fileBytes = inputStream?.readBytes() ?: throw Exception("Cannot read file")
        val encryptedBytes = contentAead.encrypt(fileBytes, ByteArray(0))
        outputStream.write(encryptedBytes)
        outputStream.close()
        inputStream?.close()
        val keysetBytes = ByteArrayOutputStream().apply {
            contentKeysetHandle.write(com.google.crypto.tink.BinaryKeysetWriter.withOutputStream(this), aead)
        }.toByteArray()
        val wrappedKey = keysetBytes
        return EncryptedFileMetadata(
            originalName = fileName,
            mimeType = mimeType,
            wrappedKey = wrappedKey
        )
    }

    fun decryptFile(context: Context, encryptedFileUri: Uri, wrappedKey: ByteArray, outputStream: OutputStream) {
        val contentKeysetHandle = KeysetHandle.read(
            com.google.crypto.tink.BinaryKeysetReader.withBytes(wrappedKey),
            aead
        )
        val contentAead = contentKeysetHandle.getPrimitive(Aead::class.java)
        val inputStream: InputStream? = context.contentResolver.openInputStream(encryptedFileUri)
        val encryptedBytes = inputStream?.readBytes() ?: throw Exception("Cannot read encrypted file")
        val decryptedBytes = contentAead.decrypt(encryptedBytes, ByteArray(0))
        outputStream.write(decryptedBytes)
        outputStream.close()
        inputStream?.close()
    }
}
