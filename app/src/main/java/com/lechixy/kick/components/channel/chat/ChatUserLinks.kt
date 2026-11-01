package com.lechixy.kick.components.channel.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

internal const val CHAT_MENTION_TAG = "chat-mention"

// Override Material's URL defaults while retaining identity colors and streamer mention brushes.
internal fun chatterLinkStyles(style: SpanStyle): TextLinkStyles {
    val unchangedStyle = style.copy(textDecoration = TextDecoration.None)
    return TextLinkStyles(style = unchangedStyle, focusedStyle = unchangedStyle,
        hoveredStyle = unchangedStyle, pressedStyle = unchangedStyle)
}

internal fun AnnotatedString.withMentionLinks(onUserClick: ((String) -> Unit)?): AnnotatedString {
    if (onUserClick == null) return this
    val source = this
    return buildAnnotatedString {
        append(source)
        source.getStringAnnotations(CHAT_MENTION_TAG, 0, source.length).forEach { mention ->
            val style = source.spanStyles.filter { it.start <= mention.start && it.end >= mention.end }
                .fold(SpanStyle()) { result, span -> result.merge(span.item) }
            addLink(LinkAnnotation.Clickable("mention:${mention.item}", styles = chatterLinkStyles(style),
                linkInteractionListener = { onUserClick(mention.item) }), mention.start, mention.end)
        }
    }
}
