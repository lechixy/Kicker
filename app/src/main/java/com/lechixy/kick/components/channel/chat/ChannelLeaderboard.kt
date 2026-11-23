package com.lechixy.kick.components.channel.chat

import android.icu.text.MessageFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.DialogProperties
import com.lechixy.kick.R
import com.lechixy.kick.components.PredictiveDialog
import com.lechixy.kick.components.SkeletonBlock
import com.lechixy.kick.components.SkeletonLoadingScope
import com.lechixy.kick.data.channel.model.ChannelLeaderboardUiState
import com.lechixy.kick.data.channel.model.LeaderboardBoard
import com.lechixy.kick.data.channel.model.LeaderboardBoardType
import com.lechixy.kick.data.channel.model.LeaderboardCategory
import com.lechixy.kick.data.channel.model.LeaderboardPeriod
import com.lechixy.kick.data.chat.model.GiftLeaderboardEntry
import com.lechixy.kick.util.FormatUtils
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@DrawableRes
internal fun resolveGifterBadgeRes(quantity: Long): Int = when {
    quantity >= 1000 -> R.drawable.gifter_1000_badge
    quantity >= 800 -> R.drawable.gifter_800_badge
    quantity >= 500 -> R.drawable.gifter_500_badge
    quantity >= 250 -> R.drawable.gifter_250_badge
    quantity >= 200 -> R.drawable.gifter_200_badge
    quantity >= 150 -> R.drawable.gifter_150_badge
    quantity >= 100 -> R.drawable.gifter_100_badge
    quantity >= 50 -> R.drawable.gifter_50_badge
    quantity >= 25 -> R.drawable.gifter_25_badge
    quantity >= 10 -> R.drawable.gifter_10_badge
    quantity >= 5 -> R.drawable.gifter_5_badge
    else -> R.drawable.gifter_1_badge
}

@DrawableRes
private fun resolveKickerBadgeRes(quantity: Long): Int = when {
    quantity >= 5000 -> R.drawable.kicker_5000_badge
    quantity >= 2500 -> R.drawable.kicker_2500_badge_
    quantity >= 1000 -> R.drawable.kicker_1000_badge
    quantity >= 500 -> R.drawable.kicker_500_badge
    quantity >= 250 -> R.drawable.kicker_250_badge
    else -> R.drawable.kicker_1_badge
}

private fun LeaderboardCategory.quantityBadge(quantity: Long) =
    if (this == LeaderboardCategory.GIFTERS) resolveGifterBadgeRes(quantity) else resolveKickerBadgeRes(quantity)

private fun LeaderboardCategory.rankBadge(rank: Int): Int? = when (this) {
    LeaderboardCategory.GIFTERS -> when (rank) {
        1 -> R.drawable.kick_first_gifter
        2 -> R.drawable.kick_second_gifter
        3 -> R.drawable.kick_third_gifter
        else -> null
    }
    LeaderboardCategory.KICKERS -> when (rank) {
        1 -> R.drawable.kick_first_kicker
        2 -> R.drawable.kick_second_kicker
        3 -> R.drawable.kick_third_kicker
        else -> null
    }
}

@Composable
private fun LeaderboardBoardType.title() = stringResource(when (this) {
    LeaderboardBoardType.MONTHLY_GIFTERS -> R.string.leaderboard_monthly_gifters
    LeaderboardBoardType.LIFETIME_GIFTERS -> R.string.leaderboard_lifetime_gifters
    LeaderboardBoardType.WEEKLY_KICKERS -> R.string.leaderboard_weekly_kickers
    LeaderboardBoardType.MONTHLY_KICKERS -> R.string.leaderboard_monthly_kickers
    LeaderboardBoardType.LIFETIME_KICKERS -> R.string.leaderboard_lifetime_kickers
    LeaderboardBoardType.WEEKLY_GIFTERS -> R.string.leaderboard_weekly_gifters
})

@Composable
private fun LeaderboardPeriod.resetLabel(): String? {
    if (this == LeaderboardPeriod.LIFETIME) return null
    var now by remember { mutableStateOf(ZonedDateTime.now(ZoneOffset.UTC)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now(ZoneOffset.UTC)
            delay(60_000)
        }
    }
    val today = now.toLocalDate()
    val resetDate = when (this) {
        LeaderboardPeriod.WEEKLY -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        LeaderboardPeriod.MONTHLY -> today.with(TemporalAdjusters.firstDayOfNextMonth())
        LeaderboardPeriod.LIFETIME -> return null
    }
    val hours = Duration.between(now, resetDate.atStartOfDay(ZoneOffset.UTC)).toHours().toInt()
    return when {
        hours >= 24 -> pluralStringResource(R.plurals.leaderboard_resets_days, hours / 24, hours / 24)
        hours >= 1 -> pluralStringResource(R.plurals.leaderboard_resets_hours, hours, hours)
        else -> stringResource(R.string.leaderboard_resets_soon)
    }
}

@Composable
internal fun ChannelLeaderboardStrip(
    state: ChannelLeaderboardUiState,
    paused: Boolean,
    onClick: (LeaderboardBoardType?) -> Unit,
) {
    val board = state.boards.firstOrNull { it.type == LeaderboardBoardType.WEEKLY_GIFTERS && it.entries.isNotEmpty() }
        ?: state.boards.firstOrNull { it.type == LeaderboardBoardType.MONTHLY_GIFTERS }
    if (state.boards.isEmpty() && !state.isLoading && !state.hasError) return

    Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 30.dp)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.leaderboard_open)) {
                        onClick(board?.type)
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                if (board == null || board.entries.isEmpty()) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.kick_first_gifter), null, Modifier.size(18.dp))
                        Text(board?.type?.title() ?: stringResource(R.string.leaderboard_title),
                            style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    key(state.channelId, board.type) {
                        Row(
                            Modifier.align(Alignment.Center).basicMarquee(
                                iterations = if (paused) 0 else Int.MAX_VALUE,
                                initialDelayMillis = 7_000,
                                repeatDelayMillis = 7_000,
                                spacing = MarqueeSpacing(16.dp),
                                velocity = 42.dp,
                            ).padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            board.entries.take(3).forEachIndexed { index, entry ->
                                val rank = index + 1
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    LeaderboardRankBadge(LeaderboardCategory.GIFTERS, rank, Modifier.size(18.dp))
                                    Text(entry.username, color = leaderboardUsernameColor(rank), style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    Text(leaderboardQuantity(entry.quantity), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
internal fun ChannelLeaderboardDialog(
    state: ChannelLeaderboardUiState,
    initialBoard: LeaderboardBoardType?,
    onUserClick: (Long) -> Unit,
    onActionCtaClick: (LeaderboardBoardType, Long) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    PredictiveDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(16.dp).widthIn(max = 520.dp).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column {
                Text(
                    stringResource(R.string.leaderboard_title),
                    Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
                ChannelLeaderboardPager(
                    state, onUserClick, onActionCtaClick, onRetry, Modifier.weight(1f, fill = false),
                    initialBoard = initialBoard,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelLeaderboardPager(
    uiState: ChannelLeaderboardUiState,
    onUserClick: (Long) -> Unit,
    onActionCtaClick: (LeaderboardBoardType, Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    initialBoard: LeaderboardBoardType? = null,
) {
    val boards = uiState.boards
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        if (boards.isEmpty()) {
            when {
                uiState.isLoading -> SkeletonLoadingScope(active = true, Modifier.fillMaxWidth()) {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(10) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                SkeletonBlock(Modifier.size(22.dp))
                                SkeletonBlock(Modifier.weight(1f).height(16.dp))
                                SkeletonBlock(Modifier.size(width = 52.dp, height = 16.dp))
                            }
                        }
                    }
                }
                else -> Box(Modifier.fillMaxWidth().heightIn(min = 128.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(if (uiState.hasError) R.string.leaderboard_load_error else R.string.leaderboard_unavailable))
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_retry)) }
                    }
                }
            }
            return@Column
        }

        key(uiState.channelId, boards.map { it.type }, initialBoard) {
            val cyclic = boards.size > 1
            val center = (Int.MAX_VALUE / 2).let { it - it % boards.size }
            val initialIndex = boards.indexOfFirst { it.type == initialBoard }.coerceAtLeast(0)
            val pager = rememberPagerState(initialPage = if (cyclic) center + initialIndex else 0) {
                if (cyclic) Int.MAX_VALUE else 1
            }
            val scope = rememberCoroutineScope()
            val board = boards[pager.currentPage % boards.size]
            LeaderboardHeader(board.type.title(), board.type.period.resetLabel(), cyclic,
                onPreviousClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } },
                onNextClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pager.pageCount - 1)) } },
            )
            PullToRefreshBox(isRefreshing = uiState.isLoading, onRefresh = onRetry, modifier = Modifier.weight(1f, fill = false)) {
                HorizontalPager(state = pager, verticalAlignment = Alignment.Top) { page ->
                    val pageBoard = boards[page % boards.size]
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 380.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (pageBoard.entries.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().heightIn(min = 104.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.leaderboard_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            itemsIndexed(pageBoard.entries, key = { _, entry -> "${pageBoard.type.name}_${entry.userId}" }) { index, entry ->
                                LeaderboardEntryRow(entry, index + 1, pageBoard.type.category, onClick = { onUserClick(entry.userId) })
                            }
                        }
                    }
                }
            }
            LeaderboardFooter(board.type.category, board.targetQuantity, board.targetRank,
                onClick = { onActionCtaClick(board.type, board.targetQuantity) })
        }
    }
}

@Composable
private fun LeaderboardHeader(
    title: String,
    resetLabel: String?,
    cyclic: Boolean,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (cyclic) IconButton(onClick = onPreviousClick) {
            LeaderboardChevron(true, stringResource(R.string.leaderboard_previous))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (resetLabel != null) Text(resetLabel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (cyclic) IconButton(onClick = onNextClick) {
            LeaderboardChevron(false, stringResource(R.string.leaderboard_next))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun LeaderboardChevron(previous: Boolean, label: String? = null) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(painterResource(R.drawable.arrow_forward_ios_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
        label, Modifier.size(16.dp).rotate(if (previous != rtl) 180f else 0f))
}

@Composable
private fun LeaderboardEntryRow(entry: GiftLeaderboardEntry, rank: Int, category: LeaderboardCategory, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (rank <= 3) LeaderboardRankBadge(category, rank, Modifier.size(22.dp))
            else Text(rank.toString(), color = colors.onSurfaceVariant, style = style, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(12.dp))
        Text(entry.username, Modifier.weight(1f), color = leaderboardUsernameColor(rank), style = style,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(leaderboardQuantity(entry.quantity), color = colors.onSurfaceVariant,
                style = style.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Medium)
            Image(painterResource(category.quantityBadge(entry.quantity)), null, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LeaderboardRankBadge(category: LeaderboardCategory, rank: Int, modifier: Modifier) {
    category.rankBadge(rank)?.let { badge ->
        Image(painterResource(badge), stringResource(R.string.leaderboard_rank, rank), modifier)
    }
}

@Composable
private fun leaderboardUsernameColor(rank: Int) = when (rank) {
    1 -> Color(0xFFFFCC81)
    3 -> Color(0xFFC6946B)
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun LeaderboardFooter(category: LeaderboardCategory, targetQuantity: Long, targetRank: Int, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val ordinalPattern = stringResource(R.string.leaderboard_ordinal)
    val rank = remember(ordinalPattern, locale, targetRank) {
        MessageFormat(ordinalPattern, locale).format(arrayOf(targetRank))
    }
    val quantity = leaderboardQuantity(targetQuantity)
    val gifts = category == LeaderboardCategory.GIFTERS
    val units = if (gifts) pluralStringResource(R.plurals.leaderboard_subs, if (targetQuantity == 1L) 1 else 2, quantity)
        else stringResource(R.string.leaderboard_kicks, quantity)
    val marker = "\uFFFC"
    val sentence = stringResource(if (gifts) R.string.leaderboard_gift_target else R.string.leaderboard_kick_target, marker, rank)
    val accent = MaterialTheme.colorScheme.primary
    val footerText = buildAnnotatedString {
        append(sentence.substringBefore(marker))
        appendInlineContent("badge", alternateText = " ")
        append(" ")
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(units) }
        append(sentence.substringAfter(marker))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        footerText,
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        inlineContent = mapOf("badge" to InlineTextContent(Placeholder(1.3.em, 1.3.em, PlaceholderVerticalAlign.TextCenter)) {
            Image(painterResource(category.quantityBadge(targetQuantity)), null, Modifier.fillMaxSize())
        }),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun leaderboardQuantity(quantity: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(quantity, locale) { FormatUtils.formatInteger(quantity, locale) }
}

@PreviewLightDark
@Composable
private fun MonthlyGiftersPreview() = LeaderboardPreview(
    LeaderboardBoardType.MONTHLY_GIFTERS,
    listOf("Yemeksepeti" to 800L, "tunaipek" to 201L, "BarisCetiin" to 200L, "Mocker" to 100L, "Eddie" to 100L,
        "Gurler_9" to 50L, "KICK" to 50L, "Dante6638" to 50L, "mertmiyiz_abi" to 46L, "deluqe" to 40L),
)

@PreviewLightDark
@Composable
private fun WeeklyGiftersPreview() = LeaderboardPreview(LeaderboardBoardType.WEEKLY_GIFTERS, emptyList())

@PreviewLightDark
@Composable
private fun MonthlyKickersPreview() = LeaderboardPreview(
    LeaderboardBoardType.MONTHLY_KICKERS,
    listOf("itsnotrealbrownie" to 7349L, "BultRow" to 5570L, "Dexter_Worgan" to 5510L, "mrfiratt" to 5000L,
        "ilkedeniz97" to 5000L, "musti32" to 3600L, "deluqe" to 3500L, "mertmiyiz_abi" to 3500L,
        "osguun" to 3000L, "eyupkoca68" to 2500L),
)

@Composable
private fun LeaderboardPreview(type: LeaderboardBoardType, values: List<Pair<String, Long>>) {
    val board = LeaderboardBoard(type, values.mapIndexed { index, (name, count) ->
        GiftLeaderboardEntry(index.toLong(), name, count)
    })
    com.lechixy.kick.theme.KickTheme(com.lechixy.kick.theme.Theme.SYSTEM, dynamicColor = false) {
        ChannelLeaderboardPager(ChannelLeaderboardUiState(isLoading = false, boards = listOf(board)), {}, { _, _ -> }, {},
            Modifier.width(360.dp))
    }
}
