package com.lechixy.kick.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal val LocalDialogBackProgress = staticCompositionLocalOf { 0f }

@Composable
internal fun Modifier.predictiveDialogTransform(): Modifier {
    val progress = LocalDialogBackProgress.current
    return graphicsLayer {
        scaleX = 1f - .09f * progress; scaleY = scaleX
        translationX = size.width * .06f * progress
        alpha = 1f - .15f * progress
        transformOrigin = TransformOrigin(.8f, .5f)
    }
}

@Composable
internal fun rememberPredictiveBackProgress(enabled: Boolean = true, onBack: () -> Unit): State<Float> {
    val onCommit by rememberUpdatedState(onBack)
    val progress = remember { Animatable(0f) }
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { progress.snapTo(it.progress.coerceIn(0f, 1f)) }
            progress.animateTo(1f, tween(120))
            onCommit()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { progress.animateTo(0f, tween(180)) }
        }
    }
    return progress.asState()
}

/** The handler belongs to the dialog's ComponentDialog dispatcher, not its underlying Activity. */
@Composable
internal fun PredictiveDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    transformContent: Boolean = true,
    content: @Composable () -> Unit
) {
    val dismiss by rememberUpdatedState(onDismissRequest)
    // Keep native keyboard/Escape fallback; our later callback owns predictive progress.
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        val progress = rememberPredictiveBackProgress(properties.dismissOnBackPress, dismiss)
        CompositionLocalProvider(LocalDialogBackProgress provides progress.value) {
            Box(if (transformContent) Modifier.predictiveDialogTransform() else Modifier) { content() }
        }
    }
}

@Composable
internal fun PredictiveAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null
) {
    PredictiveDialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(24.dp).widthIn(min = 280.dp, max = 560.dp).fillMaxWidth(),
            shape = AlertDialogDefaults.shape, color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation) {
            Column(Modifier.padding(24.dp)) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
                Box(Modifier.weight(1f, fill = false).padding(top = 16.dp, bottom = 20.dp)) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
