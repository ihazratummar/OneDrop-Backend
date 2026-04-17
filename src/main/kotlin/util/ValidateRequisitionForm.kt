package com.api.hazrat.util

import com.google.cloud.firestore.telemetry.MetricsUtil.logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess

sealed class ImageInput{
    data class UrlInput(val url: String) : ImageInput()
}


object UrlValidator {

    private val client = HttpClient(CIO)

    suspend fun isValidFileUrl(url: String) : Boolean {
        return try {

            val response: HttpResponse = client.get(url){
                method = HttpMethod.Head
            }
            val statusOk = response.status.isSuccess()
            val contentType = response.headers[HttpHeaders.ContentType]
            val isFile = contentType != null && !contentType.contains("text/html", ignoreCase = true)
            statusOk && isFile
        }catch (e: Exception){
            logger.warning("Failed to get url")
            false
        }
    }

}