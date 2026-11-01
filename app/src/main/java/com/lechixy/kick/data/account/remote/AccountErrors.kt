package com.lechixy.kick.data.account.remote

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

fun Throwable.userMessage(): String = when (this) {
    is AccountApiException -> message!!
    is java.io.IOException -> "Bağlantı kurulamadı. İnternet bağlantınızı kontrol edin."
    else -> "İşlem tamamlanamadı. Yeniden deneyin."
}
