package com.lechixy.kick.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Connected animation (Matched Geometry / Zoom Transition) için kartın ekran
 * üzerindeki başlangıç koordinatları ve görsel snapshot verilerini taşır.
 */
data class ExpansionSource(
    val cardRect: Rect,
    val mediaRect: Rect? = null,
    val cornerRadius: Dp = 12.dp,
    val thumbnailUrl: String? = null,
    val avatarUrl: String? = null,
    val title: String? = null,
    val username: String? = null,
    val category: String? = null,
    val viewerCount: Int? = null,
    val isLive: Boolean = false,
    val isStreamCard: Boolean = true
)
