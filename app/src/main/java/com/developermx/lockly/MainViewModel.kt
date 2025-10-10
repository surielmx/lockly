package com.developermx.lockly

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val vaultRoot = File(application.filesDir, "vault").apply { mkdirs() }
    val vaultRootPath: String get() = vaultRoot.absolutePath

    // --- Estados para el Explorador de Archivos Externos ---
    private val _unencryptedFiles = MutableStateFlow<List<File>>(emptyList())
    val unencryptedFiles = _unencryptedFiles.asStateFlow()
    private val _currentPath = MutableStateFlow(Environment.getExternalStorageDirectory())
    val currentPath = _currentPath.asStateFlow()

    // --- Estados para el Explorador de la Bóveda ---
    private val _vaultFiles = MutableStateFlow<List<File>>(emptyList())
    val vaultFiles = _vaultFiles.asStateFlow()
    private val _currentVaultPath = MutableStateFlow(vaultRoot)
    val currentVaultPath = _currentVaultPath.asStateFlow()

    // --- Estados de la UI y Tareas en Progreso ---
    private val _encryptingFiles = MutableStateFlow<Set<Uri>>(emptySet())
    val encryptingFiles = _encryptingFiles.asStateFlow()
    private val _creatingTempFile = MutableStateFlow<Set<String>>(emptySet())
    val creatingTempFile = _creatingTempFile.asStateFlow()
    private val _recentlyEncryptedFiles = MutableStateFlow<Set<String>>(emptySet())
    val recentlyEncryptedFiles = _recentlyEncryptedFiles.asStateFlow()
    private val _inUseFiles = MutableStateFlow<Set<String>>(emptySet())
    val inUseFiles = _inUseFiles.asStateFlow()
    var hasPermissions by mutableStateOf(false)

    // --- Diálogos ---
    private val _showDeleteConfirmationDialog = MutableStateFlow<List<File>>(emptyList())
    val showDeleteConfirmationDialog = _showDeleteConfirmationDialog.asStateFlow()

    // --- Canal para eventos de UI ---
    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()
    private val _openFileRequest = MutableSharedFlow<Intent>()
    val openFileRequest = _openFileRequest.asSharedFlow()

    private val TAG = "MainViewModel"
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Excepción no controlada en una corrutina", throwable)
        showSnackbarMessage("Ocurrió un error inesperado.")
    }

    init {
        if (hasPermissions) {
            loadUnencryptedFiles()
        }
        loadVaultFiles()
        loadInUseFiles()
    }

    fun showSnackbarMessage(message: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(message)
        }
    }

    // --- Lógica de Navegación y Filtrado ---

    private fun filterFile(file: File): Boolean {
        if (file.name.startsWith(".")) return false
        if (!file.absolutePath.startsWith(vaultRoot.absolutePath)) {
            val name = file.name.lowercase()
            if (file.isDirectory && (name == "android" || name.startsWith("com."))) {
                return false
            }
        }
        return true
    }

    fun getVisibleFileCount(directory: File): Int {
        if (!directory.isDirectory) return 0
        return directory.listFiles()?.count(::filterFile) ?: 0
    }

    fun loadUnencryptedFiles() {
        if (!hasPermissions) return
        viewModelScope.launch {
            _unencryptedFiles.value = _currentPath.value.listFiles()?.filter(::filterFile)?.sorted()?.toList() ?: emptyList()
        }
    }

    fun loadVaultFiles() {
        viewModelScope.launch {
            _vaultFiles.value = _currentVaultPath.value.listFiles()?.filter(::filterFile)?.sorted()?.toList() ?: emptyList()
        }
    }

    private fun loadInUseFiles() {
        viewModelScope.launch {
            val cacheDir = getApplication<Application>().cacheDir
            _inUseFiles.value = cacheDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        }
    }

    fun onFolderClick(folder: File) {
        _currentPath.value = folder
        loadUnencryptedFiles()
    }

    fun onVaultFolderClick(folder: File) {
        _currentVaultPath.value = folder
        loadVaultFiles()
    }

    fun onPathClick(path: String) {
        val newPath = File(path)
        if (newPath.exists() && newPath.isDirectory) {
            _currentPath.value = newPath
            loadUnencryptedFiles()
        }
    }

    fun onVaultPathClick(path: String) {
        val newPath = File(path)
        if (newPath.exists() && newPath.isDirectory) {
            _currentVaultPath.value = newPath
            loadVaultFiles()
        }
    }

    fun navigateBack(isVault: Boolean): Boolean {
        val current = if (isVault) _currentVaultPath.value else _currentPath.value
        val root = if (isVault) vaultRoot else Environment.getExternalStorageDirectory()
        val parent = current.parentFile

        return if (parent != null && parent.absolutePath.startsWith(root.absolutePath)) {
            if (isVault) {
                _currentVaultPath.value = parent
                loadVaultFiles()
            } else {
                _currentPath.value = parent
                loadUnencryptedFiles()
            }
            true
        } else {
            false
        }
    }

    // --- Lógica de Cifrado y Descifrado ---

    fun encryptFiles(originalFiles: List<File>) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val uris = originalFiles.map { it.toUri() }.toSet()
            _encryptingFiles.update { it + uris }

            val successfullyEncryptedOriginals = mutableListOf<File>()

            for (originalFile in originalFiles) {
                try {
                    val context = getApplication<Application>()
                    val encryptedFile = VaultManager.importAndEncryptFile(context, originalFile)
                    if (encryptedFile != null) {
                        successfullyEncryptedOriginals.add(originalFile)
                        _recentlyEncryptedFiles.update { it + encryptedFile.absolutePath }
                    } else {
                        showSnackbarMessage("Error al cifrar '${originalFile.name}'")
                    }
                } catch (e: Exception) {
                    showSnackbarMessage("Error al procesar '${originalFile.name}'")
                    Log.e(TAG, "Error durante el cifrado de ${originalFile.name}", e)
                }
            }

            _encryptingFiles.update { it - uris }
            loadVaultFiles()

            if (successfullyEncryptedOriginals.isNotEmpty()) {
                _showDeleteConfirmationDialog.value = successfullyEncryptedOriginals
            }
        }
    }

    fun deleteOriginalFiles(originalFiles: List<File>) {
        viewModelScope.launch(coroutineExceptionHandler) {
            var deletedCount = 0
            for (file in originalFiles) {
                if (file.exists() && file.delete()) {
                    deletedCount++
                }
            }
            showSnackbarMessage("$deletedCount de ${originalFiles.size} archivos originales eliminados.")
            loadUnencryptedFiles()
            _showDeleteConfirmationDialog.value = emptyList()
        }
    }

    fun decryptAndOpenFile(file: File) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _creatingTempFile.update { it + file.absolutePath }
            try {
                val context = getApplication<Application>()
                val tempFile = File(context.cacheDir, file.name.removeSuffix(".enc"))

                file.inputStream().use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        VaultManager.decryptStream(context, inputStream, outputStream)
                    }
                }
                _inUseFiles.update { it + tempFile.name }
                _recentlyEncryptedFiles.update { it - file.absolutePath }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(tempFile.extension)
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _openFileRequest.emit(openIntent)
            } finally {
                _creatingTempFile.update { it - file.absolutePath }
            }
        }
    }

    fun deleteTempFile(file: File) {
        viewModelScope.launch {
            val tempFile = File(getApplication<Application>().cacheDir, file.name.removeSuffix(".enc"))
            if (tempFile.exists()) {
                if (tempFile.delete()) {
                    _inUseFiles.update { it - tempFile.name }
                }
            }
        }
    }

    fun dismissDeleteConfirmationDialog() {
        _showDeleteConfirmationDialog.value = emptyList()
    }
}