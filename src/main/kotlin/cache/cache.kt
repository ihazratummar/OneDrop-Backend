package com.api.hazrat.cache

object CacheKeys {

    fun donor(userId: String) = "donor:$userId"

    fun donors() = "donors"

    fun donorsPage(page: Int) = "donors:page:$page"

    fun donorsCount() = "donors:count"
}