package com.lechixy.kick.components.channel.chat

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.lechixy.kick.R
import com.lechixy.kick.data.channel.model.SubscriberBadge
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.MessageDeletedEvent

@Composable
internal fun resolveDeletedMessageText(event: MessageDeletedEvent): String = when {
    event.aiModerated -> stringResource(R.string.message_deleted_by_ai_moderator)
    event.violatedRules.isNotEmpty() -> stringResource(R.string.message_deleted_rule_violation)
    else -> stringResource(R.string.message_deleted_by_moderator)
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 14,
    subscriberBadges: List<SubscriberBadge> = emptyList(),
    userColors: Map<String, String> = emptyMap(),
    streamerUsername: String? = null,
    streamerSlug: String? = null,
    onUserClick: ((com.lechixy.kick.data.chat.model.ChatSender) -> Unit)? = null,
    isFavorite: Boolean = false,
    isOwnMessage: Boolean = false,
    replyHighlight: Int = 0,
    onReplyClick: ((String) -> Unit)? = null,
    timestamp: String? = message.recordingOffsetMs?.let(com.lechixy.kick.util.FormatUtils::formatDuration),
) {
    val eventContext = LocalContext.current
    // Read configuration so in-place app language changes rebuild every event sentence.
    LocalConfiguration.current
    val onEventUserClick: ((String) -> Unit)? = onUserClick?.let { callback -> { username ->
        callback(message.sender?.takeIf { it.username.equals(username, ignoreCase = true) }
            ?: com.lechixy.kick.data.chat.model.ChatSender(username = username))
    } }
    // 1. Reward Redeemed Event
    val rewardEvent = message.rewardRedeemed
    if (message.isRewardRedeemed && rewardEvent != null) {
        val defaultPrimaryColor = MaterialTheme.colorScheme.primary
        val rewardColor = remember(rewardEvent.rewardBackgroundColor, defaultPrimaryColor) {
            ChatTextParser.parseHexColor(rewardEvent.rewardBackgroundColor, defaultPrimaryColor)
        }
        val segments = eventTextSegments(stringResource(R.string.chat_event_reward_redeemed),
            eventUser(rewardEvent.username.ifBlank { stringResource(R.string.chat_event_user) }, rewardColor,
                rewardEvent.username.takeIf { it.isNotBlank() }),
            eventUser(rewardEvent.rewardTitle, rewardColor, username = null))
        ChatEventBaseItem(
            iconRes = R.drawable.bubble_chart_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
            segments = segments,
            bodyContent = rewardEvent.userInput.takeIf { it.isNotBlank() },
            onUsernameClick = onEventUserClick,
            fontSizeSp = fontSizeSp,
            stripeColor = rewardColor,
            contentPadding = 2.dp,
            streamerUsername = streamerUsername,
            streamerSlug = streamerSlug,
            modifier = modifier
        )
        return
    }

    // 2. Gifted Subscriptions Event
    val giftedEvent = message.giftedSubscriptions
    if (message.isGiftedSubscriptions && giftedEvent != null) {
        val defaultPrimaryColor = MaterialTheme.colorScheme.primary
        val gifterName = giftedEvent.gifterUsername.ifBlank { stringResource(R.string.chat_event_anonymous) }
        val gifterRawColor = userColors[gifterName.lowercase()]
        val gifterColor = remember(gifterRawColor, defaultPrimaryColor) {
            ChatTextParser.parseHexColor(gifterRawColor, defaultPrimaryColor)
        }
        val segments = giftedEventSegments(eventContext, giftedEvent, userColors, defaultPrimaryColor)
        ChatEventBaseItem(
            iconRes = R.drawable.featured_seasonal_and_gifts_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
            segments = segments,
            bodyContent = null,
            fontSizeSp = fontSizeSp,
            stripeColor = gifterColor,
            onUsernameClick = onEventUserClick,
            contentPadding = 10.dp,
            streamerUsername = streamerUsername,
            streamerSlug = streamerSlug,
            modifier = modifier
        )
        return
    }

    // 3. Chat Celebration Event (Resub / Celebration message)
    if (message.isCelebration) {
        val defaultPrimaryColor = MaterialTheme.colorScheme.primary
        val celebration = message.metadata?.celebration
        val senderName = message.sender?.username?.takeIf { it.isNotBlank() }
            ?: celebration?.sender?.username?.takeIf { it.isNotBlank() }
            ?: celebration?.username?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.chat_event_someone)
        val rawColor = message.sender?.identity?.color
            ?: celebration?.sender?.identity?.color
            ?: userColors[senderName.lowercase()]
        val userColor = remember(rawColor, defaultPrimaryColor) {
            ChatTextParser.parseHexColor(rawColor, defaultPrimaryColor)
        }
        val months = celebration?.totalMonths

        val segments = subscriptionEventSegments(eventContext, senderName, months,
            userColor = userColor, primaryColor = defaultPrimaryColor)
        ChatEventBaseItem(
            iconRes = R.drawable.celebration_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
            segments = segments,
            bodyContent = message.content.takeIf { it.isNotBlank() },
            fontSizeSp = fontSizeSp,
            stripeColor = userColor,
            onUsernameClick = onEventUserClick,
            iconTint = defaultPrimaryColor,
            streamerUsername = streamerUsername,
            streamerSlug = streamerSlug,
            modifier = modifier
        )
        return
    }

    // 4. Channel Subscription Event (SubscriptionEvent)
    val subEvent = message.channelSubscription
    if (message.isChannelSubscription && subEvent != null) {
        val defaultPrimaryColor = MaterialTheme.colorScheme.primary
        val subUsername = subEvent.username.ifBlank { stringResource(R.string.chat_event_someone) }
        val subRawColor = userColors[subUsername.lowercase()]
        val userColor = remember(subRawColor, defaultPrimaryColor) {
            ChatTextParser.parseHexColor(subRawColor, defaultPrimaryColor)
        }
        val months = subEvent.totalMonths ?: subEvent.months
        val extraSubscribers = (subEvent.userIds.size - 1).coerceAtLeast(0)

        val segments = subscriptionEventSegments(eventContext, subUsername, months, extraSubscribers,
            userColor = userColor, primaryColor = defaultPrimaryColor)
        ChatEventBaseItem(
            iconRes = R.drawable.star_shine_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
            segments = segments,
            bodyContent = message.content.takeIf { it.isNotBlank() },
            fontSizeSp = fontSizeSp,
            stripeColor = userColor,
            onUsernameClick = onEventUserClick,
            iconTint = defaultPrimaryColor,
            streamerUsername = streamerUsername,
            streamerSlug = streamerSlug,
            modifier = modifier
        )
        return
    }

    val senderName = message.sender?.username ?: stringResource(R.string.chat_event_user)
    val defaultUsernameColor = MaterialTheme.colorScheme.primary
    val rawSenderColor = message.sender?.identity?.color ?: userColors[senderName.lowercase()]
    val usernameColor = remember(rawSenderColor, defaultUsernameColor) {
        ChatTextParser.parseHexColor(rawSenderColor, defaultUsernameColor)
    }

    val isBroadcaster = remember(
        message.sender?.identity?.badges,
        message.sender?.username,
        message.sender?.slug,
        streamerUsername,
        streamerSlug
    ) {
        val hasBroadcasterBadge = message.sender?.identity?.badges?.any {
            it.type.equals("broadcaster", ignoreCase = true)
        } == true
        val matchesUsername = !streamerUsername.isNullOrBlank() && (
            message.sender?.username.equals(streamerUsername, ignoreCase = true) ||
                message.sender?.slug.equals(streamerUsername, ignoreCase = true)
        )
        val matchesSlug = !streamerSlug.isNullOrBlank() && (
            message.sender?.username.equals(streamerSlug, ignoreCase = true) ||
                message.sender?.slug.equals(streamerSlug, ignoreCase = true)
        )
        hasBroadcasterBadge || matchesUsername || matchesSlug
    }

    val broadcasterPalette = remember(isBroadcaster, isFavorite, isOwnMessage, usernameColor) {
        if (isBroadcaster || isFavorite || isOwnMessage) resolveBroadcasterMessagePalette(usernameColor) else null
    }
    val replyPulse = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(replyHighlight) {
        replyPulse.snapTo(0f)
        if (replyHighlight == 0) return@LaunchedEffect
        repeat(2) {
            replyPulse.animateTo(0.26f, tween(180))
            replyPulse.animateTo(0f, tween(480))
        }
    }
    val pulseColor = MaterialTheme.colorScheme.primary
    val pulseModifier = Modifier.drawBehind {
        drawRoundRect(pulseColor.copy(alpha = replyPulse.value), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
    }
    val effectiveSenderColor = broadcasterPalette?.senderColor ?: usernameColor
    val effectiveBodyTextColor = broadcasterPalette?.contentColor ?: MaterialTheme.colorScheme.onSurface

    val context = LocalContext.current
    val badges = remember(message.sender?.identity, subscriberBadges, context) {
        ChatTextParser.resolveBadges(message.sender?.identity, subscriberBadges, context = context)
    }

    val messageContent = message.messageDeleted?.let { resolveDeletedMessageText(it) } ?: message.content

    val hasStreamerMention = remember(messageContent, message.isDeleted, streamerUsername, streamerSlug) {
        !message.isDeleted && ChatTextParser.containsStreamerMention(messageContent, streamerUsername, streamerSlug)
    }

    val (streamerMentionBrush, streamerMentionShadow) = if (hasStreamerMention) {
        rememberStreamerMentionEffects(fontSizeSp = fontSizeSp)
    } else {
        null to null
    }

    val parsedMessage = ChatTextParser.parseMessage(
        senderName = senderName,
        senderColor = effectiveSenderColor,
        content = messageContent,
        fontSizeSp = fontSizeSp,
        badges = badges,
        streamerUsername = streamerUsername,
        streamerSlug = streamerSlug,
        streamerMentionBrush = streamerMentionBrush,
        streamerMentionShadow = streamerMentionShadow,
        timestamp = timestamp,
        deleted = message.messageDeleted != null
    )

    val rowModifier = if (broadcasterPalette != null) {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(broadcasterPalette.backgroundColor)
            .then(pulseModifier)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .then(pulseModifier)
    }

    Column(
        modifier = rowModifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val originalMessage = message.metadata?.originalMessage
        val replyTargetId = originalMessage?.id?.takeIf { it.isNotBlank() }
            ?: message.threadParentId?.takeIf { it.isNotBlank() }
            ?: message.metadata?.messageRef?.takeIf { it.isNotBlank() }
        if (message.isReply && originalMessage != null && originalMessage.content.isNotBlank()) {
            val originalSenderName = message.metadata.originalSender?.username
                ?.takeIf { it.isNotBlank() }
                ?: originalMessage.sender?.username
                ?: stringResource(R.string.chat_event_user)

            val rawOriginalColor = message.metadata.originalSender?.identity?.color
                ?: originalMessage.sender?.identity?.color
                ?: userColors[originalSenderName.lowercase()]

            val originalSenderColor = remember(rawOriginalColor, defaultUsernameColor, broadcasterPalette) {
                val parsed = ChatTextParser.parseHexColor(rawOriginalColor, defaultUsernameColor)
                if (broadcasterPalette != null) {
                    ensureReadableColorOnBackground(parsed, broadcasterPalette.backgroundColor)
                } else {
                    parsed
                }
            }

            val replyMutedColor = broadcasterPalette?.mutedColor
                ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            val replyFontSizeSp = (fontSizeSp - 2).coerceAtLeast(10)
            val originalContent = originalMessage.messageDeleted?.let { resolveDeletedMessageText(it) }
                ?: originalMessage.content

            val parsedReply = ChatTextParser.parseReplyHeader(
                originalSenderName = originalSenderName,
                originalSenderColor = originalSenderColor,
                originalContent = originalContent,
                fontSizeSp = replyFontSizeSp,
                mutedColor = replyMutedColor,
                deleted = originalMessage.messageDeleted != null
            )

            Text(
                text = androidx.compose.ui.text.buildAnnotatedString {
                    append(parsedReply.annotatedString.withMentionLinks(onEventUserClick))
                    val start = parsedReply.annotatedString.text.indexOf(originalSenderName)
                    if (start >= 0 && onUserClick != null) {
                        addLink(androidx.compose.ui.text.LinkAnnotation.Clickable("reply-chatter",
                            styles = chatterLinkStyles(androidx.compose.ui.text.SpanStyle(color = originalSenderColor, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)),
                            linkInteractionListener = {
                            onUserClick(originalMessage.sender?.takeIf { it.username == originalSenderName }
                                ?: com.lechixy.kick.data.chat.model.ChatSender(
                                    id = message.metadata.originalSender?.id, username = originalSenderName,
                                    slug = message.metadata.originalSender?.slug, identity = message.metadata.originalSender?.identity))
                        }), start, start + originalSenderName.length)
                    }
                },
                fontStyle = FontStyle.Normal,
                inlineContent = parsedReply.inlineContent,
                fontSize = replyFontSizeSp.sp,
                lineHeight = (replyFontSizeSp + 3).sp,
                color = replyMutedColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().then(
                    if (onReplyClick != null && replyTargetId != null) Modifier.clickable(
                        onClickLabel = stringResource(R.string.chat_go_to_reply)
                    ) { onReplyClick(replyTargetId) } else Modifier)
            )
        }

        ChatClipLinks(parsedMessage.annotatedString) {
            Text(
                text = androidx.compose.ui.text.buildAnnotatedString {
                    append(parsedMessage.annotatedString.withMentionLinks(onEventUserClick))
                    val start = parsedMessage.annotatedString.text.indexOf(senderName)
                    if (start >= 0 && onUserClick != null && message.sender != null) {
                        addLink(androidx.compose.ui.text.LinkAnnotation.Clickable("chatter",
                            styles = chatterLinkStyles(androidx.compose.ui.text.SpanStyle(color = effectiveSenderColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)),
                            linkInteractionListener = {
                                onUserClick(message.sender)
                            }), start, start + senderName.length)
                    }
                },
                fontStyle = FontStyle.Normal,
                inlineContent = parsedMessage.inlineContent,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp + 5).sp,
                color = effectiveBodyTextColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private const val MIN_BROADCASTER_BODY_CONTRAST = 7.0
private const val MIN_BROADCASTER_SENDER_CONTRAST = 4.5

internal data class BroadcasterMessagePalette(
    val backgroundColor: Color,
    val senderColor: Color,
    val contentColor: Color,
    val mutedColor: Color
)

internal fun resolveBroadcasterMessagePalette(identityColor: Color): BroadcasterMessagePalette {
    val baseArgb = identityColor.toArgb()
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(baseArgb, hsl)

    val bgHsl = floatArrayOf(
        hsl[0],
        if (hsl[1] < 0.08f) hsl[1] else (hsl[1] * 0.78f).coerceIn(0.42f, 0.75f),
        0.20f
    )
    var bgArgb = ColorUtils.HSLToColor(bgHsl)

    val bodyTextArgb = android.graphics.Color.rgb(245, 245, 247)
    while (ColorUtils.calculateContrast(bodyTextArgb, bgArgb) < MIN_BROADCASTER_BODY_CONTRAST && bgHsl[2] > 0.12f) {
        bgHsl[2] = (bgHsl[2] - 0.01f).coerceAtLeast(0.12f)
        bgArgb = ColorUtils.HSLToColor(bgHsl)
    }

    val backgroundColor = Color(bgArgb)
    val senderColor = ensureReadableColorOnBackground(identityColor, backgroundColor)
    val contentColor = Color(bodyTextArgb)

    return BroadcasterMessagePalette(
        backgroundColor = backgroundColor,
        senderColor = senderColor,
        contentColor = contentColor,
        mutedColor = contentColor.copy(alpha = 0.80f)
    )
}

private fun ensureReadableColorOnBackground(
    foregroundColor: Color,
    backgroundColor: Color,
    minContrast: Double = MIN_BROADCASTER_SENDER_CONTRAST
): Color {
    var fgArgb = foregroundColor.toArgb()
    val bgArgb = backgroundColor.toArgb()
    if (ColorUtils.calculateContrast(fgArgb, bgArgb) >= minContrast) {
        return foregroundColor
    }
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(fgArgb, hsl)
    if (hsl[1] > 0.08f) {
        hsl[1] = hsl[1].coerceAtLeast(0.55f)
    }
    while (ColorUtils.calculateContrast(fgArgb, bgArgb) < minContrast && hsl[2] < 0.92f) {
        hsl[2] = (hsl[2] + 0.03f).coerceAtMost(0.92f)
        fgArgb = ColorUtils.HSLToColor(hsl)
    }
    return Color(fgArgb)
}

/**
 * Reusable compact base layout for chat events (Reward Redeemed, Gifted Subscriptions, New Subscriptions, etc.).
 */
@Composable
fun ChatEventBaseItem(
    @DrawableRes iconRes: Int,
    segments: List<ChatTextParser.EventTextSegment>,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 14,
    bodyContent: String? = null,
    stripeColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: Dp = 6.dp,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    streamerUsername: String? = null,
    streamerSlug: String? = null,
    onUsernameClick: ((String) -> Unit)? = null
) {
    val boldColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val headerFontSizeSp = (fontSizeSp - 1).coerceAtLeast(11)
    val stripeWidth = 4.dp

    val parsedHeader = ChatTextParser.parseEventHeader(
        iconRes = iconRes,
        iconTint = iconTint,
        segments = segments,
        boldColor = boldColor,
        mutedColor = mutedColor,
        fontSizeSp = headerFontSizeSp,
        onUsernameClick = onUsernameClick
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .drawBehind {
                val strokeWidthPx = stripeWidth.toPx()
                drawRect(
                    color = stripeColor,
                    topLeft = Offset.Zero,
                    size = Size(width = strokeWidthPx, height = size.height)
                )
            }
            .padding(start = stripeWidth)
            .padding(contentPadding)
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = parsedHeader.annotatedString,
            inlineContent = parsedHeader.inlineContent,
            fontSize = headerFontSizeSp.sp,
            lineHeight = (headerFontSizeSp + 4).sp,
            color = mutedColor,
            modifier = Modifier.fillMaxWidth()
        )

        if (!bodyContent.isNullOrBlank()) {
            val hasStreamerMention = remember(bodyContent, streamerUsername, streamerSlug) {
                ChatTextParser.containsStreamerMention(bodyContent, streamerUsername, streamerSlug)
            }
            val (streamerMentionBrush, streamerMentionShadow) = if (hasStreamerMention) {
                rememberStreamerMentionEffects(fontSizeSp = fontSizeSp)
            } else {
                null to null
            }

            val parsedBody = ChatTextParser.parseContentOnly(
                content = bodyContent,
                fontSizeSp = fontSizeSp,
                streamerUsername = streamerUsername,
                streamerSlug = streamerSlug,
                streamerMentionBrush = streamerMentionBrush,
                streamerMentionShadow = streamerMentionShadow
            )

            ChatClipLinks(parsedBody.annotatedString) {
                Text(
                    text = parsedBody.annotatedString.withMentionLinks(onUsernameClick),
                    inlineContent = parsedBody.inlineContent,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp + 5).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun rememberStreamerMentionEffects(fontSizeSp: Int): Pair<Brush, Shadow> {
    if (!LocalChatContentActive.current) {
        return Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)) to Shadow.None
    }
    val spanWidthPx = with(LocalDensity.current) { (fontSizeSp * 3.5f).sp.toPx() }
    val doubleSpanWidthPx = spanWidthPx * 2f

    val transition = rememberInfiniteTransition(label = "streamer_mention_glow")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = doubleSpanWidthPx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "streamer_mention_offset"
    )

    val glowRadius by transition.animateFloat(
        initialValue = 12f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "streamer_mention_blur"
    )

    val gradientColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )

    val brush = Brush.linearGradient(
        colors = gradientColors,
        start = Offset(offset, offset),
        end = Offset(offset + spanWidthPx, offset + spanWidthPx),
        tileMode = TileMode.Mirror
    )

    val shadow = Shadow(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        offset = Offset.Zero,
        blurRadius = glowRadius
    )

    return brush to shadow
}

