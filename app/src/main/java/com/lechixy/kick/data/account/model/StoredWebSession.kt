package com.lechixy.kick.data.account.model

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

import com.lechixy.kick.KickWebLoginActivity
import com.lechixy.kick.data.account.local.SessionStore
import com.lechixy.kick.data.account.model.AccountProfile
import com.lechixy.kick.data.account.model.AccountSession
import com.lechixy.kick.data.account.model.SessionStatus
import com.lechixy.kick.data.account.remote.AccountApiException
import com.lechixy.kick.data.account.remote.accountDiagnostic
import com.lechixy.kick.data.account.remote.accountNetworkFailureCode
import com.lechixy.kick.data.account.remote.string

@Serializable
data class StoredWebSession(
    val userId: Long, val kickCookies: String, val webCookies: String,
    val userAgent: String, val createdAt: String? = null, val profile: AccountProfile? = null
)

