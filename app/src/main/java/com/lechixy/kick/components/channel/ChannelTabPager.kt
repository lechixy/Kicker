package com.lechixy.kick.components.channel

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier

import com.lechixy.kick.screens.channel.ChannelTab

/** Short drags change tabs; quick flicks follow the gesture direction. */
@Composable
internal fun ChannelTabPager(state: PagerState, tabs: List<ChannelTab>, modifier: Modifier = Modifier,
    content: @Composable (ChannelTab) -> Unit) {
    // Keep the lazy item provider paired with its state and tab list when rotation
    // removes the chat tab. Reusing it can query the new list with the old page count.
    key(state, tabs) {
        HorizontalPager(state = state, modifier = modifier, beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(state, snapPositionalThreshold = .2f),
            key = { tabs[it].name }) { content(tabs[it]) }
    }
}
