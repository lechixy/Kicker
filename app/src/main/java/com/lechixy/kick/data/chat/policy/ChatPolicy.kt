package com.lechixy.kick.data.chat.policy

import com.lechixy.kick.data.chat.model.ChannelMembership

import com.lechixy.kick.data.chat.model.ChatSettings

import java.time.Instant

enum class ChatIssue(val legacyText: String) {
    EMPTY("Mesaj boş olamaz."), TOO_LONG("Mesaj en fazla 500 karakter olabilir."),
    TOO_LARGE("Mesaj UTF-8 olarak 2048 baytı aşamaz."),
    BANNED("Bu kanalda mesaj gönderme izniniz yok (yasak/zaman aşımı)."),
    EMOTES_ONLY("Yalnızca emote modu açık. Metin gönderimi kapalı."),
    SUBSCRIBERS_ONLY("Mesaj göndermek için abone olmalısınız."),
    FOLLOWERS_ONLY("Mesaj göndermek için kanalı takip etmelisiniz."),
    FOLLOW_TIME_UNKNOWN("Takip süreniz doğrulanamadı."), FOLLOW_DURATION("Gerekli takip süresi henüz dolmadı."),
    ACCOUNT_AGE_UNKNOWN("Hesap yaşınız doğrulanamadığı için gönderim kapalı."),
    ACCOUNT_AGE("Hesabınızın yaşı kanal şartını karşılamıyor."),
    LINKS_DISABLED("Bu kanalda bağlantı paylaşımı kapalı."), SLOW_MODE(""), RATE_LIMIT("")
}

data class ChatRestriction(val issue: ChatIssue, val seconds: Long = 0) {
    val legacyMessage: String get() = when (issue) {
        ChatIssue.SLOW_MODE -> "Yavaş mod: $seconds saniye bekleyin."
        ChatIssue.RATE_LIMIT -> "Gönderim sınırı: $seconds saniye bekleyin."
        else -> issue.legacyText
    }
}

class ChatPolicyException(val restriction: ChatRestriction) : IllegalArgumentException(restriction.legacyMessage)

object ChatPolicy {
    // Unicode extended grapheme clusters (Java/Android regex \X), not UTF-16 code units.
    fun messageIssue(content: String): ChatIssue? = when {
        content.isBlank() -> ChatIssue.EMPTY
        Regex("\\X").findAll(content).count() > 500 -> ChatIssue.TOO_LONG
        content.toByteArray(Charsets.UTF_8).size > 2048 -> ChatIssue.TOO_LARGE
        else -> null
    }
    fun messageError(content: String): String? = messageIssue(content)?.legacyText

    fun restriction(settings: ChatSettings, member: ChannelMembership, createdAt: Instant?,
                    now: Instant, lastSentAt: Instant?, content: String? = null): String? =
        restrictionIssue(settings, member, createdAt, now, lastSentAt, content)?.legacyMessage

    fun restrictionIssue(settings: ChatSettings, member: ChannelMembership, createdAt: Instant?,
                    now: Instant, lastSentAt: Instant?, content: String? = null): ChatRestriction? {
        if (member.banned) return ChatRestriction(ChatIssue.BANNED)
        if (settings.emotesOnly) return ChatRestriction(ChatIssue.EMOTES_ONLY)
        if (!member.privileged) {
            if (settings.subscribersOnly && !member.subscriber) return ChatRestriction(ChatIssue.SUBSCRIBERS_ONLY)
            if (settings.followersOnly.enabled) {
                if (!member.following) return ChatRestriction(ChatIssue.FOLLOWERS_ONLY)
                val since = member.followingSince ?: return ChatRestriction(ChatIssue.FOLLOW_TIME_UNKNOWN)
                if (now.isBefore(since.plusSeconds(settings.followersOnly.durationSeconds))) return ChatRestriction(ChatIssue.FOLLOW_DURATION)
            }
            if (settings.minimumAccountAge.enabled) {
                val created = createdAt ?: return ChatRestriction(ChatIssue.ACCOUNT_AGE_UNKNOWN)
                if (now.isBefore(created.plusSeconds(settings.minimumAccountAge.durationSeconds))) return ChatRestriction(ChatIssue.ACCOUNT_AGE)
            }
            if (settings.slowMode.enabled && lastSentAt != null) {
                val remaining = (java.time.Duration.between(now,
                    lastSentAt.plusSeconds(settings.slowMode.durationSeconds)).toMillis() + 999) / 1000
                if (remaining > 0) return ChatRestriction(ChatIssue.SLOW_MODE, remaining)
            }
        }
        if (content != null && !settings.allowLinks && Regex("(?i)(https?://|www\\.|\\b[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}(?:[/\\s]|$))").containsMatchIn(content)) {
            return ChatRestriction(ChatIssue.LINKS_DISABLED)
        }
        return null
    }
}
