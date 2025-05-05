package com.api.hazrat.route

import com.api.hazrat.execptions.ErrorResponse
import com.api.hazrat.model.BloodRequestModel
import com.api.hazrat.service.BloodRequestService
import com.api.hazrat.util.DiscordLogger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.TooManyFormFieldsException


/**
 * @author Hazrat Ummar Shaikh
 * @date 01-02-2025 17:26
 */



fun Application.bloodRequestRoutes(
    service: BloodRequestService
){
    routing {
        authenticate ("auth-token"){
            route("api/blood-request"){
                post("/create-blood-request") {
                    try {
                        val bloodRequestModel = call.receive<BloodRequestModel>()
                        val id = service.createBloodRequest(bloodRequestModel = bloodRequestModel)
                        call.respond(HttpStatusCode.OK, mapOf(
                            "id" to id,
                            "status" to "created"
                        ))
                    }catch (e: TooManyFormFieldsException){
                        DiscordLogger.log("❌ Too many form fields: ${call.request.httpMethod.value} ${call.request.uri} | IP: ${call.request.origin.remoteHost}")
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse(
                            code = "too_many_request",
                            message = e.localizedMessage
                        ))
                    }catch (e: Exception){
                        DiscordLogger.log("❌ Error: ${e.localizedMessage} | ${call.request.httpMethod.value} ${call.request.uri} | IP: ${call.request.origin.remoteHost}")
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                            code = "validation_error",
                            message = e.localizedMessage,
                        ))
                    }
                }

                get ("/get-blood-requests"){

                    try {
                        val sortBy  = call.parameters["sortBy"] ?: "Recent"
                        val bloodRequests = service.getAllBloodRequest(sortBy)
                        call.respond(HttpStatusCode.OK, bloodRequests)
                    }catch (e: Exception){
                        DiscordLogger.log("❌ Error: ${e.localizedMessage} | ${call.request.httpMethod.value} ${call.request.uri} | IP: ${call.request.origin.remoteHost}")
                        call.respond(HttpStatusCode.BadRequest, "Invalid request format")
                    }

                }

                delete("/delete-blood-request") {
                    try {
                        val bloodRequestId = call.parameters["bloodRequestId"]?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing bloodRequestId")
                        val isDeleted = service.deleteBloodRequest(bloodRequestId = bloodRequestId)
                        call.respond(HttpStatusCode.OK, mapOf(
                            "success" to isDeleted.toString(), // Boolean field
                            "message" to if (isDeleted) "Deleted Successfully" else "Failed to Delete"
                        ))
                    }catch (e: Exception){
                        DiscordLogger.log("❌ Error: ${e.localizedMessage} | ${call.request.httpMethod.value} ${call.request.uri} | IP: ${call.request.origin.remoteHost}")
                        println("Error: ${e.localizedMessage}")
                        call.respond(HttpStatusCode.BadRequest, "Invalid request")
                    }
                }
            }
        }
    }
}