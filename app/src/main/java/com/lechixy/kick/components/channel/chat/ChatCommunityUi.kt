package com.lechixy.kick.components.channel.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.lechixy.kick.components.AppLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lechixy.kick.R
import com.lechixy.kick.components.StreakBatteryAnimation
import com.lechixy.kick.data.chat.model.ActiveChatter
import com.lechixy.kick.data.chat.model.ActiveChatters

@Composable
internal fun ActiveChattersContent(
    data: ActiveChatters?, error: Int?, onRetry: () -> Unit,
    onUserClick: (ActiveChatter) -> Unit, modifier: Modifier = Modifier
) {
    var search by rememberSaveable { mutableStateOf("") }
    Column(modifier) {
        OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text(stringResource(R.string.active_chatters_search)) }, singleLine = true, shape = RoundedCornerShape(12.dp))
        if (error != null) {
            Text(stringResource(error), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_retry)) }
        }
        if (data == null && error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoadingIndicator() }
        } else if (data != null) {
            if (data.isPartial) Text(stringResource(R.string.chatters_partial, data.listedCount, data.totalCount),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val groups = listOf(
                Triple(R.string.active_chatters_moderators, R.drawable.moderator_badge, data.moderators),
                Triple(R.string.active_chatters_ogs, R.drawable.og_badge, data.ogs),
                Triple(R.string.active_chatters_vips, R.drawable.vip_badge, data.vips),
                Triple(R.string.active_chatters_viewers, R.drawable.person_24dp_e3e3e3_fill0_wght400_grad0_opsz24, data.chatters),
                Triple(R.string.active_chatters_bots, R.drawable.group_24dp_e3e3e3_fill0_wght400_grad0_opsz24, data.bots))
            val expansion = groups.associate { (nameId, _, _) ->
                nameId to rememberSaveable(nameId) { mutableStateOf(nameId != R.string.active_chatters_viewers) }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                groups.forEach { (nameId, icon, users) ->
                    val filtered = users.filter { it.username.contains(search.trim(), ignoreCase = true) }
                    val expanded = expansion.getValue(nameId).value
                    item(key = nameId) {
                        val name = stringResource(nameId)
                        val rotation by animateFloatAsState(if (expanded) 0f else -90f, label = "group arrow")
                        Row(Modifier.animateItem().fillMaxWidth().clickable { expansion.getValue(nameId).value = !expanded }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                stringResource(if (expanded) R.string.active_chatters_collapse else R.string.active_chatters_expand, name), Modifier.size(18.dp).rotate(rotation))
                            Image(painterResource(icon), null, Modifier.size(20.dp))
                            Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${filtered.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Collapsed groups have no image composables; expanded rows are loaded only in the viewport.
                    if (expanded) items(filtered, key = { "$nameId:${it.slug.ifBlank { it.username }}" }) { user ->
                        Row(Modifier.animateItem().fillMaxWidth().clickable { onUserClick(user) }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProfileImage(user.profilePicture, user.username, Modifier.size(32.dp).clip(CircleShape))
                            Text(user.username, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileImage(url: String?, description: String?, modifier: Modifier) {
    AsyncImage(model = profileImageRequest(url), contentDescription = description,
        placeholder = painterResource(R.drawable.person_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
        error = painterResource(R.drawable.person_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
        contentScale = ContentScale.Crop, modifier = modifier)
}

@Composable
private fun profileImageRequest(url: String?): ImageRequest {
    val context = LocalContext.current
    return remember(context, url) { ImageRequest.Builder(context).data(url).crossfade(true)
        .diskCachePolicy(CachePolicy.ENABLED).build() }
}

@Composable
private fun localizedProfileDate(value: String?): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return profileDate(value, locale) ?: value ?: "—"
}

internal fun levelAnimationAsset(level: Int?): String? = level?.takeIf { it >= 40 }?.let {
    "file:///android_asset/tier/animation/level-${KickLevelTier.fromLevel(it).minLevel}.webp"
}

@Composable
internal fun LevelAvatarAnimation(level: Int?, modifier: Modifier = Modifier) {
    val asset = levelAnimationAsset(level) ?: return
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (!lifecycleState.isAtLeast(Lifecycle.State.STARTED)) return
    val context = LocalContext.current
    val loader = remember(context) {
        ImageLoader.Builder(context).components { add(AnimatedImageDecoder.Factory()) }.build()
    }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    // Alpha-preserving Animated WebP transcodes of Kick's 688×306 WebM animations.
    AsyncImage(model = asset, imageLoader = loader, contentDescription = null,
        contentScale = ContentScale.Fit, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KickUserProfileDialog(state: UserProfileCardUiState, onClose: () -> Unit, onFollow: () -> Unit, onMute: () -> Unit, onChannelClick: () -> Unit = {}) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        sheetMaxWidth = 440.dp, containerColor = Color(0xFF131517), dragHandle = null) {
        KickUserProfileCard(state, onFollow, onMute, Modifier.navigationBarsPadding(), onChannelClick)
    }
}

@Composable
fun KickUserProfileCard(
    uiState: UserProfileCardUiState,
    onFollowClick: () -> Unit = {},
    onMuteClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    onChannelClick: () -> Unit = {}
) {
    val tier = KickLevelTier.fromLevel(uiState.level ?: 20)
    val accent = tier.accentColor
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color(0xFF131517), contentColor = Color.White) {
        Box {
            if (uiState.level != null) AsyncImage(tier.backgroundDottedUrl, null,
                Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = .4f)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = .22f), Color(0xFF181B1E))))
                    .then(LevelBorderModifier(accent, RoundedCornerShape(12.dp)))) {
                    if (uiState.bannerPicUrl != null) AsyncImage(profileImageRequest(uiState.bannerPicUrl), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    uiState.streakDays?.takeIf { it > 0 }?.let { days ->
                        Surface(Modifier.align(Alignment.TopStart).padding(8.dp), color = Color(0xCC131517), shape = RoundedCornerShape(20.dp)) {
                            Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            StreakBatteryAnimation(Modifier.size(18.dp))
                            Text("x$days", color = Color(0xFF53FC18), fontWeight = FontWeight.Bold)
                        }
                        }
                    }
                }
                Box(Modifier.offset(y = (-36).dp).size(96.dp), contentAlignment = Alignment.Center) {
                    LevelAvatarAnimation(uiState.level, Modifier.requiredWidth(216.dp).aspectRatio(688f / 306f))
                    ProfileImage(uiState.profilePicUrl, uiState.username,
                        Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF181B1E)).then(LevelBorderModifier(accent, CircleShape, 2.dp)))
                }
                Text(uiState.username, Modifier.offset(y = (-28).dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                KickBadgeRow(uiState.level, uiState.badges, Modifier.offset(y = (-16).dp))
                if (uiState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp), color = accent)
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ProfileMetric(stringResource(R.string.chatter_joined), localizedProfileDate(uiState.createdAt), Modifier.weight(1f))
                    ProfileMetric(stringResource(R.string.chatter_followed), localizedProfileDate(uiState.followingSince), Modifier.weight(1f))
                    ProfileMetric(stringResource(R.string.chatter_subscribed), uiState.subscribedMonths?.let {
                        pluralStringResource(R.plurals.chatter_subscription_months, it, it) } ?: "—", Modifier.weight(1f))
                }
                uiState.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onFollowClick, enabled = uiState.isFollowing != null && !uiState.actionLoading,
                        modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(8.dp)) {
                        Icon(painterResource(if (uiState.isFollowing == true) R.drawable.favorite_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                            else R.drawable.favorite_24dp_e3e3e3_fill0_wght400_grad0_opsz24), stringResource(if (uiState.isFollowing == true) R.string.chatter_unfollow else R.string.follow))
                    }
                    com.lechixy.kick.components.FavoriteUserButton(uiState.userId, uiState.username, uiState.slug,
                        colors = IconButtonDefaults.filledTonalIconButtonColors())
                    FilledTonalButton(onClick = onChannelClick, enabled = uiState.slug.isNotBlank(), modifier = Modifier.height(48.dp), shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.chatter_channel))
                    }
                    FilledTonalButton(onMuteClick, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(8.dp)) {
                        Icon(painterResource(if (uiState.isMuted) R.drawable.volume_off_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                            else R.drawable.volume_up_24dp_e3e3e3_fill0_wght400_grad0_opsz24), stringResource(if (uiState.isMuted) R.string.chatter_unmute else R.string.chatter_mute))
                    }
                }
            }
        }
    }
}

@Composable
fun LevelBorderModifier(accentColor: Color, shape: Shape, strokeWidth: Dp = 1.5.dp): Modifier =
    Modifier.border(strokeWidth, Brush.linearGradient(listOf(accentColor.copy(alpha = .65f), accentColor)), shape)

@Composable
fun KickBadgeRow(level: Int?, badges: List<ResolvedChatBadge>, modifier: Modifier = Modifier) {
    val visibleBadges = badges.filter { it.level == null }.sortedBy { it.sortOrder }
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        level?.let { Surface(color = KickLevelTier.fromLevel(it).accentColor, shape = RoundedCornerShape(6.dp)) {
            Text("$it", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = Color.Black, fontWeight = FontWeight.Bold)
        } }
        if (level != null && visibleBadges.isNotEmpty()) {
            Spacer(Modifier.size(4.dp))
        }
        visibleBadges.forEach { badge ->
            if (badge.level == null) BadgeTooltip(badge) {
                val label = badgeLabel(badge)
                badge.drawableRes?.let { Image(painterResource(it), label, Modifier.size(22.dp)) }
                    ?: AsyncImage(badge.imageUrl, label, Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color(0xFFA0A5AC), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 2)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0E10)
@Composable
private fun KickUserProfileCardPreview() {
    MaterialTheme { KickUserProfileCard(UserProfileCardUiState(username = "Lazonaaa", level = 40, streakDays = 1,
        createdAt = "Feb 16, 2024", followingSince = "Mar 4, 2024", subscribedMonths = 26, isFollowing = true, isLoading = false), {}, {}
    ) }
}
