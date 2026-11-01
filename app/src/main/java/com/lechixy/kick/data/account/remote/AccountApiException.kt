package com.lechixy.kick.data.account.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

class AccountApiException(val status: Int, val retryAfterSeconds: Long? = null) : Exception(
    when (status) {
        401 -> "Oturum doğrulanamadı. Yeniden giriş yapın."
        403 -> "Kick bu işlem için erişim izni vermedi."
        404 -> "Bu özellik veya kanal bulunamadı."
        422 -> "Kick bu isteği kabul etmedi. Mesajı ve kanal kurallarını kontrol edin."
        429 -> "Çok fazla istek. Lütfen biraz bekleyin."
        else -> "Kick isteği başarısız (HTTP $status)."
    }
)
