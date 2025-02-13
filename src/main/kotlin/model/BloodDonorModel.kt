package com.api.hazrat.model

import com.api.hazrat.util.EncryptionUtil
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.codecs.pojo.annotations.BsonId

@Serializable
data class BloodDonorModel(
    @BsonId
    var userId: String? = null, // Nullable
    val name: String? = null,
    val age: String? = null,
    val gender: String? = null,
    val bloodGroup: String? = null,
    val city: String? = null,
    val district: String? = null,
    val state: String? = null,
    val available: Boolean? = false,
    val contactNumber: String? = null,
    val isContactNumberPrivate: Boolean? = false,
    val notificationEnabled: Boolean? = false,
    val notificationScope: String? = "District",
    val email: String? = null
){
    fun toDocument() : Document {
        return Document().apply {
            append("_id", userId) // Set the userId as the MongoDB `_id`
            append("name", name?.let { EncryptionUtil.encrypt(it) })
            append("age", age?.let { EncryptionUtil.encrypt(it) })
            append("gender", gender?.let { EncryptionUtil.encrypt(it) })
            append("bloodGroup", bloodGroup?.let { EncryptionUtil.encrypt(it) })
            append("city", city?.let { EncryptionUtil.encrypt(it) })
            append("district", district?.let { EncryptionUtil.encrypt(it) })
            append("state", state?.let { EncryptionUtil.encrypt(it) })
            append("available", available)
            append("contactNumber", contactNumber?.let { EncryptionUtil.encrypt(it) })
            append("isContactNumberPrivate", isContactNumberPrivate)
            append("notificationEnabled", notificationEnabled)
            append("notificationScope", notificationScope)
            append("email", email?.let { EncryptionUtil.encrypt(it) })
        }
    }


    companion object {

        fun fromDocument(document: Document) : BloodDonorModel{
            return BloodDonorModel(
                userId = document.getString("_id"), // Retrieve `_id`
                name = document.getString("name")?.let { EncryptionUtil.decrypt(it) },
                age = document.getString("age")?.let { EncryptionUtil.decrypt(it) },
                gender = document.getString("gender")?.let { EncryptionUtil.decrypt(it) },
                bloodGroup = document.getString("bloodGroup")?.let { EncryptionUtil.decrypt(it) },
                city = document.getString("city")?.let { EncryptionUtil.decrypt(it) },
                district = document.getString("district")?.let { EncryptionUtil.decrypt(it) },
                state = document.getString("state")?.let { EncryptionUtil.decrypt(it) },
                available = document.getBoolean("available"),
                contactNumber = document.getString("contactNumber")?.let { EncryptionUtil.decrypt(it) },
                isContactNumberPrivate = document.getBoolean("isContactNumberPrivate"),
                notificationEnabled = document.getBoolean("notificationEnabled"),
                notificationScope = document.getString("notificationScope"),
                email = document.getString("email")?.let { EncryptionUtil.decrypt(it) }
            )
        }
    }
}
