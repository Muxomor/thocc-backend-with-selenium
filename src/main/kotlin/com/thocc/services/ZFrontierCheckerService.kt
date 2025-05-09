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

private const val BASE_URL = "https://www.zfrontier.com"

private var json = Json { ignoreUnknownKeys = true }

private const val JOB_INTERVAL_1_H = 1 * 60 * 60 * 1000L

class ZFrontierCheckerService(
    private val newsService: NewsService,
    private val client: HttpClient,
    private val browser: FirefoxDriver,
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
            logger.info("navigating to google.com")
            browser.get("https://www.google.com/")
            return Jsoup.parse(response.toString())
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
            val link =
                (BASE_URL + item.selectFirst("div.right > a")?.attr("href")?.trim())
            if (name == "Error" || !checkNameInCacheAndDB(name) || !checkLinkInDB(link)) {
                logger.info("skipped $name - $link")
                continue
            }
            logger.info(
                "passed $link"
            )
            val rawTimestamp =
                item.selectFirst("div.right > div.user-line.f-16.flex-center-v > span")?.ownText()
                    ?.trim() ?: "Error"
            var listOfPhotoLinks = item.select("a > div.pic-grid.multiple img").toList()
            if(listOfPhotoLinks.isEmpty()) {
                listOfPhotoLinks = item.select("a > div.pic-grid img").toList()
            }
            val photoLinks: MutableList<String> = mutableListOf()
            for (photos in listOfPhotoLinks) {
                photoLinks.add(photos.attr("data-src").trim())
            }
            val translatedName = translateString(name)
            logger.info("$name - $link - ${convertRawTimeToDateTime(rawTimestamp)}")
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
            logger.info("job done, waiting for 1 hour")
            delay(JOB_INTERVAL_1_H)
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

    private fun checkLinkInDB(link: String): Boolean {
        if (newsService.findNewsByLink(link) != null) {
            return false
        } else {
            return true
        }
    }

    private fun convertRawTimeToDateTime(input: String): String {
        val moscowZone = ZoneId.of("Europe/Moscow")
        val currentDateTime = ZonedDateTime.now(moscowZone)

        return currentDateTime.format(
            DateTimeFormatter
                .ofPattern("yyyy.MM.dd HH:mm 'UTC+3'")
                .withZone(moscowZone))
    }

    private fun cleanImageUrl(url: String): String {
        val cleanedUrl = url.replace("-cover360.webp", "")
        return cleanedUrl
    }

    private suspend fun translateString(nameToTranslate: String): String {
        return try {
            val response = client.get("https://ftapi.pythonanywhere.com/translate") {
                parameter("dl", "en")
                parameter("text", nameToTranslate)
            }
            json.decodeFromString<DestinationTextResponse>(response.bodyAsText()).destinationText
        } catch (e: Exception) {
            logger.error("Failed to translate string: ${e.message}")
            nameToTranslate
        }
    }

    private suspend fun postNewsToTelegramm(newsRequest: NewsRequest, photosList: List<String>) {
        val chatId = "@TopreThoc"
        val baseCaptionText = "[ZF] ${newsRequest.originalName.ifEmpty { newsRequest.name }}"
        val fullCaption = "$baseCaptionText - ${newsRequest.link}"

        // Очищаем URL и фильтруем только валидные http/https URL
        val cleanedPhotoUrls = photosList
            .map { cleanImageUrl(it) }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()

        when {
            cleanedPhotoUrls.size > 1 -> {
                val mediaItems = cleanedPhotoUrls.take(10).mapIndexed { index, photoUrl ->
                    MediaItem(
                        type = "photo",
                        media = photoUrl,
                        caption = if (index == 0) fullCaption.take(1024) else ""
                    )
                }

                if (mediaItems.size < 2) {
                    logger.warn("Not enough photos for a media group (${mediaItems.size}). Attempting to send as single photo or text.")
                    if (mediaItems.isNotEmpty()) {
                        sendSinglePhoto(chatId, mediaItems.first().media, fullCaption, newsRequest.link)
                    } else {
                        sendAsTextMessage(chatId, fullCaption)
                    }
                    return
                }

                val requestBody = MediaGroupRequest(chat_id = chatId, media = mediaItems)
                logger.info("Attempting to send media group. Chat ID: $chatId, Photos: ${mediaItems.size}, First photo URL: ${mediaItems.firstOrNull()?.media}")

                try {
                    val response: HttpResponse = client.post("https://api.telegram.org/bot7806136583:AAFZTO7ufHr6CUasULRAkCosEz-43lnOXnQ/sendMediaGroup") {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody)
                    }
                    val responseBodyText = response.bodyAsText()

                    if (response.status == HttpStatusCode.OK) {
                        logger.info("Media group sent successfully. Response: $responseBodyText")
                    } else {
                        logger.error("Error sending media group. Status: ${response.status}, Response: $responseBodyText")
                        if (response.status == HttpStatusCode.BadRequest && responseBodyText.contains("WEBPAGE_MEDIA_EMPTY", ignoreCase = true)) {
                            logger.warn("Media group failed due to 'WEBPAGE_MEDIA_EMPTY'. Sending as text message instead.")
                            sendAsTextMessage(chatId, fullCaption)
                        } else {
                        }
                    }
                } catch (e: ClientRequestException) {
                    val errorResponseText = e.response.bodyAsText()
                    logger.error("ClientRequestException while sending media group. Status: ${e.response.status}, Response: $errorResponseText", e)
                    if (e.response.status == HttpStatusCode.BadRequest && errorResponseText.contains("WEBPAGE_MEDIA_EMPTY", ignoreCase = true)) {
                        logger.warn("Media group failed (ClientRequestException) due to 'WEBPAGE_MEDIA_EMPTY'. Sending as text message instead.")
                        sendAsTextMessage(chatId, fullCaption)
                    }
                } catch (e: Exception) {
                    logger.error("Generic exception while sending media group: ${e.message}", e)
                    logger.warn("Unexpected error sending media group. Sending as text message instead.")
                    sendAsTextMessage(chatId, fullCaption)
                }
            }
            cleanedPhotoUrls.size == 1 -> {
                sendSinglePhoto(chatId, cleanedPhotoUrls.first(), fullCaption, newsRequest.link)
            }
            else -> {
                logger.info("No photos to send for '${newsRequest.name}'. Sending as text message.")
                sendAsTextMessage(chatId, fullCaption)
            }
        }
    }


    private suspend fun sendSinglePhoto(chatId: String, photoUrl: String, caption: String, originalLink: String) {
        logger.info("Attempting to send single photo. Chat ID: $chatId, URL: $photoUrl")
        val requestBody = mapOf(
            "chat_id" to chatId,
            "photo" to photoUrl,
            "caption" to caption.take(1024) // Ограничение caption для фото
        )
        try {
            val response: HttpResponse = client.post("https://api.telegram.org/bot7806136583:AAFZTO7ufHr6CUasULRAkCosEz-43lnOXnQ/sendPhoto") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            val responseBodyText = response.bodyAsText()
            if (response.status == HttpStatusCode.OK) {
                logger.info("Single photo sent successfully. Response: $responseBodyText")
            } else {
                logger.error("Error sending single photo. Status: ${response.status}, Response: $responseBodyText")
                if (response.status == HttpStatusCode.BadRequest && responseBodyText.contains("WEBPAGE_MEDIA_EMPTY", ignoreCase = true)) {
                    logger.warn("Single photo failed due to 'WEBPAGE_MEDIA_EMPTY'. Sending as text message instead.")
                    sendAsTextMessage(chatId, caption)
                }
            }
        } catch (e: ClientRequestException) {
            val errorResponseText = e.response.bodyAsText()
            logger.error("ClientRequestException while sending single photo. Status: ${e.response.status}, Response: $errorResponseText", e)
            if (e.response.status == HttpStatusCode.BadRequest && errorResponseText.contains("WEBPAGE_MEDIA_EMPTY", ignoreCase = true)) {
                logger.warn("Single photo failed (ClientRequestException) due to 'WEBPAGE_MEDIA_EMPTY'. Sending as text message instead.")
                sendAsTextMessage(chatId, caption)
            }
        } catch (e: Exception) {
            logger.error("Generic exception while sending single photo: ${e.message}", e)
            logger.warn("Unexpected error sending single photo. Sending as text message instead.")
            sendAsTextMessage(chatId, caption)
        }
    }

    private suspend fun sendAsTextMessage(chatId: String, text: String) {
        logger.info("Sending as text message. Chat ID: $chatId")
        val requestBody = mapOf(
            "chat_id" to chatId,
            "text" to text.take(4096),
            "parse_mode" to "HTML"
        )
        try {
            val response: HttpResponse = client.post("https://api.telegram.org/bot7806136583:AAFZTO7ufHr6CUasULRAkCosEz-43lnOXnQ/sendMessage") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            val responseBodyText = response.bodyAsText()
            if (response.status == HttpStatusCode.OK) {
                logger.info("Text message sent successfully. Response: $responseBodyText")
            } else {
                logger.error("Error sending text message. Status: ${response.status}, Response: $responseBodyText")
            }
        } catch (e: ClientRequestException) {
            logger.error("ClientRequestException while sending text message. Status: ${e.response.status}, Response: ${e.response.bodyAsText()}", e)
        } catch (e: Exception) {
            logger.error("Generic exception while sending text message: ${e.message}", e)
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