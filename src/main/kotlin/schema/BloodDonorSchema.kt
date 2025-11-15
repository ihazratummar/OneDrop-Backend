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

// ❗ FIXED — use coroutine MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase

import com.mongodb.client.model.Filters
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

class BloodDonorSchema(
    database: MongoDatabase
) {

    // ❗ FIXED — now coroutine collection
    private val donorCollection: MongoCollection<Document> = database.getCollection(USER_COLLECTION_NAME)
    private val bloodRequestCollection: MongoCollection<Document> =
        database.getCollection(BLOOD_REQUEST_COLLECTION_NAME)

    private val options = UpdateOptions().upsert(true)

    suspend fun createOrUpdateDonor(bloodDonorModel: BloodDonorModel): String =
        withContext(Dispatchers.IO) {

            val donorById =
                donorCollection.find(Filters.eq("_id", bloodDonorModel.userId)).firstOrNull()

            // Check duplicates
            val existingDonor = donorCollection.find().toList().firstOrNull { document ->
                val email = document.getString("email")?.let { EncryptionUtil.decrypt(it) }
                val contact = document.getString("contactNumber")?.let { EncryptionUtil.decrypt(it) }
                val id = document.getString("_id")

                ((email == bloodDonorModel.email) || (contact == bloodDonorModel.contactNumber))
                        && id != bloodDonorModel.userId
            }

            if (existingDonor != null)
                throw IllegalArgumentException("A donor with same email or contact number already exists.")

            return@withContext if (donorById != null) {

                val updateDoc = bloodDonorModel.toDocument().apply { remove("_id") }

                val result = donorCollection.updateOne(
                    Filters.eq("_id", bloodDonorModel.userId),
                    Updates.set("", updateDoc)
                )

                if (result.modifiedCount > 0) bloodDonorModel.userId!!
                else throw IllegalStateException("Failed to update donor")

            } else {
                val doc = bloodDonorModel.toDocument()
                donorCollection.insertOne(doc)
                doc["_id"].toString()
            }
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
            // 🔥 FIRESTORE DELETE — preserved
            val firestore = FirestoreClient.getFirestore()
            firestore.collection("users").document(userId).delete()

            // 🔥 FIREBASE AUTH DELETE — preserved
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

    suspend fun getMyBloodDonationList(userId: String): OperationResult<List<BloodRequestModel>> =
        withContext(Dispatchers.IO) {

            try {
                val donor = donorCollection.find(Filters.eq("_id", userId)).firstOrNull()
                    ?: return@withContext OperationResult.Failure(
                        "Donor not found",
                        "No donor exists with ID: $userId"
                    )

                val donated = donor.getList("bloodDonated", String::class.java) ?: emptyList()
                if (donated.isEmpty())
                    return@withContext OperationResult.Success(emptyList(), "No history")

                val objIds = donated.mapNotNull {
                    try { ObjectId(it) } catch (_: Exception) { null }
                }

                val list = bloodRequestCollection.find(Filters.`in`("_id", objIds))
                    .map { BloodRequestModel.fromDocument(it) }
                    .toList()

                OperationResult.Success(list, "Success")

            } catch (e: Exception) {
                OperationResult.Failure("Failed to fetch history", e.message ?: "Unknown error")
            }
        }
}
