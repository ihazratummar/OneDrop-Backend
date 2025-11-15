package com.api.hazrat.websocket

import com.google.cloud.firestore.annotation.ServerTimestamp
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

data class WebSocketConnection(
    val connectionId: String,
    val session: WebSocketSession,
    val userId: String,
    val subscriptions: ConcurrentHashMap<String, Subscription> = ConcurrentHashMap(),
    val messageQueue : Channel<String>,
    val lastActivity : Long = System.currentTimeMillis()
)

data class Subscription(
    val type: SubscriptionType,
    val resourceId: String
){
    fun getKey(): String ="${type.name}:$resourceId"
}

enum class SubscriptionType {
    BLOOD_REQUEST,
    BLOOD_DONOR,
    NOTIFICATION,
    BLOOD_REQUEST_LIST,
    BLOOD_DONOR_LIST
}

@Serializable
data class ClientMessage(
    val action: String,
    val type: String? = null,
    val resourceId: String ? = null
)

@Serializable
data class ServerMessage(
    val type: String,
    val action: String = "update", // update, delete, insert
    val resourceId : String,
    val data : String, // JSON String to avoid nested serialization
    val timestamp: Long = System.currentTimeMillis()
)

