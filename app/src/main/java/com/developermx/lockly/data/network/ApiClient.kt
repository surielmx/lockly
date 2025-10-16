package com.developermx.lockly.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Singleton object to manage the Retrofit client and provide the API service.
 */
object ApiClient {

    // IMPORTANT: Replace with the actual base URL of the Lockly API
    private const val BASE_URL = "http://192.168.1.14:3000/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    internal val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /**
     * Lazily created instance of the [LocklyApiService].
     */
    val apiService: LocklyApiService by lazy {
        retrofit.create(LocklyApiService::class.java)
    }
}
