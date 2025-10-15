
package com.developermx.lockly.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
import com.developermx.lockly.data.network.UploadUrlRequest
import java.io.File
import java.io.IOException
import java.util.UUID

class FileUploadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val notificationId = id.hashCode()
        val notification = createNotification("Iniciando subida...")

        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }

        setForeground(foregroundInfo)

        val encryptedFilePath = inputData.getString(EncryptWorker.KEY_ENCRYPTED_FILE_PATH)
            ?: run {
                showFinalNotification("Error de subida", "Ruta de archivo cifrado no encontrada.", notificationId, isError = true)
                return Result.failure()
            }
        
        val encryptedFile = File(encryptedFilePath)

        return try {
            if (!encryptedFile.exists()) {
                throw IOException("El archivo cifrado no se encontró en la ruta: $encryptedFilePath")
            }

            val userId = VaultManager.getUserId(appContext)
                ?: throw IllegalStateException("User ID no encontrado, abortando subida.")

            // Generate a UUID for the remote file name
            val remoteFileName = "${UUID.randomUUID()}.enc"
            Log.d(TAG, "Starting upload for ${encryptedFile.name} as $remoteFileName")

            updateNotification("Solicitando URL de subida para '${encryptedFile.name}'", notificationId)
            val request = UploadUrlRequest(
                userId = userId,
                fileName = remoteFileName // Use UUID-based name for the cloud
            )
            val response = ApiClient.apiService.getUploadUrl(request)

            if (!response.success || response.data == null) {
                throw IOException("No se pudo obtener la URL de subida: ${response.error}")
            }

            val uploadUrl = response.data.url
            Log.d(TAG, "URL de subida obtenida. Subiendo '${encryptedFile.name}'...")

            updateNotification("Subiendo '${encryptedFile.name}'...", notificationId)
            FileUploader.uploadFile(uploadUrl, encryptedFile)

            Log.d(TAG, "Subida finalizada para: ${encryptedFile.name} (remoto: $remoteFileName)")
            showFinalNotification("Subida completada", "'${encryptedFile.name}' se ha subido con éxito.", notificationId)

            // Return the original encrypted file name for UI updates
            val outputData = workDataOf(KEY_OUTPUT_ENCRYPTED_FILE_NAME to encryptedFile.name)
            Result.success(outputData)

        } catch (e: Exception) {
            Log.e(TAG, "Fallo la subida para ${encryptedFile.name}", e)
            showFinalNotification("Error en la subida", "No se pudo subir '${encryptedFile.name}'.", notificationId, isError = true)
            Result.failure()
        }
    }

    private fun createNotification(contentText: String) = NotificationCompat.Builder(appContext, CHANNEL_ID)
        .setContentTitle("Proceso de Subida")
        .setContentText(contentText)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .setProgress(100, 0, true)
        .build()

    private fun updateNotification(contentText: String, notificationId: Int) {
        val notification = createNotification(contentText)
        notificationManager.notify(notificationId, notification)
    }

    private fun showFinalNotification(title: String, contentText: String, notificationId: Int, isError: Boolean = false) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(if (isError) android.R.drawable.stat_sys_warning else R.drawable.ic_launcher_foreground)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Subidas de Archivos",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones para subidas de archivos en progreso"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    init {
        createNotificationChannel()
    }

    companion object {
        private const val TAG = "FileUploadWorker"
        private const val CHANNEL_ID = "FileUploadChannel"
        const val KEY_OUTPUT_ENCRYPTED_FILE_NAME = "key_output_encrypted_file_name"
    }
}
