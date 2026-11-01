package com.lechixy.kick.components.channel

import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss as dismissAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil3.compose.AsyncImage
import coil3.request.crossfade
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

import com.lechixy.kick.R
import com.lechixy.kick.components.NetworkInfoBar
import com.lechixy.kick.util.buildOptimizedImageRequest

/** The preview moves between the avatar bounds and the center; the source stays visible underneath. */
@Composable
fun ExpandableProfilePhoto(
    url: String?, username: String?, modifier: Modifier = Modifier,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var thumbnailPainter by remember(url) { mutableStateOf<Painter?>(null) }
    var expanded by remember(url) { mutableStateOf(false) }
    DisposableEffect(url) { onDispose { onExpandedChange(false) } }
    val expandLabel = stringResource(R.string.expand_profile_photo)
    val thumbnail = remember(url) {
        buildOptimizedImageRequest(context, url, targetWidthDp = 64, targetHeightDp = 64)
    }
    AsyncImage(
        model = thumbnail, contentDescription = username,
        onSuccess = { thumbnailPainter = it.painter },
        contentScale = ContentScale.Crop,
        modifier = modifier.onGloballyPositioned { bounds = it.boundsInWindow() }
            .clip(CircleShape)
            .combinedClickable(enabled = !url.isNullOrBlank(), onClick = {},
                onLongClickLabel = expandLabel, onLongClick = {
                    if (bounds.width > 0) {
                        expanded = true
                        onExpandedChange(true)
                    }
                })
    )
    if (expanded && !url.isNullOrBlank()) {
        ProfilePhotoPreview(url, username, bounds, thumbnailPainter, onDismissed = {
            expanded = false
            onExpandedChange(false)
        })
    }
}

@Composable
private fun ProfilePhotoPreview(url: String, username: String?, source: Rect, placeholder: Painter?, onDismissed: () -> Unit) {
    val context = LocalContext.current
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var dismissing by remember { mutableStateOf(false) }
    val dismiss = {
        if (!dismissing) {
            dismissing = true
            scope.launch {
                progress.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                onDismissed()
            }
        }
        Unit
    }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
    com.lechixy.kick.components.PredictiveDialog(onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setWindowAnimations(0)
            onDispose { }
        }
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f * progress.value))
            .clickable(interactionSource = null, indication = null, onClick = dismiss)
            .semantics { dismissAction { dismiss(); true } }) {
            val windowWidth = constraints.maxWidth.toFloat()
            val windowHeight = constraints.maxHeight.toFloat()
            val targetSize = min(min(windowWidth * 0.86f, windowHeight * 0.70f), with(density) { 480.dp.toPx() })
            val t = progress.value
            val widthPx = source.width + (targetSize - source.width) * t
            val heightPx = source.height + (targetSize - source.height) * t
            val x = source.left + ((windowWidth - targetSize) / 2 - source.left) * t
            val y = source.top + ((windowHeight - targetSize) / 2 - source.top) * t
            val radius = with(density) { (source.width / 2).toDp() } * (1 - t) + 24.dp * t
            AsyncImage(
                model = remember(url) {
                    buildOptimizedImageRequest(context, url, targetWidthDp = 720, targetHeightDp = 720)
                        .newBuilder().crossfade(false).build()
                },
                placeholder = placeholder, error = placeholder,
                contentDescription = username, contentScale = ContentScale.Crop,
                modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                    .clip(RoundedCornerShape(radius))
                    .clickable(interactionSource = null, indication = null, onClick = {})
            )
            NetworkInfoBar(Modifier.align(Alignment.BottomCenter))
        }
    }
}
