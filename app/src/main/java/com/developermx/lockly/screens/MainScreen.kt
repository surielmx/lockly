package com.developermx.lockly.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lockly.vault.EncryptedFileMetadata
import kotlinx.coroutines.launch
import java.io.File

data class NavItem(val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    snackbarHostState: SnackbarHostState,
    encryptedFiles: List<EncryptedFileMetadata>,
    unencryptedFiles: List<File>,
    encryptingFiles: Set<Uri>,
    creatingTempFile: Set<String>,
    onEncryptFile: (Uri, String) -> Unit,
    onUseTempFile: (EncryptedFileMetadata) -> Unit,
    onDeleteTempFile: (EncryptedFileMetadata) -> Unit,
    onFolderClick: (File) -> Unit,
    isFileInUse: (EncryptedFileMetadata) -> Boolean
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
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
            ModalDrawerSheet {
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
                        encryptingFiles = encryptingFiles,
                        onFileClick = {
                            fileToEncrypt = it
                            showEncryptDialog = true
                        },
                        onFolderClick = onFolderClick
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
                Button(onClick = {
                    val file = fileToEncrypt!!
                    onEncryptFile(file.toUri(), file.name)
                    showEncryptDialog = false
                }) { Text("Cifrar") }
            },
            dismissButton = {
                Button(onClick = { showEncryptDialog = false }) { Text("Cancelar") }
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
                Button(
                    onClick = {
                        onUseTempFile(fileToUse!!)
                        showUseDialog = false
                    },
                    enabled = !isLoading
                ) { Text("Usar") }
            },
            dismissButton = {
                Button(
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
                Button(onClick = {
                    onDeleteTempFile(fileToDelete!!)
                    showDeleteDialog = false
                }) { Text("Eliminar") }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
