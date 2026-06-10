package com.example.myziyubiaoqian.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Room 数据库——本地 SQLite，全量缓存所有物品数据。
 *
 * 数据库文件: ziyubiaoqian.db
 * 视障用户端只读；云端同步层通过 [ItemDao.upsert] 更新数据。
 */
@Database(
    entities = [Item::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addCallback(SeedDataCallback())
                .build()
        }

        private const val DATABASE_NAME = "ziyubiaoqian.db"
    }

    /**
     * 数据库首次创建时，预置演示数据供测试。
     *
     * 演示标签 ID 均为虚构，不会与实际 NFC 标签冲突。
     * 触碰真实标签 → 播报"未注册"，触碰演示 ID → 播报对应解说词。
     */
    private class SeedDataCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                INSTANCE?.itemDao()?.upsertAll(DEMO_ITEMS)
            }
        }

        companion object {
            val DEMO_ITEMS = listOf(
                Item(
                    tagId = "04A2F8B3",
                    name = "药盒",
                    description = "每天三次，饭后服用，放在卧室床头柜",
                    location = "卧室床头柜",
                    category = "药品",
                ),
                Item(
                    tagId = "A1B2C3D4",
                    name = "水杯",
                    description = "日常喝水用的保温杯，容量500毫升",
                    location = "客厅茶几",
                    category = "日用品",
                ),
                Item(
                    tagId = "E5F6G7H8",
                    name = "遥控器",
                    description = "电视遥控器，最上面红色按钮是电源",
                    location = "客厅沙发旁",
                    category = "电子设备",
                ),
                Item(
                    tagId = "11223344",
                    name = "家门钥匙",
                    description = "大门钥匙，三把串在一起",
                    location = "门口挂钩",
                    category = "重要物品",
                ),
            )
        }
    }
}
