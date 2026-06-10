package com.example.myziyubiaoqian.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 物品数据访问接口。
 *
 * 视障用户端仅使用只读方法（Flow / suspend getByTagIdOnce）。
 * upsert / delete 留给未来云端同步层调用。
 */
@Dao
interface ItemDao {

    /** 观察全部物品（按分类→名称排序），数据变化时自动更新 UI */
    @Query("SELECT * FROM items ORDER BY category, name")
    fun getAll(): Flow<List<Item>>

    /** 按标签 ID 观察单个物品 */
    @Query("SELECT * FROM items WHERE tagId = :tagId")
    fun getByTagId(tagId: String): Flow<Item?>

    /** 按标签 ID 一次性查询（NFC 触碰时使用） */
    @Query("SELECT * FROM items WHERE tagId = :tagId")
    suspend fun getByTagIdOnce(tagId: String): Item?

    // ── 以下方法供未来云端同步层 / 家属端使用，用户界面不暴露 ──

    /** 插入或替换（REPLACE 策略适合云端全量同步） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Item)

    /** 批量插入或替换 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Item>)

    /** 删除物品 */
    @Delete
    suspend fun delete(item: Item)
}
