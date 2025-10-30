package com.api.hazrat.model

import com.api.hazrat.util.EncryptionUtil
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@Serializable
data class BloodDonorModel(
    @BsonId
    var userId: String? = null,

    val name: String? = null,
    val age: String? = null,
    val gender: String? = null,
    val bloodGroup: String? = null,
    val city: String? = null,
    val district: String? = null,
    val state: String? = null,
    val available: Boolean? = false,
    val contactNumber: String? = null,
    val notificationEnabled: Boolean? = false,
    val notificationScope: String? = "District",
    val email: String? = null,

    // --- New fields (all optional / default-safe) ---
    val donorScore: Int? = 0,
    val lastResponseAt: Long? = null,
    val lastDonationAt: Long? = null,
    val createdAt: Long? = Instant.now().toEpochMilli(),
    val updatedAt: Long? = Instant.now().toEpochMilli()
) {
    fun toDocument(): Document {
        return Document().apply {
            append("_id", userId)
            append("name", name?.let { EncryptionUtil.encrypt(it) }) // sensitive
            append("age", age) // plain
            append("gender", gender) // plain
            append("bloodGroup", bloodGroup) // plain
            append("city", city)
            append("district", district)
            append("state", state)
            append("available", available)
            append("contactNumber", contactNumber?.let { EncryptionUtil.encrypt(it) }) // sensitive
            append("notificationEnabled", notificationEnabled)
            append("notificationScope", notificationScope)
            append("email", email?.let { EncryptionUtil.encrypt(it) }) // sensitive

            append("donorScore", donorScore)
            append("lastResponseAt", lastResponseAt)
            append("lastDonationAt", lastDonationAt)
            append("createdAt", createdAt)
            append("updatedAt", updatedAt)
        }
    }

    companion object {
        fun fromDocument(document: Document): BloodDonorModel {
            return BloodDonorModel(
                userId = document.getString("_id"),
                name = document.getString("name")?.let { EncryptionUtil.decrypt(it) },
                age = document.getString("age"),
                gender = document.getString("gender"),
                bloodGroup = document.getString("bloodGroup"),
                city = document.getString("city"),
                district = document.getString("district"),
                state = document.getString("state"),
                available = document.getBoolean("available"),
                contactNumber = document.getString("contactNumber")?.let { EncryptionUtil.decrypt(it) },
                notificationEnabled = document.getBoolean("notificationEnabled"),
                notificationScope = document.getString("notificationScope"),
                email = document.getString("email")?.let { EncryptionUtil.decrypt(it) },

                donorScore = document.getInteger("donorScore", 0),
                lastResponseAt = document.getLong("lastResponseAt"),
                lastDonationAt = document.getLong("lastDonationAt"),
                createdAt = document.getLong("createdAt"),
                updatedAt = document.getLong("updatedAt")
            )
        }
    }
}
