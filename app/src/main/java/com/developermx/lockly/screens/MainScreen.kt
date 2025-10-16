package com.developermx.lockly.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.developermx.lockly.R
import com.developermx.lockly.data.network.CloudFile
import com.developermx.lockly.screens.fileviews.getIconForFile
import com.developermx.lockly.ui.theme.White
import java.io.File
import androidx.compose.foundation.BorderStroke

data class NavItem(val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    snackbarHostState: SnackbarHostState,
    unencryptedFiles: List<File>,
    vaultFiles: List<File>,
    creatingTempFile: Set<String>,
    filesToDeleteAfterEncryption: List<File>?,
    inUseFiles: Set<String>,
    recentlyEncryptedFiles: Set<String>,
    uploadedFiles: Set<String>,
    isSyncing: Boolean,
    isDownloading: Boolean,
    missingCloudFiles: List<CloudFile>,
    showDeleteTempDialog: File?,
    onEncryptFiles: (List<File>) -> Unit,
    onDecryptAndOpenFile: (File) -> Unit,
    onDeleteTempFile: () -> Unit,
    onDismissDeleteTempDialog: () -> Unit,
    onDownloadMissingFiles: () -> Unit,
    onFolderClick: (File) -> Unit,
    onVaultFolderClick: (File) -> Unit,
    getVisibleFileCount: (File) -> Int,
    currentPath: String,
    currentVaultPath: String,
    vaultRootPath: String,
    onPathClick: (String) -> Unit,
    onVaultPathClick: (String) -> Unit,
    onDeleteOriginalFile: () -> Unit,
    onDismissDeleteConfirmation: () -> Unit,
    onNavigateBack: (Boolean) -> Boolean,
    onShareFile: (File) -> Unit,
    showDecryptMultipleDialog: List<File> = emptyList(),
    onShowDecryptMultipleDialog: (List<File>) -> Unit = {},
    onDecryptMultipleFiles: (List<File>) -> Unit = {},
    onDismissDecryptMultipleDialog: () -> Unit = {},
    showDeleteMultipleTempDialog: List<File> = emptyList(),
    onShowDeleteMultipleTempDialog: (List<File>) -> Unit = {},
    onDeleteMultipleTempFiles: () -> Unit = {},
    onDismissDeleteMultipleTempDialog: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf(setOf<File>()) }
    var vaultSelectionMode by remember { mutableStateOf(false) }
    var vaultSelectedFiles by remember { mutableStateOf(setOf<File>()) }

    val navItems = listOf(
        NavItem("Bóveda", Icons.Default.Cloud),
        NavItem("Archivos", Icons.Default.Home)
    )

    // Determinar si podemos manejar el back (si no estamos en la raíz o hay modo selección)
    val isAtRoot = if (selectedTab == 0) {
        currentVaultPath == vaultRootPath
    } else {
        currentPath == "/storage/emulated/0"
    }
    val hasSelectionMode = (selectedTab == 0 && vaultSelectionMode) || (selectedTab == 1 && selectionMode)
    val shouldHandleBack = hasSelectionMode || !isAtRoot

    // Manejar el botón back del sistema Android
    BackHandler(enabled = shouldHandleBack) {
        // Verificar si estamos en modo selección
        if (selectedTab == 0 && vaultSelectionMode) {
            // Salir del modo selección en Bóveda
            vaultSelectionMode = false
            vaultSelectedFiles = emptySet()
        } else if (selectedTab == 1 && selectionMode) {
            // Salir del modo selección en Explorador
            selectionMode = false
            selectedFiles = emptySet()
        } else {
            // Navegar hacia atrás en las carpetas
            onNavigateBack(selectedTab == 0)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // Mostrar contador de selección cuando está activo el modo de selección
                    when {
                        selectedTab == 0 && vaultSelectionMode -> {
                            Text("${vaultSelectedFiles.size} seleccionado(s)")
                        }
                        selectedTab == 1 && selectionMode -> {
                            Text("${selectedFiles.size} seleccionado(s)")
                        }
                        else -> Text(navItems[selectedTab].title)
                    }
                },
                navigationIcon = {
                    // Mostrar botón de cerrar en modo selección, o botón de regresar normal
                    when {
                        selectedTab == 0 && vaultSelectionMode -> {
                            IconButton(onClick = {
                                // Simplemente salir del modo selección sin mostrar diálogos
                                vaultSelectionMode = false
                                vaultSelectedFiles = emptySet()
                            }) {
                                Icon(Icons.Default.Close, "Salir de selección")
                            }
                        }
                        selectedTab == 1 && selectionMode -> {
                            IconButton(onClick = {
                                selectionMode = false
                                selectedFiles = emptySet()
                            }) {
                                Icon(Icons.Default.Close, "Salir de selección")
                            }
                        }
                        else -> {
                            val canNavigateBack = if (selectedTab == 0) {
                                currentVaultPath != vaultRootPath
                            } else {
                                currentPath != "/storage/emulated/0"
                            }
                            if (canNavigateBack) {
                                IconButton(onClick = { onNavigateBack(selectedTab == 0) }) {
                                    Icon(Icons.Default.ArrowBack, "Atrás")
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (isSyncing) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Sincronizando",
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            selectionMode = false
                            selectedFiles = emptySet()
                            vaultSelectionMode = false
                            vaultSelectedFiles = emptySet()
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 1 && selectionMode && selectedFiles.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        onEncryptFiles(selectedFiles.toList())
                        selectionMode = false
                        selectedFiles = emptySet()
                    }
                ) {
                    Icon(Icons.Default.Cloud, "Cifrar archivos")
                }
            }
            // Botón flotante para la Bóveda - detectar si hay archivos en uso
            if (selectedTab == 0 && vaultSelectionMode && vaultSelectedFiles.isNotEmpty()) {
                // Detectar si todos los archivos seleccionados están en uso
                val filesInUse = vaultSelectedFiles.filter { file ->
                    val fileName = file.name.removeSuffix(".enc")
                    inUseFiles.contains(fileName)
                }

                val allFilesInUse = filesInUse.size == vaultSelectedFiles.size && filesInUse.isNotEmpty()

                FloatingActionButton(
                    onClick = {
                        if (allFilesInUse) {
                            // Si todos los archivos están en uso, mostrar diálogo de eliminación
                            onShowDeleteMultipleTempDialog(vaultSelectedFiles.toList())
                        } else {
                            // Si no todos están en uso, mostrar diálogo de descifrado
                            onShowDecryptMultipleDialog(vaultSelectedFiles.toList())
                        }
                    },
                    containerColor = if (allFilesInUse) {
                        Color(0xFFD32F2F) // Rojo para eliminar
                    } else {
                        MaterialTheme.colorScheme.primaryContainer // Color por defecto para descifrar
                    }
                ) {
                    Icon(
                        imageVector = if (allFilesInUse) Icons.Default.Close else Icons.Default.LockOpen,
                        contentDescription = if (allFilesInUse) "Eliminar archivos temporales" else "Descifrar archivos",
                        tint = if (allFilesInUse) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (isSyncing || isDownloading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (selectedTab) {
                0 -> Column {
                    // Mostrar banner de archivos faltantes solo si hay archivos para descargar
                    if (missingCloudFiles.isNotEmpty()) {
                        MissingFilesSection(
                            missingFiles = missingCloudFiles,
                            onDownload = onDownloadMissingFiles,
                            isDownloading = isDownloading
                        )
                    }

                    FileExplorerScreen(
                        files = vaultFiles,
                        missingCloudFiles = missingCloudFiles,
                        onFileClick = { file ->
                            android.util.Log.d("MainScreen", "Vault file clicked: ${file.name}, selectionMode: $vaultSelectionMode")
                            if (vaultSelectionMode) {
                                // En modo selección, toggle la selección
                                android.util.Log.d("MainScreen", "In selection mode - toggling selection")
                                vaultSelectedFiles = if (vaultSelectedFiles.contains(file)) {
                                    vaultSelectedFiles - file
                                } else {
                                    vaultSelectedFiles + file
                                }
                            } else {
                                // Fuera de modo selección, descifrar archivo individual
                                android.util.Log.d("MainScreen", "NOT in selection mode - calling onDecryptAndOpenFile")
                                onDecryptAndOpenFile(file)
                            }
                        },
                        onFolderClick = onVaultFolderClick,
                        getVisibleFileCount = getVisibleFileCount,
                        currentPath = currentVaultPath,
                        rootPath = vaultRootPath,
                        rootDisplayName = "Bóveda",
                        onPathClick = onVaultPathClick,
                        isVault = true,
                        inUseFiles = inUseFiles,
                        recentlyEncryptedFiles = recentlyEncryptedFiles,
                        uploadedFiles = uploadedFiles,
                        selectionMode = vaultSelectionMode,
                        selectedFiles = vaultSelectedFiles,
                        onToggleFileSelection = { file ->
                            vaultSelectedFiles = if (vaultSelectedFiles.contains(file)) {
                                vaultSelectedFiles - file
                            } else {
                                vaultSelectedFiles + file
                            }
                        },
                        onFileLongClick = { file ->
                            if (!vaultSelectionMode && !file.isDirectory) {
                                vaultSelectionMode = true
                                vaultSelectedFiles = setOf(file)
                            }
                        },
                        creatingTempFile = creatingTempFile
                    )
                }
                1 -> FileExplorerScreen(
                    files = unencryptedFiles,
                    missingCloudFiles = emptyList(),
                    onFileClick = { if (!selectionMode) onEncryptFiles(listOf(it)) },
                    onFolderClick = onFolderClick,
                    getVisibleFileCount = getVisibleFileCount,
                    currentPath = currentPath,
                    rootPath = "/storage/emulated/0",
                    rootDisplayName = "Almacenamiento",
                    onPathClick = onPathClick,
                    isVault = false,
                    inUseFiles = emptySet(),
                    recentlyEncryptedFiles = emptySet(),
                    uploadedFiles = emptySet(),
                    selectionMode = selectionMode,
                    selectedFiles = selectedFiles,
                    onToggleFileSelection = { file ->
                        selectedFiles = if (selectedFiles.contains(file)) {
                            selectedFiles - file
                        } else {
                            selectedFiles + file
                        }
                    },
                    onFileLongClick = {
                        if (!selectionMode) {
                            selectionMode = true
                            selectedFiles = setOf(it)
                        }
                    },
                    creatingTempFile = creatingTempFile
                )
            }
        }

        if (filesToDeleteAfterEncryption != null && filesToDeleteAfterEncryption.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = onDismissDeleteConfirmation,
                title = { Text("Archivo(s) cifrado(s)") },
                text = {
                    if (filesToDeleteAfterEncryption.size == 1) {
                        Text("El archivo '${filesToDeleteAfterEncryption[0].name}' ha sido cifrado y subido a la nube. ¿Deseas eliminar el archivo original?")
                    } else {
                        Text("${filesToDeleteAfterEncryption.size} archivos han sido cifrados y subidos a la nube. ¿Deseas eliminar los archivos originales?")
                    }
                },
                confirmButton = {
                    Button(onClick = onDeleteOriginalFile) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDeleteConfirmation) {
                        Text("Conservar")
                    }
                }
            )
        }

        // Diálogo para eliminar archivo temporal en uso
        if (showDeleteTempDialog != null) {
            AlertDialog(
                onDismissRequest = onDismissDeleteTempDialog,
                title = { Text("Archivo en uso") },
                text = {
                    Text("Este archivo está actualmente en uso. ¿Deseas eliminar la copia temporal de Documentos/Lockly?")
                },
                confirmButton = {
                    Button(onClick = onDeleteTempFile) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDeleteTempDialog) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Diálogo para descifrado múltiple
        if (showDecryptMultipleDialog.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = onDismissDecryptMultipleDialog,
                title = { Text("Descifrar archivos") },
                text = {
                    Text("¿Deseas descifrar ${showDecryptMultipleDialog.size} archivo(s)?")
                },
                confirmButton = {
                    Button(onClick = {
                        onDecryptMultipleFiles(showDecryptMultipleDialog)
                        // Limpiar la selección después de confirmar el descifrado
                        vaultSelectionMode = false
                        vaultSelectedFiles = emptySet()
                    }) {
                        Text("Descifrar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDecryptMultipleDialog) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Diálogo para eliminación múltiple de archivos temporales
        if (showDeleteMultipleTempDialog.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = onDismissDeleteMultipleTempDialog,
                title = { Text("Eliminar archivos temporales") },
                text = {
                    Text("¿Deseas eliminar ${showDeleteMultipleTempDialog.size} archivo(s) temporal(es)?")
                },
                confirmButton = {
                    Button(onClick = {
                        onDeleteMultipleTempFiles()
                        // Limpiar la selección después de confirmar la eliminación
                        vaultSelectionMode = false
                        vaultSelectedFiles = emptySet()
                    }) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDeleteMultipleTempDialog) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun Breadcrumb(path: String, rootDisplayName: String, rootPath: String, onPathClick: (String) -> Unit) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val parts = path.removePrefix(rootPath).split("/").filter { it.isNotEmpty() }

        Text(
            text = rootDisplayName,
            modifier = Modifier.clickable { onPathClick(rootPath) },
            color = if (parts.isEmpty()) MaterialTheme.colorScheme.primary else White
        )

        var currentPath = rootPath
        parts.forEachIndexed { index, part ->
            currentPath += "/$part"
            val targetPath = currentPath
            val isLastPart = index == parts.size - 1
            Text(
                text = " > ${part.removeSuffix(".enc")}",
                modifier = Modifier.clickable { onPathClick(targetPath) },
                color = if (isLastPart) MaterialTheme.colorScheme.primary else White
            )
        }
    }
}

fun isImageFile(fileName: String): Boolean {
    val extension = fileName.removeSuffix(".enc").substringAfterLast('.', "".lowercase())
    return extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
}

@Composable
fun FileExplorerScreen(
    files: List<File>,
    missingCloudFiles: List<CloudFile>,
    onFileClick: (File) -> Unit,
    onFolderClick: (File) -> Unit,
    getVisibleFileCount: (File) -> Int,
    currentPath: String,
    rootPath: String,
    rootDisplayName: String,
    onPathClick: (String) -> Unit,
    isVault: Boolean,
    inUseFiles: Set<String>,
    recentlyEncryptedFiles: Set<String>,
    uploadedFiles: Set<String>,
    selectionMode: Boolean,
    selectedFiles: Set<File>,
    onToggleFileSelection: (File) -> Unit,
    onFileLongClick: (File) -> Unit,
    creatingTempFile: Set<String>
) {
    val imageCount = files.count { isImageFile(it.name) }
    val displayAsGrid = !isVault && imageCount > files.size / 2 && imageCount > 0

    Column {
        Breadcrumb(path = currentPath, rootDisplayName = rootDisplayName, rootPath = rootPath, onPathClick = onPathClick)

        if (files.isEmpty() && missingCloudFiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = "Carpeta vacía",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Esta carpeta está vacía",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else if (displayAsGrid) {
            ImageGrid(files, onFileClick, onFolderClick, getVisibleFileCount, isVault, inUseFiles, selectionMode, selectedFiles, onToggleFileSelection, onFileLongClick)
        } else {
            FileList(files, missingCloudFiles, onFileClick, onFolderClick, getVisibleFileCount, isVault, inUseFiles, recentlyEncryptedFiles, uploadedFiles, selectionMode, selectedFiles, onToggleFileSelection, onFileLongClick, creatingTempFile)
        }
    }
}

@Composable
fun FileList(
    files: List<File>,
    missingCloudFiles: List<CloudFile>,
    onFileClick: (File) -> Unit,
    onFolderClick: (File) -> Unit,
    getVisibleFileCount: (File) -> Int,
    isVault: Boolean,
    inUseFiles: Set<String>,
    recentlyEncryptedFiles: Set<String>,
    uploadedFiles: Set<String>,
    selectionMode: Boolean,
    selectedFiles: Set<File>,
    onToggleFileSelection: (File) -> Unit,
    onFileLongClick: (File) -> Unit,
    creatingTempFile: Set<String>
) {
    LazyColumn {
        items(files) { file ->
            FileListItem(file, onFileClick, onFolderClick, getVisibleFileCount, isVault, inUseFiles, recentlyEncryptedFiles, uploadedFiles, selectionMode, selectedFiles, onToggleFileSelection, onFileLongClick, creatingTempFile)
        }
        if (isVault) {
            items(missingCloudFiles) { cloudFile ->
                MissingFileListItem(cloudFile)
            }
        }
    }
}


@Composable
fun ImageGrid(
    files: List<File>,
    onFileClick: (File) -> Unit,
    onFolderClick: (File) -> Unit,
    getVisibleFileCount: (File) -> Int,
    isVault: Boolean,
    inUseFiles: Set<String>,
    selectionMode: Boolean,
    selectedFiles: Set<File>,
    onToggleFileSelection: (File) -> Unit,
    onFileLongClick: (File) -> Unit
) {
    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 128.dp), contentPadding = PaddingValues(4.dp)) {
        items(files) {
            if (it.isDirectory) {
                FolderGridItem(it, onFolderClick, getVisibleFileCount)
            } else {
                ImageGridItem(it, onFileClick, isVault, inUseFiles, selectionMode, selectedFiles, onToggleFileSelection, onFileLongClick)
            }
        }
    }
}

@Composable
fun MissingFileListItem(file: CloudFile) {
    val displayName = file.fileName.removeSuffix(".enc")
    val contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = getIconForFile(displayName),
            contentDescription = displayName,
            tint = contentColor
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = contentColor, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = "No descargado",
            tint = contentColor
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: File,
    onFileClick: (File) -> Unit,
    onFolderClick: (File) -> Unit,
    getVisibleFileCount: (File) -> Int,
    isVault: Boolean,
    inUseFiles: Set<String>,
    recentlyEncryptedFiles: Set<String>,
    uploadedFiles: Set<String>,
    selectionMode: Boolean,
    selectedFiles: Set<File>,
    onToggleFileSelection: (File) -> Unit,
    onFileLongClick: (File) -> Unit,
    creatingTempFile: Set<String>
) {
    val displayName = if (isVault) file.name.removeSuffix(".enc") else file.name
    val itemCount = if (file.isDirectory) getVisibleFileCount(file) else 0
    val isDimmed = file.isDirectory && itemCount == 0
    val isFileInUse = inUseFiles.contains(displayName)
    val isSelected = selectedFiles.contains(file)
    val isUploaded = uploadedFiles.contains(file.name)
    val isDecrypting = creatingTempFile.contains(file.absolutePath)

    val inUseColor = Color(0xFFFFA000)
    val baseContentColor = if (isDimmed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
    val contentColor = if (isVault && isFileInUse) inUseColor else baseContentColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        if (!file.isDirectory) onToggleFileSelection(file)
                    } else {
                        if (file.isDirectory) {
                            if (itemCount > 0) onFolderClick(file)
                        } else {
                            onFileClick(file)
                        }
                    }
                },
                onLongClick = {
                    if (!file.isDirectory) onFileLongClick(file)
                }
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = if (file.isDirectory) Icons.Default.Folder else getIconForFile(displayName)
        Icon(icon, contentDescription = displayName, tint = contentColor)

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(displayName, color = contentColor, fontSize = 18.sp)
            if (file.isDirectory) {
                Text("$itemCount items", style = MaterialTheme.typography.bodyMedium, color = contentColor, fontSize = 14.sp)
            }
        }

        // Indicador de descifrado en progreso
        AnimatedVisibility(
            visible = isVault && isDecrypting,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
        }

        if (isVault && isUploaded) {
            Icon(Icons.Default.CloudDone, contentDescription = "Subido a la nube", tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(16.dp))
        }

        if (isVault && recentlyEncryptedFiles.contains(file.absolutePath)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        AnimatedVisibility(
            visible = isVault && isFileInUse,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 2 })
        ) {
            Icon(Icons.Default.LockOpen, contentDescription = "En uso", tint = inUseColor)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageGridItem(
    file: File,
    onFileClick: (File) -> Unit,
    isVault: Boolean,
    inUseFiles: Set<String>,
    selectionMode: Boolean,
    selectedFiles: Set<File>,
    onToggleFileSelection: (File) -> Unit,
    onFileLongClick: (File) -> Unit
) {
    val displayName = if (isVault) file.name.removeSuffix(".enc") else file.name
    val isFileInUse = inUseFiles.contains(displayName)
    val isSelected = selectedFiles.contains(file)

    Card(
        modifier = Modifier
            .padding(4.dp)
            .combinedClickable(
                onClick = {
                    if (selectionMode) onToggleFileSelection(file) else onFileClick(file)
                },
                onLongClick = { if (!isVault) onFileLongClick(file) }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(modifier = Modifier.height(128.dp)) {
            val painter = if (isVault) {
                rememberAsyncImagePainter(model = R.drawable.ic_image_placeholder)
            } else {
                rememberAsyncImagePainter(model = file)
            }
            Image(
                painter = painter,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isVault && isFileInUse) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "En uso",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Seleccionado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun FolderGridItem(
    file: File,
    onFolderClick: (File) -> Unit,
    getVisibleFileCount: (File) -> Int
) {
    val itemCount = getVisibleFileCount(file)
    val isDimmed = itemCount == 0

    Card(
        modifier = Modifier
            .padding(4.dp)
            .clickable(enabled = !isDimmed) { onFolderClick(file) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .height(128.dp)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = file.name,
                modifier = Modifier.size(64.dp),
                tint = if (isDimmed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isDimmed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun MissingFilesSection(
    missingFiles: List<CloudFile>,
    onDownload: () -> Unit,
    isDownloading: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = "Archivos Faltantes",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${missingFiles.size} archivo(s) en la nube no están en este dispositivo.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(
                onClick = onDownload,
                enabled = !isDownloading,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = "Descargar",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Descargar todo")
                }
            }
        }
    }
}
