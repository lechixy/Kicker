package com.lechixy.kick.components.channel.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

import com.lechixy.kick.R
import com.lechixy.kick.components.account.AccountSignInState
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.model.AccountSession
import com.lechixy.kick.data.account.remote.string
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.policy.ChannelPointsPolicy
import com.lechixy.kick.data.chat.model.ActiveChatter
import com.lechixy.kick.data.chat.model.ChatIdentity
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.CompletionKind
import com.lechixy.kick.data.chat.model.EmoteCategory
import com.lechixy.kick.data.chat.model.EmoteCategoryKind
import com.lechixy.kick.data.chat.model.EmotePickerUiState
import com.lechixy.kick.data.chat.model.ChatPointsUpdate
import com.lechixy.kick.data.chat.model.chatCompletionToken
import com.lechixy.kick.data.chat.model.filterEmotes
import com.lechixy.kick.data.chat.model.insertCompletion
import com.lechixy.kick.data.chat.model.insertEmote
import com.lechixy.kick.data.chat.model.mentionMatches
import com.lechixy.kick.data.chat.policy.ChatCommandAction
import com.lechixy.kick.data.chat.policy.ChatCommandException
import com.lechixy.kick.data.chat.policy.ChatCommands
import com.lechixy.kick.data.chat.policy.ChatComposerPolicy
import com.lechixy.kick.data.chat.policy.ChatInputBlock
import com.lechixy.kick.data.chat.policy.ChatPolicy
import com.lechixy.kick.data.chat.policy.ChatRestriction
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.data.chat.repository.AccountChatRepository
import com.lechixy.kick.screens.channel.DropsUiState
import com.lechixy.kick.screens.channel.DropsViewModel
import com.lechixy.kick.util.FormatUtils

internal val chatBalanceIconSize = 20.dp

@Composable
fun AccountChatComposer(
    channel: ChannelDetail,
    connectionState: ChatConnectionState,
    settingsPatch: JsonObject = JsonObject(emptyMap()),
    pointsUpdated: ChatPointsUpdate? = null,
    emotes: com.lechixy.kick.data.chat.model.EmotePickerUiState = com.lechixy.kick.data.chat.model.EmotePickerUiState(),
    onRetryEmotes: () -> Unit = {},
    onIdentityPreviewClick: ((com.lechixy.kick.data.chat.model.ChatIdentity) -> Unit)? = null,
    replyTo: ChatMessage? = null, onCancelReply: () -> Unit = {},
    recentMessages: List<ChatMessage> = emptyList(), userColors: Map<String, String> = emptyMap(),
    onUserCommand: (String) -> Unit = {},
    onConsumePoints: (Long) -> Long = { 0L },
    drops: DropsViewModel? = null,
    chatContent: @Composable () -> Unit = {}
) {
    val session by AccountGraph.auth.session.collectAsStateWithLifecycle()
    key(session.generation, channel.slug) {
        ComposerContent(channel, connectionState, session, settingsPatch, pointsUpdated, emotes, onRetryEmotes, onIdentityPreviewClick,
            replyTo, onCancelReply, recentMessages, userColors, onUserCommand, onConsumePoints, drops, chatContent)
    }
}

@Composable
private fun ComposerContent(
    channel: ChannelDetail, connectionState: ChatConnectionState, session: AccountSession,
    settingsPatch: JsonObject, pointsUpdated: ChatPointsUpdate?,
    emotes: com.lechixy.kick.data.chat.model.EmotePickerUiState, onRetryEmotes: () -> Unit,
    onIdentityPreviewClick: ((com.lechixy.kick.data.chat.model.ChatIdentity) -> Unit)?,
    replyTo: ChatMessage?, onCancelReply: () -> Unit, recentMessages: List<ChatMessage>, userColors: Map<String, String>,
    onUserCommand: (String) -> Unit,
    onConsumePoints: (Long) -> Long,
    drops: DropsViewModel?,
    chatContent: @Composable () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val chatActive = LocalChatContentActive.current
    val scope = rememberCoroutineScope()
    var content by rememberSaveable { mutableStateOf("") }
    val permissions: ChatComposerAccessViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val permissionState by permissions.state.collectAsStateWithLifecycle()
    val accessState = permissionState.takeIf { it.matches(channel, session) }
    val effectiveAccess = accessState?.access
    val connected = connectionState == ChatConnectionState.CONNECTED
    val permissionFailed = connected && accessState?.failed == true
    val loading = connected && effectiveAccess == null && !permissionFailed
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var tick by remember { mutableLongStateOf(0L) }
    var points by remember { mutableStateOf<Long?>(null) }
    var pointsEnabled by remember { mutableStateOf<Boolean?>(null) }
    var kicks by remember { mutableStateOf<Long?>(null) }
    var showPointsDialog by remember { mutableStateOf(false) }
    var showIdentityDialog by remember { mutableStateOf(false) }
    var showDropsDialog by remember(channel.livestream?.id) { mutableStateOf(false) }
    var dropsSelection by remember(channel.livestream?.id) { mutableStateOf<Pair<String, String?>?>(null) }
    val previewId = if (chatActive && drops != null && channel.livestream != null) {
        DeveloperDropsPreview.requestId.collectAsChatState().value
    } else null
    val previewCampaign = if (previewId != null || dropsSelection?.first == DeveloperDropsPreview.campaignId) {
        developerDropsCampaign(
            channel.user?.username ?: channel.slug,
            channel.livestream?.categories?.firstOrNull()?.name ?: stringResource(R.string.developer_drops_sample_category)
        )
    } else null
    val dropsState = drops?.uiState?.collectAsChatState()?.value?.takeIf {
        channel.livestream != null && (it !is DropsUiState.Success ||
            (it.channelId == channel.id && it.livestreamId == channel.livestream.id &&
                it.accountGeneration == session.generation))
    } ?: DropsUiState.Empty
    val dropsButton: @Composable () -> Unit = {
        if (drops != null) {
            DropsRewardsButton(dropsState, onClick = {
                dropsSelection = null
                showDropsDialog = true
            })
        }
    }
    var showEmotes by rememberSaveable { mutableStateOf(false) }
    var emoteQuery by rememberSaveable { mutableStateOf("") }
    var emoteCategory by remember { mutableIntStateOf(0) }
    var selection by remember { mutableStateOf(androidx.compose.ui.text.TextRange.Zero) }
    val compact = LocalConfiguration.current.smallestScreenWidthDp < 600 && channel.livestream != null
    var expanded by rememberSaveable { mutableStateOf(false) }
    var inputFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val rulesFooter = chatRulesFooter(
        channelId = channel.id,
        userId = session.profile?.userId,
        rules = if (session.signedIn) effectiveAccess?.settings?.rules ?: settingsPatch.string("rules") else null,
        onAccepted = { expanded = true }
    )
    LaunchedEffect(rulesFooter != null) {
        if (rulesFooter != null) {
            focusManager.clearFocus()
            keyboard?.hide()
            showEmotes = false
        }
    }
    LaunchedEffect(replyTo?.id) { if (replyTo != null) expanded = true }
    val usagePreferences = remember(context) { context.getSharedPreferences("chat_emote_usage", android.content.Context.MODE_PRIVATE) }
    var usage by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    LaunchedEffect(Unit) {
        usage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            usagePreferences.all.mapNotNull { (key, value) -> key.toLongOrNull()?.let { it to (value as? Int ?: 0) } }.toMap()
        }
    }
    val pickerCategories = remember(emotes.categories, usage) {
        val available = emotes.categories.flatMap { it.emotes }.associateBy { it.id }
        val frequent = usage.entries.sortedByDescending { it.value }.mapNotNull { available[it.key] }.take(40)
        listOf(com.lechixy.kick.data.chat.model.EmoteCategory("frequently_used", "", com.lechixy.kick.data.chat.model.EmoteCategoryKind.FREQUENT, frequent)) + emotes.categories
    }
    var identitySaving by remember { mutableStateOf(false) }
    var lastPointUpdate by remember { mutableStateOf<Instant?>(null) }
    var lastEarnedAt by remember { mutableStateOf<Instant?>(null) }
    var lastEarnReason by remember { mutableStateOf<String?>(null) }
    var pointInterval by remember { mutableStateOf<Long?>(null) }
    var previousPointInterval by remember { mutableStateOf<Long?>(null) }
    val currentPointsEvent by rememberUpdatedState(pointsUpdated)
    LaunchedEffect(channel.id, session.generation, session.signedIn, connected, settingsPatch) {
        permissions.observe(channel, session, connected, settingsPatch)
    }
    val currentSession by rememberUpdatedState(session)
    val currentChannel by rememberUpdatedState(channel)
    DisposableEffect(lifecycle) {
        var resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> resumed = false
                Lifecycle.Event.ON_RESUME -> if (!resumed) {
                    resumed = true
                    refresh++
                    permissions.refresh(currentChannel, currentSession)
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(session.signedIn, refresh, connectionState == ChatConnectionState.CONNECTED, chatActive) {
        if (!session.signedIn || !chatActive) return@LaunchedEffect
        // Reconcile with the server while visible, including after a missed private publication.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    val before = currentPointsEvent
                    val settings = AccountGraph.chat.channelPointSettings(channel)
                    pointsEnabled = settings.enabled
                    val fetched = if (settings.enabled) AccountGraph.chat.points(channel) else null
                    val latest = currentPointsEvent
                    if (latest?.revision == before?.revision || latest == null ||
                        !ChannelPointsPolicy.accepts(latest.event, channel.id, AccountGraph.auth.session.value.profile?.userId))
                        {
                            if (points != null && points != fetched) lastPointUpdate = Instant.now()
                            points = fetched
                        }
                } catch (failure: Exception) { if (failure is CancellationException) throw failure }
                delay(60_000.milliseconds)
            }
        }
    }
    LaunchedEffect(session.signedIn, refresh) {
        if (!session.signedIn) return@LaunchedEffect
        launch {
            try { kicks = AccountGraph.chat.kicksBalance() }
            catch (failure: Exception) { if (failure is CancellationException) throw failure }
        }
    }
    val acceptedPoints = pointsUpdated?.takeIf {
        session.signedIn && ChannelPointsPolicy.accepts(it.event, channel.id, session.profile?.userId)
    }
    LaunchedEffect(acceptedPoints?.revision) {
        val update = acceptedPoints ?: return@LaunchedEffect
        val event = update.event
        val now = update.receivedAt
        lastPointUpdate = now
        // Infer a cadence only from consecutive awards of the same reason.
        if (event.points > 0 && event.reason.equals("WATCHED", ignoreCase = true)) {
            val interval = lastEarnedAt?.let { previous -> java.time.Duration.between(previous, now).seconds }
            if (lastEarnReason == event.reason && interval != null && interval in 30..3600) {
                pointInterval = previousPointInterval?.takeIf { previous -> kotlin.math.abs(previous - interval) <= 5 }
                    ?.let { interval }
                previousPointInterval = interval
            } else {
                pointInterval = null
                previousPointInterval = null
            }
            lastEarnedAt = now
            lastEarnReason = event.reason
        } else {
            pointInterval = null
            previousPointInterval = null
            lastEarnedAt = null
        }
        points = event.balance.toLong()
    }
    LaunchedEffect(session.signedIn, compact, expanded) {
        if (session.signedIn) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { delay((if (compact && !expanded) 1_000 else 250).milliseconds); tick++ }
        }
    }
    val currentReply by rememberUpdatedState(replyTo)
    val requester = remember { FocusRequester() }
    LaunchedEffect(replyTo?.id, expanded, rulesFooter != null) {
        if (replyTo != null && session.signedIn && rulesFooter == null && (!compact || expanded)) requester.requestFocus()
    }
    LaunchedEffect(showEmotes) { if (showEmotes) onRetryEmotes() }
    val community: com.lechixy.kick.components.channel.chat.ChatCommunityViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(key = "community-${channel.slug}")
    val chatters by community.chatters.collectAsStateWithLifecycle()
    val token = remember(content, selection) {
        if (selection.collapsed) chatCompletionToken(content, selection.start) else null
    }
    LaunchedEffect(token?.kind, channel.id) {
        if (token?.kind == CompletionKind.MENTION && chatters == null) community.loadChatters(channel.id)
    }
    val mentionUsers = remember(chatters, recentMessages, token?.kind, token?.query) {
        if (token?.kind != CompletionKind.MENTION) emptyList() else {
            val recent = recentMessages.takeLast(200).mapNotNull { it.sender }.map { ActiveChatter(it.username, it.slug.orEmpty()) }
            mentionMatches(chatters?.users.orEmpty() + recent + ActiveChatter(channel.user?.username ?: channel.slug, channel.slug), token.query)
        }
    }
    val ownChannel = session.signedIn && ChannelPointsPolicy.isOwnChannel(channel, session.profile?.userId)
    val presentation = remember(effectiveAccess, tick, session.profile) {
        effectiveAccess?.let {
            ChatComposerPolicy.evaluate(it.settings, it.member,
                AccountGraph.auth.accountCreatedAt?.let { date -> runCatching { Instant.parse(date) }.getOrNull() },
                Instant.now())
        }
    }
    val inputBlock = presentation?.block
    val inputBlockText = when (inputBlock) {
        ChatInputBlock.BANNED -> stringResource(R.string.chat_banned)
        ChatInputBlock.SUBSCRIBERS_ONLY -> stringResource(R.string.chat_subscribers_only)
        ChatInputBlock.FOLLOWERS_ONLY -> stringResource(R.string.chat_followers_only)
        ChatInputBlock.FOLLOW_DURATION -> stringResource(R.string.chat_follow_duration_required)
        ChatInputBlock.ACCOUNT_AGE -> stringResource(R.string.chat_account_age)
        ChatInputBlock.EMOTES_ONLY -> stringResource(R.string.chat_emotes_only)
        null -> null
    }
    val restrictionIssue = remember(effectiveAccess, tick, session.profile) {
        effectiveAccess?.let { AccountGraph.chat.restrictionIssue(channel.id, it) }
    }
    val restriction = restrictionIssue?.localized(context)
    val cooldown = remember(effectiveAccess, tick) {
        effectiveAccess?.let { AccountGraph.chat.cooldown(channel.id, it) }
    }
    var earnedPoints by remember { mutableLongStateOf(0L) }
    LaunchedEffect(acceptedPoints?.revision, chatActive, ownChannel, pointsEnabled == false) {
        if (!chatActive || ownChannel || pointsEnabled == false || acceptedPoints == null) {
            earnedPoints = 0L
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val earned = onConsumePoints(acceptedPoints.revision)
            if (earned > 0L) {
                earnedPoints += earned
                try {
                    delay(1_800.milliseconds)
                } finally {
                    earnedPoints = 0L
                }
            }
        }
    }
    val pointsButton: @Composable () -> Unit = {
        BalanceTooltip(points, unlimited = ownChannel, unavailable = pointsEnabled == false) {
            AnimatedPoints(points, Modifier.clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = stringResource(R.string.channel_points_title)) { showPointsDialog = true }
                .padding(vertical = 8.dp, horizontal = 4.dp),
                unlimited = ownChannel, unavailable = pointsEnabled == false, earnedPoints = earnedPoints)
        }
    }
    ChatComposerPanel(
        compact = compact,
        expanded = expanded,
        allowAutoHide = content.isBlank() && !inputFocused && !showEmotes && !showPointsDialog &&
            !showIdentityDialog && !showDropsDialog && replyTo == null && !sending,
        onExpandedChange = { expanded = it },
        onHide = {
            focusManager.clearFocus()
            keyboard?.hide()
            showEmotes = false
        },
        collapsedActions = if (session.signedIn || drops != null) {
            {
                if (session.signedIn) pointsButton()
                dropsButton()
            }
        } else null,
        chatContent = {
            val dismissals = drops?.dismissals?.collectAsChatState()?.value
            val realBanner = if (drops != null && dismissals != null && chatActive) {
                (dropsState as? DropsUiState.Success)?.campaigns?.firstOrNull {
                    it.banner != null && drops.dismissalKey(channel.id, it.id) !in dismissals
                }?.banner
            } else null
            key(channel.livestream?.id) {
                DropsChatContent(
                    banner = if (previewId != null) previewCampaign?.banner else realBanner,
                    previewId = previewId,
                    onDismiss = { campaignId ->
                        if (campaignId == DeveloperDropsPreview.campaignId) DeveloperDropsPreview.hide()
                        else drops?.dismissBanner(channel.id, campaignId)
                    },
                    onOpen = { campaignId, rewardId ->
                        dropsSelection = campaignId to rewardId
                        showDropsDialog = true
                    },
                    chatActive = chatActive,
                    chatContent = chatContent
                )
            }
        },
        rulesFooter = rulesFooter
    ) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!session.signedIn) {
                AccountSignInState(compact = true)
                dropsButton()
                return@Column
            }
            effectiveAccess?.settings?.let { settings ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Chat is open no need for any status
    //                if (presentation?.hasModes == false && inputBlock == null)
    //                    ChatMode(R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24, stringResource(R.string.chat_available))
                    if (presentation?.slowMode == true) ChatMode(R.drawable.snail_24dp_e3e3e3_fill1_wght400_grad0_opsz24,
                        stringResource(R.string.chat_slow_mode, settings.slowMode.durationSeconds))
                    if (presentation?.emotesOnly == true) ChatMode(R.drawable.mood_24dp_e3e3e3_fill0_wght400_grad0_opsz24, stringResource(R.string.chat_emotes_only))
                    if (presentation?.subscribersOnly == true) ChatMode(R.drawable.stars_2_24dp_e3e3e3_fill0_wght400_grad0_opsz24, stringResource(R.string.chat_subscribers_only))
                    if (presentation?.followersOnly == true) ChatMode(R.drawable.group_24dp_e3e3e3_fill0_wght400_grad0_opsz24, stringResource(R.string.chat_followers_only))
                    if (presentation?.accountAge == true) ChatMode(R.drawable.timer_24dp_e3e3e3_fill0_wght400_grad0_opsz24, stringResource(R.string.chat_account_age))
                }
            }
            val disabledReason = when {
                !connected -> stringResource(R.string.chat_connect_to_send)
                loading || permissionFailed -> null
                else -> restriction
            }
            when {
                loading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.chat_checking_permissions), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                permissionFailed -> Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.chat_permissions_failed), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { permissions.refresh(channel, session) }) {
                        Text(stringResource(R.string.chat_retry))
                    }
                }
                inputBlock == null && (cooldown == null || cooldown.remainingMillis == 0L) -> disabledReason?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            val validation = if (inputBlock == null && content.isNotEmpty()) ChatPolicy.messageIssue(content)?.let {
                ChatRestriction(it).localized(context) } else null
            ChatReplyPreview(replyTo, onCancelReply, channel.subscriberBadges, userColors, channel.user?.username, channel.slug)
            val availableCommands = ChatCommands.available(effectiveAccess?.member?.privileged == true, ownChannel)
            val canUseSubscriberEmotes = effectiveAccess?.member?.let { it.subscriber || it.privileged } == true
            val completionEmotes = remember(pickerCategories, token?.kind, token?.query, canUseSubscriberEmotes) {
                if (token?.kind != CompletionKind.EMOTE) emptyList() else filterEmotes(pickerCategories, token.query)
                    .filter { !it.subscribersOnly || canUseSubscriberEmotes }.take(20)
            }
            ChatCompletionPanel(token.takeIf { inputBlock == null && !sending }, mentionUsers,
                availableCommands.filter { it.name.startsWith(token?.query.orEmpty(), ignoreCase = true) },
                completionEmotes) { value ->
                token?.let {
                    val updated = insertCompletion(content, it, value)
                    if (ChatPolicy.messageIssue(updated.text) == null) { content = updated.text; selection = androidx.compose.ui.text.TextRange(updated.cursor) }
                }
            }
            ComposerAccessory(showEmotes) {
                com.lechixy.kick.components.channel.chat.EmotePickerSheet(
                    uiState = emotes.copy(searchQuery = emoteQuery, selectedCategoryIndex = emoteCategory, categories = pickerCategories),
                    onSearchQueryChange = { emoteQuery = it }, onCategorySelected = { emoteCategory = it },
                    onCloseClick = { showEmotes = false }, onRetry = onRetryEmotes,
                    canUseSubscriberEmotes = effectiveAccess?.member?.let { it.subscriber || it.privileged } == true,
                    onEmoteSelected = { emote ->
                        val updated = com.lechixy.kick.data.chat.model.insertEmote(content, selection.start, selection.end, emote)
                        if (ChatPolicy.messageIssue(updated.text) == null) {
                            content = updated.text
                            selection = androidx.compose.ui.text.TextRange(updated.cursor)
                            val count = (usage[emote.id] ?: 0) + 1
                            usage = usage + (emote.id to count)
                            usagePreferences.edit().putInt(emote.id.toString(), count).apply()
                        }
                    })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(enabled = !identitySaving && session.profile != null, onClick = { showIdentityDialog = true }) {
                    Icon(painterResource(if (showIdentityDialog) R.drawable.person_24dp_e3e3e3_fill1_wght400_grad0_opsz24 else R.drawable.person_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        contentDescription = stringResource(R.string.chat_identity))
                }
                ChatComposerTextField(value = if (inputBlock == null) content else "", onValueChange = { content = it },
                    blockedText = inputBlockText,
                    modifier = Modifier.weight(1f).focusRequester(requester).onFocusChanged { inputFocused = it.isFocused },
                    enabled = !sending && !loading && effectiveAccess != null && inputBlock == null,
                    validation = validation, selection = selection, onSelectionChange = { selection = it })
                IconButton(enabled = !sending && !loading && effectiveAccess != null && inputBlock == null,
                    onClick = { showEmotes = !showEmotes }) {
                    Icon(painterResource(if (showEmotes) R.drawable.mood_24dp_e3e3e3_fill1_wght400_grad0_opsz24 else R.drawable.mood_24dp_e3e3e3_fill0_wght400_grad0_opsz24), stringResource(R.string.emote_picker))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pointsButton()
                BalanceTooltip(kicks, isKicks = true) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(painterResource(R.drawable.kicks_color), stringResource(R.string.chat_kicks_balance),
                            tint = Color.Unspecified, modifier = Modifier.size(chatBalanceIconSize))
                        Text(kicks?.let { FormatUtils.formatFollowersCount(it.toString(), androidx.compose.ui.platform.LocalConfiguration.current.locales[0]) } ?: "—", style = MaterialTheme.typography.labelMedium)
                    }
                }
                dropsButton()
                Spacer(Modifier.weight(1f))
                ChatSendButton(sending = sending, loading = loading,
                    cooldown = cooldown.takeIf { inputBlock == null && connectionState == ChatConnectionState.CONNECTED && effectiveAccess != null },
                    enabled = effectiveAccess != null && disabledReason == null && inputBlock == null &&
                        content.isNotBlank() && validation == null,
                    onClick = {
                        sending = true; error = null
                        val draft = content
                        val reply = replyTo
                        scope.launch {
                            try {
                                val action = if (reply == null && draft.trimStart().startsWith("/"))
                                    ChatCommands.parse(channel, draft, effectiveAccess?.member?.privileged == true, ownChannel) else null
                                when (action) {
                                    ChatCommandAction.Help -> { content = "/"; selection = androidx.compose.ui.text.TextRange(1) }
                                    is ChatCommandAction.User -> { onUserCommand(action.username); content = ""; selection = androidx.compose.ui.text.TextRange.Zero }
                                    else -> {
                                        AccountGraph.chat.send(channel, draft, reply)
                                        if (content == draft) { content = ""; selection = androidx.compose.ui.text.TextRange.Zero }
                                        if (currentReply?.id == reply?.id) onCancelReply()
                                    }
                                }
                            }
                            catch (failure: Exception) {
                                if (failure is CancellationException) throw failure
                                error = if (failure is ChatCommandException) failure.usage?.let { resources.getString(R.string.command_usage, it) }
                                    ?: resources.getString(R.string.command_not_available)
                                    else if (failure is java.io.IOException) resources.getString(R.string.chat_send_unknown)
                                    else failure.chatUserMessage(context)
                            } finally {
                                sending = false
                                refresh++
                                tick++
                                permissions.refresh(channel, session)
                            }
                        }
                    })
            }
        }
    }
    if (showIdentityDialog) {
        LoadingChatIdentityDialog(channel.id, channel.slug, session.profile?.name.orEmpty(), channel.subscriberBadges,
            avatarUrl = session.profile?.avatar, onPreviewClick = onIdentityPreviewClick) { initial, updated ->
            showIdentityDialog = false
            if (updated != null) {
                identitySaving = true
                scope.launch {
                    try {
                        // Closing before load completes can only change color. Fetch the badge baseline
                        // before saving so that an early color choice cannot clear existing badges.
                        val toSave = if (initial == null) {
                            val loaded = AccountGraph.chat.identity(channel.id)
                            loaded.copy(color = updated.color)
                        } else updated
                        AccountGraph.chat.updateIdentity(channel.id, session.generation, toSave)
                    }
                    catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        error = resources.getString(R.string.identity_save_failed)
                    } finally { identitySaving = false }
                }
            }
        }
    }
    LaunchedEffect(chatActive) {
        if (!chatActive) showDropsDialog = false
    }
    if (chatActive && showDropsDialog && drops != null && channel.livestream != null) {
        val selection = dropsSelection
        val dialogState = when {
            selection?.first == DeveloperDropsPreview.campaignId && previewCampaign != null ->
                DropsUiState.Success(listOf(previewCampaign))
            selection != null && dropsState is DropsUiState.Success -> {
                val campaign = dropsState.campaigns.firstOrNull { it.id == selection.first }
                if (campaign == null) DropsUiState.Empty else dropsState.copy(campaigns = listOf(campaign))
            }
            else -> dropsState
        }
        val (displayedState, retry) = if (session.signedIn && dialogState is DropsUiState.Success &&
            selection?.first != DeveloperDropsPreview.campaignId
        ) {
            val campaignIds = dialogState.campaigns.map { it.id }
            var attempt by remember { mutableIntStateOf(0) }
            var progressState by remember(campaignIds, attempt) { mutableStateOf<DropsUiState>(DropsUiState.Loading) }
            LaunchedEffect(campaignIds, attempt) {
                progressState = try {
                    drops.loadDialogProgress(dialogState)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    DropsUiState.Error
                }
            }
            progressState to { attempt += 1 }
        } else {
            dialogState to drops::retry
        }
        DropsCampaignDialog(
            displayedState,
            onRetry = retry,
            onDismiss = { showDropsDialog = false },
            initialSelection = selection?.let { (campaignId, rewardId) -> rewardId?.let { campaignId to it } }
        )
    }
    if (showPointsDialog && session.signedIn) {
        ChannelPointsDialog(channel, points, kicks, onDismiss = { showPointsDialog = false }, ownChannel = ownChannel, pointsEnabled = pointsEnabled,
            lastPointUpdate = lastPointUpdate, nextPointAward = lastEarnedAt?.let { earned ->
                pointInterval?.takeIf { channel.livestream != null }?.let { earned.plusSeconds(it) } })
    }
}

@Composable
private fun ChatMode(icon: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(icon), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnimatedPoints(
    points: Long?,
    modifier: Modifier = Modifier,
    unlimited: Boolean = false,
    unavailable: Boolean = false,
    earnedPoints: Long = 0L
) {
    val pulse = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(points) }
    LaunchedEffect(points, earnedPoints) {
        if (earnedPoints > 0 || (previous != null && points != previous)) {
            pulse.snapTo(1f)
            pulse.animateTo(1.22f, tween(180))
            pulse.animateTo(1f, tween(420))
        }
        previous = points
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(painterResource(R.drawable.bubble_chart_24dp_e3e3e3_fill1_wght400_grad0_opsz24),
            stringResource(R.string.chat_channel_points), tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(chatBalanceIconSize).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value; rotationZ = (pulse.value - 1f) * 45f })
        if (unavailable) Icon(painterResource(R.drawable.points_unavailable), stringResource(R.string.points_disabled),
            Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (unlimited) Icon(painterResource(R.drawable.all_inclusive_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
            stringResource(R.string.points_unlimited), Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        else AnimatedContent(targetState = points, label = "channel points", transitionSpec = {
            (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
        }) { balance ->
            Text(balance?.let { FormatUtils.formatFollowersCount(it.toString(), androidx.compose.ui.platform.LocalConfiguration.current.locales[0]) } ?: "—",
                style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        if (!unlimited && !unavailable) AnimatedContent(
            targetState = earnedPoints,
            label = "points earned",
            transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith
                (fadeOut() + slideOutVertically { -it / 2 }) }
        ) { amount ->
            if (amount > 0) {
                val formatted = java.text.NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
                Text("+${formatted.format(amount)}", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun ChatComposerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    blockedText: String? = null,
    validation: String? = null,
    modifier: Modifier = Modifier,
    selection: androidx.compose.ui.text.TextRange = androidx.compose.ui.text.TextRange(value.length),
    onSelectionChange: (androidx.compose.ui.text.TextRange) -> Unit = {}
) {
    // Keep the IME composition while emote insertion can move the selection externally.
    var fieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value, selection)) }
    val displayed = if (fieldValue.text == value && fieldValue.selection == selection) fieldValue
        else fieldValue.copy(text = value, selection = selection, composition = null)
    OutlinedTextField(value = displayed, onValueChange = {
        fieldValue = it
        onValueChange(it.text)
        onSelectionChange(it.selection)
    }, enabled = enabled,
        placeholder = { Text(blockedText ?: stringResource(R.string.chat_message_placeholder)) },
        modifier = modifier, maxLines = 4, isError = validation != null,
        supportingText = validation?.let { { Text(it) } })
}

@Composable
internal fun ChatSendButton(
    sending: Boolean,
    loading: Boolean,
    cooldown: AccountChatRepository.Cooldown?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remaining = cooldown?.remainingMillis ?: 0L
    val label = when {
        sending -> stringResource(R.string.chat_sending)
        loading -> stringResource(R.string.chat_loading)
        remaining > 0L -> stringResource(R.string.chat_cooldown_short, (remaining + 999) / 1000)
        else -> stringResource(R.string.chat_send)
    }
    Button(onClick = onClick, enabled = enabled && !sending && !loading && remaining <= 0L, modifier = modifier) {
        Text(label, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
