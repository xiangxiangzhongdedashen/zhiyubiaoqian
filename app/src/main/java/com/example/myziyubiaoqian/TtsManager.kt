package com.example.myziyubiaoqian

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.myziyubiaoqian.data.Item
import java.util.Locale

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
        Log.i(TAG, "[状态] $msg")
        onStatusChanged?.invoke(msg)
    }

    fun init(onReady: (() -> Unit)? = null) {
        Log.i(TAG, "========== TTS 初始化开始 ==========")
        buildCandidateList()
        Log.i(TAG, "候选引擎列表: $candidates")

        if (candidates.isEmpty()) {
            Log.e(TAG, "候选列表为空！检查是否有 TTS 引擎 APK 安装")
            updateStatus("未找到 TTS 引擎，请安装语音引擎")
            return
        }

        candidateIndex = 0
        tryNext(onReady)
    }

    private fun buildCandidateList() {
        candidates.clear()

        // 逐个检查每个已知包名
        val allPackages = PRIORITY_PACKAGES + KNOWN_TTS_PACKAGES
        for (pkg in allPackages.distinct()) {
            val installed = isPackageInstalled(pkg)
            Log.i(TAG, "检查包 $pkg → ${if (installed) "已安装 ✓" else "未安装"}")
            if (installed && pkg !in candidates) {
                candidates.add(pkg)
            }
        }

        // 默认引擎放在已安装的后面
        if (candidates.isEmpty()) {
            Log.w(TAG, "无已知包已安装，仅尝试默认引擎")
        }
        candidates.add(null)
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            val info = appContext.packageManager.getPackageInfo(pkg, 0)
            Log.d(TAG, "  $pkg v${info.versionName} (${info.versionCode})")
            true
        } catch (e: Exception) {
            Log.d(TAG, "  $pkg 未安装: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun tryNext(onReady: (() -> Unit)?) {
        if (candidateIndex >= candidates.size) {
            Log.e(TAG, "所有 ${candidates.size} 个候选引擎均失败！")
            updateStatus("TTS 不可用 — 请安装语音引擎")
            return
        }

        val pkg = candidates[candidateIndex]
        val label = pkg ?: "默认引擎"
        Log.i(TAG, ">>> 尝试引擎 #${candidateIndex + 1}/${candidates.size}: $label")
        if (pkg != null) Log.i(TAG, "    包名: $pkg")

        // 释放旧引擎
        try {
            val oldTts = tts
            if (oldTts != null) {
                Log.d(TAG, "    关闭旧引擎…")
                oldTts.shutdown()
                tts = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "    关闭旧引擎异常: ${e.message}", e)
        }
        isInitialized = false

        try {
            Log.d(TAG, "    构造 TextToSpeech…")
            tts = if (pkg != null) {
                TextToSpeech(appContext, { s -> onInit(s, pkg, onReady) }, pkg)
            } else {
                @Suppress("DEPRECATION")
                TextToSpeech(appContext) { s -> onInit(s, null, onReady) }
            }
            Log.d(TAG, "    TextToSpeech 构造完成，等待回调…")
        } catch (e: Exception) {
            Log.e(TAG, "    TextToSpeech 构造异常: ${e.javaClass.simpleName}: ${e.message}", e)
            candidateIndex++
            handler.post { tryNext(onReady) }
        }
    }

    private fun onInit(initStatus: Int, enginePkg: String?, onReady: (() -> Unit)?) {
        val label = enginePkg ?: "默认引擎"
        Log.i(TAG, "<<< 回调: $label → initStatus=$initStatus (SUCCESS=${TextToSpeech.SUCCESS})")

        if (initStatus == TextToSpeech.SUCCESS) {
            val engine = tts
            if (engine == null) {
                Log.e(TAG, "init SUCCESS 但 tts 为 null！")
                candidateIndex++
                handler.post { tryNext(onReady) }
                return
            }

            // 尝试设置语言
            for (locale in LANGUAGE_PRIORITY) {
                val r = engine.setLanguage(locale)
                Log.d(TAG, "    setLanguage(${locale.displayName}) → $r (SUCCESS=${TextToSpeech.SUCCESS})")
                if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.i(TAG, "    语言设置成功: ${locale.displayName}")
                    isInitialized = true
                    updateStatus("TTS 已就绪 ✓ ($label)")
                    drainPending()
                    onReady?.invoke()
                    return
                }
            }

            // 所有语言都失败
            Log.w(TAG, "所有语言设置均失败，但引擎连接成功")
            isInitialized = true
            updateStatus("已连接 ($label)，缺少中文语音数据")
            drainPending()
            onReady?.invoke()
        } else {
            Log.e(TAG, "$label 初始化失败: initStatus=$initStatus")
            candidateIndex++
            // 延迟到主线程，避免 binder 死锁
            handler.post { tryNext(onReady) }
        }
    }

    private fun drainPending() {
        val last = pendingQueue.lastOrNull()
        pendingQueue.clear()
        if (last != null) {
            Log.i(TAG, "播放排队内容: $last")
            speakNow(last)
        }
    }

    fun speakTagId(tagId: String) {
        val hex = tagId.uppercase(Locale.ROOT).toCharArray().joinToString(" ")
        speak("读取到标签，编号：$hex")
    }

    /** 播报已注册物品的解说词 */
    fun speakItemDescription(item: Item) {
        val locPart = if (!item.location.isNullOrBlank()) "，放在${item.location}" else ""
        speak("${item.name}$locPart。${item.description}")
    }

    /** 播报未注册标签的提示 */
    fun speakUnknownTag(tagId: String) {
        val hex = tagId.uppercase(Locale.ROOT).toCharArray().joinToString(" ")
        speak("未注册的标签，编号：$hex")
    }

    fun speak(text: String) {
        Log.d(TAG, "speak() 请求: isInit=$isInitialized tts=${tts != null}")
        if (!isInitialized || tts == null) {
            pendingQueue.add(text)
            Log.i(TAG, "暂存到队列 (${pendingQueue.size}条)")
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        val engine = tts ?: return
        engine.stop()
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        Log.i(TAG, "speakNow → $result: \"$text\"")
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "播报失败，错误码: $result")
        }
    }

    fun openTtsSettings() {
        Log.i(TAG, "打开 TTS 设置…")
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
        Log.i(TAG, "shutdown…")
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

        private val PRIORITY_PACKAGES = listOf(
            "com.xiaomi.mibrain.speech",
        )

        private val KNOWN_TTS_PACKAGES = listOf(
            "com.google.android.tts",
            "com.svox.pico",
            "com.iflytek.speechcloud",
            "com.iflytek.speechsuite",
            "com.huawei.iassist",
            "com.samsung.SMT",
        )
    }
}
