package com.api.hazrat.execptions

import kotlinx.serialization.Serializable

@Serializable
sealed class OperationResult <out T>{
    @Serializable
    data class Success<T>(val data: T , val message: String? = null) : OperationResult<T>()

    @Serializable
    data class Failure(val message: String, val error: String? = null) : OperationResult<Nothing>()
}

