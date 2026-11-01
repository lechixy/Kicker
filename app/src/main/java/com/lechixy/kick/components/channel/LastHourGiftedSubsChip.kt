package com.lechixy.kick.components.channel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.dp

import com.lechixy.kick.R

@Composable
fun LastHourGiftedSubsChip(rank: Int, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.secondary
    val textStyle = MaterialTheme.typography.labelSmall.let { style ->
        style.copy(lineHeight = style.fontSize * 1.2f, platformStyle = PlatformTextStyle(includeFontPadding = false))
    }
    val fullLabel = stringResource(R.string.last_hour_gifted_subs_rank, rank)
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 240.dp
        Surface(modifier = Modifier.semantics { contentDescription = fullLabel },
            shape = CircleShape, color = Color.Transparent, contentColor = color, border = BorderStroke(0.75.dp, color)) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(R.drawable.featured_seasonal_and_gifts_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = null, modifier = Modifier.size(12.dp))
                Text(if (compact) stringResource(R.string.last_hour_gifted_subs_rank_compact, rank) else fullLabel,
                    modifier = Modifier.weight(1f, fill = false), style = textStyle,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
