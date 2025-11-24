package com.api.hazrat.route

import com.api.hazrat.websocket.UnifiedWebSocketManager
import io.ktor.server.application.Application
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close

fun Application.websocketRoute(manager: UnifiedWebSocketManager){
    routing {
        authenticate("auth-token"){
            webSocket("/ws"){
                val userId = call.principal<UserIdPrincipal>()?.name
                    ?: return@webSocket close(
                        CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized")
                    )
                manager.handleConnection(this, userId)
            }
        }

        // Metrics endpoint (optional)
        get("/ws/metrics") {
            val metrics = manager.getMetrics()
            call.respond(metrics)
        }
    }
}