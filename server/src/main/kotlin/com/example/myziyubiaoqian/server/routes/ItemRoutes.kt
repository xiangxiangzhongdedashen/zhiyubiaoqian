package com.example.myziyubiaoqian.server.routes

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("智语标签服务端运行中 ✅")
        }
        get("/health") {
            call.respondText("""{"status":"ok"}""", io.ktor.http.ContentType.Application.Json)
        }
    }
}
