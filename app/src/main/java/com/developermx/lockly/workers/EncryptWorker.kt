
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
import com.developermx.lockly.SessionManager
import com.developermx.lockly.VaultManager
import java.io.File

class EncryptWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val notificationId = id.hashCode()
        val notification = createNotification()

        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
        setForeground(foregroundInfo)

        val filePath = inputData.getString(KEY_FILE_PATH)
            ?: run {
                Log.e(TAG, "File path not provided for encryption")
                return Result.failure()
            }

        // Obtener índice actual y total de archivos
        val currentIndex = inputData.getInt(KEY_CURRENT_INDEX, 1)
        val totalFiles = inputData.getInt(KEY_TOTAL_FILES, 1)

        return try {
            val originalFile = File(filePath)

            // Obtener contraseña de la sesión
            val password = SessionManager.getPassword()
            if (password.isNullOrBlank()) {
                Log.e(TAG, "No password available in session or it is blank.")
                showFinalNotification("Error de sesión", "Contraseña no disponible. Inicia sesión de nuevo.", notificationId, isError = true)
                return Result.failure()
            }

            updateNotification(notificationId)

            val encryptedFile = VaultManager.importAndEncryptFile(appContext, originalFile, password)
                ?: throw Exception("Error al cifrar el archivo '${originalFile.name}'" + originalFile.name)

            showFinalNotification("Cifrado completado", "'${originalFile.name}' ha sido asegurado en tu bóveda.", notificationId)

            val outputData = workDataOf(KEY_ENCRYPTED_FILE_PATH to encryptedFile.absolutePath)
            Result.success(outputData)

        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed for path: $filePath", e)
            val fileName = File(filePath).name
            showFinalNotification("Error de cifrado", "No se pudo cifrar $fileName.", notificationId, isError = true)
            Result.failure()
        }
    }

    private fun createNotification(isIndeterminate: Boolean = true) = NotificationCompat.Builder(appContext, CHANNEL_ID)
        .setContentTitle("Proceso de Cifrado")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .setProgress(100, 0, isIndeterminate)
        .build()

    private fun updateNotification(notificationId: Int) {
        val notification = createNotification()
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
                "Cifrado de Archivos",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones para el cifrado de archivos en progreso"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    init {
        createNotificationChannel()
    }

    companion object {
        private const val TAG = "EncryptWorker"
        private const val CHANNEL_ID = "EncryptChannel"
        const val KEY_FILE_PATH = "key_file_path"
        const val KEY_ENCRYPTED_FILE_PATH = "key_encrypted_file_path"
        const val KEY_CURRENT_INDEX = "key_current_index"
        const val KEY_TOTAL_FILES = "key_total_files"
    }
}
