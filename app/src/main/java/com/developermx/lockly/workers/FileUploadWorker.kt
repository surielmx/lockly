package com.developermx.lockly.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
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

        var tempEncryptedFile: File? = null
        return try {
            val fileUriString = inputData.getString(KEY_FILE_URI)
                ?: throw IllegalArgumentException("File URI not provided")

            val userId = VaultManager.getUserId(appContext)
                ?: throw IllegalStateException("User ID not found, aborting upload.")

            Log.d(TAG, "Starting upload for URI: $fileUriString")
            val fileUri = Uri.parse(fileUriString)

            updateNotification("Cifrando archivo...", notificationId)
            Log.d(TAG, "Encrypting file...")
            tempEncryptedFile = VaultManager.encryptFileToTemp(appContext, fileUri)
                ?: throw IOException("Failed to encrypt file")
            Log.d(TAG, "File encrypted successfully: ${tempEncryptedFile.path}")

            updateNotification("Solicitando URL de subida...", notificationId)
            Log.d(TAG, "Requesting upload URL...")
            val request = UploadUrlRequest(
                userId = userId,
                fileName = tempEncryptedFile.name
            )
            val response = ApiClient.apiService.getUploadUrl(request)

            if (!response.success || response.data == null) {
                throw IOException("Failed to get upload URL: ${response.error}")
            }

            val uploadUrl = response.data.url
            Log.d(TAG, "Got upload URL. Starting upload...")

            updateNotification("Subiendo archivo...", notificationId)
            FileUploader.uploadFile(uploadUrl, tempEncryptedFile)

            Log.d(TAG, "Upload finished successfully for URI: $fileUriString")
            showFinalNotification("Subida completada", "El archivo se ha subido con éxito.", notificationId)

            // Return the name of the uploaded file on success
            val outputData = workDataOf(KEY_OUTPUT_ENCRYPTED_FILE_NAME to tempEncryptedFile.name)
            Result.success(outputData)

        } catch (e: Exception) {
            Log.e(TAG, "Upload failed for URI: ${inputData.getString(KEY_FILE_URI)}", e)
            showFinalNotification("Error en la subida", "No se pudo completar la subida del archivo.", notificationId)
            Result.failure()
        } finally {
            tempEncryptedFile?.let {
                if (it.exists()) {
                    Log.d(TAG, "Deleting temporary file: ${it.path}")
                    it.delete()
                }
            }
        }
    }

    private fun createNotification(contentText: String) = NotificationCompat.Builder(appContext, CHANNEL_ID)
        .setContentTitle("Proceso de Lockly")
        .setContentText(contentText)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .setProgress(100, 0, true)
        .build()

    private fun updateNotification(contentText: String, notificationId: Int) {
        val notification = createNotification(contentText)
        notificationManager.notify(notificationId, notification)
    }

    private fun showFinalNotification(title: String, contentText: String, notificationId: Int) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
        const val KEY_FILE_URI = "key_file_uri"
        const val KEY_OUTPUT_ENCRYPTED_FILE_NAME = "key_output_encrypted_file_name"
    }
}
