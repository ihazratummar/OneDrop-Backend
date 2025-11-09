package com.api.hazrat.service

import com.api.hazrat.execptions.OperationResult
import com.api.hazrat.model.BloodRequestFilters
import com.api.hazrat.model.BloodRequestModel
import com.api.hazrat.schema.BloodRequestSchema


/**
 * @author Hazrat Ummar Shaikh
 * @date 01-02-2025 17:24
 */

class BloodRequestService(
    private val bloodRequestSchema: BloodRequestSchema
) {

    suspend fun createBloodRequest(bloodRequestModel: BloodRequestModel): String {
        return bloodRequestSchema.createBloodRequest(bloodRequestModel = bloodRequestModel)
    }

    suspend fun getAllBloodRequest(sortBy: String, filter: String? = null) : List<BloodRequestModel> {
        return bloodRequestSchema.getAllBloodRequests(sortBy = sortBy, filter = filter)
    }

    suspend fun getBloodRequest(bloodRequestId: String): OperationResult<BloodRequestModel> {
        return bloodRequestSchema.getBloodRequest(bloodRequestId = bloodRequestId)
    }

    suspend fun deleteBloodRequest(bloodRequestId: String) : Boolean {
        return bloodRequestSchema.deleteBloodRequest(bloodRequestId = bloodRequestId)
    }

    suspend fun claimBloodRequest(bloodRequestId: String, bloodDonorId: String): OperationResult<String> {
        return bloodRequestSchema.claimBloodRequest(
            bloodRequestId = bloodRequestId,
            bloodDonorId = bloodDonorId
        )
    }

    suspend fun verifyDonation(bloodRequestId: String, bloodDonorId: String, code: String): OperationResult<String> {
        return bloodRequestSchema.verifyDonation(
            bloodRequestId = bloodRequestId,
            bloodDonorId = bloodDonorId,
            code = code
        )
    }

    suspend fun submitDonationProof(requestId: String, donorId: String, proofUrl: String): OperationResult<String> {
        return bloodRequestSchema.submitDonationProof(
            requestId = requestId,
            donorId = donorId,
            proofUrl = proofUrl
        )
    }

    suspend fun verifyDonationClaim(requestId: String, donorId: String): OperationResult<String> {
        return bloodRequestSchema.verifyDonationClaim(requestId = requestId, donorId = donorId)
    }

    suspend fun markRequestFulfilled(requestId: String) : OperationResult<String> {
        return bloodRequestSchema.markRequestFulfilled(
            requestId = requestId
        )
    }

}