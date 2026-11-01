package com.lechixy.kick.data.chat.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RealtimeClientInfo(
    val id: String,
    val type: String = "web"
)

@Serializable
data class ProviderCapability(
    val provider: String
)

@Serializable
data class RealtimeCapabilities(
    @SerialName("accepted_providers")
    val acceptedProviders: List<ProviderCapability> = listOf(
        ProviderCapability("pusher"),
        ProviderCapability("centrifugo")
    )
)

@Serializable
data class RealtimeConnectionRequest(
    val client: RealtimeClientInfo,
    val capabilities: RealtimeCapabilities = RealtimeCapabilities()
)

@Serializable
data class ConnectionCredentials(
    val url: String
)

@Serializable
data class ConnectionItem(
    val credentials: ConnectionCredentials? = null,
    val provider: String? = null
)

@Serializable
data class RealtimeConnectionData(
    val connections: List<ConnectionItem> = emptyList(),
    val mode: String? = null
)

@Serializable
data class RealtimeConnectionResponse(
    val data: RealtimeConnectionData? = null,
    val message: String? = null
)

@Serializable
data class RealtimeAuthRequest(
    @SerialName("client_id")
    val clientId: String
)

@Serializable
data class RealtimeAuthTokenData(
    val token: String? = null
)

@Serializable
data class RealtimeAuthResponse(
    val data: RealtimeAuthTokenData? = null,
    val message: String? = null
)
