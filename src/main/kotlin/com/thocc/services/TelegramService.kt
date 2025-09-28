package com.thocc.services

import com.thocc.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory


class TelegramService(
    private val client: HttpClient,
    private val config: TelegramConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val baseUrl = "https://api.telegram.org/bot${config.botToken}"
    companion object {
        private const val MAX_RETRIES = 5
        private const val RETRY_DELAY_MS = 60L * 5000L

        private val RETRYABLE_ERROR_MESSAGES = listOf(
            "WEBPAGE_MEDIA_EMPTY", "WEBPAGE_CURL_FAILED", "Failed to get HTTP URL content"
        )
    }
    private suspend fun withRetries(apiCall: suspend () -> HttpResponse): HttpResponse? {
        for (attempt in 1..MAX_RETRIES) {
            try {
                val response = apiCall()

                if (response.status.isSuccess()) {
                    return response
                }

                val responseBody = response.bodyAsText()
                val isRetryable = response.status.value >= 500 ||
                        RETRYABLE_ERROR_MESSAGES.any { responseBody.contains(it, ignoreCase = true) }

                if (isRetryable) {
                    logger.warn("Attempt $attempt/$MAX_RETRIES failed with retryable error. Response: $responseBody. Retrying in ${RETRY_DELAY_MS / 1000}s...")
                    if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS)
                } else {
                    logger.error("Request failed with non-retryable error. Response: $responseBody")
                    return response
                }

            } catch (e: HttpRequestTimeoutException) {
                logger.warn("Attempt $attempt/$MAX_RETRIES failed with timeout. Retrying...", e)
                if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS)
            } catch (e: Exception) {
                logger.error("Request failed with unexpected exception. Aborting retries.", e)
                return null
            }
        }
        logger.error("All $MAX_RETRIES attempts failed.")
        return null
    }

    suspend fun sendTextMessage(text: String): Int? {
        val response = withRetries {
            client.post("$baseUrl/sendMessage") {
                parameter("chat_id", config.chatId)
                parameter("text", text.take(4096))
            }
        }
        return if (response?.status?.isSuccess() == true) {
            response.body<TelegramApiResponse<TelegramMessageResult>>().result?.messageId
        } else {
            null
        }
    }

    suspend fun sendMediaGroup(caption: String, photos: List<String>): Boolean {
        if (photos.size < 2 || photos.size > 10) return false
        val mediaItems = photos.mapIndexed { index, url ->
            MediaItem(media = url, caption = if (index == 0) caption.take(1024) else null)
        }
        val requestBody = SendMediaGroupRequest(
            chatId = config.chatId,
            media = mediaItems
        )
        val response = withRetries {
            client.post("$baseUrl/sendMediaGroup") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
        }
        return response?.status?.isSuccess() == true
    }


    suspend fun sendSinglePhoto(caption: String, photoUrl: String): Boolean {
        val response = withRetries {
            client.post("$baseUrl/sendPhoto") {
                contentType(ContentType.Application.Json)
                setBody(mapOf(
                    "chat_id" to config.chatId,
                    "photo" to photoUrl,
                    "caption" to caption.take(1024)
                ))
            }
        }
        return response?.status?.isSuccess() == true
    }

    suspend fun pinChatMessage(messageId: Int) {
        withRetries {
            client.post("$baseUrl/pinChatMessage") {
                parameter("chat_id", config.chatId)
                parameter("message_id", messageId)
                parameter("disable_notification", true)
            }
        }
    }
}