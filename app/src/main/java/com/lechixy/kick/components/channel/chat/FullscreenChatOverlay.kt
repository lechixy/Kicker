package com.lechixy.kick.components.channel.chat

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lechixy.kick.R
import com.lechixy.kick.components.PlayerHostState
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.SubscriberBadge
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatMessageType
import com.lechixy.kick.data.chat.model.ChatIdentity
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.data.settings.model.FullscreenChatPosition
import com.lechixy.kick.data.settings.model.FullscreenChatShape
import com.lechixy.kick.data.settings.model.SettingKey
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.screens.channel.ChannelViewModel
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class FullscreenChatPreferences(
    val horizontal: Boolean,
    val opacity: Float,
    val verticalPosition: FullscreenChatPosition,
    val horizontalPosition: FullscreenChatPosition,
    val blur: Boolean = false,
    val badges: Boolean = true,
    val positionLocked: Boolean = false,
    val lanes: Int = 1,
    val scale: Int = 100,
    val shape: FullscreenChatShape = FullscreenChatShape.Rounded,
)

internal const val FULLSCREEN_CHAT_EDIT_IDLE_MS = 4_000L

@Composable
internal fun rememberFullscreenChatPreferences(): FullscreenChatPreferences {
    val horizontal by fullscreenChatSetting(SettingsKeys.FULLSCREEN_CHAT_HORIZONTAL)
    val opacity by fullscreenChatSetting(SettingsKeys.FULLSCREEN_CHAT_OPACITY)
    val verticalPosition by fullscreenChatSetting(SettingsKeys.FULLSCREEN_CHAT_VERTICAL_POSITION)
    val horizontalPosition by fullscreenChatSetting(SettingsKeys.FULLSCREEN_CHAT_HORIZONTAL_POSITION)
    val blur by fullscreenChatSetting(SettingsKeys.FULLSCREEN_CHAT_BLUR)
    val badges by fullscreenChatSetting(SettingsKeys.FULLSCREEN_CHAT_BADGES)
    val positionLocked by fullscreenChatSetting(SettingsKeys.FULLSCREEN_CHAT_POSITION_LOCKED)
    val lanes by fullscreenChatSetting(SettingsKeys.FULLSCREEN_CHAT_LANES)
    val scale by fullscreenChatSetting(SettingsKeys.FULLSCREEN_CHAT_SCALE)
    val shape by fullscreenChatSetting(SettingsKeys.FULLSCREEN_CHAT_SHAPE)
    return FullscreenChatPreferences(
        horizontal, opacity.coerceIn(0.35f, 0.85f),
        FullscreenChatPosition.decode(verticalPosition) ?: FullscreenChatPosition.VerticalDefault,
        FullscreenChatPosition.decode(horizontalPosition) ?: FullscreenChatPosition.HorizontalDefault,
        blur, badges, positionLocked, lanes.coerceIn(1, 4), scale.coerceIn(70, 150), FullscreenChatShape.fromValue(shape),
    )
}

@Composable
internal fun rememberFullscreenChatFontSizeSetting(): SettingKey<Int> {
    val smallestWidth = androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp
    val chatFontSize by fullscreenChatSetting(SettingsKeys.chatFontSize(smallestWidth))
    return remember(chatFontSize) { SettingsKeys.FULLSCREEN_CHAT_FONT_SIZE.copy(defaultValue = chatFontSize) }
}

@Composable
private fun <T> fullscreenChatSetting(setting: SettingKey<T>): androidx.compose.runtime.State<T> {
    val repository = LocalSettingsRepository.current
    val flow = remember(repository, setting) { repository.get(setting) }
    return flow.collectAsStateWithLifecycle(initialValue = setting.defaultValue)
}

@Composable
internal fun FullscreenChatOverlay(
    host: PlayerHostState?,
    channel: ChannelDetail?,
    liveChatViewModel: ChannelViewModel?,
    preferences: FullscreenChatPreferences,
    playing: Boolean,
    editing: Boolean,
    onSavePosition: (Boolean, FullscreenChatPosition) -> Unit,
    onCancelEditing: () -> Unit,
    onOpenSettings: () -> Unit = {},
    blur: FullscreenChatBlur? = null,
    viewport: Rect? = null,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val animationsEnabled by rememberChatAnimationsEnabled()
    val horizontal = preferences.horizontal && (animationsEnabled || editing)
    val defaultPosition = if (horizontal) FullscreenChatPosition.HorizontalDefault else FullscreenChatPosition.VerticalDefault
    val savedPosition = if (horizontal) preferences.horizontalPosition else preferences.verticalPosition
    var draft by remember(editing, horizontal, savedPosition) { mutableStateOf(savedPosition) }
    var dragging by remember(editing, horizontal) { mutableStateOf(false) }
    var showQuickControls by remember(editing, horizontal) { mutableStateOf(false) }
    var quickControlsAtStart by remember(editing, horizontal) { mutableStateOf(false) }
    val quickControlsVisible = showQuickControls && interactive && !editing
    val showResizeHandles = editing || quickControlsVisible
    var activeTouches by remember { mutableIntStateOf(0) }
    var lastInteraction by remember { mutableIntStateOf(0) }
    val onEditTouch = remember<(Boolean) -> Unit> {
        { pressed: Boolean ->
            if (pressed) activeTouches++ else {
                activeTouches--
                lastInteraction++
            }
        }
    }
    var contentSize by remember(editing, horizontal) { mutableStateOf(IntSize.Zero) }
    val savePosition by rememberUpdatedState(onSavePosition)
    val cancelEditing by rememberUpdatedState(onCancelEditing)
    val fontSetting = rememberFullscreenChatFontSizeSetting()
    val fontSize by fullscreenChatSetting(fontSetting)
    val repository = LocalSettingsRepository.current
    val scope = rememberCoroutineScope()
    fun changeFontSize(delta: Int) {
        lastInteraction++
        scope.launch {
            repository.editSettings {
                val currentSize = this[fontSetting.key] ?: fontSetting.defaultValue
                this[fontSetting.key] = (currentSize + delta).coerceIn(10, 24)
            }
        }
    }
    val settingsLabel = stringResource(R.string.fullscreen_chat_open_settings)
    BackHandler(enabled = editing || quickControlsVisible) {
        if (editing) onCancelEditing() else showQuickControls = false
    }
    LaunchedEffect(interactive) {
        if (!interactive) showQuickControls = false
    }
    LaunchedEffect(editing, quickControlsVisible, dragging, activeTouches, lastInteraction, horizontal, draft) {
        if (showResizeHandles && !dragging && activeTouches == 0) {
            delay(FULLSCREEN_CHAT_EDIT_IDLE_MS)
            if (editing) {
                if (draft == savedPosition) cancelEditing() else savePosition(horizontal, draft)
            } else {
                showQuickControls = false
            }
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp),
        contentAlignment = AbsoluteAlignment.TopLeft,
    ) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val scale = preferences.scale / 100f
        val lineHeight = with(LocalDensity.current) { (fontSize + 8).sp.toDp() }
        val defaultWidth = if (horizontal) maxWidth else minOf(300.dp * scale, maxWidth * (0.53f * scale))
        val defaultLaneHeight = minOf(maxOf(32.dp * scale, lineHeight), maxHeight)
        val defaultLanes = if (defaultLaneHeight > 0.dp) {
            minOf(preferences.lanes, (maxHeight / defaultLaneHeight).toInt())
        } else 1
        val defaultHeight = if (horizontal) defaultLaneHeight * defaultLanes
            else maxHeight * (0.44f * scale)
        val panelWidth = draft.widthFraction?.let { maxWidth * it } ?: defaultWidth
        val panelHeight = draft.heightFraction?.let { maxHeight * it } ?: defaultHeight
        val lanes = if (draft.heightFraction == null) defaultLanes
            else minOf(preferences.lanes, (panelHeight / lineHeight).toInt().coerceAtLeast(1))
        val laneHeight = panelHeight / lanes
        val travelX = maxWidth - panelWidth
        val travelY = maxHeight - panelHeight
        val drawnHeight = with(LocalDensity.current) { contentSize.height.toDp() }
        val dragTravelX by rememberUpdatedState(travelX)
        val dragTravelY by rememberUpdatedState(
            maxHeight - if (!showResizeHandles && contentSize.height > 0) drawnHeight else panelHeight
        )
        val resizeWithGesture by rememberUpdatedState(showResizeHandles)
        val density = LocalDensity.current
        val resizeDraft by rememberUpdatedState<(Int, Int, Offset) -> Unit> { horizontalEdge, verticalEdge, delta ->
            with(density) {
                if (horizontalEdge != 0) draft = draft.resize(
                    availableWidth.toPx(), (draft.widthFraction?.let { availableWidth * it } ?: defaultWidth).toPx(),
                    96.dp.toPx(), delta.x, horizontalEdge < 0, horizontalAxis = true,
                )
                if (verticalEdge != 0) draft = draft.resize(
                    availableHeight.toPx(), (draft.heightFraction?.let { availableHeight * it } ?: defaultHeight).toPx(),
                    lineHeight.toPx(), delta.y, verticalEdge < 0, horizontalAxis = false,
                )
            }
        }
        val dock = viewport?.let {
            FullscreenChatDock(it, FullscreenChatEdges(
                left = travelX == 0.dp || draft.x == 0f,
                top = draft.y == 0f,
                right = travelX == 0.dp || draft.x == 1f,
                bottom = draft.y == 1f,
            ))
        }
        val dragModifier = if (showResizeHandles || !preferences.positionLocked) {
            Modifier.pointerInput(editing, horizontal, savedPosition, preferences.positionLocked) {
                val threshold = 10.dp.toPx()
                var width = 0f
                var height = 0f
                var horizontalEdge = 0
                var verticalEdge = 0
                var rawPosition = savedPosition
                detectDragGestures(
                    orientationLock = null,
                    onDragStart = { down, _, _ ->
                        val handleSize = minOf(36.dp.toPx(), size.width / 3f, size.height / 3f)
                        val x = if (resizeWithGesture) resizeHandleAt(down.position.x, size.width, handleSize) else null
                        val y = if (resizeWithGesture) resizeHandleAt(down.position.y, size.height, handleSize) else null
                        horizontalEdge = if (y != null) x ?: 0 else 0
                        verticalEdge = if (x != null) y ?: 0 else 0
                        width = dragTravelX.toPx()
                        height = dragTravelY.toPx()
                        rawPosition = draft
                        dragging = true
                    },
                    onDragEnd = {
                        dragging = false
                        if (!editing && draft != savedPosition) savePosition(horizontal, draft)
                    },
                    onDragCancel = {
                        dragging = false
                        if (!editing) draft = savedPosition
                    },
                ) { change, delta ->
                    change.consume()
                    if (horizontalEdge != 0 || verticalEdge != 0) {
                        resizeDraft(horizontalEdge, verticalEdge, delta)
                    } else if (editing || !preferences.positionLocked) {
                        rawPosition = rawPosition.copy(
                            x = if (width > 0f) (rawPosition.x + delta.x / width).coerceIn(0f, 1f) else rawPosition.x,
                            y = if (height > 0f) (rawPosition.y + delta.y / height).coerceIn(0f, 1f) else rawPosition.y,
                        )
                        draft = rawPosition.snapToEdges(width, height, threshold)
                    }
                }
            }
        } else Modifier
        val editor = if (showResizeHandles) {
            val editLabel = stringResource(R.string.fullscreen_chat_edit_position)
            Modifier.semantics { contentDescription = editLabel }
                .observeChatEditTouch(onEditTouch)
                .then(dragModifier)
        } else Modifier
        val measuredContent = Modifier.onSizeChanged { contentSize = it }
        val chatGestures = if (editing) Modifier else if (!interactive) measuredContent else {
            measuredContent
                .then(if (showResizeHandles) Modifier else dragModifier)
                .clickable(interactionSource = null, indication = null, onClickLabel = settingsLabel) {
                    if (!showQuickControls) {
                        quickControlsAtStart = if (horizontal) draft.y >= 0.5f else travelX > 0.dp && draft.x >= 0.5f
                    }
                    showQuickControls = !showQuickControls
                }
        }
        val alignRight = travelX * draft.x + panelWidth / 2 > maxWidth / 2
        Box(
            Modifier.absoluteOffset {
                IntOffset((travelX.toPx() * draft.x).roundToInt(), (travelY.toPx() * draft.y).roundToInt())
            }.size(panelWidth, panelHeight).then(editor),
            contentAlignment = BiasAbsoluteAlignment(-1f, draft.y * 2f - 1f),
        ) {
            CompositionLocalProvider(
                LocalFullscreenChatDock provides dock,
                LocalFullscreenChatShape provides preferences.shape,
                LocalFullscreenChatAlignRight provides alignRight,
            ) {
                if (editing) {
                    val samples = listOf(
                        stringResource(R.string.fullscreen_chat_preview_hello),
                        stringResource(R.string.fullscreen_chat_preview_stream),
                    )
                    val preview = if (horizontal) List(lanes) { samples[it % samples.size] } else samples
                    ChatStack(preview.size, preferences.opacity, measuredContent, blur) { index ->
                        Text(preview[index], color = Color.White, fontSize = fontSize.sp, maxLines = 1,
                            lineHeight = (fontSize + 4).sp,
                            textAlign = if (alignRight) TextAlign.Right else TextAlign.Left,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier
                                .then(if (horizontal) Modifier.height(laneHeight) else Modifier)
                                .padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                } else if (host != null && channel != null) {
                    CompositionLocalProvider(LocalChatAnimationsEnabled provides animationsEnabled) {
                        FullscreenChatMessages(
                            host, channel, liveChatViewModel, horizontal, playing && !dragging,
                            preferences, fontSize, chatGestures, blur, lanes, laneHeight, lineHeight,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = showResizeHandles,
                modifier = Modifier.matchParentSize(),
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(160)),
            ) {
                Box(Modifier.fillMaxSize().border(
                    1.dp, MaterialTheme.colorScheme.primary, preferences.shape.backgroundShape(),
                )) {
                    ChatResizeHandles()
                }
            }
        }

        if (editing) {
            Surface(
                modifier = Modifier.align(if (draft.y >= 0.5f) Alignment.TopCenter else Alignment.BottomCenter)
                    .observeChatEditTouch(onEditTouch),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(Modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(
                            R.string.fullscreen_chat_size_percent,
                            if (availableWidth > 0.dp) (panelWidth / availableWidth * 100).roundToInt() else 0,
                            if (availableHeight > 0.dp) (panelHeight / availableHeight * 100).roundToInt() else 0,
                        ),
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = {
                            lastInteraction++
                            draft = defaultPosition
                        }) {
                            Text(stringResource(R.string.fullscreen_chat_reset_position))
                        }
                        TextButton(onClick = onCancelEditing) { Text(stringResource(R.string.fullscreen_chat_cancel)) }
                        TextButton(onClick = { onSavePosition(horizontal, draft) }) { Text(stringResource(R.string.fullscreen_chat_done)) }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = quickControlsVisible,
            modifier = Modifier.align(
                if (horizontal) {
                    if (quickControlsAtStart) Alignment.TopCenter else Alignment.BottomCenter
                } else {
                    if (quickControlsAtStart) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight
                }
            ),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160)),
        ) {
            val decreaseFontLabel = stringResource(R.string.fullscreen_chat_decrease_font_size)
            val increaseFontLabel = stringResource(R.string.fullscreen_chat_increase_font_size)
            Surface(
                modifier = Modifier.padding(horizontal = 4.dp).observeChatEditTouch(onEditTouch),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
            ) {
                val buttons: @Composable () -> Unit = {
                    val fontLabel = stringResource(R.string.fullscreen_chat_font_size)
                    Column(
                        Modifier.padding(6.dp).semantics(mergeDescendants = true) {},
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(painterResource(R.drawable.format_size_24dp), fontLabel, Modifier.size(18.dp))
                        Text("$fontSize sp", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(enabled = fontSize > 10, onClick = { changeFontSize(-1) }) {
                        Text("−", Modifier.semantics {
                            contentDescription = decreaseFontLabel
                        }, style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(enabled = fontSize < 24, onClick = { changeFontSize(1) }) {
                        Text("+", Modifier.semantics {
                            contentDescription = increaseFontLabel
                        }, style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = {
                        showQuickControls = false
                        onOpenSettings()
                    }) {
                        Icon(painterResource(R.drawable.settings_24dp_e3e3e3_fill0_wght400_grad0_opsz24), settingsLabel)
                    }
                    IconButton(onClick = { showQuickControls = false }) {
                        Icon(
                            painterResource(R.drawable.check_circle_24dp_e3e3e3_fill1_wght400_grad0_opsz24),
                            stringResource(R.string.fullscreen_chat_done),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (!horizontal && availableHeight >= 240.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { buttons() }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) { buttons() }
                }
            }
        }
    }
}

private fun Modifier.observeChatEditTouch(onTouch: (Boolean) -> Unit) = pointerInput(onTouch) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onTouch(true)
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
        } finally {
            onTouch(false)
        }
    }
}

private fun resizeHandleAt(position: Float, length: Int, handleSize: Float): Int? = when {
    position < handleSize -> -1
    position > length - handleSize -> 1
    kotlin.math.abs(position - length / 2f) <= handleSize / 2f -> 0
    else -> null
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ChatResizeHandles() {
    for (y in -1..1) {
        for (x in -1..1) {
            if (x == 0 && y == 0) continue
            Box(Modifier.align(BiasAbsoluteAlignment(x.toFloat(), y.toFloat()))
                .padding(2.dp).size(9.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

@Composable
private fun FullscreenChatMessages(
    host: PlayerHostState,
    channel: ChannelDetail,
    liveChatViewModel: ChannelViewModel?,
    horizontal: Boolean,
    playing: Boolean,
    preferences: FullscreenChatPreferences,
    fontSize: Int,
    gestures: Modifier,
    blur: FullscreenChatBlur?,
    lanes: Int,
    laneHeight: Dp,
    rowHeight: Dp,
) {
    val recording = host.recording
    val seek = host.recordingChatSeek.takeIf { recording != null }
    val community: ChatCommunityViewModel = viewModel(key = "community-${channel.slug}")
    val mutedUsers by community.mutedUsers.collectAsStateWithLifecycle()
    val userColors = if (recording == null) liveChatViewModel?.userColors?.collectAsChatState()?.value.orEmpty()
        else emptyMap()
    val identity = FullscreenChatIdentityStyle(
        userColors, channel.subscriberBadges, preferences.badges, channel.user?.username, channel.slug,
    )
    val messages: List<ChatMessage>
    val status: Int?
    if (recording != null) {
        val replay = recordedChatForPresentation(channel.id, recording, host)
        messages = replay?.messages.orEmpty()
        status = when {
            replay == null -> R.string.recorded_chat_unavailable
            replay.failed -> R.string.recorded_chat_failed
            replay.loading -> R.string.fullscreen_chat_loading
            else -> null
        }
    } else {
        val liveMessages = liveChatViewModel?.chatMessages?.collectAsChatState()?.value.orEmpty()
        val roomId = channel.chatroom?.id
        messages = remember(liveMessages, roomId) {
            liveMessages.filter { it.chatroomId == null || it.chatroomId == roomId }
        }
        status = when (liveChatViewModel?.chatConnectionState?.collectAsChatState()?.value) {
            ChatConnectionState.CONNECTING -> R.string.fullscreen_chat_loading
            ChatConnectionState.DISCONNECTED, ChatConnectionState.ERROR -> R.string.fullscreen_chat_disconnected
            else -> null
        }
    }
    val visible = remember(messages, mutedUsers, seek) {
        messages.filter { message ->
            message.id.isNotBlank() && !message.isDeleted &&
                (message.type == ChatMessageType.MESSAGE || message.type == ChatMessageType.REPLY) &&
                message.sender?.username?.lowercase(Locale.ROOT) !in mutedUsers &&
                (seek == null || (message.recordingOffsetMs ?: -1) >= seek.positionMs)
        }
    }
    if (status != null && visible.isEmpty()) {
        Box(gestures) {
            FullscreenChatBackdrop(preferences.opacity, blur)
            Text(stringResource(status), color = Color.White, fontSize = fontSize.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    } else if (horizontal) {
        key(channel.id, recording?.id, seek) {
            val earliestReplayOffset = if (recording != null) host.recordingPositionMs - 2_500L else null
            HorizontalChat(messages, visible, earliestReplayOffset, playing, preferences.opacity, fontSize,
                gestures, identity, blur, lanes, laneHeight)
        }
    } else if (visible.isNotEmpty()) {
        val recent = visible.takeLast(if (preferences.verticalPosition.heightFraction == null) 12 else 32)
        ChatStack(recent.size, preferences.opacity, gestures, blur, rowHeight) { index ->
            val message = recent[index]
            key(message.id) {
                Box(contentAlignment = Alignment.CenterStart) {
                    OverlayMessage(message, fontSize, lines = 1, identity)
                }
            }
        }
    }
}

private val LocalFullscreenChatAlignRight = androidx.compose.runtime.compositionLocalOf { false }

@Composable
private fun ChatStack(
    count: Int,
    opacity: Float,
    modifier: Modifier = Modifier,
    blur: FullscreenChatBlur? = null,
    rowHeight: Dp? = null,
    content: @Composable (Int) -> Unit,
) {
    val alignRight = LocalFullscreenChatAlignRight.current
    Box(modifier.fillMaxWidth()) {
        FullscreenChatBackdrop(opacity, blur)
        Layout(
            content = { repeat(count) { content(it) } },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val fixedHeight = rowHeight?.roundToPx()?.coerceAtMost(constraints.maxHeight)
            val rowConstraints = constraints.copy(
                minWidth = 0,
                minHeight = fixedHeight ?: 0,
                maxHeight = fixedHeight ?: constraints.maxHeight,
            )
            val rows = measurables.asReversed().map { it.measure(rowConstraints) }
            var height = 0
            val fittingRows = rows.takeWhile { row ->
                if (height + row.height > constraints.maxHeight) false else {
                    height += row.height
                    true
                }
            }
            layout(constraints.maxWidth, height) {
                var bottom = height
                fittingRows.forEach { row ->
                    bottom -= row.height
                    row.place(if (alignRight) constraints.maxWidth - row.width else 0, bottom)
                }
            }
        }
    }
}

@Composable
internal fun HorizontalChat(
    messages: List<ChatMessage>,
    visible: List<ChatMessage>,
    earliestReplayOffset: Long?,
    playing: Boolean,
    opacity: Float,
    fontSize: Int,
    modifier: Modifier = Modifier,
    identity: FullscreenChatIdentityStyle = FullscreenChatIdentityStyle(),
    blur: FullscreenChatBlur? = null,
    laneCount: Int = 1,
    laneHeight: Dp = 32.dp,
) {
    val ids = remember(messages) { messages.map(ChatMessage::id) }
    val eligible = remember(visible) { visible.associateBy(ChatMessage::id) }
    val queuedIds = remember(visible, earliestReplayOffset) {
        visible.filter {
            earliestReplayOffset == null || (it.recordingOffsetMs ?: -1) >= earliestReplayOffset
        }.map(ChatMessage::id)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val chipMaxWidth by rememberUpdatedState(maxWidth * 0.85f)
        key(laneCount, fontSize, identity.showOptionalBadges) {
            val distance = with(LocalDensity.current) { maxWidth.toPx() }
            val currentDistance by rememberUpdatedState(distance)
            val speed = with(LocalDensity.current) { 130.dp.toPx() }
            val gap = with(LocalDensity.current) { 12.dp.toPx() }
            val queue = remember { FullscreenChatQueue() }
            val active = remember { mutableStateListOf<HorizontalChatChip>() }
            var travel by remember { mutableDoubleStateOf(0.0) }
            var waiting by remember { mutableStateOf(false) }
            LaunchedEffect(ids, queuedIds, eligible.keys) {
                queue.update(ids, queuedIds)
                active.removeAll { it.id !in eligible }
                waiting = queue.hasPending
            }
            val hasWork = waiting || active.isNotEmpty()
            LaunchedEffect(playing, hasWork, distance > 0f, speed, gap) {
                if (!playing || !hasWork || currentDistance <= 0f) return@LaunchedEffect
                var lastFrame: Long? = null
                while (queue.hasPending || active.isNotEmpty()) {
                    withFrameNanos { now ->
                        lastFrame?.let { travel += (now - it).coerceAtMost(50_000_000L) * speed.toDouble() / 1_000_000_000 }
                        lastFrame = now
                        active.removeAll { travel - it.enteredAt - it.occupiedWidth >= currentDistance }
                        for (lane in 0 until laneCount) {
                            if (!queue.hasPending) break
                            val tail = active.lastOrNull { it.lane == lane }
                            // Every chip travels at the same speed. Admit the next chip once the tail clears the entrance.
                            if (tail == null || (tail.occupiedWidth > 0 && travel - tail.enteredAt >= tail.occupiedWidth + gap)) {
                                queue.next()?.let { active.add(HorizontalChatChip(it, lane, travel, chipMaxWidth)) }
                            }
                        }
                        waiting = queue.hasPending
                    }
                }
                travel = 0.0
            }
            if (active.isNotEmpty() || (playing && waiting)) {
                val edges = LocalFullscreenChatDock.current?.edges
                val chipShape = LocalFullscreenChatShape.current.backgroundShape()
                val hasStripBackdrop = blur != null || edges?.top == true || edges?.bottom == true
                Box(modifier.fillMaxWidth().height(laneHeight * laneCount)) {
                    if (hasStripBackdrop) FullscreenChatBackdrop(opacity, blur)
                    Box(Modifier.matchParentSize().clipToBounds()) {
                        active.forEach { chip ->
                            val message = eligible[chip.id] ?: return@forEach
                            key(chip.id) {
                                Box(
                                    Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(y = laneHeight * chip.lane)
                                        .height(laneHeight).widthIn(max = chip.maxWidth).onSizeChanged {
                                            chip.width = it.width
                                            chip.occupiedWidth = maxOf(chip.occupiedWidth, it.width)
                                        }
                                        .graphicsLayer {
                                            alpha = if (chip.width == 0) 0f else 1f
                                            translationX = (travel - chip.enteredAt - chip.width).toFloat()
                                        },
                                    contentAlignment = AbsoluteAlignment.CenterLeft,
                                ) {
                                    Box(
                                        if (!hasStripBackdrop) {
                                            Modifier.background(Color.Black.copy(alpha = opacity), chipShape)
                                        } else Modifier
                                    ) {
                                        OverlayMessage(message, fontSize, lines = 1, identity)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private class HorizontalChatChip(val id: String, val lane: Int, val enteredAt: Double, val maxWidth: Dp) {
    var width by mutableIntStateOf(0)
    var occupiedWidth = 0
}

internal data class FullscreenChatIdentityStyle(
    val userColors: Map<String, String> = emptyMap(),
    val subscriberBadges: List<SubscriberBadge> = emptyList(),
    val showOptionalBadges: Boolean = true,
    val streamerUsername: String? = null,
    val streamerSlug: String? = null,
)

private val essentialBadgeTypes = setOf("verified", "moderator", "broadcaster")

internal fun ChatIdentity.withEssentialFullscreenBadges(): ChatIdentity = copy(
    badges = badges.filter { it.type.lowercase(Locale.ROOT) in essentialBadgeTypes },
    badgesV2 = badgesV2.filter {
        it.name.lowercase(Locale.ROOT) in essentialBadgeTypes || it.badgeType?.lowercase(Locale.ROOT) in essentialBadgeTypes
    },
)

@Composable
internal fun OverlayMessage(message: ChatMessage, fontSize: Int, lines: Int, identity: FullscreenChatIdentityStyle) {
    val name = message.sender?.username?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_event_user)
    val senderIdentity = message.sender?.identity
    val rawColor = senderIdentity?.color ?: identity.userColors[name.lowercase(Locale.ROOT)]
    val context = LocalContext.current
    val badges = remember(senderIdentity, identity.subscriberBadges, identity.showOptionalBadges, context) {
        val visibleIdentity = if (identity.showOptionalBadges) senderIdentity else senderIdentity?.withEssentialFullscreenBadges()
        ChatTextParser.resolveBadges(visibleIdentity, identity.subscriberBadges, context = context)
    }
    val mentionsStreamer = remember(message.content, identity.streamerUsername, identity.streamerSlug) {
        ChatTextParser.containsStreamerMention(message.content, identity.streamerUsername, identity.streamerSlug)
    }
    val (mentionBrush, mentionShadow) = if (mentionsStreamer) rememberStreamerMentionEffects(fontSize) else null to null
    val parsed = ChatTextParser.parseMessage(
        senderName = name,
        senderColor = ChatTextParser.parseHexColor(rawColor, Color(0xFFBDF6C9)),
        content = message.content,
        fontSizeSp = fontSize,
        badges = badges,
        streamerUsername = identity.streamerUsername,
        streamerSlug = identity.streamerSlug,
        streamerMentionBrush = mentionBrush,
        streamerMentionShadow = mentionShadow,
        interactive = false,
    )
    Text(
        parsed.annotatedString, inlineContent = parsed.inlineContent, color = Color.White,
        style = MaterialTheme.typography.bodySmall.copy(shadow = Shadow(color = Color.Black, blurRadius = 3f)),
        fontSize = fontSize.sp, lineHeight = (fontSize + 4).sp, maxLines = lines,
        textAlign = if (LocalFullscreenChatAlignRight.current) TextAlign.Right else TextAlign.Left,
        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun rememberChatAnimationsEnabled(): androidx.compose.runtime.State<Boolean> {
    val resolver = LocalContext.current.contentResolver
    return produceState(true, resolver) {
        val setting = Settings.Global.ANIMATOR_DURATION_SCALE
        fun update() { value = Settings.Global.getFloat(resolver, setting, 1f) > 0f }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = update()
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(setting), false, observer)
        update()
        awaitDispose { resolver.unregisterContentObserver(observer) }
    }
}
