package com.lechixy.kick.components.channel.chat

import com.lechixy.kick.components.AppLoadingIndicator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import kotlinx.coroutines.launch

import com.lechixy.kick.R
import com.lechixy.kick.data.chat.model.EmoteCategory
import com.lechixy.kick.data.chat.model.EmoteCategoryKind
import com.lechixy.kick.data.chat.model.EmoteDto
import com.lechixy.kick.data.chat.model.EmotePickerUiState
import com.lechixy.kick.data.chat.model.filterEmotes

@Composable
fun EmotePickerSheet(
    uiState: EmotePickerUiState,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (Int) -> Unit,
    onEmoteSelected: (EmoteDto) -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    canUseSubscriberEmotes: Boolean = true
) {
    val backProgress by com.lechixy.kick.components.rememberPredictiveBackProgress(onBack = onCloseClick)
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var tabTarget by remember { mutableStateOf<Int?>(null) }
    val dragged by gridState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) tabTarget = null }
    val matches = remember(uiState.categories, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) emptyList() else filterEmotes(uiState.categories, uiState.searchQuery)
    }
    val starts = remember(uiState.categories) {
        var index = 0
        uiState.categories.map { category -> index.also { index += 1 + category.emotes.size.coerceAtLeast(1) } }
    }
    val active by remember(starts, gridState) {
        derivedStateOf {
            starts.indexOfLast { it <= gridState.firstVisibleItemIndex }.coerceAtLeast(0)
        }
    }
    Surface(modifier.fillMaxWidth().heightIn(max = 300.dp).graphicsLayer {
        scaleX = 1f - .06f * backProgress; scaleY = scaleX
        translationY = size.height * .25f * backProgress
        alpha = 1f - .3f * backProgress
    }, shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.search_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null,
                            Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(uiState.searchQuery, onSearchQueryChange, singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner ->
                                Box {
                                    if (uiState.searchQuery.isEmpty()) Text(stringResource(R.string.emote_search),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    inner()
                                }
                            })
                    }
                }
                IconButton(onClick = onCloseClick) {
                    Icon(painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24), stringResource(R.string.account_close))
                }
            }
            LazyRow {
                itemsIndexed(uiState.categories, key = { _, category -> category.id }) { index, category ->
                    EmoteCategoryTabItem(category, index == (tabTarget ?: active), onClick = {
                        tabTarget = index
                        onSearchQueryChange("")
                        onCategorySelected(index)
                        scope.launch { gridState.animateScrollToItem(starts[index]) }
                    })
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.fillMaxWidth().weight(1f).heightIn(min = 64.dp), contentAlignment = Alignment.Center) {
                when {
                    uiState.isLoading -> AppLoadingIndicator(Modifier.size(40.dp), contained = true)
                    uiState.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.emote_load_failed))
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_retry)) }
                    }
                    uiState.searchQuery.isNotBlank() && matches.isEmpty() -> Text(stringResource(R.string.emote_no_results))
                    else -> LazyVerticalGrid(columns = GridCells.Adaptive(40.dp), state = gridState,
                        contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (uiState.searchQuery.isNotBlank()) {
                            items(matches, key = { "search_${it.id}" }, contentType = { "emote" }) { emote ->
                                EmoteItem(emote, { onEmoteSelected(emote) }, enabled = !emote.subscribersOnly || canUseSubscriberEmotes)
                            }
                        } else uiState.categories.forEach { category ->
                            item(key = "header_${category.id}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                                Text(categoryTitle(category), Modifier.padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (category.emotes.isEmpty()) {
                                item(key = "empty_${category.id}", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                                    Text(stringResource(R.string.emote_empty), Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            items(category.emotes, key = { "${category.id}_emote_${it.id}" }, contentType = { "emote" }) { emote ->
                                EmoteItem(emote, { onEmoteSelected(emote) }, enabled = !emote.subscribersOnly || canUseSubscriberEmotes)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryTitle(category: EmoteCategory): String = when (category.kind) {
    EmoteCategoryKind.FREQUENT -> stringResource(R.string.emote_frequent)
    EmoteCategoryKind.GLOBAL -> stringResource(R.string.emote_global)
    EmoteCategoryKind.EMOJIS -> stringResource(R.string.emote_emojis)
    EmoteCategoryKind.COLLECTIBLES -> stringResource(R.string.emote_collectibles)
    EmoteCategoryKind.CHANNEL -> category.title
}

@Composable
fun EmoteCategoryTabItem(category: EmoteCategory, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = categoryTitle(category)
    val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.width(52.dp).semantics { selected = isSelected }.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
            when {
                category.kind == EmoteCategoryKind.CHANNEL && category.profilePicUrl != null ->
                    AsyncImage(category.profilePicUrl, label, Modifier.size(24.dp).clip(CircleShape))
                category.kind == EmoteCategoryKind.EMOJIS && category.emotes.isNotEmpty() ->
                    AsyncImage(category.emotes.firstOrNull { it.name == "emojiAstonished" }?.imageUrl ?: category.emotes.first().imageUrl,
                        label, Modifier.size(24.dp))
                else -> Icon(painterResource(when (category.kind) {
                    EmoteCategoryKind.FREQUENT -> R.drawable.history_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                    EmoteCategoryKind.GLOBAL -> R.drawable.public_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                    EmoteCategoryKind.COLLECTIBLES -> R.drawable.cards_star_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                    else -> R.drawable.mood_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                }), label, Modifier.size(24.dp), tint = tint)
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest))
    }
}

@Composable
fun EmoteItem(emote: EmoteDto, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val context = LocalContext.current
    val request = remember(context, emote.id) {
        ImageRequest.Builder(context).data(emote.imageUrl).size(96)
            .memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED).build()
    }
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).clickable(enabled = enabled,
        onClickLabel = emote.name, onClick = onClick), contentAlignment = Alignment.Center) {
        AsyncImage(request, emote.name, Modifier.size(36.dp), contentScale = ContentScale.Fit, alpha = if (enabled) 1f else 0.35f)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 360)
@Composable
private fun EmotePickerPreview() {
    MaterialTheme(colorScheme = darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF53FC18))) {
        EmotePickerSheet(EmotePickerUiState(categories = listOf(
            EmoteCategory("frequently_used", "", EmoteCategoryKind.FREQUENT, emptyList()),
            EmoteCategory("channel", "RRaenee", EmoteCategoryKind.CHANNEL, listOf(EmoteDto(2930809, name = "rraeneearti"))),
            EmoteCategory("global", "Global", EmoteCategoryKind.GLOBAL, listOf(EmoteDto(3753119, name = "asmonSmash"))),
            EmoteCategory("emoji", "Emojis", EmoteCategoryKind.EMOJIS, listOf(EmoteDto(1730752, name = "emojiAngel"))),
            EmoteCategory("collectibles", "Collectibles", EmoteCategoryKind.COLLECTIBLES, emptyList())
        )), {}, {}, {}, {})
    }
}
