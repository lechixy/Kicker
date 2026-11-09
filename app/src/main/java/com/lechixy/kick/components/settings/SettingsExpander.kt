package com.lechixy.kick.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.data.settings.model.SettingKey
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsExpander(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: Int? = null,
    toggleKey: SettingKey<Boolean>? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.(enabled: Boolean) -> Unit,
) {
    var expanded by rememberSaveable(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    val repository = toggleKey?.let { LocalSettingsRepository.current }
    val scope = rememberCoroutineScope()
    val enabled = if (toggleKey != null && repository != null) {
        remember(repository, toggleKey) { repository.get(toggleKey) }
            .collectAsStateWithLifecycle(toggleKey.defaultValue).value
    } else true
    val rotation = animateFloatAsState(if (expanded) 180f else 0f, tween(220), label = "settings chevron")
    val expansionState = stringResource(if (expanded) R.string.settings_expanded else R.string.settings_collapsed)
    val border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, border),
    ) {
        Column {
            SettingCard(
                title = title,
                modifier = Modifier.semantics { stateDescription = expansionState },
                description = description,
                icon = icon,
                containerColor = Color.Transparent,
                borderColor = Color.Transparent,
                onClick = { expanded = !expanded },
                action = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (toggleKey != null && repository != null) Switch(
                            checked = enabled,
                            onCheckedChange = { checked -> scope.launch { repository.set(toggleKey, checked) } },
                            modifier = Modifier.semantics { contentDescription = title },
                        )
                        Icon(
                            painter = painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation.value },
                        )
                    }
                },
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(240), expandFrom = Alignment.Top) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(220), shrinkTowards = Alignment.Top) + fadeOut(tween(130)),
            ) {
                Column {
                    HorizontalDivider(color = border)
                    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        content(enabled)
                    }
                }
            }
        }
    }
}
