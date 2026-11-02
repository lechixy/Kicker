package com.lechixy.kick.components.channel.chat

import android.content.Context
import android.icu.text.ListFormatter
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
//    return event.gifterTotal?.takeIf { it > 1 }?.let { total ->
//        eventTextSegments(context.getString(R.string.chat_event_gifted_total), result, context.eventCount(total, primaryColor))
//    } ?: result
    return result
}

internal fun raidEventSegments(context: Context, username: String, count: Int, color: Color) =
    eventTextSegments(context.resources.getString(R.string.chat_event_raid),
        eventUser(username, color), context.eventCount(count, color))
