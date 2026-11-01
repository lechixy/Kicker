package com.lechixy.kick.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.lechixy.kick.R

@Composable
fun LiveBadge(){
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = stringResource(R.string.live_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}