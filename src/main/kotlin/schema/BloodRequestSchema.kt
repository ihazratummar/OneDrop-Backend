package com.api.hazrat.schema

import com.api.hazrat.model.BloodRequestModel
import com.api.hazrat.util.SecretConstant.BLOOD_REQUEST_COLLECTION_NAME
import com.api.hazrat.util.SecretConstant.USER_COLLECTION_NAME
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Indexes
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.TooManyFormFieldsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId


/**
 * @author Hazrat Ummar Shaikh
 * @date 01-02-2025 17:16
 */

class BloodRequestSchema(
    database: MongoDatabase
) {
    private var bloodRequestCollection: MongoCollection<Document>
    private var bloodDonorCollection: MongoCollection<Document>

    init {

        bloodRequestCollection = database.getCollection(BLOOD_REQUEST_COLLECTION_NAME)
        bloodDonorCollection = database.getCollection(USER_COLLECTION_NAME)
        bloodRequestCollection.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("userId"),
                Indexes.ascending("bloodRequestStatus")
            )
        )
    }

    suspend fun createBloodRequest(bloodRequestModel: BloodRequestModel): String = withContext(Dispatchers.IO) {

        val openRequestCount = bloodRequestCollection.find(
            Document("userId", bloodRequestModel.userId)
                .append("bloodRequestStatus", "Open")
        ).map { BloodRequestModel.fromDocument(it) }.toList()

        if (openRequestCount.size >= 2) {
            throw TooManyFormFieldsException()
        }

        val donationCode = (1..6)
            .map { ('A'..'Z') + ('0'..'9') }
            .flatten()
            .shuffled()
            .take(6)
            .joinToString("")

        val expiryAt =  bloodRequestModel.date?.let {
             + it
        }

        val newRequest = bloodRequestModel.copy(
            id = null,
            bloodRequestStatus = "Pending",
            donationCode = donationCode,
            donorsResponded = emptyList(),
            verifiedDonors = emptyList(),
            donationClaims = emptyList(),
            dateOfCreation = System.currentTimeMillis(),
            lastUpdatedAt = System.currentTimeMillis(),
            fulfilledAt = null,
            expiryAt = expiryAt
        )

        val doc = newRequest.toDocument()
        bloodRequestCollection.insertOne(doc)

        val insertedId = doc.getObjectId("_id").toString()

        sendNewBloodRequestNotification(
            title = "Urgent Blood Request!",
            message = "${bloodRequestModel.patientBloodGroup} blood needed at ${bloodRequestModel.hospitalName}, ${bloodRequestModel.patientCity} - ${bloodRequestModel.patientDistrict}.",
            district = bloodRequestModel.patientDistrict ?: "Murshidabad",
            state = bloodRequestModel.patientState ?: "West Bengal",
            bloodRequestId = insertedId,
            userId = bloodRequestModel.userId,
            city = bloodRequestModel.patientCity ?: "Jangipur"
        )

        return@withContext insertedId
    }

    suspend fun getAllBloodRequests(sortBy: String): List<BloodRequestModel> = withContext(Dispatchers.IO) {

        val sortField = when(sortBy){
            "Recent" -> Document("dateOfCreation", -1)
            "Date" -> Document("date", 1)
            else -> Document("dateOfCreation", -1)
        }

        bloodRequestCollection.find()
            .sort(sortField)
            .map { BloodRequestModel.fromDocument(it) }
            .toList()
    }

    suspend fun deleteBloodRequest(bloodRequestId: String): Boolean = withContext(Dispatchers.IO) {
        val objectId = ObjectId(bloodRequestId)
        val exists = bloodRequestCollection.find(Document("_id", objectId)).firstOrNull()
            ?: throw IllegalStateException("Blood Request Not Found")

        val document = bloodRequestCollection.deleteOne(Document("_id", objectId))

        if (document.deletedCount > 0) true
        else throw IllegalStateException("Failed to Delete Blood Request")
    }



    private suspend fun sendNewBloodRequestNotification(
        title: String,
        message: String,
        district: String,
        state: String,
        city: String,
        bloodRequestId: String,
        userId: String
    ) {
        withContext(Dispatchers.IO) {
            try {

                val messageList = listOf(
                    NotificationData(
                        topic = "blood_request_city_${city.replace(" ", "_").lowercase()}",
                        scope = "city"
                    ),
                    NotificationData(
                        topic = "blood_request_district_${district.replace(" ", "_").lowercase()}",
                        scope = "district"
                    ),
                    NotificationData(
                        topic = "blood_request_state_${state.replace(" ", "_").lowercase()}",
                        scope = "state"
                    )
                )

                messageList.forEach {
                    notificationBuilder(
                        title = title,
                        body = message,
                        bloodRequestId = bloodRequestId,
                        userId = userId,
                        notificationScope = it.scope,
                        notificationTopic = it.topic
                    )
                }

            } catch (e: Exception) {
                println("Error Sending notification: ${e.localizedMessage}")
            }
        }
    }

    private fun notificationBuilder(title: String, body:String, bloodRequestId: String, userId: String, notificationScope: String, notificationTopic: String){
        val message = Message.builder()
            .putData("title", title)
            .putData("body", body)
            .putData("bloodRequestId", bloodRequestId)
            .putData("notificationScope", notificationScope)
            .putData("userId", userId)
            .setTopic(notificationTopic)
            .build()

        val response = FirebaseMessaging.getInstance().send(message)
        println("Notification sent to $notificationTopic $response")
    }
}

data class NotificationData(
    val scope: String,
    val topic: String
)