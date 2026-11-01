package com.lechixy.kick.components

import androidx.compose.runtime.staticCompositionLocalOf

data class ContentNavigation(val browse: (String) -> Unit = {}, val category: (String) -> Unit = {},
    val channel: (String) -> Unit = {})
val LocalContentNavigation = staticCompositionLocalOf { ContentNavigation() }

val LocalSharedTopBar = staticCompositionLocalOf { false }
