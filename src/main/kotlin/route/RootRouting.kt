package com.api.hazrat.route

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.rootRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
    }
}
