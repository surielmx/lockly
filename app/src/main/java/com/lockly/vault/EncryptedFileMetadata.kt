package com.lockly.vault

import java.util.UUID

data class EncryptedFileMetadata(
    val uuid: String = UUID.randomUUID().toString(),
    val originalName: String,
    val mimeType: String,
    val wrappedKey: ByteArray
)
