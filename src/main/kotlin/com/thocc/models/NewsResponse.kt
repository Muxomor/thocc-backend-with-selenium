package com.thocc.models

import kotlinx.serialization.Serializable
import java.sql.Timestamp

@Serializable
data class NewsResponse(
    val name: String,
    val originalName: String,
    val link: String,
    val timestamp: String,
)

@Serializable
data class NewsRequest(
    val name: String,
    val originalName: String,
    val link: String,
    val sourceId: Int,
    val timestamp: String,
)

@Serializable
data class ErrorResponse(
    val message: String,
)