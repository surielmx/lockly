package com.developermx.lockly.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.developermx.lockly.ui.theme.Yellow80
import com.lockly.vault.EncryptedFileMetadata

@Composable
fun VaultScreen(
    encryptedFiles: List<EncryptedFileMetadata>,
    onFileClick: (EncryptedFileMetadata) -> Unit,
    isFileInUse: (EncryptedFileMetadata) -> Boolean,
    isLoadingFile: (EncryptedFileMetadata) -> Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(encryptedFiles) { file ->
            VaultFileItem(
                file = file,
                isInUse = isFileInUse(file),
                isLoading = isLoadingFile(file),
                onClick = { onFileClick(file) }
            )
            Divider()
        }
    }
}

@Composable
fun VaultFileItem(
    file: EncryptedFileMetadata,
    isInUse: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = file.originalName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.width(24.dp))
        } else if (isInUse) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "En uso",
                style = MaterialTheme.typography.bodySmall,
                color = Yellow80,
                modifier = Modifier
                    .background(
                        color = Yellow80.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}