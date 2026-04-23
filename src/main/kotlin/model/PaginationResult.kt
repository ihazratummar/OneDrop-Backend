package com.api.hazrat.model

import kotlinx.serialization.Serializable

@Serializable
data class PaginationResult <T>(
    val data: List<T>,
    val page: Int,
    val limit: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
)