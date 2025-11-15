package com.api.hazrat.websocket

import com.api.hazrat.execptions.InvalidMessageFormatException
import com.api.hazrat.execptions.SubscriptionFailedException
import com.api.hazrat.util.DiscordLogger
import io.ktor.server.application.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages all WebSocket connections, subscriptions, and message broadcasting.
 * This class is designed for high concurrency and scalability.
 *
 * @param environment The Ktor application environment, used to access configuration.
 */
class UnifiedWebSocketManager(private val environment: ApplicationEnvironment) {

    private val webSocketConfig = environment.config.config("websocket")
    private val channelCapacity = webSocketConfig.property("channel_capacity").getString().toInt()
    private val staleTimeout = webSocketConfig.property("stale_timeout").getString().toLong()
    private val healthCheckInterval = webSocketConfig.property("health_check_interval").getString().toLong()
    private val metricsLogInterval = webSocketConfig.property("metrics_log_interval").getString().toLong()

    // A thread-safe map to store all active WebSocket connections.
    private val connections = ConcurrentHashMap<String, WebSocketConnection>()
    // A thread-safe map to index subscriptions for fast lookups.
    private val subscriptionIndexMap = ConcurrentHashMap<String, MutableSet<String>>()
    // A thread-safe counter for the number of active connections.
    private val activeConnectionsCount = AtomicInteger(0)

    // A channel to broadcast messages to all subscribed clients.
    private val broadcastMessageChannel = Channel<BroadcastMessage>(capacity = Channel.BUFFERED)

    // Atomic counters for monitoring message statistics.
    private val messagesSentCount = AtomicInteger(0)
    private val messagesDroppedCount = AtomicInteger(0)

    // A JSON serializer configured for performance.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }


    private data class BroadcastMessage(
        val subscriptionKey: String,
        val message: String
    )

    init {
        // Launch background workers for broadcasting, health checks, and metrics logging.
        launchBroadcastWorker()
        launchHealthCheckWorker()
        launchMetricsLogger()
    }


    /**
     * Handles a new WebSocket connection.
     * This method initializes the connection, and then launches coroutines to handle incoming and outgoing messages.
     *
     * @param session The WebSocket session.
     * @param userId The ID of the user associated with the connection.
     */
    suspend fun handleConnection(session: WebSocketSession, userId: String) {
        val connection = initializeConnection(session, userId)
        try {
            coroutineScope {
                // Launch a coroutine to send messages from the connection's message queue.
                launch { startMessageSender(connection) }
                // Launch a coroutine to receive and process messages from the client.
                launch { startMessageReceiver(connection) }
            }
        } catch (e: Exception) {
            DiscordLogger.log(
                DiscordLogger.LogMessage(
                    level = "ERROR",
                    message = "Connection error",
                    connectionId = connection.connectionId,
                    userId = userId,
                    error = e.message
                )
            )
        } finally {
            cleanup(connection)
        }
    }

    /**
     * Initializes a new WebSocket connection and adds it to the connection pool.
     *
     * @param session The WebSocket session.
     * @param userId The ID of the user associated with the connection.
     * @return The newly created WebSocketConnection.
     */
    private suspend fun initializeConnection(session: WebSocketSession, userId: String): WebSocketConnection {
        val connectionId = generateConnectionId()
        val connection = WebSocketConnection(
            connectionId = connectionId,
            session = session,
            userId = userId,
            messageQueue = Channel(channelCapacity)
        )

        connections[connectionId] = connection
        activeConnectionsCount.incrementAndGet()
        DiscordLogger.log(
            DiscordLogger.LogMessage(
                level = "INFO",
                message = "Connection established",
                connectionId = connectionId,
                userId = userId
            )
        )

        sendServerMessageToConnection(
            connection = connection,
            message = ServerMessage(
                type = "SYSTEM",
                action = "connected",
                resourceId = connectionId,
                data = """{"userId":"$userId", "connectionId":"$connectionId"}"""
            )
        )
        return connection
    }

    /**
     * Starts a coroutine that sends messages from the connection's message queue to the client.
     * This allows for non-blocking message sending.
     *
     * @param connection The WebSocket connection.
     */
    private suspend fun startMessageSender(connection: WebSocketConnection) {
        try {
            for (message in connection.messageQueue) {
                connection.session.send(frame = Frame.Text(message))
                messagesSentCount.incrementAndGet()
            }
        } catch (e: Exception) {
            DiscordLogger.log(
                DiscordLogger.LogMessage(
                    level = "ERROR",
                    message = "Sender error",
                    connectionId = connection.connectionId,
                    userId = connection.userId,
                    error = e.message
                )
            )
        }
    }

    /**
     * Starts a coroutine that receives and processes messages from the client.
     *
     * @param connection The WebSocket connection.
     */
    private suspend fun startMessageReceiver(connection: WebSocketConnection) {
        try {
            for (frame in connection.session.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        processClientMessage(connection = connection, messageText = text)

                        // Update last activity to prevent the connection from being marked as stale.
                        connection.lastActivity
                    }

                    is Frame.Close -> {
                        DiscordLogger.log(
                            DiscordLogger.LogMessage(
                                level = "INFO",
                                message = "Client closed connection",
                                connectionId = connection.connectionId,
                                userId = connection.userId
                            )
                        )
                        break
                    }

                    else -> {}
                }
            }
        } catch (e: Exception) {
            DiscordLogger.log(
                DiscordLogger.LogMessage(
                    level = "ERROR",
                    message = "Receiver error",
                    connectionId = connection.connectionId,
                    userId = connection.userId,
                    error = e.message
                )
            )
        }
    }

    /**
     * Queues a message to be sent to a specific connection.
     *
     * @param connection The WebSocket connection.
     * @param message The message to send.
     */
    private suspend fun sendToConnectionQueue(connection: WebSocketConnection, message: ServerMessage) {
        try {
            val json = this.json.encodeToString(message)
            connection.messageQueue.send(json)
        } catch (e: Exception) {
            DiscordLogger.log(
                DiscordLogger.LogMessage(
                    level = "ERROR",
                    message = "Failed to queue message",
                    connectionId = connection.connectionId,
                    userId = connection.userId,
                    error = e.message
                )
            )
        }
    }

    private fun generateConnectionId(): String {
        return "conn_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /**
     * Processes a message received from a client.
     *
     * @param connection The WebSocket connection.
     * @param messageText The raw text of the message.
     */
    private suspend fun processClientMessage(connection: WebSocketConnection, messageText: String) {
        try {
            val message = try {
                json.decodeFromString<ClientMessage>(messageText)
            } catch (e: Exception) {
                throw InvalidMessageFormatException("Invalid message format: ${e.message}")
            }

            when (message.action) {
                "subscribe" -> {
                    val type = message.type?.let {
                        try {
                            SubscriptionType.valueOf(it)
                        } catch (e: IllegalArgumentException) {
                            throw InvalidMessageFormatException("Invalid subscription type: $it")
                        }
                    } ?: throw InvalidMessageFormatException("Subscription type is required")

                    val resourceId = message.resourceId ?: throw InvalidMessageFormatException("Resource ID is required")

                    subscribe(connection = connection, type = type, resourceId = resourceId)
                }

                "unsubscribe" -> {
                    val type = message.type?.let {
                        try {
                            SubscriptionType.valueOf(it)
                        } catch (e: IllegalArgumentException) {
                            throw InvalidMessageFormatException("Invalid subscription type: $it")
                        }
                    } ?: throw InvalidMessageFormatException("Subscription type is required")

                    val resourceId = message.resourceId ?: throw InvalidMessageFormatException("Resource ID is required")
                    unsubscribe(connection = connection, type = type, resourceId = resourceId)
                }

                "ping" -> {
                    sendServerMessageToConnection(
                        connection = connection,
                        message = ServerMessage(
                            type = "SYSTEM",
                            action = "pong",
                            resourceId = connection.connectionId,
                            data = """{"timestamp":"${System.currentTimeMillis()}"}"""
                        )
                    )
                }

                "get_subscription" -> {
                    val subs = connection.subscriptions.values.map {
                        mapOf("type" to it.type.name, "resourceId" to it.resourceId)
                    }
                    sendServerMessageToConnection(
                        connection = connection,
                        message = ServerMessage(
                            type = "SYSTEM",
                            action = "subscriptions",
                            resourceId = connection.connectionId,
                            data = json.encodeToString(subs)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            DiscordLogger.log(
                DiscordLogger.LogMessage(
                    level = "ERROR",
                    message = "Error handling message",
                    connectionId = connection.connectionId,
                    userId = connection.userId,
                    error = e.message
                )
            )
        }
    }


    /**
     * Subscribes a connection to a resource.
     *
     * @param connection The WebSocket connection.
     * @param type The type of the resource to subscribe to.
     * @param resourceId The ID of the resource to subscribe to.
     */
    private suspend fun subscribe(connection: WebSocketConnection, type: SubscriptionType, resourceId: String) {
        try {
            val subscription = Subscription(type = type, resourceId = resourceId)
            val subscriptionKey = subscription.getKey()

            // Add the subscription to the connection's subscription set.
            connection.subscriptions[subscriptionKey] = subscription

            // Add the connection to the subscription index for fast lookups.
            subscriptionIndexMap.getOrPut(subscriptionKey) { ConcurrentHashMap.newKeySet() }
                .add(connection.connectionId)
            DiscordLogger.log(
                DiscordLogger.LogMessage(
                    level = "INFO",
                    message = "Subscribed",
                    connectionId = connection.connectionId,
                    userId = connection.userId
                )
            )

            // Send a confirmation message to the client.
            sendServerMessageToConnection(
                connection = connection,
                message = ServerMessage(
                    type = "SYSTEM",
                    action = "subscribe",
                    resourceId = resourceId,
                    data = """{"type":"${type.name}","resourceId":"$resourceId"}"""
                )
            )
        } catch (e: Exception) {
            throw SubscriptionFailedException("Subscription failed for user ${connection.userId} to $type:$resourceId: ${e.message}")
        }
    }

    /**
     * Unsubscribes a connection from a resource.
     *
     * @param connection The WebSocket connection.
     * @param type The type of the resource to unsubscribe from.
     * @param resourceId The ID of the resource to unsubscribe from.
     */
    private suspend fun unsubscribe(connection: WebSocketConnection, type: SubscriptionType, resourceId: String) {
        val subscription = Subscription(type = type, resourceId = resourceId)
        val subscriptionKey = subscription.getKey()

        connection.subscriptions.remove(subscriptionKey)
        subscriptionIndexMap[subscriptionKey]?.remove(connection.connectionId)
        DiscordLogger.log(
            DiscordLogger.LogMessage(
                level = "INFO",
                message = "Unsubscribed",
                connectionId = connection.connectionId,
                userId = connection.userId
            )
        )
    }

    /**
     * Broadcasts a message to all clients subscribed to a specific resource.
     *
     * @param type The type of the resource.
     * @param resourceId The ID of the resource.
     * @param action The action that triggered the broadcast (e.g., "update", "delete").
     * @param dataJson The JSON data to broadcast.
     */
    suspend fun broadcast(
        type: SubscriptionType,
        resourceId: String,
        action: String = "update",
        dataJson: String
    ) {
        val subscriptionKey = "${type.name}:$resourceId"
        val message = json.encodeToString(
            ServerMessage(
                type = type.name,
                action = action,
                resourceId = resourceId,
                data = dataJson,
                timestamp = System.currentTimeMillis()
            )
        )

        broadcastMessageChannel.trySend(BroadcastMessage(subscriptionKey = subscriptionKey, message = message))
    }

    /**
     * Starts a background coroutine that processes broadcast messages from the broadcast channel.
     */
    private fun launchBroadcastWorker(){
        CoroutineScope(Dispatchers.IO).launch {
            for (broadcast in broadcastMessageChannel){
                val connectionId = subscriptionIndexMap[broadcast.subscriptionKey]?: continue

                // Send the broadcast message to all subscribed connections in parallel.
                connectionId.forEach { connectionId->
                    connections[connectionId]?.let { connection ->

                        // Use a non-blocking send to the connection's message queue.
                        val sent =  connection.messageQueue.trySend(broadcast.message)
                        if (sent.isFailure){
                            messagesDroppedCount.incrementAndGet()
                        }
                    }
                }
            }
        }
    }


    /**
     * Sends a message directly to a specific connection.
     *
     * @param connection The WebSocket connection.
     * @param message The message to send.
     */
    private suspend fun sendServerMessageToConnection(connection: WebSocketConnection, message: ServerMessage) {
        try {
            val json = this.json.encodeToString(message)
            connection.messageQueue.send(json)
        } catch (e: Exception) {
            DiscordLogger.log(
                DiscordLogger.LogMessage(
                    level = "ERROR",
                    message = "Failed to queue message",
                    connectionId = connection.connectionId,
                    userId = connection.userId,
                    error = e.message
                )
            )
        }
    }


    /**
     * Starts a background coroutine that periodically checks for and removes stale connections.
     */
    private fun launchHealthCheckWorker(){
        CoroutineScope(Dispatchers.IO).launch {
            while (isActive){
                delay(healthCheckInterval)

                val now = System.currentTimeMillis()

                connections.values
                    .filter { now - it.lastActivity > staleTimeout }
                    .forEach { connection ->
                        DiscordLogger.log(
                            DiscordLogger.LogMessage(
                                level = "INFO",
                                message = "Removing stale connection",
                                connectionId = connection.connectionId,
                                userId = connection.userId
                            )
                        )
                        cleanup(connection = connection)
                    }
            }
        }
    }


    /**
     * Starts a background coroutine that periodically logs WebSocket metrics.
     */
    private fun launchMetricsLogger(){
        CoroutineScope(Dispatchers.IO).launch {
            while (isActive){
                delay(metricsLogInterval)

                DiscordLogger.log(
                    DiscordLogger.LogMessage(
                        level = "INFO",
                        message = """
                    📊 WebSocket Metrics:
                    - Active Coroutines : ${activeConnectionsCount.get()}
                    - Total Subscriptions : ${subscriptionIndexMap.values.sumOf { it.size }}
                    - Total Message Sent : ${messagesSentCount.get()}
                    - Message Dropped : ${messagesDroppedCount.get()}
                    - Unique Topics : ${subscriptionIndexMap.size}
                """.trimIndent()
                    )
                )
            }
        }
    }


    /**
     * Cleans up the resources associated with a WebSocket connection.
     *
     * @param connection The WebSocket connection to clean up.
     */
    private fun cleanup(connection: WebSocketConnection) {

        connections.remove(connection.connectionId)
        activeConnectionsCount.decrementAndGet()

        // Remove the connection from all subscription indexes.
        connection.subscriptions.values.forEach { subscription ->
            val subscriptionKey = subscription.getKey()
            subscriptionIndexMap[subscriptionKey]?.remove(connection.connectionId)

            // Clean up empty subscription indexes to save memory.
            if (subscriptionIndexMap[subscriptionKey]?.isEmpty() == true) {
                subscriptionIndexMap.remove(subscriptionKey)
            }
        }

        // Close the connection's message queue.
        connection.messageQueue.close()

        DiscordLogger.log(
            DiscordLogger.LogMessage(
                level = "INFO",
                message = "Cleaned up connection",
                connectionId = connection.connectionId,
                userId = connection.userId
            )
        )
    }

    /**
     * Gracefully shuts down the WebSocket manager.
     * This method closes all active connections and clears all data structures.
     */
    suspend fun shutdown(){
        DiscordLogger.log(
            DiscordLogger.LogMessage(
                level = "INFO",
                message = "Shutting down WebSocket manager"
            )
        )

        // Close all active connections.
        connections.values.forEach { connection ->
            try {
                sendServerMessageToConnection(
                    connection = connection,
                    message = ServerMessage(
                        type = "SYSTEM",
                        action = "shutdown",
                        resourceId = connection.connectionId,
                        data = """{"reason":"Server shutting down"}"""
                    )
                )
                connection.session.close(CloseReason(code = CloseReason.Codes.GOING_AWAY, message = "Server Shutting Down"))
            }catch (e: Exception){
                DiscordLogger.log(
                    DiscordLogger.LogMessage(
                        level = "ERROR",
                        message = "Error closing connection",
                        connectionId = connection.connectionId,
                        userId = connection.userId,
                        error = e.message
                    )
                )
            }
        }
        connections.clear()
        subscriptionIndexMap.clear()
        broadcastMessageChannel.close()
    }

    /**
     * Returns a map of current WebSocket metrics.
     *
     * @return A map of metrics.
     */
    fun getMetrics() : Map<String, Any>{
        return mapOf(
            "activeConnections" to activeConnectionsCount.get(),
            "totalSubscriptions" to subscriptionIndexMap.values.sumOf { it.size },
            "messageSent" to messagesSentCount.get(),
            "messageDropped" to messagesDroppedCount.get(),
            "uniqueTopics" to subscriptionIndexMap.size
        )
    }

}
