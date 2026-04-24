package com.api.hazrat

import com.api.hazrat.cache.CacheService
import com.api.hazrat.mongdb.MongoChangeStreamManager
import com.api.hazrat.mongdb.configureDatabases
import com.api.hazrat.route.rootRouting
import com.api.hazrat.route.websocketRoute
import com.api.hazrat.serialization.configureSerialization
import com.api.hazrat.util.AppSecret
import com.api.hazrat.util.AppSecret.BLOOD_REQUEST_COLLECTION_NAME
import com.api.hazrat.util.AppSecret.ONE_DROP_API_TOKEN
import com.api.hazrat.util.DiscordLogger
import com.api.hazrat.websocket.UnifiedWebSocketManager
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.netty.EngineMain

import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.request.*
import io.ktor.server.websocket.*
import io.ktor.util.logging.*
import kotlinx.coroutines.runBlocking
import org.slf4j.event.Level
import redis.clients.jedis.JedisPool
import java.io.FileInputStream
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {

    val firebaseKeyPath = AppSecret.FIREBASE_KEY_PATH
    val serviceAccount = FileInputStream(firebaseKeyPath)

    val options = FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
        .build()

    FirebaseApp.initializeApp(options)

    // Install plugins FIRST (authentication must come before routes that use authenticate{})
    authentication()
    configurePlugins()
    configureSerialization()

    install(CallLogging) {
        level = Level.DEBUG

        filter { call ->
            call.request.path().startsWith("/")
        }
    }

    val cacheService = configureCache()

    // MongoDB connection
    val mongoDatabase = configureDatabases()

    // WebSocket manager (handles 10,000+ connection)
    val webSocketManager = UnifiedWebSocketManager(environment)

    // MongoDB Change Streams (automatic real-time updates)
    val changeStreamManager = MongoChangeStreamManager(
        database = mongoDatabase,
        webSocketManager = webSocketManager
    )
    changeStreamManager.startWatching()

    // Blood request expiry job
    val expiryJob = BloodRequestExpiryJob(
        bloodRequestCollection = mongoDatabase.getCollection(BLOOD_REQUEST_COLLECTION_NAME),
        // No need to pass websocketManager - Change Streams handle it automatically!
        cache = cacheService!!
    )
    expiryJob.start()

    environment.monitor.subscribe(ApplicationStopping){
        DiscordLogger.log(DiscordLogger.LogMessage(level = "INFO", message = "Application stopping..."))
        expiryJob.stop()
        changeStreamManager.stop()
        runBlocking {
            webSocketManager.shutdown()
        }
        DiscordLogger.log(DiscordLogger.LogMessage(level = "INFO", message = "Graceful shutdown complete"))
    }

    // Routes
    rootRouting()
    websocketRoute(manager = webSocketManager)
}

fun Application.configurePlugins() {
    install(WebSockets){
        pingPeriod = 30.seconds
        timeout = 30.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(Compression){
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024)
        }
    }

}

fun Application.configureCache(): CacheService ?{
    return try {
        val jedis = JedisPool(AppSecret.REDIS_HOST, AppSecret.REDIS_PORT)
        CacheService(jedis)
    }catch (e: Exception){
        val logger = KtorSimpleLogger("Cache")
        logger.error("Failed to connect to redis: ${e.message}")
        null
    }
}

fun Application.authentication(){
    install(Authentication){
        bearer ("auth-token"){
            authenticate { bearerTokenCredential ->
                if(bearerTokenCredential.token == ONE_DROP_API_TOKEN){
                    UserIdPrincipal("admin")
                }else{
                    DiscordLogger.log(
                        DiscordLogger.LogMessage(
                            level = "WARN",
                            message = "Unauthorized access with token: ${bearerTokenCredential.token}"
                        )
                    )
                    null
                }
            }
        }
    }
}