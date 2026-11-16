package com.lechixy.kick.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R

@Composable
internal fun <T> FilterDropdown(
    title: String,
    options: List<T>,
    label: @Composable (T) -> String,
    selected: Boolean = true,
    compact: Boolean = false,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
        FilterChip(
            selected = selected,
            shape = if (compact) RoundedCornerShape(6.dp) else FilterChipDefaults.shape,
            colors = if (compact) FilterChipDefaults.filterChipColors(
                containerColor = scheme.surfaceContainerHigh,
                selectedContainerColor = scheme.surfaceContainerHigh,
            ) else FilterChipDefaults.filterChipColors(),
            border = if (compact) BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.3f))
            else FilterChipDefaults.filterChipBorder(enabled = true, selected = selected),
            onClick = { expanded = true },
            label = { Text(title) },
            trailingIcon = {
                Icon(
                    painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = {
                    expanded = false
                    onSelect(option)
                })
            }
        }
    }
}
