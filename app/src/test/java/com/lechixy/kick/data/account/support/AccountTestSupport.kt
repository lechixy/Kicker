package com.lechixy.kick.data.account.support

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import okhttp3.Request

import com.lechixy.kick.data.account.local.SessionStore
import com.lechixy.kick.data.account.model.AccountProfile
import com.lechixy.kick.data.account.repository.AuthRepository
import com.lechixy.kick.data.account.model.StoredWebSession

class MemorySessionStore : SessionStore {
    private val data = mutableMapOf<String, String>()
    @Synchronized override fun read(name: String) = data[name]
    @Synchronized override fun write(name: String, value: String?) { if (value == null) data.remove(name) else data[name] = value }
}

fun objectJson(value: String): JsonObject = Json.parseToJsonElement(value) as JsonObject

fun signedInAuth(transport: suspend (Request) -> JsonElement): AuthRepository {
    val store = MemorySessionStore()
    store.write("website_account", Json.encodeToString(StoredWebSession(42, "session_token=access", "session_token=access", "test-agent",
        "2020-01-01T00:00:00Z", AccountProfile(42, "tester", createdAt = "2020-01-01T00:00:00Z"))))
    return AuthRepository(store, transport)
}
