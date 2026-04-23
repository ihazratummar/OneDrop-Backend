package com.api.hazrat.cache

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPool

class CacheService(
    private val pool: JedisPool
) {
    val logger= KtorSimpleLogger("CacheService")

    internal inline fun <reified T> get(key: String): T? {
        return try {
            pool.resource.use { jedis ->
                val data = jedis.get(key) ?: return null
                Json.decodeFromString<T>(data)
            }
        } catch (e: Exception) {
            logger.error("Error while fetching $key", e)
            null
        }
    }

    internal inline fun <reified T> set(key: String, value: T, ttl: Long = 60) {
        try {
            pool.resource.use { jedis ->
                val json = Json.encodeToString(value)
                jedis.setex(key, ttl, json)
            }
        } catch (e: Exception) {
            logger.error("Error while setting $key", e)
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