package com.lechixy.kick.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.lechixy.kick.data.common.remote.KickNetwork
import kotlinx.coroutines.CancellationException
import java.text.Collator
import java.util.Locale

private val fallbackLanguageCodes = listOf(
    "af", "an", "ar", "ast", "az", "be", "bg", "bs", "ca", "cs", "da", "de", "el", "en",
    "en-IN", "es", "eu", "fa", "fi", "fil", "fr", "he", "hi", "hr", "hu", "hy", "id", "it",
    "ja", "ko", "la", "lt", "lv", "ml", "mn", "mr", "ne", "nl", "no", "pa", "pl", "pt",
    "qu", "ro", "ru", "si", "sk", "sq", "sr", "sv", "ta", "te", "th", "tr", "uk", "ur", "zh", "zu",
)

@Composable
internal fun rememberStreamLanguageCodes(): State<List<String>> =
    produceState(fallbackLanguageCodes) {
        try {
            val codes = KickNetwork.api.getStreamLanguageCodes().data
            if (codes.isNotEmpty()) value = codes
        } catch (error: Exception) {
            if (error is CancellationException) throw error
        }
    }

internal fun streamLanguageOptions(codes: List<String>): List<Pair<String, String>> {
    val collator = Collator.getInstance(Locale.ROOT)
    return codes.distinct().map { it to streamLanguageName(it) }
        .sortedWith { first, second -> collator.compare(first.second, second.second) }
}

internal fun streamLanguageName(code: String): String = when (code) {
    "an" -> "Aragonés"
    "ast" -> "Asturianu"
    "la" -> "Latina"
    "mo" -> "Română (Moldova)"
    "qu" -> "Runasimi"
    else -> {
        val locale = Locale.forLanguageTag(code)
        locale.getDisplayName(locale).ifBlank { code }.replaceFirstChar { it.titlecase(locale) }
    }
}
