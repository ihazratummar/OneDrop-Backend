package com.api.hazrat

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
import com.api.hazrat.util.SecretConstant.MONGO_CONNECTION_URI
import com.api.hazrat.util.SecretConstant.MONGO_DATABASE_NAME
import com.api.hazrat.util.SecretConstant.USER_COLLECTION_NAME
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import io.ktor.server.application.*

fun Application.configureDatabases() {
    val mongoDatabase = connectToMongoDB()
    val bloodDonorSchema = BloodDonorSchema(mongoDatabase)
    val bloodDonorService = BloodDonorService(bloodDonorSchema = bloodDonorSchema)
    bloodDonorRoutes(service = bloodDonorService)


    val bloodRequestSchema = BloodRequestSchema(database = mongoDatabase)
    val bloodRequestService = BloodRequestService(bloodRequestSchema = bloodRequestSchema)
    bloodRequestRoutes(service = bloodRequestService)

    val reportSchema = ReportSchema(database = mongoDatabase)
    val reportService = ReportService(reportSchema = reportSchema)
    reportRoutes(reportService = reportService)

    migrationRoutes(donorCollection = mongoDatabase.getCollection(USER_COLLECTION_NAME))

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

    val mongoClient = MongoClients.create(MONGO_CONNECTION_URI)
    val database = mongoClient.getDatabase(MONGO_DATABASE_NAME)
    monitor.subscribe(ApplicationStopped) {
        mongoClient.close()
    }
    return database
}