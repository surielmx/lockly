package com.developermx.lockly.screens.fileviews

import android.webkit.MimeTypeMap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File
import kotlin.math.log10
import kotlin.math.pow

fun isImage(file: File): Boolean {
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
    return mimeType?.startsWith("image/") == true
}

@Composable
fun getIconForFile(fileName: String): ImageVector {
    return when (fileName.substringAfterLast(".", "").lowercase()) {
        "jpg", "jpeg", "png", "gif", "bmp" -> Icons.Default.Image
        "mp3", "wav", "ogg", "m4a" -> Icons.Default.AudioFile
        "mp4", "3gp", "mkv", "webm" -> Icons.Default.VideoFile
        "pdf" -> Icons.Default.PictureAsPdf
        "doc", "docx" -> Icons.Default.Article
        else -> Icons.Default.Article
    }
}

@Composable
fun getIconForMimeType(mimeType: String): ImageVector {
    return when {
        mimeType.startsWith("image/") -> Icons.Default.Image
        mimeType.startsWith("audio/") -> Icons.Default.AudioFile
        mimeType.startsWith("video/") -> Icons.Default.VideoFile
        mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
        else -> Icons.Default.Article
    }
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return String.format("%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}
