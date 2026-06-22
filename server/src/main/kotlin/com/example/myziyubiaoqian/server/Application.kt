package com.example.myziyubiaoqian.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.example.myziyubiaoqian.server.plugins.*
import com.example.myziyubiaoqian.server.routes.*

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    configureDatabase()
    configureRouting()
}
