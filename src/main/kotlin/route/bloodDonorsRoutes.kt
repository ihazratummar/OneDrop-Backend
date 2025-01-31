package com.api.hazrat.route

import com.api.hazrat.model.BloodDonorModel
import com.api.hazrat.service.BloodDonorService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.bloodDonorRoutes(
    service: BloodDonorService
) {
    routing {
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
                val donors = service.getAllBloodDonors()
                call.respond(HttpStatusCode.OK, donors)
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
        }
    }
}