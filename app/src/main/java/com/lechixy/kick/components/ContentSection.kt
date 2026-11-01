package com.lechixy.kick.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R

/** Shared sizing for media, video and channel cards, including their placeholders. */
fun contentColumns(width: Dp): Int = if (width < 600.dp) 2 else (width / 280.dp).toInt().coerceAtLeast(2)

@Composable
fun SectionHeader(title: String, onMore: (() -> Unit)? = null, moreEnabled: Boolean = true, leadingIcon: Int? = null) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val actionable = onMore != null && moreEnabled
    val arrowOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(hovered, actionable) {
        if (hovered && actionable) {
            while (true) {
                arrowOffset.animateTo(4f, androidx.compose.animation.core.tween(360))
                arrowOffset.animateTo(0f, androidx.compose.animation.core.tween(360))
            }
        } else arrowOffset.animateTo(0f, androidx.compose.animation.core.tween(160))
    }
    val highlighted = actionable && (hovered || focused || pressed)
    val primary = MaterialTheme.colorScheme.primary
    val background by animateColorAsState(
        if (highlighted) primary.copy(alpha = if (pressed) .14f else .08f) else Color.Transparent,
        label = "section header background")
    val textColor by animateColorAsState(
        if (highlighted) primary else MaterialTheme.colorScheme.onSurface,
        label = "section header text")
    val moreLabel = stringResource(R.string.home_more)
    Row(Modifier.wrapContentWidth(Alignment.Start).heightIn(min = 48.dp)
        .clip(MaterialTheme.shapes.medium)
        .background(background)
        .then(if (onMore != null) Modifier.clickable(
            enabled = moreEnabled, interactionSource = interactions,
            indication = ripple(), role = Role.Button, onClickLabel = moreLabel, onClick = onMore
        ) else Modifier)
        .semantics { heading() }
        .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (leadingIcon != null) Icon(painterResource(leadingIcon), null, Modifier.size(24.dp), tint = primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = textColor, modifier = Modifier.weight(1f, fill = false))
        if (onMore != null) Icon(
            painterResource(R.drawable.arrow_forward_ios_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
            contentDescription = null, tint = primary.copy(alpha = if (moreEnabled) 1f else .38f),
            modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(arrowOffset.value.dp.roundToPx(), 0) }.size(18.dp))
    }
}

@Composable
fun ContentGrid(
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    content: LazyGridScope.(columns: Int) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = contentColumns(maxWidth - 32.dp)
        LazyVerticalGrid(columns = GridCells.Fixed(columns), state = state,
            modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) { content(columns) }
    }
}

fun <T> LazyGridScope.cardSection(
    key: String, title: String, data: List<T>, columns: Int,
    loading: Boolean = false, previewRows: Int? = null, onMore: (() -> Unit)? = null, itemSpan: Int = 1,
    itemKey: (T) -> Any,
    skeleton: @Composable () -> Unit,
    card: @Composable (T) -> Unit
) {
    item(key = "${key}_header", span = { GridItemSpan(maxLineSpan) }) { SectionHeader(title, onMore) }
    val rowColumns = (columns / itemSpan.coerceIn(1, columns)).coerceAtLeast(1)
    val count = if (previewRows != null) rowColumns * previewRows else data.size.takeIf { it > 0 } ?: (rowColumns * 3)
    if (loading) items(count, key = { "${key}_skeleton_$it" }, span = { GridItemSpan(itemSpan) }) { skeleton() }
    else items(if (previewRows != null) data.take(count) else data,
        key = { "${key}_${itemKey(it)}" }, span = { GridItemSpan(itemSpan) }) { card(it) }
}

/** For sections inside a vertically scrolling channel page; rows use the same sizing as ContentGrid. */
@Composable
fun <T> CardSection(title: String?, data: List<T>, modifier: Modifier = Modifier,
    previewRows: Int? = null, onMore: (() -> Unit)? = null,
    loading: Boolean = false, skeleton: @Composable () -> Unit = { VideoCardSkeleton() },
    card: @Composable (T) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = contentColumns(maxWidth)
        val count = if (loading) columns * (previewRows ?: 2) else
            data.size.coerceAtMost(previewRows?.times(columns) ?: Int.MAX_VALUE)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) SectionHeader(title, onMore)
            (0 until count).chunked(columns).forEach { indices ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(columns) { column ->
                        Box(Modifier.weight(1f)) {
                            indices.getOrNull(column)?.let { if (loading) skeleton() else card(data[it]) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContentState(
    message: String,
    onRetry: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    icon: Int = R.drawable.info_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(painterResource(icon), null,
                Modifier.padding(16.dp).size(28.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.chat_retry)) }
        action?.invoke()
    }
}
