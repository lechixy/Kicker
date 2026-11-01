package com.lechixy.kick.data.chat.policy

import com.lechixy.kick.data.chat.model.ChannelMembership

import com.lechixy.kick.data.chat.model.ChatSettings

import java.time.Instant

enum class ChatInputBlock { BANNED, SUBSCRIBERS_ONLY, FOLLOWERS_ONLY, FOLLOW_DURATION, ACCOUNT_AGE, EMOTES_ONLY }

data class ChatComposerPolicy(
    val block: ChatInputBlock?,
    val slowMode: Boolean,
    val emotesOnly: Boolean,
    val subscribersOnly: Boolean,
    val followersOnly: Boolean,
    val accountAge: Boolean
) {
    val hasModes get() = slowMode || emotesOnly || subscribersOnly || followersOnly || accountAge

    companion object {
        fun evaluate(settings: ChatSettings, member: ChannelMembership, createdAt: Instant?, now: Instant): ChatComposerPolicy {
            val subscriber = !member.privileged && settings.subscribersOnly && !member.subscriber
            val followDuration = !member.privileged && settings.followersOnly.enabled && member.following &&
                (member.followingSince == null || now.isBefore(member.followingSince.plusSeconds(settings.followersOnly.durationSeconds)))
            val follow = !member.privileged && settings.followersOnly.enabled && (!member.following || followDuration)
            val age = !member.privileged && settings.minimumAccountAge.enabled &&
                (createdAt == null || now.isBefore(createdAt.plusSeconds(settings.minimumAccountAge.durationSeconds)))
            val block = when {
                member.banned -> ChatInputBlock.BANNED
                subscriber -> ChatInputBlock.SUBSCRIBERS_ONLY
                follow && !member.following -> ChatInputBlock.FOLLOWERS_ONLY
                followDuration -> ChatInputBlock.FOLLOW_DURATION
                age -> ChatInputBlock.ACCOUNT_AGE
                settings.emotesOnly -> ChatInputBlock.EMOTES_ONLY
                else -> null
            }
            return ChatComposerPolicy(block,
                slowMode = settings.slowMode.enabled && !member.privileged && block == null,
                emotesOnly = settings.emotesOnly && !member.banned,
                subscribersOnly = subscriber && !member.banned,
                followersOnly = follow && !member.banned,
                accountAge = age && !member.banned)
        }
    }
}
