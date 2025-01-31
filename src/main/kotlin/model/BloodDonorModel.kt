package com.api.hazrat.model

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
    val available: Boolean? = null,
    val contactNumber: String? = null,
    val isContactNumberPrivate: Boolean? = null,
    val notificationEnabled: Boolean? = null,
    val notificationScope: String? = null,
    val email: String? = null
){
    fun toDocument() : Document {
        return Document().apply {
            append("_id", userId) // Set the userId as the MongoDB `_id`
            append("name", name)
            append("age", age)
            append("gender", gender)
            append("bloodGroup", bloodGroup)
            append("city", city)
            append("district", district)
            append("state", state)
            append("available", available)
            append("contactNumber", contactNumber)
            append("isContactNumberPrivate", isContactNumberPrivate)
            append("notificationEnabled", notificationEnabled)
            append("notificationScope", notificationScope)
            append("email", email)
        }
    }


    companion object {

        fun fromDocument(document: Document) : BloodDonorModel{
            return BloodDonorModel(
                userId = document.getString("_id"), // Retrieve `_id`
                name = document.getString("name"),
                age = document.getString("age"),
                gender = document.getString("gender"),
                bloodGroup = document.getString("bloodGroup"),
                city = document.getString("city"),
                district = document.getString("district"),
                state = document.getString("state"),
                available = document.getBoolean("available"),
                contactNumber = document.getString("contactNumber"),
                isContactNumberPrivate = document.getBoolean("isContactNumberPrivate"),
                notificationEnabled = document.getBoolean("notificationEnabled"),
                notificationScope = document.getString("notificationScope"),
                email = document.getString("email")
            )
        }
    }
}
