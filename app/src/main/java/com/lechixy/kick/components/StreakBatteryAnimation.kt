package com.lechixy.kick.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder

internal const val STREAK_BATTERY_ANIMATION_ASSET = "file:///android_asset/rewards/streaks-battery.webp"

fun streakAnimationAsset(): String = STREAK_BATTERY_ANIMATION_ASSET

@Composable
fun StreakBatteryAnimation(
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader? = null,
    contentDescription: String? = null
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    if (!lifecycleState.isAtLeast(Lifecycle.State.STARTED)) return
    val context = LocalContext.current
    val resolvedLoader = imageLoader ?: remember(context) {
        ImageLoader.Builder(context).components { add(AnimatedImageDecoder.Factory()) }.build()
    }
    if (imageLoader == null) {
        DisposableEffect(resolvedLoader) { onDispose { resolvedLoader.shutdown() } }
    }
    AsyncImage(
        model = STREAK_BATTERY_ANIMATION_ASSET,
        imageLoader = resolvedLoader,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}
