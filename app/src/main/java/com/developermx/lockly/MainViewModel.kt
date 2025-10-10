package com.developermx.lockly

import android.app.Application
import android.content.Intent
import android.media.MediaScannerConnection
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineExceptionHandler

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val vaultRoot = File(application.filesDir, "vault").apply { mkdirs() }
    val vaultRootPath: String get() = vaultRoot.absolutePath

    // Directorio público para archivos temporales
    private val publicTempDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Lockly").apply { mkdirs() }

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

        // Ocultar la carpeta pública de Lockly en el explorador (ahora ignora mayúsculas/minúsculas)
        if (file.absolutePath.equals(publicTempDir.absolutePath, ignoreCase = true)) {
            return false
        }

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
            // Ahora busca en el directorio público
            _inUseFiles.value = publicTempDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
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
                val tempFile = File(publicTempDir, VaultManager.getOriginalFileName(file))

                file.inputStream().use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        VaultManager.decryptStream(context, inputStream, outputStream)
                    }
                }

                // Notificar al MediaStore para que el archivo sea visible en otras apps
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(tempFile.absolutePath),
                    arrayOf(MimeTypeMap.getSingleton().getMimeTypeFromExtension(tempFile.extension))
                ) { _, uri ->
                    Log.i(TAG, "MediaScanner completado para ${tempFile.name}. URI: $uri")
                }

                _inUseFiles.update { it + tempFile.name }
                _recentlyEncryptedFiles.update { it - file.absolutePath }

                showSnackbarMessage("Archivo guardado en Documentos/Lockly")

            } finally {
                _creatingTempFile.update { it - file.absolutePath }
            }
        }
    }

    fun shareFile(file: File) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _creatingTempFile.update { it + file.absolutePath }
            try {
                val context = getApplication<Application>()
                val tempFileUri = VaultManager.createTempFileForSharing(context, file)

                if (tempFileUri != null) {
                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        File(VaultManager.getOriginalFileName(file)).extension
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_STREAM, tempFileUri)
                        type = mimeType
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    _openFileRequest.emit(Intent.createChooser(shareIntent, "Compartir archivo"))
                } else {
                    showSnackbarMessage("No se pudo compartir el archivo.")
                }
            } finally {
                _creatingTempFile.update { it - file.absolutePath }
            }
        }
    }


    fun deleteTempFile(file: File) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val tempFile = File(publicTempDir, VaultManager.getOriginalFileName(file))
            if (tempFile.exists()) {
                if (tempFile.delete()) {
                    // Notificar al MediaStore que el archivo fue eliminado
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(tempFile.absolutePath),
                        null
                    ) { _, _ ->
                        Log.i(TAG, "MediaScanner completado para eliminación de ${tempFile.name}")
                    }
                    _inUseFiles.update { it - tempFile.name }
                     showSnackbarMessage("Copia temporal eliminada.")
                } else {
                    showSnackbarMessage("Error al eliminar la copia temporal.")
                }
            } else {
                Log.w(TAG, "Se intentó eliminar un archivo temporal que no existe: ${tempFile.absolutePath}")
            }
        }
    }

    fun dismissDeleteConfirmationDialog() {
        _showDeleteConfirmationDialog.value = emptyList()
    }
}
