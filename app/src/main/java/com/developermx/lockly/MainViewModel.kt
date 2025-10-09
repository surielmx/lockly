package com.developermx.lockly

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.lockly.vault.EncryptedFileMetadata
import com.lockly.vault.VaultManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _encryptedFiles = MutableStateFlow<List<EncryptedFileMetadata>>(emptyList())
    val encryptedFiles = _encryptedFiles.asStateFlow()

    private val _unencryptedFiles = MutableStateFlow<List<File>>(emptyList())
    val unencryptedFiles = _unencryptedFiles.asStateFlow()

    private val _currentPath = MutableStateFlow(Environment.getExternalStorageDirectory())
    val currentPath = _currentPath.asStateFlow()

    private val _recomposeTrigger = MutableStateFlow(false)
    val recomposeTrigger = _recomposeTrigger.asStateFlow()

    private val _loadingFileId = MutableStateFlow<String?>(null)
    val loadingFileId = _loadingFileId.asStateFlow()

    var hasPermissions by mutableStateOf(false)

    private val excludedFolders = setOf(
        "alarms", "android", "audiobooks", "miui", "movies", "music",
        "notifications", "podcasts", "ringtones"
    )

    init {
        VaultManager.init(application)
        loadEncryptedFiles()
    }

    fun loadEncryptedFiles() {
        viewModelScope.launch {
            val files = getApplication<Application>().filesDir.listFiles()
                ?.filter { it.name.startsWith("metadata_") && it.name.endsWith(".json") }
                ?.map { Gson().fromJson(it.readText(), EncryptedFileMetadata::class.java) }
                ?: emptyList()
            _encryptedFiles.value = files
        }
    }

    fun loadUnencryptedFiles() {
        if (!hasPermissions) return
        viewModelScope.launch {
            val files = _currentPath.value.listFiles()?.filter { file ->
                if (!file.isDirectory) {
                    true // Siempre mostrar archivos
                } else {
                    val name = file.name.lowercase()
                    name !in excludedFolders && !name.startsWith("com.") && name != "lockly"
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


    fun encryptFile(file: File) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val metadata = EncryptedFileMetadata(
                    originalName = file.name,
                    mimeType = context.contentResolver.getType(Uri.fromFile(file)) ?: "application/octet-stream",
                    wrappedKey = ByteArray(0)
                )
                val outFile = File(context.filesDir, "encrypted_${metadata.uuid}")
                FileOutputStream(outFile).use { outputStream ->
                    val newMetadata = VaultManager.encryptFile(
                        context,
                        Uri.fromFile(file),
                        outputStream,
                        file.name,
                        context.contentResolver.getType(Uri.fromFile(file)) ?: "application/octet-stream"
                    )
                    val metadataWithUuid = newMetadata.copy(uuid = metadata.uuid)
                    val metadataFile = File(context.filesDir, "metadata_${metadataWithUuid.uuid}.json")
                    metadataFile.writeText(Gson().toJson(metadataWithUuid))
                    _encryptedFiles.value = _encryptedFiles.value + metadataWithUuid
                    file.delete()
                    loadUnencryptedFiles() // Recargar para reflejar la eliminación
                    _recomposeTrigger.value = !_recomposeTrigger.value
                }
            } catch (e: Exception) {
                android.util.Log.e("VaultTest", "Error al cifrar el archivo: ${e.message}", e)
            }
        }
    }

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
        val tempFile = File(tempDir, file.originalName)
        return tempFile.exists()
    }

    fun useTempFile(file: EncryptedFileMetadata) {
        viewModelScope.launch {
            _loadingFileId.value = file.uuid
            val context = getApplication<Application>()
            val tempDir = getTempDir()
            val encryptedFile = File(context.filesDir, "encrypted_${file.uuid}")
            val tempFile = File(tempDir, file.originalName)
            try {
                FileOutputStream(tempFile).use { outputStream ->
                    VaultManager.decryptFile(context, Uri.fromFile(encryptedFile), file.wrappedKey, outputStream)
                }
                _recomposeTrigger.value = !_recomposeTrigger.value
            } catch (e: Exception) {
                android.util.Log.e("VaultTest", "Error al descifrar y usar el archivo temporal: ${e.message}", e)
            } finally {
                _loadingFileId.value = null
            }
        }
    }

    fun deleteTempFile(file: EncryptedFileMetadata) {
        viewModelScope.launch {
            val tempDir = getTempDir()
            val tempFile = File(tempDir, file.originalName)
            if (tempFile.exists()) {
                try {
                    if (tempFile.delete()) {
                        _recomposeTrigger.value = !_recomposeTrigger.value
                    } else {
                        android.util.Log.e("VaultTest", "No se pudo eliminar el archivo temporal.")
                    }
                } catch (e: SecurityException) {
                    android.util.Log.e("VaultTest", "Error de seguridad al eliminar el archivo temporal: ${e.message}", e)
                }
            }
        }
    }
}