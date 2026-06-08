package com.example.myziyubiaoqian

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myziyubiaoqian.ui.theme.MyZiyubiaoqianTheme

class MainActivity : ComponentActivity() {

    private val nfcReader by lazy { NfcReader(this) }
    private var tagId by mutableStateOf<String?>(null)
    private var errorMsg by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            // 处理从 NFC 标签启动的 Intent
            nfcReader.handleIntent(intent)?.let { updateTagDisplay(it) }
        } catch (e: Exception) {
            errorMsg = "NFC读取异常: ${e.message}"
        }

        setContent {
            MyZiyubiaoqianTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NfcScreen(
                        tagId = tagId,
                        errorMsg = errorMsg,
                        isNfcSupported = try { nfcReader.isSupported } catch (_: Exception) { false },
                        isNfcEnabled = try { nfcReader.isEnabled } catch (_: Exception) { false },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            nfcReader.enableForegroundDispatch()
        } catch (e: Exception) {
            errorMsg = "前台调度异常: ${e.message}"
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            nfcReader.disableForegroundDispatch()
        } catch (_: Exception) { }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        try {
            nfcReader.handleIntent(intent)?.let { updateTagDisplay(it) }
        } catch (e: Exception) {
            errorMsg = "NFC读取异常: ${e.message}"
        }
    }

    private fun updateTagDisplay(tagInfo: NfcTagInfo) {
        tagId = "ID: ${tagInfo.id}\n技术: ${tagInfo.techList.joinToString(", ")}"
    }
}

@Composable
fun NfcScreen(
    tagId: String?,
    errorMsg: String?,
    isNfcSupported: Boolean,
    isNfcEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 优先显示错误（帮助排查闪退原因）
        if (errorMsg != null) {
            Text("⚠️ 发生异常", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Red)
            Text(
                errorMsg,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
                color = androidx.compose.ui.graphics.Color(0xFFB71C1C)
            )
        } else {
            when {
                !isNfcSupported -> {
                    Text("设备不支持 NFC", fontSize = 20.sp, color = androidx.compose.ui.graphics.Color.Red)
                }
                !isNfcEnabled -> {
                    Text("请先开启 NFC 功能", fontSize = 20.sp, color = androidx.compose.ui.graphics.Color(0xFFFF9800))
                }
                tagId == null -> {
                    Text("等待 NFC 标签…", fontSize = 20.sp)
                    Text(
                        "请将手机靠近 NFC 标签",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp),
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
                else -> {
                    Text("读取成功 ✅", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        tagId,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}
