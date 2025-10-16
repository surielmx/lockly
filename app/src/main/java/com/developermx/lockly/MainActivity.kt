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

        // Inicializar SessionManager con el contexto de la aplicación
        SessionManager.init(applicationContext)

        // Obtener contraseña del Intent (viene de AuthActivity)
        val password = intent.getStringExtra("_vlt_pwd.bin")
        if (password != null) {
            SessionManager.setPassword(password)
            Log.d("MainActivity", "Password set in SessionManager")
        } else {
            // Si no hay contraseña en el intent, intentar cargar desde la sesión
            val sessionPassword = SessionManager.getPassword()
            if (sessionPassword != null) {
                Log.d("MainActivity", "Password loaded from session")
            } else {
                Log.w("MainActivity", "No password available - session expired")
            }
        }

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
                val uploadedFiles by viewModel.uploadedFiles.collectAsState()
                val isSyncing by viewModel.isSyncing.collectAsState()
                val isDownloading by viewModel.isDownloading.collectAsState()
                val missingCloudFiles by viewModel.missingCloudFiles.collectAsState()
                val showDeleteTempDialog by viewModel.showDeleteTempDialog.collectAsState()
                val showDecryptMultipleDialog by viewModel.showDecryptMultipleDialog.collectAsState()
                val showDeleteMultipleTempDialog by viewModel.showDeleteMultipleTempDialog.collectAsState()

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
                    MainScreen(
                        snackbarHostState = snackbarHostState,
                        unencryptedFiles = unencryptedFiles,
                        vaultFiles = vaultFiles,
                        creatingTempFile = creatingTempFile,
                        filesToDeleteAfterEncryption = if (filesToDelete.isNotEmpty()) filesToDelete else null,
                        inUseFiles = inUseFiles,
                        recentlyEncryptedFiles = recentlyEncryptedFiles,
                        uploadedFiles = uploadedFiles,
                        isSyncing = isSyncing,
                        isDownloading = isDownloading,
                        missingCloudFiles = missingCloudFiles,
                        showDeleteTempDialog = showDeleteTempDialog,
                        showDecryptMultipleDialog = showDecryptMultipleDialog,
                        showDeleteMultipleTempDialog = showDeleteMultipleTempDialog,
                        onEncryptFiles = viewModel::encryptAndUploadFiles,
                        onDecryptAndOpenFile = viewModel::onVaultFileClick,
                        onDeleteTempFile = viewModel::confirmDeleteTempFile,
                        onDismissDeleteTempDialog = viewModel::dismissDeleteTempDialog,
                        onDownloadMissingFiles = viewModel::downloadMissingFiles,
                        onFolderClick = viewModel::onFolderClick,
                        onVaultFolderClick = viewModel::onVaultFolderClick,
                        getVisibleFileCount = viewModel::getVisibleFileCount,
                        currentPath = currentPath.absolutePath,
                        currentVaultPath = currentVaultPath.absolutePath,
                        vaultRootPath = viewModel.vaultRootPath,
                        onPathClick = viewModel::onPathClick,
                        onVaultPathClick = viewModel::onVaultPathClick,
                        onDeleteOriginalFile = {
                            if (filesToDelete.isNotEmpty()) {
                                viewModel.deleteOriginalFiles(filesToDelete)
                            }
                         },
                        onDismissDeleteConfirmation = viewModel::dismissDeleteConfirmationDialog,
                        onNavigateBack = viewModel::navigateBack,
                        onShareFile = viewModel::shareFile,
                        onShowDecryptMultipleDialog = viewModel::showDecryptMultipleDialog,
                        onDecryptMultipleFiles = viewModel::decryptMultipleFiles,
                        onDismissDecryptMultipleDialog = viewModel::dismissDecryptMultipleDialog,
                        onShowDeleteMultipleTempDialog = { files -> viewModel.showDeleteMultipleTempDialog(files) },
                        onDeleteMultipleTempFiles = { viewModel.confirmDeleteMultipleTempFiles() },
                        onDismissDeleteMultipleTempDialog = viewModel::dismissDeleteMultipleTempDialog
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

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar la sesión cuando la app se destruye
        if (isFinishing) {
            SessionManager.clearSession()
            Log.d("MainActivity", "Session cleared")
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
            // Implement request for older versions if needed
        }
    }
}
