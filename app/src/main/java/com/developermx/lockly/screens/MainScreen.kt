package com.developermx.lockly.screens

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.developermx.lockly.utils.getIconForFile
import kotlinx.coroutines.launch
import java.io.File

data class NavItem(val title: String, val icon: ImageVector)

@Composable
fun Breadcrumb(path: String, rootDisplayName: String, rootPath: String, onPathClick: (String) -> Unit) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val parts = path.removePrefix(rootPath).split("/").filter { it.isNotEmpty() }

        Text(
            text = rootDisplayName,
            modifier = Modifier.clickable { onPathClick(rootPath) },
            color = MaterialTheme.colorScheme.primary
        )

        var currentPath = rootPath
        parts.forEach { part ->
            currentPath += "/$part"
            val targetPath = currentPath
            Text(
                text = " > ${part.removeSuffix(".enc")}",
                modifier = Modifier.clickable { onPathClick(targetPath) },
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun isImageFile(fileName: String): Boolean {
    val extension = fileName.removeSuffix(".enc").substringAfterLast('.', "").lowercase()
    return extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
}

@Composable
fun FileExplorerScreen(
    files: List<File>,
    onFileClick: (File) -> Unit,
    onFolderClick: (File) -> Unit,
    getVisibleFileCount: (File) -> Int,
    currentPath: String,
    rootPath: String,
    rootDisplayName: String,
    onPathClick: (String) -> Unit,
    isVault: Boolean,
    inUseFiles: Set<String>,
    recentlyEncryptedFiles: Set<String>
) {
    val imageCount = files.count { isImageFile(it.name) }
    val displayAsGrid = !isVault && imageCount > files.size / 2 && imageCount > 0

    Column {
        Breadcrumb(path = currentPath, rootDisplayName = rootDisplayName, rootPath = rootPath, onPathClick = onPathClick)

        if (files.isEmpty()) {
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
            ImageGrid(files, onFileClick, onFolderClick, getVisibleFileCount, isVault, inUseFiles)
        } else {
            FileList(files, onFileClick, onFolderClick, getVisibleFileCount, isVault, inUseFiles, recentlyEncryptedFiles)
        }
    }
}

@Composable
fun FileList(
    files: List<File>,
    onFileClick: (File) -> Unit,
    onFolderClick: (File) -> Unit,
    getVisibleFileCount: (File) -> Int,
    isVault: Boolean,
    inUseFiles: Set<String>,
    recentlyEncryptedFiles: Set<String>
) {
    LazyColumn {
        items(files) { file ->
            FileListItem(file, onFileClick, onFolderClick, getVisibleFileCount, isVault, inUseFiles, recentlyEncryptedFiles)
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
    inUseFiles: Set<String>
) {
    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 128.dp), contentPadding = PaddingValues(4.dp)) {
        items(files) {
            if (it.isDirectory) {
                FolderGridItem(it, onFolderClick, getVisibleFileCount)
            } else {
                ImageGridItem(it, onFileClick, isVault, inUseFiles)
            }
        }
    }
}

@Composable
fun FileListItem(
    file: File,
    onFileClick: (File) -> Unit,
    onFolderClick: (File) -> Unit,
    getVisibleFileCount: (File) -> Int,
    isVault: Boolean,
    inUseFiles: Set<String>,
    recentlyEncryptedFiles: Set<String>
) {
    val displayName = if (isVault) file.name.removeSuffix(".enc") else file.name
    val itemCount = if (file.isDirectory) getVisibleFileCount(file) else 0
    val isDimmed = file.isDirectory && itemCount == 0
    val contentColor = if (isDimmed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
    val isFileInUse = inUseFiles.contains(displayName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (file.isDirectory) onFolderClick(file) else onFileClick(file) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = if (file.isDirectory) Icons.Default.Folder else getIconForFile(displayName)
        Icon(icon, contentDescription = displayName, tint = contentColor)

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = contentColor, fontSize = 18.sp)
            if (file.isDirectory) {
                Text("$itemCount items", style = MaterialTheme.typography.bodyMedium, color = contentColor, fontSize = 14.sp)
            }
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

        if (isVault && isFileInUse) {
            Icon(Icons.Default.LockOpen, contentDescription = "En uso", tint = Color(0xFFFFA000))
        }
    }
}

@Composable
fun ImageGridItem(file: File, onFileClick: (File) -> Unit, isVault: Boolean, inUseFiles: Set<String>) {
    val displayName = if (isVault) file.name.removeSuffix(".enc") else file.name
    val isFileInUse = inUseFiles.contains(displayName)

    Card(
        modifier = Modifier
            .padding(4.dp)
            .clickable { onFileClick(file) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun FolderGridItem(folder: File, onFolderClick: (File) -> Unit, getVisibleFileCount: (File) -> Int) {
    val itemCount = getVisibleFileCount(folder)
    val isDimmed = itemCount == 0
    val contentColor = if (isDimmed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .padding(4.dp)
            .clickable { onFolderClick(folder) },
        colors = CardDefaults.cardColors(containerColor = if(isDimmed) MaterialTheme.colorScheme.surface.copy(alpha=0.38f) else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.height(128.dp).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Folder, contentDescription = "Carpeta", modifier = Modifier.size(48.dp), tint = contentColor)
            Spacer(Modifier.height(8.dp))
            Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = contentColor)
            Text("$itemCount items", style = MaterialTheme.typography.bodySmall, color = contentColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    snackbarHostState: SnackbarHostState,
    unencryptedFiles: List<File>,
    vaultFiles: List<File>,
    creatingTempFile: Set<String>,
    fileToDeleteAfterEncryption: File?,
    inUseFiles: Set<String>,
    recentlyEncryptedFiles: Set<String>,
    onEncryptFile: (File) -> Unit,
    onDecryptAndOpenFile: (File) -> Unit,
    onDeleteTempFile: (File) -> Unit,
    onFolderClick: (File) -> Unit,
    onVaultFolderClick: (File) -> Unit,
    getVisibleFileCount: (File) -> Int,
    currentPath: String,
    currentVaultPath: String,
    vaultRootPath: String,
    onPathClick: (String) -> Unit,
    onVaultPathClick: (String) -> Unit,
    onDeleteOriginalFile: (File) -> Unit,
    onDismissDeleteConfirmation: () -> Unit,
    onNavigateBack: (Boolean) -> Boolean
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val navItems = listOf(
        NavItem("Bóveda", Icons.Filled.Security),
        NavItem("Explorador", Icons.AutoMirrored.Filled.Article)
    )

    var showOpenDialog by remember { mutableStateOf<File?>(null) }
    var showDeleteTempFileDialog by remember { mutableStateOf<File?>(null) }
    var showEncryptDialog by remember { mutableStateOf<File?>(null) }

    BackHandler(enabled = true) {
        if (!onNavigateBack(selectedTab == 0)) {
            // Si no se puede navegar hacia atrás, el usuario podría querer salir de la app
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Lockly", modifier = Modifier.padding(16.dp))
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(navItems[selectedTab].title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            selected = selectedTab == index,
                            onClick = { selectedTab = index }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    0 -> FileExplorerScreen(
                        files = vaultFiles,
                        onFileClick = { file ->
                            if (inUseFiles.contains(file.name.removeSuffix(".enc"))) {
                                showDeleteTempFileDialog = file
                            } else {
                                showOpenDialog = file
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
                        recentlyEncryptedFiles = recentlyEncryptedFiles
                    )

                    1 -> FileExplorerScreen(
                        files = unencryptedFiles,
                        onFileClick = { showEncryptDialog = it },
                        onFolderClick = onFolderClick,
                        getVisibleFileCount = getVisibleFileCount,
                        currentPath = currentPath,
                        rootPath = Environment.getExternalStorageDirectory().absolutePath,
                        rootDisplayName = "Interno",
                        onPathClick = onPathClick,
                        isVault = false,
                        inUseFiles = emptySet(),
                        recentlyEncryptedFiles = emptySet()
                    )
                }
            }
        }
    }

    // --- Diálogos ---

    showEncryptDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showEncryptDialog = null },
            title = { Text("Cifrar archivo", fontSize = 20.sp) },
            text = { Text("¿Deseas cifrar y mover este archivo a la bóveda?", fontSize = 16.sp) },
            confirmButton = {
                TextButton(onClick = {
                    onEncryptFile(file)
                    showEncryptDialog = null
                }) { Text("Cifrar") }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptDialog = null }) { Text("Cancelar") }
            }
        )
    }

    fileToDeleteAfterEncryption?.let {
        AlertDialog(
            onDismissRequest = onDismissDeleteConfirmation,
            title = { Text("Cifrado completado", fontSize = 20.sp) },
            text = { Text("El archivo se ha cifrado con éxito. ¿Deseas eliminar el archivo original?", fontSize = 16.sp) },
            confirmButton = {
                TextButton(onClick = { onDeleteOriginalFile(it) }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { onDismissDeleteConfirmation() }) { Text("Conservar") }
            }
        )
    }

    showOpenDialog?.let { file ->
        val isLoading = creatingTempFile.contains(file.absolutePath)
        AlertDialog(
            onDismissRequest = { if (!isLoading) showOpenDialog = null },
            title = { Text("Abrir archivo", fontSize = 20.sp) },
            text = {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text("Se creará una copia temporal y se abrirá con otra aplicación. ¿Continuar?", fontSize = 16.sp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDecryptAndOpenFile(file)
                        showOpenDialog = null
                    },
                    enabled = !isLoading
                ) { Text("Abrir") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showOpenDialog = null },
                    enabled = !isLoading
                ) { Text("Cancelar") }
            }
        )
    }

    showDeleteTempFileDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showDeleteTempFileDialog = null },
            title = { Text("Dejar de usar archivo", fontSize = 20.sp) },
            text = { Text("¿Deseas eliminar la copia temporal de este archivo? Ya no estará disponible para otras aplicaciones.", fontSize = 16.sp) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTempFile(file)
                    showDeleteTempFileDialog = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTempFileDialog = null }) { Text("Cancelar") }
            }
        )
    }
}
