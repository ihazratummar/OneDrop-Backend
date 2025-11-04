package com.api.hazrat.schema

import com.api.hazrat.execptions.OperationResult
import com.api.hazrat.model.BloodRequestModel
import com.api.hazrat.util.SecretConstant.BLOOD_REQUEST_COLLECTION_NAME
import com.api.hazrat.util.SecretConstant.USER_COLLECTION_NAME
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Updates
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

        val expiryAt = bloodRequestModel.date?.let {
            +it
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

        val sortField = when (sortBy) {
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

    suspend fun claimBloodRequest(bloodRequestId: String, bloodDonorId: String): OperationResult =
        withContext(Dispatchers.IO) {
            val requestDoc = bloodRequestCollection.find(
                Filters.eq("_id", ObjectId(bloodRequestId))
            ).firstOrNull() ?: return@withContext OperationResult.Failure("Blood request not found")

            val request = BloodRequestModel.fromDocument(requestDoc)

            if (request.expiryAt != null && request.expiryAt < System.currentTimeMillis()) {
                return@withContext OperationResult.Failure("This blood request has been expired.")
            }

            //add donor only if not already added
            val update = Updates.addToSet("donorsResponded", bloodDonorId)
            val result = bloodRequestCollection.updateOne(
                Filters.eq("_id", ObjectId(bloodRequestId)),
                update
            )

            if (result.modifiedCount > 0) {
                bloodDonorCollection.updateOne(
                    Filters.eq("_id", bloodDonorId),
                    Updates.combine(
                        Updates.set("lastResponseAt", System.currentTimeMillis()),
                        Updates.inc("donorScore", 2),
                    )

                )

                OperationResult.Success("Blood Donor added to blood request")
            } else {
                OperationResult.Failure("Donor Already responded to failed to update")
            }
        }

    suspend fun verifyDonation(bloodRequestId: String, bloodDonorId: String, code: String): OperationResult =
        withContext(
            Dispatchers.IO
        ) {
            val requestDoc = bloodRequestCollection.find(Filters.eq("_id", ObjectId(bloodRequestId))).firstOrNull()
                ?: return@withContext OperationResult.Failure("Blood request not found")

            val request = BloodRequestModel.fromDocument(requestDoc)


            if (request.donationCode != code) {
                return@withContext OperationResult.Failure("Invalid donation code")
            }

            if (request.verifiedDonors?.any { it.donorId == bloodDonorId } == true) {
                return@withContext OperationResult.Failure("You are already verified")
            }

            val verifyDonor = Document(
                mapOf(
                    "donorId" to bloodDonorId,
                    "verifiedAt" to System.currentTimeMillis(),
                    "verifiedByCode" to true
                )
            )

            val updates = Updates.combine(
                Updates.addToSet("verifiedDonors", verifyDonor),
                Updates.set("lastUpdatedAt", System.currentTimeMillis())
            )

            val result = bloodRequestCollection.updateOne(
                Filters.eq("_id", ObjectId(bloodRequestId)),
                updates
            )

            if (result.modifiedCount > 0) {
                bloodDonorCollection.updateOne(
                    Filters.eq("_id", bloodDonorId),
                    Updates.combine(
                        Updates.addToSet("bloodDonated", bloodRequestId),
                        Updates.set("lastDonationAt", System.currentTimeMillis()),
                        Updates.inc("donorScore", 10),
                    )
                )


                return@withContext OperationResult.Success("Donation successfully verified.")
            } else {
                return@withContext OperationResult.Failure("Donor already added to verify list or failed to add.")
            }
        }

    suspend fun submitDonationProof(requestId: String, donorId: String, proofUrl: String): OperationResult =
        withContext(
            Dispatchers.IO
        ) {
            val requestDoc = bloodRequestCollection.find(Filters.eq("_id", ObjectId(requestId))).firstOrNull()
                ?: return@withContext OperationResult.Failure("Blood request not found")

            val claim = Document(
                mapOf(
                    "donorId" to donorId,
                    "proofUrl" to proofUrl,
                    "claimedAt" to System.currentTimeMillis(),
                    "verified" to false
                )
            )
            val result = bloodRequestCollection.updateOne(
                Filters.eq("_id", ObjectId(requestId)),
                Updates.combine(
                    Updates.addToSet("donationClaims", claim),
                    Updates.set("lastUpdatedAt", System.currentTimeMillis()),
                )
            )

            if (result.modifiedCount > 0) {
                return@withContext OperationResult.Success("Donation proof uploaded successfully.")
            } else {
                return@withContext OperationResult.Failure("Failed to upload proof")
            }
        }

    suspend fun verifyDonationClaim(requestId: String, donorId: String): OperationResult = withContext(Dispatchers.IO) {
        val requestDoc = bloodRequestCollection.find(Filters.eq("_id", ObjectId(requestId))).firstOrNull()
            ?: return@withContext OperationResult.Failure("Blood request not found")


        val verifyDonor = Document(mapOf(
            "donorId" to donorId,
            "verifiedAt" to System.currentTimeMillis(),
            "verifiedByCode" to false
        ))

        val result = bloodRequestCollection.updateOne(
            Filters.and(
                Filters.eq("_id", ObjectId(requestId)),
                Filters.eq("donationClaims.donorId", donorId)
            ),
            Updates.combine(
                Updates.pull("donationClaims", Document("donorId", donorId)),
                Updates.push("verifiedDonors", verifyDonor),
                Updates.set("lastUpdatedAt", System.currentTimeMillis())
            )
        )

        if (result.modifiedCount > 0) {
            // ✅ Update donor record
            bloodDonorCollection.updateOne(
                Filters.eq("_id", donorId),
                Updates.combine(
                    Updates.inc("donorScore", 10),
                    Updates.set("lastDonationAt", System.currentTimeMillis()),
                    Updates.addToSet("bloodDonated", requestId)
                )
            )
            return@withContext OperationResult.Success("Donation claim verified and donor updated.")
        } else {
            return@withContext OperationResult.Failure("Failed to verify claim.")
        }
    }


    suspend fun markRequestFulfilled(requestId: String): OperationResult = withContext(Dispatchers.IO) {
        val requestDoc = bloodRequestCollection.find(Filters.eq("_id", ObjectId(requestId))).firstOrNull()
            ?: return@withContext OperationResult.Failure("Blood request not found")

        val request = BloodRequestModel.fromDocument(requestDoc)
        if (request.bloodRequestStatus == "Fulfilled") {
            return@withContext OperationResult.Failure("Request already marked fulfilled")
        }
        val result = bloodRequestCollection.updateOne(
            Filters.eq("_id", ObjectId(requestId)),
            Updates.combine(
                Updates.set("bloodRequestStatus", "Fulfilled"),
                Updates.set("fulfilledAt", System.currentTimeMillis()),
                Updates.set("lastUpdatedAt", System.currentTimeMillis()),
            )
        )

        if (result.modifiedCount > 0) {
            return@withContext OperationResult.Success("Blood request marked as fulfilled.")
        } else {
            return@withContext OperationResult.Failure("Failed to mark as fulfilled.")
        }
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

    private fun notificationBuilder(
        title: String,
        body: String,
        bloodRequestId: String,
        userId: String,
        notificationScope: String,
        notificationTopic: String
    ) {
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