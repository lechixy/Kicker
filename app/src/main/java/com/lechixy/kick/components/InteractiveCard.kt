package com.lechixy.kick.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun InteractiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .3f),
    onClickLabel: String? = null,
    onDoubleClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(mediaZoom: Modifier) -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val emphasized = enabled && (hovered || focused)
    val down = enabled && pressed
    val pressScale by animateFloatAsState(if (down) .97f else 1f, tween(120), label = "card press")
    val mediaScale by animateFloatAsState(if (emphasized) 1.045f else 1f, tween(220), label = "card media zoom")
    val lift by animateDpAsState(if (emphasized) (-3).dp else 0.dp, tween(180), label = "card lift")
    val elevation by animateDpAsState(if (emphasized) 10.dp else 2.dp, tween(180), label = "card shadow")
    val outline by animateColorAsState(
        if (emphasized || down) MaterialTheme.colorScheme.primary.copy(alpha = .65f)
        else Color.White.copy(alpha = .08f),
        tween(180),
        label = "card outline",
    )
    val interactionModifier = if (onDoubleClick == null) {
        Modifier.clickable(
            interactionSource = interactions,
            indication = ripple(),
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier.combinedClickable(
            interactionSource = interactions,
            indication = ripple(),
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = Role.Button,
            onDoubleClick = onDoubleClick,
            onClick = onClick,
        )
    }
    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
            translationY = lift.toPx()
        },
        shape = RoundedCornerShape(12.dp),
        color = color,
        border = BorderStroke(1.dp, outline),
        shadowElevation = elevation,
    ) {
        Box(interactionModifier) {
            content(Modifier.graphicsLayer {
                scaleX = mediaScale
                scaleY = mediaScale
            })
        }
    }
}
