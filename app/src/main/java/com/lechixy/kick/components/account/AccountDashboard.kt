package com.lechixy.kick.components.account

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import com.lechixy.kick.R
import com.lechixy.kick.components.StreakBatteryAnimation
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.model.AccountDashboardState
import com.lechixy.kick.data.account.model.AccountProfile
import com.lechixy.kick.data.account.model.AccountStreak

@Composable
internal fun AccountDashboard(onChannelClick: (String) -> Unit, onDismiss: () -> Unit) {
    val session by AccountGraph.auth.session.collectAsStateWithLifecycle()
    val state by AccountGraph.dashboard.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // minSdk 30 supports ImageDecoder, including animated GIF level badges.
    val badgeLoader = remember(context) {
        ImageLoader.Builder(context).components { add(AnimatedImageDecoder.Factory()) }.build()
    }
    DisposableEffect(badgeLoader) { onDispose { badgeLoader.shutdown() } }
    LaunchedEffect(session.signedIn, session.generation, session.profile?.userId) {
        if (session.signedIn) {
            if (session.profile == null) {
                try { AccountGraph.auth.loadProfile() }
                catch (error: Exception) { if (error is CancellationException) throw error }
            }
            else AccountGraph.dashboard.refresh()
        }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.account_title), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = stringResource(R.string.account_close))
            }
        }
        if (!session.signedIn || session.profile == null) {
            AccountPanel(showTitle = false)
        } else {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            GamificationCard(state, session.profile, badgeLoader, onEdit = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://kick.com/settings/profile")))
            })
            StreakCard(state.streak, badgeLoader)
            if (state.failures.isNotEmpty()) {
                Text(stringResource(R.string.account_details_failed), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = AccountGraph.dashboard::refresh, enabled = !state.loading) {
                    Text(stringResource(R.string.chat_retry))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val slug = state.channel?.slug ?: session.profile?.name
                        if (slug != null) { onDismiss(); onChannelClick(slug) }
                    }, enabled = state.channel != null || session.profile != null,
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface)
                ) { Text(stringResource(R.string.account_view_channel), fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = { scope.launch { AccountGraph.auth.signOut(); onDismiss() } },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(painterResource(R.drawable.logout_24dp_e3e3e3_fill0_wght400_grad0_opsz24), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.account_logout), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GamificationCard(state: AccountDashboardState, profile: AccountProfile?, badgeLoader: ImageLoader, onEdit: () -> Unit) {
    val level = state.level
    val progress = level?.progress ?: 0f
    val channel = state.channel
    val name = channel?.user?.username ?: profile?.name.orEmpty()
    val avatar = channel?.user?.avatar ?: profile?.avatar
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Box(Modifier.fillMaxWidth()) {
            level?.let {
                AsyncImage(model = it.tierBackground, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(245.dp))
            }
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), RoundedCornerShape(12.dp))) {
                    AsyncImage(model = channel?.banner?.src ?: channel?.offlineBanner?.src, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    FilledIconButton(onClick = onEdit, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))) {
                        Icon(painterResource(R.drawable.edit_24dp_e3e3e3_fill0_wght400_grad0_opsz24), contentDescription = stringResource(R.string.account_edit_profile), modifier = Modifier.size(16.dp))
                    }
                    if (level != null) Surface(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
                        Text(stringResource(R.string.account_level_progress, (progress * 100).roundToInt(), level.level + 1),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Box(Modifier.offset(y = (-30).dp).size(88.dp), contentAlignment = Alignment.Center) {
                    Surface(Modifier.fillMaxSize(), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {}
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeWidth = 4.dp)
                    val badge = level?.badge
                    if (badge?.animatedImage != null || badge?.image != null) {
                        AsyncImage(model = badge.animatedImage ?: badge.image, imageLoader = badgeLoader,
                            contentDescription = stringResource(R.string.account_level, level.level),
                            modifier = Modifier.size(64.dp), contentScale = ContentScale.Fit,
                            error = painterResource(R.drawable.person_24dp_e3e3e3_fill0_wght400_grad0_opsz24))
                    } else {
                        AccountAvatar(avatar, name, Modifier.size(68.dp))
                    }
                }
                Column(Modifier.offset(y = (-20).dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    level?.let {
                        Text(stringResource(R.string.account_level, it.level), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.account_xp_remaining, it.xpToNextLevel), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.account_level_description), style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StreakCard(streak: AccountStreak?, imageLoader: ImageLoader? = null) {
    val days = streak?.days?.coerceAtLeast(0)
    val active = (days ?: 0).coerceIn(0, 6)
    val accent = MaterialTheme.colorScheme.secondary
    val inactive = MaterialTheme.colorScheme.surfaceContainerHighest
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StreakBatteryAnimation(Modifier.size(28.dp), imageLoader = imageLoader)
            Column {
                Text(if (days == null) "—" else stringResource(R.string.account_streak_days, days),
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.account_streak), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.weight(1f).height(32.dp)) {
                val gap = 4.dp.toPx()
                val width = (size.width - 5 * gap) / 6
                for (index in 0..5) {
                    val x = index * (width + gap)
                    val slant = width * 0.28f
                    val path = Path().apply {
                        moveTo(x + slant, 1f); lineTo(x + width, 1f)
                        lineTo(x + width - slant, size.height - 1f); lineTo(x, size.height - 1f); close()
                    }
                    drawPath(path, if (index < active) accent.copy(alpha = 0.3f) else inactive)
                    if (index < active) drawPath(path, accent, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.info_24dp_e3e3e3_fill0_wght400_grad0_opsz24), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(if ((days ?: 0) > 0) R.string.account_streak_keep else R.string.account_streak_activate),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
