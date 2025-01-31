package com.api.hazrat

import com.api.hazrat.route.bloodDonorRoutes
import com.api.hazrat.schema.BloodDonorSchema
import com.api.hazrat.service.BloodDonorService
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import io.ktor.server.application.*

fun Application.configureDatabases() {
    val mongoDatabase = connectToMongoDB()
    val bloodDonorSchema = BloodDonorSchema(mongoDatabase)
    val bloodDonorService = BloodDonorService(bloodDonorSchema = bloodDonorSchema)
    bloodDonorRoutes(service = bloodDonorService)

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
    val databaseName = System.getenv("DB_MONGO_DATABASE_NAME")
    val uri = "mongodb://localhost:27017/"
//    val uri = System.getenv("MONGO_URI")
    val mongoClient = MongoClients.create(uri)
    val database = mongoClient.getDatabase(databaseName)
    monitor.subscribe(ApplicationStopped) {
        mongoClient.close()
    }

    return database
}