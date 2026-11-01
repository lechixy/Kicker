package com.lechixy.kick.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.util.Locale

import com.lechixy.kick.data.browse.model.BrowseCategory
import com.lechixy.kick.util.FormatUtils

private val CategoryPosterRatio = 2f / 3f
private val CategoryPosterShape = RoundedCornerShape(12.dp)
private val CategoryScrim = Brush.verticalGradient(
    0f to Color.Transparent,
    .4f to Color.Transparent,
    .75f to Color(0xB30D0E15),
    1f to Color(0xF20D0E15)
)

/** A shared poster for category grids and search; all navigation stays with its caller. */
@Composable
fun CategoryCard(category: BrowseCategory, onClick: () -> Unit, modifier: Modifier = Modifier,
    showTag: Boolean = true, showViewers: Boolean = true, loading: Boolean = false) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val emphasized = !loading && (hovered || focused)
    val pressScale by animateFloatAsState(if (!loading && pressed) .97f else 1f,
        animationSpec = tween(120), label = "category press")
    val posterScale by animateFloatAsState(if (emphasized) 1.045f else 1f,
        animationSpec = tween(220), label = "category poster zoom")
    val lift by animateDpAsState(if (emphasized) (-3).dp else 0.dp,
        animationSpec = tween(180), label = "category lift")
    val elevation by animateDpAsState(if (emphasized) 10.dp else 2.dp,
        animationSpec = tween(180), label = "category shadow")
    val outline by animateColorAsState(if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = .65f)
        else Color.White.copy(alpha = .08f), animationSpec = tween(180), label = "category outline")
    val context = LocalContext.current
    val poster = remember(context, category.banner?.url) {
        ImageRequest.Builder(context).data(category.banner?.url).crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED).build()
    }
    Surface(onClick = onClick, enabled = !loading, interactionSource = interactions,
        shape = CategoryPosterShape, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, outline), shadowElevation = elevation,
        modifier = modifier.fillMaxWidth().graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
            translationY = lift.toPx()
        }) {
        Box(Modifier.fillMaxWidth().aspectRatio(CategoryPosterRatio).clip(CategoryPosterShape)) {
            if (loading) {
                CategoryPosterSkeleton(showTag, showViewers)
            } else {
                AsyncImage(model = poster, contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = posterScale; scaleY = posterScale },
                    contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(CategoryScrim))
                if (showViewers) CategoryPill(Modifier.align(Alignment.TopStart).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Text(FormatUtils.formatFollowersCount(category.viewers.toString(), LocalConfiguration.current.locales[0]),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = .9f))
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(category.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    if (showTag) category.tags.firstOrNull()?.let { tag ->
                        CategoryPill {
                            Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = .9f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = Color(0x73202532),
        border = BorderStroke(.5.dp, Color.White.copy(alpha = .12f))) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) { content() }
    }
}

@Composable
private fun BoxScope.CategoryPosterSkeleton(showTag: Boolean, showViewers: Boolean) {
    SkeletonBlock(Modifier.fillMaxSize(), CategoryPosterShape)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .16f)).background(CategoryScrim))
    if (showViewers) SkeletonBlock(Modifier.align(Alignment.TopStart).padding(10.dp).width(64.dp).height(26.dp),
        RoundedCornerShape(8.dp))
    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth(.85f).height(20.dp))
        if (showTag) SkeletonBlock(Modifier.width(72.dp).height(26.dp), RoundedCornerShape(8.dp))
    }
}

@Composable
fun CategoryRow(categories: List<BrowseCategory>, loading: Boolean, onCategoryClick: (String) -> Unit,
    showTag: Boolean = true) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        if (loading) items(8) { CategoryCard(BrowseCategory(name = "", slug = ""), {},
            Modifier.width(152.dp), showTag, showViewers = showTag, loading = true) }
        else items(categories, key = { it.slug }) { category ->
            CategoryCard(category, { onCategoryClick(category.slug) }, Modifier.width(152.dp), showTag, showViewers = showTag)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F14)
@Composable
private fun CategoryPosterPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryCard(BrowseCategory(name = "Marvel Rivals", slug = "marvel-rivals", tags = listOf("Shooter"),
                viewers = 110800), {}, Modifier.width(160.dp))
            CategoryCard(BrowseCategory(name = "Just Chatting", slug = "just-chatting"), {}, Modifier.width(160.dp),
                showTag = false, showViewers = false)
        }
    }
}
