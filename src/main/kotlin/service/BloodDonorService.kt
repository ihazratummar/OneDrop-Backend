package com.api.hazrat.service

import com.api.hazrat.model.BloodDonorModel
import com.api.hazrat.schema.BloodDonorSchema

class BloodDonorService(
    private val bloodDonorSchema: BloodDonorSchema
) {

    suspend fun createOrUpdateBloodDonor(bloodDonorModel: BloodDonorModel): String {
        return bloodDonorSchema.createOrUpdateDonor(bloodDonorModel = bloodDonorModel)
    }


    suspend fun getAllBloodDonors() : List<BloodDonorModel>{
        return bloodDonorSchema.getAllDonors()
    }


    suspend fun getDonorProfile(userId: String) : BloodDonorModel {
        return bloodDonorSchema.getDonorProfile(userId = userId)
    }

    suspend fun isBloodDonorExist(userId: String) : Boolean {
        return bloodDonorSchema.isBloodDonorExist(userId = userId)
    }

    suspend fun toggleAvailability(userId: String, key: String): Boolean {
        return bloodDonorSchema.toggleAvailability(userId = userId, key = key)
    }

    suspend fun deleteBloodDonor(userId: String): Boolean {
        return bloodDonorSchema.deleteBloodDonor(userId = userId)
    }

    suspend fun deleteFirebaseUser(userId: String) : Boolean {
        return bloodDonorSchema.deleteUserAccount(userId = userId)
    }
}