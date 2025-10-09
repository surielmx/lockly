package com.developermx.lockly

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.developermx.lockly.ui.theme.LocklyTheme
import com.developermx.lockly.ui.theme.Yellow80
import com.google.gson.Gson
import com.lockly.vault.EncryptedFileMetadata
import com.lockly.vault.VaultManager
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    private val encryptedFiles = mutableStateListOf<EncryptedFileMetadata>()
    private var showUseDialog by mutableStateOf(false)
    private var showDeleteDialog by mutableStateOf(false)
    private var showEncryptDialog by mutableStateOf(false)
    private var fileToUse: EncryptedFileMetadata? = null
    private var fileToDelete: EncryptedFileMetadata? = null
    private var fileToEncrypt: File? = null
    private var recomposeTrigger by mutableStateOf(false)
    private val unencryptedFiles = mutableStateListOf<File>()
    private var hasPermissions by mutableStateOf(false)

    private val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allPermissionsGranted = permissions.entries.all { it.value }
        if (allPermissionsGranted) {
            hasPermissions = true
            loadUnencryptedFiles()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        VaultManager.init(this)

        encryptedFiles.clear()
        filesDir.listFiles()?.filter { it.name.startsWith("metadata_") && it.name.endsWith(".json") }?.forEach { file ->
            val metadata = Gson().fromJson(file.readText(), EncryptedFileMetadata::class.java)
            encryptedFiles.add(metadata)
        }

        setContent {
            LocklyTheme {
                if (hasPermissions) {
                    MainScreen()
                } else {
                    PermissionRequestScreen {
                        requestPermissionLauncher.launch(permissionsToRequest)
                    }
                }
            }
        }

        if (checkPermissions()) {
            hasPermissions = true
            loadUnencryptedFiles()
        }
    }

    private fun checkPermissions(): Boolean {
        return permissionsToRequest.all {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun loadUnencryptedFiles() {
        unencryptedFiles.clear()
        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        documents.listFiles()?.let { unencryptedFiles.addAll(it) }
        pictures.listFiles()?.let { unencryptedFiles.addAll(it) }
        dcim.listFiles()?.let { unencryptedFiles.addAll(it) }
    }

    private fun encryptFile(file: File) {
        try {
            VaultManager.init(this)
            val metadata = EncryptedFileMetadata(originalName = file.name, mimeType = contentResolver.getType(Uri.fromFile(file)) ?: "application/octet-stream", wrappedKey = ByteArray(0))
            val outFile = File(filesDir, "encrypted_${metadata.uuid}")
            FileOutputStream(outFile).use { outputStream ->
                val newMetadata = VaultManager.encryptFile(this, Uri.fromFile(file), outputStream, file.name, contentResolver.getType(Uri.fromFile(file)) ?: "application/octet-stream")
                val metadataWithUuid = newMetadata.copy(uuid = metadata.uuid)
                val metadataFile = File(filesDir, "metadata_${metadataWithUuid.uuid}.json")
                metadataFile.writeText(Gson().toJson(metadataWithUuid))
                encryptedFiles.add(metadataWithUuid)
                file.delete()
                unencryptedFiles.remove(file)
                recomposeTrigger = !recomposeTrigger
            }
        } catch (e: Exception) {
            android.util.Log.e("VaultTest", "Error al cifrar el archivo: ${e.message}", e)
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

    private fun isFileInUse(file: EncryptedFileMetadata): Boolean {
        val tempDir = getTempDir()
        val tempFile = File(tempDir, file.originalName)
        return tempFile.exists()
    }

    private fun useTempFile(file: EncryptedFileMetadata) {
        VaultManager.init(this)
        val tempDir = getTempDir()
        val encryptedFile = File(filesDir, "encrypted_${file.uuid}")
        val tempFile = File(tempDir, file.originalName)
        try {
            FileOutputStream(tempFile).use { outputStream ->
                VaultManager.decryptFile(this, Uri.fromFile(encryptedFile), file.wrappedKey, outputStream)
            }
            recomposeTrigger = !recomposeTrigger
        } catch (e: Exception) {
            android.util.Log.e("VaultTest", "Error al descifrar y usar el archivo temporal: ${e.message}", e)
        }
    }

    private fun deleteTempFile(file: EncryptedFileMetadata) {
        val tempDir = getTempDir()
        val tempFile = File(tempDir, file.originalName)
        if (tempFile.exists()) {
            try {
                if (tempFile.delete()) {
                    recomposeTrigger = !recomposeTrigger
                } else {
                    android.util.Log.e("VaultTest", "No se pudo eliminar el archivo temporal.")
                }
            } catch (e: SecurityException) {
                android.util.Log.e("VaultTest", "Error de seguridad al eliminar el archivo temporal: ${e.message}", e)
            }
        }
    }

    @Composable
    fun MainScreen() {
        var selectedTab by remember { mutableStateOf(0) }
        val tabs = listOf("Documentos", "Imágenes", "Cámara", "Bóveda")

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
                when (selectedTab) {
                    0 -> FileExplorerScreen(
                        files = unencryptedFiles.filter { it.parentFile?.name == Environment.DIRECTORY_DOCUMENTS },
                        onFileClick = {
                            fileToEncrypt = it
                            showEncryptDialog = true
                        }
                    )
                    1 -> FileExplorerScreen(
                        files = unencryptedFiles.filter { it.parentFile?.name == Environment.DIRECTORY_PICTURES },
                        onFileClick = {
                            fileToEncrypt = it
                            showEncryptDialog = true
                        }
                    )
                    2 -> FileExplorerScreen(
                        files = unencryptedFiles.filter { it.parentFile?.name == Environment.DIRECTORY_DCIM },
                        onFileClick = {
                            fileToEncrypt = it
                            showEncryptDialog = true
                        }
                    )
                    3 -> VaultScreen(
                        encryptedFiles = encryptedFiles,
                        onFileClick = { file ->
                            if (isFileInUse(file)) {
                                fileToDelete = file
                                showDeleteDialog = true
                            } else {
                                fileToUse = file
                                showUseDialog = true
                            }
                        },
                        isFileInUse = { file -> isFileInUse(file) },
                        recomposeTrigger = recomposeTrigger
                    )
                }
            }

            if (showEncryptDialog && fileToEncrypt != null) {
                AlertDialog(
                    onDismissRequest = { showEncryptDialog = false },
                    title = { Text("Cifrar archivo") },
                    text = { Text("¿Deseas cifrar este archivo y eliminar el original?") },
                    confirmButton = {
                        Button(onClick = {
                            encryptFile(fileToEncrypt!!)
                            showEncryptDialog = false
                        }) { Text("Cifrar") }
                    },
                    dismissButton = {
                        Button(onClick = { showEncryptDialog = false }) { Text("Cancelar") }
                    }
                )
            }
            if (showUseDialog && fileToUse != null) {
                AlertDialog(
                    onDismissRequest = { showUseDialog = false },
                    title = { Text("Usar archivo temporalmente") },
                    text = { Text("¿Deseas desencriptar y usar este archivo temporalmente?") },
                    confirmButton = {
                        Button(onClick = {
                            useTempFile(fileToUse!!)
                            showUseDialog = false
                        }) { Text("Usar") }
                    },
                    dismissButton = {
                        Button(onClick = { showUseDialog = false }) { Text("Cancelar") }
                    }
                )
            }
            if (showDeleteDialog && fileToDelete != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Eliminar archivo temporal") },
                    text = { Text("¿Deseas eliminar el archivo temporal?") },
                    confirmButton = {
                        Button(onClick = {
                            deleteTempFile(fileToDelete!!)
                            showDeleteDialog = false
                        }) { Text("Eliminar") }
                    },
                    dismissButton = {
                        Button(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
                    }
                )
            }
        }
    }
}

@Composable
fun FileExplorerScreen(
    files: List<File>,
    onFileClick: (File) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(files) { file ->
            FileItem(
                file = file,
                onClick = { onFileClick(file) }
            )
            Divider()
        }
    }
}

@Composable
fun FileItem(
    file: File,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun VaultScreen(
    encryptedFiles: List<EncryptedFileMetadata>,
    onFileClick: (EncryptedFileMetadata) -> Unit,
    isFileInUse: (EncryptedFileMetadata) -> Boolean,
    recomposeTrigger: Boolean
) {
    LaunchedEffect(recomposeTrigger) {}

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(encryptedFiles) { file ->
            VaultFileItem(
                file = file,
                isInUse = isFileInUse(file),
                onClick = { onFileClick(file) }
            )
            Divider()
        }
    }
}

@Composable
fun VaultFileItem(
    file: EncryptedFileMetadata,
    isInUse: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = file.originalName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (isInUse) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "En uso",
                style = MaterialTheme.typography.bodySmall,
                color = Yellow80,
                modifier = Modifier
                    .background(
                        color = Yellow80.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun PermissionRequestScreen(onPermissionClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Se necesita permiso para leer archivos.")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onPermissionClick) {
                Text("Conceder permiso")
            }
        }
    }
}
