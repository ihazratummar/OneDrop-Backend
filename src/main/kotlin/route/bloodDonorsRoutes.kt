package com.api.hazrat.route

import com.api.hazrat.execptions.OperationResult
import com.api.hazrat.model.BloodDonorModel
import com.api.hazrat.service.BloodDonorService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun Application.bloodDonorRoutes(
    service: BloodDonorService
) {
    routing {
        authenticate ("auth-token"){
            route("api/donors"){
                post("/create-update-donor") {
                    try {
                        val bloodDonorModel = call.receive<BloodDonorModel>()
                        val id = service.createOrUpdateBloodDonor(bloodDonorModel = bloodDonorModel)
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Success", "donorId" to id))
                    }catch (e: Exception){
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, "An error occurred: ${e.message}")
                    }
                }

                get("/get-donors"){
                    try {
                        val donors = service.getAllBloodDonors()
                        println("Fetched Donors : $donors")
                        call.respond(HttpStatusCode.OK, donors)
                    }catch (e: Exception){
                        e.printStackTrace()
                        println("Error fetching donors ${e.localizedMessage}")
                    }
                }


                get ("/donor-profile"){
                    val userId = call.parameters["userId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing userId")
                    val donor = service.getDonorProfile(userId = userId)
                    call.respond(HttpStatusCode.OK, donor)
                }

                get("/is-donor-exist"){
                    val userId = call.parameters["userId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing userId")
                    val isDonor = service.isBloodDonorExist(userId = userId)
                    call.respond(HttpStatusCode.OK, isDonor)
                }

                post("/toggle-availability") {
                    val userId = call.parameters["userId"]?: return@post call.respond(HttpStatusCode.BadRequest, "Missing userId")
                    val key = call.parameters["key"]?: return@post call.respond(HttpStatusCode.BadRequest, "Missing userId")

                    try {
                        val update = service.toggleAvailability(userId = userId, key= key)
                        call.respond(HttpStatusCode.OK, update)
                    }catch (e: Exception){
                        call.respond(HttpStatusCode.InternalServerError, "Error ${e.localizedMessage}")
                    }
                }

                delete ("/delete-donor"){
                    val userID = call.parameters["userID"]?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing userId")

                    try {
                        val delete = service.deleteBloodDonor(userId = userID)
                        call.respond(HttpStatusCode.OK, mapOf(
                            "success" to delete.toString(), // Boolean field
                            "message" to if (delete) "Deleted Successfully" else "Failed to Delete"
                        ))
                    }catch (e: Exception){
                        call.respond(HttpStatusCode.InternalServerError, "Error ${e.localizedMessage}")
                    }
                }

                delete("/delete-profile") {
                    val userID = call.parameters["userID"]?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing userId")
                    try {
                        val delete = service.deleteFirebaseUser(userId = userID)
                        call.respond(HttpStatusCode.OK, mapOf(
                            "success" to delete.toString(),
                            "message" to if (delete) "Deleted Successfully" else "Failed to Delete"
                        ))
                    }catch (e: Exception){
                        call.respond(HttpStatusCode.InternalServerError, "Error ${e.localizedMessage}")
                    }
                }

                patch("/score-reset") {
                    val userID = call.parameters["userID"]?: return@patch call.respond(HttpStatusCode.BadRequest, "Missing userId")
                    try {
                        val success = service.resetDonorScoreDonorScore(userId = userID)
                        call.respond(HttpStatusCode.OK, message = mapOf(
                            "success" to success.toString(),
                            "message" to if (success) "Score reset to 0" else "Failed to add score"
                        ))
                    }catch (e: Exception){
                        call.respond(HttpStatusCode.InternalServerError, "Error ${e.localizedMessage}")
                    }
                }

                patch("set-notification-scope"){
                    val userId = call.parameters["userID"]?: return@patch call.respond(HttpStatusCode.BadRequest, "Missing userId")
                    val notificationScope = call.parameters["notificationScope"]?: return@patch call.respond(HttpStatusCode.BadRequest, "Missing notificationScope")
                    try {
                        val success = service.updateNotificationScope(
                            userId = userId,
                            notificationScope = notificationScope
                        )
                        call.respond(
                            HttpStatusCode.OK,
                            message = mapOf(
                                "success" to success.toString(),
                                "message" to if(success) "Notification Scope updated to $notificationScope" else "Failed to update notification scope"
                            )
                        )
                    }catch (e: Exception){
                        call.respond(HttpStatusCode.InternalServerError, "Error ${e.localizedMessage}")
                    }
                }

                get("get-blood-donated-list"){
                    val userId = call.parameters["userID"]?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        message = "Missing userid"
                    )
                    try {
                        val success  = service.getMyBloodDonationList(userId = userId)
                        when(success){
                            is OperationResult.Failure -> {
                                call.respond(
                                    call.respond(HttpStatusCode.NotFound, success.message)
                                )
                            }
                            is OperationResult.Success ->  {
                                call.respond(
                                    status = HttpStatusCode.OK,
                                    message = success.data
                                )
                            }
                        }
                    }catch (e: Exception){
                        call.respond(HttpStatusCode.InternalServerError, "Error ${e.localizedMessage}")
                    }
                }
            }
        }
    }
}