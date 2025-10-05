package com.thocc

import com.thocc.routes.configureNewsRoutes
import com.thocc.services.NewsService
import com.thocc.services.TelegramService
import io.ktor.client.HttpClient
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get

fun Application.configureRouting() {
    val newsService = get<NewsService>()
    val telegramService = get<TelegramService>()

    routing {
        configureNewsRoutes(newsService, telegramService)
    }
}