package com.developermx.lockly.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lockly.vault.EncryptedFileMetadata
import kotlinx.coroutines.launch
import java.io.File

data class NavItem(val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    encryptedFiles: List<EncryptedFileMetadata>,
    unencryptedFiles: List<File>,
    onEncryptFile: (File) -> Unit,
    onUseTempFile: (EncryptedFileMetadata) -> Unit,
    onDeleteTempFile: (EncryptedFileMetadata) -> Unit,
    onFolderClick: (File) -> Unit,
    isFileInUse: (EncryptedFileMetadata) -> Boolean,
    isLoadingFile: (EncryptedFileMetadata) -> Boolean,
    recomposeTrigger: Boolean
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
                        isLoadingFile = isLoadingFile,
                        recomposeTrigger = recomposeTrigger
                    )
                }
            }
        }
    }


    if (showEncryptDialog && fileToEncrypt != null) {
        AlertDialog(
            onDismissRequest = { showEncryptDialog = false },
            title = { Text("Cifrar archivo") },
            text = { Text("¿Deseas cifrar este archivo y eliminar el original?") },
            confirmButton = {
                Button(onClick = {
                    onEncryptFile(fileToEncrypt!!)
                    showEncryptDialog = false
                }) { Text("Cifrar") }
            },
            dismissButton = {
                Button(onClick = { showEncryptDialog = false }) { Text("Cancelar") }
            }
        )
    }
    if (showUseDialog && fileToUse != null) {
        val isLoading = isLoadingFile(fileToUse!!)
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
                    onClick = { onUseTempFile(fileToUse!!) },
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