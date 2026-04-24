package com.api.hazrat.mongdb

import com.api.hazrat.configureCache
import com.api.hazrat.route.bloodDonorRoutes
import com.api.hazrat.route.bloodRequestRoutes
import com.api.hazrat.route.migrationRoutes
import com.api.hazrat.route.reportRoutes
import com.api.hazrat.schema.BloodDonorSchema
import com.api.hazrat.schema.BloodRequestSchema
import com.api.hazrat.schema.ReportSchema
import com.api.hazrat.service.BloodDonorService
import com.api.hazrat.service.BloodRequestService
import com.api.hazrat.service.ReportService
import com.api.hazrat.util.AppSecret.MONGO_CONNECTION_URI
import com.api.hazrat.util.AppSecret.MONGO_DATABASE_NAME
import com.api.hazrat.util.AppSecret.USER_COLLECTION_NAME
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.server.application.*
import kotlinx.coroutines.launch
import org.bson.Document
import java.util.concurrent.TimeUnit

fun Application.configureDatabases(): MongoDatabase {
    val mongoDatabase = connectToMongoDB()
    val bloodDonorSchema = BloodDonorSchema(mongoDatabase)
    val cacheService = configureCache()!!
    val bloodDonorService = BloodDonorService(bloodDonorSchema = bloodDonorSchema, cacheService)
    bloodDonorRoutes(service = bloodDonorService)


    val bloodRequestSchema = BloodRequestSchema(database = mongoDatabase)
    launch {
        bloodRequestSchema.ensureIndexes()
    }
    val bloodRequestService = BloodRequestService(bloodRequestSchema = bloodRequestSchema, cache = cacheService)
    bloodRequestRoutes(service = bloodRequestService)

    val reportSchema = ReportSchema(database = mongoDatabase)
    val reportService = ReportService(reportSchema = reportSchema)
    reportRoutes(reportService = reportService)

    migrationRoutes(donorCollection = mongoDatabase.getCollection<Document>(USER_COLLECTION_NAME))

    return mongoDatabase
}

/**
 * Establishes connection with a MongoDB database.
 *
 * The following configuration properties (in application.yaml/application.conf) can be specified:
 * * `db.mongo.user` username for your database
 * * `db.mongo.password` password for the user
 * * `db.mongo.host` host that will be used for the database connection
 * * `db.mongo.port` port that will be used for the database connection
 * * `db.mongo.maxPoolSize` maximum number of connections to a MongoDB server
 * * `DB_MONGO_DATABASE_NAME` name of the database
 *
 * IMPORTANT NOTE: in order to make MongoDB connection working, you have to start a MongoDB server first.
 * See the instructions here: https://www.mongodb.com/docs/manual/administration/install-community/
 * all the parameters above
 *
 * @returns [MongoDatabase] instance
 * */
fun Application.connectToMongoDB(): MongoDatabase {

    val connectionString = ConnectionString(MONGO_CONNECTION_URI)

    val settings = MongoClientSettings.builder()
        .applyConnectionString(connectionString)
        .applyToConnectionPoolSettings {
            it.maxSize(10)
            it.minSize(2)
            it.maxWaitTime(10, TimeUnit.SECONDS)
            it.maxConnectionIdleTime(60, TimeUnit.SECONDS)
            it.maxConnectionLifeTime(60, TimeUnit.SECONDS)
        }
        .applyToSocketSettings {
            it.connectTimeout(5, TimeUnit.SECONDS)
            it.readTimeout(10, TimeUnit.SECONDS)
        }
        .applyToClusterSettings {
            it.serverSelectionTimeout(10, TimeUnit.SECONDS)
        }
        .build()
    val mongoClient = MongoClient.create(settings)
    val database = mongoClient.getDatabase(MONGO_DATABASE_NAME)
    environment.monitor.subscribe(ApplicationStopped) {
        mongoClient.close()
    }
    return database
}