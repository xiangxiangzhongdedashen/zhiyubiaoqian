package com.example.myziyubiaoqian.network

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

// 物品请求/响应 DTO（与服务器一致）
@Serializable
data class ItemDto(
    val id: Int = 0,
    val tagId: String,
    val name: String,
    val description: String = "",
    val location: String = "",
    val category: String = "",
    val syncVersion: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
data class ItemCreateRequest(
    val tagId: String,
    val name: String,
    val description: String = "",
    val location: String = "",
    val category: String = ""
)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * 智语标签服务器 API 客户端
 */
object ApiClient {
    private var baseUrl = "http://192.168.233.129:8080"

    private val client = HttpClient(OkHttp)

    fun init(url: String) {
        baseUrl = url.trimEnd('/')
    }

    /** 健康检查 */
    suspend fun health(): String {
        val resp = client.get("$baseUrl/health")
        return resp.bodyAsText()
    }

    /** 获取所有物品 */
    suspend fun listItems(): List<ItemDto> {
        val resp = client.get("$baseUrl/items")
        val text = resp.bodyAsText()
        return json.decodeFromString(ListSerializer(ItemDto.serializer()), text)
    }

    /** 按 tagId 查询 */
    suspend fun getItem(tagId: String): ItemDto? {
        return try {
            val resp = client.get("$baseUrl/items/$tagId")
            json.decodeFromString(ItemDto.serializer(), resp.bodyAsText())
        } catch (e: Exception) {
            null
        }
    }

    /** 新增物品 */
    suspend fun createItem(req: ItemCreateRequest): ItemDto {
        val body = json.encodeToString(req)
        val resp = client.post("$baseUrl/items") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return json.decodeFromString(ItemDto.serializer(), resp.bodyAsText())
    }

    /** 更新物品 */
    suspend fun updateItem(tagId: String, req: ItemCreateRequest): ItemDto? {
        return try {
            val body = json.encodeToString(req)
            val resp = client.put("$baseUrl/items/$tagId") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            json.decodeFromString(ItemDto.serializer(), resp.bodyAsText())
        } catch (e: Exception) {
            null
        }
    }

    /** 删除物品 */
    suspend fun deleteItem(tagId: String): Boolean {
        return try {
            client.delete("$baseUrl/items/$tagId")
            true
        } catch (e: Exception) {
            false
        }
    }
}
