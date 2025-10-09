package com.developermx.lockly

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lockly.vault.EncryptedFileMetadata
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {
    // --- Estados para Archivos y UI ---
    private val _encryptedFiles = MutableStateFlow<List<EncryptedFileMetadata>>(emptyList())
    val encryptedFiles = _encryptedFiles.asStateFlow()

    private val _unencryptedFiles = MutableStateFlow<List<File>>(emptyList())
    val unencryptedFiles = _unencryptedFiles.asStateFlow()

    private val _currentPath = MutableStateFlow(Environment.getExternalStorageDirectory())
    val currentPath = _currentPath.asStateFlow()

    private val _encryptingFiles = MutableStateFlow<Set<Uri>>(emptySet())
    val encryptingFiles = _encryptingFiles.asStateFlow()

    private val _creatingTempFile = MutableStateFlow<Set<String>>(emptySet())
    val creatingTempFile = _creatingTempFile.asStateFlow()

    var hasPermissions by mutableStateOf(false)

    // --- Canal para eventos de UI (Snackbar y Permisos) ---
    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private val _permissionRequest = MutableSharedFlow<IntentSender>()
    val permissionRequest = _permissionRequest.asSharedFlow()

    private val excludedFolders = setOf(
        "alarms", "android", "audiobooks", "miui", "movies", "music",
        "notifications", "podcasts", "ringtones"
    )

    private val TAG = "MainViewModel"

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Excepción no controlada en una corrutina", throwable)
    }

    init {
        loadEncryptedFiles()
    }

    // --- Lógica de Carga y Navegación ---
    fun loadEncryptedFiles() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val vaultDir = File(context.filesDir, "vault")
            if (!vaultDir.exists()) vaultDir.mkdirs()

            _encryptedFiles.value = vaultDir.listFiles()
                ?.filter { it.name.endsWith(".enc") }
                ?.map {
                    EncryptedFileMetadata(
                        uuid = it.nameWithoutExtension,
                        originalName = it.name,
                        mimeType = "application/octet-stream",
                        wrappedKey = ByteArray(0)
                    )
                } ?: emptyList()
        }
    }

    fun loadUnencryptedFiles() {
        if (!hasPermissions) return
        viewModelScope.launch {
            val files = _currentPath.value.listFiles()?.filter { file ->
                if (!file.isDirectory) {
                    true
                } else {
                    val name = file.name.lowercase()
                    !name.startsWith(".") && name !in excludedFolders && !name.startsWith("com.") && name != "lockly"
                }
            }?.toList() ?: emptyList()
            _unencryptedFiles.value = files
        }
    }

    fun onFolderClick(folder: File) {
        if (folder.isDirectory) {
            _currentPath.value = folder
            loadUnencryptedFiles()
        }
    }

    fun navigateBack(): Boolean {
        val parent = _currentPath.value.parentFile
        val root = Environment.getExternalStorageDirectory()
        return if (parent != null && parent.canRead() && parent.path != root.parent) {
            _currentPath.value = parent
            loadUnencryptedFiles()
            true
        } else {
            false
        }
    }

    // --- Lógica de Cifrado con Feedback Mejorado ---
    fun encryptFile(fileUri: Uri, fileName: String) {
        Log.d(TAG, "encryptFile llamado con URI: $fileUri y nombre: $fileName")
        viewModelScope.launch(coroutineExceptionHandler) {
            _encryptingFiles.update { it + fileUri }
            var success = false
            try {
                val context = getApplication<Application>()
                val (encryptedFile, intentSender) = VaultManager.importAndEncryptFile(context, fileUri, fileName)

                if (encryptedFile != null) {
                    success = true
                    delay(1000)
                    if (intentSender != null) {
                        _permissionRequest.emit(intentSender)
                    } else {
                        // Eliminación exitosa o no necesaria
                        loadUnencryptedFiles()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción atrapada en la corrutina de cifrado", e)
            }
            finally {
                _encryptingFiles.update { it - fileUri }
            }

            if (success) {
                _snackbarMessage.emit("'$fileName' cifrado con éxito.")
                loadEncryptedFiles()
            } else {
                _snackbarMessage.emit("Error al cifrar '$fileName'")
            }
        }
    }

    // --- Lógica de Archivos Temporales ---
    private fun getTempDir(): File {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val tempDir = File(publicDir, "lockly")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        return tempDir
    }

    fun isFileInUse(file: EncryptedFileMetadata): Boolean {
        val tempDir = getTempDir()
        val tempFile = File(tempDir, file.originalName.removeSuffix(".enc"))
        return tempFile.exists()
    }

    fun useTempFile(file: EncryptedFileMetadata) {
        viewModelScope.launch {
            _creatingTempFile.update { it + file.uuid }
            try {
                val context = getApplication<Application>()
                val vaultDir = File(context.filesDir, "vault")
                val encryptedFile = File(vaultDir, file.originalName)
                val tempDir = getTempDir()
                val tempFile = File(tempDir, file.originalName.removeSuffix(".enc"))

                encryptedFile.inputStream().use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        VaultManager.decryptStream(context, inputStream, outputStream)
                    }
                }
                loadEncryptedFiles()
            } finally {
                _creatingTempFile.update { it - file.uuid }
            }
        }
    }

    fun deleteTempFile(file: EncryptedFileMetadata) {
        viewModelScope.launch {
            val tempDir = getTempDir()
            val tempFile = File(tempDir, file.originalName.removeSuffix(".enc"))
            if (tempFile.exists() && tempFile.delete()) {
                loadEncryptedFiles()
            }
        }
    }
}
