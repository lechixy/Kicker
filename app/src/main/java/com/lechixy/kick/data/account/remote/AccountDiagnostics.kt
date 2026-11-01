package com.lechixy.kick.data.account.remote

import android.util.Log

import com.lechixy.kick.BuildConfig

/** Never include headers, cookies, tokens, response bodies or exception messages here. */
internal fun accountDiagnostic(tag: String, message: String) {
    if (BuildConfig.DEBUG) runCatching { Log.d(tag, message) }
}

/** Reduce platform errors to known codes instead of logging potentially sensitive messages. */
internal fun accountNetworkFailureCode(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    val codes = Regex("\\b(ECONNRESET|ECONNABORTED|ENETUNREACH|EHOSTUNREACH|ETIMEDOUT|EPERM|EACCES|ECONNREFUSED|ENETDOWN|EPIPE)\\b")
    for (cause in causes) {
        codes.find(cause.message.orEmpty())?.let { return it.value }
        if (cause is java.net.UnknownHostException) return "DNS"
        if (cause is java.net.SocketTimeoutException) return "TIMEOUT"
        if (cause is javax.net.ssl.SSLException) return "TLS"
        if (cause is java.net.SocketException && cause.message?.contains("socket closed", ignoreCase = true) == true) return "SOCKET_CLOSED"
    }
    return "UNKNOWN"
}
