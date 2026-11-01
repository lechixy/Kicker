package com.lechixy.kick.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.lechixy.kick.R
import com.lechixy.kick.data.favorites.local.FavoriteUsersStore

@Composable
fun FavoriteUserButton(userId: Long?, username: String, slug: String = "", modifier: Modifier = Modifier,
    colors: IconButtonColors? = null, matchButtonSize: Boolean = false) {
    val favorites by FavoriteUsersStore.favorites.collectAsStateWithLifecycle()
    val favorite = favorites.contains(userId, username, slug)
    val enabled = userId != null || username.isNotBlank() || slug.isNotBlank()
    val containerColor = if (favorite) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
    val contentColor = if (favorite) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
    val icon: @Composable () -> Unit = {
        Icon(painterResource(if (favorite) R.drawable.star_24dp_e3e3e3_fill1_wght400_grad0_opsz24
            else R.drawable.star_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
            stringResource(if (favorite) R.string.favorite_user_remove else R.string.favorite_user_add),
            Modifier.size(24.dp))
    }
    if (matchButtonSize) {
        FilledTonalButton(onClick = { FavoriteUsersStore.toggle(userId, username, slug) }, enabled = enabled,
            modifier = modifier.size(ButtonDefaults.MinHeight).semantics { selected = favorite },
            contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = containerColor, contentColor = contentColor)) { icon() }
    } else {
        FilledTonalIconButton(onClick = { FavoriteUsersStore.toggle(userId, username, slug) }, enabled = enabled,
            modifier = modifier.size(48.dp).semantics { selected = favorite }, shape = RoundedCornerShape(8.dp),
            colors = colors ?: IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = containerColor, contentColor = contentColor)) { icon() }
    }
}
