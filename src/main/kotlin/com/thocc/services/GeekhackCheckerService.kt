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
import org.jsoup.parser.Parser


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

    private suspend fun geekhackRssChecker(rssUrl: String, sourceId: Int) {
        val rss = getRssDataFromUrl(rssUrl)
        for (item in rss.select("rss>channel>item")) {
            val rawTitle = item.selectFirst("title")?.text() ?: continue
            val title = cleanTitle(rawTitle) ?: continue

            val guid = item.selectFirst("guid")?.ownText()
                ?: item.selectFirst("link")?.text()
                ?: continue

            if (newsService.findNewsByName(title, sourceId) != null ||
                newsService.findNewsByLink(guid)         != null
            ) {
                logger.debug("skip duplicate: $title / $guid")
                continue
            }

            val pub = item.selectFirst("pubDate")?.text()?.trim() ?: ""
            val timestamp = convertDateToOtherFormat(pub)

            val news = NewsRequest(
                name = title,
                originalName = rawTitle.trim(),
                link = guid.trim(),
                sourceId = sourceId,
                timestamp = timestamp
            )

            logger.info("new post: $title ($guid)")
            sendNewDataToTelegram(news)
            newsService.createNews(news)
        }
    }

    suspend fun checkGeekhackFeeds() {
        geekhackRssChecker("https://geekhack.org/index.php?board=132.0;action=.xml;type=rss;sa=news", 1)
        geekhackRssChecker("https://geekhack.org/index.php?board=70.0;action=.xml;type=rss;sa=news", 1)
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

    private fun cleanTitle(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        val unescaped = raw
            .replace("<![CDATA[", "")
            .replace("]]>", "")
            .let { Parser.unescapeEntities(it, false) }
            .trim()

        val collapsed = unescaped.replace(Regex("]]+"), "]")

        if (collapsed.startsWith("Re:", ignoreCase = true)) return null

        return collapsed
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