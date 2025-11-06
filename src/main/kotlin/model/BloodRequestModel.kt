package com.api.hazrat.model

import com.api.hazrat.util.EncryptionUtil
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId

@Serializable
data class BloodRequestModel(
    val id: String? = null,
    val userId: String = "",

    // --- Core Patient Info ---
    val patientName: String? = "",                  // Sensitive ✅
    val contactPersonName: String? = "",            // Sensitive ✅
    val patientAge: String? = "",
    val patientGender: String? = "",
    val patientBloodGroup: String? = "",
    val hospitalName: String? = "",
    val patientCity: String? = "",
    val patientDistrict: String? = "",
    val patientState: String? = "",

    // --- Request Details ---
    val date: Long? = null,
    val bloodUnit: Int? = 0,
    val urgency: String? = "",
    val requisitionForm: String? = "",
    val bloodRequestStatus: String? = "Pending",    // Pending / Active / Fulfilled / Cancelled / Expired
    val dateOfCreation: Long? = System.currentTimeMillis(),
    val number: String? = "",                       // Sensitive ✅

    // --- Donation Verification Flow ---
    val donationCode: String? = null,               // unique code shared with donors
    val donorsResponded: List<String>? = emptyList(), // all donors who showed interest
    val verifiedDonors: List<VerifiedDonor>? = emptyList(), // verified donors (real donors)
    val donationClaims: List<DonationClaim>? = emptyList(), // optional: manual proof uploads

    // --- Status Tracking ---
    val fulfilledAt: Long? = null,
    val lastUpdatedAt: Long? = System.currentTimeMillis(),
    val expiryAt: Long? = null
) {

    fun toDocument(): Document {
        return Document().apply {
            id?.let { append("_id", ObjectId(it)) }
            append("userId", userId)

            // Encrypt only sensitive info
            append("patientName", patientName?.let { EncryptionUtil.encrypt(it) })
            append("contactPersonName", contactPersonName?.let { EncryptionUtil.encrypt(it) })
            append("number", number?.let { EncryptionUtil.encrypt(it) })

            append("patientAge", patientAge)
            append("patientGender", patientGender)
            append("patientBloodGroup", patientBloodGroup)
            append("hospitalName", hospitalName)
            append("patientCity", patientCity)
            append("patientDistrict", patientDistrict)
            append("patientState", patientState)

            append("date", date)
            append("bloodUnit", bloodUnit)
            append("urgency", urgency)
            append("requisitionForm", requisitionForm)
            append("bloodRequestStatus", bloodRequestStatus)
            append("dateOfCreation", dateOfCreation)

            append("donationCode", donationCode)
            append("donorsResponded", donorsResponded)
            append("fulfilledAt", fulfilledAt)
            append("lastUpdatedAt", lastUpdatedAt)
            append("expiryAt", expiryAt)

            append("verifiedDonors", verifiedDonors?.map {
                Document()
                    .append("donorId", it.donorId)
                    .append("verifiedAt", it.verifiedAt)
                    .append("verifiedByCode", it.verifiedByCode)
            })

            append("donationClaims", donationClaims?.map {
                Document()
                    .append("donorId", it.donorId)
                    .append("claimedAt", it.claimedAt)
                    .append("proofUrl", it.proofUrl)
                    .append("verified", it.verified)
            })
        }
    }

    companion object {
        fun fromDocument(document: Document): BloodRequestModel {
            val verifiedDonorDocs = document.getList("verifiedDonors", Document::class.java)
            val verifiedDonors = verifiedDonorDocs?.map {
                VerifiedDonor(
                    donorId = it.getString("donorId"),
                    verifiedAt = it.getLong("verifiedAt"),
                    verifiedByCode = it.getBoolean("verifiedByCode", true)
                )
            }

            val claimDocs = document.getList("donationClaims", Document::class.java)
            val claims = claimDocs?.map {
                DonationClaim(
                    donorId = it.getString("donorId"),
                    claimedAt = it.getLong("claimedAt"),
                    proofUrl = it.getString("proofUrl"),
                    verified = it.getBoolean("verified", false)
                )
            }

            return BloodRequestModel(
                id = document.getObjectId("_id")?.toString(),
                userId = document.getString("userId"),

                patientName = document.getString("patientName")?.let { EncryptionUtil.decrypt(it) },
                contactPersonName = document.getString("contactPersonName")?.let { EncryptionUtil.decrypt(it) },
                number = document.getString("number")?.let { EncryptionUtil.decrypt(it) },

                patientAge = document.getString("patientAge"),
                patientGender = document.getString("patientGender"),
                patientBloodGroup = document.getString("patientBloodGroup"),
                hospitalName = document.getString("hospitalName"),
                patientCity = document.getString("patientCity"),
                patientDistrict = document.getString("patientDistrict"),
                patientState = document.getString("patientState"),

                date = document.getLong("date"),
                bloodUnit = document.getInteger("bloodUnit"),
                urgency = document.getString("urgency"),
                requisitionForm = document.getString("requisitionForm"),
                bloodRequestStatus = document.getString("bloodRequestStatus"),
                dateOfCreation = document.getLong("dateOfCreation"),

                donationCode = document.getString("donationCode"),
                donorsResponded = document.getList("donorsResponded", String::class.java),
                verifiedDonors = verifiedDonors,
                donationClaims = claims,

                fulfilledAt = document.getLong("fulfilledAt"),
                lastUpdatedAt = document.getLong("lastUpdatedAt"),
                expiryAt = document.getLong("expiryAt"),
            )
        }
    }
}

@Serializable
data class VerifiedDonor(
    val donorId: String,
    val verifiedAt: Long = System.currentTimeMillis(),
    val verifiedByCode: Boolean = true
)

@Serializable
data class DonationClaim(
    val donorId: String,
    val claimedAt: Long = System.currentTimeMillis(),
    val proofUrl: String? = null,  // uploaded proof photo / form / receipt
    val verified: Boolean = false  // true when verified by admin or requester
)
