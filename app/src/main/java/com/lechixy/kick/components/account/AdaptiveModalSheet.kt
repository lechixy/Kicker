package com.lechixy.kick.components.account

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.lechixy.kick.components.predictiveDialogTransform
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdaptiveModalSheet(onDismiss: () -> Unit, sheetWidth: Dp = 420.dp, content: @Composable (() -> Unit) -> Unit) {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    if (width < 600.dp) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
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
        com.lechixy.kick.components.PredictiveDialog(onDismissRequest = close,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false), transformContent = false) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect {
                // The Compose scrim owns dimming and fades on the sheet's transition clock.
                window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window?.setDimAmount(0f)
                window?.setWindowAnimations(0)
            }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha * (1f - com.lechixy.kick.components.LocalDialogBackProgress.current * .6f)))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = close
                    ))
                sheetTransition.AnimatedVisibility(visible = { it }, modifier = Modifier.align(Alignment.CenterEnd),
                    enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it } + fadeIn(),
                    exit = slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220))) {
                    Surface(modifier = Modifier.width(sheetWidth).fillMaxHeight().predictiveDialogTransform()
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
