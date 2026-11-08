package com.lechixy.kick.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.components.account.RewardAvailability
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.notifications.AppNotification
import com.lechixy.kick.data.notifications.NotificationKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun InAppNotificationHost(onActivate: (AppNotification) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val repository = AccountGraph.notifications
    val notifications by repository.notifications.collectAsStateWithLifecycle()
    val session by AccountGraph.auth.session.collectAsStateWithLifecycle()
    val next = if (!enabled) null else notifications.firstOrNull { it.kind.isSleepTimer || it.preview ||
        (session.signedIn && session.generation == it.generation && session.profile?.userId == it.userId) }
    var displayed by remember { mutableStateOf<AppNotification?>(null) }
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(next?.id) {
        if (next == null) { visible = false; delay(260.milliseconds); displayed = null; return@LaunchedEffect }
        displayed = next
        visible = true
        delay(7_000.milliseconds)
        visible = false
        delay(260.milliseconds)
        repository.dismiss(next.id)
    }
    val current = displayed
    AnimatedVisibility(visible && current != null, modifier = modifier.statusBarsPadding().padding(top = 16.dp, start = 16.dp, end = 16.dp),
        enter = slideInVertically(tween(400, easing = FastOutSlowInEasing)) { -it - 100 } + fadeIn(tween(240)),
        exit = slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { -it - 100 } + fadeOut(tween(180))) {
        if (current != null) NotificationCard(current, onClick = {
            if (visible) scope.launch {
                visible = false
                delay(260.milliseconds)
                repository.dismiss(current.id)
                if (repository.accepts(current)) onActivate(current)
            }
        }, onDismiss = {
            scope.launch { visible = false; delay(260.milliseconds); repository.dismiss(current.id) }
        })
    }
}

@Composable
internal fun NotificationCard(notification: AppNotification, onClick: () -> Unit, onDismiss: () -> Unit) {
    val title = when (notification.kind) {
        NotificationKind.WatchTimeGoal -> stringResource(R.string.notification_reward_title)
        NotificationKind.SleepTimerStarted -> stringResource(R.string.sleep_timer_started_title)
        NotificationKind.SleepTimerStopped -> stringResource(R.string.sleep_timer_stopped_title)
        NotificationKind.StreamStarted -> notification.title.takeIf { it.isNotBlank() }?.let {
            HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
        } ?: stringResource(R.string.notification_live_title, notification.channelSlug.orEmpty())
    }
    val description = when (notification.kind) {
        NotificationKind.WatchTimeGoal -> stringResource(R.string.notification_reward_description, notification.thresholdMinutes)
        NotificationKind.SleepTimerStarted -> stringResource(R.string.sleep_timer_started_description, notification.timerMinutes)
        NotificationKind.SleepTimerStopped -> stringResource(R.string.sleep_timer_stopped_description)
        NotificationKind.StreamStarted -> notification.description.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.notification_live_description)
    }
    key(notification.id) {
    SwipeDismissBox(allowUp = true, onDismiss = { onDismiss() }, modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .35f)), shadowElevation = 12.dp) {
        Row(Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                when (notification.kind) {
                    NotificationKind.WatchTimeGoal -> RewardAvailability(true)
                    NotificationKind.StreamStarted -> LiveNotificationIcon()
                    NotificationKind.SleepTimerStarted ->
                        ActiveSleepTimerIcon(Modifier.size(28.dp), contentDescription = null)
                    NotificationKind.SleepTimerStopped -> StoppedSleepTimerIcon()
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    maxLines = if (notification.kind.isSleepTimer) 2 else 1, overflow = TextOverflow.Ellipsis)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

        }
    }
    }
    }
}

@Composable
private fun StoppedSleepTimerIcon() {
    val impact = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        impact.animateTo(1f, tween(durationMillis = 100, delayMillis = 160, easing = FastOutSlowInEasing))
        impact.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    Icon(
        painterResource(R.drawable.timer_off_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
        contentDescription = null,
        modifier = Modifier.size(28.dp).graphicsLayer {
            val press = impact.value
            scaleX = 1f + .06f * press
            scaleY = 1f - .19f * press
            rotationZ = -9f * press
        },
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun LiveNotificationIcon(
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "broadcast")
    val pulse by transition.animateFloat(.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "signal")
    Canvas(Modifier.size(36.dp)) {
        drawCircle(color, size.minDimension * .10f)
        for (ring in 1..2) {
            val radius = size.minDimension * (.15f + .14f * ring)
            for (angle in listOf(-55f, 125f)) drawArc(color.copy(alpha = pulse), angle, 110f, false,
                Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(2.dp.toPx()))
        }
    }
}
