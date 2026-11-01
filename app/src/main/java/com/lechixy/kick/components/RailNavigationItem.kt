package com.lechixy.kick.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Keeps the icon in one composition while both rail sections change layout together. */
@Composable
internal fun RailNavigationItem(
    expanded: Boolean,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = label,
    supportingText: String? = null,
    icon: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(if (expanded) 1f else 0f,
        tween(320, easing = FastOutSlowInEasing), label = "rail expansion")
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val iconScale by animateFloatAsState(if (pressed) .92f else 1f,
        tween(140), label = "rail press")
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        tween(180), label = "rail selection")
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val hasSupportingText = !supportingText.isNullOrBlank()
    val isChannel = supportingText != null
    val expandedTextStart = if (isChannel) 80f else 68f
    val textWidth = with(density) { (240f - expandedTextStart - 36f).dp.roundToPx() }
    val labelSize = textMeasurer.measure(label, style = MaterialTheme.typography.labelLarge,
        maxLines = 1, softWrap = false, constraints = Constraints(maxWidth = textWidth))
    val supportingSize = if (hasSupportingText) textMeasurer.measure(supportingText.orEmpty(),
        style = MaterialTheme.typography.bodySmall, maxLines = 2,
        overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = textWidth)) else null
    val textHeight = with(density) { (labelSize.size.height + (supportingSize?.size?.height ?: 0)).toDp().value } +
        if (hasSupportingText) 4f else 0f
    val collapsedLabelHeight = with(density) { MaterialTheme.typography.labelMedium.lineHeight.toDp().value }
    // M3: 64 dp destination, 56 x 32 dp indicator, 4 dp indicator-to-label gap.
    // Avatars need a 40 dp indicator rather than the destination icon's 32 dp.
    val collapsedIndicatorHeight = if (isChannel) 40f else 32f
    val collapsedHeight = maxOf(64f, collapsedIndicatorHeight + 4f + collapsedLabelHeight + 8f)
    val expandedHeight = maxOf(56f, textHeight + 16f)
    val height = collapsedHeight + (expandedHeight - collapsedHeight) * progress
    val collapsedIndicatorTop = (collapsedHeight - collapsedIndicatorHeight - 4f - collapsedLabelHeight) / 2f
    val collapsedIconCenterY = collapsedIndicatorTop + collapsedIndicatorHeight / 2f
    val iconCenterY = collapsedIconCenterY + (height / 2f - collapsedIconCenterY) * progress
    val indicatorHeight = collapsedIndicatorHeight + (height - collapsedIndicatorHeight) * progress
    Box(modifier.fillMaxWidth().height(height.dp).clipToBounds()
        .selectable(selected, role = Role.Tab, interactionSource = interactions,
            indication = null, onClick = onClick)
        .semantics(mergeDescendants = true) {
            contentDescription = description
            text = androidx.compose.ui.text.AnnotatedString(label)
        }) {
        Box(Modifier.offset(x = (12f + 8f * progress).dp,
            y = (iconCenterY - indicatorHeight / 2).dp)
            .size((56f + 144f * progress).dp, indicatorHeight.dp)
            .clip(RoundedCornerShape(50)).background(color)
            .indication(interactions, androidx.compose.material3.ripple()))
        Box(Modifier.offset(x = (20f + (if (isChannel) 14f else 8f) * progress).dp, y = (iconCenterY - 20f).dp)
            .size(40.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
            contentAlignment = Alignment.Center) { icon() }
        Text(label, Modifier.offset(y = (collapsedIndicatorTop + collapsedIndicatorHeight + 4f).dp).width(80.dp).padding(horizontal = 4.dp)
            .graphicsLayer { alpha = (1f - progress * 3f).coerceIn(0f, 1f) }
            .clearAndSetSemantics {},
            style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        // Measure at the final text width, so letters do not reflow during expansion.
        Box(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(240.dp).fillMaxHeight()
            .padding(start = expandedTextStart.dp, end = 36.dp)
            .graphicsLayer { alpha = ((progress - .25f) / .75f).coerceIn(0f, 1f) }
            .clearAndSetSemantics {
                if (expanded && hasSupportingText) text = androidx.compose.ui.text.AnnotatedString(supportingText)
            }, contentAlignment = Alignment.CenterStart) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (supportingText != null) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                if (hasSupportingText) Text(supportingText.orEmpty(),
                    style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
