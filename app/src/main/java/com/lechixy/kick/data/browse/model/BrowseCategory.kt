package com.lechixy.kick.data.browse.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BrowseCategory(val id: Long = 0, val name: String, val slug: String,
    val tags: List<String> = emptyList(), val viewers: Int = 0, val banner: CategoryBanner? = null)
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class CategoryBanner(@kotlinx.serialization.json.JsonNames("src") val url: String? = null, @kotlinx.serialization.json.JsonNames("srcset") val responsive: String? = null)
@Serializable
data class CategoriesResponse(val data: List<BrowseCategory> = emptyList(),
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("last_page") val lastPage: Int = 1)

@Serializable
data class RecentCategoriesResponse(val data: List<BrowseCategory> = emptyList())
