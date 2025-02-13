package com.api.hazrat.schema

import com.api.hazrat.model.BloodDonorModel
import com.api.hazrat.util.SecretConstant.USER_COLLECTION_NAME
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document

class BloodDonorSchema(
    database: MongoDatabase
) {

    private var userCollection: MongoCollection<Document>

    init {
        if (!database.listCollectionNames().contains(USER_COLLECTION_NAME)) {
            database.createCollection(USER_COLLECTION_NAME)
        }
        userCollection = database.getCollection(USER_COLLECTION_NAME)
    }

    suspend fun createOrUpdateDonor(bloodDonorModel: BloodDonorModel): String = withContext(Dispatchers.IO) {
        val existingDonor = userCollection.find(Document("_id", bloodDonorModel.userId)).firstOrNull()

        return@withContext if (existingDonor != null) {
            val updateDocument = bloodDonorModel.toDocument().apply {
                remove("_id")
            }


            val result = userCollection.updateOne(
                Document("_id", existingDonor["_id"]),
                Document("\$set", updateDocument)
            )
            if (result.modifiedCount > 0) existingDonor["_id"].toString() else throw IllegalStateException("Failed to update donor")
        }else{
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

}