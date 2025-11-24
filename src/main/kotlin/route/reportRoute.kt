package com.api.hazrat.route

import com.api.hazrat.model.ReportModel
import com.api.hazrat.model.ReportStatusUpdateRequest
import com.api.hazrat.service.ReportService
import com.api.hazrat.util.DiscordLogger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


/**
 * @author Hazrat Ummar Shaikh
 * @date 04-05-2025 11:34
 */


fun Application.reportRoutes(
    reportService: ReportService
) {
    routing {
        authenticate("auth-token") {
            route("api/report") {

                // 🔴 1. Submit a new report
                post("/create-report") {
                    // Logic: Parse report data from body
                    // Validate and create a new report
                    // Respond with success or error message

                    try {
                        val reportModel = call.receive<ReportModel>()
                        val reportId = reportService.createOrUpdateReport(reportModel)
                        call.respond(HttpStatusCode.Created, "Report created with ID: $reportId")
                    }catch (e: Exception){
                        DiscordLogger.log(
                            DiscordLogger.LogMessage(
                                level = "ERROR",
                                message = "Exception in POST /create-report: ${e.message} | IP: ${call.request.origin.remoteHost}"
                            )
                        )
                        call.respond(HttpStatusCode.InternalServerError, "Something went wrong")
                    }
                }

                // 🟡 2. Get all reports (admin access required)
                get("/all-reports") {
                    // Logic: Optional query params -> status, type
                    // Fetch all reports
                    // Respond with the list of reports

                    try {
                        val reports = reportService.getAllReports()
                        if (reports.isNotEmpty()) {
                            call.respond(HttpStatusCode.OK, reports)
                        } else {
                            DiscordLogger.log(
                                DiscordLogger.LogMessage(
                                    level = "INFO",
                                    message = "No reports found from ${call.request.origin.remoteHost} for getting all reports "
                                )
                            )
                            call.respond(HttpStatusCode.NoContent, "No reports found")
                        }
                    }catch (e: Exception){
                        DiscordLogger.log(
                            DiscordLogger.LogMessage(
                                level = "ERROR",
                                message = "Exception in GET /all-reports: ${e.message} | IP: ${call.request.origin.remoteHost}"
                            )
                        )
                        call.respond(HttpStatusCode.InternalServerError, "Something went wrong")
                    }
                }

                // 🔵 3. Get single report by ID
                get("/report") {
                    try {
                        val reportId = call.request.queryParameters["reportId"]
                        if (reportId.isNullOrBlank()) {
                            DiscordLogger.log(
                                DiscordLogger.LogMessage(
                                    level = "WARN",
                                    message = "Missing `reportId` in GET /report from ${call.request.origin.remoteHost}"
                                )
                            )
                            return@get call.respond(HttpStatusCode.BadRequest, "Missing Report ID")
                        }

                        val report = reportService.getReportById(reportId)
                        if (report != null) {
                            call.respond(HttpStatusCode.OK, report)
                        } else {
                            DiscordLogger.log(
                                DiscordLogger.LogMessage(
                                    level = "INFO",
                                    message = "Report not found for ID `$reportId` from ${call.request.origin.remoteHost} for getting report by ID"
                                )
                            )
                            call.respond(HttpStatusCode.NotFound, "Report not found")
                        }
                    } catch (e: Exception) {
                        DiscordLogger.log(
                            DiscordLogger.LogMessage(
                                level = "ERROR",
                                message = "Exception in GET /report: ${e.message} | IP: ${call.request.origin.remoteHost}"
                            )
                        )
                        call.respond(HttpStatusCode.InternalServerError, "Something went wrong")
                    }
                }


                // 🟢 4. Get all reports made by a user
                get("/user") {

                    // Logic: Fetch all reports by this user

                    try {
                        val userId = call.request.queryParameters["reporterId"]
                        if (userId.isNullOrBlank()){
                            DiscordLogger.log(
                                DiscordLogger.LogMessage(
                                    level = "WARN",
                                    message = "Missing `reporterId` in GET /user from ${call.request.origin.remoteHost}"
                                )
                            )
                            return@get call.respond(HttpStatusCode.BadRequest, "Missing User ID")
                        }
                        val reports = reportService.getAllReportsByUserId(userId)
                        if (reports.isNotEmpty()) {
                            call.respond(HttpStatusCode.OK, reports)
                        } else {
                            DiscordLogger.log(
                                DiscordLogger.LogMessage(
                                    level = "INFO",
                                    message = "No reports found for user ID `$userId` from ${call.request.origin.remoteHost} for getting reports"
                                )
                            )
                            call.respond(HttpStatusCode.NoContent, "No reports found for this user")
                        }
                    }catch (e: Exception){
                        DiscordLogger.log(
                            DiscordLogger.LogMessage(
                                level = "ERROR",
                                message = "Exception in GET /user: ${e.message} | IP: ${call.request.origin.remoteHost}"
                            )
                        )
                        call.respond(HttpStatusCode.InternalServerError, "Something went wrong")
                    }


                }

                // 🟣 5. Update report status (admin only)
                patch("/status") {
                    try {
                        val reportId = call.request.queryParameters["reportId"]
                        if (reportId.isNullOrBlank()) {
                            DiscordLogger.log(
                                DiscordLogger.LogMessage(
                                    level = "WARN",
                                    message = "Missing `reportId` in PATCH /status from ${call.request.origin.remoteHost}"
                                )
                            )
                            return@patch call.respond(HttpStatusCode.BadRequest, "Missing Report ID")
                        }
                        val requestBody = call.receive<ReportStatusUpdateRequest>()
                        val isUpdated = reportService.updateReportStatus(reportId, requestBody.reportStatus)
                        if (isUpdated) {
                            call.respond(HttpStatusCode.OK, "Report status updated successfully")
                        } else {
                            DiscordLogger.log(
                                DiscordLogger.LogMessage(
                                    level = "INFO",
                                    message = "Report not found for ID `$reportId` from ${call.request.origin.remoteHost} for updating status"
                                )
                            )
                            call.respond(HttpStatusCode.NotFound, "Report not found")
                        }
                    }catch (e: Exception){
                        DiscordLogger.log(
                            DiscordLogger.LogMessage(
                                level = "ERROR",
                                message = "Exception in PATCH /status: ${e.message} | IP: ${call.request.origin.remoteHost}"
                            )
                        )
                        call.respond(HttpStatusCode.InternalServerError, "Something went wrong")
                    }
                }


                // ⚫ 6. Delete report (admin only)
                // Optional
                // ⚫ 6. Delete report (admin only) — using query param now
                delete("/delete") {
                    try {
                        val reportId = call.request.queryParameters["reportId"]

                        if (reportId.isNullOrBlank()) {
                            DiscordLogger.log(
                                DiscordLogger.LogMessage(
                                    level = "WARN",
                                    message = "Missing `reportId` in DELETE /delete from ${call.request.origin.remoteHost}"
                                )
                            )
                            return@delete call.respond(HttpStatusCode.BadRequest, "Missing Report ID")
                        }
                        val isDeleted = reportService.deleteReport(reportId)
                        if (isDeleted) {
                            call.respond(HttpStatusCode.OK, "Report deleted successfully")
                        } else {
                            DiscordLogger.log(
                                DiscordLogger.LogMessage(
                                    level = "INFO",
                                    message = "Report not found for ID `$reportId` from ${call.request.origin.remoteHost} for deleting"
                                )
                            )
                            call.respond(HttpStatusCode.NotFound, "Report not found")
                        }
                    }catch (e: Exception){
                        DiscordLogger.log(
                            DiscordLogger.LogMessage(
                                level = "ERROR",
                                message = "Exception in DELETE /delete: ${e.message} | IP: ${call.request.origin.remoteHost}"
                            )
                        )
                        call.respond(HttpStatusCode.InternalServerError, "Something went wrong")
                    }
                }

            }
        }
    }
}
