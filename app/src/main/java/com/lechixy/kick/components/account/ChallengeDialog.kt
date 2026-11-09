package com.lechixy.kick.components.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds

import com.lechixy.kick.R
import com.lechixy.kick.components.StreakBatteryAnimation
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.model.Challenge
import com.lechixy.kick.data.account.model.ChallengeLoadFailure
import com.lechixy.kick.data.account.model.ChallengesState
import com.lechixy.kick.data.account.model.Condition
import com.lechixy.kick.data.account.model.DropTableEntry
import com.lechixy.kick.data.account.model.Window
import com.lechixy.kick.data.account.model.challengeCountdown
import com.lechixy.kick.data.account.model.formatDropChance
import com.lechixy.kick.data.account.model.orderChallenges
import com.lechixy.kick.data.account.model.rewardAnimationImageUrl
import com.lechixy.kick.theme.KickTheme
import com.lechixy.kick.theme.Theme

val LocalOpenChallenges = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun ChallengesButton() {
    val session by AccountGraph.auth.session.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val openFromRoot = LocalOpenChallenges.current
    if (!session.signedIn) return
    val availability by AccountGraph.challenges.state.collectAsStateWithLifecycle()
    var availabilityNow by remember { mutableStateOf(Instant.now()) }
    val availabilityLifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(session.generation, availabilityLifecycle) {
        availabilityLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                availabilityNow = Instant.now()
                AccountGraph.challenges.refresh()
                delay(60_000.milliseconds)
            }
        }
    }
    val available = availability.challenges.any { it.canClaim(availabilityNow) }
    val accessibilityLabel = stringResource(R.string.challenges_title)
    com.lechixy.kick.components.TopBarActionButton(
        onClick = { if (openFromRoot != null) openFromRoot() else open = true }, label = accessibilityLabel,
    ) {
        RewardAvailability(available)
    }
    AccountChallengeDialogHost(open, { open = false })
}

@Composable
fun AccountChallengeDialogHost(open: Boolean, onDismiss: () -> Unit) {
    val session by AccountGraph.auth.session.collectAsStateWithLifecycle()
    if (open && !session.signedIn) {
        AdaptiveModalSheet(onDismiss = onDismiss) { AccountSignInState(compact = true) }
    }
    if (open && session.signedIn) {
        val state by AccountGraph.challenges.state.collectAsStateWithLifecycle()
        val dashboard by AccountGraph.dashboard.state.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        var now by remember { mutableStateOf(Instant.now()) }
        LaunchedEffect(lifecycle, session.generation) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    AccountGraph.dashboard.refresh()
                    while (true) { AccountGraph.challenges.refresh(); delay(60_000.milliseconds) }
                }
                while (true) { now = Instant.now(); delay(1_000.milliseconds) }
            }
        }
        ChallengeDialog(state, dashboard.streak?.days, now, onRefresh = AccountGraph.challenges::refresh,
            onDismiss = onDismiss)
    }
}

@Composable
fun ChallengeDialog(state: ChallengesState, streakDays: Int?, now: Instant, onRefresh: () -> Unit, onDismiss: () -> Unit) {
    AdaptiveModalSheet(onDismiss = onDismiss, sheetWidth = 480.dp) { close ->
        val reward = state.reward
        if (reward != null) {
            val context = LocalContext.current
            val imageLoader = remember(context) { ImageLoader.Builder(context).build() }
            DisposableEffect(imageLoader) { onDispose { imageLoader.shutdown() } }
            RewardRouletteReveal(reward, imageLoader, onConfirm = {
                AccountGraph.challenges.acknowledgeReward()
                AccountGraph.dashboard.refresh()
            })
        } else {
            ChallengeDialogContent(state, streakDays, now, onRefresh, close,
                onClaim = AccountGraph.challenges::claim)
        }
    }
}

@Composable
internal fun ChallengeDialogContent(state: ChallengesState, streakDays: Int?, now: Instant,
    onRefresh: () -> Unit, onDismiss: () -> Unit, onClaim: (Challenge) -> Unit) {
    val challenges = remember(state.challenges) { orderChallenges(state.challenges) }
    val pager = rememberPagerState { challenges.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context).components { add(AnimatedImageDecoder.Factory()) }.build()
    }
    DisposableEffect(imageLoader) { onDispose { imageLoader.shutdown() } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.rewarded_ads_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.challenges_title), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24), contentDescription = stringResource(R.string.account_close))
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.failed) {
            val message = when (state.failure) {
                ChallengeLoadFailure.Http -> stringResource(R.string.challenges_http_failed, state.httpStatus ?: 0)
                ChallengeLoadFailure.Network -> stringResource(R.string.challenges_network_failed)
                ChallengeLoadFailure.Format -> stringResource(R.string.challenges_format_failed)
                else -> stringResource(R.string.challenges_load_failed)
            }
            Text(message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRefresh, enabled = !state.loading) { Text(stringResource(R.string.chat_retry)) }
        }
        if (state.loaded && challenges.isEmpty()) Text(stringResource(R.string.challenges_empty))
        if (state.claimFailed) {
            Text(if (state.claimHttpStatus != null) stringResource(R.string.challenges_claim_http_failed, state.claimHttpStatus)
                else stringResource(R.string.challenges_claim_failed), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRefresh, enabled = !state.loading) { Text(stringResource(R.string.chat_retry)) }
        }
        if (challenges.isNotEmpty()) {
            if (challenges.size > 1) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }, enabled = pager.currentPage > 0) {
                        Icon(painterResource(R.drawable.arrow_back_24dp_e3e3e3_fill0_wght400_grad0_opsz24), contentDescription = stringResource(R.string.challenges_previous))
                    }
                    Text(stringResource(R.string.challenges_page, pager.currentPage + 1, challenges.size),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(challenges.lastIndex)) } }, enabled = pager.currentPage < challenges.lastIndex) {
                        Icon(painterResource(R.drawable.arrow_forward_ios_24dp_e3e3e3_fill0_wght400_grad0_opsz24), contentDescription = stringResource(R.string.challenges_next))
                    }
                }
            }
            HorizontalPager(state = pager, key = { challenges[it].id }, pageSpacing = 12.dp,
                verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) { index ->
                ChallengeCardItem(challenges[index], streakDays, now, imageLoader, onClaim,
                    claiming = state.claimingId == challenges[index].id, claimEnabled = state.claimingId == null)
            }
        }
    }
}

@Composable
fun ChallengeCardItem(challenge: Challenge, streakDays: Int?, now: Instant, imageLoader: ImageLoader, onClaim: (Challenge) -> Unit,
    claiming: Boolean = false, claimEnabled: Boolean = true) {
    val previewUrl = remember(challenge.animations?.idleUrl) { rewardAnimationImageUrl(challenge.animations?.idleUrl) }
    val locale = LocalConfiguration.current.locales[0]
    val eventEnd = challenge.activePeriod?.end
    val eventDate = remember(eventEnd, locale) {
        eventEnd?.let { DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM)
            .withLocale(locale).withZone(ZoneId.systemDefault()).format(it) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(modifier = Modifier.fillMaxWidth().testTag("challenge-summary-${challenge.id}"), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(challenge.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(challenge.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (challenge.featured && eventDate != null) {
                    Surface(modifier = Modifier.testTag("challenge-metadata-${challenge.id}"), color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Text(stringResource(R.string.challenges_event_ends, eventDate), Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                } else if (challenge.recurrence == "daily") {
                    Box(Modifier.testTag("challenge-metadata-${challenge.id}")) { ChallengeStreakRow(streakDays, imageLoader) }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(minOf(maxWidth / 1.8f, 200.dp)).testTag("challenge-preview-${challenge.id}")
                        .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest), contentAlignment = Alignment.Center) {
                        var failed by remember(previewUrl) { mutableStateOf(false) }
                        if (failed || previewUrl == null) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(painterResource(R.drawable.rewarded_ads_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                    contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.secondary)
                                Text(stringResource(R.string.challenges_preview_unavailable), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else AsyncImage(model = previewUrl, imageLoader = imageLoader,
                            contentDescription = stringResource(R.string.challenges_preview, challenge.name),
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), onError = { failed = true })
                    }
                }
            }
        }
        RarityIndicatorRow(challenge.dropTable)
        Button(onClick = { onClaim(challenge) }, enabled = challenge.canClaim(now) && claimEnabled, modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium) {
            Text(stringResource(when { claiming -> R.string.challenges_claiming; challenge.claimed -> R.string.challenges_claimed; else -> R.string.challenges_claim }), fontWeight = FontWeight.Bold)
        }
        val reset = challengeCountdown(challenge.window?.end, now)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(when {
                challenge.claimed -> R.string.challenges_already_claimed
                challenge.expired(now) -> R.string.challenges_expired
                challenge.canClaim(now) -> R.string.challenges_ready
                else -> R.string.challenges_keep_watching
            }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (!challenge.claimed && !challenge.expired(now) && challenge.remaining > 0) {
                Text(if (challenge.condition.type == "watch_time_minutes") stringResource(R.string.challenges_watch_remaining, challenge.remaining)
                    else stringResource(R.string.challenges_progress, challenge.condition.progress, challenge.condition.threshold),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            reset?.let { Text(stringResource(R.string.challenges_resets_in, it), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ChallengeStreakRow(days: Int?, imageLoader: ImageLoader? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StreakBatteryAnimation(Modifier.size(18.dp), imageLoader = imageLoader)
        Text(if (days != null) stringResource(R.string.challenges_streak, days) else stringResource(R.string.account_streak),
            style = MaterialTheme.typography.labelSmall)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(6) { index -> Box(Modifier.weight(1f).height(7.dp).clip(MaterialTheme.shapes.extraSmall)
                .background(if (days != null && index < days) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant)) }
        }
    }
}

@Composable
internal fun rarityName(rarity: String): String = when (rarity.lowercase()) {
    "mythic", "mythical" -> stringResource(R.string.rarity_mythic)
    "legendary" -> stringResource(R.string.rarity_legendary)
    "epic" -> stringResource(R.string.rarity_epic)
    "rare" -> stringResource(R.string.rarity_rare)
    "uncommon" -> stringResource(R.string.rarity_uncommon)
    "common" -> stringResource(R.string.rarity_common)
    else -> rarity.replaceFirstChar { it.titlecase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RarityIndicatorRow(table: List<DropTableEntry>) {
    if (table.isEmpty()) return
    val tooltip = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        table.forEach { entry ->
            RaritySwatch(entry.rarity, Modifier.padding(4.dp).size(14.dp))
        }
        TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above), state = tooltip,
            tooltip = { RichTooltip(title = { Text(stringResource(R.string.challenges_reward_rarity)) }) { RarityDropTableTooltip(table) } }) {
            IconButton(onClick = { scope.launch { tooltip.show() } }) {
                Icon(painterResource(R.drawable.info_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = stringResource(R.string.challenges_reward_rarity), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun RarityDropTableTooltip(table: List<DropTableEntry>) {
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        table.sortedBy { it.weighting }.forEach { entry ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RaritySwatch(entry.rarity, Modifier.size(14.dp))
                Text(rarityName(entry.rarity), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(formatDropChance(entry, table, locale), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun previewChallenge(featured: Boolean) = Challenge(
    id = "preview", name = if (featured) "Trick or Treat" else "Daily Reward",
    description = "Claim your reward for a chance to win exclusive prizes.",
    condition = Condition(8, if (featured) 180 else 60, "watch_time_minutes"), status = "in_progress", recurrence = "daily",
    dropTable = listOf(DropTableEntry("common", 550000), DropTableEntry("uncommon", 280000), DropTableEntry("rare", 120000),
        DropTableEntry("epic", 47000), DropTableEntry("legendary", 2995), DropTableEntry("mythic", 5)),
    featured = featured, window = Window("2026-10-28T00:00:00Z", "2026-10-29T00:00:00Z"),
    activePeriod = if (featured) Window("2026-10-24T00:00:00Z", "2026-11-01T00:00:00Z") else null
)
@Preview(name = "Daily reward Â· phone", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun DailyRewardPreview() {
    KickTheme(theme = Theme.DARK, dynamicColor = false) { Surface { ChallengeDialogContent(ChallengesState(listOf(previewChallenge(false)), loaded = true),
        0, Instant.parse("2026-10-28T12:00:00Z"), {}, {}, {}) } }
}
@Preview(name = "Featured event Â· tablet sheet", widthDp = 480, heightDp = 900, showBackground = true)
@Composable
private fun FeaturedRewardPreview() {
    KickTheme(theme = Theme.DARK, dynamicColor = false) { Surface { ChallengeDialogContent(ChallengesState(listOf(previewChallenge(true)), loaded = true),
        0, Instant.parse("2026-10-28T12:00:00Z"), {}, {}, {}) } }
}
