package com.lechixy.kick.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

import com.lechixy.kick.R
import com.lechixy.kick.data.common.model.LiveTotals

/** Phones show a compact summary; tablets retain the descriptive labels. */
@Composable
fun LiveTotalsButton(totals: LiveTotals, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val format = NumberFormat.getIntegerInstance(locale)
    val streamers = format.format(totals.totalStreamers)
    val viewers = format.format(totals.totalViewers)
    val description = stringResource(R.string.home_live_totals_description, streamers, viewers)
    val compact = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() < 600.dp }
    ElevatedButton(
        onClick = onClick,
        modifier = modifier.widthIn(max = if (compact) 280.dp else 340.dp)
            .wrapContentWidth()
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = if (compact) 16.dp else 20.dp, vertical = if (compact) 8.dp else 10.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .78f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 6.dp)
    ) {
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.visibility_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = stringResource(R.string.home_total_viewers), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(viewers, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                VerticalDivider(Modifier.height(18.dp).padding(horizontal = 4.dp))
                Icon(painterResource(R.drawable.play_circle_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = stringResource(R.string.home_live_streamers), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(streamers, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row( verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.visibility_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column {
                    Text(viewers, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.home_total_viewers), style = MaterialTheme.typography.labelSmall)
                }
            }
            VerticalDivider(Modifier.height(28.dp).padding(horizontal = 12.dp))
            Row( verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.play_circle_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column {
                    Text(streamers, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.home_live_streamers), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
