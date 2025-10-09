package com.lockly

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.lockly.vault.VaultManager
import java.io.File
import java.io.FileOutputStream

class MainActivity : FragmentActivity() {
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VaultManager.init(this)
        setupBiometricPrompt()
        selectFileWithSAF()
    }

    private fun selectFileWithSAF() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        filePickerLauncher.launch(intent)
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            uri?.let { handleSelectedFile(it) }
        }
    }

    private fun handleSelectedFile(uri: Uri) {
        val fileName = getFileName(uri)
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val vaultFile = File(filesDir, "vault_${System.currentTimeMillis()}")
        val outputStream = FileOutputStream(vaultFile)
        val metadata = VaultManager.encryptFile(this, uri, outputStream, fileName, mimeType)
        // Aquí podrías guardar metadata en una base de datos o lista
    }

    private fun getFileName(uri: Uri): String {
        var name = "file"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun setupBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Aquí puedes permitir desencriptar el archivo
                }
            })
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear archivo")
            .setSubtitle("Autenticación biométrica requerida")
            .setNegativeButtonText("Usar contraseña")
            .build()
    }

    private fun decryptFileWithBiometric(uri: Uri, wrappedKey: ByteArray, outputFile: File) {
        biometricPrompt.authenticate(promptInfo)
        // En onAuthenticationSucceeded, llamar a VaultManager.decryptFile
    }
}
