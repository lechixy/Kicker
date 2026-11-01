package com.lechixy.kick.data.account.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

internal fun JsonObject.string(key: String): String? =
    (get(key) as? kotlinx.serialization.json.JsonPrimitive)?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.content
