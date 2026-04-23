package com.api.hazrat.service

import com.api.hazrat.cache.CacheKeys
import com.api.hazrat.cache.CacheService
import com.api.hazrat.execptions.OperationResult
import com.api.hazrat.model.BloodRequestModel
import com.api.hazrat.model.PaginationResult
import com.api.hazrat.schema.BloodRequestSchema


/**
 * @author Hazrat Ummar Shaikh
 * @date 01-02-2025 17:24
 */

class BloodRequestService(
    private val bloodRequestSchema: BloodRequestSchema,
    private val cache: CacheService
) {

    suspend fun createBloodRequest(bloodRequestModel: BloodRequestModel): String {
        val requestId = bloodRequestSchema.createBloodRequest(bloodRequestModel)
        resetCache(requestId = requestId)
        return requestId
    }

    suspend fun getAllBloodRequest(sortBy: String, filter: String? = null) : List<BloodRequestModel> {
        val key = "bloodRequests:$sortBy:${filter ?: "all"}"
        cache.get<List<BloodRequestModel>>(key)?.let {
            return it
        }
        val bloodRequests = bloodRequestSchema.getAllBloodRequests(sortBy = sortBy, filter = filter)
        cache.set(key= key, value = bloodRequests)
        return bloodRequests

    }

    suspend fun getAllBloodRequestRaw(sortBy: String, filter: String? = null, page: Int = 1, limit: Int = 20) : PaginationResult<BloodRequestModel> {
        val key = "bloodRequests:$sortBy:${filter ?: "all"}:$page:${limit}"
        cache.get<PaginationResult<BloodRequestModel>>(key)?.let {
            return it
        }

        val bloodRequests = bloodRequestSchema.getAllBloodRequestRaw(sortBy = sortBy, page = page, limit = limit)
        cache.set(key = key, value = bloodRequests)
        return bloodRequests
    }


    suspend fun getBloodRequest(bloodRequestId: String): OperationResult<BloodRequestModel> {
        val key = CacheKeys.bloodRequest(bloodRequestId)

        cache.get<OperationResult<BloodRequestModel>>(key)?.let {
            return it
        }
        val bloodRequest = bloodRequestSchema.getBloodRequest(bloodRequestId = bloodRequestId)
        cache.set(key = key, value = bloodRequest)
        return bloodRequest
    }

    suspend fun deleteBloodRequest(bloodRequestId: String) : Boolean {
        val isDeleted =  bloodRequestSchema.deleteBloodRequest(bloodRequestId = bloodRequestId)
        if (isDeleted) {
            resetCache(requestId = bloodRequestId)
        }
        return isDeleted
    }

    suspend fun claimBloodRequest(bloodRequestId: String, bloodDonorId: String): OperationResult<String> {

        val result =  bloodRequestSchema.claimBloodRequest(
            bloodRequestId = bloodRequestId,
            donorId = bloodDonorId
        )
        if (result is OperationResult.Success) {
            resetCache(requestId = bloodRequestId)
        }
        return result
    }

    suspend fun verifyDonation(bloodRequestId: String, bloodDonorId: String, code: String): OperationResult<String> {
        val result =  bloodRequestSchema.verifyDonation(
            bloodRequestId = bloodRequestId,
            donorId = bloodDonorId,
            code = code
        )
        if (result is OperationResult.Success) {
            resetCache(requestId = bloodRequestId)
        }
        return result
    }

    suspend fun submitDonationProof(requestId: String, donorId: String, proofUrl: String): OperationResult<String> {
        return bloodRequestSchema.submitDonationProof(
            requestId = requestId,
            donorId = donorId,
            proofUrl = proofUrl
        )
    }

    suspend fun verifyDonationClaim(requestId: String, donorId: String): OperationResult<String> {
        val result =  bloodRequestSchema.verifyDonationClaim(requestId = requestId, donorId = donorId)
        if (result is OperationResult.Success){
            resetCache(requestId = requestId)
        }
        return result
    }

    suspend fun markRequestFulfilled(requestId: String) : OperationResult<String> {
        val result =  bloodRequestSchema.markRequestFulfilled(
            requestId = requestId
        )
        if (result is OperationResult.Success){
            resetCache(requestId = requestId)
        }
        return result

    }

    fun resetCache(requestId: String? = null){
        requestId?.let {
            cache.delete(CacheKeys.bloodRequest(requestId = requestId))
        }
        cache.delete(CacheKeys.bloodRequests())
    }

}