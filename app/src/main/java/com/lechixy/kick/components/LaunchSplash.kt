package com.lechixy.kick.components

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.lechixy.kick.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Keep the real app composed underneath so startup work continues during the GIF. */
@Composable
internal fun LaunchSplashHost(
    visible: Boolean,
    appReady: Boolean,
    playAnimation: Boolean,
    onFinished: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Box(if (visible) Modifier.clearAndSetSemantics {} else Modifier) { content() }
        if (visible) {
            var animationFinished by remember { mutableStateOf(false) }
            var timedOut by remember { mutableStateOf(false) }
            val alpha = remember { Animatable(1f) }
            val finish by rememberUpdatedState(onFinished)

            // A corrupt asset or interrupted decoder must never trap the user at launch.
            LaunchedEffect(Unit) {
                delay(10_000)
                timedOut = true
            }
            LaunchedEffect(animationFinished, appReady, timedOut) {
                if ((animationFinished && appReady) || timedOut) {
                    alpha.animateTo(0f, tween(200))
                    finish()
                }
            }
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { this.alpha = alpha.value }
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
            ) {
                LaunchGif(playAnimation) { animationFinished = true }
            }
        }
    }
}

@Composable
private fun LaunchGif(playAnimation: Boolean, onFinished: () -> Unit) {
    val resources = LocalContext.current.resources
    val tablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val resource = if (tablet) R.drawable.splash_tablet else R.drawable.splash_mobile
    var drawable by remember(resource) { mutableStateOf<AnimatedImageDrawable?>(null) }
    var completed by remember(resource) { mutableStateOf(false) }
    val finish by rememberUpdatedState(onFinished)
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()

    LaunchedEffect(resource) {
        try {
            drawable = withContext(Dispatchers.IO) {
                (ImageDecoder.decodeDrawable(ImageDecoder.createSource(resources, resource))
                    as AnimatedImageDrawable).apply { repeatCount = 0 }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            finish()
        }
    }
    DisposableEffect(drawable, playAnimation, lifecycleState) {
        val image = drawable
        val callback = object : Animatable2.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) {
                completed = true
                finish()
            }
        }
        image?.registerAnimationCallback(callback)
        if (!completed && playAnimation && lifecycleState.isAtLeast(Lifecycle.State.STARTED)) image?.start()
        onDispose {
            image?.unregisterAnimationCallback(callback)
            image?.stop()
        }
    }
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view -> if (view.drawable !== drawable) view.setImageDrawable(drawable) }
    )
}
