package com.thocc

import com.thocc.di.appModule
import com.thocc.di.configureBackgroundJobs
import com.thocc.di.networkModule
import com.thocc.di.seleniumModule
import com.thocc.routes.configureNewsRoutes
import com.thocc.services.NewsService
import io.ktor.server.application.*
import io.ktor.server.routing.routing
import org.koin.ktor.ext.get
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureFrameworks() {
    install(Koin) {
        slf4jLogger()
        modules(
            appModule,
            networkModule,
            seleniumModule,
        )
    }
    val newsService = get<NewsService>()
    routing {
        configureNewsRoutes(newsService)
    }
    configureBackgroundJobs()
}
