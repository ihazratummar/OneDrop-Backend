package com.api.hazrat

import com.api.hazrat.util.DiscordLogger
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bson.Document
import java.util.concurrent.TimeUnit

/**
 * Background job that automatically expires old blood requests.
 */


class BloodRequestExpiryJob (
    private val bloodRequestCollection: MongoCollection<Document>,
    private val intervalMinutes: Long = 30
){
    private val jobScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        jobScope.launch {
            while (isActive){
                try {
                    val now = System.currentTimeMillis()

                    //Filter all requests that are Pending and past expiry
                    val expiredFilter = Filters.and(
                        Filters.eq("bloodRequestStatus", "Pending"),
                        Filters.lt("expiryAt", now)
                    )

                    val update = Updates.combine(
                        Updates.set("bloodRequestStatus", "Expired"),
                        Updates.set("lastUpdatedAt", now)
                    )

                    val result = bloodRequestCollection.updateMany(expiredFilter, update)

                    if (result.modifiedCount > 0) {
                        DiscordLogger.log("[BloodRequestExpiryJob] Expired ${result.modifiedCount} requests at ${java.util.Date(now)}")
                    }

                }catch (e: Exception){
                    DiscordLogger.log("[BLOOD REQUEST EXPIRY JOB] Error: ${e.localizedMessage}")
                }
                delay(TimeUnit.MINUTES.toMillis(intervalMinutes))
            }
        }
    }

    fun stop(){
        jobScope.cancel()
    }

}