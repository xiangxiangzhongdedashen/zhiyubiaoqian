package com.example.myziyubiaoqian.data

import kotlinx.coroutines.flow.Flow

/**
 * 物品数据仓库——UI 层与数据库之间的中介。
 *
 * 视障用户端：
 * - 只读：[getAll], [getByTagId], [getByTagIdOnce]
 * - 写操作仅开放给未来的云端同步层（internal 可见性）
 */
class ItemRepository(private val dao: ItemDao) {

    /** 获取全部物品（响应式 Flow） */
    fun getAll(): Flow<List<Item>> = dao.getAll()

    /** 按标签 ID 观察物品 */
    fun getByTagId(tagId: String): Flow<Item?> = dao.getByTagId(tagId)

    /** 按标签 ID 一次性查询（NFC 触碰时使用） */
    suspend fun getByTagIdOnce(tagId: String): Item? = dao.getByTagIdOnce(tagId)

    // ── 写操作 —— 当前用于标签注册/编辑，未来云端同步层也会调用 ──

    suspend fun upsert(item: Item) = dao.upsert(item)
    suspend fun upsertAll(items: List<Item>) = dao.upsertAll(items)
    suspend fun delete(item: Item) = dao.delete(item)
}
