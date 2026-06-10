package com.example.myziyubiaoqian.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 物品实体——NFC 标签 ID 与解说词的对应关系。
 *
 * 视障用户端只读查询，写操作由未来的云端同步层或家属端完成。
 * [updatedAt] 和 [syncVersion] 为同步冲突解决预留。
 */
@Entity(tableName = "items")
data class Item(
    /** NFC 标签唯一 ID（十六进制大写，如 "04A2F8B3"）*/
    @PrimaryKey
    val tagId: String,
    /** 物品名称，如"药盒" */
    val name: String,
    /** 解说词——触碰后语音播报的内容 */
    val description: String,
    /** 所在位置，如"卧室床头柜" */
    val location: String? = null,
    /** 分类：药品、食品、衣物、工具、电子设备、重要物品、日用品、其他 */
    val category: String? = null,
    /** 创建时间戳（毫秒） */
    val createdAt: Long = System.currentTimeMillis(),
    /** 最后更新时间戳——供云端同步做 last-write-wins 冲突解决 */
    val updatedAt: Long = System.currentTimeMillis(),
    /** 同步版本号——预留乐观锁，后续同步层使用 */
    val syncVersion: Int = 0,
)
