package com.developermx.lockly.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.io.File

@Composable
fun FileExplorerScreen(
    files: List<File>,
    encryptingFiles: Set<Uri>, // <-- Nuevo estado
    onFileClick: (File) -> Unit,
    onFolderClick: (File) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(files.sortedWith(compareBy({ !it.isDirectory }, { it.name }))) { file ->
            val isEncrypting = file.toUri() in encryptingFiles
            FileItem(
                file = file,
                isEncrypting = isEncrypting, // <-- Pasar estado de cifrado
                onClick = {
                    if (!isEncrypting) { // <-- Deshabilitar click si está cifrando
                        if (file.isDirectory) {
                            onFolderClick(file)
                        } else {
                            onFileClick(file)
                        }
                    }
                }
            )
            Divider()
        }
    }
}

@Composable
fun FileItem(
    file: File,
    isEncrypting: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            val icon = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
            if (isEncrypting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (isEncrypting) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
        )
    }
}
