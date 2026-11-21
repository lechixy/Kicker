package com.lechixy.kick.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

import com.lechixy.kick.data.settings.model.SettingKey
import com.lechixy.kick.data.settings.model.resolvedDefault
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository

@Composable
fun SettingTextField(
    key: SettingKey<String>,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true,
    icon: Int? = null
) {
    val repository = LocalSettingsRepository.current
    val coroutineScope = rememberCoroutineScope()
    val default = key.resolvedDefault
    val persistentValue by remember(repository, key) { repository.get(key) }.collectAsStateWithLifecycle(default)

    var draftValue by rememberSaveable { mutableStateOf(persistentValue) }
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Dışarıdan (örneğin import veya başka ekran) gelen güncellemeleri
    // yalnızca kullanıcı aktif olarak metin alanına odaklanmamışsa eşitle
    LaunchedEffect(persistentValue) {
        if (!isFocused && draftValue != persistentValue) {
            draftValue = persistentValue
        }
    }

    fun commit() {
        val value = draftValue
        if (value != persistentValue) {
            coroutineScope.launch {
                repository.set(key, value)
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        val fieldFontSize = MaterialTheme.typography.bodyLarge.fontSize
        Layout(
            contents = listOf(
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) SettingLeadingIcon(icon, enabled)
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (description != null) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                {
                    OutlinedTextField(
                        value = draftValue,
                        onValueChange = { draftValue = it },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { focusState ->
                            if (isFocused && !focusState.isFocused) commit()
                            isFocused = focusState.isFocused
                        },
                        enabled = enabled,
                        singleLine = singleLine,
                        placeholder = { Text(text = placeholder) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                        shape = RoundedCornerShape(6.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            commit()
                            focusManager.clearFocus()
                        }),
                    )
                },
                {
                    if (draftValue != default || persistentValue != default) {
                        SettingResetButton(title, onClick = {
                            draftValue = default
                            coroutineScope.launch { repository.reset(key) }
                        }, enabled = enabled)
                    }
                },
            ),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { (headerItems, fieldItems, resetItems), constraints ->
            val gap = 12.dp.roundToPx()
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val header = headerItems.single()
            val field = fieldItems.single()
            val reset = resetItems.singleOrNull()?.measure(loose)
            val resetSpace = reset?.let { it.width + 4.dp.roundToPx() } ?: 0
            val availableWidth = if (constraints.hasBoundedWidth) (constraints.maxWidth - resetSpace).coerceAtLeast(0)
                else Constraints.Infinity
            val fieldWidth = maxOf(144.dp.roundToPx(), (fieldFontSize * 8).roundToPx())
            val headerWidth = header.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(280.dp.roundToPx())
            val sideBySide = constraints.hasBoundedWidth && headerWidth + gap + fieldWidth <= availableWidth
            val headerPlaceable = header.measure(
                loose.copy(maxWidth = if (sideBySide) availableWidth - gap - fieldWidth else availableWidth)
            )
            val fieldPlaceable = field.measure(
                if (sideBySide) loose.copy(minWidth = fieldWidth, maxWidth = fieldWidth) else loose
            )
            val headerHeight = maxOf(headerPlaceable.height, reset?.height ?: 0)
            val topHeight = if (sideBySide) maxOf(headerHeight, fieldPlaceable.height) else headerHeight
            val height = if (sideBySide) topHeight else topHeight + gap + fieldPlaceable.height
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth
                else maxOf(headerPlaceable.width + resetSpace, fieldPlaceable.width).coerceAtLeast(constraints.minWidth)
            layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
                headerPlaceable.placeRelative(0, (topHeight - headerPlaceable.height) / 2)
                if (sideBySide) {
                    fieldPlaceable.placeRelative(width - resetSpace - fieldPlaceable.width, (topHeight - fieldPlaceable.height) / 2)
                } else {
                    fieldPlaceable.placeRelative(0, topHeight + gap)
                }
                reset?.placeRelative(width - reset.width, (topHeight - reset.height) / 2)
            }
        }
    }
}
