package com.thocc.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelegramMessageResult(
    @SerialName("message_id") val messageId: Int
)

@Serializable
data class TelegramApiResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    val description: String? = null
)

@Serializable
data class MediaItem(
    val type: String = "photo",
    val media: String,
    val caption: String? = null
)
@Serializable
data class SendMediaGroupRequest(
    @SerialName("chat_id")
    val chatId: String,
    val media: List<MediaItem>
)
data class TelegramConfig(
    val botToken: String,
    val chatId: String
)