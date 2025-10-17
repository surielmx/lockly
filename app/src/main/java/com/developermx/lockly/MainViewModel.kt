package com.developermx.lockly

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.media.MediaScannerConnection
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.io.File
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.Constraints
import androidx.work.NetworkType
import com.developermx.lockly.data.network.ApiClient
import com.developermx.lockly.data.network.CloudFile
import com.developermx.lockly.workers.EncryptWorker
import com.developermx.lockly.workers.FileUploadWorker
import com.developermx.lockly.workers.DownloadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.os.Environment
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.FileOutputStream
import java.util.UUID
import android.content.Context
import com.developermx.lockly.R

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

    // --- Cloud Sync States ---
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()
    private val _missingCloudFiles = MutableStateFlow<List<CloudFile>>(emptyList())
    val missingCloudFiles = _missingCloudFiles.asStateFlow()

    // --- UI & Progress States ---
    private val _creatingTempFile = MutableStateFlow<Set<String>>(emptySet())
    val creatingTempFile = _creatingTempFile.asStateFlow()
    private val _recentlyEncryptedFiles = MutableStateFlow<Set<String>>(emptySet())
    val recentlyEncryptedFiles = _recentlyEncryptedFiles.asStateFlow()
    private val _inUseFiles = MutableStateFlow<Set<String>>(emptySet())
    val inUseFiles = _inUseFiles.asStateFlow()
    private val _uploadedFiles = MutableStateFlow<Set<String>>(emptySet())
    val uploadedFiles = _uploadedFiles.asStateFlow()
    private val _encryptingFiles = MutableStateFlow<Set<String>>(emptySet())
    val encryptingFiles = _encryptingFiles.asStateFlow()
    var hasPermissions by mutableStateOf(false)

    // --- Dialogs ---
    private val _showDeleteConfirmationDialog = MutableStateFlow<List<File>>(emptyList())
    val showDeleteConfirmationDialog = _showDeleteConfirmationDialog.asStateFlow()

    private val _showDeleteTempDialog = MutableStateFlow<File?>(null)
    val showDeleteTempDialog = _showDeleteTempDialog.asStateFlow()

    private val _showDecryptMultipleDialog = MutableStateFlow<List<File>>(emptyList())
    val showDecryptMultipleDialog = _showDecryptMultipleDialog.asStateFlow()

    private val _showDeleteMultipleTempDialog = MutableStateFlow<List<File>>(emptyList())
    val showDeleteMultipleTempDialog = _showDeleteMultipleTempDialog.asStateFlow()

    // --- Progress States for Multiple Operations ---
    private val _isProcessingMultipleFiles = MutableStateFlow(false)
    val isProcessingMultipleFiles = _isProcessingMultipleFiles.asStateFlow()

    private val _multipleFilesProgress = MutableStateFlow<Pair<Int, Int>?>(null) // Pair(current, total)
    val multipleFilesProgress = _multipleFilesProgress.asStateFlow()

    // --- UI Events Channel ---
    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()
    private val _openFileRequest = MutableSharedFlow<Intent>()
    val openFileRequest = _openFileRequest.asSharedFlow()

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught exception in coroutine", throwable)
        showSnackbarMessage("Ocurrió un error inesperado.")
    }

    // --- Sync Preferences Helper Functions ---

    /**
     * Checks if the initial cloud sync should be performed.
     * Returns true if:
     * 1. Initial sync has never been completed, OR
     * 2. More than SYNC_CACHE_DURATION_MS has passed since last sync
     */
    private fun shouldRunCloudSync(): Boolean {
        val prefs = getApplication<Application>().getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        val initialSyncCompleted = prefs.getBoolean(KEY_INITIAL_SYNC_COMPLETED, false)

        // If initial sync never completed, we should sync
        if (!initialSyncCompleted) {
            Log.d(SYNC_TAG, "Initial sync not completed. Should sync: true")
            return true
        }

        // Check if cache has expired
        val lastSyncTimestamp = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastSync = currentTime - lastSyncTimestamp
        val shouldSync = timeSinceLastSync > SYNC_CACHE_DURATION_MS

        Log.d(SYNC_TAG, "Initial sync completed. Time since last sync: ${timeSinceLastSync / 1000}s. Should sync: $shouldSync")
        return shouldSync
    }

    /**
     * Marks the initial sync as completed and updates the last sync timestamp.
     */
    private fun markSyncCompleted() {
        val prefs = getApplication<Application>().getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(KEY_INITIAL_SYNC_COMPLETED, true)
            putLong(KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
        Log.d(SYNC_TAG, "Sync marked as completed")
    }

    init {
        if (hasPermissions) {
            loadUnencryptedFiles()
        }
        loadVaultFiles()
        loadInUseFiles()

        // Only sync cloud files if:
        // 1. Initial sync has never been completed, OR
        // 2. Cache has expired (> 1 hour since last sync)
        if (shouldRunCloudSync()) {
            syncCloudFiles()
        } else {
            Log.d(SYNC_TAG, "Skipping cloud sync - cache is still valid")
        }
    }

    private fun loadVaultFilesSync() {
        _vaultFiles.value = _currentVaultPath.value.listFiles()?.sorted()?.toList() ?: emptyList()
    }

    private fun syncCloudFiles() {
        viewModelScope.launch(coroutineExceptionHandler) {
            Log.d(SYNC_TAG, "Attempting to sync cloud files...")
            val userId = VaultManager.getUserId(getApplication())
            if (userId == null) {
                Log.w(SYNC_TAG, "Cannot sync cloud files, userId is null. Aborting.")
                return@launch
            }
            Log.d(SYNC_TAG, "Using userId: $userId")

            // Cargar archivos locales primero antes de sincronizar
            loadVaultFilesSync()

            val localFiles = _vaultFiles.value
            val hasLocalFiles = localFiles.isNotEmpty()

            _isSyncing.value = true
            try {
                val response = ApiClient.apiService.getCloudFiles(userId)
                Log.i(SYNC_TAG, "API request sent. HTTP Response Code: ${response.code()}")

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse != null && apiResponse.success) {
                        val cloudFiles = apiResponse.data?.filterNot { it.fileName.isNullOrEmpty() } ?: emptyList()

                        // SOLO mostrar el banner si NO hay archivos locales (primera vez en el dispositivo)
                        // Si ya tiene archivos locales, NO mostrar el banner aunque falten algunos
                        val missingFiles = if (hasLocalFiles) {
                            // Ya usó la app antes - NO mostrar banner
                            emptyList()
                        } else {
                            // Primera vez en este dispositivo - mostrar todos los archivos de la nube
                            cloudFiles
                        }

                        _missingCloudFiles.value = missingFiles

                        Log.i(SYNC_TAG, "Sync successful. Found ${cloudFiles.size} cloud files.")
                        Log.d(SYNC_TAG, "Has local files: $hasLocalFiles (${localFiles.size} files)")
                        if (hasLocalFiles) {
                            Log.d(SYNC_TAG, "User has already used the app - banner will NOT be shown")
                        } else {
                            Log.d(SYNC_TAG, "First time on this device - banner will show ${missingFiles.size} files")
                        }

                        // Mark sync as completed only if the API call was successful
                        markSyncCompleted()
                    } else {
                        Log.e(SYNC_TAG, "API call was successful but returned a business logic error: ${apiResponse?.error}")
                        showSnackbarMessage("Error del servidor al sincronizar: ${apiResponse?.error}")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(SYNC_TAG, "API call failed. Response body: $errorBody")
                    showSnackbarMessage("Error al sincronizar con la nube (Código: ${response.code()})")
                }
            } catch (e: Exception) {
                Log.e(SYNC_TAG, "A network or unexpected exception occurred during sync.", e)
                showSnackbarMessage("Error de conexión al sincronizar. Revisa la URL de la API y la conexión a internet.")
            } finally {
                _isSyncing.value = false
                Log.d(SYNC_TAG, "Sync process finished.")
            }
        }
    }

    /**
     * Downloads missing files from the cloud using WorkManager for persistent background execution.
     * Files are downloaded using individual Workers with network constraints.
     */
    fun downloadMissingFiles() {
        val filesToDownload = _missingCloudFiles.value
        if (filesToDownload.isEmpty()) {
            showSnackbarMessage("No hay archivos para descargar.")
            return
        }

        val userId = VaultManager.getUserId(getApplication())
        if (userId == null) {
            showSnackbarMessage("Error: ID de usuario no encontrado.")
            return
        }

        val workManager = WorkManager.getInstance(getApplication())
        val totalFiles = filesToDownload.size

        Log.d(TAG, "Iniciando descarga de $totalFiles archivos usando WorkManager")

        // Initialize download progress
        _isDownloading.value = true

        // Network constraint: require internet connection
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Track all work IDs for monitoring
        val workIds = mutableListOf<UUID>()

        // Enqueue download workers for each file
        filesToDownload.forEachIndexed { index, cloudFile ->
            val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    workDataOf(
                        DownloadWorker.KEY_USER_ID to userId,
                        DownloadWorker.KEY_FILE_NAME to cloudFile.fileName,
                        DownloadWorker.KEY_CURRENT_INDEX to (index + 1),
                        DownloadWorker.KEY_TOTAL_FILES to totalFiles
                    )
                )
                .setConstraints(constraints)
                .addTag(DOWNLOAD_WORK_TAG)
                .build()

            workManager.enqueue(downloadRequest)
            workIds.add(downloadRequest.id)

            Log.d(TAG, "Download work enqueued for ${cloudFile.fileName}. Work ID: ${downloadRequest.id}")
        }

        // Observe overall progress of all download workers
        observeDownloadProgress(workIds)

        showSnackbarMessage("Sincronizando $totalFiles archivos en segundo plano...")
    }

    /**
     * Observes the progress of all download workers and updates UI state accordingly.
     */
    private fun observeDownloadProgress(workIds: List<UUID>) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val workManager = WorkManager.getInstance(getApplication())
            val totalFiles = workIds.size
            var successCount = 0
            var failedCount = 0

            try {
                Log.d(TAG, "Starting to observe $totalFiles download workers")

                // Observe all workers concurrently using combine
                val observeJobs = workIds.map { workId ->
                    async {
                        try {
                            // Collect until we get a final state
                            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                                if (workInfo == null) return@collect

                                Log.d(TAG, "Work $workId state: ${workInfo.state}")

                                when (workInfo.state) {
                                    WorkInfo.State.SUCCEEDED -> {
                                        val fileName = workInfo.outputData.getString(DownloadWorker.KEY_DOWNLOADED_FILE_NAME)
                                        Log.i(TAG, "Download succeeded for: $fileName")
                                        throw StopCollectingException() // Stop observing this worker
                                    }
                                    WorkInfo.State.FAILED -> {
                                        Log.e(TAG, "Download failed for work ID: $workId")
                                        throw StopCollectingException() // Stop observing this worker
                                    }
                                    WorkInfo.State.CANCELLED -> {
                                        Log.w(TAG, "Download cancelled for work ID: $workId")
                                        throw StopCollectingException() // Stop observing this worker
                                    }
                                    else -> {
                                        // ENQUEUED, RUNNING, BLOCKED - continue observing
                                    }
                                }
                            }
                        } catch (_: StopCollectingException) {
                            // Expected - worker finished
                        } catch (e: Exception) {
                            Log.e(TAG, "Error observing download work $workId", e)
                        }
                    }
                }

                // Wait for all observations to complete
                observeJobs.awaitAll()

                Log.i(TAG, "All download workers finished. Counting results...")

                // After all workers finish, count final results
                workIds.forEach { workId ->
                    try {
                        val finalInfo = workManager.getWorkInfoById(workId).get()
                        if (finalInfo != null) {
                            when (finalInfo.state) {
                                WorkInfo.State.SUCCEEDED -> successCount++
                                WorkInfo.State.FAILED -> failedCount++
                                else -> {}
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting final work info for $workId", e)
                    }
                }

                Log.i(TAG, "Download process finished. Success: $successCount, Failed: $failedCount, Total: $totalFiles")

                // Show completion message
                if (successCount > 0) {
                    showSnackbarMessage("$successCount de $totalFiles archivos sincronizados.")
                    loadVaultFiles() // Refresh vault view
                    _missingCloudFiles.value = emptyList() // Clear the banner

                    // Collect downloaded file names and mark as synced
                    val downloadedFiles = mutableListOf<String>()
                    workIds.forEach { workId ->
                        try {
                            val finalInfo = workManager.getWorkInfoById(workId).get()
                            if (finalInfo?.state == WorkInfo.State.SUCCEEDED) {
                                val fileName = finalInfo.outputData.getString(DownloadWorker.KEY_DOWNLOADED_FILE_NAME)
                                if (!fileName.isNullOrEmpty()) {
                                    downloadedFiles.add(fileName)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting downloaded file name for $workId", e)
                        }
                    }

                    // Update cloud sync status for downloaded files
                    if (downloadedFiles.isNotEmpty()) {
                        _uploadedFiles.update { currentSet ->
                            currentSet + downloadedFiles.toSet()
                        }
                        Log.i(TAG, "Marcados ${downloadedFiles.size} archivos descargados como sincronizados")
                    }

                    // Show system notification for completion
                    val notificationManager = getApplication<Application>()
                        .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    val notification = NotificationCompat.Builder(getApplication(), "DownloadChannel")
                        .setContentTitle("Sincronización completada")
                        .setContentText("$successCount archivos sincronizados correctamente")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .build()

                    notificationManager.notify(System.currentTimeMillis().toInt(), notification)
                    Log.i(TAG, "Notificación final de sincronización mostrada")
                }

                if (failedCount > 0) {
                    showSnackbarMessage("$failedCount archivos no se pudieron descargar. Verifica tu conexión.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during download progress observation", e)
                showSnackbarMessage("Error al monitorear la sincronización.")
            } finally {
                _isDownloading.value = false
                Log.i(TAG, "Download observation completed. UI state updated.")
            }
        }
    }

    // Exception class to stop collecting flow
    private class StopCollectingException : Exception()

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
        val totalFiles = originalFiles.size

        // Agregar todos los archivos al estado de "cifrando"
        _encryptingFiles.update { current ->
            current + originalFiles.map { it.absolutePath }.toSet()
        }

        originalFiles.forEachIndexed { index, originalFile ->
            val currentIndex = index + 1

            val encryptRequest = OneTimeWorkRequestBuilder<EncryptWorker>()
                .setInputData(workDataOf(
                    EncryptWorker.KEY_FILE_PATH to originalFile.absolutePath,
                    EncryptWorker.KEY_CURRENT_INDEX to currentIndex,
                    EncryptWorker.KEY_TOTAL_FILES to totalFiles
                ))
                .build()

            val uploadRequest = OneTimeWorkRequestBuilder<FileUploadWorker>()
                .setInputData(workDataOf(
                    FileUploadWorker.KEY_CURRENT_INDEX to currentIndex,
                    FileUploadWorker.KEY_TOTAL_FILES to totalFiles
                ))
                .build()

            workManager
                .beginWith(encryptRequest)
                .then(uploadRequest)
                .enqueue()

            Log.d(TAG, "Work chain enqueued for ${originalFile.name} ($currentIndex/$totalFiles). Final work ID: ${uploadRequest.id}")
            observeWorkChainWithCoroutines(uploadRequest.id, originalFile)
        }
        showSnackbarMessage("Iniciando cifrado y subida para $totalFiles archivos...")
    }

    private fun observeWorkChainWithCoroutines(workId: UUID, originalFile: File) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val workManager = WorkManager.getInstance(getApplication())

            try {
                // Observar el estado del trabajo usando Flow en lugar de LiveData
                val workInfoFlow = workManager.getWorkInfoByIdFlow(workId)

                workInfoFlow.collect { workInfo ->
                    if (workInfo == null) return@collect

                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            val encryptedFileName = workInfo.outputData.getString(FileUploadWorker.KEY_OUTPUT_ENCRYPTED_FILE_NAME)
                            if (!encryptedFileName.isNullOrEmpty()) {
                                _uploadedFiles.update { it + encryptedFileName }
                                Log.i(TAG, "Chain succeeded for ${originalFile.name}. Encrypted file: $encryptedFileName")
                            }
                            loadVaultFiles()
                            // Add the original file to the list for the deletion confirmation dialog
                            _showDeleteConfirmationDialog.update { it + originalFile }
                            // Remover del estado de "cifrando"
                            _encryptingFiles.update { it - originalFile.absolutePath }
                            // Terminar la recolección cuando el trabajo finaliza
                            return@collect
                        }
                        WorkInfo.State.FAILED -> {
                            Log.e(TAG, "Work chain failed for ${originalFile.name}")
                            showSnackbarMessage("Falló el proceso para ${originalFile.name}")
                            // Remover del estado de "cifrando"
                            _encryptingFiles.update { it - originalFile.absolutePath }
                            return@collect
                        }
                        WorkInfo.State.CANCELLED -> {
                            Log.w(TAG, "Work chain was cancelled for ${originalFile.name}")
                            // Remover del estado de "cifrando"
                            _encryptingFiles.update { it - originalFile.absolutePath }
                            return@collect
                        }
                        else -> { /* ENQUEUED, RUNNING, BLOCKED - continuar observando */ }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing work chain for ${originalFile.name}", e)
                showSnackbarMessage("Error al monitorear el proceso para ${originalFile.name}")
                // Remover del estado de "cifrando" en caso de error
                _encryptingFiles.update { it - originalFile.absolutePath }
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
                val password = SessionManager.getPassword()

                if (password.isNullOrBlank()) {
                    showSnackbarMessage("Error: Sesión expirada. Por favor reinicia la app.")
                    return@launch
                }

                val tempFile = File(publicTempDir, VaultManager.getOriginalFileName(file))

                Log.d(TAG, "Descifrando archivo: ${file.name} -> ${tempFile.absolutePath}")

                // Descifrar el archivo usando la contraseña
                file.inputStream().use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        VaultManager.decryptStream(password, inputStream, outputStream)
                    }
                }

                Log.d(TAG, "Archivo descifrado exitosamente. Tamaño: ${tempFile.length()} bytes")

                // Escanear el archivo con MediaScanner
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

            } catch (e: Exception) {
                Log.e(TAG, "Error al descifrar archivo: ${file.name}", e)
                showSnackbarMessage("Error al descifrar el archivo: ${e.message}")
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
                val password = SessionManager.getPassword()

                if (password == null) {
                    showSnackbarMessage("Error: Sesión expirada. Por favor reinicia la app.")
                    return@launch
                }

                val tempFileUri = VaultManager.createTempFileForSharing(context, file, password)

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
            }
        }
    }

    fun dismissDeleteConfirmationDialog() {
        if (_showDeleteConfirmationDialog.value.isNotEmpty()) {
            showSnackbarMessage("Los archivos originales no se eliminaron y permanecen en el dispositivo.")
        }
        _showDeleteConfirmationDialog.value = emptyList()
    }

    fun onVaultFileClick(file: File) {
        val fileName = VaultManager.getOriginalFileName(file)
        if (_inUseFiles.value.contains(fileName)) {
            // Si el archivo ya está en uso, mostrar diálogo de eliminación
            _showDeleteTempDialog.value = file
        } else {
            // Si no está en uso, descifrarlo y abrirlo
            decryptAndOpenFile(file)
        }
    }

    fun dismissDeleteTempDialog() {
        _showDeleteTempDialog.value = null
    }

    fun confirmDeleteTempFile() {
        val file = _showDeleteTempDialog.value
        if (file != null) {
            deleteTempFile(file)
            _showDeleteTempDialog.value = null
        }
    }

    // --- Multiple File Decryption (HU-017) ---

    fun showDecryptMultipleDialog(files: List<File>) {
        Log.d(TAG, "showDecryptMultipleDialog called with ${files.size} files")
        _showDecryptMultipleDialog.value = files
    }

    fun dismissDecryptMultipleDialog() {
        Log.d(TAG, "dismissDecryptMultipleDialog called")
        _showDecryptMultipleDialog.value = emptyList()
    }

    fun decryptMultipleFiles(files: List<File>) {
        if (files.isEmpty()) return

        Log.d(TAG, "decryptMultipleFiles called with ${files.size} files")

        viewModelScope.launch(coroutineExceptionHandler) {
            val context = getApplication<Application>()
            val password = SessionManager.getPassword()

            Log.d(TAG, "Password from SessionManager: ${if (password != null) "NOT NULL (${password.length} chars)" else "NULL"}")

            if (password.isNullOrBlank()) {
                Log.e(TAG, "Password is null or blank - session expired!")
                showSnackbarMessage("Error: Sesión expirada. Por favor reinicia la app.")
                return@launch
            }

            // Iniciar indicador de progreso
            val totalFiles = files.size
            _isProcessingMultipleFiles.value = true
            _multipleFilesProgress.value = Pair(0, totalFiles) // Inicializar en 0
            Log.d(TAG, "isProcessingMultipleFiles set to TRUE, progress initialized: 0/$totalFiles")

            // Pequeño delay para permitir que la UI se actualice
            kotlinx.coroutines.delay(200)

            Log.d(TAG, "Starting decryption of $totalFiles files")
            var successCount = 0
            var failedCount = 0

            files.forEachIndexed { index, file ->
                // Actualizar progreso
                val currentProgress = Pair(index + 1, totalFiles)
                _multipleFilesProgress.value = currentProgress
                Log.d(TAG, "Progress updated: ${currentProgress.first}/${currentProgress.second}")

                _creatingTempFile.update { it + file.absolutePath }
                try {
                    // Ejecutar operación de I/O en dispatcher IO
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val tempFile = File(publicTempDir, VaultManager.getOriginalFileName(file))

                        Log.d(TAG, "Descifrando archivo: ${file.name} -> ${tempFile.absolutePath}")

                        // Descifrar el archivo usando la contraseña
                        file.inputStream().use { inputStream ->
                            FileOutputStream(tempFile).use { outputStream ->
                                VaultManager.decryptStream(password, inputStream, outputStream)
                            }
                        }

                        Log.d(TAG, "Archivo descifrado exitosamente. Tamaño: ${tempFile.length()} bytes")
                    }

                    // Escanear el archivo con MediaScanner (en el Main thread)
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(File(publicTempDir, VaultManager.getOriginalFileName(file)).absolutePath),
                        arrayOf(MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                            VaultManager.getOriginalFileName(file).substringAfterLast('.')
                        ))
                    ) { _, uri ->
                        Log.i(TAG, "MediaScanner completed for ${VaultManager.getOriginalFileName(file)}. URI: $uri")
                    }

                    _inUseFiles.update { it + VaultManager.getOriginalFileName(file) }
                    _recentlyEncryptedFiles.update { it - file.absolutePath }
                    successCount++

                } catch (e: Exception) {
                    Log.e(TAG, "Error al descifrar archivo: ${file.name}", e)
                    failedCount++
                } finally {
                    _creatingTempFile.update { it - file.absolutePath }
                }
            }

            // Limpiar estado de progreso
            Log.d(TAG, "Cleaning up progress state")
            _isProcessingMultipleFiles.value = false
            _multipleFilesProgress.value = null
            Log.d(TAG, "isProcessingMultipleFiles set to FALSE")

            // Mostrar mensaje de resultado
            if (successCount > 0 && failedCount == 0) {
                showSnackbarMessage("$successCount archivos guardados en Documentos/Lockly")
            } else if (successCount > 0 && failedCount > 0) {
                showSnackbarMessage("$successCount archivos descifrados, $failedCount fallidos")
            } else {
                showSnackbarMessage("Error: No se pudo descifrar ningún archivo")
            }

            Log.d(TAG, "Decryption completed. Success: $successCount, Failed: $failedCount")

            // Cerrar el diálogo
            _showDecryptMultipleDialog.value = emptyList()
        }
    }

    // --- Multiple Temp File Deletion (HU-018) ---

    fun showDeleteMultipleTempDialog(files: List<File>) {
        Log.d(TAG, "showDeleteMultipleTempDialog called with ${files.size} files")
        _showDeleteMultipleTempDialog.value = files
    }

    fun dismissDeleteMultipleTempDialog() {
        Log.d(TAG, "dismissDeleteMultipleTempDialog called")
        _showDeleteMultipleTempDialog.value = emptyList()
    }

    fun confirmDeleteMultipleTempFiles() {
        val files = _showDeleteMultipleTempDialog.value
        if (files.isNotEmpty()) {
            viewModelScope.launch(coroutineExceptionHandler) {
                // Iniciar indicador de progreso
                val totalFiles = files.size
                _isProcessingMultipleFiles.value = true
                _multipleFilesProgress.value = Pair(0, totalFiles) // Inicializar en 0
                Log.d(TAG, "isProcessingMultipleFiles set to TRUE for deletion, progress initialized: 0/$totalFiles")

                // Pequeño delay para permitir que la UI se actualice
                kotlinx.coroutines.delay(200)

                var deletedCount = 0

                files.forEachIndexed { index, file ->
                    // Actualizar progreso
                    _multipleFilesProgress.value = Pair(index + 1, totalFiles)
                    Log.d(TAG, "Delete progress updated: ${index + 1}/$totalFiles")

                    try {
                        // Ejecutar operación de I/O en dispatcher IO
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val tempFile = File(publicTempDir, VaultManager.getOriginalFileName(file))
                            if (tempFile.exists() && tempFile.delete()) {
                                deletedCount++
                                _inUseFiles.update { it - tempFile.name }
                                Log.d(TAG, "Archivo temporal eliminado: ${tempFile.name}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al eliminar el archivo temporal: ${file.name}", e)
                    }
                }

                // Limpiar estado de progreso
                _isProcessingMultipleFiles.value = false
                _multipleFilesProgress.value = null

                showSnackbarMessage("$deletedCount de $totalFiles archivos temporales eliminados.")
                // Cerrar el diálogo después de completar la eliminación
                _showDeleteMultipleTempDialog.value = emptyList()
            }
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val SYNC_TAG = "LocklySync"
        private const val DOWNLOAD_WORK_TAG = "download_work"

        // Sync Preferences Constants
        private const val SYNC_PREFS_NAME = "lockly_sync_prefs"
        private const val KEY_INITIAL_SYNC_COMPLETED = "initial_sync_completed"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val SYNC_CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour
    }
}
