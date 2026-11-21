package com.lechixy.kick.components.channel.chat

import android.content.Context
import android.icu.text.ListFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.lechixy.kick.components.channel.StripedProgressBar
import com.lechixy.kick.data.channel.model.KickGoalDto
import com.lechixy.kick.util.FormatUtils
import androidx.compose.ui.graphics.Color
import com.lechixy.kick.R
import com.lechixy.kick.data.chat.model.GiftedSubscriptionsEvent
import java.text.NumberFormat
import java.util.Locale

private val eventArgument = Regex("%(?:(\\d+)[$]s|%)")

/** Positional resource arguments retain their styling and links even when a translation reorders them. */
internal fun eventTextSegments(
    template: String,
    vararg arguments: List<ChatTextParser.EventTextSegment>
): List<ChatTextParser.EventTextSegment> = buildList {
    var end = 0
    eventArgument.findAll(template).forEach { match ->
        if (match.range.first > end) add(ChatTextParser.EventTextSegment(template.substring(end, match.range.first)))
        if (match.value == "%%") add(ChatTextParser.EventTextSegment("%"))
        else addAll(arguments[match.groupValues[1].toInt() - 1])
        end = match.range.last + 1
    }
    if (end < template.length) add(ChatTextParser.EventTextSegment(template.substring(end)))
}

internal fun eventUser(name: String, color: Color, username: String? = name) =
    listOf(ChatTextParser.EventTextSegment(name, isBold = true, color = color, username = username))

internal fun Context.eventLocale(): Locale = resources.configuration.locales[0]

private fun Context.eventCount(count: Int, color: Color) =
    eventUser(NumberFormat.getIntegerInstance(eventLocale()).format(count), color, username = null)

internal fun subscriptionEventSegments(
    context: Context,
    username: String,
    months: Int?,
    extraSubscribers: Int = 0,
    userColor: Color,
    primaryColor: Color
): List<ChatTextParser.EventTextSegment> {
    val user = eventUser(username.ifBlank { context.getString(R.string.chat_event_someone) }, userColor,
        username.takeIf { it.isNotBlank() })
    return when {
        months != null && months > 0 -> {
            val template = if (months == 1) context.getString(R.string.chat_event_subscribed_month)
                else context.getString(R.string.chat_event_subscribed_months)
            eventTextSegments(template, user, context.eventCount(months, primaryColor))
        }
        extraSubscribers > 0 -> {
            val template = if (extraSubscribers == 1) context.getString(R.string.chat_event_group_subscribed_single)
                else context.getString(R.string.chat_event_group_subscribed)
            eventTextSegments(template, user, context.eventCount(extraSubscribers, primaryColor))
        }
        else -> eventTextSegments(context.getString(R.string.chat_event_subscribed), user)
    }
}

internal fun celebrationEventSegments(
    context: Context,
    username: String,
    months: Int?,
    userColor: Color,
    primaryColor: Color
): List<ChatTextParser.EventTextSegment> {
    if (months == null || months <= 0) {
        return subscriptionEventSegments(context, username, months, userColor = userColor, primaryColor = primaryColor)
    }
    val template = context.getString(if (months == 1) R.string.chat_event_celebrates_month
        else R.string.chat_event_celebrates_months)
    return eventTextSegments(template, eventUser(username, userColor), context.eventCount(months, primaryColor))
}

internal fun giftedEventSegments(
    context: Context,
    event: GiftedSubscriptionsEvent,
    userColors: Map<String, String>,
    primaryColor: Color
): List<ChatTextParser.EventTextSegment> {
    val recipients = event.giftedUsernames.filter { it.isNotBlank() }
    val count = event.giftedTotal?.takeIf { it > 0 } ?: recipients.size.coerceAtLeast(1)
    fun user(name: String) = eventUser(name,
        ChatTextParser.parseHexColor(userColors[name.lowercase(Locale.ROOT)], primaryColor))
    val gifter = if (event.gifterUsername.isBlank())
        eventUser(context.getString(R.string.chat_event_anonymous), primaryColor, username = null)
        else user(event.gifterUsername)
    val result = if (recipients.isNotEmpty() && count < 5) {
        val recipientPattern = ListFormatter.getInstance(context.eventLocale())
            .format(recipients.indices.map { $$"%$${it + 1}$s" })
        val recipientSegments = eventTextSegments(recipientPattern, *recipients.map(::user).toTypedArray())
        val template = if (count == 1) context.getString(R.string.chat_event_gifted_to_single)
            else context.getString(R.string.chat_event_gifted_to)
        eventTextSegments(template, gifter, context.eventCount(count, primaryColor), recipientSegments)
    } else {
        val template = if (count == 1) context.getString(R.string.chat_event_gifted_community_single)
            else context.getString(R.string.chat_event_gifted_community)
        eventTextSegments(template, gifter, context.eventCount(count, primaryColor))
    }
    val total = event.gifterTotal?.takeIf { it > 0 && (recipients.isEmpty() || count >= 5) } ?: return result
    val totalTemplate = context.getString(if (total == 1) R.string.chat_event_gifted_channel_total_single
        else R.string.chat_event_gifted_channel_total)
    return eventTextSegments(totalTemplate, result, context.eventCount(total, primaryColor))
}

internal fun raidEventSegments(context: Context, username: String, count: Int, color: Color) =
    eventTextSegments(context.resources.getString(R.string.chat_event_raid),
        eventUser(username, color), context.eventCount(count, color))


internal fun kicksGiftedSegments(
    context: Context,
    event: com.lechixy.kick.data.chat.model.KicksGiftedEvent,
    accent: Color
) = eventTextSegments(
    context.getString(R.string.chat_event_kicks_gifted),
    eventUser(event.sender.username, ChatTextParser.parseHexColor(event.sender.usernameColor, accent)),
    context.eventCount(event.gift.amount, accent),
    eventUser(event.gift.name, accent, username = null)
)


@Composable
internal fun ChatSubscriptionGoalProgress(goal: KickGoalDto) {
    if (goal.targetValue <= 0 || goal.status !in listOf("active", "achieved")) return
    val locale = LocalConfiguration.current.locales[0]
    val current = FormatUtils.formatCompactCount(goal.currentValue.toLong(), locale)
    val target = FormatUtils.formatCompactCount(goal.targetValue.toLong(), locale)
    val progress = (goal.currentValue.toFloat() / goal.targetValue).coerceIn(0f, 1f)
    val animated = LocalChatContentActive.current
    Column(Modifier.width(78.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$current / $target", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        StripedProgressBar(progress, Modifier.fillMaxWidth().height(5.dp), animated = animated)
    }
}
