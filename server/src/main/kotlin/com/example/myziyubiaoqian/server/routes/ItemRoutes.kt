package com.example.myziyubiaoqian.server.routes

import com.example.myziyubiaoqian.server.plugins.Items
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class ItemRequest(
    val tagId: String,
    val name: String,
    val description: String = "",
    val location: String = "",
    val category: String = ""
)

@Serializable
data class ItemResponse(
    val id: Int,
    val tagId: String,
    val name: String,
    val description: String,
    val location: String,
    val category: String,
    val syncVersion: Long,
    val updatedAt: Long
)

private val json = Json { prettyPrint = true }

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("智语标签服务端运行中 ✅")
        }

        get("/health") {
            call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
        }

        // 获取所有物品
        get("/items") {
            val items = transaction {
                Items.selectAll().orderBy(Items.id).map { it.toItemResponse() }
            }
            call.respondText(json.encodeToString(items), ContentType.Application.Json)
        }

        // 按 tagId 查询单个物品
        get("/items/{tagId}") {
            val tagId = call.parameters["tagId"] ?: ""
            val item = transaction {
                Items.selectAll().where { Items.tagId eq tagId }.singleOrNull()?.toItemResponse()
            }
            if (item != null) {
                call.respondText(json.encodeToString(item), ContentType.Application.Json)
            } else {
                call.respondText("""{"error":"未找到物品"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
            }
        }

        // 新增物品
        post("/items") {
            val req = call.receive<ItemRequest>()
            val now = System.currentTimeMillis()
            val itemId = transaction {
                Items.insert {
                    it[tagId] = req.tagId
                    it[name] = req.name
                    it[description] = req.description
                    it[location] = req.location
                    it[category] = req.category
                    it[syncVersion] = 1
                    it[updatedAt] = now
                } get Items.id
            }
            val item = transaction {
                Items.selectAll().where { Items.id eq itemId }.single().toItemResponse()
            }
            call.respondText(json.encodeToString(item), ContentType.Application.Json, HttpStatusCode.Created)
        }

        // 更新物品
        put("/items/{tagId}") {
            val tagId = call.parameters["tagId"] ?: ""
            val req = call.receive<ItemRequest>()
            val now = System.currentTimeMillis()
            // 先查当前版本号
            val currentVersion = transaction {
                Items.selectAll().where { Items.tagId eq tagId }.singleOrNull()?.get(Items.syncVersion) ?: 0L
            }
            if (currentVersion == 0L) {
                call.respondText("""{"error":"未找到物品"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                return@put
            }
            val newVersion = currentVersion + 1L
            val updated = transaction {
                Items.update({ Items.tagId eq tagId }) {
                    it[name] = req.name
                    it[description] = req.description
                    it[location] = req.location
                    it[category] = req.category
                    it[Items.syncVersion] = newVersion
                    it[updatedAt] = now
                }
            }
            if (updated > 0) {
                val item = transaction {
                    Items.selectAll().where { Items.tagId eq tagId }.single().toItemResponse()
                }
                call.respondText(json.encodeToString(item), ContentType.Application.Json)
            } else {
                call.respondText("""{"error":"未找到物品"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
            }
        }

        // 删除物品
        delete("/items/{tagId}") {
            val tagId = call.parameters["tagId"] ?: ""
            val deleted = transaction {
                exec("DELETE FROM items WHERE tag_id = '$tagId'") as Int
            }
            if (deleted > 0) {
                call.respondText("""{"ok":true}""", ContentType.Application.Json)
            } else {
                call.respondText("""{"error":"未找到物品"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
            }
        }
    }
}

private fun ResultRow.toItemResponse() = ItemResponse(
    id = this[Items.id],
    tagId = this[Items.tagId],
    name = this[Items.name],
    description = this[Items.description],
    location = this[Items.location],
    category = this[Items.category],
    syncVersion = this[Items.syncVersion],
    updatedAt = this[Items.updatedAt]
)
