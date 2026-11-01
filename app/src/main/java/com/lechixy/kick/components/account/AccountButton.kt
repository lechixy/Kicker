package com.lechixy.kick.components.account

import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

import com.lechixy.kick.R
import com.lechixy.kick.data.account.AccountGraph

@Composable
internal fun AccountAvatar(avatar: String?, name: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun AccountButton(onChannelClick: (String) -> Unit = com.lechixy.kick.components.LocalContentNavigation.current.channel, modifier: Modifier = Modifier) {
    val session by AccountGraph.auth.session.collectAsStateWithLifecycle()
    val dashboard by AccountGraph.dashboard.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val name = if (session.signedIn) dashboard.channel?.user?.username ?: session.profile?.name
        ?: stringResource(R.string.account_my_account) else stringResource(R.string.account_sign_in)
    Surface(onClick = { open = true }, shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.widthIn(max = 210.dp), tonalElevation = 2.dp) {
        Row(Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (session.signedIn) AccountAvatar(dashboard.channel?.user?.avatar ?: session.profile?.avatar, name, Modifier.size(34.dp))
            else Icon(painterResource(R.drawable.person_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = null, modifier = Modifier.padding(5.dp).size(24.dp))
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
            Icon(painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) AdaptiveModalSheet(onDismiss = { open = false }) { close -> AccountDashboard(onChannelClick, close) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdaptiveModalSheet(onDismiss: () -> Unit, sheetWidth: Dp = 420.dp, content: @Composable (() -> Unit) -> Unit) {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    if (width < 600.dp) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            content(onDismiss)
        }
    } else {
        val transition = remember { MutableTransitionState(false).apply { targetState = true } }
        val sheetTransition = rememberTransition(transition, label = "Account sheet")
        val scrimAlpha by sheetTransition.animateFloat(
            transitionSpec = { tween(durationMillis = 220, easing = FastOutSlowInEasing) },
            label = "Account sheet scrim"
        ) { visible -> if (visible) 0.5f else 0f }
        val close = { transition.targetState = false }
        LaunchedEffect(transition) {
            snapshotFlow { transition.isIdle && !transition.currentState && !transition.targetState }
                .filter { it }.first()
            onDismiss()
        }
        Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect {
                // The Compose scrim owns dimming and fades on the sheet's transition clock.
                window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window?.setDimAmount(0f)
                window?.setWindowAnimations(0)
            }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = close
                    ))
                sheetTransition.AnimatedVisibility(visible = { it }, modifier = Modifier.align(Alignment.CenterEnd),
                    enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it } + fadeIn(),
                    exit = slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220))) {
                    Surface(modifier = Modifier.width(sheetWidth).fillMaxHeight()
                        .pointerInput(Unit) { detectTapGestures {} },
                        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                        color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                            content(close)
                        }
                    }
                }
            }
        }
    }
}
