package com.api.hazrat.service

import com.api.hazrat.model.ReportModel
import com.api.hazrat.model.ReportStatus
import com.api.hazrat.schema.ReportSchema


/**
 * @author Hazrat Ummar Shaikh
 * @date 04-05-2025 11:59
 */

class ReportService(
    private val reportSchema: ReportSchema
) {

    suspend fun createOrUpdateReport(reportModel: ReportModel): String {
        return reportSchema.createOrUpdateReport(reportModel = reportModel)
    }

    suspend fun getAllReports(): List<ReportModel> {
        return reportSchema.getAllReports()
    }

    suspend fun getReportById(reportId: String): ReportModel? {
        return reportSchema.getReportById(reportId = reportId)
    }

    suspend fun getAllReportsByUserId(reporterId: String): List<ReportModel> {
        return reportSchema.getAllReportByUserId(reporterId = reporterId)
    }

    suspend fun deleteReport(reportId: String): Boolean {
        return reportSchema.deleteReport(reportId = reportId)
    }

    suspend fun updateReportStatus(reportId: String, reportStatus: ReportStatus): Boolean {
        return reportSchema.updateReportStatus(reportId = reportId, status = reportStatus)
    }
}