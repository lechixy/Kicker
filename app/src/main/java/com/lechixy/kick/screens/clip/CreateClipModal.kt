package com.lechixy.kick.screens.clip

import android.content.ClipData
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.model.AccountProfile
import com.lechixy.kick.data.channel.model.ClipChannel
import com.lechixy.kick.data.channel.model.ClipCreator
import com.lechixy.kick.data.channel.model.ClipDetail
import com.lechixy.kick.data.channel.model.ClipDraft
import com.lechixy.kick.data.player.remote.ViewerSession
import com.lechixy.kick.theme.KickTheme
import com.lechixy.kick.theme.Theme
import com.lechixy.kick.util.FormatUtils.formatDuration
import com.lechixy.kick.util.buildOptimizedImageRequest
import kotlinx.coroutines.launch

@Composable
internal fun CreateClipModal(
    videoId: String?,
    positionSeconds: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    liveSession: ViewerSession? = null,
) {
    val auth = AccountGraph.auth
    val owner = remember(videoId, positionSeconds, liveSession) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val model = viewModel(viewModelStoreOwner = owner) {
        CreateClipViewModel(auth, videoId, positionSeconds, liveSession)
    }
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val shareLabel = stringResource(R.string.clip_share)

    fun share(baseUrl: String, queryName: String, clipUrl: String) {
        val url = Uri.parse(baseUrl).buildUpon().appendQueryParameter(queryName, clipUrl).build()
        try {
            uriHandler.openUri(url.toString())
        } catch (_: IllegalArgumentException) {
            Toast.makeText(context, R.string.clip_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = { if (!state.isPublishing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val useColumns = maxWidth >= 600.dp
            Box(Modifier.matchParentSize().pointerInput(state.isPublishing, onDismiss) {
                detectTapGestures { if (!state.isPublishing) onDismiss() }
            })
            Surface(
                modifier = modifier.widthIn(max = if (useColumns) 780.dp else 500.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column {
                    ClipModalHeader(state.publishedClip != null, !state.isPublishing, onDismiss)
                    AnimatedContent(
                        targetState = state.publishedClip,
                        label = "Clip creation step",
                        modifier = Modifier.weight(1f, fill = false),
                    ) { clip ->
                        if (clip == null) {
                            CreateClipContent(
                                uiState = state,
                                useColumns = useColumns,
                                onTitleChange = model::setTitle,
                                onTrimChange = model::setTrim,
                                onTogglePlayPause = model::togglePlayback,
                                onToggleMute = model::toggleMute,
                                onPausePreview = model::pausePreview,
                                onRetry = model::retry,
                                onSignIn = { auth.beginLogin(context) },
                            )
                        } else {
                            ShareClipContent(
                                clip = clip,
                                useColumns = useColumns,
                                onCopyUrl = { url ->
                                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(shareLabel, url))) }
                                },
                                onShareFacebook = { share("https://www.facebook.com/sharer/sharer.php", "u", it) },
                                onShareX = { share("https://twitter.com/intent/tweet", "url", it) },
                            )
                        }
                    }
                    if (state.publishedClip == null) {
                        ClipPublishFooter(state, model::publish, onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipModalHeader(isSharing: Boolean, enabled: Boolean, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (isSharing) R.string.clip_share else R.string.clip_create),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        ClipIconButton(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24, R.string.player_close, onDismiss, enabled)
    }
}

@Composable
private fun CreateClipContent(
    uiState: CreateClipUiState,
    useColumns: Boolean,
    onTitleChange: (String) -> Unit,
    onTrimChange: (ClipTrim) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onPausePreview: () -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
) {
    ClipModalBody(
        useColumns = useColumns,
        media = {
            val draft = uiState.draft
            if (draft != null && !LocalInspectionMode.current) {
                ClipDraftPreview(
                    previewUrl = draft.previewUrl,
                    trim = uiState.trim,
                    isPlaying = uiState.isPlaying,
                    isMuted = uiState.isMuted,
                    onTogglePlayback = onTogglePlayPause,
                    onToggleMute = onToggleMute,
                    onPause = onPausePreview,
                )
            } else {
                ClipPreviewFrame { if (uiState.isInitializing) CircularProgressIndicator() }
            }
        },
        details = {
            uiState.errorMessage?.let { message ->
                ClipNotice(
                    message = message,
                    actionLabel = if (uiState.needsSignIn) R.string.account_sign_in else R.string.chat_retry,
                    onAction = if (uiState.needsSignIn) onSignIn else onRetry,
                    actionEnabled = uiState.draft == null || uiState.canPublish,
                )
            }
            if (uiState.draft != null) {
                ClipTitleInput(uiState, onTitleChange)
                val trim = uiState.trim
                val sourceDuration = uiState.draft.sourceDurationSeconds
                val range = remember(trim.start, trim.end) {
                    val start = formatDuration(trim.start * 1000L).removePrefix("0")
                    val end = formatDuration(trim.end * 1000L).removePrefix("0")
                    "$start – $end"
                }
                ClipTrimTimeline(trim, sourceDuration, range, !uiState.isPublishing, onTrimChange)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            stringResource(R.string.clip_trim_summary, trim.duration, sourceDuration, range),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ClipTitleInput(uiState: CreateClipUiState, onTitleChange: (String) -> Unit) {
    val creator = uiState.creator
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (creator != null) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (creator.avatar.isNullOrBlank()) {
                    Icon(painterResource(R.drawable.person_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null, Modifier.size(18.dp))
                } else {
                    AsyncImage(
                        model = remember(context, creator.avatar) { buildOptimizedImageRequest(context, creator.avatar, 28, 28) },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            creator?.name.orEmpty(),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.clip_title_counter, uiState.title.length, CLIP_TITLE_LIMIT),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedTextField(
        value = uiState.title,
        onValueChange = onTitleChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = !uiState.isPublishing,
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        placeholder = {
            Text(stringResource(R.string.clip_title_required), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@Composable
private fun ClipPublishFooter(uiState: CreateClipUiState, onPublish: () -> Unit, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel, enabled = !uiState.isPublishing) { Text(stringResource(android.R.string.cancel)) }
        Button(onClick = onPublish, enabled = uiState.canPublish, shape = RoundedCornerShape(8.dp)) {
            if (uiState.isPublishing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.clip_publish))
        }
    }
}

@Composable
private fun ShareClipContent(
    clip: ClipDetail,
    onCopyUrl: (String) -> Unit,
    onShareFacebook: (String) -> Unit,
    onShareX: (String) -> Unit,
    useColumns: Boolean,
    onDownloadClick: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    ClipModalBody(
        useColumns = useColumns,
        media = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column {
                    AsyncImage(
                        model = remember(context, clip.thumbnailUrl) { buildOptimizedImageRequest(context, clip.thumbnailUrl, 480, 270) },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    )
                    Text(clip.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
                }
            }
        },
        banner = { ClipNotice(R.string.clip_published, icon = R.drawable.check_circle_24dp_e3e3e3_fill0_wght400_grad0_opsz24) },
        details = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        clip.shareUrl, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    )
                    ClipIconButton(R.drawable.content_copy_24dp_e3e3e3_fill0_wght400_grad0_opsz24, R.string.clip_copy_url, { onCopyUrl(clip.shareUrl) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ClipShareAction(R.drawable.download_24dp_e3e3e3_fill0_wght400_grad0_opsz24, stringResource(R.string.recording_download), onDownloadClick != null) {
                    onDownloadClick?.invoke(clip.playbackUrl)
                }
                ClipShareAction(R.drawable.facebook_logo, "Facebook") { onShareFacebook(clip.shareUrl) }
                ClipShareAction(R.drawable.x_logo, "X") { onShareX(clip.shareUrl) }
            }
        },
    )
}

@Composable
private fun ClipModalBody(
    useColumns: Boolean,
    media: @Composable () -> Unit,
    banner: @Composable () -> Unit = {},
    details: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        banner()
        if (useColumns) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { media() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { details() }
            }
        } else {
            media()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { details() }
        }
    }
}

@Composable
private fun ClipNotice(
    @StringRes message: Int,
    @DrawableRes icon: Int? = null,
    @StringRes actionLabel: Int? = null,
    onAction: () -> Unit = {},
    actionEnabled: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon?.let { Icon(painterResource(it), null, tint = MaterialTheme.colorScheme.primary) }
            Text(stringResource(message), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            actionLabel?.let { TextButton(onClick = onAction, enabled = actionEnabled) { Text(stringResource(it)) } }
        }
    }
}

@Composable
private fun ClipShareAction(@DrawableRes icon: Int, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(icon), null, Modifier.size(20.dp))
            Text(label)
        }
    }
}

@Composable
internal fun ClipIconButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), stringResource(label), tint = if (enabled) tint else tint.copy(alpha = .38f))
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, widthDp = 360)
@Composable
private fun CreateClipContentPreview() {
    KickTheme(theme = Theme.DARK, dynamicColor = false) {
        Surface {
            CreateClipContent(
                uiState = CreateClipUiState(
                    isInitializing = false,
                    creator = AccountProfile(1, "ClipCreator"),
                    draft = ClipDraft("clip_preview", "", 180),
                ),
                useColumns = false, onTitleChange = {}, onTrimChange = {}, onTogglePlayPause = {},
                onToggleMute = {}, onPausePreview = {}, onRetry = {}, onSignIn = {},
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, widthDp = 360)
@Composable
private fun ShareClipContentPreview() {
    KickTheme(theme = Theme.DARK, dynamicColor = false) {
        Surface {
            ShareClipContent(
                clip = ClipDetail(
                    id = "clip_preview", title = "Stream highlight", duration = 30.0, playbackUrl = "",
                    startedAt = "", createdAt = "", channel = ClipChannel(1, "Streamer", "streamer"),
                    creator = ClipCreator(1, "ClipCreator", "clipcreator"),
                ),
                onCopyUrl = {}, onShareFacebook = {}, onShareX = {}, useColumns = false,
            )
        }
    }
}
