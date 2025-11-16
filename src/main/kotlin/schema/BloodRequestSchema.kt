package com.api.hazrat.schema

// ❗ FIXED — use coroutine versions and OperationResult
import com.api.hazrat.execptions.OperationResult
import com.api.hazrat.model.BloodRequestModel
import com.api.hazrat.util.SecretConstant.BLOOD_REQUEST_COLLECTION_NAME
import com.api.hazrat.util.SecretConstant.USER_COLLECTION_NAME
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.grpc.netty.shaded.io.netty.handler.codec.http.multipart.HttpPostRequestDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId

class BloodRequestSchema(
    database: MongoDatabase
) {

    // ❗ coroutine collections
    private val bloodRequestCollection: MongoCollection<Document> =
        database.getCollection(BLOOD_REQUEST_COLLECTION_NAME)

    private val bloodDonorCollection: MongoCollection<Document> =
        database.getCollection(USER_COLLECTION_NAME)


    suspend fun ensureIndexes() {
        bloodRequestCollection.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("userId"),
                Indexes.ascending("bloodRequestStatus")
            )
        )
    }

    suspend fun createBloodRequest(bloodRequestModel: BloodRequestModel): String =
        withContext(Dispatchers.IO) {

            val openRequests = bloodRequestCollection.find(
                Document("userId", bloodRequestModel.userId)
                    .append("bloodRequestStatus", "Open")
            ).map { BloodRequestModel.fromDocument(it) }.toList()

            if (openRequests.size >= 2) throw HttpPostRequestDecoder.TooManyFormFieldsException()

            val donationCode = (1..6)
                .map { ('A'..'Z') + ('0'..'9') }
                .flatten()
                .shuffled()
                .take(6)
                .joinToString("")

            // fixed: remove stray '+' operator
            val expiryAt = bloodRequestModel.date

            val newRequest = bloodRequestModel.copy(
                id = null,
                bloodRequestStatus = "Active",
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

            insertedId
        }


    suspend fun getAllBloodRequests(
        sortBy: String,
        filter: String? = null
    ): List<BloodRequestModel> =
        withContext(Dispatchers.IO) {

            val sortField = when (sortBy) {
                "Recent" -> Sorts.descending("dateOfCreation")
                "Date" -> Sorts.ascending("date")
                else -> Sorts.descending("dateOfCreation")
            }

            val filterDoc = when {
                filter == null || filter == "All" -> Filters.empty()
                else -> Filters.eq("bloodRequestStatus", filter)
            }

            bloodRequestCollection.find(filterDoc)
                .sort(sortField)
                .map { BloodRequestModel.fromDocument(it) }
                .toList()
        }


    suspend fun getBloodRequest(bloodRequestId: String): OperationResult<BloodRequestModel> =
        withContext(Dispatchers.IO) {
            try {
                val objectId = ObjectId(bloodRequestId)
                val doc = bloodRequestCollection.find(Filters.eq("_id", objectId)).firstOrNull()

                if (doc != null) {
                    OperationResult.Success(
                        data = BloodRequestModel.fromDocument(doc),
                        message = "Blood request found successfully",
                        httpStatus = 200
                    )
                } else {
                    OperationResult.Failure(
                        message = "Blood request not found",
                        httpStatus = 404,
                        details = "No blood request with ID: $bloodRequestId"
                    )
                }
            } catch (e: Exception) {
                OperationResult.Failure(
                    message = "Failed to fetch blood request",
                    httpStatus = 500,
                    details = e.message ?: "Unknown error"
                )
            }
        }


    suspend fun deleteBloodRequest(bloodRequestId: String): Boolean =
        withContext(Dispatchers.IO) {

            val objectId = ObjectId(bloodRequestId)
            val existing = bloodRequestCollection.find(Filters.eq("_id", objectId)).firstOrNull()
                ?: throw IllegalStateException("Blood Request Not Found")

            val result = bloodRequestCollection.deleteOne(Filters.eq("_id", objectId))

            if (result.deletedCount > 0) true
            else throw IllegalStateException("Failed to Delete Blood Request")
        }


    suspend fun claimBloodRequest(bloodRequestId: String, donorId: String): OperationResult<String> =
        withContext(Dispatchers.IO) {

            val doc = bloodRequestCollection.find(Filters.eq("_id", ObjectId(bloodRequestId)))
                .firstOrNull() ?: return@withContext OperationResult.Failure(
                message = "Blood request not found",
                httpStatus = 404
            )

            val request = BloodRequestModel.fromDocument(doc)

            if (request.expiryAt != null && request.expiryAt < System.currentTimeMillis())
                return@withContext OperationResult.Failure(
                    message = "This blood request has expired.",
                    httpStatus = 410
                )

            val update = Updates.addToSet("donorsResponded", donorId)

            val result = bloodRequestCollection.updateOne(
                Filters.eq("_id", ObjectId(bloodRequestId)),
                update
            )

            if (result.modifiedCount > 0) {

                bloodDonorCollection.updateOne(
                    Filters.eq("_id", donorId),
                    Updates.combine(
                        Updates.set("lastResponseAt", System.currentTimeMillis()),
                        Updates.inc("donorScore", 2)
                    )
                )

                OperationResult.Success(
                    data = result.modifiedCount.toString(),
                    message = "Blood Donor added to request.",
                    httpStatus = 200
                )
            } else {
                OperationResult.Failure(
                    message = "Donor already responded or update failed",
                    httpStatus = 409
                )
            }
        }


    suspend fun verifyDonation(
        bloodRequestId: String,
        donorId: String,
        code: String
    ): OperationResult<String> =
        withContext(Dispatchers.IO) {

            val doc = bloodRequestCollection.find(Filters.eq("_id", ObjectId(bloodRequestId)))
                .firstOrNull() ?: return@withContext OperationResult.Failure(
                message = "Blood request not found",
                httpStatus = 404
            )

            val request = BloodRequestModel.fromDocument(doc)

            if (request.donationCode != code)
                return@withContext OperationResult.Failure(
                    message = "Invalid donation code",
                    httpStatus = 400
                )

            if (request.verifiedDonors?.any { it.donorId == donorId } == true)
                return@withContext OperationResult.Failure(
                    message = "You are already verified",
                    httpStatus = 409
                )

            val verifyDoc = Document(
                mapOf(
                    "donorId" to donorId,
                    "verifiedAt" to System.currentTimeMillis(),
                    "verifiedByCode" to true
                )
            )

            val result = bloodRequestCollection.updateOne(
                Filters.eq("_id", ObjectId(bloodRequestId)),
                Updates.combine(
                    Updates.addToSet("verifiedDonors", verifyDoc),
                    Updates.set("lastUpdatedAt", System.currentTimeMillis())
                )
            )

            if (result.modifiedCount > 0) {

                bloodDonorCollection.updateOne(
                    Filters.eq("_id", donorId),
                    Updates.combine(
                        Updates.addToSet("bloodDonated", bloodRequestId),
                        Updates.set("lastDonationAt", System.currentTimeMillis()),
                        Updates.inc("donorScore", 10)
                    )
                )

                OperationResult.Success(
                    data = result.modifiedCount.toString(),
                    message = "Donation successfully verified.",
                    httpStatus = 200
                )
            } else {
                OperationResult.Failure(
                    message = "Failed to verify donor.",
                    httpStatus = 500
                )
            }
        }


    suspend fun submitDonationProof(
        requestId: String,
        donorId: String,
        proofUrl: String
    ): OperationResult<String> = withContext(Dispatchers.IO) {

        val doc = bloodRequestCollection.find(Filters.eq("_id", ObjectId(requestId)))
            .firstOrNull() ?: return@withContext OperationResult.Failure(
            message = "Blood request not found",
            httpStatus = 404
        )

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
                Updates.set("lastUpdatedAt", System.currentTimeMillis())
            )
        )

        if (result.modifiedCount > 0)
            OperationResult.Success(
                data = result.modifiedCount.toString(),
                message = "Donation proof uploaded.",
                httpStatus = 200
            )
        else OperationResult.Failure(
            message = "Failed to upload proof.",
            httpStatus = 500
        )
    }


    suspend fun verifyDonationClaim(
        requestId: String,
        donorId: String
    ): OperationResult<String> = withContext(Dispatchers.IO) {

        val doc = bloodRequestCollection.find(Filters.eq("_id", ObjectId(requestId)))
            .firstOrNull() ?: return@withContext OperationResult.Failure(
            message = "Blood request not found",
            httpStatus = 404
        )

        val verifyDoc = Document(
            mapOf(
                "donorId" to donorId,
                "verifiedAt" to System.currentTimeMillis(),
                "verifiedByCode" to false
            )
        )

        val result = bloodRequestCollection.updateOne(
            Filters.and(
                Filters.eq("_id", ObjectId(requestId)),
                Filters.eq("donationClaims.donorId", donorId)
            ),
            Updates.combine(
                Updates.pull("donationClaims", Document("donorId", donorId)),
                Updates.push("verifiedDonors", verifyDoc),
                Updates.set("lastUpdatedAt", System.currentTimeMillis())
            )
        )

        if (result.modifiedCount > 0) {

            bloodDonorCollection.updateOne(
                Filters.eq("_id", donorId),
                Updates.combine(
                    Updates.inc("donorScore", 10),
                    Updates.set("lastDonationAt", System.currentTimeMillis()),
                    Updates.addToSet("bloodDonated", requestId)
                )
            )

            OperationResult.Success(
                data = result.modifiedCount.toString(),
                message = "Donation claim verified.",
                httpStatus = 200
            )
        } else {
            OperationResult.Failure(
                message = "Failed to verify claim.",
                httpStatus = 500
            )
        }
    }


    suspend fun markRequestFulfilled(requestId: String): OperationResult<String> =
        withContext(Dispatchers.IO) {

            val doc = bloodRequestCollection.find(Filters.eq("_id", ObjectId(requestId)))
                .firstOrNull() ?: return@withContext OperationResult.Failure(
                message = "Blood request not found",
                httpStatus = 404
            )

            val model = BloodRequestModel.fromDocument(document = doc)

            val verifiedList = model.verifiedDonors ?: emptyList()

            if (verifiedList.isEmpty()) {
                return@withContext OperationResult.Failure(
                    message = "Cannot mark fulfilled. No verified donors found",
                    httpStatus = 400
                )
            }

            val result = bloodRequestCollection.updateOne(
                Filters.eq("_id", ObjectId(requestId)),
                Updates.combine(
                    Updates.set("bloodRequestStatus", "Fulfilled"),
                    Updates.set("fulfilledAt", System.currentTimeMillis()),
                    Updates.set("lastUpdatedAt", System.currentTimeMillis())
                )
            )

            if (result.modifiedCount > 0)
                OperationResult.Success(
                    data = requestId,
                    message = "Marked fulfilled.",
                    httpStatus = 200
                )
            else OperationResult.Failure(
                message = "Failed to update.",
                httpStatus = 500
            )
        }

    // ------------------------------
    // 🔥 Notifications (unchanged)
    // ------------------------------

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
                val list = listOf(
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

                list.forEach {
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
