package com.lechixy.kick.screens.events

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lechixy.kick.data.channel.model.ChannelEvent
import kotlinx.coroutines.launch

private const val FeaturedEventDurationNanos = 10_000_000_000L

@Composable
internal fun FeaturedEventsCarousel(
    events: List<ChannelEvent>,
    modifier: Modifier = Modifier,
    onClick: (ChannelEvent) -> Unit,
) {
    if (events.isEmpty()) return
    if (events.size == 1) {
        val event = events.single()
        FeaturedEventHeroCard(event, modifier) { onClick(event) }
        return
    }

    // Each end includes the neighboring event so the wrap uses the same slide as other pages.
    val pager = rememberPagerState(initialPage = 1) { events.size + 2 }
    val page = pager.settledPage
    val selected = Math.floorMod(pager.currentPage - 1, events.size)
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(page, dragged, lifecycle, events) {
        progress = 0f
        if (dragged || lifecycle != Lifecycle.State.RESUMED) return@LaunchedEffect
        when (page) {
            0 -> pager.scrollToPage(events.size)
            events.size + 1 -> pager.scrollToPage(1)
            in 1..events.size -> {
                val start = withFrameNanos { it }
                while (progress < 1f) {
                    progress = withFrameNanos { ((it - start).toFloat() / FeaturedEventDurationNanos).coerceIn(0f, 1f) }
                }
                pager.animateScrollToPage(page + 1, animationSpec = tween(520))
            }
        }
    }

    Box(modifier.animateContentSize()) {
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxWidth()) { index ->
            val event = events[Math.floorMod(index - 1, events.size)]
            FeaturedEventHeroCard(event, Modifier.fillMaxWidth(), bottomInset = 28.dp) { onClick(event) }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 6.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = 0.42f))
                .horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            events.forEachIndexed { index, event ->
                val active = selected == index
                val width by animateDpAsState(if (active) 56.dp else 22.dp, tween(220), label = "page indicator width")
                val label = event.title?.takeIf(String::isNotBlank) ?: (index + 1).toString()
                Box(
                    modifier = Modifier.width(width).height(32.dp)
                        .selectable(selected = active, role = Role.Tab, onClick = {
                            scope.launch { pager.animateScrollToPage(index + 1, animationSpec = tween(420)) }
                        })
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.padding(horizontal = 7.dp).fillMaxWidth().height(7.dp).clip(CircleShape)) {
                        drawRect(Color.White.copy(alpha = if (active) 0.48f else 0.35f))
                        if (active && page == pager.currentPage) {
                            drawRoundRect(
                                Color.White,
                                size = Size(size.width * progress, size.height),
                                cornerRadius = CornerRadius(size.height / 2f),
                            )
                        }
                    }
                }
            }
        }
    }
}
