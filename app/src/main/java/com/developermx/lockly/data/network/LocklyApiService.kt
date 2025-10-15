package com.developermx.lockly.data.network

import retrofit2.http.Body
import retrofit2.http.POST

interface LocklyApiService {

    @POST("api/upload-url")
    suspend fun getUploadUrl(@Body request: UploadUrlRequest): UploadUrlResponse

}

data class UploadUrlRequest(
    val userId: String,
    val fileName: String,
    val contentType: String = "application/octet-stream"
)

data class UploadUrlResponse(
    val success: Boolean,
    val data: UploadUrlData? = null,
    val error: String? = null
)

data class UploadUrlData(
    val url: String,
    val key: String,
    val expiresIn: Int
)
