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
// Импорты для корутин
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

private const val JOB_INTERVAL_1_H = 1 * 60 * 60 * 1000L // 1 час для основной проверки ZF

private const val TELEGRAM_BOT_TOKEN = "7806136583:AAFZTO7ufHr6CUasULRAkCosEz-43lnOXnQ"
private const val TELEGRAM_API_BASE_URL = "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN"
private const val TELEGRAM_CHAT_ID = "@TopreThoc"

class ZFrontierCheckerService(
    private val newsService: NewsService,
    private val client: HttpClient,
    private val browser: FirefoxDriver,
) {
    companion object {
        const val CACHE_CLEAR_INTERVAL_HOURS = 4 * 24

        private val TELEGRAM_RETRYABLE_MEDIA_FETCH_ERRORS = listOf(
            "WEBPAGE_MEDIA_EMPTY",
            "WEBPAGE_CURL_FAILED",
            "Failed to get HTTP URL content"
        )

        private const val TELEGRAM_SEND_RETRY_INTERVAL_MINUTES = 15L // Интервал повтора для сообщения
        private const val TELEGRAM_SEND_RETRY_INTERVAL_MS = TELEGRAM_SEND_RETRY_INTERVAL_MINUTES * 60 * 1000L
        private const val MAX_TELEGRAM_RETRY_ATTEMPTS = 5

        // Новая константа: как часто проверять очередь на наличие сообщений для повтора
        private const val RETRY_PROCESSOR_POLL_INTERVAL_MINUTES = 1L // Проверять каждую минуту
        private const val RETRY_PROCESSOR_POLL_INTERVAL_MS = RETRY_PROCESSOR_POLL_INTERVAL_MINUTES * 60 * 1000L
    }

    private enum class TelegramSendType { SINGLE_PHOTO, MEDIA_GROUP }

    private data class FailedTelegramMessage(
        val chatId: String,
        val newsRequest: NewsRequest,
        val photosList: List<String>,
        val type: TelegramSendType,
        val originalCaption: String,
        var lastAttemptTimestamp: Long,
        var attemptCount: Int
    )

    private val failedMessagesQueue = ConcurrentLinkedQueue<FailedTelegramMessage>()
    private val cache = ConcurrentHashMap.newKeySet<String>()
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    // ... (getDocumentFromURL, zFrontierChecker, checkNameInCacheAndDB, checkLinkInDB, convertRawTimeToDateTime, cleanImageUrl, translateString без изменений) ...
    private suspend fun getDocumentFromURL(url: String): Document {
        try {
            logger.info("zf checker page loading...")
            browser.get(url)
            delay(60000L)
            val response = browser.pageSource
            logger.info("zf page loaded!")
            logger.info("navigating to google.com to potentially release resources or change IP context")
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
            postNewsToTelegramm(request, photoLinks)
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

    private fun convertRawTimeToDateTime(input: String): String {
        val moscowZone = ZoneId.of("Europe/Moscow")
        val currentDateTime = ZonedDateTime.now(moscowZone)
        return currentDateTime.format(
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm 'UTC+3'").withZone(moscowZone)
        )
    }

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
            //logger.info(response.status.value.toString())
            logger.info(response.toString())
            json.decodeFromString<DestinationTextResponse>(response.bodyAsText()).destinationText
        } catch (e: Exception) {
            logger.error("Failed to translate string: ${e.message}")
            nameToTranslate
        }
    }
    suspend fun startChecker() = coroutineScope {
        var lastCacheClearTime = System.currentTimeMillis()
        launch {
            logger.info("Telegram retry processor coroutine started. Will check queue every ${RETRY_PROCESSOR_POLL_INTERVAL_MINUTES} minute(s).")
            while (true) {
                try {
                    processFailedTelegramMessages()
                } catch (e: Exception) {
                    logger.error("Error in Telegram retry processor: ${e.message}", e)
                }
                delay(RETRY_PROCESSOR_POLL_INTERVAL_MS) // Опрашиваем очередь с коротким интервалом
            }
        }

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
            logger.info("Main ZF check job done, waiting for ${JOB_INTERVAL_1_H / (60*1000)} minute(s). Pending retries in queue: ${failedMessagesQueue.size}")
            delay(JOB_INTERVAL_1_H)
        }
    }

    private fun isRetryableTelegramError(responseStatus: HttpStatusCode, responseBodyText: String): Boolean {
        if (responseStatus == HttpStatusCode.BadRequest || responseStatus.value >= 500) {
            TELEGRAM_RETRYABLE_MEDIA_FETCH_ERRORS.forEach { errorMsg ->
                if (responseBodyText.contains(errorMsg, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    private fun queueFailedMessage(
        chatId: String,
        newsRequest: NewsRequest,
        photosList: List<String>,
        type: TelegramSendType,
        originalCaption: String
    ) {
        val failedMessage = FailedTelegramMessage(
            chatId = chatId,
            newsRequest = newsRequest,
            photosList = photosList,
            type = type,
            originalCaption = originalCaption,
            lastAttemptTimestamp = System.currentTimeMillis(),
            attemptCount = 1
        )
        failedMessagesQueue.add(failedMessage)
        logger.info("Message queued for retry. Type: $type, Caption: ${originalCaption.take(50)}..., Attempts: 1")
    }

    private suspend fun postNewsToTelegramm(newsRequest: NewsRequest, photosList: List<String>) {
        val baseCaptionText = "[ZF] ${newsRequest.name}"
        val fullCaption = "$baseCaptionText - ${newsRequest.link}"

        val cleanedPhotoUrls = photosList
            .map { cleanImageUrl(it) }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
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
                    logger.warn("Not enough photos for a media group (${mediaItems.size}) after cleaning/filtering. Attempting to send as single photo or text.")
                    if (mediaItems.isNotEmpty()) {
                        sendSinglePhoto(TELEGRAM_CHAT_ID, mediaItems.first().media, fullCaption, newsRequest, listOf(mediaItems.first().media))
                    } else {
                        sendAsTextMessage(TELEGRAM_CHAT_ID, fullCaption)
                    }
                    return
                }

                val requestBody = MediaGroupRequest(chat_id = TELEGRAM_CHAT_ID, media = mediaItems)
                logger.info("Attempting to send media group. Chat ID: $TELEGRAM_CHAT_ID, Photos: ${mediaItems.size}, First photo URL: ${mediaItems.firstOrNull()?.media}")

                try {
                    val response: HttpResponse = client.post("$TELEGRAM_API_BASE_URL/sendMediaGroup") {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody)
                    }
                    val responseBodyText = response.bodyAsText()

                    if (response.status == HttpStatusCode.OK) {
                        logger.info("Media group sent successfully. Response: $responseBodyText")
                    } else {
                        logger.error("Error sending media group. Status: ${response.status}, Response: $responseBodyText")
                        if (isRetryableTelegramError(response.status, responseBodyText)) {
                            logger.warn("Media group failed with retryable error, queuing. Caption: ${fullCaption.take(50)}")
                            queueFailedMessage(TELEGRAM_CHAT_ID, newsRequest, cleanedPhotoUrls, TelegramSendType.MEDIA_GROUP, fullCaption)
                        } else {
                            logger.warn("Media group failed with non-retryable error. Sending as text message instead. Status: ${response.status}")
                            sendAsTextMessage(TELEGRAM_CHAT_ID, fullCaption)
                        }
                    }
                } catch (e: ClientRequestException) {
                    val errorResponseText = e.response.bodyAsText()
                    logger.error("ClientRequestException while sending media group. Status: ${e.response.status}, Response: $errorResponseText", e)
                    if (isRetryableTelegramError(e.response.status, errorResponseText)) {
                        logger.warn("Media group (ClientRequestException) failed with retryable error, queuing. Caption: ${fullCaption.take(50)}")
                        queueFailedMessage(TELEGRAM_CHAT_ID, newsRequest, cleanedPhotoUrls, TelegramSendType.MEDIA_GROUP, fullCaption)
                    } else {
                        logger.warn("Media group (ClientRequestException) failed with non-retryable error. Sending as text message instead.")
                        sendAsTextMessage(TELEGRAM_CHAT_ID, fullCaption)
                    }
                } catch (e: Exception) {
                    logger.error("Generic exception while sending media group: ${e.message}", e)
                    logger.warn("Unexpected error sending media group. Sending as text message instead.")
                    sendAsTextMessage(TELEGRAM_CHAT_ID, fullCaption)
                }
            }
            cleanedPhotoUrls.size == 1 -> {
                sendSinglePhoto(TELEGRAM_CHAT_ID, cleanedPhotoUrls.first(), fullCaption, newsRequest, cleanedPhotoUrls)
            }
            else -> {
                logger.info("No photos to send for '${newsRequest.name}'. Sending as text message.")
                sendAsTextMessage(TELEGRAM_CHAT_ID, fullCaption)
            }
        }
    }
    private suspend fun sendSinglePhoto(chatId: String, photoUrl: String, caption: String, newsRequest: NewsRequest, originalPhotosList: List<String>) {
        logger.info("Attempting to send single photo. Chat ID: $chatId, URL: $photoUrl")
        val requestBody = mapOf(
            "chat_id" to chatId,
            "photo" to photoUrl,
            "caption" to caption.take(1024)
        )
        try {
            val response: HttpResponse = client.post("$TELEGRAM_API_BASE_URL/sendPhoto") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            val responseBodyText = response.bodyAsText()
            if (response.status == HttpStatusCode.OK) {
                logger.info("Single photo sent successfully. Response: $responseBodyText")
            } else {
                logger.error("Error sending single photo. Status: ${response.status}, Response: $responseBodyText")
                if (isRetryableTelegramError(response.status, responseBodyText)) {
                    logger.warn("Single photo failed with retryable error, queuing. Caption: ${caption.take(50)}")
                    queueFailedMessage(chatId, newsRequest, originalPhotosList, TelegramSendType.SINGLE_PHOTO, caption)
                } else {
                    logger.warn("Single photo failed with non-retryable error. Fallback to text. Status: ${response.status}")
                    sendAsTextMessage(chatId, caption)
                }
            }
        } catch (e: ClientRequestException) {
            val errorResponseText = e.response.bodyAsText()
            logger.error("ClientRequestException while sending single photo. Status: ${e.response.status}, Response: $errorResponseText", e)
            if (isRetryableTelegramError(e.response.status, errorResponseText)) {
                logger.warn("Single photo (ClientRequestException) failed with retryable error, queuing. Caption: ${caption.take(50)}")
                queueFailedMessage(chatId, newsRequest, originalPhotosList, TelegramSendType.SINGLE_PHOTO, caption)
            } else {
                logger.warn("Single photo (ClientRequestException) failed with non-retryable error. Fallback to text.")
                sendAsTextMessage(chatId, caption)
            }
        } catch (e: Exception) {
            logger.error("Generic exception while sending single photo: ${e.message}", e)
            logger.warn("Unexpected error sending single photo. Sending as text message instead.")
            sendAsTextMessage(chatId, caption)
        }
    }

    private suspend fun sendSinglePhotoApiCall(chatId: String, photoUrl: String, caption: String): HttpResponse {
        val requestBody = mapOf(
            "chat_id" to chatId,
            "photo" to photoUrl,
            "caption" to caption.take(1024)
        )
        return client.post("$TELEGRAM_API_BASE_URL/sendPhoto") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
    }

    private suspend fun sendMediaGroupApiCall(chatId: String, mediaItems: List<MediaItem>): HttpResponse {
        val requestBody = MediaGroupRequest(chat_id = chatId, media = mediaItems)
        return client.post("$TELEGRAM_API_BASE_URL/sendMediaGroup") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
    }
    private suspend fun processFailedTelegramMessages() {
        if (failedMessagesQueue.isEmpty()) {
            return
        }
        // На этот раз не выводим лог о количестве здесь, т.к. функция будет вызываться часто.
        // Логирование будет при фактической попытке повтора.

        val processingQueue = ConcurrentLinkedQueue(failedMessagesQueue)
        failedMessagesQueue.clear()

        var messageToProcess = processingQueue.poll()
        while (messageToProcess != null) {
            if (System.currentTimeMillis() - messageToProcess.lastAttemptTimestamp >= TELEGRAM_SEND_RETRY_INTERVAL_MS) {
                logger.info("Retrying message. Type: ${messageToProcess.type}, Caption: ${messageToProcess.originalCaption.take(50)}..., Attempt: ${messageToProcess.attemptCount + 1} of $MAX_TELEGRAM_RETRY_ATTEMPTS")
                var success = false
                var isRetryableFailureAfterRetry = false
                var attemptFailed = true

                try {
                    val response: HttpResponse
                    when (messageToProcess.type) {
                        TelegramSendType.SINGLE_PHOTO -> {
                            response = sendSinglePhotoApiCall(messageToProcess.chatId, messageToProcess.photosList.first(), messageToProcess.originalCaption)
                        }
                        TelegramSendType.MEDIA_GROUP -> {
                            val mediaItems = messageToProcess.photosList.take(10).mapIndexed { index, photoUrl ->
                                MediaItem(
                                    type = "photo",
                                    media = photoUrl,
                                    caption = if (index == 0) messageToProcess.originalCaption.take(1024) else ""
                                )
                            }
                            if (mediaItems.size < 2 && messageToProcess.photosList.isNotEmpty()) {
                                logger.warn("Media group retry for caption '${messageToProcess.originalCaption.take(50)}' has only one eligible photo. Attempting as single photo.")
                                response = sendSinglePhotoApiCall(messageToProcess.chatId, messageToProcess.photosList.first(), messageToProcess.originalCaption)
                            } else if (mediaItems.size >=2) {
                                response = sendMediaGroupApiCall(messageToProcess.chatId, mediaItems)
                            } else {
                                logger.error("Cannot retry media group for caption '${messageToProcess.originalCaption.take(50)}', no valid media items. Sending as text.")
                                sendAsTextMessage(messageToProcess.chatId, messageToProcess.originalCaption)
                                success = true
                                attemptFailed = false
                                messageToProcess = processingQueue.poll()
                                continue
                            }
                        }
                    }

                    val responseBodyText = response.bodyAsText()
                    if (response.status == HttpStatusCode.OK) {
                        success = true
                        logger.info("Successfully resent ${messageToProcess.type}. Caption: ${messageToProcess.originalCaption.take(50)}")
                    } else {
                        logger.error("Error resending ${messageToProcess.type}. Status: ${response.status}, Response: $responseBodyText")
                        if (isRetryableTelegramError(response.status, responseBodyText)) {
                            isRetryableFailureAfterRetry = true
                        }
                    }
                } catch (e: ClientRequestException) {
                    val errorStatus = e.response.status
                    val errorBody = e.response.bodyAsText()
                    logger.error("ClientRequestException during retry. Type: ${messageToProcess.type}, Status: $errorStatus, Response: $errorBody", e)
                    if (isRetryableTelegramError(errorStatus, errorBody)) {
                        isRetryableFailureAfterRetry = true
                    }
                } catch (e: Exception) {
                    logger.error("Generic exception during retry. Type: ${messageToProcess.type}, Caption: ${messageToProcess.originalCaption.take(50)}...: ${e.message}", e)
                    isRetryableFailureAfterRetry = false
                }

                if (attemptFailed) {
                    if (!success) {
                        messageToProcess.attemptCount++
                        messageToProcess.lastAttemptTimestamp = System.currentTimeMillis()
                        if (isRetryableFailureAfterRetry && messageToProcess.attemptCount < MAX_TELEGRAM_RETRY_ATTEMPTS) {
                            failedMessagesQueue.add(messageToProcess)
                            logger.warn("Message re-queued after failed attempt ${messageToProcess.attemptCount}. Caption: ${messageToProcess.originalCaption.take(50)}")
                        } else {
                            if (!isRetryableFailureAfterRetry) {
                                logger.warn("Non-retryable error during retry for caption '${messageToProcess.originalCaption.take(50)}' or max attempts reached without retryable error. Sending as text.")
                            } else {
                                logger.warn("Max retries (${MAX_TELEGRAM_RETRY_ATTEMPTS}) reached for caption '${messageToProcess.originalCaption.take(50)}'. Sending as text.")
                            }
                            sendAsTextMessage(messageToProcess.chatId, messageToProcess.originalCaption)
                        }
                    }
                }
            } else {
                failedMessagesQueue.add(messageToProcess)
            }
            messageToProcess = processingQueue.poll()
        }
    }
    private suspend fun sendAsTextMessage(chatId: String, text: String) {
        logger.info("Sending as text message. Chat ID: $chatId, Text: ${text.take(100)}...")
        val requestBody = mapOf(
            "chat_id" to chatId,
            "text" to text.take(4096),
            "parse_mode" to "HTML"
        )
        try {
            val response: HttpResponse = client.post("$TELEGRAM_API_BASE_URL/sendMessage") {
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