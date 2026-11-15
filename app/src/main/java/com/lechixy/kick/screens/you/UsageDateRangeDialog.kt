package com.lechixy.kick.screens.you

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R

// Material date selection represents calendar dates at UTC midnight.
private const val UTC_DAY_MILLIS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsageDateRangeDialog(initial: LongRange?, onDismiss: () -> Unit, onApply: (LongRange) -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.first?.times(UTC_DAY_MILLIS),
        initialSelectedEndDateMillis = initial?.last?.times(UTC_DAY_MILLIS),
    )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = start != null && end != null, onClick = {
                if (start != null && end != null) onApply((start / UTC_DAY_MILLIS)..(end / UTC_DAY_MILLIS))
            }) { Text(stringResource(R.string.you_usage_apply_range)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    ) {
        DateRangePicker(state, modifier = Modifier.heightIn(max = 460.dp), showModeToggle = true)
    }
}
