package com.lockly.vault

import android.content.Context
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import java.io.File

object KeystoreHelper {
    private const val MASTER_KEY_ALIAS = "master_key_alias"
    private const val KEYSET_FILENAME = "master_keyset.json"

    fun getOrCreateMasterKey(context: Context): KeysetHandle {
        val keysetFile = File(context.filesDir, KEYSET_FILENAME)
        val kmsClient = AndroidKeystoreKmsClient()
        val aead = kmsClient.getAead("android-keystore://$MASTER_KEY_ALIAS")
        return if (keysetFile.exists()) {
            KeysetHandle.read(
                JsonKeysetReader.withFile(keysetFile),
                aead
            )
        } else {
            val handle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
            handle.write(
                JsonKeysetWriter.withFile(keysetFile),
                aead
            )
            handle
        }
    }
}
