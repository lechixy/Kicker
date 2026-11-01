package com.lechixy.kick.components.channel.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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

import com.lechixy.kick.R
import com.lechixy.kick.components.account.AccountSignInState
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.model.AccountSession
import com.lechixy.kick.data.account.remote.string
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.policy.ChannelPointsPolicy
import com.lechixy.kick.data.chat.model.ActiveChatter
import com.lechixy.kick.data.chat.model.ChatAccess
import com.lechixy.kick.data.chat.model.ChatIdentity
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.CompletionKind
import com.lechixy.kick.data.chat.model.EmoteCategory
import com.lechixy.kick.data.chat.model.EmoteCategoryKind
import com.lechixy.kick.data.chat.model.EmotePickerUiState
import com.lechixy.kick.data.chat.model.PointsUpdatedEvent
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
import com.lechixy.kick.data.chat.policy.ChatSettingsUpdates
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.data.chat.repository.AccountChatRepository
import com.lechixy.kick.util.FormatUtils

@Composable
fun AccountChatComposer(
    channel: ChannelDetail,
    connectionState: ChatConnectionState,
    settingsPatch: JsonObject = JsonObject(emptyMap()),
    pointsUpdated: PointsUpdatedEvent? = null,
    rulesSeen: (Long) -> Boolean = { false },
    markRulesSeen: (Long) -> Unit = {},
    emotes: com.lechixy.kick.data.chat.model.EmotePickerUiState = com.lechixy.kick.data.chat.model.EmotePickerUiState(),
    onRetryEmotes: () -> Unit = {},
    onIdentityPreviewClick: ((com.lechixy.kick.data.chat.model.ChatIdentity) -> Unit)? = null,
    replyTo: ChatMessage? = null, onCancelReply: () -> Unit = {},
    recentMessages: List<ChatMessage> = emptyList(), userColors: Map<String, String> = emptyMap(),
    onUserCommand: (String) -> Unit = {}
) {
    val session by AccountGraph.auth.session.collectAsStateWithLifecycle()
    key(session.generation, channel.slug) {
        ComposerContent(channel, connectionState, session, settingsPatch, pointsUpdated, rulesSeen, markRulesSeen, emotes, onRetryEmotes, onIdentityPreviewClick,
            replyTo, onCancelReply, recentMessages, userColors, onUserCommand)
    }
}

@Composable
private fun ComposerContent(
    channel: ChannelDetail, connectionState: ChatConnectionState, session: AccountSession,
    settingsPatch: JsonObject, pointsUpdated: PointsUpdatedEvent?,
    rulesSeen: (Long) -> Boolean, markRulesSeen: (Long) -> Unit,
    emotes: com.lechixy.kick.data.chat.model.EmotePickerUiState, onRetryEmotes: () -> Unit,
    onIdentityPreviewClick: ((com.lechixy.kick.data.chat.model.ChatIdentity) -> Unit)?,
    replyTo: ChatMessage?, onCancelReply: () -> Unit, recentMessages: List<ChatMessage>, userColors: Map<String, String>,
    onUserCommand: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var content by rememberSaveable { mutableStateOf("") }
    var access by remember { mutableStateOf<ChatAccess?>(null) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var accessError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var tick by remember { mutableLongStateOf(0L) }
    var points by remember { mutableStateOf<Long?>(null) }
    var pointsEnabled by remember { mutableStateOf<Boolean?>(null) }
    var kicks by remember { mutableStateOf<Long?>(null) }
    var showPointsDialog by remember { mutableStateOf(false) }
    var showIdentityDialog by remember { mutableStateOf(false) }
    var showEmotes by rememberSaveable { mutableStateOf(false) }
    var emoteQuery by rememberSaveable { mutableStateOf("") }
    var emoteCategory by remember { mutableIntStateOf(0) }
    var selection by remember { mutableStateOf(androidx.compose.ui.text.TextRange.Zero) }
    val compact = LocalConfiguration.current.smallestScreenWidthDp < 600 && channel.livestream != null
    var expanded by rememberSaveable { mutableStateOf(false) }
    var inputFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
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
    val currentSettingsPatch by rememberUpdatedState(settingsPatch)
    LaunchedEffect(settingsPatch) {
        access = access?.let { it.copy(settings = ChatSettingsUpdates.apply(it.settings, settingsPatch)) }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(session.signedIn, refresh, connectionState == ChatConnectionState.CONNECTED) {
        if (!session.signedIn) return@LaunchedEffect
        loading = true; accessError = null
        try {
            if (AccountGraph.auth.session.value.profile == null) AccountGraph.auth.loadProfile()
            val before = currentSettingsPatch
            val fetched = AccountGraph.chat.access(channel)
            // A fresh HTTP snapshot supersedes old events; keep events received during the fetch.
            access = if (currentSettingsPatch == before) fetched else fetched.copy(
                settings = ChatSettingsUpdates.apply(fetched.settings, currentSettingsPatch))
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            accessError = failure.chatUserMessage(context)
        } finally { loading = false }
    }
    LaunchedEffect(session.signedIn, refresh, connectionState == ChatConnectionState.CONNECTED) {
        if (!session.signedIn) return@LaunchedEffect
        // Reconcile with the server while visible, including after a missed private publication.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    val before = currentPointsEvent
                    val settings = AccountGraph.chat.channelPointSettings(channel)
                    pointsEnabled = settings.enabled
                    val fetched = if (settings.enabled) AccountGraph.chat.points(channel) else null
                    val latest = currentPointsEvent
                    if (latest == before || latest == null ||
                        !ChannelPointsPolicy.accepts(latest, channel.id, AccountGraph.auth.session.value.profile?.userId))
                        {
                            if (points != null && points != fetched) lastPointUpdate = Instant.now()
                            points = fetched
                        }
                } catch (failure: Exception) { if (failure is CancellationException) throw failure }
                delay(60_000)
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
    LaunchedEffect(pointsUpdated, session.profile?.userId) {
        pointsUpdated?.takeIf { session.signedIn && ChannelPointsPolicy.accepts(it, channel.id, session.profile?.userId) }
            ?.let {
                val now = Instant.now()
                lastPointUpdate = now
                // Infer a cadence only from consecutive awards of the same reason, never balance polls.
                if (it.points > 0 && it.reason.equals("WATCHED", ignoreCase = true)) {
                    val interval = lastEarnedAt?.let { previous -> java.time.Duration.between(previous, now).seconds }
                    if (lastEarnReason == it.reason && interval != null && interval in 30..3600) {
                        pointInterval = previousPointInterval?.takeIf { previous -> kotlin.math.abs(previous - interval) <= 5 }?.let { interval }
                        previousPointInterval = interval
                    } else { pointInterval = null; previousPointInterval = null }
                    lastEarnedAt = now
                    lastEarnReason = it.reason
                } else { pointInterval = null; previousPointInterval = null; lastEarnedAt = null }
                points = it.balance.toLong()
            }
    }
    LaunchedEffect(session.signedIn, compact, expanded) {
        if (session.signedIn) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { delay(if (compact && !expanded) 1_000 else 250); tick++ }
        }
    }
    val currentReply by rememberUpdatedState(replyTo)
    val requester = remember { FocusRequester() }
    LaunchedEffect(replyTo?.id, expanded) {
        if (replyTo != null && session.signedIn && (!compact || expanded)) requester.requestFocus()
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
    val effectiveAccess = access
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
    ChatComposerPanel(
        compact = compact,
        expanded = expanded,
        allowAutoHide = content.isBlank() && !inputFocused && !showEmotes && !showPointsDialog &&
            !showIdentityDialog && replyTo == null && !sending,
        onExpandedChange = { expanded = it },
        onHide = {
            focusManager.clearFocus()
            keyboard?.hide()
            showEmotes = false
        }
    ) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!session.signedIn) {
                AccountSignInState(compact = true)
                return@Column
            }
            ChatRulesCard(effectiveAccess?.settings?.rules.orEmpty(), session.generation, rulesSeen, markRulesSeen)
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
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val disabledReason = when {
                connectionState != ChatConnectionState.CONNECTED -> stringResource(R.string.chat_connect_to_send)
                loading -> stringResource(R.string.chat_checking_permissions)
                effectiveAccess == null -> stringResource(R.string.chat_permissions_failed)
                else -> restriction
            }
            if (inputBlock == null && (cooldown == null || cooldown.remainingMillis == 0L)) disabledReason?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            accessError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
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
                BalanceTooltip(points, unlimited = ownChannel, unavailable = pointsEnabled == false) {
                    AnimatedPoints(points, Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = stringResource(R.string.channel_points_title)) { showPointsDialog = true }
                        .padding(vertical = 8.dp, horizontal = 4.dp), unlimited = ownChannel, unavailable = pointsEnabled == false)
                }
                BalanceTooltip(kicks, isKicks = true) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(painterResource(R.drawable.kicks_color), stringResource(R.string.chat_kicks_balance),
                            tint = Color.Unspecified, modifier = Modifier.size(20.dp))
                        Text(kicks?.let { FormatUtils.formatFollowersCount(it.toString(), androidx.compose.ui.platform.LocalConfiguration.current.locales[0]) } ?: "—", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.weight(1f))
                ChatSendButton(sending = sending, loading = loading,
                    cooldown = cooldown.takeIf { inputBlock == null && connectionState == ChatConnectionState.CONNECTED && effectiveAccess != null },
                    enabled = disabledReason == null && inputBlock == null && content.isNotBlank() && validation == null,
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
                                error = if (failure is ChatCommandException) failure.usage?.let { context.getString(R.string.command_usage, it) }
                                    ?: context.getString(R.string.command_not_available)
                                    else if (failure is java.io.IOException) context.getString(R.string.chat_send_unknown)
                                    else failure.chatUserMessage(context)
                            } finally { sending = false; refresh++; tick++ }
                        }
                    })
            }
            if (access == null && !loading) TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.chat_retry)) }
        }
    }
    if (showIdentityDialog) {
        LoadingChatIdentityDialog(channel.id, session.profile?.name.orEmpty(), channel.subscriberBadges,
            onPreviewClick = onIdentityPreviewClick) { initial, updated ->
            showIdentityDialog = false
            if (updated != null) {
                identitySaving = true
                scope.launch {
                    var previous = initial
                    try {
                        // Closing before load completes can only change color. Fetch the badge baseline
                        // before saving so that an early color choice cannot clear existing badges.
                        val toSave = if (initial == null) {
                            val loaded = AccountGraph.chat.identity(channel.id)
                            previous = loaded
                            loaded.copy(color = updated.color)
                        } else updated
                        access = access?.copy(identity = toSave)
                        AccountGraph.chat.updateIdentity(channel.id, session.generation, toSave)
                    }
                    catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        if (previous != null) access = access?.copy(identity = previous)
                        error = context.getString(R.string.identity_save_failed)
                    } finally { identitySaving = false }
                }
            }
        }
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
private fun AnimatedPoints(points: Long?, modifier: Modifier = Modifier, unlimited: Boolean = false, unavailable: Boolean = false) {
    val pulse = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(points) }
    LaunchedEffect(points) {
        if (previous != null && points != previous) {
            pulse.snapTo(1f)
            pulse.animateTo(1.22f, tween(180))
            pulse.animateTo(1f, tween(420))
        }
        previous = points
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(painterResource(R.drawable.bubble_chart_24dp_e3e3e3_fill1_wght400_grad0_opsz24),
            stringResource(R.string.chat_channel_points), tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value; rotationZ = (pulse.value - 1f) * 45f })
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
    }
}

@Composable
private fun ChatRulesCard(rules: String, generation: Long, seen: (Long) -> Boolean, markSeen: (Long) -> Unit) {
    var visible by remember { mutableStateOf(!seen(generation)) }
    var expanded by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    LaunchedEffect(rules, interaction) {
        if (rules.isBlank() || !visible) return@LaunchedEffect
        markSeen(generation)
        delay(if (expanded) 12000 else 8000)
        visible = false
    }
    AnimatedVisibility(visible && rules.isNotBlank()) {
        Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded; interaction++ }) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.chat_rules), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Icon(painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        stringResource(if (expanded) R.string.chat_collapse_rules else R.string.chat_expand_rules),
                        modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f })
                }
                if (expanded) Text(rules, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()))
                else Text(rules, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
