package com.lechixy.kick.screens.settings

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window

import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.combine
import java.text.DateFormat
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

import com.lechixy.kick.BuildConfig
import com.lechixy.kick.R
import com.lechixy.kick.components.settings.SettingSwitch
import com.lechixy.kick.components.settings.SettingsTopBar
import com.lechixy.kick.data.player.remote.ViewerTrackingDiagnostics
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.util.findActivity

@Composable
fun DeveloperToolsScreen(onBackClick: () -> Unit, onTriggerLive: () -> Unit, canTriggerLive: Boolean) {
    val repository = LocalSettingsRepository.current
    val unlocked by repository.get(SettingsKeys.DEVELOPER_ENABLED).collectAsStateWithLifecycle(false)
    Scaffold(topBar = { SettingsTopBar(stringResource(R.string.developer_tools), onBackClick) }) { padding ->
        if (BuildConfig.DEBUG || unlocked) Column(Modifier.fillMaxSize().padding(padding).wrapContentWidth(androidx.compose.ui.Alignment.CenterHorizontally).widthIn(max = 840.dp).fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingSwitch(key = SettingsKeys.DEVELOPER_FLOATING_MENU, title = stringResource(R.string.developer_floating_menu))
            DeveloperToolsContent(onTriggerLive, canTriggerLive)
        }
    }
}

@Composable
private fun DeveloperToolsContent(onTriggerLive: () -> Unit, canTriggerLive: Boolean) {
    val previewRelease = com.lechixy.kick.components.LocalReleasePreview.current
    Text(stringResource(R.string.developer_notification_previews), style = MaterialTheme.typography.titleMedium)
    Button(onClick = { com.lechixy.kick.data.account.AccountGraph.notifications.preview(com.lechixy.kick.data.notifications.NotificationKind.WatchTimeGoal) }) {
        Text(stringResource(R.string.developer_reward_notification))
    }
    Button(onClick = { com.lechixy.kick.data.account.AccountGraph.notifications.preview(com.lechixy.kick.data.notifications.NotificationKind.StreamStarted) }) {
        Text(stringResource(R.string.developer_live_notification))
    }
    TextButton(onClick = { previewRelease(com.lechixy.kick.data.releases.ReleaseDialogMode.WhatsNew) }) { Text(stringResource(R.string.release_whats_new)) }
    TextButton(onClick = { previewRelease(com.lechixy.kick.data.releases.ReleaseDialogMode.UpdateAvailable) }) { Text(stringResource(R.string.release_update_available)) }
    HorizontalDivider()
    PlayerDiagnosticsContent()
    HorizontalDivider()
    val state by ViewerTrackingDiagnostics.state.collectAsStateWithLifecycle()
    fun time(value: Long?) = value?.let { DateFormat.getTimeInstance().format(Date(it)) } ?: "—"
    Button(onClick = onTriggerLive, enabled = canTriggerLive) { Text(stringResource(R.string.developer_trigger_live)) }
    if (!canTriggerLive) Text(stringResource(R.string.developer_live_required), style = MaterialTheme.typography.bodySmall)
    HorizontalDivider()
    Text(stringResource(R.string.developer_viewer_socket), style = MaterialTheme.typography.titleMedium)
    val status = when (state.status) {
        "metadata" -> R.string.developer_status_metadata
        "token" -> R.string.developer_status_token
        "connecting" -> R.string.developer_status_connecting
        "connected" -> R.string.developer_status_connected
        "retrying" -> R.string.developer_status_retrying
        else -> R.string.developer_status_idle
    }
    Text(stringResource(status))
    Text("Channel: ${state.session?.channelId ?: "—"}\nLivestream: ${state.session?.livestreamId ?: "—"}\nVOD: ${state.session?.vodId ?: "—"}", style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.developer_socket_times, time(state.connectedAt), time(state.lastPing), time(state.lastPong),
        state.latencyMs?.toString() ?: "—", time(state.lastHandshake), time(state.lastWatchEvent), state.reconnects), style = MaterialTheme.typography.bodySmall)
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun PlayerDiagnosticsContent() {
    val state by com.lechixy.kick.service.player.PlaybackDiagnostics.state.collectAsStateWithLifecycle()
    fun number(value: Number?, digits: Int = 1): String = value?.let {
        String.format(java.util.Locale.getDefault(), "%.${digits}f", it.toDouble()).trim()
    } ?: "—"
    Text(stringResource(R.string.developer_player), style = MaterialTheme.typography.titleMedium)
    if (!state.active) {
        Text(stringResource(R.string.developer_player_idle), style = MaterialTheme.typography.bodySmall)
        state.errorCode?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        return
    }
    val mode = when {
        state.buffering -> R.string.developer_player_buffering
        !state.playing -> R.string.developer_player_paused
        !state.live -> R.string.developer_player_recording
        state.requestedSpeed != 1f -> R.string.developer_player_manual
        state.speed > 1.001f -> R.string.developer_player_catching_up
        state.speed < .999f -> R.string.developer_player_slowing
        else -> R.string.developer_player_steady
    }
    Text(stringResource(mode), color = MaterialTheme.colorScheme.primary)
    Text(stringResource(R.string.developer_player_speed, number(state.speed, 3), number(state.requestedSpeed, 3)),
        style = MaterialTheme.typography.bodySmall)
    if (state.live) {
        Text(stringResource(R.string.developer_player_offsets, number(state.liveOffsetMs?.div(1000.0)),
            number(state.targetOffsetMs?.div(1000.0)), number(state.behindTargetMs?.div(1000.0))),
            style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.developer_player_offset_note), style = MaterialTheme.typography.bodySmall)
    }
    Text(stringResource(R.string.developer_player_buffer, number(state.bufferedMs / 1000.0), state.rebuffers,
        number(state.bufferingMs / 1000.0)), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.developer_player_frames, state.droppedFrames, state.skippedFrames, state.renderedFrames),
        style = MaterialTheme.typography.bodySmall)
    state.lastDropAgeMs?.let { age ->
        Text(stringResource(R.string.developer_player_last_drop, state.lastDropCount, number(age / 1000.0)),
            style = MaterialTheme.typography.bodySmall)
    }
    Text(stringResource(R.string.developer_player_processing, number(state.processingOffsetMs), state.audioUnderruns),
        style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.developer_player_format, state.width?.toString() ?: "—", state.height?.toString() ?: "—",
        number(state.frameRate), number(state.videoBitrate?.div(1000000.0)), number(state.bandwidthBps?.div(1000000.0))),
        style = MaterialTheme.typography.bodySmall)
    state.errorCode?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

/** A bounded, non-focusable application subwindow. It never participates in screen measurement,
 * changes soft-input behavior, dims a dialog, or intercepts touches outside its own bounds. */
@Composable
fun DeveloperToolsOverlay(isInPipMode: Boolean, onTriggerLive: () -> Unit, canTriggerLive: Boolean) {
    val repository = LocalSettingsRepository.current
    val unlocked by repository.get(SettingsKeys.DEVELOPER_ENABLED).collectAsStateWithLifecycle(false)
    val floating by repository.get(SettingsKeys.DEVELOPER_FLOATING_MENU).collectAsStateWithLifecycle(true)
    val activity = LocalContext.current.findActivity() as? ComponentActivity ?: return
    val parent = rememberCompositionContext()
    val trigger by rememberUpdatedState(onTriggerLive)
    val canTrigger by rememberUpdatedState(canTriggerLive)
    val scope = rememberCoroutineScope()
    val position by remember(repository) {
        combine(repository.get(SettingsKeys.DEVELOPER_MENU_X), repository.get(SettingsKeys.DEVELOPER_MENU_Y)) { x, y -> x to y }
    }.collectAsStateWithLifecycle(initialValue = null)
    val savedPosition = position ?: return
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    if ((!BuildConfig.DEBUG && !unlocked) || !floating || isInPipMode) return
    DisposableEffect(activity, parent, configuration.screenWidthDp, configuration.screenHeightDp) {
        val manager = activity.getSystemService(WindowManager::class.java)
        var xFraction = savedPosition.first.coerceIn(0f, 1f)
        var yFraction = savedPosition.second.coerceIn(0f, 1f)
        val windowBounds = manager.currentWindowMetrics.bounds
        val maxPanelWidth = with(density) { windowBounds.width().toDp() - 16.dp }
        val maxPanelHeight = with(density) { windowBounds.height().toDp() - 48.dp }
        val expandedWidth = with(density) { minOf(340.dp, maxPanelWidth).roundToPx() }
        val collapsedWidth = with(density) { minOf(260.dp, maxPanelWidth).roundToPx() }
        val expandedHeight = with(density) { minOf(468.dp, maxPanelHeight).roundToPx() }
        val collapsedHeight = with(density) { 48.dp.roundToPx() }
        val view = ComposeView(activity).apply {
            setParentCompositionContext(parent)
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        val params = WindowManager.LayoutParams(
            if (expanded) expandedWidth else collapsedWidth,
            if (expanded) expandedHeight else collapsedHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.LEFT; token = activity.window.decorView.windowToken }
        val initialRoomX = (windowBounds.width() - params.width).coerceAtLeast(0)
        val initialRoomY = (windowBounds.height() - params.height).coerceAtLeast(0)
        params.x = (initialRoomX * xFraction).toInt()
        params.y = (initialRoomY * yFraction).toInt()
        var resizeJob: Job? = null
        var idleJob: Job? = null
        var idleHidden by mutableStateOf(false)
        fun recordInput() {
            idleJob?.cancel()
            idleHidden = false
            view.visibility = View.VISIBLE
            if (params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                if (view.isAttachedToWindow) manager.updateViewLayout(view, params)
            }
            idleJob = scope.launch {
                delay(7_000.milliseconds)
                idleHidden = true
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                if (view.isAttachedToWindow) manager.updateViewLayout(view, params)
                delay(180.milliseconds)
                view.visibility = View.INVISIBLE
            }
        }
        // Observe input without consuming it, including when a Compose dialog owns the window.
        val inputCallbacks = mutableMapOf<Window, Pair<Window.Callback, Window.Callback>>()
        fun observeWindow(window: Window) {
            if (window in inputCallbacks) return
            val original = window.callback ?: return
            val observer = object : Window.Callback by original {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    recordInput()
                    return original.dispatchTouchEvent(event)
                }
                override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
                    recordInput()
                    return original.dispatchGenericMotionEvent(event)
                }
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    recordInput()
                    return original.dispatchKeyEvent(event)
                }
            }
            inputCallbacks[window] = original to observer
            window.callback = observer
        }
        fun observeDialogWindows(root: View) {
            if (root is DialogWindowProvider) observeWindow(root.window)
            if (root is ViewGroup) for (index in 0 until root.childCount) observeDialogWindows(root.getChildAt(index))
        }
        observeWindow(activity.window)
        recordInput()
        fun savePosition() {
            val x = xFraction
            val y = yFraction
            scope.launch {
                repository.set(SettingsKeys.DEVELOPER_MENU_X, x)
                repository.set(SettingsKeys.DEVELOPER_MENU_Y, y)
            }
        }
        fun resizeWindow(isExpanded: Boolean) {
            val bounds = manager.currentWindowMetrics.bounds
            params.width = if (isExpanded) expandedWidth else collapsedWidth
            params.height = if (isExpanded) expandedHeight else collapsedHeight
            // Growing content must not continuously move the window's anchor. Clamp only once
            // when changing modes, and let Compose animate inside this fixed native envelope.
            params.x = params.x.coerceIn(0, (bounds.width() - params.width).coerceAtLeast(0))
            params.y = params.y.coerceIn(0, (bounds.height() - params.height).coerceAtLeast(0))
            val roomX = (bounds.width() - params.width).coerceAtLeast(0)
            val roomY = (bounds.height() - params.height).coerceAtLeast(0)
            xFraction = if (roomX > 0) params.x.toFloat() / roomX else 0.5f
            yFraction = if (roomY > 0) params.y.toFloat() / roomY else 0.5f
            if (view.isAttachedToWindow) manager.updateViewLayout(view, params)
            savePosition()
        }
        fun toggleExpanded() {
            resizeJob?.cancel()
            val next = !expanded
            if (next) resizeWindow(true)
            expanded = next
            if (!next) resizeJob = scope.launch {
                delay(220.milliseconds)
                if (!expanded) resizeWindow(false)
            }
        }
        view.setContent {
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }
            val opacity by animateFloatAsState(if (visible && !idleHidden) 1f else 0f, tween(180), label = "developerOpacity")
            val expandDescription = stringResource(if (expanded) R.string.developer_minimize else R.string.developer_expand)
            Surface(shape = MaterialTheme.shapes.large, shadowElevation = 8.dp, tonalElevation = 6.dp,
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            recordInput()
                        }
                    }
                }.graphicsLayer {
                        alpha = opacity; scaleX = 0.96f + opacity * 0.04f; scaleY = scaleX
                    }) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).heightIn(min = 48.dp).pointerInput(Unit) {
                        detectDragGestures(onDragEnd = { savePosition() }, onDragCancel = { savePosition() }) { change, amount ->
                            change.consume()
                            val bounds = manager.currentWindowMetrics.bounds
                            val roomX = (bounds.width() - params.width).coerceAtLeast(0)
                            val roomY = (bounds.height() - params.height).coerceAtLeast(0)
                            params.x = (params.x + amount.x.toInt()).coerceIn(0, roomX)
                            params.y = (params.y + amount.y.toInt()).coerceIn(0, roomY)
                            xFraction = if (roomX > 0) params.x.toFloat() / roomX else 0.5f
                            yFraction = if (roomY > 0) params.y.toFloat() / roomY else 0.5f
                            if (view.isAttachedToWindow) manager.updateViewLayout(view, params)
                        }
                    }, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = ::toggleExpanded, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.developer_tools), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = ::toggleExpanded, modifier = Modifier.size(40.dp).semantics { contentDescription = expandDescription }) {
                            Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge)
                        }
                        IconButton(onClick = {
                            visible = false
                            scope.launch { delay(180.milliseconds); repository.set(SettingsKeys.DEVELOPER_FLOATING_MENU, false) }
                        }, modifier = Modifier.size(40.dp)) {
                            Icon(painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                contentDescription = stringResource(R.string.developer_close), modifier = Modifier.size(20.dp))
                        }
                    }
                    AnimatedVisibility(expanded, enter = expandVertically(tween(220)) + fadeIn(tween(160)),
                        exit = shrinkVertically(tween(220)) + fadeOut(tween(120))) {
                        Column(Modifier.fillMaxWidth().heightIn(max = minOf(420.dp, maxPanelHeight - 48.dp))
                            .verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // A native INVISIBLE window keeps its composition alive.
                            // Drop the live collectors while the panel is idle-hidden.
                            if (!idleHidden) DeveloperToolsContent({ trigger() }, canTrigger)
                        }
                    }
                }
            }
        }
        fun attach() {
            if (!view.isAttachedToWindow && !activity.isFinishing) {
                params.token = activity.window.decorView.windowToken
                if (params.token != null) try { manager.addView(view, params) }
                catch (_: WindowManager.BadTokenException) { /* Parent window has just closed. Retry on focus change. */ }
            }
        }
        fun detach() { if (view.isAttachedToWindow) manager.removeViewImmediate(view) }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) attach()
            if (event == Lifecycle.Event.ON_PAUSE) detach()
        }
        activity.lifecycle.addObserver(observer)
        val attachTask = Runnable { if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) attach() }
        activity.window.decorView.post(attachTask)
        // Dialogs and focusable popups own separate windows. Follow their token so the tools
        // remain above them without taking focus or changing any existing window's flags.
        val followFocusedWindow = scope.launch {
            while (isActive) {
                delay(200.milliseconds)
                if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) continue
                val roots = WindowInspector.getGlobalWindowViews()
                roots.filter { it !== view }.forEach(::observeDialogWindows)
                val closed = inputCallbacks.keys.filter { it !== activity.window && !it.decorView.isAttachedToWindow }
                closed.forEach { window ->
                    val callbacks = inputCallbacks.remove(window)!!
                    if (window.callback === callbacks.second) window.callback = callbacks.first
                }
                val focused = roots.lastOrNull {
                    it !== view && it.hasWindowFocus() && it.isAttachedToWindow
                } ?: continue
                if (params.token != focused.windowToken || !view.isAttachedToWindow) {
                    detach()
                    params.token = focused.windowToken
                    try { manager.addView(view, params) }
                    catch (_: WindowManager.BadTokenException) { /* Dialog closed during attachment. */ }
                }
            }
        }
        onDispose {
            resizeJob?.cancel()
            idleJob?.cancel()
            inputCallbacks.forEach { (window, callbacks) ->
                if (window.callback === callbacks.second) window.callback = callbacks.first
            }
            followFocusedWindow.cancel()
            activity.window.decorView.removeCallbacks(attachTask)
            activity.lifecycle.removeObserver(observer)
            detach()
            view.disposeComposition()
        }
    }
}
