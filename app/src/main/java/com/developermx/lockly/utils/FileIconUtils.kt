package com.developermx.lockly.utils

import android.webkit.MimeTypeMap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File

fun getIconForFile(fileName: String): ImageVector {
    val extension = File(fileName).extension
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())

    return when {
        mimeType?.startsWith("image/") == true -> Icons.Default.Image
        mimeType?.startsWith("video/") == true -> Icons.Default.Videocam
        mimeType?.startsWith("audio/") == true -> Icons.Default.Audiotrack
        mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
        extension.equals("apk", ignoreCase = true) -> Icons.Default.Android
        extension in listOf("doc", "docx", "odt", "txt", "log") -> Icons.Default.Description
        extension in listOf("xls", "xlsx", "ods") -> Icons.Default.GridOn
        extension in listOf("ppt", "pptx", "odp") -> Icons.Default.Slideshow
        extension in listOf("zip", "rar", "7z", "tar", "gz") -> Icons.Default.Archive
        else -> Icons.AutoMirrored.Filled.Article
    }
}
