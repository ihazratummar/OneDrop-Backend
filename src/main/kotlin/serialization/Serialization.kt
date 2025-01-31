package com.api.hazrat.serialization

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                encodeDefaults = true
                ignoreUnknownKeys = true
            }
        )
    }

    install(StatusPages){
        exception<IllegalArgumentException>{call, cause ->
            call.respond(HttpStatusCode.Conflict, cause.localizedMessage ?:"Conflict: Illegal argument.")
        }
        exception<kotlinx.serialization.SerializationException>{call, cause ->
            call.respond(HttpStatusCode.BadRequest, "Invalid request body format ${cause.localizedMessage}")
        }

        exception<org.bson.BsonInvalidOperationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, "Invalid data provided.")
        }
        exception<com.mongodb.MongoCommandException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, "Database error: ${cause.errorMessage ?: "Unknown issue."}")
        }
        exception<com.mongodb.MongoException> { call, _ ->
            call.respond(HttpStatusCode.ServiceUnavailable, "Database connection error. Please try again later.")
        }
        exception<Exception> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, "An unexpected error occurred: ${cause.localizedMessage}.")
        }
    }

}