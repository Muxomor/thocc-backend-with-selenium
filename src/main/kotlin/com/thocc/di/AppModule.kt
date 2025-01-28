package com.thocc.di

import com.tfowl.ktor.client.plugins.JsoupPlugin
import com.thocc.services.GeekhackCheckerService
import com.thocc.services.NewsService
import com.thocc.services.ZFrontierCheckerService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.xml.*
import org.jsoup.parser.Parser
import org.koin.dsl.module
import org.ktorm.database.Database
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import java.time.Duration

val appModule = module {
    single {
        //TODO("Надо убрать это отсюда")
        Database.connect(
            url = "jdbc:postgresql://thocc-project-db-mxmr.db-msk0.amvera.tech:5432/postgres",
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = "fuckyouintheass",
        )
    }
    single { NewsService(get()) }
}
val networkModule = module {
    single {
        HttpClient(CIO)
        {
            install(Logging) { level = LogLevel.BODY }
            install(ContentNegotiation) {
                xml(contentType = ContentType.parse("application/rss+xml"))
                json()
            }
            install(JsoupPlugin) {
                parsers[ContentType.Application.Rss] = Parser.xmlParser()
            }
        }
    }
    //single { GeekhackCheckerService(get(), get()) }
}
val seleniumModule = module {
    val firefoxOptions = FirefoxOptions()
    firefoxOptions.addArguments("-headless")
    firefoxOptions.addPreference("permissions.default.image", 2)
    single<FirefoxDriver> {
        FirefoxDriver(firefoxOptions).apply {
            logger.info("firefox starting...")
            manage().timeouts().implicitlyWait(Duration.ofSeconds(30))
            logger.info("firefox initialized")
        }
    }
    single { ZFrontierCheckerService(get(), get(), get()) }
}