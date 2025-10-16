package com.developermx.lockly.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.Url

// --- Generic API Response Wrapper ---

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: String?
)

// --- Cloud File Listing ---

data class CloudFile(
    val key: String,
    val fileName: String,
    val size: Long,
    val lastModified: String, // ISO 8601 format
    val eTag: String
)

// --- URL Requests & Responses ---

data class FileUrlRequest(
    val userId: String,
    val fileName: String
)

data class UploadUrlResponse(
    val url: String
)

data class DownloadUrlResponse(
    val url: String,
    val expiresIn: Int
)

// --- Batch Download ---

data class BatchDownloadRequest(
    val userId: String,
    val fileNames: List<String>
)

data class BatchDownloadResponse(
    val success: Boolean,
    val count: Int? = null,
    val data: List<DownloadPresignedUrl>? = null,
    val error: String? = null
)

data class DownloadPresignedUrl(
    val fileName: String,
    val url: String,
    val key: String,
    val expiresIn: Int
)


/**
 * Retrofit service interface for Lockly's cloud API.
 */
interface LocklyApiService {

    /**
     * Fetches the list of file metadata from the cloud for a given user.
     * @param userId The unique identifier of the user.
     * @return A Response containing a wrapped list of cloud files.
     */
    @GET("api/files/{userId}")
    suspend fun getCloudFiles(@Path("userId") userId: String): Response<ApiResponse<List<CloudFile>>>

    /**
     * Requests a pre-signed URL to upload a file.
     * @param request The request body containing userId and fileName.
     * @return A Response containing a wrapped pre-signed URL.
     */
    @POST("api/upload-url")
    suspend fun getUploadUrl(@Body request: FileUrlRequest): Response<ApiResponse<UploadUrlResponse>>

    /**
     * Requests a pre-signed URL to download a file.
     * @param request The request body containing userId and fileName.
     * @return A Response containing a wrapped pre-signed URL for download.
     */
    @POST("api/download-url")
    suspend fun getDownloadUrl(@Body request: FileUrlRequest): Response<ApiResponse<DownloadUrlResponse>>

    /**
     * Requests multiple pre-signed URLs to download files in batch.
     * More efficient than calling getDownloadUrl multiple times.
     * @param request The request body containing userId and list of fileNames.
     * @return A Response containing a list of pre-signed URLs for download.
     */
    @POST("api/download-urls/batch")
    suspend fun getBatchDownloadUrls(@Body request: BatchDownloadRequest): Response<BatchDownloadResponse>

    /**
     * Downloads a file from a given URL.
     * This is a streaming download, so the response body must be handled carefully.
     * @param url The full URL to download the file from.
     * @return A Response containing the raw file data in its body.
     */
    @Streaming
    @GET
    suspend fun downloadFileFromUrl(@Url url: String): Response<ResponseBody>
}
