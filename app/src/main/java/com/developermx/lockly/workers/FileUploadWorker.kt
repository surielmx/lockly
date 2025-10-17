package com.developermx.lockly.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.developermx.lockly.R
import com.developermx.lockly.VaultManager
import com.developermx.lockly.data.network.ApiClient
import com.developermx.lockly.data.network.FileUploader
import com.developermx.lockly.data.network.FileUrlRequest
import com.developermx.lockly.receivers.RetryUploadReceiver
import java.io.File
import java.io.IOException

class FileUploadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val notificationId = id.hashCode()
        val notification = createNotification("Iniciando subida...")

        val foregroundInfo = ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        setForeground(foregroundInfo)

        val encryptedFilePath = inputData.getString(EncryptWorker.KEY_ENCRYPTED_FILE_PATH)
            ?: run {
                showFinalNotification(
                    title = "Error de subida",
                    contentText = "Ruta de archivo cifrado no encontrada.",
                    notificationId = notificationId,
                    isError = true
                )
                return Result.failure()
            }

        // Obtener índice actual y total de archivos
        val currentIndex = inputData.getInt(KEY_CURRENT_INDEX, 1)
        val totalFiles = inputData.getInt(KEY_TOTAL_FILES, 1)

        val encryptedFile = File(encryptedFilePath)

        return try {
            if (!encryptedFile.exists()) {
                throw IOException("El archivo cifrado no se encontró en la ruta: $encryptedFilePath")
            }

            val userId = VaultManager.getUserId(appContext)
                ?: throw IllegalStateException("User ID no encontrado, abortando subida.")

            val remoteFileName = encryptedFile.name
            Log.d(TAG, "Starting upload for ${encryptedFile.name} as $remoteFileName ($currentIndex/$totalFiles)")

            updateNotification("Solicitando URL de subida para '${encryptedFile.name}' ($currentIndex de $totalFiles)", notificationId)
            val request = FileUrlRequest(
                userId = userId,
                fileName = remoteFileName
            )

            val response = ApiClient.apiService.getUploadUrl(request)
            if (!response.isSuccessful) {
                throw IOException("Error de red al obtener URL de subida: ${response.code()}")
            }

            val apiResponse = response.body()
            if (apiResponse == null || !apiResponse.success || apiResponse.data == null) {
                throw IOException("No se pudo obtener la URL de subida: ${apiResponse?.error}")
            }

            val uploadUrl = apiResponse.data.url
            Log.d(TAG, "URL de subida obtenida. Subiendo '${encryptedFile.name}'...")

            updateNotification("Subiendo $currentIndex de $totalFiles: '${encryptedFile.name}'", notificationId)
            FileUploader.uploadFile(uploadUrl, encryptedFile)

            Log.d(TAG, "Subida finalizada para: ${encryptedFile.name} (remoto: $remoteFileName)")

            notificationManager.cancel(notificationId)

            val outputData = workDataOf(KEY_OUTPUT_ENCRYPTED_FILE_NAME to encryptedFile.name)
            Result.success(outputData)

        } catch (e: Exception) {
            Log.e(TAG, "Fallo la subida para ${encryptedFile.name}", e)
            showFinalNotification(
                title = "Error en la subida",
                contentText = "No se pudo subir '${encryptedFile.name}'.",
                notificationId = notificationId,
                isError = true,
                encryptedFilePath = encryptedFile.absolutePath
            )
            Result.failure()
        }
    }

    private fun createNotification(contentText: String) =
        NotificationCompat.Builder(appContext, PROGRESS_CHANNEL_ID)
            .setContentTitle("Proceso de Subida")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()

    private fun updateNotification(contentText: String, notificationId: Int) {
        val notification = createNotification(contentText)
        notificationManager.notify(notificationId, notification)
    }

    private fun showFinalNotification(
        title: String,
        contentText: String,
        notificationId: Int,
        isError: Boolean = false,
        encryptedFilePath: String? = null
    ) {
        val channelId = if (isError) ERROR_CHANNEL_ID else PROGRESS_CHANNEL_ID
        
        val builder = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(if (isError) android.R.drawable.stat_sys_warning else R.mipmap.ic_launcher)
            .setAutoCancel(true)

        if (isError && encryptedFilePath != null) {
            val retryIntent = Intent(appContext, RetryUploadReceiver::class.java).apply {
                action = RetryUploadReceiver.ACTION_RETRY_UPLOAD
                putExtra(EncryptWorker.KEY_ENCRYPTED_FILE_PATH, encryptedFilePath)
                putExtra(RetryUploadReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            
            val retryPendingIntent = PendingIntent.getBroadcast(
                appContext,
                notificationId, // Use notificationId as request code for uniqueness
                retryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            builder.addAction(android.R.drawable.ic_popup_sync, "Reintentar", retryPendingIntent)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    private fun createNotificationChannels() {
        val progressChannel = NotificationChannel(
                PROGRESS_CHANNEL_ID,
                "Subidas en Progreso",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones para subidas de archivos en progreso"
            }
            
            val errorChannel = NotificationChannel(
                ERROR_CHANNEL_ID,
                "Errores de Subida",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones para errores al subir archivos"
            }

        notificationManager.createNotificationChannel(progressChannel)
        notificationManager.createNotificationChannel(errorChannel)
    }

    init {
        createNotificationChannels()
    }

    companion object {
        private const val TAG = "FileUploadWorker"
        private const val PROGRESS_CHANNEL_ID = "FileUploadProgressChannel"
        private const val ERROR_CHANNEL_ID = "FileUploadErrorChannel"
        const val KEY_OUTPUT_ENCRYPTED_FILE_NAME = "key_output_encrypted_file_name"
        const val KEY_CURRENT_INDEX = "key_current_index"
        const val KEY_TOTAL_FILES = "key_total_files"
    }
}
