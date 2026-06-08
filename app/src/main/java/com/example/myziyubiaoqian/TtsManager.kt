package com.example.myziyubiaoqian

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * TTS 语音播报模块。
 *
 * 面向视障用户做中文优化：
 * - 依次尝试默认引擎 + 已知 TTS 引擎包名，直到初始化成功
 * - 语言按优先级回退：简体中文 → 繁体中文 → 默认 → 英文
 * - 标签 ID 逐字朗读（字间加空格），确保每个字符清晰可辨
 * - 持待播队列：引擎就绪后自动播放排队内容
 */
class TtsManager(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingQueue = mutableListOf<String>()

    private var candidateIndex = 0
    /** 候选引擎列表：null=默认引擎，其余为已知包名（仅保留已安装的） */
    private val candidates = mutableListOf<String?>()

    var onStatusChanged: ((String) -> Unit)? = null

    var status: String = "正在初始化 TTS…"
        private set

    private fun updateStatus(msg: String) {
        status = msg
        Log.i(TAG, msg)
        onStatusChanged?.invoke(msg)
    }

    fun init(onReady: (() -> Unit)? = null) {
        buildCandidateList()
        Log.i(TAG, "候选引擎: $candidates")

        if (candidates.isEmpty()) {
            updateStatus("未找到 TTS 引擎，请安装语音引擎（如 Google 文字转语音）")
            return
        }

        candidateIndex = 0
        tryNext(onReady)
    }

    /** 构建候选引擎列表：默认引擎(null) + 已安装的已知 TTS 包 */
    private fun buildCandidateList() {
        candidates.clear()
        // null = 系统默认引擎
        candidates.add(null)
        // 已知 TTS 包名（国产手机常见）
        for (pkg in KNOWN_TTS_PACKAGES) {
            if (isPackageInstalled(pkg)) {
                candidates.add(pkg)
            }
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            appContext.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryNext(onReady: (() -> Unit)?) {
        if (candidateIndex >= candidates.size) {
            updateStatus("TTS 不可用 — 请下载语音引擎（如 Google 文字转语音）")
            return
        }

        val pkg = candidates[candidateIndex]
        val label = pkg ?: "默认引擎"
        updateStatus("尝试: $label (${candidateIndex + 1}/${candidates.size})")

        try { tts?.shutdown() } catch (_: Exception) { }
        isInitialized = false

        tts = if (pkg != null) {
            TextToSpeech(appContext, { s -> onInit(s, pkg, onReady) }, pkg)
        } else {
            @Suppress("DEPRECATION")
            TextToSpeech(appContext) { s -> onInit(s, null, onReady) }
        }
    }

    private fun onInit(initStatus: Int, enginePkg: String?, onReady: (() -> Unit)?) {
        if (initStatus == TextToSpeech.SUCCESS) {
            val engine = tts ?: return

            var ok = false
            for (locale in LANGUAGE_PRIORITY) {
                val r = engine.setLanguage(locale)
                if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                    ok = true
                    break
                }
            }

            isInitialized = true
            updateStatus(if (ok) "TTS 已就绪 ✓" else "已连接，缺少中文语音数据")
            drainPending()
            onReady?.invoke()
        } else {
            Log.w(TAG, "${enginePkg ?: "默认"} 失败: $initStatus")
            candidateIndex++
            tryNext(onReady)
        }
    }

    private fun drainPending() {
        val last = pendingQueue.lastOrNull()
        pendingQueue.clear()
        if (last != null) speakNow(last)
    }

    fun speakTagId(tagId: String) {
        val hex = tagId.uppercase(Locale.ROOT).toCharArray().joinToString(" ")
        speak("读取到标签，编号：$hex")
    }

    fun speak(text: String) {
        if (!isInitialized || tts == null) {
            pendingQueue.add(text)
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        val engine = tts ?: return
        engine.stop()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /** 打开 TTS 设置 / 语音数据安装 */
    fun openTtsSettings() {
        // 优先：TTS 设置
        if (tryStart("com.android.settings.TTS_SETTINGS")) return
        // 回退：安装语音数据
        if (tryStart(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) return
        // 最终：检查数据
        tryStart(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
    }

    private fun tryStart(action: String): Boolean {
        return try {
            appContext.startActivity(Intent(action).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            true
        } catch (_: Exception) { false }
    }

    fun shutdown() {
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) { }
        isInitialized = false
        tts = null
        pendingQueue.clear()
    }

    companion object {
        private const val TAG = "TtsManager"
        private const val UTTERANCE_ID = "nfc_tag_id"

        private val LANGUAGE_PRIORITY = listOf(
            Locale.SIMPLIFIED_CHINESE, Locale.CHINESE,
            Locale.TRADITIONAL_CHINESE, Locale.getDefault(), Locale.US
        )

        /** 国内外常见 TTS 引擎包名 */
        private val KNOWN_TTS_PACKAGES = listOf(
            "com.google.android.tts",          // Google 文字转语音
            "com.svox.pico",                    // AOSP Pico TTS
            "com.iflytek.speechcloud",         // 讯飞语音
            "com.iflytek.speechsuite",         // 讯飞语记
            "com.xiaomi.mibrain.speech",       // 小米小爱
            "com.huawei.iassist",               // 华为智慧语音
            "com.samsung.SMT",                  // 三星 TTS
            "com.oppo.aispeech",                // OPPO
            "com.vivo.aispeech",                // vivo
        )
    }
}
