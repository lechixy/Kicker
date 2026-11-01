package com.lechixy.kick.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

private class SkeletonShimmer(val progress: State<Float>) {
    var origin by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(Size.Zero)
}

private val LocalSkeletonShimmer = staticCompositionLocalOf<SkeletonShimmer?> { null }

/** One clock and coordinate space for every placeholder, including lazy items. */
@Composable
fun SkeletonLoadingScope(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shimmer = if (active) {
        val transition = rememberInfiniteTransition(label = "screen_skeleton")
        val progress = transition.animateFloat(
            initialValue = -0.4f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
            label = "diagonal_light"
        )
        remember(progress) { SkeletonShimmer(progress) }
    } else null
    CompositionLocalProvider(LocalSkeletonShimmer provides shimmer) {
        Box(modifier.onGloballyPositioned {
            shimmer?.origin = it.positionInRoot()
            shimmer?.size = Size(it.size.width.toFloat(), it.size.height.toFloat())
        }) { content() }
    }
}

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp)
) {
    val shimmer = LocalSkeletonShimmer.current
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = Color.White.copy(alpha = if (base.luminance() > 0.5f) 0.6f else 0.12f)
    var position by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.clearAndSetSemantics { }.clip(shape).background(base)
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawWithContent {
            drawContent()
            if (shimmer != null && shimmer.size != Size.Zero) {
                // Project x + y onto a shared diagonal; local card sizes never restart the beam.
                val extent = shimmer.size.width + shimmer.size.height
                val center = extent * shimmer.progress.value
                val halfWidth = extent * 0.14f
                val localOrigin = shimmer.origin - position
                drawRect(Brush.linearGradient(
                    colors = listOf(Color.Transparent, highlight, Color.Transparent),
                    start = localOrigin + Offset((center - halfWidth) / 2f, (center - halfWidth) / 2f),
                    end = localOrigin + Offset((center + halfWidth) / 2f, (center + halfWidth) / 2f)
                ))
            }
        })
}

@Composable
internal fun MediaCardSkeleton(modifier: Modifier, withAvatar: Boolean) {
    Column(modifier.fillMaxWidth().clearAndSetSemantics { }
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(0.dp))
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (withAvatar) SkeletonBlock(Modifier.size(40.dp), CircleShape)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(16.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(12.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.35f).height(10.dp))
            }
        }
    }
}

/** Matches the compact, horizontal search result card. */
@Composable
fun BrowseStreamCardSkeleton(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        SkeletonBlock(Modifier.width(120.dp).aspectRatio(16f / 9f), RoundedCornerShape(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(16.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoSkeletonGrid(modifier: Modifier = Modifier) {
    CardSection<Unit>(title = null, data = emptyList(), modifier = modifier,
        loading = true, skeleton = { VideoCardSkeleton() }) { }
}
