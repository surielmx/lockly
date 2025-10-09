package com.developermx.lockly.screens

import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lockly.vault.EncryptedFileMetadata
import kotlinx.coroutines.launch
import java.io.File

data class NavItem(val title: String, val icon: ImageVector)

@Composable
fun Breadcrumb(path: String, onPathClick: (String) -> Unit) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val parts = path.removePrefix(rootPath).split("/").filter { it.isNotEmpty() }

        Text(
            text = "Internal",
            modifier = Modifier.clickable { onPathClick(rootPath) },
            color = MaterialTheme.colorScheme.primary
        )

        var currentPath = rootPath
        parts.forEach { part ->
            currentPath += "/$part"
            val targetPath = currentPath
            Text(
                text = " > $part",
                modifier = Modifier.clickable { onPathClick(targetPath) },
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}


@Composable
fun FileExplorerScreen(
    files: List<File>,
    onFileClick: (File) -> Unit,
    onFolderClick: (File) -> Unit,
    currentPath: String,
    onPathClick: (String) -> Unit
) {
    Column {
        Breadcrumb(path = currentPath, onPathClick = onPathClick)
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
        } else {
            LazyColumn {
                items(files) { file ->
                    val itemCount = if (file.isDirectory) file.listFiles()?.size ?: 0 else 0
                    val isDimmed = file.isDirectory && itemCount == 0
                    val contentColor = if (isDimmed) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (file.isDirectory) onFolderClick(file) else onFileClick(file) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (file.isDirectory) {
                            Icon(Icons.Default.Folder, contentDescription = "Folder", tint = contentColor)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "File")
                        }
                        Column {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor
                            )
                            if (file.isDirectory) {
                                Text(
                                    text = "$itemCount items",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    snackbarHostState: SnackbarHostState,
    encryptedFiles: List<EncryptedFileMetadata>,
    unencryptedFiles: List<File>,
    creatingTempFile: Set<String>,
    onEncryptFile: (Uri, String) -> Unit,
    onUseTempFile: (EncryptedFileMetadata) -> Unit,
    onDeleteTempFile: (EncryptedFileMetadata) -> Unit,
    onFolderClick: (File) -> Unit,
    isFileInUse: (EncryptedFileMetadata) -> Boolean,
    currentPath: String,
    onPathClick: (String) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val navItems = listOf(
        NavItem("Explorador", Icons.AutoMirrored.Filled.Article),
        NavItem("Bóveda", Icons.Filled.Security)
    )

    var showUseDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEncryptDialog by remember { mutableStateOf(false) }
    var fileToUse by remember { mutableStateOf<EncryptedFileMetadata?>(null) }
    var fileToDelete by remember { mutableStateOf<EncryptedFileMetadata?>(null) }
    var fileToEncrypt by remember { mutableStateOf<File?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Text("Lockly", modifier = Modifier.padding(16.dp))
                Spacer(Modifier.height(16.dp))
                NavigationDrawerItem(
                    label = { Text("Configuración (Próximamente)") },
                    selected = false,
                    onClick = { /* TODO */ }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = { Text(navItems[selectedTab].title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menú")
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
                        files = unencryptedFiles,
                        onFileClick = {
                            fileToEncrypt = it
                            showEncryptDialog = true
                        },
                        onFolderClick = onFolderClick,
                        currentPath = currentPath,
                        onPathClick = onPathClick
                    )
                    1 -> VaultScreen(
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
                        isFileInUse = isFileInUse,
                        isLoadingFile = { file -> creatingTempFile.contains(file.uuid) }
                    )
                }
            }
        }
    }

    if (showEncryptDialog && fileToEncrypt != null) {
        AlertDialog(
            onDismissRequest = { showEncryptDialog = false },
            title = { Text("Cifrar archivo") },
            text = { Text("¿Deseas cifrar este archivo?") },
            confirmButton = {
                TextButton(onClick = {
                    val file = fileToEncrypt!!
                    onEncryptFile(file.toUri(), file.name)
                    showEncryptDialog = false
                }) { Text("Cifrar") }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showUseDialog && fileToUse != null) {
        val isLoading = creatingTempFile.contains(fileToUse?.uuid)
        AlertDialog(
            onDismissRequest = { if (!isLoading) showUseDialog = false },
            title = { Text("Usar archivo temporalmente") },
            text = {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text("¿Deseas desencriptar y usar este archivo temporalmente?")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUseTempFile(fileToUse!!)
                        showUseDialog = false
                    },
                    enabled = !isLoading
                ) { Text("Usar") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUseDialog = false },
                    enabled = !isLoading
                ) { Text("Cancelar") }
            }
        )
    }

    if (showDeleteDialog && fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar archivo temporal") },
            text = { Text("¿Deseas eliminar el archivo temporal?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTempFile(fileToDelete!!)
                    showDeleteDialog = false
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
