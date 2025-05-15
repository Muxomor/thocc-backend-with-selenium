package com.thocc

import com.thocc.plugins.configureSerialization
import com.thocc.routes.configureNewsRoutes
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty


//fun main() {
//    embeddedServer(Netty, port = 8080) {
//        configureSerialization()
//        configureFrameworks()
//    }.start(wait = true)
//}

fun main(){
    embeddedServer(Netty, port = 7895, module = Application::module).start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureFrameworks()
}
