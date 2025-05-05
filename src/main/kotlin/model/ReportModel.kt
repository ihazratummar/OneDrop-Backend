package com.api.hazrat.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.codecs.pojo.annotations.BsonId
import org.threeten.bp.Instant


@Serializable
data class ReportModel(
    @BsonId
    val reportId: String? = null,
    val reporterId: String,
    val reportedId: String? = null,
    val reportType: ReportType,
    val reason: String,
    val description: String? = null,
    val annonymous: Boolean = false,
    val reportStatus: ReportStatus ? = ReportStatus.PENDING,
    val timestamp: String = Instant.now().toString(),
    val screenshot: String? = null,
){
    fun toDocument(): Document {
        return Document().apply {
            reportId?.let { append("_id", it) }
            append("reporterId", reporterId)
            append("reportedId", reportedId)
            append("reportType", reportType.name)
            append("reason", reason)
            append("description", description)
            append("annonymous", annonymous)
            append("reportStatus", reportStatus?.name)
            append("timestamp", timestamp)
            append("screenshot", screenshot)
        }
    }

    companion object {
        fun fromDocument(document: Document): ReportModel {
            return ReportModel(
                reportId = document.getObjectId("_id")?.toString(),
                reporterId = document.getString("reporterId"),
                reportedId = document.getString("reportedId"),
                reportType = ReportType.valueOf(document.getString("reportType")),
                reason = document.getString("reason"),
                description = document.getString("description"),
                annonymous = document.getBoolean("annonymous"),
                reportStatus = ReportStatus.valueOf(document.getString("reportStatus")),
                timestamp = document.getString("timestamp") ?: Instant.now().toString(),
                screenshot = document.getString("screenshot")
            )
        }
    }
}

@Serializable
enum class ReportStatus {
    @SerialName("Pending")
    PENDING,

    @SerialName("Open")
    OPEN,

    @SerialName("Resolved")
    RESOLVED,

    @SerialName("Closed")
    CLOSED,

    @SerialName("Rejected")
    REJECTED
}

@Serializable
data class ReportStatusUpdateRequest(val reportStatus: ReportStatus)

@Serializable
enum class ReportType {
    @SerialName("User")
    USER,

    @SerialName("Request")
    REQUEST
}
