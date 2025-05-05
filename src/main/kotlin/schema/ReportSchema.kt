package com.api.hazrat.schema

import com.api.hazrat.model.ReportModel
import com.api.hazrat.model.ReportStatus
import com.api.hazrat.util.DiscordLogger
import com.api.hazrat.util.SecretConstant.REPORT_COLLECTION_NAME
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId


/**
 * @author Hazrat Ummar Shaikh
 * @date 04-05-2025 11:43
 */

class ReportSchema (
    database: MongoDatabase
) {

    private var reportCollection: MongoCollection<Document> = database.getCollection(REPORT_COLLECTION_NAME)


    init {
        if (!database.listCollectionNames().contains(REPORT_COLLECTION_NAME)){
            database.createCollection(REPORT_COLLECTION_NAME)
        }
    }

    suspend fun createOrUpdateReport(reportModel: ReportModel): String = withContext(Dispatchers.IO){

        val reportId = reportModel.reportId

        return@withContext if (reportId != null){
            val existingReport = reportCollection.find(Document("_id", ObjectId(reportId))).firstOrNull()
            if (existingReport != null){
                reportCollection.replaceOne(
                    Document("_id", ObjectId(reportId)),
                    reportModel.toDocument()
                )
                reportId
            }else{
                // No existing report, insert as new
                val newId = reportId // already provided
                reportCollection.insertOne(reportModel.toDocument())
                newId
            }
        }else{
            // No ID, treat as a new report (let MongoDB assign ID)
            val document = reportModel.toDocument()
            reportCollection.insertOne(document)
            document.getObjectId("_id").toString()
        }
    }

    suspend fun getAllReports(): List<ReportModel> = withContext(Dispatchers.IO) {
        return@withContext reportCollection.find().map { ReportModel.fromDocument(it) }.toList()
    }

    suspend fun getReportById(reportId: String): ReportModel? = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!isValidObjectId(reportId)){
                DiscordLogger.log("Invalid ObjectId format: $reportId")
                return@withContext null
            }
            val document = reportCollection.find(Document("_id", ObjectId(reportId))).firstOrNull()
            document?.let { ReportModel.fromDocument(it) }
        }catch (e: Exception){
            DiscordLogger.log("Exception while fetching report '$reportId': ${e.localizedMessage}")
            null
        }
    }

    suspend fun getAllReportByUserId(reporterId: String): List<ReportModel> = withContext(Dispatchers.IO) {
        return@withContext reportCollection.find(Document("reporterId", reporterId)).map { ReportModel.fromDocument(it) }.toList()
    }

    suspend fun deleteReport(reportId: String): Boolean = withContext(Dispatchers.IO) {
        val result = reportCollection.deleteOne(Document("_id", ObjectId(reportId)))
        return@withContext result.deletedCount > 0
    }

    suspend fun updateReportStatus(reportId: String, reportStatus: ReportStatus): Boolean = withContext(Dispatchers.IO) {
        val result = reportCollection.updateOne(
            Document("_id", ObjectId(reportId)),
            Document("\$set", Document("reportStatus", reportStatus.name))
        )
        return@withContext result.modifiedCount > 0
    }

    fun isValidObjectId(id: String): Boolean {
        return ObjectId.isValid(id)
    }

}