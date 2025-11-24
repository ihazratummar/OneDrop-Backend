# Reusable Notification System Guide (V2)

This guide outlines the steps to implement a reusable and scalable notification system. It covers storing notifications, delivering them via WebSockets and FCM, and allowing users to manage their notification preferences.

---

## 1. Core Components & File Structure

We will add the following new files and modify some existing ones.

**New Files:**
- `src/main/kotlin/model/NotificationModel.kt`
- `src/main/kotlin/schema/NotificationSchema.kt`
- `src/main/kotlin/service/NotificationService.kt`
- `src/main/kotlin/service/FCMService.kt`
- `src/main/kotlin/route/NotificationRoutes.kt`

---

## 2. Database Model and Schema

First, define the data structure for a notification.

### A. `NotificationModel.kt`

Create `src/main/kotlin/model/NotificationModel.kt`.

```kotlin
package com.api.hazrat.model

import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId

@Serializable
enum class NotificationType {
    DONATION_CLAIMED,
    DONATION_VERIFIED,
    // Add other types here in the future
}

@Serializable
data class NotificationModel(
    val id: String? = null,
    val recipientId: String,
    val title: String,
    val body: String,
    val type: NotificationType,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val data: Map<String, String>? = null
) {
    companion object {
        fun fromDocument(doc: Document): NotificationModel {
            // Handle potential legacy data if 'data' field is missing
            val dataMap = if (doc.containsKey("data")) {
                (doc.get("data", Document::class.java))?.let { dataDoc ->
                    dataDoc.entries.associate { it.key to it.value.toString() }
                }
            } else {
                null
            }

            return NotificationModel(
                id = doc.getObjectId("_id").toString(),
                recipientId = doc.getString("recipientId"),
                title = doc.getString("title"),
                body = doc.getString("body"),
                type = NotificationType.valueOf(doc.getString("type")),
                isRead = doc.getBoolean("isRead", false),
                createdAt = doc.getLong("createdAt"),
                data = dataMap
            )
        }
    }
}
```

### B. `NotificationSchema.kt`

Create `src/main/kotlin/schema/NotificationSchema.kt` for database operations.

```kotlin
package com.api.hazrat.schema

import com.api.hazrat.model.NotificationModel
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId

const val NOTIFICATION_COLLECTION_NAME = "notifications"

class NotificationSchema(database: MongoDatabase) {

    private val notificationCollection: MongoCollection<Document> =
        database.getCollection(NOTIFICATION_COLLECTION_NAME)

    suspend fun ensureIndexes() {
        notificationCollection.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("recipientId"),
                Indexes.descending("createdAt")
            )
        )
    }

    suspend fun createNotification(notification: NotificationModel): String = withContext(Dispatchers.IO) {
        val doc = Document().apply {
            append("recipientId", notification.recipientId)
            append("title", notification.title)
            append("body", notification.body)
            append("type", notification.type.name)
            append("isRead", notification.isRead)
            append("createdAt", notification.createdAt)
            notification.data?.let { append("data", Document(it)) }
        }
        notificationCollection.insertOne(doc)
        doc.getObjectId("_id").toString()
    }

    suspend fun getNotificationsForUser(userId: String): List<NotificationModel> = withContext(Dispatchers.IO) {
        notificationCollection.find(Filters.eq("recipientId", userId))
            .sort(Sorts.descending("createdAt"))
            .limit(50)
            .map { NotificationModel.fromDocument(it) }
            .toList()
    }

    suspend fun markAsRead(notificationId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        val result = notificationCollection.updateOne(
            Filters.and(
                Filters.eq("_id", ObjectId(notificationId)),
                Filters.eq("recipientId", userId)
            ),
            Updates.set("isRead", true)
        )
        result.modifiedCount > 0
    }
}
```

---

## 3. User Notification Preferences

Allow users to control which notifications they receive.

### A. Update User Schema
Modify `BloodDonorSchema.kt` to add and retrieve user-specific notification settings.

```kotlin
// In src/main/kotlin/schema/BloodDonorSchema.kt

// ... other functions

suspend fun updateNotificationPreferences(userId: String, preferences: Map<String, Boolean>): Boolean = withContext(Dispatchers.IO) {
    val preferencesDocument = Document(preferences)
    val result = bloodDonorCollection.updateOne(
        Filters.eq("_id", ObjectId(userId)),
        Updates.set("notificationPreferences", preferencesDocument)
    )
    result.modifiedCount > 0
}

suspend fun getNotificationPreferences(userId: String): Map<String, Boolean> = withContext(Dispatchers.IO) {
    val user = bloodDonorCollection.find(Filters.eq("_id", ObjectId(userId))).firstOrNull()
    val preferencesDoc = user?.get("notificationPreferences", Document::class.java)
    preferencesDoc?.entries?.associate { it.key to it.value as Boolean } ?: emptyMap()
}
```

### B. Add Preferences API Endpoint
Modify `bloodDonorsRoutes.kt` to add an endpoint for managing preferences.

```kotlin
// In src/main/kotlin/route/bloodDonorsRoutes.kt

// ... inside the authenticate("auth-token") block

put("/me/notification-preferences") {
    val principal = call.principal<JWTPrincipal>()
    val userId = principal?.payload?.getClaim("id")?.asString() ?: return@put call.respond(HttpStatusCode.Unauthorized)

    try {
        val preferences = call.receive<Map<String, Boolean>>()
        // Optional: Validate that keys match NotificationType enum names
        val success = service.updateNotificationPreferences(userId, preferences)
        if (success) {
            call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
        } else {
            call.respond(HttpStatusCode.InternalServerError, "Failed to update preferences")
        }
    } catch (e: Exception) {
        call.respond(HttpStatusCode.BadRequest, "Invalid request body")
    }
}
```
You will also need to add the corresponding `updateNotificationPreferences` function to `BloodDonorService.kt`.

---

## 4. Service Layer & Delivery

Create services for notifications and FCM, then integrate them.

### A. `FCMService.kt` (New)
Create `src/main/kotlin/service/FCMService.kt` to centralize push notification logic.

```kotlin
package com.api.hazrat.service

import com.api.hazrat.model.NotificationModel
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FCMService {
    suspend fun sendPushNotification(notification: NotificationModel, fcmToken: String?) = withContext(Dispatchers.IO) {
        if (fcmToken.isNullOrBlank()) return@withContext

        val message = Message.builder()
            .putData("title", notification.title)
            .putData("body", notification.body)
            .putData("type", notification.type.name)
            .apply {
                notification.data?.forEach { (key, value) ->
                    putData(key, value)
                }
            }
            .setToken(fcmToken)
            .build()

        try {
            FirebaseMessaging.getInstance().send(message)
            println("FCM push notification sent to user ${notification.recipientId}")
        } catch (e: Exception) {
            println("Error sending FCM message: ${e.localizedMessage}")
        }
    }
}
```

### B. `NotificationService.kt` (Updated)
Create `src/main/kotlin/service/NotificationService.kt` and inject all necessary dependencies.

```kotlin
package com.api.hazrat.service

import com.api.hazrat.model.NotificationModel
import com.api.hazrat.model.NotificationType
import com.api.hazrat.schema.BloodDonorSchema
import com.api.hazrat.schema.NotificationSchema
import com.api.hazrat.websocket.SubscriptionType
import com.api.hazrat.websocket.UnifiedWebSocketManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NotificationService(
    private val notificationSchema: NotificationSchema,
    private val bloodDonorSchema: BloodDonorSchema, // To check preferences
    private val fcmService: FCMService,
    private val webSocketManager: UnifiedWebSocketManager
) {

    suspend fun createNotification(notification: NotificationModel) {
        // 1. Check user's notification preferences
        val preferences = bloodDonorSchema.getNotificationPreferences(notification.recipientId)
        val isEnabled = preferences.getOrDefault(notification.type.name, true) // Default to true if not set

        if (!isEnabled) {
            println("Notification of type ${notification.type.name} is disabled for user ${notification.recipientId}. Skipping.")
            return
        }

        // 2. Save notification to the database
        val savedNotification = notification.copy(id = notificationSchema.createNotification(notification))

        // 3. Send real-time notification via WebSocket
        webSocketManager.broadcast(
            type = SubscriptionType.USER_NOTIFICATIONS,
            resourceId = savedNotification.recipientId,
            action = "new_notification",
            dataJson = Json.encodeToString(savedNotification)
        )

        // 4. Send push notification via FCM
        val fcmToken = bloodDonorSchema.getFcmToken(savedNotification.recipientId) // Assumes this function exists
        fcmService.sendPushNotification(savedNotification, fcmToken)
    }

    // ... other functions like getNotificationsForUser and markNotificationAsRead
}
```
**Note:** You will need to implement `getFcmToken(userId: String): String?` in `BloodDonorSchema.kt` to retrieve the user's FCM token.

---

## 5. WebSocket Integration

Enable real-time notifications over your existing WebSocket manager.

### A. Add `SubscriptionType`
In `src/main/kotlin/websocket/WebSocketConnection.kt` (or wherever `SubscriptionType` is defined), add a new type for user-specific notifications.

```kotlin
// In the file where SubscriptionType is defined
enum class SubscriptionType {
    BLOOD_REQUEST_DETAILS,
    // ... other types
    USER_NOTIFICATIONS // Add this
}
```

### B. Client-Side Subscription
The client application, after connecting and authenticating with the WebSocket, should send a subscription message like this:

```json
{
  "action": "subscribe",
  "type": "USER_NOTIFICATIONS",
  "resourceId": "THE_LOGGED_IN_USER_ID"
}
```
The backend will then push any new notifications for that `USER_ID` to that specific client.

---

## 6. Final Integration in `Databases.kt`

Tie everything together in `src/main/kotlin/mongdb/Databases.kt`.

```kotlin
// In src/main/kotlin/mongdb/Databases.kt

// ... other imports
import com.api.hazrat.route.notificationRoutes
import com.api.hazrat.schema.NotificationSchema
import com.api.hazrat.service.FCMService
import com.api.hazrat.service.NotificationService
import com.api.hazrat.websocket.UnifiedWebSocketManager // Make sure this is passed or available

fun Application.configureDatabases(): MongoDatabase {
    val mongoDatabase = connectToMongoDB()
    
    // Assuming UnifiedWebSocketManager is a singleton or can be accessed here
    // If it's created elsewhere, you need to pass it to this function.
    val webSocketManager = UnifiedWebSocketManager(environment) 
    websocketRoute(manager = webSocketManager) // Your existing WebSocket route setup

    // Schemas
    val bloodDonorSchema = BloodDonorSchema(mongoDatabase)
    val bloodRequestSchema = BloodRequestSchema(database = mongoDatabase)
    val reportSchema = ReportSchema(database = mongoDatabase)
    val notificationSchema = NotificationSchema(database = mongoDatabase)
    
    launch {
        bloodRequestSchema.ensureIndexes()
        notificationSchema.ensureIndexes()
    }

    // Services
    val fcmService = FCMService()
    val notificationService = NotificationService(
        notificationSchema = notificationSchema,
        bloodDonorSchema = bloodDonorSchema,
        fcmService = fcmService,
        webSocketManager = webSocketManager
    )
    val bloodDonorService = BloodDonorService(bloodDonorSchema = bloodDonorSchema)
    val reportService = ReportService(reportSchema = reportSchema)
    val bloodRequestService = BloodRequestService(
        bloodRequestSchema = bloodRequestSchema,
        notificationService = notificationService // Pass the service here
    )

    // Routes
    notificationRoutes(service = notificationService)
    bloodDonorRoutes(service = bloodDonorService)
    bloodRequestRoutes(service = bloodRequestService)
    reportRoutes(reportService = reportService)
    migrationRoutes(donorCollection = mongoDatabase.getCollection<Document>(USER_COLLECTION_NAME))

    return mongoDatabase
}
```
This revised structure provides a complete, decoupled, and scalable notification system.
