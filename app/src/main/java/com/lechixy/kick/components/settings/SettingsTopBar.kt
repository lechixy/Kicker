package com.lechixy.kick.components.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight

import com.lechixy.kick.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(
    title: String,
    onBackClick: () -> Unit
) {
    TopAppBar(
        title = { Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        ) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = "Geri"
                )
            }
        }
    )
}