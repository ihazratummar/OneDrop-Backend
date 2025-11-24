package com.api.hazrat.mongdb

import com.api.hazrat.model.BloodRequestModel
import com.api.hazrat.util.DiscordLogger
import com.api.hazrat.util.AppSecret.BLOOD_REQUEST_COLLECTION_NAME
import com.api.hazrat.websocket.SubscriptionType
import com.api.hazrat.websocket.UnifiedWebSocketManager
import com.mongodb.client.model.changestream.FullDocument
import com.mongodb.client.model.changestream.OperationType
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bson.Document
import java.lang.Exception

class MongoChangeStreamManager(
    private val database: MongoDatabase,
    private val webSocketManager: UnifiedWebSocketManager
) {

    private val supervisorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Start watching all collections
     */

    fun startWatching() {
        DiscordLogger.log(DiscordLogger.LogMessage(level = "INFO", message = "Starting MongoDB Change Streams"))
        watchBloodRequests()

        DiscordLogger.log(DiscordLogger.LogMessage(level = "INFO", message = "All Change Streams started"))
    }


    /***
     * Watch blood requests collection
     */

    private fun watchBloodRequests() {
        supervisorScope.launch {
            try {
                val collection = database.getCollection<Document>(BLOOD_REQUEST_COLLECTION_NAME)

                collection.watch()
                    .fullDocument(FullDocument.UPDATE_LOOKUP)
                    .collect { changeEvent ->
                        try {
                            when (changeEvent.operationType) {
                                OperationType.INSERT -> {
                                    val document = changeEvent.fullDocument ?: return@collect
                                    val model = BloodRequestModel.fromDocument(document = document)
                                    val id = document.getObjectId("_id")?.toString() ?: return@collect

                                    DiscordLogger.log(
                                        DiscordLogger.LogMessage(
                                            level = "INFO",
                                            message = "New blood request: $id"
                                        )
                                    )

                                    // Broadcast to specific request subscribers
                                    webSocketManager.broadcast(
                                        type = SubscriptionType.BLOOD_REQUEST,
                                        resourceId = id,
                                        action = "insert",
                                        dataJson = json.encodeToString(model)
                                    )

                                    // Broadcast to list subscribers (Blood Request Screen)
                                    webSocketManager.broadcast(
                                        type = SubscriptionType.BLOOD_REQUEST_LIST,
                                        resourceId = "ALL",
                                        action = "insert",
                                        dataJson = json.encodeToString(model)
                                    )
                                }

                                OperationType.UPDATE, OperationType.REPLACE -> {
                                    val document = changeEvent.fullDocument ?: return@collect
                                    val model = BloodRequestModel.fromDocument(document)
                                    val id = document.getObjectId("_id")?.toString() ?: return@collect

                                    DiscordLogger.log(
                                        DiscordLogger.LogMessage(
                                            level = "INFO",
                                            message = "Updated blood request: $id"
                                        )
                                    )

                                    // Broadcast to specific request subscribers
                                    webSocketManager.broadcast(
                                        type = SubscriptionType.BLOOD_REQUEST,
                                        resourceId = id,
                                        action = "update",
                                        dataJson = json.encodeToString(model)
                                    )

                                    // ALWAYS broadcast updates to list
                                    // Any field change should update the list view
                                    webSocketManager.broadcast(
                                        type = SubscriptionType.BLOOD_REQUEST_LIST,
                                        resourceId = "ALL",
                                        action = "update",
                                        dataJson = json.encodeToString(model)
                                    )
                                }

                                OperationType.DELETE -> {
                                    val id = changeEvent.documentKey?.get("_id")?.asObjectId()?.value?.toString()
                                        ?: return@collect

                                    DiscordLogger.log(
                                        DiscordLogger.LogMessage(
                                            level = "INFO",
                                            message = "Deleted blood request: $id"
                                        )
                                    )

                                    webSocketManager.broadcast(
                                        type = SubscriptionType.BLOOD_REQUEST,
                                        resourceId = id,
                                        action = "delete",
                                        dataJson = """{"id":"$id","deleted":true}"""
                                    )

                                    webSocketManager.broadcast(
                                        type = SubscriptionType.BLOOD_REQUEST_LIST,
                                        resourceId = "ALL",
                                        action = "delete",
                                        dataJson = """{"id":"$id","deleted":true}"""
                                    )
                                }

                                else -> {
                                    // Handle other operations if needed
                                }
                            }
                        } catch (e: Exception) {
                            DiscordLogger.log(
                                DiscordLogger.LogMessage(
                                    level = "ERROR",
                                    message = "Error processing blood request change: ${e.message}",
                                    error = e.stackTraceToString()
                                )
                            )
                        }
                    }
            } catch (e: Exception) {
                DiscordLogger.log(
                    DiscordLogger.LogMessage(
                        level = "ERROR",
                        message = "Error in blood request change stream: ${e.message}",
                        error = e.stackTraceToString()
                    )
                )

                // Retry after delay
                delay(5000)
                watchBloodRequests()
            }
        }
    }

    /**
     *  Stop all change streams
     */

    fun stop(){
        DiscordLogger.log(DiscordLogger.LogMessage(level = "INFO", message = "Stopping MongoDB Change Streams..."))
        supervisorScope.cancel()
        DiscordLogger.log(DiscordLogger.LogMessage(level = "INFO", message = "Change Streams stopped"))
    }

}