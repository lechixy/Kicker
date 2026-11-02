package com.lechixy.kick.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing

private val primaryDestinations = setOf("home", "following", "search", "browse", "events", "settings")
private fun changesPrimaryDestination(from: String?, to: String?) =
    from?.substringBefore('?') in primaryDestinations && to?.substringBefore('?') in primaryDestinations

internal fun navigationEnter(from: String?, to: String?) = if (changesPrimaryDestination(from, to))
    fadeIn(tween(220, easing = FastOutSlowInEasing))
else fadeIn(tween(240, delayMillis = 60)) +
    scaleIn(initialScale = .98f, animationSpec = tween(300, easing = FastOutSlowInEasing))

internal fun navigationExit(from: String?, to: String?) = fadeOut(tween(
    if (changesPrimaryDestination(from, to)) 180 else 150, easing = FastOutSlowInEasing))

internal fun navigationPopEnter(from: String? = null, to: String? = null) = if (changesPrimaryDestination(from, to))
    fadeIn(tween(220, easing = FastOutSlowInEasing))
else fadeIn(tween(300)) + scaleIn(initialScale = .98f, animationSpec = tween(300, easing = FastOutSlowInEasing))

internal fun navigationPopExit(from: String? = null, to: String? = null) = if (changesPrimaryDestination(from, to))
    fadeOut(tween(180, easing = FastOutSlowInEasing))
else fadeOut(tween(300)) + scaleOut(targetScale = .98f, animationSpec = tween(300, easing = FastOutSlowInEasing))

internal fun navigationDestinationSelected(route: String?, activeChannelSlug: String?, destination: String): Boolean =
    activeChannelSlug == null && route?.substringBefore('?')?.substringBefore('/') == destination
