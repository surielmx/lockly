package com.developermx.lockly

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
            viewModel.hasPermissions = true
            viewModel.loadUnencryptedFiles()
        }
    }

    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // El usuario concedió el permiso, los archivos deberían actualizarse
            viewModel.loadUnencryptedFiles()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LocklyTheme {
                val encryptedFiles by viewModel.encryptedFiles.collectAsState()
                val unencryptedFiles by viewModel.unencryptedFiles.collectAsState()
                val creatingTempFile by viewModel.creatingTempFile.collectAsState()
                val currentPath by viewModel.currentPath.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.snackbarMessage.collectLatest { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.permissionRequest.collectLatest { intentSender ->
                        val request = IntentSenderRequest.Builder(intentSender).build()
                        deleteRequestLauncher.launch(request)
                    }
                }

                if (viewModel.hasPermissions) {
                    MainScreen(
                        snackbarHostState = snackbarHostState,
                        encryptedFiles = encryptedFiles,
                        unencryptedFiles = unencryptedFiles,
                        creatingTempFile = creatingTempFile,
                        onEncryptFile = viewModel::encryptFile,
                        onUseTempFile = viewModel::useTempFile,
                        onDeleteTempFile = viewModel::deleteTempFile,
                        onFolderClick = viewModel::onFolderClick,
                        isFileInUse = viewModel::isFileInUse,
                        currentPath = currentPath.absolutePath,
                        onPathClick = viewModel::onPathClick
                    )
                } else {
                    PermissionRequestScreen {
                        requestPermissionLauncher.launch(permissionsToRequest)
                    }
                }
            }
        }

        if (checkPermissions()) {
            viewModel.hasPermissions = true
            viewModel.loadUnencryptedFiles()
        }
    }

    override fun onBackPressed() {
        if (!viewModel.navigateBack()) {
            super.onBackPressed()
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
}
