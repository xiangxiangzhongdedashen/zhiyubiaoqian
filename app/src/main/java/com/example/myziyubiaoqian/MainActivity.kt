package com.example.myziyubiaoqian

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.myziyubiaoqian.data.AppDatabase
import com.example.myziyubiaoqian.data.Item
import com.example.myziyubiaoqian.data.ItemRepository
import com.example.myziyubiaoqian.ui.theme.MyZiyubiaoqianTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val nfcReader by lazy { NfcReader(this) }
    private val ttsManager by lazy { TtsManager(this) }
    private val database by lazy { AppDatabase.getInstance(this) }
    private val repository by lazy { ItemRepository(database.itemDao()) }

    // ── UI 状态 ──
    private var selectedTab by mutableIntStateOf(0)
    private var tagId by mutableStateOf<String?>(null)
    private var lastScannedItem by mutableStateOf<Item?>(null)
    private var isUnknownTag by mutableStateOf(false)
    private var errorMsg by mutableStateOf<String?>(null)
    private var ttsStatus by mutableStateOf("正在初始化 TTS 引擎…")
    private var allItems by mutableStateOf<List<Item>>(emptyList())
    private var simulateIndex by mutableIntStateOf(0)  // 模拟触碰：依次循环demo标签
    private var simulateLabel by mutableStateOf<String?>(null)  // 上一轮模拟的物品名

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 初始化 TTS
        ttsManager.onStatusChanged = { status -> ttsStatus = status }
        ttsManager.init()

        // 收集全部物品（响应式，云端同步后自动更新）
        lifecycleScope.launch {
            repository.getAll().collectLatest { items ->
                allItems = items
            }
        }

        // 处理 NFC 启动 Intent
        try {
            nfcReader.handleIntent(intent)?.let { onTagScanned(it) }
        } catch (_: Exception) { }

        setContent {
            MyZiyubiaoqianTheme {
                MainScreen(
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    tagId = tagId,
                    lastScannedItem = lastScannedItem,
                    isUnknownTag = isUnknownTag,
                    errorMsg = errorMsg,
                    ttsStatus = ttsStatus,
                    allItems = allItems,
                    isNfcSupported = try { nfcReader.isSupported } catch (_: Exception) { false },
                    isNfcEnabled = try { nfcReader.isEnabled } catch (_: Exception) { false },
                    onOpenTtsSettings = { ttsManager.openTtsSettings() },
                    onSpeakItem = { item -> ttsManager.speakItemDescription(item) },
                    onSimulateTag = { simulateLabel = onSimulateTag() },
                    simulateLabel = simulateLabel,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try { nfcReader.enableForegroundDispatch() } catch (_: Exception) { }
    }

    override fun onPause() {
        super.onPause()
        try { nfcReader.disableForegroundDispatch() } catch (_: Exception) { }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        try {
            nfcReader.handleIntent(intent)?.let { onTagScanned(it) }
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }

    /** NFC 标签被触碰 → 查数据库 → 播报 */
    private fun onTagScanned(tagInfo: NfcTagInfo) {
        tagId = tagInfo.id
        errorMsg = null

        lifecycleScope.launch {
            val item = repository.getByTagIdOnce(tagInfo.id)
            if (item != null) {
                lastScannedItem = item
                isUnknownTag = false
                ttsManager.speakItemDescription(item)
            } else {
                lastScannedItem = null
                isUnknownTag = true
                ttsManager.speakUnknownTag(tagInfo.id)
            }
        }
    }

    /** 模拟触碰——循环使用4个demo标签ID，无需硬件即可测试 */
    private fun onSimulateTag(): String {
        val demoIds = arrayOf("04A2F8B3", "A1B2C3D4", "E5F6G7H8", "11223344")
        val demoLabels = arrayOf("药盒", "水杯", "遥控器", "家门钥匙")
        val id = demoIds[simulateIndex % demoIds.size]
        val label = demoLabels[simulateIndex % demoIds.size]
        simulateIndex++
        onTagScanned(NfcTagInfo(id, listOf("android.nfc.tech.NfcA")))
        return label  // 返回模拟的物品名，UI可显示
    }
}

// ════════════════════════════════════════════════════════════════════
// UI
// ════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    tagId: String?,
    lastScannedItem: Item?,
    isUnknownTag: Boolean,
    errorMsg: String?,
    ttsStatus: String,
    allItems: List<Item>,
    isNfcSupported: Boolean,
    isNfcEnabled: Boolean,
    onOpenTtsSettings: () -> Unit,
    onSpeakItem: (Item) -> Unit,
    onSimulateTag: () -> Unit,
    simulateLabel: String?,
) {
    val tabs = listOf("🏷️ 扫描", "📋 物品")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("智语标签", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { onTabChange(index) },
                        text = { Text(title, fontSize = 16.sp) }
                    )
                }
            }

            when (selectedTab) {
                0 -> ScanTab(
                    tagId = tagId,
                    lastScannedItem = lastScannedItem,
                    isUnknownTag = isUnknownTag,
                    errorMsg = errorMsg,
                    ttsStatus = ttsStatus,
                    isNfcSupported = isNfcSupported,
                    isNfcEnabled = isNfcEnabled,
                    onOpenTtsSettings = onOpenTtsSettings,
                    onSimulateTag = onSimulateTag,
                    simulateLabel = simulateLabel,
                )
                1 -> ItemsTab(
                    items = allItems,
                    onSpeakItem = onSpeakItem,
                )
            }
        }
    }
}

// ── 扫描标签页 ──

@Composable
private fun ScanTab(
    tagId: String?,
    lastScannedItem: Item?,
    isUnknownTag: Boolean,
    errorMsg: String?,
    ttsStatus: String,
    isNfcSupported: Boolean,
    isNfcEnabled: Boolean,
    onOpenTtsSettings: () -> Unit,
    onSimulateTag: () -> Unit,
    simulateLabel: String?,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 错误
        if (errorMsg != null) {
            Text("⚠️ 异常", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Red)
            Text(errorMsg, fontSize = 14.sp, color = Color(0xFFB71C1C))
        }
        // NFC 状态
        else if (!isNfcSupported) {
            Text("设备不支持 NFC", fontSize = 22.sp, color = Color.Red)
        } else if (!isNfcEnabled) {
            Text("请先开启 NFC", fontSize = 22.sp, color = Color(0xFFFF9800))
        }
        // 已扫描到标签
        else if (tagId != null) {
            when {
                lastScannedItem != null -> ItemResultCard(lastScannedItem!!)
                isUnknownTag -> UnknownTagCard(tagId)
            }
        }
        // 等待触碰
        else {
            Text("等待 NFC 标签…", fontSize = 22.sp)
            Text(
                "请将手机靠近物品上的 NFC 标签",
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp),
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // TTS 状态
        TtsStatusBar(ttsStatus, onOpenTtsSettings)

        Spacer(modifier = Modifier.height(32.dp))

        // 模拟触碰按钮（无需NFC硬件即可测试）
        if (simulateLabel != null) {
            Text(
                "上一轮模拟：$simulateLabel ✅",
                fontSize = 13.sp,
                color = Color(0xFF4CAF50)
            )
        } else {
            Text(
                "无需硬件即可测试，点击按钮模拟触碰",
                fontSize = 13.sp,
                color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onSimulateTag) {
            Text("🧪 模拟触碰", fontSize = 16.sp)
        }
    }
}

/** 已知物品——显示名称、解说词、位置 */
@Composable
private fun ItemResultCard(item: Item) {
    Text("✅ 识别成功", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
    Spacer(modifier = Modifier.height(12.dp))

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(item.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
            Spacer(modifier = Modifier.height(8.dp))
            if (item.location != null) {
                LabelValue("📍 位置", item.location)
            }
            if (item.category != null) {
                LabelValue("📂 分类", item.category)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                item.description,
                fontSize = 18.sp,
                lineHeight = 28.sp,
                color = Color(0xFF333333),
                modifier = Modifier.semantics { contentDescription = "解说词：${item.description}" }
            )
        }
    }
}

/** 未注册标签——显示 ID，提示未知 */
@Composable
private fun UnknownTagCard(tagId: String) {
    Text("❓ 未注册的标签", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
    Spacer(modifier = Modifier.height(12.dp))

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("此标签尚未在系统中注册", fontSize = 16.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Text("标签 ID", fontSize = 14.sp, color = Color.Gray)
            Text(tagId, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color(0xFF555555))
        }
    }
}

/** TTS 状态指示条 */
@Composable
private fun TtsStatusBar(ttsStatus: String, onOpenTtsSettings: () -> Unit) {
    val statusColor = when {
        ttsStatus.contains("就绪") -> Color(0xFF4CAF50)
        ttsStatus.contains("失败") || ttsStatus.contains("缺失") -> Color.Red
        else -> Color.Gray
    }
    Text("🔊 TTS: $ttsStatus", fontSize = 12.sp, color = statusColor)

    if (ttsStatus.contains("失败") || ttsStatus.contains("缺失")) {
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onOpenTtsSettings) {
            Text("打开 TTS 设置", fontSize = 14.sp)
        }
    }
}

// ── 物品列表标签页 ──

@Composable
private fun ItemsTab(
    items: List<Item>,
    onSpeakItem: (Item) -> Unit,
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无物品数据", fontSize = 18.sp, color = Color.Gray)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "共 ${items.size} 个物品（点击可听解说）",
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            )
        }
        items(items, key = { it.tagId }) { item ->
            ItemListItem(
                item = item,
                onClick = { onSpeakItem(item) }
            )
        }
    }
}

/** 物品列表卡片——点击播报解说词 */
@Composable
private fun ItemListItem(item: Item, onClick: () -> Unit) {
    val categoryEmoji = remember(item.category) {
        when (item.category) {
            "药品" -> "💊"
            "食品" -> "🍔"
            "衣物" -> "👕"
            "工具" -> "🔧"
            "电子设备" -> "📱"
            "重要物品" -> "🔑"
            "日用品" -> "🧴"
            else -> "📦"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "${item.name}，${item.description}" }
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(categoryEmoji, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(item.description, fontSize = 15.sp, color = Color(0xFF666666), maxLines = 2)
                if (item.location != null) {
                    Text("📍 ${item.location}", fontSize = 13.sp, color = Color(0xFF999999))
                }
            }
        }
    }
}

// ── 小工具 ──

@Composable
private fun LabelValue(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.width(6.dp))
        Text(value, fontSize = 16.sp)
    }
}
