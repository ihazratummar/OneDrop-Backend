package com.api.hazrat.util

import com.api.hazrat.util.SecretConstant.DISCORD_WEBHOOK_URL
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object DiscordLogger {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    @Serializable
    data class DiscordMessage(val content: String)

    fun log(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                println("Sending to Discord: $message") // ✅ Print before sending

                val response = client.post(DISCORD_WEBHOOK_URL) {
                    contentType(ContentType.Application.Json)
                    setBody(DiscordMessage("<@475357995367137282> $message"))
                }

                println("Discord log status: ${response.status}")
                if (!response.status.isSuccess()) {
                    val error = response.bodyAsText()
                    println("Discord log failed: $error")
                }

            } catch (e: Exception) {
                println("Failed to send log to Discord: ${e.localizedMessage}")
            }
        }
    }
}