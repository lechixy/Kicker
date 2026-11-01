package com.lechixy.kick.components.account

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder

import com.lechixy.kick.R

@Composable
internal fun RewardAvailability(available: Boolean) {
    val context = LocalContext.current
    val loader = remember(context) { ImageLoader.Builder(context).components { add(AnimatedImageDecoder.Factory()) }.build() }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    if (available) AsyncImage(model = R.drawable.reward_available, imageLoader = loader, contentDescription = null,
        modifier = Modifier.size(40.dp))
    else Icon(painterResource(R.drawable.reward_case), stringResource(R.string.challenges_title))
}
