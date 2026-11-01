package com.lechixy.kick.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R

/** Both the search shortcut and editor share icon, text and padding geometry. */
@Composable
fun TopBarSearchField(query: String = "", onQueryChange: ((String) -> Unit)? = null, onClick: () -> Unit = {}) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    val contents: @Composable () -> Unit = {
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.search_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null,
                Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f)) {
                if (onQueryChange == null) Text(stringResource(R.string.search_placeholder), style = textStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                else BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), textStyle = textStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { input ->
                        if (query.isEmpty()) Text(stringResource(R.string.search_placeholder), style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        input()
                    })
            }
            if (onQueryChange != null && query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                Icon(painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24), stringResource(R.string.search_clear))
            } else Spacer(Modifier.width(12.dp))
        }
    }
    val modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().height(56.dp)
    if (onQueryChange == null) Surface(onClick = onClick, modifier = modifier, shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh, content = contents)
    else Surface(modifier = modifier, shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh, content = contents)
}
