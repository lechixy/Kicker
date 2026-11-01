package com.lechixy.kick.components.channel.chat

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.components.AppLoadingIndicator
import com.lechixy.kick.components.settings.SettingCard
import com.lechixy.kick.components.settings.SettingSlider
import com.lechixy.kick.components.settings.SettingSwitch
import com.lechixy.kick.data.channel.model.SubscriberBadge
import com.lechixy.kick.data.chat.model.ChannelSubscriptionEvent
import com.lechixy.kick.data.chat.model.ChatCelebration
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatWebSocketEvent
import com.lechixy.kick.data.chat.model.GiftedSubscriptionsEvent
import com.lechixy.kick.data.chat.model.PinnedMessageInfo
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(
    messages: List<ChatMessage>,
    connectionState: ChatConnectionState,
    modifier: Modifier = Modifier,
    subscriberBadges: List<SubscriberBadge> = emptyList(),
    userColors: Map<String, String> = emptyMap(),
    pinnedMessage: PinnedMessageInfo? = null,
    chatEvents: Flow<ChatWebSocketEvent> = emptyFlow(),
    streamerUsername: String? = null,
    streamerSlug: String? = null,
    channelId: Long? = null,
    onConnectClick: () -> Unit = {},
    onDisconnectClick: () -> Unit = {},
    onHideChat: (() -> Unit)? = null,
    composer: @Composable () -> Unit = {},
    onOpenChannel: (String) -> Unit = {},
    onReplyTo: (ChatMessage) -> Unit = {},
    isReplay: Boolean = false,
    highTraffic: Boolean = false
) {
    val repository = LocalSettingsRepository.current
    val fontSize by repository
        .get(SettingsKeys.CHAT_FONT_SIZE)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.CHAT_FONT_SIZE.defaultValue)
    val autoConnectChat by repository
        .get(SettingsKeys.AUTO_CONNECT_CHAT)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.AUTO_CONNECT_CHAT.defaultValue)
    val messageSpacing by repository
        .get(SettingsKeys.CHAT_MESSAGE_SPACING)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.CHAT_MESSAGE_SPACING.defaultValue)

    val timestampPreference by repository.get(SettingsKeys.CHAT_TIMESTAMPS)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.CHAT_TIMESTAMPS.defaultValue)
    val timestampMode = com.lechixy.kick.data.chat.model.ChatTimestampMode.fromValue(timestampPreference)
    val timestampZone = java.time.ZoneId.systemDefault()

    var showOptions by remember { mutableStateOf(false) }
    var showChatters by androidx.compose.runtime.saveable.rememberSaveable(streamerSlug) { mutableStateOf(false) }
    val community: ChatCommunityViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "community-$streamerSlug")
    val activeChatters by community.chatters.collectAsStateWithLifecycle()
    val activeChattersError by community.listError.collectAsStateWithLifecycle()
    val selectedProfile by community.profile.collectAsStateWithLifecycle()
    val mutedUsers by community.mutedUsers.collectAsStateWithLifecycle()
    val visibleMessages = remember(messages, mutedUsers) {
        messages.filter { it.sender?.username?.lowercase(java.util.Locale.ROOT) !in mutedUsers }
    }
    val firstNewMessageId = remember(visibleMessages) {
        if (visibleMessages.any { it.isHistory }) visibleMessages.firstOrNull { !it.isHistory }?.id else null
    }
    val chattersBackProgress by com.lechixy.kick.components.rememberPredictiveBackProgress(
        enabled = showChatters && selectedProfile == null, onBack = { showChatters = false })
    LaunchedEffect(showChatters, channelId) {
        if (showChatters && channelId != null) community.loadChatters(channelId)
    }
    selectedProfile?.let { profile ->
        KickUserProfileDialog(profile, community::closeProfile, community::toggleFollow, community::toggleMute,
            onChannelClick = { community.closeProfile(); onOpenChannel(profile.slug) })
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = visibleMessages.lastIndex.coerceAtLeast(0)
    )
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()
    val chatBackdrop = rememberGraphicsLayer()
    var chatBackdropPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    val favorites by com.lechixy.kick.data.favorites.local.FavoriteUsersStore.favorites.collectAsStateWithLifecycle()
    val accountSession by com.lechixy.kick.data.account.AccountGraph.auth.session.collectAsStateWithLifecycle()
    var highlightedReply by remember(streamerSlug) { mutableStateOf<String?>(null) }
    var highlightSequence by remember(streamerSlug) { androidx.compose.runtime.mutableIntStateOf(0) }
    var replyNavigationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var navigatingReply by remember { mutableStateOf(false) }
    val currentVisibleMessages by rememberUpdatedState(visibleMessages)
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var unreadCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(highlightSequence) {
        if (highlightedReply != null) {
            kotlinx.coroutines.delay(1400.milliseconds)
            highlightedReply = null
        }
    }

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf true
            val lastVisibleItem =
                layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            lastVisibleItem.index >= totalItems - 2
        }
    }

    LaunchedEffect(isAtBottom, isUserDragging, navigatingReply) {
        if (isAtBottom && !isUserDragging && !navigatingReply) {
            autoScrollEnabled = true
            unreadCount = 0
        } else if (isUserDragging && !isAtBottom) {
            autoScrollEnabled = false
        }
    }

    val lastMessageId = visibleMessages.lastOrNull()?.id

    var previousMessageId by remember(streamerSlug) { mutableStateOf(lastMessageId) }
    LaunchedEffect(lastMessageId) {
        if (lastMessageId != null && visibleMessages.isNotEmpty()) {
            if (autoScrollEnabled && !isUserDragging) {
                listState.scrollToItem(visibleMessages.lastIndex)
                unreadCount = 0
            } else {
                val previousIndex = visibleMessages.indexOfLast { it.id == previousMessageId }
                unreadCount += if (previousIndex >= 0) visibleMessages.lastIndex - previousIndex else visibleMessages.size
            }
        }
        previousMessageId = lastMessageId
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalChatAnimationsEnabled provides true) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Chat Header with Options button
        ChatHeader(
            connectionState = connectionState,
            showOptions = showOptions,
            onToggleOptions = { showOptions = true },
            onHideChat = onHideChat,
            showChatters = showChatters,
            chatterCount = activeChatters?.totalCount,
            onToggleChatters = { showChatters = !showChatters },
            chattersEnabled = channelId != null && !isReplay
        )

        // Chat Options Modal BottomSheet
        if (showOptions) {
            val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
            ModalBottomSheet(
                onDismissRequest = { showOptions = false },
                sheetState = sheetState,
                sheetMaxWidth = 640.dp,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.chat_settings),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!isReplay) {
                            SettingSwitch(
                                key = SettingsKeys.AUTO_CONNECT_CHAT,
                                title = stringResource(R.string.chat_auto_connect_title),
                                description = stringResource(R.string.chat_auto_connect_description)
                            )

                            val isDisconnected = connectionState == ChatConnectionState.DISCONNECTED
                            SettingCard(
                                title = stringResource(R.string.chat_disconnect_title),
                                description = stringResource(if (isDisconnected) R.string.chat_disconnected_description else R.string.chat_disconnect_description),
                                enabled = !isDisconnected,
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                titleColor = MaterialTheme.colorScheme.error,
                                descriptionColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                onClick = {
                                    onDisconnectClick()
                                    showOptions = false
                                }
                            )

                        }

                        com.lechixy.kick.components.settings.ChatTimestampSetting()

                        SettingSlider(
                            key = SettingsKeys.CHAT_FONT_SIZE,
                            title = stringResource(R.string.chat_font_size_title),
                            description = stringResource(R.string.chat_font_size_description),
                            valueRange = 10f..24f,
                            steps = 13,
                            valueFormatter = { "${it}sp" }
                        )

                        SettingSlider(
                            key = SettingsKeys.CHAT_MESSAGE_SPACING,
                            title = stringResource(R.string.chat_spacing_title),
                            description = stringResource(R.string.chat_spacing_description),
                            valueRange = 0f..25f,
                            steps = 24,
                            valueFormatter = { "${it}dp" }
                        )

                        Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                    }
                }
            }
        }

        if (showChatters) {
            ActiveChattersContent(activeChatters, activeChattersError,
                onRetry = { channelId?.let(community::loadChatters) },
                onUserClick = { user -> streamerSlug?.let { community.openProfile(it, user, subscriberBadges = subscriberBadges) } },
                modifier = Modifier.fillMaxWidth().weight(1f).graphicsLayer {
                    translationX = size.width * .2f * chattersBackProgress
                    alpha = 1f - chattersBackProgress
                })
        } else {
        // Chat Message List
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (visibleMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (connectionState) {
                        ChatConnectionState.CONNECTING -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AppLoadingIndicator(
                                    modifier = Modifier.size(40.dp), contained = true
                                )
                                Text(
                                    text = stringResource(if (isReplay) R.string.recorded_chat_loading else R.string.chat_connecting_message),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        ChatConnectionState.DISCONNECTED -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = stringResource(if (!autoConnectChat) R.string.chat_auto_connect_disabled else R.string.chat_disconnected_message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Button(
                                    onClick = onConnectClick,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.chat_connect_action))
                                }
                            }
                        }

                        else -> {
                            Text(
                                text = stringResource(R.string.chat_empty_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                        .onGloballyPositioned { chatBackdropPosition = it.positionInRoot() }
                        .drawWithContent {
                            if (!isAtBottom) {
                                chatBackdrop.record { this@drawWithContent.drawContent() }
                                drawLayer(chatBackdrop)
                            } else drawContent()
                        },
                    verticalArrangement = Arrangement.spacedBy(messageSpacing.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = visibleMessages,
                        key = { it.id }
                    ) { message ->
                        Column {
                            if (message.id == firstNewMessageId) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    HorizontalDivider(Modifier.weight(1f))
                                    Text(stringResource(R.string.chat_new_messages),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    HorizontalDivider(Modifier.weight(1f))
                                }
                            }
                            SwipeToReply(enabled = !isReplay && accountSession.signedIn && message.sender?.id != null &&
                                !message.isDeleted && message.type in listOf("message", "reply"), onReply = { onReplyTo(message) }) {
                            ChatMessageItem(
                                message = message,
                                fontSizeSp = fontSize,
                                timestamp = remember(message.createdAt, message.recordingOffsetMs, timestampMode, isReplay, timestampZone) {
                                    com.lechixy.kick.data.chat.model.chatTimestamp(message, timestampMode, isReplay, timestampZone)
                                },
                                subscriberBadges = subscriberBadges,
                                userColors = userColors,
                                streamerUsername = streamerUsername,
                                streamerSlug = streamerSlug,
                                isOwnMessage = accountSession.signedIn && isOwnChatMessage(message,
                                    accountSession.profile?.userId, accountSession.profile?.name),
                                isFavorite = favorites.contains(message.sender?.id ?: message.userId, message.sender?.username, message.sender?.slug),
                                replyHighlight = if (message.id == highlightedReply) highlightSequence else 0,
                                onReplyClick = { originalId ->
                                    if (currentVisibleMessages.any { it.id == originalId }) {
                                        replyNavigationJob?.cancel()
                                        autoScrollEnabled = false
                                        navigatingReply = true
                                        replyNavigationJob = scope.launch {
                                            try {
                                                val index = currentVisibleMessages.indexOfFirst { it.id == originalId }
                                                if (index >= 0) {
                                                    listState.scrollToItem(index)
                                                    // Measure the actual row (replies and emotes can change height)
                                                    // before aligning its center with the chat viewport center.
                                                    withFrameNanos { }
                                                    val layout = listState.layoutInfo
                                                    val target = layout.visibleItemsInfo.firstOrNull { it.key == originalId }
                                                    if (target != null) {
                                                        val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                                                        listState.scrollBy(target.offset + target.size / 2f - viewportCenter)
                                                    }
                                                    highlightedReply = originalId
                                                    highlightSequence++
                                                }
                                            } finally { navigatingReply = false }
                                        }
                                    }
                                },
                                onUserClick = { sender -> streamerSlug?.let { slug ->
                                    community.openProfile(slug, com.lechixy.kick.data.chat.model.ActiveChatter(
                                        username = sender.username, slug = sender.slug.orEmpty()), sender, subscriberBadges)
                                } }
                            )
                            }
                        }
                    }
                }
            }

            val currentPrimaryColor by rememberUpdatedState(MaterialTheme.colorScheme.primary)
            val tickerEventQueue = remember {
                mutableStateListOf<ChatTickerOverlayItem>()
            }

            LaunchedEffect(streamerSlug) {
                tickerEventQueue.clear()
            }

            LaunchedEffect(chatEvents) {
                chatEvents.collect { wsEvent ->
                    val tickerItem = when (wsEvent) {
                        is ChatWebSocketEvent.StreamHosted -> {
                            val raid = wsEvent.event
                            val raidCount = raid.message.numberOfViewers
                            // min 20_000L max 60_000L
                            val durationDependingOnRaidCount = (raidCount * 1000L).coerceIn(20_000L, 60_000L)
                            ChatTickerOverlayItem(
                                id = "raid_${raid.message.id}",
                                iconRes = R.drawable.swords_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                                accentColor = currentPrimaryColor,
                                durationMillis = durationDependingOnRaidCount,
                                source = ChatTickerEventSource.Raid(raid)
                            )
                        }
                        is ChatWebSocketEvent.GiftedSubscriptions -> {
                            buildGiftedSubscriptionsTickerItem(
                                event = wsEvent.event,
                                primaryColor = currentPrimaryColor
                            )
                        }

                        is ChatWebSocketEvent.ChannelSubscribed -> {
                            buildChannelSubscriptionTickerItem(
                                event = wsEvent.event,
                                primaryColor = currentPrimaryColor
                            )
                        }

                        is ChatWebSocketEvent.Celebration -> {
                            buildCelebrationTickerItem(
                                celebration = wsEvent.event,
                                senderUsername = wsEvent.event.sender?.username
                                    ?: wsEvent.event.username,
                                senderColorHex = wsEvent.event.sender?.identity?.color,
                                primaryColor = currentPrimaryColor
                            )
                        }

                        is ChatWebSocketEvent.MessageReceived -> {
                            val msg = wsEvent.message
                            if (msg.isCelebration) {
                                val celebration =
                                    msg.metadata?.celebration ?: ChatCelebration(id = msg.id)
                                buildCelebrationTickerItem(
                                    celebration = celebration,
                                    senderUsername = msg.sender?.username,
                                    senderColorHex = msg.sender?.identity?.color,
                                    primaryColor = currentPrimaryColor
                                )
                            } else {
                                null
                            }
                        }

                        else -> null
                    }
                    if (tickerItem != null) {
                        if (tickerEventQueue.none { it.id == tickerItem.id }) tickerEventQueue.add(tickerItem)
                    }
                }
            }

            var tickerOverlayHeightPx by remember { mutableIntStateOf(0) }
            val overlapGapAdjustmentPx = with(LocalDensity.current) { 4.dp.roundToPx() }
            val targetPinnedOffsetPx = if (tickerOverlayHeightPx > 0) {
                (tickerOverlayHeightPx - overlapGapAdjustmentPx).coerceAtLeast(0)
            } else {
                0
            }
            val animatedPinnedOffsetPx by animateIntAsState(
                targetValue = targetPinnedOffsetPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "pinned_message_slide_down"
            )

            // Event Ticker Overlay above PinnedMessageOverlay
            ChatEventTickerOverlay(
                queue = tickerEventQueue,
                userColors = userColors,
                fontSizeSp = fontSize,
                onActiveHeightChanged = { tickerOverlayHeightPx = it },
                onUsernameClick = { username -> streamerSlug?.let { slug ->
                    community.openProfile(slug, com.lechixy.kick.data.chat.model.ActiveChatter(username = username), subscriberBadges = subscriberBadges)
                } },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            )

            // Pinned Message Overlay at top of chat (slides down smoothly when ticker overlay is active)
            PinnedMessageOverlay(
                pinnedMessage = pinnedMessage,
                fontSizeSp = fontSize,
                subscriberBadges = subscriberBadges,
                streamerUsername = streamerUsername,
                streamerSlug = streamerSlug,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, animatedPinnedOffsetPx) }
                    .fillMaxWidth()
            )

            // Floating "Scroll to bottom" button with unread counter
            androidx.compose.animation.AnimatedVisibility(
                visible = !autoScrollEnabled && !isAtBottom && visibleMessages.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                FrostedChatScrollButton(unreadCount, chatBackdrop, chatBackdropPosition) {
                    replyNavigationJob?.cancel()
                    highlightedReply = null
                    autoScrollEnabled = true
                    unreadCount = 0
                    scope.launch {
                        val lastIdx = visibleMessages.lastIndex
                        if (lastIdx >= 0) listState.scrollToItem(lastIdx)
                    }
                }
            }
        }
        if (highTraffic) {
            Text(stringResource(R.string.chat_high_traffic),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
        }
        composer()
        }
    }
    }
}

@Composable
internal fun PinnedMessageOverlay(
    pinnedMessage: PinnedMessageInfo?,
    fontSizeSp: Int,
    subscriberBadges: List<SubscriberBadge>,
    streamerUsername: String?,
    streamerSlug: String?,
    modifier: Modifier = Modifier
) {
    var cachedPinnedMessage by remember { mutableStateOf<PinnedMessageInfo?>(null) }
    var isHidden by remember { mutableStateOf(false) }
    var enterFromHide by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    var restoreSequence by remember { mutableIntStateOf(0) }
    var hideSide by androidx.compose.runtime.saveable.rememberSaveable(streamerSlug) { mutableIntStateOf(1) }
    val scaleAnim = remember { Animatable(0f) }

    if (pinnedMessage != null) {
        cachedPinnedMessage = pinnedMessage
    }

    val currentMessageId = pinnedMessage?.message?.id
    val currentContent = pinnedMessage?.message?.content

    LaunchedEffect(currentMessageId, currentContent) {
        if (pinnedMessage != null) {
            enterFromHide = false
            isHidden = false
            isExpanded = false
            scaleAnim.snapTo(0f)
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    val showCard = pinnedMessage != null && !isHidden
    val showUnhidePin = pinnedMessage != null && isHidden

    Box(modifier = modifier) {
        // Main Pinned Message Surface
        AnimatedVisibility(
            visible = showCard,
            enter = if (enterFromHide) {
                slideInHorizontally(
                    animationSpec = tween(durationMillis = 280, delayMillis = 140),
                    initialOffsetX = { fullWidth -> hideSide * (fullWidth + 40) }
                ) + fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 140))
            } else {
                fadeIn(animationSpec = tween(durationMillis = 120))
            },
            exit = slideOutHorizontally(
                animationSpec = tween(durationMillis = 260),
                targetOffsetX = { fullWidth -> hideSide * (fullWidth + 40) }
            ) + fadeOut(animationSpec = tween(durationMillis = 200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            val activePin = cachedPinnedMessage ?: return@AnimatedVisibility
            val msg = activePin.message ?: return@AnimatedVisibility
            val sender = msg.sender
            val pinnedBy = activePin.pinnedBy

            val headerFontSizeSp = (fontSizeSp - 2).coerceAtLeast(10)
            val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            val primaryColor = MaterialTheme.colorScheme.primary

            val senderColor = remember(sender?.identity?.color, primaryColor) {
                ChatTextParser.parseHexColor(sender?.identity?.color, primaryColor)
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            val senderBadges = remember(sender?.identity, subscriberBadges, context) {
                ChatTextParser.resolveBadges(sender?.identity, subscriberBadges, context = context)
            }
            val sentByHeader = ChatTextParser.parseUserHeaderWithBadges(
                text = stringResource(R.string.sent_by),
                textColor = mutedColor,
                senderName = sender?.username ?: "Unknown",
                senderColor = senderColor,
                fontSizeSp = headerFontSizeSp,
                badges = senderBadges,
                leadingIconRes = R.drawable.push_pin_24dp,
                leadingIconTint = primaryColor
            )

            val pinnedContent = msg.messageDeleted?.let { resolveDeletedMessageText(it) } ?: msg.content
            val parsedBody = ChatTextParser.parseContentOnly(
                content = pinnedContent,
                fontSizeSp = fontSizeSp,
                streamerUsername = streamerUsername,
                streamerSlug = streamerSlug
            )

            key(currentMessageId, restoreSequence) {
            com.lechixy.kick.components.SwipeDismissBox(
                allowUp = false,
                onDismiss = { direction ->
                    hideSide = if (direction == com.lechixy.kick.components.SwipeDismissDirection.Left) -1 else 1
                    enterFromHide = true
                    isHidden = true
                }, modifier = Modifier.fillMaxWidth()
            ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .graphicsLayer {
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isExpanded = !isExpanded }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Row 1: PIN_ICON Sent by BADGES USERNAME ... Hide button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = sentByHeader.annotatedString,
                            inlineContent = sentByHeader.inlineContent,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = headerFontSizeSp.sp,
                                lineHeight = (headerFontSizeSp + 4).sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    enterFromHide = true
                                    isHidden = true
                                }
                        ) {
                            Text(
                                text = stringResource(R.string.hide),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Row 2: Message content (1 line when collapsed, all lines when expanded)
                    Text(
                        text = parsedBody.annotatedString,
                        inlineContent = parsedBody.inlineContent,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp + 5).sp
                        ),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Row 3: Pinned by BADGES USERNAME (no icon, only when expanded)
                    if (isExpanded && pinnedBy != null) {
                        val isPinnedBySameSender = remember(sender, pinnedBy) {
                            sender != null && (
                                    (sender.id != null && pinnedBy.id != null && sender.id == pinnedBy.id) ||
                                            (sender.username.isNotBlank() && sender.username.equals(
                                                pinnedBy.username,
                                                ignoreCase = true
                                            )) ||
                                            (!sender.slug.isNullOrBlank() && sender.slug.equals(
                                                pinnedBy.slug,
                                                ignoreCase = true
                                            ))
                                    )
                        }

                        if (isPinnedBySameSender) {
                            Text(
                                text = stringResource(R.string.pinned_by_the_author_of_the_message),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = mutedColor,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = headerFontSizeSp.sp,
                                    lineHeight = (headerFontSizeSp + 4).sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            )
                        } else {
                            val pinnedByColor = remember(pinnedBy.identity?.color, primaryColor) {
                                ChatTextParser.parseHexColor(pinnedBy.identity?.color, primaryColor)
                            }
                            val pinnedByBadges = remember(pinnedBy.identity, subscriberBadges, context) {
                                ChatTextParser.resolveBadges(pinnedBy.identity, subscriberBadges, context = context)
                            }
                            val pinnedByHeader = ChatTextParser.parseUserHeaderWithBadges(
                                text = stringResource(R.string.pinned_by),
                                textColor = mutedColor,
                                senderName = pinnedBy.username,
                                senderColor = pinnedByColor,
                                fontSizeSp = headerFontSizeSp,
                                badges = pinnedByBadges,
                                leadingIconRes = null
                            )

                            Text(
                                text = pinnedByHeader.annotatedString,
                                inlineContent = pinnedByHeader.inlineContent,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = headerFontSizeSp.sp,
                                    lineHeight = (headerFontSizeSp + 4).sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        }
        }
        // Restore the pin on the side most recently used to dismiss it.
        AnimatedVisibility(
            visible = showUnhidePin,
            enter = slideInHorizontally(
                animationSpec = tween(durationMillis = 250, delayMillis = 200),
                initialOffsetX = { fullWidth -> hideSide * (fullWidth + 40) }
            ) + fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 200)),
            exit = slideOutHorizontally(
                animationSpec = tween(durationMillis = 180),
                targetOffsetX = { fullWidth -> hideSide * (fullWidth + 40) }
            ) + fadeOut(animationSpec = tween(durationMillis = 150)),
            modifier = Modifier
                .align(if (hideSide < 0) androidx.compose.ui.AbsoluteAlignment.TopLeft else androidx.compose.ui.AbsoluteAlignment.TopRight)
                .padding(top = 6.dp, start = 10.dp, end = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { restoreSequence++; isHidden = false }
            ) {
                Box(
                    modifier = Modifier.padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.push_pin_24dp),
                        contentDescription = stringResource(R.string.chat_show_pinned),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(
    connectionState: ChatConnectionState,
    showOptions: Boolean,
    onToggleOptions: () -> Unit,
    onHideChat: (() -> Unit)? = null,
    showChatters: Boolean = false,
    chatterCount: Int? = null,
    onToggleChatters: () -> Unit = {},
    chattersEnabled: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onHideChat != null) {
                IconButton(
                    onClick = onHideChat,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.keyboard_double_arrow_right_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        contentDescription = stringResource(R.string.hide_chat),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = stringResource(if (showChatters) R.string.active_chatters_title else R.string.chat),
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showChatters) Text("(${chatterCount ?: "…"})", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val (statusText, statusColor) = when (connectionState) {
                ChatConnectionState.CONNECTED -> stringResource(R.string.live) to MaterialTheme.colorScheme.primary
                ChatConnectionState.CONNECTING -> stringResource(R.string.connecting) to MaterialTheme.colorScheme.secondary
                ChatConnectionState.DISCONNECTED -> stringResource(R.string.disconnected) to MaterialTheme.colorScheme.outline
                ChatConnectionState.ERROR -> stringResource(R.string.error) to MaterialTheme.colorScheme.error
            }

            if (!showChatters) Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onToggleChatters, enabled = chattersEnabled, modifier = Modifier.size(32.dp)) {
                Icon(painterResource(if (showChatters) R.drawable.group_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                    else R.drawable.group_24dp_e3e3e3_fill0_wght400_grad0_opsz24), stringResource(R.string.active_chatters_title), Modifier.size(20.dp)
                )
            }
            if (!showChatters) IconButton(
                onClick = onToggleOptions,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(if (showOptions) R.drawable.settings_24dp_e3e3e3_fill1_wght400_grad0_opsz24 else R.drawable.settings_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = stringResource(R.string.chat_settings),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

sealed interface ChatTickerEventSource {
    data class Raid(val event: com.lechixy.kick.data.chat.model.StreamHostedEvent) : ChatTickerEventSource
    data class GiftedSubscriptions(val event: GiftedSubscriptionsEvent) : ChatTickerEventSource
    data class Celebration(val event: ChatCelebration, val username: String = "", val colorHex: String? = null) :
        ChatTickerEventSource

    data class ChannelSubscription(val event: ChannelSubscriptionEvent) : ChatTickerEventSource
}

data class ChatTickerOverlayItem(
    val id: String,
    @DrawableRes val iconRes: Int,
    val accentColor: Color? = null,
    val durationMillis: Long = 7_000L,
    val isInfinite: Boolean = false,
    val source: ChatTickerEventSource
)

private fun buildCelebrationTickerItem(
    celebration: ChatCelebration,
    senderUsername: String?,
    senderColorHex: String?,
    primaryColor: Color
): ChatTickerOverlayItem {
    val username = senderUsername?.takeIf { it.isNotBlank() }
        ?: celebration.sender?.username?.takeIf { it.isNotBlank() } ?: celebration.username
    return ChatTickerOverlayItem(
        id = "celebration_${celebration.id.ifBlank { username }}_${System.nanoTime()}",
        iconRes = R.drawable.celebration_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
        accentColor = primaryColor,
        durationMillis = 12_000L,
        source = ChatTickerEventSource.Celebration(celebration, username, senderColorHex)
    )
}

private fun buildChannelSubscriptionTickerItem(
    event: ChannelSubscriptionEvent,
    primaryColor: Color
) = ChatTickerOverlayItem(
    id = "sub_${event.username}_${System.nanoTime()}",
    iconRes = R.drawable.star_shine_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
    accentColor = primaryColor,
    durationMillis = 12_000L,
    source = ChatTickerEventSource.ChannelSubscription(event)
)

private fun buildGiftedSubscriptionsTickerItem(
    event: GiftedSubscriptionsEvent,
    primaryColor: Color
) = ChatTickerOverlayItem(
    id = "gift_${event.correlationId ?: event.gifterUsername}_${System.nanoTime()}",
    iconRes = R.drawable.featured_seasonal_and_gifts_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
    accentColor = primaryColor,
    durationMillis = 15_000L,
    source = ChatTickerEventSource.GiftedSubscriptions(event)
)

@Composable
private fun ChatEventTickerOverlay(
    queue: MutableList<ChatTickerOverlayItem>,
    userColors: Map<String, String>,
    fontSizeSp: Int,
    onActiveHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onUsernameClick: ((String) -> Unit)? = null
) {
    val currentItem = queue.firstOrNull()
    var displayedItem by remember { mutableStateOf<ChatTickerOverlayItem?>(null) }
    var isShowing by remember { mutableStateOf(false) }
    var isUserScrolling by remember { mutableStateOf(false) }
    var measuredHeightPx by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(1f) }

    val scaleAnim = remember { Animatable(0f) }

    if (currentItem != null) {
        displayedItem = currentItem
    }

    LaunchedEffect(isShowing, measuredHeightPx) {
        onActiveHeightChanged(if (isShowing) measuredHeightPx else 0)
    }

    LaunchedEffect(currentItem?.id) {
        if (currentItem == null) {
            isShowing = false
            isUserScrolling = false
            if (scaleAnim.value > 0f) {
                scaleAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 240,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            displayedItem = null
            return@LaunchedEffect
        }

        displayedItem = currentItem
        isShowing = true
        isUserScrolling = false
        progress = 1f
        scaleAnim.snapTo(0f)

        // Enter animation: same bouncy spring scale-in as PinnedMessageOverlay
        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )

        val durationMs = currentItem.durationMillis.coerceAtLeast(1_000L)
        var remainingMs = durationMs.toFloat()
        var lastFrameNanos = withFrameNanos { it }
        while (isActive && (currentItem.isInfinite || remainingMs > 0f)) {
            withFrameNanos { frameNanos ->
                val deltaMs = ((frameNanos - lastFrameNanos).coerceAtLeast(0L)) / 1_000_000f
                lastFrameNanos = frameNanos
                if (!isUserScrolling) {
                    remainingMs -= deltaMs
                    if (currentItem.isInfinite && remainingMs <= 0f) {
                        remainingMs = durationMs.toFloat()
                    }
                    progress = (remainingMs / durationMs.toFloat()).coerceIn(0f, 1f)
                }
            }
        }

        if (!currentItem.isInfinite) {
            // If no more items are waiting in the queue, start sliding PinnedMessageOverlay back up during scale-out
            if (queue.size <= 1) {
                isShowing = false
            }

            // Exit animation: reverse of enter (scale out to 0)
            scaleAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 260,
                    easing = FastOutSlowInEasing
                )
            )

            if (queue.firstOrNull()?.id == currentItem.id) {
                queue.removeAt(0)
            }
            if (queue.isEmpty()) {
                isShowing = false
                displayedItem = null
            }
        }
    }

    val active = displayedItem ?: return
    val defaultAccent = MaterialTheme.colorScheme.primary
    val accentColor = active.accentColor ?: defaultAccent
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.ui.platform.LocalConfiguration.current
    val segments = when (val source = active.source) {
        is ChatTickerEventSource.GiftedSubscriptions -> giftedEventSegments(context, source.event, userColors, accentColor)
        is ChatTickerEventSource.Celebration -> subscriptionEventSegments(context, source.username,
            source.event.totalMonths,
            userColor = ChatTextParser.parseHexColor(source.colorHex ?: source.event.sender?.identity?.color
                ?: userColors[source.username.lowercase()], accentColor), primaryColor = accentColor)
        is ChatTickerEventSource.ChannelSubscription -> subscriptionEventSegments(context, source.event.username,
            source.event.totalMonths ?: source.event.months,
            (source.event.userIds.size - 1).coerceAtLeast(0),
            userColor = ChatTextParser.parseHexColor(userColors[source.event.username.lowercase()], accentColor),
            primaryColor = accentColor)
        is ChatTickerEventSource.Raid -> raidEventSegments(context, source.event.user.username,
            source.event.message.numberOfViewers,
            ChatTextParser.parseHexColor(userColors[source.event.user.username.lowercase()], accentColor))
    }

    Box(
        modifier = modifier.onSizeChanged { size ->
            if (size.height > 0) {
                measuredHeightPx = size.height
            }
        }
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    alpha = scaleAnim.value.coerceIn(0f, 1f)
                }
                .clip(RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    key(active.id) {
                        ContinuousTickerRow(
                            iconRes = active.iconRes,
                            iconTint = accentColor,
                            segments = segments,
                            fontSizeSp = fontSizeSp,
                            defaultBoldColor = accentColor,
                            defaultTextColor = MaterialTheme.colorScheme.onSurface,
                            onScrollingChanged = { isUserScrolling = it },
                            onUsernameClick = onUsernameClick,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (isShowing) {
                                    isShowing = false
                                    isUserScrolling = false
                                    queue.clear()
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                contentDescription = stringResource(R.string.hide),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Full-width bottom progress bar whose width decreases to 0 as time runs out
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(accentColor)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ContinuousTickerRow(
    @DrawableRes iconRes: Int,
    iconTint: Color,
    segments: List<ChatTextParser.EventTextSegment>,
    fontSizeSp: Int,
    defaultBoldColor: Color,
    defaultTextColor: Color,
    modifier: Modifier = Modifier,
    onScrollingChanged: (Boolean) -> Unit = {},
    onUsernameClick: ((String) -> Unit)? = null,
    speedDpPerSecond: Float = 48f
) {
    val safeFontSizeSp = fontSizeSp.coerceAtLeast(10)
    val annotatedString = remember(segments, defaultBoldColor, defaultTextColor, onUsernameClick) {
        ChatTextParser.buildEventAnnotatedString(
            segments = segments,
            boldColor = defaultBoldColor,
            mutedColor = defaultTextColor,
            onUsernameClick = onUsernameClick
        )
    }

    val density = LocalDensity.current
    val speedPxPerSec = remember(density, speedDpPerSecond) {
        with(density) { speedDpPerSecond.dp.toPx() }
    }
    val minGapPx = remember(density) {
        with(density) { 24.dp.toPx() }
    }

    var stridePx by remember { mutableFloatStateOf(0f) }
    var offsetPx by remember { mutableFloatStateOf(0f) }

    val scrollableState = rememberScrollableState { delta ->
        if (stridePx > 0f) {
            offsetPx = ((offsetPx - delta) % stridePx + stridePx) % stridePx
        }
        delta
    }
    val isUserScrolling = scrollableState.isScrollInProgress

    LaunchedEffect(isUserScrolling) {
        onScrollingChanged(isUserScrolling)
    }

    LaunchedEffect(speedPxPerSec, stridePx, isUserScrolling) {
        if (stridePx <= 0f || speedPxPerSec <= 0f || isUserScrolling) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameNanos ->
                val deltaSec = ((frameNanos - lastFrameNanos).coerceAtLeast(0L)) / 1_000_000_000f
                lastFrameNanos = frameNanos
                offsetPx = ((offsetPx + speedPxPerSec * deltaSec) % stridePx + stridePx) % stridePx
            }
        }
    }

    @Composable
    fun TickerUnit() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size((safeFontSizeSp + 4).dp)
            )
            Text(
                text = annotatedString,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = safeFontSizeSp.sp,
                    lineHeight = (safeFontSizeSp + 4).sp
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }

    Layout(
        content = {
            TickerUnit()
            TickerUnit()
            TickerUnit()
        },
        modifier = modifier
            .clipToBounds()
            .scrollable(
                state = scrollableState,
                orientation = Orientation.Horizontal
            )
    ) { measurables, constraints ->
        val unboundedConstraints = constraints.copy(
            minWidth = 0,
            maxWidth = Constraints.Infinity
        )
        val placeables = measurables.map { it.measure(unboundedConstraints) }
        val first = placeables.firstOrNull()
        val second = placeables.getOrNull(1)
        val third = placeables.getOrNull(2)
        val itemWidth = first?.width ?: 0
        val itemHeight = first?.height ?: 0
        val containerWidth = constraints.maxWidth

        val computedStride = itemWidth.toFloat() + minGapPx
        if (stridePx != computedStride) {
            stridePx = computedStride
        }

        layout(width = containerWidth, height = itemHeight) {
            val currentOffset = if (computedStride > 0f) {
                ((offsetPx % computedStride) + computedStride) % computedStride
            } else {
                0f
            }
            val x1 = -currentOffset.roundToInt()
            val x2 = (computedStride - currentOffset).roundToInt()
            val x3 = (computedStride * 2 - currentOffset).roundToInt()
            first?.placeRelative(x = x1, y = 0)
            second?.placeRelative(x = x2, y = 0)
            third?.placeRelative(x = x3, y = 0)
        }
    }
}

