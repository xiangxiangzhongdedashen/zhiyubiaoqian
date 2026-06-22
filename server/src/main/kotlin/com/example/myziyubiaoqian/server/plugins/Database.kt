package com.example.myziyubiaoqian.server.plugins

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

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
        // 连接测试
        exec("SELECT 1")
    }
}
