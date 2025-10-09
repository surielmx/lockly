package com.developermx.lockly

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.developermx.lockly.screens.MainScreen
import com.developermx.lockly.screens.PermissionRequestScreen
import com.developermx.lockly.ui.theme.LocklyTheme

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LocklyTheme {
                val encryptedFiles by viewModel.encryptedFiles.collectAsState()
                val unencryptedFiles by viewModel.unencryptedFiles.collectAsState()
                val recomposeTrigger by viewModel.recomposeTrigger.collectAsState()
                val loadingFileId by viewModel.loadingFileId.collectAsState()

                if (viewModel.hasPermissions) {
                    MainScreen(
                        encryptedFiles = encryptedFiles,
                        unencryptedFiles = unencryptedFiles,
                        onEncryptFile = viewModel::encryptFile,
                        onUseTempFile = viewModel::useTempFile,
                        onDeleteTempFile = viewModel::deleteTempFile,
                        onFolderClick = viewModel::onFolderClick,
                        isFileInUse = viewModel::isFileInUse,
                        isLoadingFile = { it.uuid == loadingFileId },
                        recomposeTrigger = recomposeTrigger
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