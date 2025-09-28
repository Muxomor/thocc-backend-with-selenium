package com.thocc

import com.thocc.di.*
import com.thocc.routes.configureNewsRoutes
import com.thocc.services.NewsService
import io.ktor.client.HttpClient
import io.ktor.server.application.*
import io.ktor.server.routing.routing
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureFrameworks() {
    install(Koin) {
        slf4jLogger()
        modules(
            appModule,
            networkModule,
            telegreamModule,
            seleniumModule,
        )
    }
    val newsService = get<NewsService>()
    val httpClient = get<HttpClient>()

    routing {
        configureNewsRoutes(newsService, httpClient)
    }
    configureBackgroundJobs()
}
