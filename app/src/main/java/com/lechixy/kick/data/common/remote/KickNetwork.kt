package com.lechixy.kick.data.common.remote

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object KickNetwork {

    private const val BASE_URL = "https://web.kick.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val headers = Interceptor { chain ->

        val request = chain.request()
            .newBuilder()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10; SM-G975F) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/91.0.4472.120 Mobile Safari/537.36"
            )
            .header(
                "Accept",
                "application/json"
            )
            .header(
                "Origin",
                "https://kick.com"
            )
            .header(
                "Referer",
                "https://kick.com/"
            )
            .build()

        chain.proceed(request)
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(headers)
        .addInterceptor(logging)
        .build()

    val api: KickApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(
                json.asConverterFactory(
                    "application/json".toMediaType()
                )
            )
            .build()
            .create(KickApi::class.java)
    }
}