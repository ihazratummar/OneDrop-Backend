package com.api.hazrat.schema

import com.api.hazrat.model.ReportModel
import com.api.hazrat.model.ReportStatus
import com.api.hazrat.util.DiscordLogger
import com.api.hazrat.util.AppSecret.REPORT_COLLECTION_NAME

// ✅ FIXED: Coroutine Mongo imports
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

import org.bson.Document
import org.bson.types.ObjectId

class ReportSchema(
    private val database: MongoDatabase
) {

    // ✅ Correct coroutine collection
    private val reportCollection: MongoCollection<Document> =
        database.getCollection(REPORT_COLLECTION_NAME)

    suspend fun ensureCollection() {
        // listCollectionNames() returns Flow<String>
        val collections = database.listCollectionNames().toList()

        if (!collections.contains(REPORT_COLLECTION_NAME)) {
            database.createCollection(REPORT_COLLECTION_NAME)
        }
    }

    suspend fun createOrUpdateReport(reportModel: ReportModel): String =
        withContext(Dispatchers.IO) {

            val reportId = reportModel.reportId

            if (reportId != null) {
                val existing = reportCollection
                    .find(Document("_id", ObjectId(reportId)))
                    .firstOrNull()

                return@withContext if (existing != null) {
                    reportCollection.replaceOne(
                        Document("_id", ObjectId(reportId)),
                        reportModel.toDocument()
                    )
                    reportId
                } else {
                    reportCollection.insertOne(reportModel.toDocument())
                    reportId
                }

            } else {
                val document = reportModel.toDocument()
                reportCollection.insertOne(document)
                document.getObjectId("_id").toString()
            }
        }

    suspend fun getAllReports(): List<ReportModel> = withContext(Dispatchers.IO) {
        reportCollection.find()
            .map { ReportModel.fromDocument(it) }
            .toList()
    }

    suspend fun getReportById(reportId: String): ReportModel? =
        withContext(Dispatchers.IO) {

            if (!ObjectId.isValid(reportId)) {
                DiscordLogger.log(
                    DiscordLogger.LogMessage(
                        level = "WARN",
                        message = "Invalid ObjectId: $reportId"
                    )
                )
                return@withContext null
            }

            try {
                val doc = reportCollection
                    .find(Document("_id", ObjectId(reportId)))
                    .firstOrNull()

                doc?.let { ReportModel.fromDocument(it) }
            } catch (e: Exception) {
                DiscordLogger.log(
                    DiscordLogger.LogMessage(
                        level = "ERROR",
                        message = "Error fetching report '$reportId': ${e.localizedMessage}"
                    )
                )
                null
            }
        }

    suspend fun getAllReportByUserId(reporterId: String): List<ReportModel> =
        withContext(Dispatchers.IO) {
            reportCollection.find(Document("reporterId", reporterId))
                .map { ReportModel.fromDocument(it) }
                .toList()
        }

    suspend fun deleteReport(reportId: String): Boolean =
        withContext(Dispatchers.IO) {
            val result = reportCollection.deleteOne(Document("_id", ObjectId(reportId)))
            result.deletedCount > 0
        }

    suspend fun updateReportStatus(reportId: String, status: ReportStatus): Boolean =
        withContext(Dispatchers.IO) {
            val result = reportCollection.updateOne(
                Document("_id", ObjectId(reportId)),
                Document("\$set", Document("reportStatus", status.name))
            )
            result.modifiedCount > 0
        }

    fun isValidObjectId(id: String): Boolean {
        return ObjectId.isValid(id)
    }
}
