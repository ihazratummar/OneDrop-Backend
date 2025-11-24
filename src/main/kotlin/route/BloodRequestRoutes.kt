package com.api.hazrat.route

import com.api.hazrat.execptions.ErrorResponse
import com.api.hazrat.execptions.OperationResult
import com.api.hazrat.model.BloodRequestFilters
import com.api.hazrat.model.BloodRequestModel
import com.api.hazrat.service.BloodRequestService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
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
) {
    routing {
        authenticate("auth-token") {
            route("api/blood-request") {
                post("/create-blood-request") {
                    try {
                        val bloodRequestModel = call.receive<BloodRequestModel>()
                        val id = service.createBloodRequest(bloodRequestModel = bloodRequestModel)
                        call.respond(
                            HttpStatusCode.OK, mapOf(
                                "id" to id,
                                "status" to "created"
                            )
                        )
                    } catch (e: TooManyFormFieldsException) {
                        call.respond(
                            HttpStatusCode.TooManyRequests, ErrorResponse(
                                code = "too_many_request",
                                message = e.localizedMessage
                            )
                        )
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest, ErrorResponse(
                                code = "validation_error",
                                message = e.localizedMessage,
                            )
                        )
                    }
                }

                get("/get-blood-requests") {

                    try {
                        val sortBy = call.parameters["sortBy"] ?: "Recent"
                        val filter = call.parameters["statusFilter"] ?: BloodRequestFilters.ALL.displayName
                        val bloodRequests = service.getAllBloodRequest(sortBy = sortBy, filter = filter)
                        call.respond(HttpStatusCode.OK, bloodRequests)
                    } catch (e: Exception) {
                        println("Error: ${e.localizedMessage}")
                        call.respond(HttpStatusCode.BadRequest, "Invalid request format")
                    }

                }

                get("/get-blood-request") {

                    try {
                        val bloodRequestId = call.parameters["bloodRequestId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing Request Id")
                        when(val bloodRequests = service.getBloodRequest(bloodRequestId)){
                            is OperationResult.Failure -> {
                                call.respond(HttpStatusCode.BadRequest, bloodRequests.message)
                            }
                            is OperationResult.Success-> {
                                call.respond(HttpStatusCode.OK, bloodRequests.data)
                            }
                        }


                    } catch (e: Exception) {
                        println("Error: ${e.localizedMessage}")
                        call.respond(HttpStatusCode.BadRequest, "Invalid request format")
                    }

                }

                delete("/delete-blood-request") {
                    try {
                        val bloodRequestId = call.parameters["bloodRequestId"] ?: return@delete call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing bloodRequestId"
                        )
                        val isDeleted = service.deleteBloodRequest(bloodRequestId = bloodRequestId)
                        call.respond(
                            HttpStatusCode.OK, mapOf(
                                "success" to isDeleted.toString(), // Boolean field
                                "message" to if (isDeleted) "Deleted Successfully" else "Failed to Delete"
                            )
                        )
                    } catch (e: Exception) {
                        println("Error: ${e.localizedMessage}")
                        call.respond(HttpStatusCode.BadRequest, "Invalid request")
                    }
                }

                post("/claim") {
                    try {
                        val body = call.receive<Map<String, String>>()
                        val success = service.claimBloodRequest(
                            bloodRequestId = body["requestId"] ?: error("Missing requestId"),
                            bloodDonorId = body["donorId"] ?: error("Missing donor id")
                        )

                        when(success){
                            is OperationResult.Failure -> call.respond(
                                status = HttpStatusCode.fromValue(success.httpStatus),
                                message = success
                            )
                            is OperationResult.Success -> call.respond(
                                status = HttpStatusCode.fromValue(success.httpStatus),
                                message = success
                            )
                        }
                    } catch (e: Exception) {
                        println("Error: ${e.localizedMessage}")
                        call.respond(HttpStatusCode.BadRequest, "Invalid request")
                    }
                }
                post("/verify-donation") {
                    try {
                        val body = call.receive<Map<String, String>>()
                        val success = service.verifyDonation(
                            bloodRequestId = body["requestId"] ?: error("Missing requestId"),
                            bloodDonorId = body["donorId"] ?: error("Missing donor id"),
                            code = body["code"] ?: error("Missing code")
                        )
                        when(success){
                            is OperationResult.Failure -> call.respond(
                                status = HttpStatusCode.fromValue(success.httpStatus),
                                message = success
                            )
                            is OperationResult.Success -> call.respond(
                                status = HttpStatusCode.fromValue(success.httpStatus),
                                message = success
                            )
                        }

                    }catch (e: Exception) {
                        println("Error: ${e.localizedMessage}")
                        call.respond(HttpStatusCode.BadRequest, "Invalid request")
                    }
                }

                post("/manual-claim") {
                    try {
                        val body = call.receive<Map<String, String>>()
                        val success = service.submitDonationProof(
                            requestId = body["requestId"] ?: error("Missing requestId"),
                            donorId = body["donorId"] ?: error("Missing donorId"),
                            proofUrl = body["proofUrl"] ?: error("Missing proofUrl")
                        )
                        when(success){
                            is OperationResult.Failure -> call.respond(
                                status = HttpStatusCode.fromValue(success.httpStatus),
                                message = success
                            )
                            is OperationResult.Success -> call.respond(
                                status = HttpStatusCode.fromValue(success.httpStatus),
                                message = success
                            )
                        }

                    }catch (e: Exception) {
                        println("Error: ${e.localizedMessage}")
                        call.respond(HttpStatusCode.BadRequest, "Invalid request")
                    }
                }

                post("/manual-verify-claim") {
                    try {
                        val body = call.receive<Map<String, String>>()
                        val success = service.verifyDonationClaim(
                            requestId = body["requestId"] ?: error("Missing requestId"),
                            donorId = body["donorId"] ?: error("Missing donorId")
                        )

                        when(success){
                            is OperationResult.Failure -> call.respond(
                                status = HttpStatusCode.fromValue(success.httpStatus),
                                message = success
                            )
                            is OperationResult.Success -> call.respond(
                                status = HttpStatusCode.fromValue(success.httpStatus),
                                message = success
                            )
                        }
                    } catch (e: Exception) {
                        println("Error: ${e.localizedMessage}")
                        call.respond(HttpStatusCode.BadRequest, "Invalid request")
                    }
                }


                post("/mark-fulfilled") {
                    try {
                        val body = call.receive<Map<String, String>>()
                        val success = service.markRequestFulfilled(
                            requestId = body["requestId"] ?: error("Missing requestId")
                        )
                        when(success){
                            is OperationResult.Failure -> call.respond(
                                status = HttpStatusCode.fromValue(success.httpStatus),
                                message = success
                            )
                            is OperationResult.Success -> call.respond(
                                status = HttpStatusCode.fromValue(success.httpStatus),
                                message = success
                            )
                        }

                    }catch (e: Exception) {
                        println("Error: ${e.localizedMessage}")
                        call.respond(HttpStatusCode.BadRequest, "Invalid request")
                    }
                }
            }
        }
    }
}

