package com.api.hazrat.schema

import com.api.hazrat.model.BloodDonorModel
import com.api.hazrat.util.DiscordLogger
import com.api.hazrat.util.EncryptionUtil
import com.api.hazrat.util.SecretConstant.BLOOD_REQUEST_COLLECTION_NAME
import com.api.hazrat.util.SecretConstant.USER_COLLECTION_NAME
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.cloud.FirestoreClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class BloodDonorSchema(
    database: MongoDatabase
) {

    private var donorCollection: MongoCollection<Document>
    private var bloodRequestCollection: MongoCollection<Document> = database.getCollection(BLOOD_REQUEST_COLLECTION_NAME)

    init {
        if (!database.listCollectionNames().contains(USER_COLLECTION_NAME)) {
            database.createCollection(USER_COLLECTION_NAME)
        }
        donorCollection = database.getCollection(USER_COLLECTION_NAME)
    }

    private val options = UpdateOptions().upsert(true)

    suspend fun createOrUpdateDonor(bloodDonorModel: BloodDonorModel): String = withContext(Dispatchers.IO) {

        // First, check if donor exists by userId
        val donorById = donorCollection.find(Document("_id", bloodDonorModel.userId)).firstOrNull()

        // Check for duplicate email/contact (but exclude the current donor if updating)
        val existingDonor = donorCollection.find().toList().firstOrNull { document ->
            val storedEmail = document.getString("email")?.let { EncryptionUtil.decrypt(it) }
            val storedContact = document.getString("contactNumber")?.let { EncryptionUtil.decrypt(it) }
            val documentId = document.getString("_id")

            // Match email or contact, but NOT the same document we're updating
            ((storedEmail == bloodDonorModel.email) || (storedContact == bloodDonorModel.contactNumber))
                    && documentId != bloodDonorModel.userId
        }

        // If duplicate found (and it's not the same donor), throw error
        if (existingDonor != null) {
            throw IllegalArgumentException("A donor with same email or contact number already exists.")
        }


        return@withContext if (donorById != null) {
            // Update existing donor
            val updateDocument = bloodDonorModel.toDocument().apply {
                remove("_id")
            }

            val result = donorCollection.updateOne(
                Document("_id", bloodDonorModel.userId),
                Document("\$set", updateDocument)
            )
            if (result.modifiedCount > 0) {
                bloodDonorModel.userId!!
            } else {
                throw IllegalStateException("Failed to update donor")
            }
        } else {
            // Create new donor
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
        val donorDocument = donorCollection.find(Document("_id", userId)).firstOrNull()
            ?: throw IllegalArgumentException("No donor found with this id")

        BloodDonorModel.fromDocument(donorDocument)
    }

    suspend fun isBloodDonorExist(userId: String): Boolean = withContext(Dispatchers.IO) {
        donorCollection.find(Document("_id", userId)).firstOrNull() != null
    }

    suspend fun toggleAvailability(userId: String, key: String): Boolean = withContext(Dispatchers.IO) {
        val donorDocument = donorCollection.find(Document("_id", userId)).firstOrNull()
            ?: throw IllegalArgumentException("No Donor Found")

        val currentAvailability = donorDocument.getBoolean(key)?: false
        val updateAvailability = !currentAvailability

        val result = donorCollection.updateOne(
            Document("_id", userId),
            Document("\$set", Document(key, updateAvailability))
        )

        if (result.modifiedCount > 0) updateAvailability else throw IllegalStateException("Failed to update availability")
    }

    suspend fun deleteBloodDonor(userId: String) : Boolean = withContext(Dispatchers.IO) {
        val donorDocument = donorCollection.deleteOne(Document("_id", userId))
        if (donorDocument.deletedCount > 0) true else throw  IllegalStateException("Failed to delete donor account")
    }

    suspend fun deleteUserAccount(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {

            // Delete user data from Firestore
            val fireStore = FirestoreClient.getFirestore()
            val collection = fireStore.collection("users").document(userId)

            val isDeleted = collection.delete().isDone
            if (isDeleted){
                println("User data deleted from Firestore")
            }else{
                println("Failed to delete user data from Firestore")
            }

            // Attempt to delete Firebase user
            FirebaseAuth.getInstance().deleteUser(userId)

            // Check if user was deleted successfully from Firebase (if no exception is thrown)
            val firebaseUserDeleted = try {
                FirebaseAuth.getInstance().getUser(userId) // This will throw if the user doesn't exist
                false // User still exists, so deletion failed
            } catch (e: FirebaseAuthException) {
                DiscordLogger.log("User with ID `$userId` deleted from Firebase. ${e.localizedMessage}" )
                true // User not found, deletion was successful
            }

            // Attempt to delete the donor profile from MongoDB
            val donorDocument = donorCollection.deleteOne(Document("_id", userId))
            val donorDeleted = donorDocument.deletedCount > 0

            // Attempt to delete the blood request data from MongoDB
            val bloodRequestDocument = bloodRequestCollection.deleteMany(Document("userId", userId))
            val bloodRequestDeleted = bloodRequestDocument.deletedCount > 0 || bloodRequestDocument.deletedCount.toInt() == 0



            // Return true if all deletions were successful
            return@withContext  donorDeleted || bloodRequestDeleted || firebaseUserDeleted
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext  false // Return false if any error occurs
        }
    }


    @OptIn(ExperimentalTime::class)
    suspend fun incrementDonorScore(userId: String, inc: Int): Boolean = withContext(Dispatchers.IO){
        try {
            val result = donorCollection.updateOne(
                Filters.eq("_id", userId),
                Updates.combine(
                    Updates.inc("donorScore", inc),
                    Updates.set("updatedAt", Clock.System.now().toEpochMilliseconds())
                )
            )
            return@withContext result.modifiedCount > 0
        }catch (e: Exception){
            return@withContext  false
        }
    }
    @OptIn(ExperimentalTime::class)
    suspend fun resetBloodDonorScore(userId: String): Boolean = withContext(Dispatchers.IO){
        try {
            val result = donorCollection.updateOne(
                Filters.eq("_id", userId),
                Updates.combine(
                    Updates.set("donorScore", 0),
                    Updates.set("updatedAt", Clock.System.now().toEpochMilliseconds())
                )
            )
            return@withContext result.modifiedCount > 0
        }catch (e: Exception){
            return@withContext  false
        }
    }


    @OptIn(ExperimentalTime::class)
    suspend fun updateLastResponse(userId: String, timestamp: Long) : Boolean = withContext(Dispatchers.IO){
        try {

            val query = Filters.eq("_id", userId)
            val updates = Updates.combine(
                Updates.set("lastResponseAt", timestamp),
                Updates.set("updatedAt", Clock.System.now().toEpochMilliseconds())
            )
            val options = UpdateOptions().upsert(true)
            val result = donorCollection.updateOne(query,updates, options )
            return@withContext result.modifiedCount > 0
        }catch (e: Exception){
            return@withContext  false
        }
    }


    @OptIn(ExperimentalTime::class)
    suspend fun updateLastDonationAt(userId: String, timestamp: Long) : Boolean = withContext(Dispatchers.IO){
        try {

            val query = Filters.eq("_id", userId)
            val updates = Updates.combine(
                Updates.set("lastDonationAt", timestamp),
                Updates.set("updatedAt", Clock.System.now().toEpochMilliseconds())
            )
//            val options = UpdateOptions().upsert(true)
            val result = donorCollection.updateOne(query,updates, options )
            return@withContext result.modifiedCount > 0
        }catch (e: Exception){
            return@withContext  false
        }
    }

}