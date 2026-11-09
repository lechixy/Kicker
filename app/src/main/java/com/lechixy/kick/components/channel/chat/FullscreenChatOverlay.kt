package com.lechixy.kick.components.channel.chat

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
    val chatFontSize by fullscreenChatSetting(SettingsKeys.CHAT_FONT_SIZE)
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
    var settingsAnchor by remember(editing, horizontal) { mutableStateOf<Alignment?>(null) }
    val settingsInteraction = remember { MutableInteractionSource() }
    val settingsPressed by settingsInteraction.collectIsPressedAsState()
    var contentSize by remember(editing, horizontal) { mutableStateOf(IntSize.Zero) }
    val savePosition by rememberUpdatedState(onSavePosition)
    val fontSize by fullscreenChatSetting(rememberFullscreenChatFontSizeSetting())
    val settingsLabel = stringResource(R.string.fullscreen_chat_open_settings)
    BackHandler(enabled = editing, onBack = onCancelEditing)
    LaunchedEffect(settingsAnchor, settingsPressed) {
        if (settingsAnchor != null && !settingsPressed) {
            delay(4_000)
            settingsAnchor = null
        }
    }
    LaunchedEffect(interactive) {
        if (!interactive) settingsAnchor = null
    }

    BoxWithConstraints(
        modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp),
        contentAlignment = AbsoluteAlignment.TopLeft,
    ) {
        val scale = preferences.scale / 100f
        val panelWidth = if (horizontal) maxWidth else minOf(300.dp * scale, maxWidth * (0.53f * scale))
        val lineHeight = with(LocalDensity.current) { (fontSize + 8).sp.toDp() }
        val laneHeight = minOf(maxOf(32.dp * scale, lineHeight), maxHeight)
        val lanes = if (laneHeight > 0.dp) minOf(preferences.lanes, (maxHeight / laneHeight).toInt()) else 1
        val panelHeight = if (horizontal) laneHeight * lanes else maxHeight * (0.44f * scale)
        val travelX = maxWidth - panelWidth
        val travelY = maxHeight - panelHeight
        val drawnHeight = with(LocalDensity.current) { contentSize.height.toDp() }
        val dragTravelY by rememberUpdatedState(maxHeight - if (contentSize.height > 0) drawnHeight else panelHeight)
        val dock = viewport?.let {
            FullscreenChatDock(it, FullscreenChatEdges(
                left = horizontal || draft.x == 0f,
                top = draft.y == 0f,
                right = horizontal || draft.x == 1f,
                bottom = draft.y == 1f,
            ))
        }
        val dragModifier = if (editing || !preferences.positionLocked) {
            Modifier.pointerInput(editing, horizontal, travelX, travelY, savedPosition) {
                val width = travelX.toPx()
                val threshold = 10.dp.toPx()
                var height = 0f
                var rawPosition = savedPosition
                val start: (Offset) -> Unit = {
                    height = dragTravelY.toPx()
                    rawPosition = draft
                    dragging = true
                    settingsAnchor = null
                }
                val finish: () -> Unit = {
                    dragging = false
                    if (!editing && draft != savedPosition) savePosition(horizontal, draft)
                }
                val cancel: () -> Unit = {
                    dragging = false
                    if (!editing) draft = savedPosition
                }
                fun move(dx: Float, dy: Float) {
                    rawPosition = FullscreenChatPosition(
                        if (width > 0f) (rawPosition.x + dx / width).coerceIn(0f, 1f) else 0f,
                        if (height > 0f) (rawPosition.y + dy / height).coerceIn(0f, 1f) else rawPosition.y,
                    )
                    draft = rawPosition.snapToEdges(width, height, threshold)
                }
                if (horizontal) {
                    detectVerticalDragGestures(start, finish, cancel) { change, dy ->
                        change.consume()
                        move(0f, dy)
                    }
                } else {
                    detectDragGestures(start, finish, cancel) { change, delta ->
                        change.consume()
                        move(delta.x, delta.y)
                    }
                }
            }
        } else Modifier
        val editor = if (editing) {
            val editLabel = stringResource(R.string.fullscreen_chat_edit_position)
            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, preferences.shape.backgroundShape())
                .semantics { contentDescription = editLabel }
                .then(dragModifier)
        } else Modifier
        val measuredContent = Modifier.onSizeChanged { contentSize = it }
        val chatGestures = if (editing) Modifier else if (!interactive) measuredContent else {
            measuredContent
                .then(dragModifier)
                .clickable(interactionSource = null, indication = null, onClickLabel = settingsLabel) {
                    settingsAnchor = when {
                        settingsAnchor != null -> null
                        !horizontal && draft.x >= 0.5f -> AbsoluteAlignment.CenterLeft
                        else -> AbsoluteAlignment.CenterRight
                    }
                }
        }
        Box(
            Modifier.absoluteOffset {
                IntOffset((travelX.toPx() * draft.x).roundToInt(), (travelY.toPx() * draft.y).roundToInt())
            }.size(panelWidth, panelHeight).then(editor),
            contentAlignment = BiasAbsoluteAlignment(-1f, draft.y * 2f - 1f),
        ) {
            CompositionLocalProvider(
                LocalFullscreenChatDock provides dock,
                LocalFullscreenChatShape provides preferences.shape,
            ) {
                if (editing) {
                    val samples = listOf(
                        stringResource(R.string.fullscreen_chat_preview_hello),
                        stringResource(R.string.fullscreen_chat_preview_stream),
                    )
                    val preview = if (horizontal) List(lanes) { samples[it % samples.size] } else samples
                    ChatStack(preview.size, preferences.opacity, measuredContent, blur) { index ->
                        Text(preview[index], color = Color.White, fontSize = fontSize.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier
                                .then(if (horizontal) Modifier.height(laneHeight) else Modifier)
                                .padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                } else if (host != null && channel != null) {
                    CompositionLocalProvider(LocalChatAnimationsEnabled provides animationsEnabled) {
                        FullscreenChatMessages(
                            host, channel, liveChatViewModel, horizontal, playing && !dragging,
                            preferences, fontSize, chatGestures, blur, lanes, laneHeight,
                        )
                    }
                }
            }
        }

        if (editing) {
            Surface(
                modifier = Modifier.align(if (draft.y >= 0.5f) Alignment.TopCenter else Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                    TextButton(onClick = { draft = defaultPosition }) {
                        Text(stringResource(R.string.fullscreen_chat_reset_position))
                    }
                    TextButton(onClick = onCancelEditing) { Text(stringResource(R.string.fullscreen_chat_cancel)) }
                    TextButton(onClick = { onSavePosition(horizontal, draft) }) { Text(stringResource(R.string.fullscreen_chat_done)) }
                }
            }
        }
        settingsAnchor?.takeIf { interactive }?.let { anchor ->
            Surface(
                modifier = Modifier.align(anchor).padding(horizontal = 8.dp).size(48.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(8.dp),
            ) {
                IconButton(
                    onClick = {
                        settingsAnchor = null
                        onOpenSettings()
                    },
                    interactionSource = settingsInteraction,
                ) {
                    Icon(
                        painterResource(R.drawable.settings_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        contentDescription = settingsLabel,
                    )
                }
            }
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
        val recent = visible.takeLast(12)
        ChatStack(recent.size, preferences.opacity, gestures, blur) { index ->
            val message = recent[index]
            key(message.id) { OverlayMessage(message, fontSize, lines = 2, identity) }
        }
    }
}

/** The stack keeps the newest complete rows when messages wrap or font scaling increases. */
@Composable
private fun ChatStack(
    count: Int,
    opacity: Float,
    modifier: Modifier = Modifier,
    blur: FullscreenChatBlur? = null,
    content: @Composable (Int) -> Unit,
) {
    Box(modifier.fillMaxWidth()) {
        FullscreenChatBackdrop(opacity, blur)
        Layout(
            content = { repeat(count) { content(it) } },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val rows = measurables.asReversed().map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
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
                    row.placeRelative(0, bottom)
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
