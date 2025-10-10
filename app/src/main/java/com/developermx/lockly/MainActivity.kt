package com.developermx.lockly

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.developermx.lockly.screens.MainScreen
import com.developermx.lockly.screens.PermissionRequestScreen
import com.developermx.lockly.ui.theme.LocklyTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val manageStorageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkPermissions()) {
            viewModel.hasPermissions = true
            viewModel.loadUnencryptedFiles()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LocklyTheme {
                val unencryptedFiles by viewModel.unencryptedFiles.collectAsState()
                val vaultFiles by viewModel.vaultFiles.collectAsState()
                val creatingTempFile by viewModel.creatingTempFile.collectAsState()
                val currentPath by viewModel.currentPath.collectAsState()
                val currentVaultPath by viewModel.currentVaultPath.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                val filesToDelete by viewModel.showDeleteConfirmationDialog.collectAsState()
                val recentlyEncryptedFiles by viewModel.recentlyEncryptedFiles.collectAsState()
                val inUseFiles by viewModel.inUseFiles.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.snackbarMessage.collectLatest { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.openFileRequest.collectLatest { intent ->
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "No se pudo abrir el archivo.", e)
                            viewModel.showSnackbarMessage("No se encontró una aplicación para abrir este archivo.")
                        }
                    }
                }

                if (viewModel.hasPermissions) {
                    // Pasamos la lista de archivos a eliminar al MainScreen
                    val fileToDelete = if (filesToDelete.isNotEmpty()) filesToDelete.first() else null

                    MainScreen(
                        snackbarHostState = snackbarHostState,
                        unencryptedFiles = unencryptedFiles,
                        vaultFiles = vaultFiles,
                        creatingTempFile = creatingTempFile,
                        fileToDeleteAfterEncryption = fileToDelete, // Adaptado para la UI, que aún espera un solo archivo
                        inUseFiles = inUseFiles,
                        recentlyEncryptedFiles = recentlyEncryptedFiles,
                        onEncryptFiles = viewModel::encryptFiles, // Conectado al nuevo método
                        onDecryptAndOpenFile = viewModel::decryptAndOpenFile,
                        onDeleteTempFile = viewModel::deleteTempFile,
                        onFolderClick = viewModel::onFolderClick,
                        onVaultFolderClick = viewModel::onVaultFolderClick,
                        getVisibleFileCount = viewModel::getVisibleFileCount,
                        currentPath = currentPath.absolutePath,
                        currentVaultPath = currentVaultPath.absolutePath,
                        vaultRootPath = viewModel.vaultRootPath,
                        onPathClick = viewModel::onPathClick,
                        onVaultPathClick = viewModel::onVaultPathClick,
                        onDeleteOriginalFile = { // Modificado para llamar a la nueva función de borrado múltiple
                            if (filesToDelete.isNotEmpty()) {
                                viewModel.deleteOriginalFiles(filesToDelete)
                            }
                         },
                        onDismissDeleteConfirmation = viewModel::dismissDeleteConfirmationDialog,
                        onNavigateBack = viewModel::navigateBack,
                        onShareFile = viewModel::shareFile
                    )
                } else {
                    PermissionRequestScreen {
                        requestPermissions()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkPermissions()) {
            if (!viewModel.hasPermissions) {
                viewModel.hasPermissions = true
                viewModel.loadUnencryptedFiles()
            }
        } else {
            viewModel.hasPermissions = false
        }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                manageStorageLauncher.launch(intent)
            }
        } else {
            // Implementar la solicitud para versiones anteriores si es necesario
        }
    }
}
