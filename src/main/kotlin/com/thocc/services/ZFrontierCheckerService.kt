package com.thocc.services

import com.thocc.models.NewsRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.openqa.selenium.firefox.FirefoxDriver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

private const val BASE_URL = "https://www.zfrontier.com"
private var json = Json { ignoreUnknownKeys = true }

private const val JOB_INTERVAL_1_H = 1 * 60 * 60 * 1000L 

class ZFrontierCheckerService(
    private val newsService: NewsService,
    private val client: HttpClient,
    private val browser: FirefoxDriver,
    private val telegramService: TelegramService
) {
    companion object {
        const val CACHE_CLEAR_INTERVAL_HOURS = 4 * 24
    }

    private val cache = ConcurrentHashMap.newKeySet<String>()
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private suspend fun getDocumentFromURL(url: String): Document {
        try {
            logger.info("zf checker page loading...")
            browser.get(url)
            delay(60000L)
            val response = browser.pageSource
            logger.info("zf page loaded!")
            logger.info("navigating to google.com to release resources")
            browser.get("https://www.google.com/")
            return Jsoup.parse(response)
        } catch (e: Exception) {
            logger.error("failed to get document from URL: $url", e)
            throw e
        }
    }

    private suspend fun zFrontierChecker() {
        val document = getDocumentFromURL("https://www.zfrontier.com/app/#info")
        logger.info("main function receiver answer from ZF")
        val listOfElements = document.select("div.right").toList()
        for (item in listOfElements) {
            var name = item.selectFirst("div.article-title.f-16.fw-b")
                ?.ownText()
                ?.trim()
                ?.take(200)
                ?: "Error"
            if(name == "Error"){
                name = item.selectFirst("div.f-15.fw-b.ellipsis_4.short-flow-article")?.ownText()
                    ?.trim()
                    ?.take(200)
                    ?: "Error"
            }
            val link = (BASE_URL + item.selectFirst("div.right > a")?.attr("href")?.trim())

            if (name == "Error" || !checkNameInCacheAndDB(name) || !checkLinkInDB(link)) {
                logger.info("skipped $name - $link")
                continue
            }
            logger.info("passed $link")

            val rawTimestamp = item.selectFirst("div.right > div.user-line.f-16.flex-center-v > span")?.ownText()?.trim() ?: "Error"
            var listOfPhotoLinks = item.select("a > div.pic-grid.multiple img").toList()
            if(listOfPhotoLinks.isEmpty()) {
                listOfPhotoLinks = item.select("a > div.pic-grid img").toList()
            }
            val photoLinks: MutableList<String> = mutableListOf()
            for (photos in listOfPhotoLinks) {
                val photoUrl = photos.attr("data-src").trim().ifEmpty { photos.attr("src").trim() }
                if (photoUrl.isNotEmpty()) {
                    photoLinks.add(photoUrl)
                }
            }
            val translatedName = translateString(name)
            logger.info("$name - $link - ${convertRawTimeToDateTime(rawTimestamp)}")
            val request = NewsRequest(translatedName, name, link, 2, convertRawTimeToDateTime(rawTimestamp))
            delay(10000L)

            postNewsToTelegram(request, photoLinks)

            postNewsToDB(request)
        }
    }

    private fun checkNameInCacheAndDB(name: String): Boolean {
        if (cache.contains(name)) {
            return false
        }
        return newsService.findNewsByName(name, 2)?.also { cache.add(name) } == null
    }

    private fun checkLinkInDB(link: String): Boolean {
        return newsService.findNewsByLink(link) == null
    }
    //this thing previously worked as real converter, but now its just returning current time
    private fun convertRawTimeToDateTime(input: String): String {
        val moscowZone = ZoneId.of("Europe/Moscow")
        val currentDateTime = ZonedDateTime.now(moscowZone)
        return currentDateTime.format(
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm 'UTC+3'").withZone(moscowZone)
        )
    }

    //better pictures quality but telegram cant send them at the time(cdn servers cant handle it i guess)
    private fun cleanImageUrl(url: String): String {
        //return url.replace("-cover360.webp", "")
        return url
    }

    private suspend fun translateString(nameToTranslate: String): String {
        return try {
            val response = client.get("https://ftapi.pythonanywhere.com/translate") {
                parameter("dl", "en")
                parameter("text", nameToTranslate)
            }
            logger.info(response.toString())
            json.decodeFromString<DestinationTextResponse>(response.bodyAsText()).destinationText
        } catch (e: Exception) {
            logger.error("Failed to translate string: ${e.message}")
            nameToTranslate
        }
    }

    suspend fun startChecker() = coroutineScope {
        var lastCacheClearTime = System.currentTimeMillis()

        logger.info("Main ZFrontier news checker loop started. Will check for news every ${JOB_INTERVAL_1_H / (60*1000)} minute(s).")
        while (true) {
            try {
                zFrontierChecker()

                if (System.currentTimeMillis() - lastCacheClearTime > CACHE_CLEAR_INTERVAL_HOURS * 60 * 60 * 1000L) {
                    cache.clear()
                    logger.info("ZF checker cache cleared")
                    lastCacheClearTime = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                logger.error("Some error has occurred in main ZF checker loop: ${e.message}", e)
            }
            logger.info("Main ZF check job done, waiting for ${JOB_INTERVAL_1_H / (60*1000)} minute(s).")
            delay(JOB_INTERVAL_1_H)
        }
    }

    private suspend fun postNewsToTelegram(newsRequest: NewsRequest, photosList: List<String>) {
        val fullCaption = "[ZF] ${newsRequest.name} - ${newsRequest.link}"
        var success = false

        if (photosList.isNotEmpty()) {
            success = when {
                photosList.size > 1 -> telegramService.sendMediaGroup(fullCaption, photosList)
                else -> telegramService.sendSinglePhoto(fullCaption, photosList.first())
            }
        }


        if (!success) {
            if (photosList.isNotEmpty()) {
                logger.warn("Failed to send media for '${newsRequest.name}' after all retries. Falling back to a text message.")
            }
            telegramService.sendTextMessage(fullCaption)
        }
    }

    private fun postNewsToDB(newsRequest: NewsRequest) {
        try {
            newsService.createNews(newsRequest)
        } catch (e: Exception) {
            logger.error("Failed to save news to DB: ${e.message}")
        }
    }
}

@Serializable
data class DestinationTextResponse(
    @SerialName("destination-text") val destinationText: String,
)
