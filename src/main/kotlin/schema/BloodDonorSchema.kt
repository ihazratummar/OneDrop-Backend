package com.api.hazrat.schema

import com.api.hazrat.execptions.OperationResult
import com.api.hazrat.model.BloodDonorModel
import com.api.hazrat.model.BloodRequestModel
import com.api.hazrat.util.DiscordLogger
import com.api.hazrat.util.EncryptionUtil
import com.api.hazrat.util.SecretConstant.BLOOD_REQUEST_COLLECTION_NAME
import com.api.hazrat.util.SecretConstant.USER_COLLECTION_NAME
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.cloud.FirestoreClient

import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class BloodDonorSchema(
    database: MongoDatabase
) {

    private val donorCollection: MongoCollection<Document> =
        database.getCollection(USER_COLLECTION_NAME)

    private val bloodRequestCollection: MongoCollection<Document> =
        database.getCollection(BLOOD_REQUEST_COLLECTION_NAME)

    private val options = UpdateOptions().upsert(true)

    @OptIn(ExperimentalTime::class)
    suspend fun createOrUpdateDonor(bloodDonorModel: BloodDonorModel): String = withContext(Dispatchers.IO) {

        // Validate required fields

        require(!bloodDonorModel.userId.isNullOrBlank()) {"User ID required"}
        require(!bloodDonorModel.email.isNullOrBlank()) {"Email is required"}
        require(!bloodDonorModel.contactNumber.isNullOrBlank()) {"Contact Number is required"}

        // Check for duplicate (email/contact) excluding current user
        val duplicateFilter = Filters.and(
            Filters.ne("_id", bloodDonorModel.userId),
            Filters.or(
                Filters.eq("email", EncryptionUtil.encrypt(bloodDonorModel.email)),
                Filters.eq("contactNumber", EncryptionUtil.encrypt(bloodDonorModel.contactNumber)),
            )
        )

        val existingDonor = donorCollection.find(duplicateFilter).firstOrNull()

        if (existingDonor != null) {
            val duplicateEmail = existingDonor.getString("email")
                ?.let { EncryptionUtil.decrypt(it) } == bloodDonorModel.email
            val duplicateContact = existingDonor.getString("contactNumber")
                ?.let { EncryptionUtil.decrypt(it) } == bloodDonorModel.contactNumber

            val field = when {
                duplicateEmail && duplicateContact -> "email and contact number"
                duplicateEmail -> "email"
                else -> "contact number"
            }

            throw IllegalArgumentException("A donor with the same $field already exists")
        }

        val existingDoc = donorCollection.find(Filters.eq("_id", bloodDonorModel.userId)).firstOrNull()
        val documentToSave = if (existingDoc != null){
            bloodDonorModel.copy(
                createdAt = existingDoc.getLong("createdAt") ?: bloodDonorModel.createdAt,
                updatedAt = Clock.System.now().toEpochMilliseconds(),
                bloodDonated = existingDoc.getList("bloodDonated", String::class.java)?: bloodDonorModel.bloodDonated,
                donorScore = existingDoc.getInteger("donorScore",0)
            ).toDocument()
        }else {
            // Insert: User new document with current timestamp
            bloodDonorModel.copy(
                createdAt = Clock.System.now().toEpochMilliseconds(),
                updatedAt = Clock.System.now().toEpochMilliseconds(),
            ).toDocument()
        }

        val result = donorCollection.replaceOne(
            Filters.eq("_id", bloodDonorModel.userId),
            documentToSave,
            ReplaceOptions().upsert(true)
        )

        if (result.matchedCount > 0){
            DiscordLogger.log(
                DiscordLogger.LogMessage(
                    level = "INFO",
                    message = "Donor update successfully : ${bloodDonorModel.userId} : ${bloodDonorModel.name}"
                )
            )
        }else if (result.upsertedId != null){
            DiscordLogger.log(
                DiscordLogger.LogMessage(
                    level = "INFO",
                    message = "New donor created: ${bloodDonorModel.userId}"
                )
            )
        }
        return@withContext bloodDonorModel.userId!!

    }

    suspend fun getAllDonors(): List<BloodDonorModel> = withContext(Dispatchers.IO) {
        donorCollection.find()
            .map { BloodDonorModel.fromDocument(it) }
            .toList()
    }

    suspend fun getDonorProfile(userId: String): BloodDonorModel = withContext(Dispatchers.IO) {
        donorCollection.find(Filters.eq("_id", userId))
            .firstOrNull()
            ?.let { BloodDonorModel.fromDocument(it) }
            ?: throw IllegalArgumentException("No donor found with this id")
    }

    suspend fun isBloodDonorExist(userId: String): Boolean = withContext(Dispatchers.IO) {
        donorCollection.find(Filters.eq("_id", userId)).firstOrNull() != null
    }

    suspend fun toggleAvailability(userId: String, key: String): Boolean =
        withContext(Dispatchers.IO) {

            val donor = donorCollection.find(Filters.eq("_id", userId)).firstOrNull()
                ?: throw IllegalArgumentException("No Donor Found")

            val current = donor.getBoolean(key) ?: false
            val updated = !current

            val result = donorCollection.updateOne(
                Filters.eq("_id", userId),
                Updates.set(key, updated)
            )

            if (result.modifiedCount > 0) updated
            else throw IllegalStateException("Failed to update availability")
        }

    suspend fun deleteBloodDonor(userId: String): Boolean = withContext(Dispatchers.IO) {
        donorCollection.deleteOne(Filters.eq("_id", userId)).deletedCount > 0
    }

    suspend fun deleteUserAccount(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val firestore = FirestoreClient.getFirestore()
            firestore.collection("users").document(userId).delete()

            FirebaseAuth.getInstance().deleteUser(userId)

            val firebaseUserDeleted = try {
                FirebaseAuth.getInstance().getUser(userId)
                false
            } catch (e: FirebaseAuthException) {
                DiscordLogger.log(
                    DiscordLogger.LogMessage(
                        level = "INFO",
                        message = "User $userId deleted from Firebase: ${e.localizedMessage}"
                    )
                )
                true
            }

            val donorDeleted =
                donorCollection.deleteOne(Filters.eq("_id", userId)).deletedCount > 0

            val bloodRequestDeleted =
                bloodRequestCollection.deleteMany(Filters.eq("userId", userId)).deletedCount >= 0

            donorDeleted || bloodRequestDeleted || firebaseUserDeleted

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun resetBloodDonorScore(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            donorCollection.updateOne(
                Filters.eq("_id", userId),
                Updates.combine(
                    Updates.set("donorScore", 0),
                    Updates.set("updatedAt", Clock.System.now().toEpochMilliseconds())
                )
            ).modifiedCount > 0
        } catch (_: Exception) {
            false
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun updateNotificationScope(userId: String, notificationScope: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                donorCollection.updateOne(
                    Filters.eq("_id", userId),
                    Updates.combine(
                        Updates.set("notificationScope", notificationScope),
                        Updates.set("updatedAt", Clock.System.now().toEpochMilliseconds())
                    )
                ).modifiedCount > 0
            } catch (e: Exception) {
                DiscordLogger.log(
                    DiscordLogger.LogMessage(
                        level = "ERROR",
                        message = "Failed to update scope for $userId: ${e.localizedMessage}"
                    )
                )
                false
            }
        }

    // ---------------------------------------------------------------------
    // 🔥 UPDATED: OperationResult IMPLEMENTATION WITH httpStatus
    // ---------------------------------------------------------------------

    suspend fun getMyBloodDonationList(userId: String): OperationResult<List<BloodRequestModel>> =
        withContext(Dispatchers.IO) {

            try {
                val donor = donorCollection.find(Filters.eq("_id", userId)).firstOrNull()
                    ?: return@withContext OperationResult.Failure(
                        message = "Donor not found",
                        httpStatus = 404
                    )

                val donated = donor.getList("bloodDonated", String::class.java) ?: emptyList()

                if (donated.isEmpty())
                    return@withContext OperationResult.Success(
                        data = emptyList(),
                        message = "No history found",
                        httpStatus = 200
                    )

                val objIds = donated.mapNotNull {
                    try {
                        ObjectId(it)
                    } catch (_: Exception) {
                        null
                    }
                }

                val list = bloodRequestCollection.find(Filters.`in`("_id", objIds))
                    .map { BloodRequestModel.fromDocument(it) }
                    .toList()

                OperationResult.Success(
                    data = list,
                    message = "Success",
                    httpStatus = 200
                )

            } catch (e: Exception) {
                OperationResult.Failure(
                    message = "Failed to fetch donation history",
                    httpStatus = 500,
                    details = e.message ?: "Unknown error"
                )
            }
        }
}
