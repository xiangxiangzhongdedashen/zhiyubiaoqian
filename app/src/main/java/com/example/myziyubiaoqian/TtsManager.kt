package com.example.myziyubiaoqian

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * TTS 语音播报模块。
 *
 * 面向视障用户做中文优化：
 * - 依次尝试候选引擎包名，直到初始化成功
 * - 引擎切换通过主线程 Handler 延迟调度，避免在 TTS 回调中同步创建新实例导致死锁
 * - 语言按优先级回退：简体中文 → 繁体中文 → 默认 → 英文
 * - 标签 ID 逐字朗读（字间加空格）
 * - 持待播队列：引擎就绪后自动播放
 */
class TtsManager(context: Context) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingQueue = mutableListOf<String>()

    private var candidateIndex = 0
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
            updateStatus("未找到 TTS 引擎，请安装语音引擎")
            return
        }

        candidateIndex = 0
        tryNext(onReady)
    }

    /** 构建候选列表：优先用户确认的引擎 → 默认 → 其他已知包 */
    private fun buildCandidateList() {
        candidates.clear()

        // 1. 优先：用户设备确认的引擎包名
        for (pkg in PRIORITY_PACKAGES) {
            if (isPackageInstalled(pkg)) candidates.add(pkg)
        }

        // 2. 默认引擎
        candidates.add(null)

        // 3. 其他已知包名（去重）
        for (pkg in KNOWN_TTS_PACKAGES) {
            if (pkg !in PRIORITY_PACKAGES && isPackageInstalled(pkg)) {
                candidates.add(pkg)
            }
        }

        Log.i(TAG, "已安装候选: $candidates")
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            appContext.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) { false }
    }

    /**
     * 通过主线程 Handler 延迟调度下一次尝试。
     * 不在 TTS 回调线程中同步创建新的 TextToSpeech，避免底层 binder 死锁。
     */
    private fun tryNext(onReady: (() -> Unit)?) {
        if (candidateIndex >= candidates.size) {
            updateStatus("TTS 不可用 — 请安装语音引擎（如 Google 文字转语音）")
            return
        }

        val pkg = candidates[candidateIndex]
        val label = pkg ?: "默认引擎"
        updateStatus("尝试: $label (${candidateIndex + 1}/${candidates.size})")

        // 释放旧引擎
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
            val label = enginePkg ?: "默认引擎"
            updateStatus(if (ok) "TTS 已就绪 ✓ ($label)" else "已连接，缺少中文语音数据")
            drainPending()
            onReady?.invoke()
        } else {
            Log.w(TAG, "${enginePkg ?: "默认"} 失败: $initStatus")
            // 关键修复：延迟到主线程再尝试下一个，避免死锁
            candidateIndex++
            handler.post { tryNext(onReady) }
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

    fun openTtsSettings() {
        if (tryStart("com.android.settings.TTS_SETTINGS")) return
        if (tryStart(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) return
        tryStart(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
    }

    private fun tryStart(action: String): Boolean {
        return try {
            appContext.startActivity(Intent(action).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            true
        } catch (_: Exception) { false }
    }

    fun shutdown() {
        handler.removeCallbacksAndMessages(null)
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

        /** 优先尝试：用户设备确认存在的引擎 */
        private val PRIORITY_PACKAGES = listOf(
            "com.xiaomi.mibrain.speech",       // 小米小爱（用户确认）
        )

        /** 国内外常见 TTS 引擎（备用） */
        private val KNOWN_TTS_PACKAGES = listOf(
            "com.google.android.tts",          // Google 文字转语音
            "com.svox.pico",                    // AOSP Pico TTS
            "com.iflytek.speechcloud",         // 讯飞语音
            "com.iflytek.speechsuite",         // 讯飞语记
            "com.huawei.iassist",               // 华为智慧语音
            "com.samsung.SMT",                  // 三星 TTS
        )
    }
}
