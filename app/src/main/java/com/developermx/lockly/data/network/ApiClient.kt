package com.developermx.lockly.data.network

import com.developermx.lockly.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Singleton object to manage the Retrofit client and provide the API service.
 */
object ApiClient {

    // Base URL is configured per build variant in build.gradle.kts
    // Debug: http://192.168.1.14:3000/
    // Release: https://lockly-api.onrender.com/
    private const val BASE_URL = BuildConfig.BASE_URL

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
