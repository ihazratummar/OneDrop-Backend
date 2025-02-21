package com.api.hazrat.service

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

    suspend fun getAllBloodRequest(sortBy: String) : List<BloodRequestModel> {
        return bloodRequestSchema.getAllBloodRequests(sortBy = sortBy)
    }

    suspend fun deleteBloodRequest(bloodRequestId: String) : Boolean {
        return bloodRequestSchema.deleteBloodRequest(bloodRequestId = bloodRequestId)
    }

}