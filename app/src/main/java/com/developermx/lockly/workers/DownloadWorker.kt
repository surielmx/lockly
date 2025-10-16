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
import com.developermx.lockly.data.network.ApiClient
import com.developermx.lockly.data.network.FileUrlRequest
import java.io.File
import java.io.FileOutputStream

/**
 * Worker para descargar archivos cifrados desde la nube en segundo plano.
 * Se ejecuta como ForegroundService para garantizar que no sea terminado por el sistema.
 */
class DownloadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val notificationId = id.hashCode()
        val notification = createNotification("Iniciando descarga...")

        // Configurar como ForegroundService para evitar que el sistema lo mate
        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
        setForeground(foregroundInfo)

        // Obtener parámetros de entrada
        val userId = inputData.getString(KEY_USER_ID)
            ?: run {
                Log.e(TAG, "User ID not provided for download")
                return Result.failure()
            }

        val fileName = inputData.getString(KEY_FILE_NAME)
            ?: run {
                Log.e(TAG, "File name not provided for download")
                return Result.failure()
            }

        val currentIndex = inputData.getInt(KEY_CURRENT_INDEX, 0)
        val totalFiles = inputData.getInt(KEY_TOTAL_FILES, 0)

        return try {
            updateNotification("Descargando '$fileName' ($currentIndex de $totalFiles)", notificationId)

            // 1. Obtener URL de descarga del servidor
            Log.d(TAG, "Requesting download URL for: $fileName")
            val urlRequest = FileUrlRequest(userId = userId, fileName = fileName)
            val urlResponse = ApiClient.apiService.getDownloadUrl(urlRequest)

            if (!urlResponse.isSuccessful || urlResponse.body()?.data == null) {
                Log.e(TAG, "Failed to get download URL. Response: ${urlResponse.code()}")
                throw Exception("No se pudo obtener la URL de descarga para $fileName")
            }

            val downloadUrl = urlResponse.body()!!.data!!.url
            Log.d(TAG, "Download URL obtained successfully")

            // 2. Descargar archivo desde la URL pre-firmada
            Log.d(TAG, "Downloading file from URL")
            val fileResponse = ApiClient.apiService.downloadFileFromUrl(downloadUrl)

            if (!fileResponse.isSuccessful) {
                Log.e(TAG, "Download failed. Status code: ${fileResponse.code()}")
                throw Exception("Error al descargar $fileName (Código: ${fileResponse.code()})")
            }

            val responseBody = fileResponse.body()
                ?: throw Exception("El cuerpo de la respuesta es nulo para $fileName")

            // 3. Guardar archivo en el vault
            val vaultRoot = File(appContext.filesDir, "vault").apply { mkdirs() }
            val destinationFile = File(vaultRoot, fileName)

            // Crear directorios intermedios si es necesario
            destinationFile.parentFile?.mkdirs()

            Log.d(TAG, "Saving file to: ${destinationFile.absolutePath}")
            FileOutputStream(destinationFile).use { fileOutputStream ->
                responseBody.byteStream().use { inputStream ->
                    inputStream.copyTo(fileOutputStream)
                }
            }

            val fileSizeKB = destinationFile.length() / 1024
            Log.i(TAG, "File downloaded successfully. Size: ${fileSizeKB}KB")

            // Mostrar notificación de éxito
            showFinalNotification(
                "Descarga completada",
                "'$fileName' descargado ($currentIndex de $totalFiles)",
                notificationId
            )

            // Retornar el nombre del archivo descargado como output
            val outputData = workDataOf(KEY_DOWNLOADED_FILE_NAME to fileName)
            Result.success(outputData)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for: $fileName", e)
            showFinalNotification(
                "Error de descarga",
                "No se pudo descargar $fileName: ${e.message}",
                notificationId,
                isError = true
            )

            // Reintentar automáticamente hasta 3 veces
            if (runAttemptCount < 3) {
                Log.w(TAG, "Retrying download. Attempt ${runAttemptCount + 1} of 3")
                Result.retry()
            } else {
                Log.e(TAG, "Max retry attempts reached for $fileName")
                Result.failure()
            }
        }
    }

    private fun createNotification(contentText: String, isIndeterminate: Boolean = true) =
        NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Sincronización de archivos")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setProgress(100, 0, isIndeterminate)
            .build()

    private fun updateNotification(contentText: String, notificationId: Int) {
        val notification = createNotification(contentText)
        notificationManager.notify(notificationId, notification)
    }

    private fun showFinalNotification(
        title: String,
        contentText: String,
        notificationId: Int,
        isError: Boolean = false
    ) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(if (isError) android.R.drawable.stat_sys_warning else R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Descarga de Archivos",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones para la descarga de archivos desde la nube"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    init {
        createNotificationChannel()
    }

    companion object {
        private const val TAG = "DownloadWorker"
        private const val CHANNEL_ID = "DownloadChannel"

        // Input keys
        const val KEY_USER_ID = "key_user_id"
        const val KEY_FILE_NAME = "key_file_name"
        const val KEY_CURRENT_INDEX = "key_current_index"
        const val KEY_TOTAL_FILES = "key_total_files"

        // Output keys
        const val KEY_DOWNLOADED_FILE_NAME = "key_downloaded_file_name"
    }
}
