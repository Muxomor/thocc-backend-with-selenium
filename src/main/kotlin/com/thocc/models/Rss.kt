package com.thocc.models


import kotlinx.serialization.Serializable

@Serializable
data class Rss(
    val channel: channel
)

@Serializable
data class channel(
    val title: String,
    val link: String,
    val description: String,
    val item: List<Item>
)



@Serializable
data class Item(
    val title: String,
    val link: String,
    val description: String,
    val category: String,
    val comments: String,
    val pubDate: String,
    val guid: String
)