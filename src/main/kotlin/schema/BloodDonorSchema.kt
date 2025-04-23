package com.api.hazrat.schema

import com.api.hazrat.model.BloodDonorModel
import com.api.hazrat.util.EncryptionUtil
import com.api.hazrat.util.SecretConstant.BLOOD_REQUEST_COLLECTION_NAME
import com.api.hazrat.util.SecretConstant.USER_COLLECTION_NAME
import com.google.firebase.auth.FirebaseAuth
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import com.google.firebase.cloud.FirestoreClient

class BloodDonorSchema(
    database: MongoDatabase
) {

    private var userCollection: MongoCollection<Document>
    private var bloodRequestCollection: MongoCollection<Document>

    init {
        bloodRequestCollection = database.getCollection(BLOOD_REQUEST_COLLECTION_NAME)
        if (!database.listCollectionNames().contains(USER_COLLECTION_NAME)) {
            database.createCollection(USER_COLLECTION_NAME)
        }
        userCollection = database.getCollection(USER_COLLECTION_NAME)
    }

    suspend fun createOrUpdateDonor(bloodDonorModel: BloodDonorModel): String = withContext(Dispatchers.IO) {

        val existingDonor = userCollection.find().toList().firstOrNull{document ->
            val storedEmail = document.getString("email")?.let { EncryptionUtil.decrypt(it) }
            val storedContact = document.getString("contactNumber")?.let { EncryptionUtil.decrypt(it) }
            (storedEmail == bloodDonorModel.email) || (storedContact == bloodDonorModel.contactNumber)
        }



        val donorById = userCollection.find(Document("_id", bloodDonorModel.userId)).firstOrNull()

        return@withContext if (donorById != null) {
            val updateDocument = bloodDonorModel.toDocument().apply {
                remove("_id")
            }


            val result = userCollection.updateOne(
                Document("_id", donorById["_id"]),
                Document("\$set", updateDocument)
            )
            if (result.modifiedCount > 0) donorById["_id"].toString() else throw IllegalStateException("Failed to update donor")
        }else{

            if (existingDonor != null){
            throw  IllegalArgumentException("A donor with same email or contact number already exist.")
        }

            // Create new donor
            val doc = bloodDonorModel.toDocument()
            userCollection.insertOne(doc)
            doc["_id"].toString()
        }
    }

    suspend fun getAllDonors(): List<BloodDonorModel> = withContext(Dispatchers.IO) {
        userCollection.find()
            .map { BloodDonorModel.fromDocument(it) }
            .toList()
    }


    suspend fun getDonorProfile(userId: String): BloodDonorModel = withContext(Dispatchers.IO) {
        val donorDocument = userCollection.find(Document("_id", userId)).firstOrNull()
            ?: throw IllegalArgumentException("No donor found with this id")

        BloodDonorModel.fromDocument(donorDocument)
    }

    suspend fun isBloodDonorExist(userId: String): Boolean = withContext(Dispatchers.IO) {
        userCollection.find(Document("_id", userId)).firstOrNull() != null
    }

    suspend fun toggleAvailability(userId: String, key: String): Boolean = withContext(Dispatchers.IO) {
        val donorDocument = userCollection.find(Document("_id", userId)).firstOrNull()
            ?: throw IllegalArgumentException("No Donor Found")

        val currentAvailability = donorDocument.getBoolean(key)?: false
        val updateAvailability = !currentAvailability

        val result = userCollection.updateOne(
            Document("_id", userId),
            Document("\$set", Document(key, updateAvailability))
        )

        if (result.modifiedCount > 0) updateAvailability else throw IllegalStateException("Failed to update availability")
    }

    suspend fun deleteBloodDonor(userId: String) : Boolean = withContext(Dispatchers.IO) {
        val donorDocument = userCollection.deleteOne(Document("_id", userId))
        if (donorDocument.deletedCount > 0) true else throw  IllegalStateException("Failed to delete donor account")
    }

    suspend fun deleteUserAccount(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Attempt to delete the donor profile
            val donorDocument = userCollection.deleteOne(Document("_id", userId))
            donorDocument.deletedCount > 0

            val bloodRequestDocument = bloodRequestCollection.deleteMany(Document("userId", userId))
            bloodRequestDocument.deletedCount > 0

            //Delete user data from firestore
            val fireStore = FirestoreClient.getFirestore()
            val collection = fireStore.collection("users").document(userId)
            collection.delete().get()

            // Delete Firebase user only if the donor profile exists or if it doesn't exist at all
            FirebaseAuth.getInstance().deleteUser(userId)

            true // Successfully deleted both
        } catch (e: Exception) {
            e.printStackTrace()
            false // Return false if any error occurs
        }
    }
}