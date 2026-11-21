package com.lechixy.kick.components

import android.content.Intent
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.lechixy.kick.BuildConfig
import com.lechixy.kick.R
import com.lechixy.kick.data.releases.ReleaseConfig
import com.lechixy.kick.data.releases.ReleaseDialogMode
import com.lechixy.kick.data.releases.ReleaseDialogRequest
import com.lechixy.kick.data.releases.normalizeVersion
import com.lechixy.kick.util.FormatUtils
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


@Composable
fun ReleaseDialog(request: ReleaseDialogRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val release = request.release
    val update = request.mode == ReleaseDialogMode.UpdateAvailable
    fun open(url: String) {
        val uri = url.toUri()
        if (uri.scheme == "https" && !uri.host.isNullOrBlank())
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    val date = release.publicationTime?.let { releaseDateLabel(it) }
    com.lechixy.kick.components.PredictiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(release.name?.takeIf { it.isNotBlank() } ?: release.tagName, maxLines = 3) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    date?.let { label ->
                        Surface(
                            shape = AssistChipDefaults.shape,
                            border = AssistChipDefaults.assistChipBorder(enabled = true),
                        ) {
                            Row(
                                modifier = Modifier.height(AssistChipDefaults.Height).padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painterResource(R.drawable.calendar_month_24dp_e3e3e3_fill1_wght400_grad0_opsz24),
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                                )
                                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (update) stringResource(R.string.release_update_available) else stringResource(
                                    R.string.release_whats_new
                                ),
                                maxLines = 1,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.wand_stars_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        }
                    )
                    if (update) {
                        SuggestionChip(onClick = {}, label = {
                            Text(
                                "v${normalizeVersion(BuildConfig.VERSION_NAME)} → v${
                                    normalizeVersion(
                                        release.tagName
                                    )
                                }",
                                maxLines = 1,
                            )
                        })
                    }
                    AssistChip(
                        onClick = { open(release.htmlUrl) },
                        label = { Text(stringResource(R.string.github_label), maxLines = 1) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.github),
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        })
                }

                val body = release.body?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.release_no_notes)
                val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
                val accent = MaterialTheme.colorScheme.primary.toArgb()
                val markwon = remember(context) {
                    Markwon.builder(context).usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS)).build()
                }
                val notes = remember(markwon, body) { markwon.toMarkdown(body) }
                AndroidView(modifier = Modifier.fillMaxWidth(), factory = {
                    TextView(it).apply {
                        textSize = 14f; setLineSpacing(0f, 1.15f); setHorizontallyScrolling(false)
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                }, update = {
                    it.setTextColor(foreground); it.setLinkTextColor(accent)
                    markwon.setParsedMarkdown(it, notes)
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

@Composable
internal fun releaseDateLabel(publicationTime: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val date = remember(publicationTime, locale, zone) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zone)
            .format(publicationTime)
    }
    val relativeTime by produceState(
        initialValue = FormatUtils.formatRelativeTime(publicationTime, locale),
        publicationTime,
        locale,
    ) {
        while (true) {
            value = FormatUtils.formatRelativeTime(publicationTime, locale)
            delay(60_000)
        }
    }
    return stringResource(R.string.release_publication_date, date, relativeTime)
}
