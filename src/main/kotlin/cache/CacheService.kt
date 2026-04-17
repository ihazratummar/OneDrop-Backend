package com.api.hazrat.cache

import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPool

class CacheService(
    private val pool: JedisPool
) {

    internal inline fun <reified T> get(key: String): T? {
        pool.resource.use { jedis ->
            val data = jedis.get(key) ?: return null
            return try {
                Json.decodeFromString<T>(data)
            } catch (e: Exception) {
                null
            }
        }
    }

    internal inline fun <reified T> set(key: String, value: T, ttl: Long = 60) {
        pool.resource.use { jedis ->
            val json = Json.encodeToString(value)
            jedis.setex(key, ttl, json)
        }
    }

    fun delete(key: String) {
        pool.resource.use { jedis ->
            jedis.del(key)
        }
    }

    fun deleteByPattern(pattern: String) {
        pool.resource.use { jedis ->
            val keys = jedis.keys(pattern)
            if (keys.isNotEmpty()) {
                jedis.del(*keys.toTypedArray())
            }
        }
    }
}