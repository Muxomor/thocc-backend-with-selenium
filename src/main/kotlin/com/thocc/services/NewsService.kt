package com.thocc.services

import com.thocc.database.News
import com.thocc.database.NewsSourceEntity
import com.thocc.database.Newses
import com.thocc.models.NewsRequest
import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.eq
import org.ktorm.entity.add
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.toSet
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class NewsService(private val db: Database) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    fun createNews(newsRequest: NewsRequest): Boolean {
        val newNews = News {
            name = if (newsRequest.sourceId == 1) {
                "[GH]" + newsRequest.name
            } else if (newsRequest.sourceId == 2) {
                "[ZF]" + newsRequest.name
            } else {
                "[Other]" + newsRequest.name
            }
            originalName = newsRequest.originalName
            timestamp = newsRequest.timestamp
            link = newsRequest.link
            sourceId = NewsSourceEntity {
                id = newsRequest.sourceId
            }
        }
        val affectedRecordsNumber = db.sequenceOf(Newses).add(newNews)
        logger.info("Check DB! News: ${newNews.name} must appeared")
        return affectedRecordsNumber == 1
    }

    fun selectAllNews(): Set<News> = db.sequenceOf(Newses).toSet()
    fun findNewsByName(name: String, sourceId: Int): News? =
        db.sequenceOf(Newses).find { x -> x.originalName eq name and (x.sourceId eq sourceId) }

    fun selectNewsById(id: Int): News? = db.sequenceOf(Newses).find { x -> x.id eq id }

    fun findNewsByLink(link: String): News? = db.sequenceOf(Newses).find { x -> x.link eq link }
}