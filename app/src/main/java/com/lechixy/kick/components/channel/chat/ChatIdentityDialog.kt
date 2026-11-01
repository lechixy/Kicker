package com.lechixy.kick.components.channel.chat

import androidx.compose.animation.animateContentSize

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage

import com.lechixy.kick.R
import com.lechixy.kick.data.channel.model.SubscriberBadge
import com.lechixy.kick.data.chat.model.ChatIdentity

internal val identityColors = listOf("#00F1FF", "#4CFF75", "#55FFC7", "#6F87FF", "#AAA9FF", "#BDFF28", "#E26EFF", "#E4D88F",
    "#E5FFAB", "#FEA0CF", "#FF2C56", "#FF4117", "#FF55B3", "#FFA600", "#FFAE76", "#FFFFFF")

@Composable
internal fun ChatIdentityDialog(
    initialIdentity: ChatIdentity?,
    username: String,
    subscriberBadges: List<SubscriberBadge>,
    onPreviewClick: ((ChatIdentity) -> Unit)? = null,
    isLoading: Boolean = false,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
    onDismiss: (ChatIdentity?) -> Unit
) {
    var draft by remember(initialIdentity) { mutableStateOf(initialIdentity ?: ChatIdentity()) }
    // A color selected while badges load must survive the arriving server snapshot.
    var selectedColor by remember { mutableStateOf<String?>(null) }
    val pending = draft.copy(color = selectedColor ?: draft.color)
    var dismissed by remember { mutableStateOf(false) }
    val dismiss = {
        if (!dismissed) {
            dismissed = true
            val changed = if (initialIdentity == null) selectedColor != null else (!pending.color.equals(initialIdentity.color, true) ||
                pending.badges.filter { it.active != false }.map { it.type }.toSet() !=
                initialIdentity.badges.filter { it.active != false }.map { it.type }.toSet() ||
                pending.badgesV2.filter { it.selected }.map { it.name }.toSet() !=
                initialIdentity.badgesV2.filter { it.selected }.map { it.name }.toSet())
            onDismiss(pending.takeIf { changed })
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val selected = pending.badges.count { it.active != false } + pending.badgesV2.count { it.selected }
    val preview = ChatTextParser.resolveBadges(pending, subscriberBadges, context = context).take(4)
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 640.dp),
            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.animateContentSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.chat_identity), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
//                    IconButton(onClick = dismiss) { Icon(painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24), stringResource(R.string.account_close)) }
                }
                Surface(modifier = Modifier.clickable(enabled = onPreviewClick != null,
                    onClickLabel = stringResource(R.string.identity_open_profile)) {
                    dismiss()
                    onPreviewClick?.invoke(pending)
                }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(R.string.identity_preview), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        preview.forEach { IdentityBadgeImage(it) }
                        Text(username, color = ChatTextParser.parseHexColor(pending.color, MaterialTheme.colorScheme.primary))
                    }
                }
                Text(stringResource(R.string.identity_badges), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.identity_badges_limit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                if (isLoading || loadFailed) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 64.dp), contentAlignment = Alignment.Center) {
                        if (loadFailed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.identity_load_failed))
                            androidx.compose.material3.TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_retry)) }
                        } else com.lechixy.kick.components.AppLoadingIndicator(Modifier.size(40.dp), contained = true)
                    }
                } else {
                    // V2 badges may be either global or channel-specific; retain the server's classification.
                    listOf(false, true).forEach { channelSection ->
                        Text(stringResource(if (channelSection) R.string.identity_channel_badges else R.string.identity_global_badges),
                            style = MaterialTheme.typography.bodySmall)
                        val v2 = pending.badgesV2.filter {
                            it.badgeType.equals("channel", true) == channelSection
                        }.sortedBy { it.sortOrder }
                        if (channelSection && pending.badges.isEmpty() && v2.isEmpty()) {
                            Text(stringResource(R.string.identity_no_badges), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            v2.forEach { badge ->
                                val resolved = ResolvedChatBadge("v2_${badge.name}", badge.name, imageUrl = badge.imageUrl, sortOrder = 0, level = badge.levelNumber())
                                IdentityBadgeChoice(resolved, badge.selected, selected < 4 || badge.selected) {
                                    draft = pending.copy(badgesV2 = pending.badgesV2.map { if (it.name == badge.name) it.copy(selected = !it.selected) else it })
                                }
                            }
                            if (channelSection) pending.badges.sortedBy { it.sortOrder }.forEach { badge ->
                                val resolved = ChatTextParser.resolveBadges(ChatIdentity(badges = listOf(badge.copy(active = true))), subscriberBadges, context = context).firstOrNull()
                                    ?: ResolvedChatBadge(badge.type, badge.text ?: badge.type, sortOrder = 0)
                                IdentityBadgeChoice(resolved, badge.active != false, selected < 4 || badge.active != false) {
                                    draft = pending.copy(badges = pending.badges.map { if (it.type == badge.type) it.copy(active = it.active == false) else it })
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.identity_name_color), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.identity_color_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                identityColors.chunked(8).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { hex ->
                            val chosen = hex.equals(pending.color, true)
                            Surface(Modifier.weight(1f).aspectRatio(1f).semantics { contentDescription = hex; this.selected = chosen }
                                .clickable { selectedColor = hex },
                                shape = RoundedCornerShape(8.dp), color = ChatTextParser.parseHexColor(hex, Color.White),
                                border = if (chosen) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null) {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityBadgeChoice(badge: ResolvedChatBadge, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(Modifier.semantics { this.selected = selected }.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp), border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) { IdentityBadgeImage(badge) }
    }
}

@Composable
private fun IdentityBadgeImage(badge: ResolvedChatBadge) {
    val label = badgeLabel(badge)
    when {
        badge.drawableRes != null -> Icon(painterResource(badge.drawableRes), label, Modifier.size(24.dp), tint = Color.Unspecified)
        !badge.imageUrl.isNullOrBlank() -> AsyncImage(badge.imageUrl, label, Modifier.size(24.dp))
        else -> Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** The dialog owns its request, so closing it cancels any outstanding fetch. */
@Composable
internal fun LoadingChatIdentityDialog(
    channelId: Long,
    username: String,
    subscriberBadges: List<SubscriberBadge>,
    onPreviewClick: ((ChatIdentity) -> Unit)? = null,
    onDismiss: (ChatIdentity?, ChatIdentity?) -> Unit
) {
    var identity by remember(channelId) { mutableStateOf<ChatIdentity?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(channelId, retry) {
        failed = false
        try { identity = com.lechixy.kick.data.account.AccountGraph.chat.identity(channelId) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    ChatIdentityDialog(identity, username, subscriberBadges, onPreviewClick,
        isLoading = identity == null && !failed, loadFailed = failed, onRetry = { retry++ }) {
        onDismiss(identity, it)
    }
}
