package com.api.hazrat.serialization

import com.api.hazrat.util.DiscordLogger
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
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
            DiscordLogger.log("❌ Serialization error: ${call.request.httpMethod.value} ${call.request.uri} | IP: ${call.request.origin.remoteHost}")
            call.respond(HttpStatusCode.BadRequest, "Invalid request body format ${cause.localizedMessage}")
        }

        exception<org.bson.BsonInvalidOperationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, "Invalid data provided.")
        }
        exception<com.mongodb.MongoCommandException> { call, cause ->
            DiscordLogger.log("❌ MongoDB command error: ${call.request.httpMethod.value} ${call.request.uri} | IP: ${call.request.origin.remoteHost}")
            call.respond(HttpStatusCode.Conflict, "Database error: ${cause.errorMessage ?: "Unknown issue."}")
        }
        exception<com.mongodb.MongoException> { call, _ ->
            DiscordLogger.log("❌ MongoDB error: ${call.request.httpMethod.value} $ | IP: ${call.request.origin.remoteHost}")
            call.respond(HttpStatusCode.ServiceUnavailable, "Database connection error. Please try again later.")
        }
        exception<Exception> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, "An unexpected error occurred: ${cause.localizedMessage}.")
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, "An unexpected error occurred: ${cause.localizedMessage}.")
        }
        status(HttpStatusCode.Unauthorized) { call, code ->
            DiscordLogger.log("🔐 Unauthorized access: ${call.request.httpMethod.value} ${call.request.uri} | IP: ${call.request.origin.remoteHost}")
            call.respond(HttpStatusCode.Unauthorized, "$code: Unauthorized access. Please check your credentials.")
        }
    }

}