package com.lechixy.kick.data.chat.model

import java.util.Locale

enum class CompletionKind { MENTION, COMMAND, EMOTE }
data class ChatCompletionToken(val kind: CompletionKind, val query: String, val start: Int, val end: Int)

fun chatCompletionToken(text: String, cursor: Int): ChatCompletionToken? {
    val caret = cursor.coerceIn(0, text.length)
    val start = text.substring(0, caret).indexOfLast { it.isWhitespace() } + 1
    if (start >= caret) return null
    val kind = when (text[start]) {
        '@' -> CompletionKind.MENTION
        ':' -> CompletionKind.EMOTE
        '/' -> if (start == 0) CompletionKind.COMMAND else return null
        else -> return null
    }
    val query = text.substring(start + 1, caret)
    if (query.any { it in "[]:@/" }) return null
    val end = text.indexOfFirstFrom(caret) { it.isWhitespace() }
    return ChatCompletionToken(kind, query, start, end)
}
private inline fun String.indexOfFirstFrom(start: Int, predicate: (Char) -> Boolean): Int {
    for (i in start until length) if (predicate(this[i])) return i
    return length
}
fun insertCompletion(text: String, token: ChatCompletionToken, value: String): EmoteInsertion {
    val suffix = if (token.end < text.length && text[token.end].isWhitespace()) "" else " "
    val insertion = value + suffix
    val cursor = token.start + insertion.length + if (suffix.isEmpty()) 1 else 0
    return EmoteInsertion(text.replaceRange(token.start, token.end, insertion), cursor)
}
fun mentionMatches(users: List<ActiveChatter>, query: String): List<ActiveChatter> = users
    .filter { it.username.isNotBlank() && it.username.contains(query, ignoreCase = true) }
    .distinctBy { it.username.lowercase(Locale.ROOT) }
    .sortedWith(compareByDescending<ActiveChatter> { it.username.startsWith(query, ignoreCase = true) }
        .thenBy { it.username.lowercase(Locale.ROOT) }).take(12)
