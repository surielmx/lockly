package com.developermx.lockly.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL = "http://192.168.1.14:3000/" // Using local server IP

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // TEMPORARY WORKAROUND: Hardcoded to true to fix build issues.
        // TODO: Replace with a proper build-time flag once the project environment is fixed.
        level = if (true) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    internal val okHttpClient = OkHttpClient.Builder() // Made internal to be accessible from FileUploader
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS) // Increased timeout for uploads
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: LocklyApiService = retrofit.create(LocklyApiService::class.java)
}
