package com.lechixy.kick.components.channel.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.lechixy.kick.R

@Composable
internal fun ChatScrollButton(
    unreadCount: Int,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    val label = if (unreadCount > 0) stringResource(R.string.chat_scroll_unread, unreadCount)
        else stringResource(R.string.chat_scroll_bottom)
    Surface(shape = shape, color = Color.Black.copy(alpha = .80f), contentColor = Color.White,
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = .7f)),
        modifier = Modifier.clip(shape)
            .clickable(onClickLabel = stringResource(R.string.chat_scroll_bottom), onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                null, Modifier.size(18.dp), tint = Color.White)
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
