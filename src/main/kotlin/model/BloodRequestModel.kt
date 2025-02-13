package com.api.hazrat.model

import com.api.hazrat.util.EncryptionUtil
import kotlinx.serialization.Serializable
import org.bson.Document


@Serializable
data class BloodRequestModel(
    val id: String? = null,
    val userId: String = "",
    val patientName: String ?= "",
    val contactPersonName: String ?= "",
    val patientAge: String? = "",
    val patientGender: String? ="" ,
    val patientBloodGroup: String? = "",
    val hospitalName: String? = "",
    val patientCity: String? = "",
    val patientDistrict: String? = "",
    val patientState: String? = "",
    val date: Long? = 0,
    val bloodUnit: Int? = 0,
    val urgency: String ?= "",
    val bloodRequestStatus: String? = "",
    val dateOfCreation: Long? = 0,
    val number: String ?=""
){
    fun toDocument () : Document {
        return Document().apply {
            id?.let { append("_id", it) }
            append("userId", userId)
            append("patientName", patientName?.let { EncryptionUtil.encrypt(it) })
            append("contactPersonName", contactPersonName?.let { EncryptionUtil.encrypt(it) })
            append("patientAge", patientAge?.let { EncryptionUtil.encrypt(it) })
            append("patientGender", patientGender?.let { EncryptionUtil.encrypt(it) })
            append("patientBloodGroup", patientBloodGroup?.let { EncryptionUtil.encrypt(it) })
            append("hospitalName", hospitalName?.let { EncryptionUtil.encrypt(it) })
            append("patientCity", patientCity?.let { EncryptionUtil.encrypt(it) })
            append("patientDistrict", patientDistrict?.let { EncryptionUtil.encrypt(it) })
            append("patientState", patientState?.let { EncryptionUtil.encrypt(it) })
            append("date", date)
            append("bloodUnit", bloodUnit)
            append("urgency", urgency)
            append("bloodRequestStatus", bloodRequestStatus)
            append("dateOfCreation", dateOfCreation)
            append("number", number?.let { EncryptionUtil.encrypt(it) })
        }
    }

    companion object {

        fun fromDocument(document: Document) : BloodRequestModel {
            return BloodRequestModel(
                id = document.getObjectId("_id").toString(),
                userId = document.getString("userId"),
                patientName = document.getString("patientName")?.let { EncryptionUtil.decrypt(it) },
                contactPersonName = document.getString("contactPersonName")?.let { EncryptionUtil.decrypt(it) },
                patientAge = document.getString("patientAge")?.let { EncryptionUtil.decrypt(it) },
                patientGender = document.getString("patientGender")?.let { EncryptionUtil.decrypt(it) },
                patientBloodGroup = document.getString("patientBloodGroup")?.let { EncryptionUtil.decrypt(it) },
                hospitalName = document.getString("hospitalName")?.let { EncryptionUtil.decrypt(it) },
                patientCity = document.getString("patientCity")?.let { EncryptionUtil.decrypt(it) },
                patientDistrict = document.getString("patientDistrict")?.let { EncryptionUtil.decrypt(it) },
                patientState = document.getString("patientState")?.let { EncryptionUtil.decrypt(it) },
                date = document.getLong("date"),
                bloodUnit = document.getInteger("bloodUnit"),
                urgency = document.getString("urgency"),
                bloodRequestStatus = document.getString("bloodRequestStatus"),
                dateOfCreation = document.getLong("dateOfCreation"),
                number = document.getString("number")?.let { EncryptionUtil.decrypt(it) },
            )
        }
    }
}

