
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
import androidx.lifecycle.Observer
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.developermx.lockly.workers.EncryptWorker
import com.developermx.lockly.workers.FileUploadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.os.Environment
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.FileOutputStream
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val vaultRoot = File(application.filesDir, "vault").apply { mkdirs() }
    val vaultRootPath: String get() = vaultRoot.absolutePath

    private val publicTempDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Lockly").apply { mkdirs() }

    // --- File & Vault States ---
    private val _unencryptedFiles = MutableStateFlow<List<File>>(emptyList())
    val unencryptedFiles = _unencryptedFiles.asStateFlow()
    private val _currentPath = MutableStateFlow(Environment.getExternalStorageDirectory())
    val currentPath = _currentPath.asStateFlow()
    private val _vaultFiles = MutableStateFlow<List<File>>(emptyList())
    val vaultFiles = _vaultFiles.asStateFlow()
    private val _currentVaultPath = MutableStateFlow(vaultRoot)
    val currentVaultPath = _currentVaultPath.asStateFlow()

    // --- UI & Progress States ---
    private val _creatingTempFile = MutableStateFlow<Set<String>>(emptySet())
    val creatingTempFile = _creatingTempFile.asStateFlow()
    private val _recentlyEncryptedFiles = MutableStateFlow<Set<String>>(emptySet())
    val recentlyEncryptedFiles = _recentlyEncryptedFiles.asStateFlow()
    private val _inUseFiles = MutableStateFlow<Set<String>>(emptySet())
    val inUseFiles = _inUseFiles.asStateFlow()
    private val _uploadedFiles = MutableStateFlow<Set<String>>(emptySet())
    val uploadedFiles = _uploadedFiles.asStateFlow()
    var hasPermissions by mutableStateOf(false)

    // --- Dialogs ---
    private val _showDeleteConfirmationDialog = MutableStateFlow<List<File>>(emptyList())
    val showDeleteConfirmationDialog = _showDeleteConfirmationDialog.asStateFlow()

    // --- UI Events Channel ---
    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()
    private val _openFileRequest = MutableSharedFlow<Intent>()
    val openFileRequest = _openFileRequest.asSharedFlow()

    private val TAG = "MainViewModel"
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught exception in coroutine", throwable)
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

    // --- Navigation & Filtering Logic ---

    private fun filterFile(file: File): Boolean {
        if (file.name.startsWith(".")) return false
        if (file.absolutePath.equals(publicTempDir.absolutePath, ignoreCase = true)) return false
        if (!file.absolutePath.startsWith(vaultRoot.absolutePath)) {
            val name = file.name.lowercase()
            if (file.isDirectory && (name == "android" || name.startsWith("com."))) return false
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
            _vaultFiles.value = _currentVaultPath.value.listFiles()?.sorted()?.toList() ?: emptyList()
        }
    }

    private fun loadInUseFiles() {
        viewModelScope.launch {
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

    // --- Encryption & Upload Chain ---

    fun encryptAndUploadFiles(originalFiles: List<File>) {
        if (originalFiles.isEmpty()) return

        val workManager = WorkManager.getInstance(getApplication())

        for (originalFile in originalFiles) {
            val encryptRequest = OneTimeWorkRequestBuilder<EncryptWorker>()
                .setInputData(workDataOf(EncryptWorker.KEY_FILE_PATH to originalFile.absolutePath))
                .build()

            val uploadRequest = OneTimeWorkRequestBuilder<FileUploadWorker>().build()

            workManager
                .beginWith(encryptRequest)
                .then(uploadRequest)
                .enqueue()

            Log.d(TAG, "Work chain enqueued for ${originalFile.name}. Final work ID: ${uploadRequest.id}")
            observeWorkChain(uploadRequest.id, originalFile)
        }
        showSnackbarMessage("Iniciando cifrado y subida para ${originalFiles.size} archivos...")
    }

    private fun observeWorkChain(workId: UUID, originalFile: File) {
        val workManager = WorkManager.getInstance(getApplication())
        workManager.getWorkInfoByIdLiveData(workId).observeForever(object : Observer<WorkInfo?> {
            override fun onChanged(value: WorkInfo?) {
                if (value == null) return

                when (value.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val encryptedFileName = value.outputData.getString(FileUploadWorker.KEY_OUTPUT_ENCRYPTED_FILE_NAME)
                        if (!encryptedFileName.isNullOrEmpty()) {
                            _uploadedFiles.update { it + encryptedFileName }
                            Log.i(TAG, "Chain succeeded for ${originalFile.name}. Encrypted file: $encryptedFileName")
                        }
                        loadVaultFiles()
                        // Add the original file to the list for the deletion confirmation dialog
                        _showDeleteConfirmationDialog.update { it + originalFile }
                        workManager.getWorkInfoByIdLiveData(workId).removeObserver(this)
                    }
                    WorkInfo.State.FAILED -> {
                        Log.e(TAG, "Work chain failed for ${originalFile.name}")
                        showSnackbarMessage("Falló el proceso para ${originalFile.name}")
                        workManager.getWorkInfoByIdLiveData(workId).removeObserver(this)
                    }
                    WorkInfo.State.CANCELLED -> {
                        Log.w(TAG, "Work chain was cancelled for ${originalFile.name}")
                        workManager.getWorkInfoByIdLiveData(workId).removeObserver(this)
                    }
                    else -> { /* ENQUEUED, RUNNING, BLOCKED */ }
                }
            }
        })
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

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(tempFile.absolutePath),
                    arrayOf(MimeTypeMap.getSingleton().getMimeTypeFromExtension(tempFile.extension))
                ) { _, uri ->
                    Log.i(TAG, "MediaScanner completed for ${tempFile.name}. URI: $uri")
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
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(tempFile.absolutePath),
                        null
                    ) { _, _ ->
                        Log.i(TAG, "MediaScanner completed for deletion of ${tempFile.name}")
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
