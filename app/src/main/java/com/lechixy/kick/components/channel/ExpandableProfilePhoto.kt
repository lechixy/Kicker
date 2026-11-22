package com.lechixy.kick.components.channel

import android.graphics.Bitmap
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.size.Precision
import coil3.size.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

import com.lechixy.kick.R
import com.lechixy.kick.components.NetworkInfoBar
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository
import com.lechixy.kick.util.buildOptimizedImageRequest

/** The preview moves between the avatar bounds and the center; the source stays visible underneath. */
@Composable
fun ExpandableProfilePhoto(
    url: String?, username: String?, modifier: Modifier = Modifier,
    expandOnClick: Boolean = false,
    placeholder: Painter? = null,
    imagePadding: Dp = 0.dp,
    onTapLabel: String? = null,
    onTap: (() -> Unit)? = null,
    onExpandedChange: (Boolean) -> Unit = {},
    channelSlug: String? = null,
    fullSizeUrl: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var thumbnailPainter by remember(url) { mutableStateOf<Painter?>(null) }
    var expandedUrl by remember(username, channelSlug) { mutableStateOf<String?>(null) }
    DisposableEffect(username, channelSlug) { onDispose { onExpandedChange(false) } }
    val expandLabel = stringResource(R.string.expand_profile_photo)
    val hasPhoto = !url.isNullOrBlank()
    val thumbnail = remember(url) {
        buildOptimizedImageRequest(context, url, targetWidthDp = 64, targetHeightDp = 64)
    }
    val expand: () -> Unit = {
        if (hasPhoto && bounds.width > 0f && bounds.height > 0f) {
            expandedUrl = url
            onExpandedChange(true)
        }
    }
    AsyncImage(
        model = thumbnail, contentDescription = username,
        placeholder = placeholder, error = placeholder, fallback = placeholder,
        onSuccess = { thumbnailPainter = it.painter },
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape)
            .combinedClickable(
                enabled = hasPhoto || onTap != null,
                onClickLabel = onTapLabel ?: expandLabel.takeIf { expandOnClick },
                onClick = onTap ?: if (expandOnClick) expand else ({}),
                onLongClickLabel = expandLabel,
                onLongClick = expand.takeIf { hasPhoto },
            )
            .padding(imagePadding)
            .onGloballyPositioned { bounds = it.boundsInWindow().translate(view.windowOriginOnScreen()) }
            .clip(CircleShape)
    )
    expandedUrl?.let { previewUrl ->
        ProfilePhotoPreview(previewUrl, fullSizeUrl, channelSlug, username, bounds, thumbnailPainter ?: placeholder, onDismissed = {
            expandedUrl = null
            onExpandedChange(false)
        })
    }
}

@Composable
private fun ProfilePhotoPreview(
    url: String,
    fullSizeUrl: String?,
    channelSlug: String?,
    username: String?,
    sourceOnScreen: Rect,
    placeholder: Painter?,
    onDismissed: () -> Unit,
) {
    val context = LocalContext.current
    var fetchedUrl by remember(url, channelSlug) { mutableStateOf<String?>(null) }
    LaunchedEffect(url, channelSlug, fullSizeUrl) {
        if (fullSizeUrl.isNullOrBlank() && !channelSlug.isNullOrBlank() &&
            !url.substringBefore('?').substringBefore('#').substringAfterLast('/').substringBeforeLast('.').endsWith("-fullsize")
        ) {
            fetchedUrl = try {
                KickRepository(KickNetwork.api).getChannelProfilePhoto(channelSlug)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }
    val preferredUrl = fullSizeUrl?.takeIf(String::isNotBlank) ?: fetchedUrl ?: url
    var preferredFailed by remember(preferredUrl) { mutableStateOf(false) }
    val imageUrl = if (preferredFailed) url else preferredUrl
    var loadedPainter by remember { mutableStateOf<Painter?>(null) }
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
        var originOnScreen by remember { mutableStateOf<Offset?>(null) }
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f * progress.value))
            .onGloballyPositioned { originOnScreen = it.boundsInWindow().topLeft + view.windowOriginOnScreen() }
            .clickable(interactionSource = null, indication = null, onClick = dismiss)
            .semantics { dismissAction { dismiss(); true } }) {
            val source = originOnScreen?.let { sourceOnScreen.translate(-it.x, -it.y) } ?: return@BoxWithConstraints
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
                model = remember(context, imageUrl) {
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .memoryCacheKey("profile-preview:$imageUrl")
                        .size(Size.ORIGINAL)
                        .precision(Precision.EXACT)
                        .bitmapConfig(Bitmap.Config.ARGB_8888)
                        .build()
                },
                placeholder = loadedPainter ?: placeholder,
                error = loadedPainter ?: placeholder,
                onSuccess = { loadedPainter = it.painter },
                onError = { if (imageUrl != url) preferredFailed = true },
                contentDescription = username, contentScale = ContentScale.Crop,
                modifier = Modifier.absoluteOffset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                    .clip(RoundedCornerShape(radius))
                    .clickable(interactionSource = null, indication = null, onClick = {})
            )
            NetworkInfoBar(Modifier.align(Alignment.BottomCenter))
        }
    }
}

private fun View.windowOriginOnScreen(): Offset {
    val screen = IntArray(2).also(::getLocationOnScreen)
    val window = IntArray(2).also(::getLocationInWindow)
    return Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
}
