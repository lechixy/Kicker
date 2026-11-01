package com.lechixy.kick.components.account

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

import com.lechixy.kick.R
import com.lechixy.kick.data.account.model.ChallengeClaim
import com.lechixy.kick.data.account.model.RewardWinner
import com.lechixy.kick.data.account.model.ShieldReward
import com.lechixy.kick.data.account.model.shieldRarity
import com.lechixy.kick.data.account.model.shieldReelPlan
import com.lechixy.kick.data.account.remote.string

private data class RewardPalette(val color: Color, val brush: Brush)

@Composable
private fun rewardPalette(rarity: String): RewardPalette {
    val colors = MaterialTheme.colorScheme
    val color = when (rarity.lowercase()) {
        "uncommon" -> Color(0xFF34C759)
        "rare" -> colors.secondary
        "epic" -> Color(0xFFFF2A55)
        "legendary" -> Color(0xFFFFA000)
        "mythic", "mythical" -> Color(0xFFB794FF)
        else -> colors.outlineVariant
    }
    val brush = if (rarity.lowercase() in setOf("mythic", "mythical")) Brush.linearGradient(
        listOf(Color(0xFF65FFC2), Color(0xFF61A4FF), Color(0xFFFFA0EC), Color(0xFFFFD76A)))
    else Brush.linearGradient(listOf(color, color))
    return RewardPalette(color, brush)
}

/** This screen only presents an already accepted claim. Confirm never sends another POST. */
@Composable
internal fun RewardRouletteReveal(claim: ChallengeClaim, imageLoader: ImageLoader, onConfirm: () -> Unit) {
    var revealed by rememberSaveable(claim.challengeId, claim.winner.id) { mutableStateOf(claim.revealType != "roulette") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(if (revealed) R.string.reward_reveal_title else R.string.reward_opening_title),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        AnimatedContent(revealed, label = "Reward reveal", transitionSpec = {
            (fadeIn(tween(280)) + scaleIn(tween(350), initialScale = 0.92f)) togetherWith fadeOut(tween(180))
        }) { showWinner ->
            if (showWinner) WinnerCardDisplay(claim.winner, imageLoader, onConfirm)
            else ShieldRoulette(claim, imageLoader, onStopped = { revealed = true })
        }
    }
}

@Composable
internal fun ShieldRoulette(claim: ChallengeClaim, imageLoader: ImageLoader, onStopped: () -> Unit) {
    val plan = remember(claim) { shieldReelPlan(claim) }
    val state = rememberLazyListState()
    val density = LocalDensity.current
    val context = LocalContext.current
    val onFinished by rememberUpdatedState(onStopped)
    var preparing by remember { mutableStateOf(true) }
    val shieldWidth = with(density) { 80.dp.roundToPx() }
    val shieldHeight = with(density) { 90.dp.roundToPx() }
    // LazyRow measures item width and spacing separately in whole pixels. Summing 90.dp as
    // a float accumulates drift over dozens of slots on fractional-density displays.
    val slotWidth = (shieldWidth + with(density) { 10.dp.roundToPx() }).toFloat()
    val jitter = with(density) { 80.dp.toPx() } * plan.landingOffsetFraction
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    LaunchedEffect(claim.challengeId, claim.winner.id) {
        // Bound startup latency and concurrent decodes. Failed images retain the shield placeholder.
        withTimeoutOrNull(900) {
            val permits = Semaphore(4)
            coroutineScope {
                (claim.roulette.map { it.itemUrl } + listOfNotNull(claim.winner.thumbnailUrl))
                    .filter(String::isNotBlank).distinct().take(201).map { url -> async {
                        permits.withPermit { imageLoader.execute(ImageRequest.Builder(context).data(url).size(shieldWidth, shieldHeight).build()) }
                    } }.awaitAll()
            }
        }
        claim.winner.cardUrl?.let { imageLoader.enqueue(ImageRequest.Builder(context).data(it).size(600, 800).build()) }
        preparing = false
        state.scroll(MutatePriority.PreventUserInput) {
            var last = 0f
            animate(0f, plan.winnerIndex * slotWidth + jitter,
                animationSpec = tween(4_600, easing = CubicBezierEasing(0.15f, 0.85f, 0.35f, 1f))) { value, _ ->
                scrollBy(value - last)
                last = value
            }
        }
        delay(500)
        onFinished()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(20.dp), color = surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                RouletteNeedle(top = true)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    LazyRow(state = state, userScrollEnabled = false,
                        contentPadding = PaddingValues(horizontal = ((maxWidth - 80.dp) / 2).coerceAtLeast(0.dp)),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().height(104.dp).testTag("reward-reel").drawWithContent {
                            drawContent()
                            drawRect(Brush.horizontalGradient(0f to surface, 0.16f to Color.Transparent,
                                0.84f to Color.Transparent, 1f to surface))
                        }) {
                        itemsIndexed(plan.items, key = { index, _ -> index }) { index, item ->
                            ShieldRouletteItem(item, imageLoader,
                                if (index == plan.winnerIndex) Modifier.testTag("reward-winning-shield") else Modifier)
                        }
                    }
                }
                RouletteNeedle(top = false)
            }
        }
        Text(stringResource(if (preparing) R.string.reward_preparing else R.string.reward_spinning),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun RouletteNeedle(top: Boolean) {
    val color = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val x = size.width / 2
        val path = Path().apply {
            moveTo(x - 6.dp.toPx(), if (top) 2.dp.toPx() else size.height - 2.dp.toPx())
            lineTo(x, if (top) 10.dp.toPx() else size.height - 10.dp.toPx())
            lineTo(x + 6.dp.toPx(), if (top) 2.dp.toPx() else size.height - 2.dp.toPx())
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
internal fun ShieldRouletteItem(item: ShieldReward, imageLoader: ImageLoader, modifier: Modifier = Modifier) {
    val palette = rewardPalette(shieldRarity(item.itemUrl))
    val context = LocalContext.current
    val density = LocalDensity.current
    val request = remember(item.itemUrl, density) { ImageRequest.Builder(context).data(item.itemUrl)
        .size(with(density) { 80.dp.roundToPx() }, with(density) { 90.dp.roundToPx() }).build() }
    Column(modifier.size(80.dp, 90.dp).background(Brush.radialGradient(listOf(palette.color.copy(alpha = 0.16f), Color.Transparent))),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        AsyncImage(model = request, imageLoader = imageLoader, contentDescription = null, contentScale = ContentScale.Fit,
            placeholder = painterResource(R.drawable.rewarded_ads_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
            error = painterResource(R.drawable.rewarded_ads_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(5.dp))
        Box(Modifier.width(26.dp).height(3.dp).background(palette.brush, CircleShape))
    }
}

@Composable
internal fun WinnerCardDisplay(winner: RewardWinner, imageLoader: ImageLoader, onConfirm: () -> Unit) {
    val palette = rewardPalette(winner.rarity)
    val cardHeight = minOf(320.dp, LocalConfiguration.current.screenHeightDp.dp * 0.4f)
    Column(Modifier.fillMaxWidth().testTag("reward-winner"), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().height(cardHeight + 32.dp)
            .background(Brush.radialGradient(listOf(palette.color.copy(alpha = 0.35f), Color.Transparent))), contentAlignment = Alignment.Center) {
            AsyncImage(model = winner.cardUrl ?: winner.thumbnailUrl, imageLoader = imageLoader,
                contentDescription = stringResource(R.string.reward_winner_card), contentScale = ContentScale.Fit,
                placeholder = painterResource(R.drawable.rewarded_ads_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                error = painterResource(R.drawable.rewarded_ads_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                modifier = Modifier.height(cardHeight).aspectRatio(0.72f).clip(RoundedCornerShape(16.dp)))
        }
        Surface(shape = CircleShape, color = palette.color.copy(alpha = 0.14f), border = BorderStroke(1.dp, palette.brush)) {
            Text(rarityName(winner.rarity).uppercase(LocalConfiguration.current.locales[0]),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = if (winner.rarity.lowercase() == "common") MaterialTheme.colorScheme.onSurfaceVariant else palette.color,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().testTag("reward-confirm")) {
            Text(stringResource(R.string.challenges_claim), fontWeight = FontWeight.Bold)
        }
    }
}
