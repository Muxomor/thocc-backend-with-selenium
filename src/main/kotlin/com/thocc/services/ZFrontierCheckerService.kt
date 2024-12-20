package com.thocc.services

import com.thocc.models.NewsRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.openqa.selenium.firefox.FirefoxDriver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap

private const val BASE_URL = "https://www.zfrontier.com"

private var json = Json { ignoreUnknownKeys = true }

private const val JOB_INTERVAL_2_H = 2 * 60 * 60 * 1000L

class ZFrontierCheckerService(
    private val newsService: NewsService,
    private val client: HttpClient,
    private val browser: FirefoxDriver,
) {
    companion object {
        const val CACHE_CLEAR_INTERVAL_HOURS = 24
    }

    private val cache = ConcurrentHashMap.newKeySet<String>()

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private suspend fun getDocumentFromURL(url: String): Document {
        return try {
            logger.info("zf checker page loading...")
            browser.get(url)
            delay(30000L)
            val response = browser.pageSource
            logger.info("zf page loaded!")
            logger.info("navigating to google.com")
            browser.get("https://www.google.com/")
            return Jsoup.parse(response.toString())
        } catch (e: Exception) {
            logger.error("failed to get document from URL: ${url}", e)
            throw e
        }
    }

    suspend fun zFrontierChecker() {
        val document = getDocumentFromURL("https://www.zfrontier.com/app/#info")
        logger.info("main function receiver answer from ZF")
        val listOfElements = document.select("div.right").toList()
        for (item in listOfElements) {
            var name = item.selectFirst("div.article-title.f-16.fw-b")?.ownText()?.trim() ?: "Error"
            if (name == "Error" || !checkNameInCacheAndDB(name)) {
                continue
            }
            var link =
                (BASE_URL + item.selectFirst("div.right > a")?.attr("href")?.trim()) ?: "Error"
            var rawTimestamp =
                item.selectFirst("div.right > div.user-line.f-16.flex-center-v > span")?.ownText()
                    ?.trim() ?: "Error"
            val listOfPhotoLinks = item.select("a > div.pic-grid.multiple img").toList()
            var photoLinks: MutableList<String> = mutableListOf()
            for (photos in listOfPhotoLinks) {
                photoLinks.add(photos.attr("data-src")?.trim() ?: "Error")
            }
            val translatedName = translateString(name)
            logger.info("${name} - ${link} - ${convertRawTimeToDateTime(rawTimestamp)}")
            val request =
                NewsRequest(translatedName, name, link, 2, convertRawTimeToDateTime(rawTimestamp))
            delay(10000L)
            postNewsToTelegramm(request, photoLinks)
            postNewsToDB(request)
        }
    }

    suspend fun startChecker() {
        var lastCacheClearTime = System.currentTimeMillis()
        while (true) {
            try {
                zFrontierChecker()
                if (System.currentTimeMillis() - lastCacheClearTime > CACHE_CLEAR_INTERVAL_HOURS * 60 * 60 * 1000L) {
                    cache.clear()
                    logger.info("ZF checker cache cleared")
                    lastCacheClearTime = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                logger.error("some error has occurred in ZF checker: $e")
            }
            logger.info("job done, waiting for 4 hours")
            delay(JOB_INTERVAL_2_H)
        }
    }

    private fun checkNameInCacheAndDB(name: String): Boolean {
        if (cache.contains(name)) {
            return false
        } else {
            if (newsService.findNewsByName(name, 2) != null) {
                cache.add(name)
                return false
            } else {
                cache.add(name)
                return true
            }
        }
    }

    private fun convertRawTimeToDateTime(input: String): String {
        var currentDate = LocalDate.now()
        var currentTimeInMoscow = LocalTime.now()
        var dateTime = input
        if (dateTime.contains("小时前")) {
            dateTime = dateTime.replace(Regex("[^0-9]"), "")
            currentTimeInMoscow = currentTimeInMoscow.minusHours(dateTime.toLong())
        } else if (dateTime.contains("分钟前")) {
            dateTime = dateTime.replace(Regex("[^0-9]"), "")
            currentTimeInMoscow = currentTimeInMoscow.minusMinutes(dateTime.toLong())
        } else if (dateTime == "Error") {
            return "Error"
        } else {
            dateTime = dateTime.replace(Regex("[^0-9]"), "")
            currentDate = currentDate.minusDays(dateTime.toLong())
        }
        return "${currentDate.toString().replace(Regex("-"), ".")} ${
            currentTimeInMoscow.toString().substring(0, 5)
        } UTC+3"
    }

    private suspend fun translateString(nameToTranslate: String): String {
        val response = client.get("https://ftapi.pythonanywhere.com/translate") {
            parameter("dl", "en")
            parameter("text", nameToTranslate)
        }
        return json.decodeFromString<DestinationTextResponse>(response.bodyAsText()).destinationText
    }

    private suspend fun postNewsToTelegramm(newsRequest: NewsRequest, photosList: List<String>) {
        if (photosList.isNotEmpty()) {
            val mediaGroup = photosList.mapIndexed { index, photoUrl ->
                MediaItem(
                    type = "photo",
                    media = photoUrl,
                    caption = if (index == 0) "[ZF] ${newsRequest.name} - ${newsRequest.link}" else ""
                )
            }
            val requestBody = MediaGroupRequest(
                chat_id = "@TopreThoc",
                media = mediaGroup
            )
            logger.info("request body: ${requestBody.toString()}")
            val response =
                client.post("https://api.telegram.org/bot7806136583:AAFZTO7ufHr6CUasULRAkCosEz-43lnOXnQ/sendMediaGroup") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            logger.info(response.bodyAsText())
        }
    }

    private suspend fun postNewsToDB(newsRequest: NewsRequest) {
        newsService.createNews(newsRequest)
    }
}

@Serializable
data class DestinationTextResponse(
    @SerialName("destination-text") val destinationText: String,
)

@Serializable
data class MediaItem(
    val type: String,
    val media: String,
    val caption: String? = null,
)

@Serializable
data class MediaGroupRequest(
    val chat_id: String,
    val media: List<MediaItem>,
)