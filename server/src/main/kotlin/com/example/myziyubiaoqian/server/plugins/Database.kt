package com.example.myziyubiaoqian.server.plugins

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

// 数据库表定义
object Items : Table("items") {
    val id = integer("id").autoIncrement()
    val tagId = varchar("tag_id", 32).uniqueIndex()
    val name = varchar("name", 255)
    val description = text("description")
    val location = varchar("location", 255).default("")
    val category = varchar("category", 50).default("")
    val syncVersion = long("sync_version").default(0)
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

fun Application.configureDatabase() {
    val config = HikariConfig().apply {
        jdbcUrl = "jdbc:mysql://localhost:3306/zhiyubiaoqian?createDatabaseIfNotExist=true"
        username = "root"
        password = "bubailan"
        driverClassName = "com.mysql.cj.jdbc.Driver"
        maximumPoolSize = 10
    }

    val dataSource = HikariDataSource(config)
    Database.Companion.connect(dataSource)

    transaction {
        SchemaUtils.create(Items)
    }
}
