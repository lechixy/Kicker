package com.lechixy.kick.data.settings.model

object MiniPlayerSize {
    const val TABLET_DEFAULT_WIDTH_DP = 360f
    const val MIN_WIDTH_DP = 220f
    const val MAX_WIDTH_DP = 720f

    fun defaultWidthDp(smallestScreenWidthDp: Int): Float =
        if (smallestScreenWidthDp in 1 until 600) 240f else TABLET_DEFAULT_WIDTH_DP
}
