package com.lechixy.kick.components.channel.chat

import android.content.Context

import com.lechixy.kick.R
import com.lechixy.kick.data.account.remote.AccountApiException
import com.lechixy.kick.data.account.remote.string
import com.lechixy.kick.data.chat.policy.ChatIssue
import com.lechixy.kick.data.chat.policy.ChatPolicyException
import com.lechixy.kick.data.chat.policy.ChatRestriction

internal fun ChatRestriction.localized(context: Context): String = context.getString(when (issue) {
    ChatIssue.EMPTY -> R.string.chat_message_empty
    ChatIssue.TOO_LONG -> R.string.chat_message_too_long
    ChatIssue.TOO_LARGE -> R.string.chat_message_too_large
    ChatIssue.BANNED -> R.string.chat_banned
    ChatIssue.EMOTES_ONLY -> R.string.chat_emotes_only
    ChatIssue.SUBSCRIBERS_ONLY -> R.string.chat_subscribers_only
    ChatIssue.FOLLOWERS_ONLY -> R.string.chat_followers_only
    ChatIssue.FOLLOW_TIME_UNKNOWN -> R.string.chat_follow_time_unknown
    ChatIssue.FOLLOW_DURATION -> R.string.chat_follow_duration_required
    ChatIssue.ACCOUNT_AGE_UNKNOWN -> R.string.chat_account_age_unknown
    ChatIssue.ACCOUNT_AGE -> R.string.chat_account_age
    ChatIssue.LINKS_DISABLED -> R.string.chat_links_disabled
    ChatIssue.SLOW_MODE -> R.string.chat_cooldown
    ChatIssue.RATE_LIMIT -> R.string.chat_rate_limited
}, seconds)

internal fun Throwable.chatUserMessage(context: Context): String = when (this) {
    is ChatPolicyException -> restriction.localized(context)
    is java.io.IOException -> context.getString(R.string.chat_network_error)
    else -> context.getString(when ((this as? AccountApiException)?.status) {
        401 -> R.string.chat_auth_error
        403 -> R.string.chat_forbidden_error
        429 -> R.string.chat_limit_error
        else -> R.string.chat_action_error
    })
}
