package com.api.hazrat.execptions

import kotlinx.serialization.Serializable

@Serializable
sealed class OperationResult <out T>{
    @Serializable
    data class Success<T>(val data: T , val message: String? = null, val httpStatus: Int = 200) : OperationResult<T>()

    @Serializable
    data class Failure(val message: String, val httpStatus: Int , val  details: String? = null) : OperationResult<Nothing>()
}

