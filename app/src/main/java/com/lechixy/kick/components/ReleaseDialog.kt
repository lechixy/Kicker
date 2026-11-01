package com.lechixy.kick.components

import android.content.Intent
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.lechixy.kick.BuildConfig
import com.lechixy.kick.R
import com.lechixy.kick.data.releases.*
import io.noties.markwon.Markwon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val LocalReleasePreview = staticCompositionLocalOf<(ReleaseDialogMode) -> Unit> { {} }

@Composable
fun ReleaseDialog(request: ReleaseDialogRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val release = request.release
    val update = request.mode == ReleaseDialogMode.UpdateAvailable
    fun open(url: String) {
        val uri = url.toUri()
        if (uri.scheme == "https" && !uri.host.isNullOrBlank())
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
    val date = remember(release.publishedAt, locale) { runCatching {
        DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", locale).withZone(ZoneId.systemDefault())
            .format(Instant.parse(release.publishedAt))
    }.getOrNull() }
    com.lechixy.kick.components.PredictiveAlertDialog(onDismissRequest = onDismiss,
        title = { Text(release.name?.takeIf { it.isNotBlank() } ?: release.tagName, maxLines = 3) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SuggestionChip(onClick = {}, label = { Text(if (update)
                    "v${normalizeVersion(BuildConfig.VERSION_NAME)} → v${normalizeVersion(release.tagName)}"
                    else "v${normalizeVersion(release.tagName)}") })
                Text(stringResource(if (update) R.string.release_update_available else R.string.release_whats_new), color = MaterialTheme.colorScheme.primary)
                date?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                TextButton(onClick = { open(release.htmlUrl) }) { Text(stringResource(R.string.release_on_github)) }
                val body = release.body?.takeIf { it.isNotBlank() } ?: stringResource(R.string.release_no_notes)
                val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
                val accent = MaterialTheme.colorScheme.primary.toArgb()
                val markwon = remember(context) { Markwon.create(context) }
                AndroidView(modifier = Modifier.fillMaxWidth(), factory = { TextView(it).apply {
                    textSize = 14f; setLineSpacing(0f, 1.15f); setHorizontallyScrolling(false)
                    movementMethod = LinkMovementMethod.getInstance()
                } }, update = {
                    it.setTextColor(foreground); it.setLinkTextColor(accent)
                    markwon.setMarkdown(it, body)
                })
            }
        }, confirmButton = {
            TextButton(onClick = {
                if (update) open(ReleaseConfig.STORE_URL.ifBlank { release.htmlUrl })
                onDismiss()
            }) { Text(stringResource(if (update) R.string.release_update else android.R.string.ok)) }
        }, dismissButton = {
            if (update) TextButton(onClick = onDismiss) { Text(stringResource(R.string.release_later)) }
        })
}
