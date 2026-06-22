package com.example.myziyubiaoqian.shared.model

/**
 * 物品数据模型——Android端和服务器端共用
 * NFC标签仅存4-8字节ID，所有解说内容存于数据库
 */
data class Item(
    val tagId: String,           // NFC标签唯一ID（十六进制字符串）
    val name: String,            // 物品名称
    val description: String,     // AI/人工生成的解说词
    val location: String = "",   // 当前位置（蓝牙信标自动更新）
    val category: String = "",   // 物品类别（药品/食品/衣物/其他）
    val syncVersion: Long = 0,   // 增量同步版本号（乐观锁）
    val updatedAt: Long = 0,     // 最后修改时间戳（epoch millis）
)
