package com.lechixy.kick.components.channel.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun ChatComposerPanel(
    compact: Boolean,
    expanded: Boolean,
    allowAutoHide: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onHide: () -> Unit,
    collapsedActions: (@Composable () -> Unit)? = null,
    chatContent: @Composable () -> Unit = {},
    rulesFooter: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val chatActive = LocalChatContentActive.current
    LaunchedEffect(compact, expanded, allowAutoHide, chatActive, rulesFooter != null) {
        if (compact && expanded && allowAutoHide && chatActive && rulesFooter == null) {
            delay(5_000.milliseconds)
            onExpandedChange(false)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            chatContent()

            if (compact && chatActive && rulesFooter == null) {
                val buttonPosition = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 6.dp)
                ChatRestoreOverlay(visible = !expanded, modifier = buttonPosition) {
                    ChatRestoreButton(R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                        stringResource(R.string.chat_show_composer)) {
                        onExpandedChange(true)
                    }
                }
                ChatRestoreOverlay(visible = expanded, modifier = buttonPosition) {
                    ChatRestoreButton(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                        stringResource(R.string.chat_hide_composer)) {
                        onHide()
                        onExpandedChange(false)
                    }
                }

                if (collapsedActions != null) {
                    ChatRestoreOverlay(visible = !expanded, side = -1,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 6.dp)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            collapsedActions()
                        }
                    }
                }
            }
        }
        if (rulesFooter != null) {
            rulesFooter()
        } else if (!compact) {
            content()
        } else {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(220), expandFrom = Alignment.Bottom) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(180), shrinkTowards = Alignment.Bottom) + fadeOut(tween(130))
            ) {
                content()
            }
        }
    }
}
