plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))

    // Ktor 服务端
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Exposed ORM
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)

    // MySQL 连接池
    implementation(libs.mysql.connector)
    implementation(libs.hikaricp)

    // 日志
    implementation(libs.logback.classic)
}

application {
    mainClass.set("com.example.myziyubiaoqian.server.ApplicationKt")
}
