package com.lechixy.kick.util

import java.util.Locale

/** Normalize a locale to its two-letter base language for existing preferences. */
fun kickLanguageCode(value: String): String? {
    val language = Locale.forLanguageTag(value.trim().replace('_', '-')).language.lowercase(Locale.ROOT)
    val canonical = when (language) { "iw" -> "he"; "in" -> "id"; "ji" -> "yi"; else -> language }
    return canonical.takeIf { it.length == 2 && it in Locale.getISOLanguages() }
}

internal fun preferredStreamLanguageCode(value: String): String? {
    val tag = Locale.forLanguageTag(value.trim().replace('_', '-')).toLanguageTag()
    return when (tag) {
        "ast", "en-IN", "fil", "mo" -> tag
        else -> kickLanguageCode(tag)
    }
}

fun recommendedStreamLanguage(locale: Locale): String {
    val regional = when (locale.country.uppercase(Locale.ROOT)) {
        "TR" -> "tr"
        "US", "GB", "AU", "NZ", "IE" -> "en"
        "DE", "AT" -> "de"
        "FR" -> "fr"
        "ES", "MX", "AR", "CL", "CO", "PE", "VE", "EC", "UY", "BO", "PY", "CR", "PA", "DO", "GT" -> "es"
        "PT", "BR" -> "pt"
        "IT" -> "it"
        "NL" -> "nl"
        "PL" -> "pl"
        "RU", "BY" -> "ru"
        "UA" -> "uk"
        "JP" -> "ja"
        "KR" -> "ko"
        "CN", "TW", "HK" -> "zh"
        "ID" -> "id"
        "TH" -> "th"
        "VN" -> "vi"
        "IL" -> "he"
        "SA", "AE", "EG", "IQ", "JO", "KW", "QA", "OM", "BH", "LB" -> "ar"
        "IR" -> "fa"
        "SE" -> "sv"
        "NO" -> "no"
        "DK" -> "da"
        "FI" -> "fi"
        "GR", "CY" -> "el"
        "CZ" -> "cs"
        "SK" -> "sk"
        "HU" -> "hu"
        "RO", "MD" -> "ro"
        "BG" -> "bg"
        "HR" -> "hr"
        "RS" -> "sr"
        "CA" -> if (locale.language == "fr") "fr" else "en"
        "CH" -> locale.language.takeIf { it in setOf("de", "fr", "it", "rm") } ?: "de"
        "BE" -> locale.language.takeIf { it in setOf("nl", "fr", "de") } ?: "nl"
        else -> locale.language
    }
    return kickLanguageCode(regional) ?: "en"
}

internal fun systemRecommendedStreamLanguage(): String {
    val locales = android.content.res.Resources.getSystem().configuration.locales
    return recommendedStreamLanguage(if (locales.isEmpty) Locale.getDefault() else locales[0])
}
