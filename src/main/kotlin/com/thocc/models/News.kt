package com.thocc.models

import kotlinx.serialization.Serializable
import java.sql.Timestamp


@Serializable
data class News(
    val name: String,
    var originalName: String,
    var link: String,
    var sourceId: Int,
    var timestamp: String,
)
