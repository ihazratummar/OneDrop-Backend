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

        val doc = bloodRequestModel.toDocument()
        bloodRequestCollection.insertOne(doc)

        val insertedId = doc.getObjectId("_id").toString()

        sendNewBloodRequestNotification(
            title = "Urgent Blood Request!",
            message = "${bloodRequestModel.patientBloodGroup} blood needed at ${bloodRequestModel.hospitalName}, ${bloodRequestModel.patientCity} - ${bloodRequestModel.patientDistrict}.",
            district = bloodRequestModel.patientDistrict ?: "Murshidabad",
            state = bloodRequestModel.patientState ?: "West Bengal",
            bloodRequestId = insertedId,
            userId = bloodRequestModel.userId
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

        if (exists == null) {
            throw IllegalStateException("Blood Request Not Found")
        }

        val document = bloodRequestCollection.deleteOne(Document("_id", objectId))

        if (document.deletedCount > 0) true
        else throw IllegalStateException("Failed to Delete Blood Request")
    }



    private suspend fun sendNewBloodRequestNotification(
        title: String,
        message: String,
        district: String,
        state: String,
        bloodRequestId: String,
        userId: String
    ) {
        withContext(Dispatchers.IO) {
            try {

                val districtTopic = "blood_request_district_${district.replace(" ", "_").lowercase()}"
                val stateTopic = "blood_request_state_${state.replace(" ", "_").lowercase()}"

                val districtMessage = Message.builder()
                    .putData("title", title)
                    .putData("body", message)
                    .putData("bloodRequestId", bloodRequestId)
                    .putData("notificationScope", "district")
                    .putData("userId", userId)
                    .setTopic(districtTopic)
                    .build()

                val stateMessage = Message.builder()
                    .putData("title", title)
                    .putData("body", message)
                    .putData("bloodRequestId", bloodRequestId)
                    .putData("notificationScope", "state")
                    .putData("userId", userId)
                    .setTopic(stateTopic)
                    .build()


                val response = FirebaseMessaging.getInstance().send(districtMessage)
                val stateResponse = FirebaseMessaging.getInstance().send(stateMessage)
                println("Notification sent to: $districtTopic and $stateTopic")
                println("Notification sent to: $response $stateResponse")
            } catch (e: Exception) {
                println("Error Sending notification: ${e.localizedMessage}")
            }
        }
    }
}