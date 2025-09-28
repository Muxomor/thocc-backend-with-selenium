package com.thocc.di

import com.tfowl.ktor.client.plugins.JsoupPlugin
import com.thocc.models.TelegramConfig
import com.thocc.services.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.xml.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import org.jsoup.parser.Parser
import org.koin.dsl.module
import org.ktorm.database.Database
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import java.time.Duration
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureDI() {
    install(Koin) {
        slf4jLogger()
        modules(
            appModule(environment.config),
            networkModule,
            telegramModule(environment.config),
            seleniumModule
        )
    }
}

fun appModule(config: ApplicationConfig) = module {
    single {
        Database.connect(
            url = config.property("url").getString(),
            driver = "org.postgresql.Driver",
            user = config.property("user").getString(),
            password = config.property("password").getString(),
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
            install(HttpTimeout) {
                //cant remember why i have this hardcoded timeouts
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 20000
                socketTimeoutMillis = 25000
            }

            install(JsoupPlugin) {
                parsers[ContentType.Application.Rss] = Parser.xmlParser()
            }
        }
    }
    single { GeekhackCheckerService(get(), get(), get()) }
}
fun telegramModule(config: ApplicationConfig) = module {
    single {
        val botToken = config.property("token").getString()
        val chatId = config.property("chatId").getString()
        TelegramConfig(botToken, chatId)
    }
    single { TelegramService(get(), get()) }
}
val seleniumModule = module {
    System.setProperty("webdriver.gecko.driver", "/usr/local/bin/geckodriver")
    val firefoxOptions = FirefoxOptions().apply {
        addArguments("-headless")
        addPreference("permissions.default.image", 2)
    }
    single<FirefoxDriver> {
        FirefoxDriver(firefoxOptions).apply {
            logger.info("firefox starting...")
            manage().timeouts().implicitlyWait(Duration.ofSeconds(30))
            logger.info("firefox initialized")
        }
    }
    single { ZFrontierCheckerService(get(), get(), get(), get()) }
}