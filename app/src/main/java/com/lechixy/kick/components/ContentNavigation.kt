package com.lechixy.kick.components

import androidx.compose.runtime.staticCompositionLocalOf

data class ContentNavigation(val search: (String) -> Unit = {}, val category: (String) -> Unit = {},
    val channel: (String) -> Unit = {},
    val recording: (String, com.lechixy.kick.data.player.model.RecordedPlayback, ExpansionSource?) -> Unit = { _, _, _ -> })
val LocalContentNavigation = staticCompositionLocalOf { ContentNavigation() }

val LocalSharedTopBar = staticCompositionLocalOf { false }
