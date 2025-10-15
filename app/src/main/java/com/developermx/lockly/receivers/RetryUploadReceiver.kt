package com.developermx.lockly.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.developermx.lockly.workers.EncryptWorker
import com.developermx.lockly.workers.FileUploadWorker

class RetryUploadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val encryptedFilePath = intent.getStringExtra(EncryptWorker.KEY_ENCRYPTED_FILE_PATH)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        if (encryptedFilePath.isNullOrEmpty()) {
            Log.e(TAG, "Encrypted file path not found in retry intent.")
            return
        }

        Log.d(TAG, "Retrying upload for: $encryptedFilePath")

        val workManager = WorkManager.getInstance(context)

        // Create a new upload request with the same encrypted file path
        val uploadRequest = OneTimeWorkRequestBuilder<FileUploadWorker>()
            .setInputData(workDataOf(EncryptWorker.KEY_ENCRYPTED_FILE_PATH to encryptedFilePath))
            .build()

        workManager.enqueue(uploadRequest)

        // Dismiss the notification that triggered the retry
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        // Ideally, the MainViewModel would observe this new work request to update the UI.
        // For now, we just re-enqueue and the worker will post its own progress notifications.
    }

    companion object {
        private const val TAG = "RetryUploadReceiver"
        const val ACTION_RETRY_UPLOAD = "com.developermx.lockly.ACTION_RETRY_UPLOAD"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
