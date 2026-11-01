package com.lechixy.kick.components.channel.chat

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.lechixy.kick.R

@Composable
internal fun FrostedChatScrollButton(
    unreadCount: Int,
    backdrop: GraphicsLayer,
    backdropPosition: Offset,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    var position by remember { mutableStateOf(Offset.Zero) }
    val label = if (unreadCount > 0) stringResource(R.string.chat_scroll_unread, unreadCount)
        else stringResource(R.string.chat_scroll_bottom)
    Surface(shape = shape, color = Color.Transparent, contentColor = Color.White,
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = .7f)),
        modifier = Modifier.onGloballyPositioned { position = it.positionInRoot() }.clip(shape)
            .clickable(onClickLabel = stringResource(R.string.chat_scroll_bottom), onClick = onClick)) {
        Box(contentAlignment = Alignment.Center) {
            // Sample the chat layer behind this pill. Blurring a gradient alone cannot blur the messages.
            Box(Modifier.matchParentSize().graphicsLayer {
                if (Build.VERSION.SDK_INT >= 31) renderEffect = BlurEffect(18.dp.toPx(), 18.dp.toPx(), TileMode.Clamp)
            }.drawWithContent {
                val offset = backdropPosition - position
                translate(offset.x, offset.y) { drawLayer(backdrop) }
            })
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .60f)))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    null, Modifier.size(18.dp), tint = Color.White)
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
