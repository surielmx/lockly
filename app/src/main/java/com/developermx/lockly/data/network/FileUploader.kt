package com.developermx.lockly.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

object FileUploader {

    private const val TAG = "FileUploader"

    suspend fun uploadFile(uploadUrl: String, file: File) {
        Log.d(TAG, "Attempting to upload file '${file.name}' to the cloud...")

        val request = Request.Builder()
            .url(uploadUrl)
            .put(file.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
            .build()

        // Execute the call within a coroutine context optimized for I/O
        withContext(Dispatchers.IO) {
            try {
                ApiClient.okHttpClient.newCall(request).execute().use { response ->
                    // Log the server's response code and message regardless of success
                    Log.i(TAG, "Cloud server response: Code=${response.code}, Message='${response.message}'")

                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        Log.e(TAG, "Upload failed. Server error details: $errorBody")
                        // This will now properly throw an exception that the worker can catch
                        throw IOException("Upload failed with code: ${response.code} (${response.message})")
                    }

                    Log.i(TAG, "SUCCESS: File '${file.name}' was uploaded to the cloud successfully.")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Upload failed due to a network I/O error.", e)
                throw e // Re-throw the exception to be caught by the worker
            }
        }
    }
}
