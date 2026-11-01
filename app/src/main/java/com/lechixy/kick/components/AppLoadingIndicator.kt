package com.lechixy.kick.components

import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppLoadingIndicator(modifier: Modifier = Modifier, contained: Boolean = false) {
    if (contained) ContainedLoadingIndicator(modifier = modifier)
    else LoadingIndicator(modifier = modifier)
}
