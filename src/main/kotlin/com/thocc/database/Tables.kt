package com.thocc.database

import org.ktorm.entity.Entity
import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.text
import org.ktorm.schema.timestamp
import org.ktorm.schema.varchar
import java.sql.Timestamp

//i dont even know why this shit looks like that, random guy in guide said its correct
interface News : Entity<News> {
    companion object : Entity.Factory<News>()
    var id:Int?
    var name: String
    var originalName: String
    var link: String
    var sourceId: NewsSourceEntity
    var timestamp: String
}
object Newses : Table<News>("news"){
    val id = int("id").primaryKey().bindTo(News::id)
    val name = text("name").bindTo(News::name)
    val originalName = text("originalname").bindTo(News::originalName)
    val sourceId = int("source_id").references(NewsSources){it.sourceId}
    val link = text("link").bindTo(News::link)
    val timestamp = text("timestamp").bindTo(News::timestamp)
}
interface NewsSourceEntity : Entity<NewsSourceEntity> {
    companion object : Entity.Factory<NewsSourceEntity>()

    var id: Int
    var name: String
}
object NewsSources : Table<NewsSourceEntity>("newssource") {
    val id = int("id").primaryKey().bindTo(NewsSourceEntity::id)
    val name = varchar("name").bindTo(NewsSourceEntity::name)
}
