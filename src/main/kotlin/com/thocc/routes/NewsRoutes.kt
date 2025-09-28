package com.thocc.routes

import com.thocc.database.News
import com.thocc.models.ErrorResponse
import com.thocc.models.NewsRequest
import com.thocc.models.NewsResponse
import com.thocc.services.NewsService
import io.ktor.client.*
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Application.configureNewsRoutes(newsService: NewsService, httpClient: HttpClient) {
    routing {
        route("/") {
            createNews(newsService)
            createNewsWeb(newsService, httpClient)
            selectAllNews(newsService)
            selectNewsById(newsService)
            findNewsByName(newsService)
        }
    }
}

private fun News?.toNewsResponse(): NewsResponse? =
    this?.let { NewsResponse(it.name, it.originalName, it.link, it.timestamp) }

fun Route.createNews(newsService: NewsService) {
    post("/news") {
        val request = call.receive<NewsRequest>()
        val success = newsService.createNews(newsRequest = request)

        if (success) {
            call.respond(HttpStatusCode.Created)
        } else {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(message = "Cannot create news"))
        }
    }
}

fun Route.createNewsWeb(newsService: NewsService, httpClient: HttpClient) {
    get("/createNews") {
        val now = LocalDateTime.now()
        val zoneId = ZoneId.of("UTC+3")
        val zonedDateTime = now.atZone(zoneId)
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'UTC+3'")
        val formattedDateTime = zonedDateTime.format(formatter)
        val link = call.request.queryParameters["link"].orEmpty()
        val name = call.request.queryParameters["name"].orEmpty()

        if (link.isEmpty() || name.isEmpty()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = "Missing 'link' or 'name' parameter")
            )
            return@get
        }

        val request = NewsRequest(
            name = "[Other] $name",
            originalName = name,
            link = link,
            sourceId = 3,
            timestamp = formattedDateTime
        )

        val success = newsService.createNews(newsRequest = request)
        if (success) {
            httpClient.post("https://api.telegram.org/bot7806136583:AAFZTO7ufHr6CUasULRAkCosEz-43lnOXnQ/sendMessage") {
                parameter("chat_id", "@TopreThoc")
                parameter("text", "[Other] ${request.originalName} - ${request.link}")
            }
            call.respond(HttpStatusCode.Created)
        } else {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(message = "Cannot create news"))
        }
    }
}

fun Route.selectAllNews(newsService: NewsService) {
    get("/news") {
        val news = newsService.selectAllNews().map(News::toNewsResponse)

        call.respond(message = news)
    }
}

fun Route.selectNewsById(newsService: NewsService) {
    get("/news/{id}") {
        val id: Int = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid ID"))

        newsService.selectNewsById(id)?.let { foundNews -> foundNews.toNewsResponse() }
            ?.let { response -> call.respond(response) } ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("News with ID [$id] not found")
        )
    }
}

fun Route.findNewsByName(newsService: NewsService) {
    get("/news/{name}:{sourceId}") {
        val name = call.parameters["name"]
        val sourceId = call.parameters["sourceId"]
        if (name.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Name parameters is missing or empty")
            return@get
        }
        if (sourceId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Source id parameters is missing or empty!")
            return@get
        }
        val news = newsService.findNewsByName(name, sourceId.toInt())
        if (news != null) {
            call.respond(news)
        } else {
            call.respond(HttpStatusCode.NotFound, "News with name '$name' not found")
        }
    }
}