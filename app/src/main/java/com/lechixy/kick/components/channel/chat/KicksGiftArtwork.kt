package com.lechixy.kick.components.channel.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.request.ImageRequest

private val kickGiftTiers = listOf(1, 50, 100, 500, 1_000, 2_000, 5_000, 10_000, 50_000)

internal fun kicksGiftArtworkTier(amount: Int): Int? =
    kickGiftTiers.lastOrNull { it <= amount }

@Composable
internal fun KicksGiftArtwork(amount: Int) {
    val tier = kicksGiftArtworkTier(amount) ?: return
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    Box(Modifier.size(48.dp)) {
        if (LocalChatContentActive.current && lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
            val context = LocalContext.current
            val request = remember(context, tier) {
                ImageRequest.Builder(context)
                    .data("file:///android_asset/kicks/gifts/$tier-kick-bundle.webp")
                    .apply { if (tier >= 500) decoderFactory(AnimatedImageDecoder.Factory()) }
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
