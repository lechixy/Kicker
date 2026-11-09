package com.lechixy.kick.components.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.components.PredictiveAlertDialog
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.settings.model.SettingKey
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.orderedAppDestinations
import com.lechixy.kick.screens.home.orderedHomeSections
import com.lechixy.kick.visibleInBottomBar
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private data class OrderItem(val id: String, @StringRes val title: Int, @DrawableRes val icon: Int, val onPhone: Boolean = true)

@Composable
internal fun NavigationOrderSetting() {
    val session by AccountGraph.auth.session.collectAsStateWithLifecycle()
    OrderSetting(
        SettingsKeys.NAVIGATION_ORDER,
        R.string.settings_navigation_order,
        R.string.settings_navigation_order_description,
        R.drawable.menu_24dp,
        items = { order ->
            orderedAppDestinations(order).map {
                OrderItem(it.name, it.labelFor(session.signedIn), it.activeIcon, it.visibleInBottomBar())
            }
        },
    ) { ordered ->
        val phoneLabels = ordered.filter { it.onPhone }.map { stringResource(it.title) }
        stringResource(R.string.settings_navigation_order_phone, phoneLabels.joinToString(" · "))
    }
}

@Composable
internal fun HomeSectionOrderSetting() {
    OrderSetting(
        SettingsKeys.HOME_SECTION_ORDER,
        R.string.settings_home_order,
        R.string.settings_home_order_description,
        R.drawable.home_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
        items = { order -> orderedHomeSections(order).map { OrderItem(it.name, it.title, it.icon) } },
    ) {
        stringResource(R.string.settings_home_order_phone, stringResource(R.string.home_following_live),
            stringResource(R.string.home_continue_watching))
    }
}

@Composable
private fun OrderSetting(
    setting: SettingKey<String>,
    @StringRes title: Int,
    @StringRes description: Int,
    @DrawableRes icon: Int,
    items: (String) -> List<OrderItem>,
    warning: @Composable (List<OrderItem>) -> String,
) {
    val repository = LocalSettingsRepository.current
    val saved by repository.get(setting).collectAsStateWithLifecycle(setting.defaultValue)
    val scope = rememberCoroutineScope()
    var initialOrder by rememberSaveable { mutableStateOf<String?>(null) }
    val label = stringResource(title)
    SettingCard(label, icon = icon, description = stringResource(description), onClick = { initialOrder = saved })
    initialOrder?.let { order ->
        OrderDialog(
            label = label,
            initialItems = items(order),
            warning = warning,
            onDismiss = { initialOrder = null },
            onSave = { newOrder ->
                initialOrder = null
                scope.launch { repository.set(setting, newOrder) }
            },
        )
    }
}

@Composable
private fun OrderDialog(
    label: String,
    initialItems: List<OrderItem>,
    warning: @Composable (List<OrderItem>) -> String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(initialItems.joinToString(",") { it.id }) }
    val byId = initialItems.associateBy { it.id }
    val latestById by rememberUpdatedState(byId)
    fun order() = draft.split(',').mapNotNull(latestById::get)
    fun move(from: Int, to: Int) {
        val items = order().toMutableList()
        items.add(to, items.removeAt(from))
        draft = items.joinToString(",") { it.id }
    }

    var dragged by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val rowHeight = 52.dp
    val rowPx = with(LocalDensity.current) { rowHeight.roundToPx().toFloat() }
    val edgePx = with(LocalDensity.current) { 32.dp.toPx() }
    val scroll = rememberScrollState()
    fun dragBy(delta: Float) {
        val items = order()
        val index = items.indexOfFirst { it.id == dragged }
        if (index < 0) return
        offset = (offset + delta).coerceIn(-index * rowPx, (items.lastIndex - index) * rowPx)
        val target = (index + (offset / rowPx).roundToInt()).coerceIn(0, items.lastIndex)
        if (target != index) {
            move(index, target)
            offset -= (target - index) * rowPx
        }
    }
    LaunchedEffect(dragged, rowPx) {
        if (dragged == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val step = ((frame - previous) / 16_666_667f).coerceIn(0f, 2f)
            previous = frame
            val edge = minOf(edgePx, viewportHeight / 2f)
            if (edge <= 0f) continue
            val direction = when {
                fingerY < edge -> (fingerY - edge) / edge
                fingerY > viewportHeight - edge -> (fingerY - viewportHeight + edge) / edge
                else -> 0f
            }
            if (direction != 0f) dragBy(scroll.dispatchRawDelta(direction.coerceIn(-1f, 1f) * rowPx * .14f * step))
        }
    }

    PredictiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            val rows = order()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_order_drag_hint))
                Text(warning(rows), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.fillMaxWidth().onSizeChanged { viewportHeight = it.height }.verticalScroll(scroll)) {
                    Column(Modifier.fillMaxWidth().pointerInput(rowPx) {
                        var beforeDrag = ""
                        detectDragGestures(
                            orientationLock = Orientation.Vertical,
                            onDragStart = { down, _, _ ->
                                val point = down.position
                                dragged = order().getOrNull((point.y / rowPx).toInt())?.id
                                beforeDrag = draft
                                offset = 0f
                                fingerY = point.y - scroll.value
                            },
                            onDragEnd = { dragged = null; offset = 0f },
                            onDragCancel = {
                                if (dragged != null) draft = beforeDrag
                                dragged = null
                                offset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                fingerY += amount.y
                                dragBy(amount.y)
                            },
                        )
                    }) {
                        rows.forEachIndexed { index, item ->
                            key(item.id) {
                                val name = stringResource(item.title)
                                val active = dragged == item.id
                                val moveUp = stringResource(R.string.settings_navigation_move_up, name)
                                val moveDown = stringResource(R.string.settings_navigation_move_down, name)
                                val actions = buildList {
                                    if (index > 0) add(CustomAccessibilityAction(moveUp) { move(index, index - 1); true })
                                    if (index < rows.lastIndex) add(CustomAccessibilityAction(moveDown) { move(index, index + 1); true })
                                }
                                Row(
                                    Modifier.fillMaxWidth().height(rowHeight)
                                        .zIndex(if (active) 1f else 0f)
                                        .offset { IntOffset(0, if (active) offset.roundToInt() else 0) }
                                        .background(if (active) MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                                        .semantics(mergeDescendants = true) { customActions = actions }
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(painterResource(item.icon), null)
                                    Text(name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Icon(painterResource(R.drawable.drag_indicator_24dp), null)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.settings_navigation_order_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
