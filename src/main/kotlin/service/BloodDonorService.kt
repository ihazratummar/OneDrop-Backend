package com.api.hazrat.service

import com.api.hazrat.cache.CacheKeys
import com.api.hazrat.cache.CacheService
import com.api.hazrat.execptions.OperationResult
import com.api.hazrat.model.BloodDonorModel
import com.api.hazrat.model.BloodRequestModel
import com.api.hazrat.schema.BloodDonorSchema

class BloodDonorService(
    private val bloodDonorSchema: BloodDonorSchema,
    private val cache: CacheService
) {

    suspend fun createOrUpdateBloodDonor(bloodDonorModel: BloodDonorModel): String {
        val userId = bloodDonorSchema.createOrUpdateDonor(bloodDonorModel = bloodDonorModel)
        resetCache(userId = userId)
        return userId
    }


    suspend fun getAllBloodDonors(): List<BloodDonorModel> {
        val key = CacheKeys.donors()

        // check cache
        cache.get<List<BloodDonorModel>>(key)?.let {
            return it
        }
        val donors = bloodDonorSchema.getAllDonors()
        cache.set(key, donors)
        return donors
    }


    suspend fun getDonorProfile(userId: String): BloodDonorModel {
        val key = CacheKeys.donor(userId = userId)
        cache.get<BloodDonorModel>(key)?.let {
            return it
        }
        val donor = bloodDonorSchema.getDonorProfile(userId = userId)
        cache.set(key, donor)
        return donor
    }


    suspend fun toggleAvailability(userId: String, key: String): Boolean {
        val result = bloodDonorSchema.toggleAvailability(userId = userId, key = key)
        resetCache(userId = userId)
        return result
    }

    suspend fun deleteBloodDonor(userId: String): Boolean {
        val isDeleted = bloodDonorSchema.deleteBloodDonor(userId = userId)
        if (isDeleted) {
            resetCache(userId = userId)
        }
        return isDeleted
    }

    suspend fun deleteFirebaseUser(userId: String): Boolean {

        val isDeleted = bloodDonorSchema.deleteUserAccount(userId = userId)
        if (isDeleted) {
            resetCache(userId = userId)
        }

        return isDeleted
    }

    suspend fun resetDonorScoreDonorScore(userId: String): Boolean {
        val isReset =  bloodDonorSchema.resetBloodDonorScore(userId = userId)
        if (isReset) {
            resetCache(userId = userId)
        }
        return isReset
    }

    suspend fun updateNotificationScope(userId: String, notificationScope: String): Boolean {
        val isUpdate =  bloodDonorSchema.updateNotificationScope(userId = userId, notificationScope = notificationScope)
        if (isUpdate) {
            resetCache(userId = userId)
        }
        return isUpdate
    }

    suspend fun getMyBloodDonationList(userId: String): OperationResult<List<BloodRequestModel>> {
        return bloodDonorSchema.getMyBloodDonationList(userId = userId)
    }

    fun resetCache(userId: String? = null){
        userId?.let {
            cache.delete(CacheKeys.donor(userId = userId))
        }
        cache.delete(CacheKeys.donors())
    }
}