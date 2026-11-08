package com.lechixy.kick.components.channel.chat

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.util.Locale

import com.lechixy.kick.R
import com.lechixy.kick.data.channel.model.SubscriberBadge
import com.lechixy.kick.data.chat.model.ChatBadge
import com.lechixy.kick.data.chat.model.ChatIdentity

data class ParsedChatMessage(
    val annotatedString: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>
)

data class ResolvedChatBadge(
    val key: String,
    val name: String,
    val imageUrl: String? = null,
    @DrawableRes val drawableRes: Int? = null,
    val sortOrder: Int,
    val level: Int? = null,
    val count: Int? = null,
    val months: Int? = null
)

internal fun ChatBadge.levelNumber(): Int? {
    val metadata = metadata
    val number = if (metadata is JsonObject) {
        (metadata["level"] as? JsonPrimitive)?.intOrNull
            ?: (metadata["current_level"] as? JsonPrimitive)?.intOrNull
    } else (metadata as? JsonPrimitive)?.intOrNull
    return number?.takeIf { it >= 0 }
}

object ChatTextParser {

    private val EMOTE_REGEX = Regex("\\[emote:(\\d+)(?::([^\\]]+))?\\]")
    private val MENTION_REGEX = Regex("@[a-zA-Z0-9_]+")
    private val URL_REGEX = Regex(
        "(?:https?://[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+|www\\.[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+|[a-zA-Z0-9\\-]+\\.(?:com|org|net|edu|gov|io|gg|tv|me|co|app|live|xyz|dev)(?:/[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?)",
        RegexOption.IGNORE_CASE
    )
    private const val TRAILING_URL_PUNCTUATION = ".,!?;:)%]\"'"

    fun resolveBadges(
        identity: ChatIdentity?,
        subscriberBadges: List<SubscriberBadge>,
        context: Context? = null,
        locale: Locale? = null
    ): List<ResolvedChatBadge> {
        if (identity == null) return emptyList()
        val resContext = if (context != null && locale != null) {
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.createConfigurationContext(config)
        } else context

        val result = mutableListOf<ResolvedChatBadge>()

        identity.badgesV2.forEachIndexed { index, badge ->
            if (!badge.selected) return@forEachIndexed
            val url = badge.imageUrl
            if (!url.isNullOrBlank()) {
                val level = badge.levelNumber()
                val badgeName = if (level != null) {
                    resContext?.getString(R.string.badge_level, level.toString())
                        ?: "Level $level"
                } else {
                    val standardType = badge.name.lowercase()
                    if (standardType in listOf("broadcaster", "verified", "moderator", "og", "vip", "founder", "subscriber", "sub_gifter", "subgifter", "gifter")) {
                        resolveBadgeName(standardType, count = null, context = resContext)
                    } else {
                        badge.name.ifBlank { "badge" }
                    }
                }
                result.add(
                    ResolvedChatBadge(
                        key = "badge_v2_${index}_${badge.name}",
                        name = badgeName,
                        imageUrl = url,
                        sortOrder = badge.sortOrder ?: (index + 1),
                        level = level
                    )
                )
            }
        }

        val v2BadgeNames = identity.badgesV2
            .filter { it.selected && !it.imageUrl.isNullOrBlank() }
            .map { it.name.lowercase() }
            .toSet()

        for (badgeInfo in identity.badges) {
            if (badgeInfo.active == false) continue
            val badgeType = badgeInfo.type.lowercase()
            if (badgeType in v2BadgeNames) continue

            when (badgeType) {
                "broadcaster" -> {
                    result.add(
                        ResolvedChatBadge(
                            key = "badge_local_broadcaster",
                            name = resolveBadgeName("broadcaster", null, resContext),
                            drawableRes = R.drawable.broadcaster_badge,
                            sortOrder = badgeInfo.sortOrder ?: 1
                        )
                    )
                }

                "verified" -> {
                    result.add(
                        ResolvedChatBadge(
                            key = "badge_local_verified",
                            name = resolveBadgeName("verified", null, resContext),
                            drawableRes = R.drawable.verified_badge,
                            sortOrder = badgeInfo.sortOrder ?: 2
                        )
                    )
                }

                "moderator" -> {
                    result.add(
                        ResolvedChatBadge(
                            key = "badge_local_moderator",
                            name = resolveBadgeName("moderator", null, resContext),
                            drawableRes = R.drawable.moderator_badge,
                            sortOrder = badgeInfo.sortOrder ?: 3
                        )
                    )
                }

                "og" -> {
                    result.add(
                        ResolvedChatBadge(
                            key = "badge_local_og",
                            name = resolveBadgeName("og", null, resContext),
                            drawableRes = R.drawable.og_badge,
                            sortOrder = badgeInfo.sortOrder ?: 4
                        )
                    )
                }

                "vip" -> {
                    result.add(
                        ResolvedChatBadge(
                            key = "badge_local_vip",
                            name = resolveBadgeName("vip", null, resContext),
                            drawableRes = R.drawable.vip_badge,
                            sortOrder = badgeInfo.sortOrder ?: 5
                        )
                    )
                }

                "founder" -> {
                    result.add(
                        ResolvedChatBadge(
                            key = "badge_local_founder",
                            name = resolveBadgeName("founder", null, resContext),
                            drawableRes = R.drawable.founder_badge,
                            sortOrder = badgeInfo.sortOrder ?: 6
                        )
                    )
                }

                "sub_gifter", "subgifter", "gifter" -> {
                    val count = badgeInfo.count ?: 1
                    val drawableRes = when {
                        count >= 100 -> R.drawable.gifter_100_badge
                        count >= 50 -> R.drawable.gifter_50_badge
                        count >= 25 -> R.drawable.gifter_25_badge
                        count >= 10 -> R.drawable.gifter_10_badge
                        count >= 5 -> R.drawable.gifter_5_badge
                        else -> R.drawable.gifter_1_badge
                    }
                    result.add(
                        ResolvedChatBadge(
                            key = "badge_local_gifter_$count",
                            name = resolveBadgeName("sub_gifter", badgeInfo.count, resContext),
                            drawableRes = drawableRes,
                            sortOrder = badgeInfo.sortOrder ?: 8,
                            count = count
                        )
                    )
                }

                "subscriber" -> {
                    val months = badgeInfo.count ?: 1
                    val matchedChannelBadge = if (subscriberBadges.isNotEmpty()) {
                        subscriberBadges
                            .filter { it.months <= months && !it.badgeImage?.src.isNullOrBlank() }
                            .maxByOrNull { it.months }
                            ?: subscriberBadges
                                .filter { !it.badgeImage?.src.isNullOrBlank() }
                                .minByOrNull { it.months }
                    } else null

                    val subImageUrl = matchedChannelBadge?.badgeImage?.src
                    if (!subImageUrl.isNullOrBlank() || subscriberBadges.isEmpty()) {
                        result.add(
                            ResolvedChatBadge(
                                key = if (matchedChannelBadge != null) "badge_sub_${matchedChannelBadge.id}_$months" else "badge_sub_$months",
                                name = resolveBadgeName("subscriber", badgeInfo.count, resContext),
                                imageUrl = subImageUrl,
                                sortOrder = badgeInfo.sortOrder ?: 9,
                                months = months
                            )
                        )
                    }
                }
            }
        }

        return result.sortedBy { it.sortOrder }
    }

    fun resolveBadgeName(
        type: String,
        count: Int?,
        context: Context? = null
    ): String {
        return when (type.lowercase()) {
            "broadcaster" -> context?.getString(R.string.badge_broadcaster)
                ?: "Broadcaster"
            "verified" -> context?.getString(R.string.badge_verified)
                ?: "Verified"
            "moderator" -> context?.getString(R.string.badge_moderator)
                ?: "Moderator"
            "og" -> context?.getString(R.string.badge_og)
                ?: "OG"
            "vip" -> context?.getString(R.string.badge_vip)
                ?: "VIP"
            "founder" -> context?.getString(R.string.badge_founder)
                ?: "Founder"
            "subscriber" -> {
                if (count != null && count > 0) {
                    if (count == 1) {
                        context?.getString(R.string.badge_subscriber_month, count)
                            ?: "1-Month Subscriber"
                    } else {
                        context?.getString(R.string.badge_subscriber_months, count)
                            ?: "$count-Months Subscriber"
                    }
                } else {
                    context?.getString(R.string.badge_subscriber)
                        ?: "Subscriber"
                }
            }
            "sub_gifter", "subgifter", "gifter" -> {
                if (count != null && count > 0) {
                    if (count == 1) {
                        context?.getString(R.string.badge_gifter_count_single, count)
                            ?: "Gifted 1 sub"
                    } else {
                        context?.getString(R.string.badge_gifter_count, count)
                            ?: "Gifted $count sub"
                    }
                } else {
                    context?.getString(R.string.badge_gifter)
                        ?: "Sub Gifter"
                }
            }
            else -> type
        }
    }

    fun containsStreamerMention(
        content: String,
        streamerUsername: String?,
        streamerSlug: String?
    ): Boolean {
        if (content.isEmpty() || !content.contains('@')) return false
        if (streamerUsername.isNullOrBlank() && streamerSlug.isNullOrBlank()) return false

        return MENTION_REGEX.findAll(content).any { matchResult ->
            val mentioned = matchResult.value.removePrefix("@")
            isStreamerMatch(mentioned, streamerUsername, streamerSlug)
        }
    }

    private fun isStreamerMatch(
        mentionedUsername: String,
        streamerUsername: String?,
        streamerSlug: String?
    ): Boolean {
        if (!streamerUsername.isNullOrBlank() && mentionedUsername.equals(streamerUsername, ignoreCase = true)) {
            return true
        }
        if (!streamerSlug.isNullOrBlank() && mentionedUsername.equals(streamerSlug, ignoreCase = true)) {
            return true
        }
        return false
    }

    @Composable
    fun parseMessage(
        senderName: String,
        senderColor: Color,
        content: String,
        fontSizeSp: Int,
        badges: List<ResolvedChatBadge> = emptyList(),
        streamerUsername: String? = null,
        streamerSlug: String? = null,
        streamerMentionBrush: Brush? = null,
        streamerMentionShadow: Shadow? = null,
        deleted: Boolean = false,
        timestamp: String? = null,
        interactive: Boolean = true,
    ): ParsedChatMessage {
        val safeFontSizeSp = fontSizeSp.coerceAtLeast(10)
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val primaryColor = MaterialTheme.colorScheme.primary
        val deletedColor = MaterialTheme.colorScheme.onSurfaceVariant

        return remember(
            timestamp,
            interactive,
            deleted,
            deletedColor,
            senderName,
            senderColor,
            content,
            safeFontSizeSp,
            badges,
            secondaryColor,
            primaryColor,
            streamerUsername,
            streamerSlug,
            streamerMentionBrush,
            streamerMentionShadow
        ) {
            val inlineContentMap = mutableMapOf<String, InlineTextContent>()

            val annotatedString = buildAnnotatedString {
                timestamp?.let {
                    withStyle(SpanStyle(color = deletedColor, fontSize = (safeFontSizeSp - 2).sp, fontFeatureSettings = "tnum")) {
                        append(it)
                        append("  ")
                    }
                }
                // 1. Inline Badges (so wrapped text lines start at the very beginning under the badges)
                if (badges.isNotEmpty()) {
                    val badgeHeightSp = (safeFontSizeSp + 2).sp
                    val badgeWidthSp = (safeFontSizeSp + 5).sp
                    for (badge in badges) {
                        if (badge.drawableRes == null && badge.imageUrl.isNullOrBlank()) continue
                        val inlineId = badge.key
                        if (!inlineContentMap.containsKey(inlineId)) {
                            inlineContentMap[inlineId] = InlineTextContent(
                                Placeholder(
                                    width = badgeWidthSp,
                                    height = badgeHeightSp,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                )
                            ) {
                                ChatInlineBadge(badge, interactive)
                            }
                        }
                        appendSafeInlineContent(inlineId, alternateText = "[${badge.name.ifBlank { "badge" }}]")
                    }
                }

                // 2. Sender Username
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = senderColor)) {
                    append(senderName)
                }
                append(": ")

                // 3. Parse Content for Emotes and Mentions
                if (deleted) withStyle(SpanStyle(color = deletedColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(content)
                } else appendParsedContent(
                    content = content,
                    fontSizeSp = safeFontSizeSp,
                    secondaryColor = secondaryColor,
                    primaryColor = primaryColor,
                    inlineContentMap = inlineContentMap,
                    streamerUsername = streamerUsername,
                    streamerSlug = streamerSlug,
                    streamerMentionBrush = streamerMentionBrush,
                    streamerMentionShadow = streamerMentionShadow,
                    interactive = interactive,
                )
            }

            ParsedChatMessage(annotatedString, inlineContentMap)
        }
    }

    private fun AnnotatedString.Builder.appendSafeInlineContent(
        id: String,
        alternateText: String
    ) {
        appendInlineContent(id, alternateText = alternateText.ifBlank { "[inline]" })
    }

    @Composable
    private fun ChatInlineBadge(badge: ResolvedChatBadge, interactive: Boolean) {
        val content: @Composable () -> Unit = {
            Box(
                modifier = Modifier.fillMaxSize().padding(end = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (badge.drawableRes != null) {
                    Image(painterResource(badge.drawableRes), badge.name, Modifier.fillMaxSize())
                } else {
                    AsyncImage(badge.imageUrl, badge.name, Modifier.fillMaxSize())
                }
            }
        }
        if (interactive) BadgeTooltip(badge, content) else content()
    }

    @Composable
    fun parseReplyHeader(
        originalSenderName: String,
        originalSenderColor: Color,
        originalContent: String,
        fontSizeSp: Int,
        mutedColor: Color,
        deleted: Boolean = false,
        timestamp: String? = null
    ): ParsedChatMessage {
        val safeFontSizeSp = fontSizeSp.coerceAtLeast(10)
        val replyingToLabel = stringResource(R.string.replying_to)

        return remember(
            deleted,
            originalSenderName,
            originalSenderColor,
            originalContent,
            safeFontSizeSp,
            mutedColor,
            replyingToLabel
        ) {
            val inlineContentMap = mutableMapOf<String, InlineTextContent>()
            val iconSizeSp = (safeFontSizeSp + 2).sp
            val iconWidthSp = (safeFontSizeSp + 5).sp

            val annotatedString = buildAnnotatedString {
                val replyIconId = "reply_header_icon"
                inlineContentMap[replyIconId] = InlineTextContent(
                    Placeholder(
                        width = iconWidthSp,
                        height = iconSizeSp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            painter = painterResource(R.drawable.reply_24dp),
                            contentDescription = "Reply",
                            tint = mutedColor,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                appendSafeInlineContent(replyIconId, alternateText = "↩ ")

                withStyle(SpanStyle(color = mutedColor)) {
                    append("$replyingToLabel ")
                }
                withStyle(SpanStyle(color = originalSenderColor, fontWeight = FontWeight.SemiBold)) {
                    append(originalSenderName)
                }
                withStyle(SpanStyle(color = mutedColor)) {
                    append(": ")
                }

                if (deleted) withStyle(SpanStyle(color = mutedColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(originalContent)
                } else if (originalContent.isNotEmpty()) {
                    val tokens = findTokens(originalContent)
                    for (token in tokens) {
                        when (token) {
                            is ChatToken.Emote -> {
                                val inlineId = "reply_emote_${token.id}"
                                if (!inlineContentMap.containsKey(inlineId)) {
                                    val emoteUrl = "https://files.kick.com/emotes/${token.id}/fullsize"
                                    val sizeSp = (safeFontSizeSp + 2).sp
                                    inlineContentMap[inlineId] = InlineTextContent(
                                        Placeholder(
                                            width = sizeSp,
                                            height = sizeSp,
                                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                        )
                                    ) {
                                        SynchronizedEmote(
                                            url = emoteUrl,
                                            name = token.name,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                appendSafeInlineContent(inlineId, alternateText = ":${token.name.ifBlank { "emote" }}:")
                            }
                            is ChatToken.Mention -> {
                                pushStringAnnotation(CHAT_MENTION_TAG, token.text.removePrefix("@"))
                                withStyle(SpanStyle(color = mutedColor, fontWeight = FontWeight.Medium)) {
                                    append(token.text)
                                }
                                pop()
                            }
                            is ChatToken.Url -> {
                                withStyle(
                                    SpanStyle(
                                        color = mutedColor,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = TextDecoration.Underline
                                    )
                                ) {
                                    append(token.url)
                                }
                            }
                            is ChatToken.PlainText -> {
                                withStyle(SpanStyle(color = mutedColor)) {
                                    append(token.text)
                                }
                            }
                        }
                    }
                }
            }

            ParsedChatMessage(annotatedString, inlineContentMap)
        }
    }

    private val USER_PLACEHOLDER_REGEX = Regex("%(?:\\d+\\$)?s")

    @Composable
    fun parseUserHeaderWithBadges(
        text: String,
        textColor: Color,
        senderName: String,
        senderColor: Color,
        fontSizeSp: Int,
        badges: List<ResolvedChatBadge> = emptyList(),
        @DrawableRes leadingIconRes: Int? = null,
        leadingIconTint: Color = textColor
    ): ParsedChatMessage {
        val safeFontSizeSp = fontSizeSp.coerceAtLeast(10)
        return remember(
            text,
            textColor,
            senderName,
            senderColor,
            safeFontSizeSp,
            badges,
            leadingIconRes,
            leadingIconTint
        ) {
            val inlineContentMap = mutableMapOf<String, InlineTextContent>()
            val badgeHeightSp = (safeFontSizeSp + 2).sp
            val badgeWidthSp = (safeFontSizeSp + 5).sp

            val annotatedString = buildAnnotatedString {
                if (leadingIconRes != null) {
                    val iconId = "leading_header_icon_$leadingIconRes"
                    inlineContentMap[iconId] = InlineTextContent(
                        Placeholder(
                            width = badgeWidthSp,
                            height = badgeHeightSp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                painter = painterResource(leadingIconRes),
                                contentDescription = null,
                                tint = leadingIconTint,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    appendSafeInlineContent(iconId, alternateText = "[icon]")
                }

                val appendUserBlock: AnnotatedString.Builder.() -> Unit = {
                    for (badge in badges) {
                        if (badge.drawableRes == null && badge.imageUrl.isNullOrBlank()) continue
                        val inlineId = badge.key
                        if (!inlineContentMap.containsKey(inlineId)) {
                            inlineContentMap[inlineId] = InlineTextContent(
                                Placeholder(
                                    width = badgeWidthSp,
                                    height = badgeHeightSp,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                )
                            ) {
                                BadgeTooltip(badge) { Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(end = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        badge.drawableRes != null -> {
                                            Image(
                                                painter = painterResource(badge.drawableRes),
                                                contentDescription = badge.name,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        !badge.imageUrl.isNullOrBlank() -> {
                                            AsyncImage(
                                                model = badge.imageUrl,
                                                contentDescription = badge.name,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                } }
                            }
                        }
                        appendSafeInlineContent(inlineId, alternateText = "[${badge.name.ifBlank { "badge" }}]")
                    }

                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = senderColor)) {
                        append(senderName)
                    }
                }

                val match = USER_PLACEHOLDER_REGEX.find(text)

                if (match != null) {
                    val prefix = text.substring(0, match.range.first)
                    val suffix = text.substring(match.range.last + 1)

                    // Placeholder öncesi (örneğin İngilizcede "Pinned by ")
                    if (prefix.isNotEmpty()) {
                        withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Medium)) {
                            append(prefix)
                        }
                    }

                    appendUserBlock()

                    if (suffix.isNotEmpty()) {
                        withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Medium)) {
                            append(suffix)
                        }
                    }
                } else {
                    if (text.isNotEmpty()) {
                        withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Medium)) {
                            append(text)
                        }
                        if (!text.endsWith(" ")) {
                            append(" ")
                        }
                    }
                    appendUserBlock()
                }
            }

            ParsedChatMessage(annotatedString, inlineContentMap)
        }
    }

    data class EventTextSegment(
        val text: String,
        val isBold: Boolean = false,
        val color: Color? = null,
        val username: String? = null
    )

    fun buildEventAnnotatedString(
        segments: List<EventTextSegment>,
        boldColor: Color,
        mutedColor: Color,
        onUsernameClick: ((String) -> Unit)? = null
    ): AnnotatedString = buildAnnotatedString {
        for (segment in segments) {
            if (segment.text.isEmpty()) continue
            val resolvedColor = segment.color ?: if (segment.isBold) boldColor else mutedColor
            val style = SpanStyle(
                color = resolvedColor,
                fontWeight = if (segment.isBold) FontWeight.Bold else FontWeight.Normal
            )
            withStyle(style) {
                if (segment.username != null && onUsernameClick != null) {
                    withLink(LinkAnnotation.Clickable("chatter:${segment.username}", styles = chatterLinkStyles(style),
                        linkInteractionListener = { onUsernameClick(segment.username) })) { append(segment.text) }
                } else append(segment.text)
            }
        }
    }

    @Composable
    fun parseEventHeader(
        @DrawableRes iconRes: Int,
        iconTint: Color,
        segments: List<EventTextSegment>,
        boldColor: Color,
        mutedColor: Color,
        fontSizeSp: Int,
        onUsernameClick: ((String) -> Unit)? = null
    ): ParsedChatMessage {
        val safeFontSizeSp = fontSizeSp.coerceAtLeast(10)
        return remember(iconRes, iconTint, segments, boldColor, mutedColor, safeFontSizeSp, onUsernameClick) {
            val inlineContentMap = mutableMapOf<String, InlineTextContent>()
            val iconSizeSp = (safeFontSizeSp + 2).sp
            val iconWidthSp = (safeFontSizeSp + 5).sp

            val annotatedString = buildAnnotatedString {
                val iconId = "event_header_icon_$iconRes"
                inlineContentMap[iconId] = InlineTextContent(
                    Placeholder(
                        width = iconWidthSp,
                        height = iconSizeSp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                appendSafeInlineContent(iconId, alternateText = "[icon]")

                append(buildEventAnnotatedString(segments, boldColor, mutedColor, onUsernameClick))
            }

            ParsedChatMessage(annotatedString, inlineContentMap)
        }
    }

    @Composable
    fun parseContentOnly(
        content: String,
        fontSizeSp: Int,
        streamerUsername: String? = null,
        streamerSlug: String? = null,
        streamerMentionBrush: Brush? = null,
        streamerMentionShadow: Shadow? = null
    ): ParsedChatMessage {
        val safeFontSizeSp = fontSizeSp.coerceAtLeast(10)
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val primaryColor = MaterialTheme.colorScheme.primary

        return remember(
            content,
            safeFontSizeSp,
            secondaryColor,
            primaryColor,
            streamerUsername,
            streamerSlug,
            streamerMentionBrush,
            streamerMentionShadow
        ) {
            val inlineContentMap = mutableMapOf<String, InlineTextContent>()

            val annotatedString = buildAnnotatedString {
                appendParsedContent(
                    content = content,
                    fontSizeSp = safeFontSizeSp,
                    secondaryColor = secondaryColor,
                    primaryColor = primaryColor,
                    inlineContentMap = inlineContentMap,
                    streamerUsername = streamerUsername,
                    streamerSlug = streamerSlug,
                    streamerMentionBrush = streamerMentionBrush,
                    streamerMentionShadow = streamerMentionShadow
                )
            }

            ParsedChatMessage(annotatedString, inlineContentMap)
        }
    }

    private fun AnnotatedString.Builder.appendParsedContent(
        content: String,
        fontSizeSp: Int,
        secondaryColor: Color,
        primaryColor: Color = secondaryColor,
        inlineContentMap: MutableMap<String, InlineTextContent>,
        streamerUsername: String?,
        streamerSlug: String?,
        streamerMentionBrush: Brush?,
        streamerMentionShadow: Shadow?,
        interactive: Boolean = true,
    ) {
        if (content.isEmpty()) return
        val safeFontSizeSp = fontSizeSp.coerceAtLeast(10)

        val tokens = findTokens(content)

        for (token in tokens) {
            when (token) {
                is ChatToken.Emote -> {
                    val inlineId = "emote_${token.id}"
                    if (!inlineContentMap.containsKey(inlineId)) {
                        val emoteUrl = "https://files.kick.com/emotes/${token.id}/fullsize"
                        val sizeSp = (safeFontSizeSp + 4).sp
                        inlineContentMap[inlineId] = InlineTextContent(
                            Placeholder(
                                width = sizeSp,
                                height = sizeSp,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                            )
                        ) {
                            SynchronizedEmote(
                                url = emoteUrl,
                                name = token.name,
                                modifier = Modifier.fillMaxSize(),
                                showTooltip = interactive,
                            )
                        }
                    }
                    appendSafeInlineContent(inlineId, alternateText = ":${token.name.ifBlank { "emote" }}:")
                }
                is ChatToken.Mention -> {
                    val mentionedUser = token.text.removePrefix("@")
                    val isStreamer = isStreamerMatch(mentionedUser, streamerUsername, streamerSlug)

                    val mentionSpanStyle = if (isStreamer && streamerMentionBrush != null) {
                        SpanStyle(
                            brush = streamerMentionBrush,
                            fontWeight = FontWeight.ExtraBold,
                            shadow = streamerMentionShadow
                        )
                    } else {
                        SpanStyle(
                            color = secondaryColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    pushStringAnnotation(CHAT_MENTION_TAG, mentionedUser)
                    withStyle(mentionSpanStyle) {
                        append(token.text)
                    }
                    pop()
                }
                is ChatToken.Url -> {
                    if (interactive) withLink(
                        LinkAnnotation.Url(
                            url = token.fullUrl,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = primaryColor,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        )
                    ) {
                        append(token.url)
                    } else append(token.url)
                }
                is ChatToken.PlainText -> {
                    append(token.text)
                }
            }
        }
    }

    fun parseHexColor(colorStr: String?, defaultColor: Color): Color {
        if (colorStr.isNullOrBlank()) return defaultColor
        return try {
            val hex = if (colorStr.startsWith("#")) colorStr else "#$colorStr"
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            defaultColor
        }
    }

    private sealed class ChatToken {
        data class Emote(val id: String, val name: String) : ChatToken()
        data class Mention(val text: String) : ChatToken()
        data class Url(val url: String, val fullUrl: String) : ChatToken()
        data class PlainText(val text: String) : ChatToken()
    }

    private fun findTokens(text: String): List<ChatToken> {
        val tokens = mutableListOf<ChatToken>()
        var currentIndex = 0

        val emoteMatches = EMOTE_REGEX.findAll(text)
        val mentionMatches = MENTION_REGEX.findAll(text)
        val urlMatches = URL_REGEX.findAll(text)

        val allMatches = (
            emoteMatches.map { MatchItem(it.range, MatchType.EMOTE, it) } +
            mentionMatches.map { MatchItem(it.range, MatchType.MENTION, it) } +
            urlMatches.map { MatchItem(it.range, MatchType.URL, it) }
        ).sortedBy { it.range.first }

        for (matchItem in allMatches) {
            if (matchItem.range.first < currentIndex) continue

            if (matchItem.range.first > currentIndex) {
                tokens.add(ChatToken.PlainText(text.substring(currentIndex, matchItem.range.first)))
            }

            when (matchItem.type) {
                MatchType.EMOTE -> {
                    val emoteId = matchItem.matchResult.groupValues[1]
                    val emoteName = matchItem.matchResult.groupValues.getOrNull(2).orEmpty()
                    tokens.add(ChatToken.Emote(emoteId, emoteName))
                }
                MatchType.MENTION -> {
                    tokens.add(ChatToken.Mention(matchItem.matchResult.value))
                }
                MatchType.URL -> {
                    var rawUrl = matchItem.matchResult.value
                    var trailing = ""
                    while (rawUrl.isNotEmpty() && rawUrl.last() in TRAILING_URL_PUNCTUATION) {
                        trailing = rawUrl.last() + trailing
                        rawUrl = rawUrl.dropLast(1)
                    }
                    if (rawUrl.isNotEmpty()) {
                        val fullUrl = when {
                            rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true) -> rawUrl
                            else -> "https://$rawUrl"
                        }
                        tokens.add(ChatToken.Url(url = rawUrl, fullUrl = fullUrl))
                        if (trailing.isNotEmpty()) {
                            tokens.add(ChatToken.PlainText(trailing))
                        }
                    }
                }
            }

            currentIndex = matchItem.range.last + 1
        }

        if (currentIndex < text.length) {
            tokens.add(ChatToken.PlainText(text.substring(currentIndex)))
        }

        return tokens
    }

    private enum class MatchType { EMOTE, MENTION, URL }
    private data class MatchItem(val range: IntRange, val type: MatchType, val matchResult: MatchResult)
}

