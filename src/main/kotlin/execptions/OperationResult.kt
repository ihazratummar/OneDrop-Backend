package com.api.hazrat.execptions

import kotlinx.serialization.Serializable

@Serializable
sealed class OperationResult {
    @Serializable
    data class Success(val message: String) : OperationResult()

    @Serializable
    data class Failure(val message: String) : OperationResult()
}

