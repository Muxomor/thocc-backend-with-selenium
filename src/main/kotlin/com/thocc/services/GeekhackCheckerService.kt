package com.thocc.services

import com.thocc.models.NewsRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


class GeekhackCheckerService(private val newsService: NewsService, private val client: HttpClient) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private suspend fun getRssDataFromUrl(rssUrl: String): Document {
        return try {
            val response = client.get(rssUrl) {
                accept(ContentType.Application.Xml)
            }
            val responseBody = response.bodyAsText()
            Jsoup.parse(responseBody)
        } catch (e: Exception) {
            logger.error("failed to get document from rss by url: $rssUrl", e)
            throw e
        }
    }
    private suspend fun sendNewDataToTelegram(newsRequest: NewsRequest){
        val json = Json{ignoreUnknownKeys = true}
        val response = client.post("https://api.telegram.org/bot7806136583:AAFZTO7ufHr6CUasULRAkCosEz-43lnOXnQ/sendMessage") {
            parameter("chat_id", "@TopreThoc")
            parameter("text", "[GH] ${newsRequest.name} - ${newsRequest.link}")
        }
        val responseSerialized = json.decodeFromString<SendMessageResponse>(response.bodyAsText())
        if(responseSerialized.ok){
            client.post("https://api.telegram.org/bot7806136583:AAFZTO7ufHr6CUasULRAkCosEz-43lnOXnQ/pinChatMessage"){
                parameter("chat_id", "@TopreThoc")
                parameter("message_id",responseSerialized.result?.messageId)
                parameter("disable_notification", true)
            }
        }
    }

    private suspend fun geekhackRssChecker(rssUrl: String, boardType: String) {
        val rss = getRssDataFromUrl(rssUrl)
        for (rssItem in rss.select("rss>channel>item")) {
            if (checkItemTitle(rssItem.selectFirst("title")?.text())) {
                val news = NewsRequest(
                    name = rssItem.selectFirst("title")?.text()?.trim() ?: "Error",
                    originalName = rssItem.selectFirst("title")?.text()?.trim() ?: "Error",
                    link = rssItem.selectFirst("guid")?.ownText()?.trim() ?: "Error",
                    sourceId = 1,
                    timestamp = convertDateToOtherFormat(
                        rssItem.selectFirst("pubDate")?.text()?.trim() ?: "Error"
                    ),
                )
                logger.info("found new GH thread: ${rssItem.selectFirst("title")?.text()?.trim()}")
                sendNewDataToTelegram(news)
                newsService.createNews(news)
            }
        }
    }

    suspend fun checkGeekhackFeeds() {
        geekhackRssChecker("https://geekhack.org/index.php?board=132.0;action=.xml;type=rss", "IC")
        geekhackRssChecker("https://geekhack.org/index.php?board=70.0;action=.xml;type=rss", "GB")
    }

    private fun checkItemTitle(title: String?): Boolean {
        if (title != null) {
            if (title.startsWith("Re:")) {
                return false
            } else {
                if (newsService.findNewsByName(title, 1) != null) {
                    return false
                } else {
                    return true;
                }
            }
        } else {
            logger.info("Title is null! Check the data")
            return false
        }
    }

    private fun convertDateToOtherFormat(dateTime: String): String {
        val inputFormat = DateTimeFormatter.RFC_1123_DATE_TIME
        val parsedDate = ZonedDateTime.parse(dateTime, inputFormat)
        val targetedTimeZone = parsedDate.withZoneSameInstant(ZoneId.of("UTC+3"))
        val outputFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'UTC+3'")
        return targetedTimeZone.format(outputFormat)
    }
}

@Serializable
data class MessageResult(
    @SerialName("message_id")
    val messageId: Int,
    val chat: Chat,
    val text: String? = null
)
@Serializable
data class SendMessageResponse(
    val ok: Boolean,
    val result: MessageResult? = null
)
@Serializable
data class Chat(
    val id: Long,
    val type: String
)