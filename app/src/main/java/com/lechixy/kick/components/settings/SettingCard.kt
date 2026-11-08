package com.lechixy.kick.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun SettingCard(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: Int? = null,
    image: Int? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(8.dp)
    val resolvedContainerColor = if (enabled) containerColor else containerColor.copy(alpha = containerColor.alpha * 0.5f)
    val resolvedBorder = BorderStroke(
        width = 1.dp,
        color = if (enabled) borderColor else borderColor.copy(alpha = borderColor.alpha * 0.38f)
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = resolvedContainerColor,
        border = resolvedBorder,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    SettingLeadingIcon(icon, enabled, iconTint)
                }

                if (image != null) {
                    Image(
                        painter = painterResource(id = image),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) titleColor else titleColor.copy(alpha = 0.38f)
                    )
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (enabled) descriptionColor else descriptionColor.copy(alpha = 0.38f)
                        )
                    }
                }
            }

            if (action != null) {
                Spacer(modifier = Modifier.width(16.dp))
                action()
            }
        }
    }
}

@Composable
internal fun SettingLeadingIcon(
    icon: Int,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = if (enabled) tint else tint.copy(alpha = tint.alpha * 0.38f),
        modifier = Modifier.padding(end = 16.dp).size(22.dp)
    )
}
