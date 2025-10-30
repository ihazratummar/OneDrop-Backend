package com.api.hazrat.route

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.html.*

fun Application.rootRouting() {
    routing {
        get("/") {
            call.respondHtml {
                head { title("OneDrop-Home") }
                body {
                    h1 { +"Welcome to OneDrop!" }
                    p { +"This is a blood donation platform." }
                }
            }
        }
    }
}
