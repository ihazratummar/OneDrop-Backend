package com.api.hazrat.util

import com.api.hazrat.util.AppSecret.DISCORD_WEBHOOK_URL
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

    @Serializable
    data class LogMessage(
        val level: String,
        val message: String,
        val userId: String? = null,
        val connectionId: String? = null,
        val error: String? = null
    )

    fun log(logMessage: LogMessage) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = Json { prettyPrint = true }.encodeToString(LogMessage.serializer(), logMessage)
                val discordMessage = "```json\n$json\n```"

                println("Sending to Discord: $discordMessage") // ✅ Print before sending

                val response = client.post(DISCORD_WEBHOOK_URL) {
                    contentType(ContentType.Application.Json)
                    setBody(DiscordMessage(discordMessage))
                }

                println("Discord log status: ${response.status}")
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    println("Discord log failed: $errorBody")
                }

            } catch (e: Exception) {
                println("Failed to send log to Discord: ${e.localizedMessage}")
            }
        }
    }
}