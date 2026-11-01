package com.lechixy.kick.data.browse.model

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

import com.lechixy.kick.data.common.remote.KickApi
import com.lechixy.kick.data.common.repository.KickRepository

class CategoriesFeedTest {
    private val json = Json { ignoreUnknownKeys = true }
    @Test fun categoryPageKeepsViewerCountBannerAndTagsFromPublicPayload() {
        val response = json.decodeFromString<CategoriesResponse>("""{
            "current_page":2,"last_page":568,"data":[{
                "id":8098,"category_id":1,"name":"Marvel Rivals","slug":"marvel-rivals",
                "viewers":980,"tags":["Shooter","Strategy"],"description":null,
                "banner":{"url":"https://files.kick.com/banner.webp","responsive":"image.webp 528w"}
            }]}""")
        assertEquals(2, response.currentPage)
        assertEquals(568, response.lastPage)
        val category = response.data.single()
        assertEquals(980, category.viewers)
        assertEquals("Shooter", category.tags.first())
        assertEquals("https://files.kick.com/banner.webp", category.banner?.url)
    }
    @Test fun paginationRequestsTheRequestedPublicPageAndSearchEscapesQuery() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.host == "kick.com") {
                assertEquals("/api/v1/subcategories", request.url.encodedPath)
                assertEquals("2", request.url.queryParameter("page"))
                assertEquals("32", request.url.queryParameter("limit"))
                """{"current_page":2,"last_page":2,"data":[]}"""
            } else {
                assertEquals("Games & Music + fun", request.url.queryParameter("query"))
                """{"data":{}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val repository = KickRepository(Retrofit.Builder().baseUrl("https://kick.com/").client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(KickApi::class.java))
        assertTrue(repository.getCategories(2).data.isEmpty())
        assertTrue(repository.search("Games & Music + fun").channels.isEmpty())
    }
}
