package com.example.myziyubiaoqian

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * TTS 语音播报模块。
 *
 * 封装 Android 原生 TextToSpeech，面向视障用户做中文优化：
 * - 朗读前自动清空队列，避免累积
 * - 标签 ID 逐字朗读（字间加空格），确保每个字符清晰可辨
 *
 * 使用方式：
 * 1. Activity.onCreate 中调用 [init]
 * 2. 需要播报时调用 [speakTagId]
 * 3. Activity.onDestroy 中调用 [shutdown]
 */
class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    /**
     * 初始化 TTS 引擎。
     * 应在 Activity.onCreate 中调用。
     *
     * @param onReady 引擎就绪后的回调（可选），用于播报欢迎语等
     */
    fun init(onReady: (() -> Unit)? = null) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINESE) ?: TextToSpeech.LANG_MISSING_DATA
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "中文 TTS 不可用，尝试默认语言")
                    // 回退到设备默认语言
                    tts?.setLanguage(Locale.getDefault())
                }
                isInitialized = true
                Log.i(TAG, "TTS 引擎初始化完成")
                onReady?.invoke()
            } else {
                Log.e(TAG, "TTS 初始化失败，状态码: $status")
            }
        }
    }

    /**
     * 播报 NFC 标签 ID。
     * 将十六进制 ID（如 "04A2F8B3"）逐字以空格分隔后朗读，
     * 确保每个字符被清晰读出（如 "0 4 A 2 F 8 B 3"）。
     */
    fun speakTagId(tagId: String) {
        val hexId = tagId.uppercase(Locale.ROOT)
        // 逐字加空格，让 TTS 一个字符一个字符地读
        val spoken = hexId.toCharArray().joinToString(" ")
        val text = "读取到标签，编号：$spoken"
        speak(text)
    }

    /**
     * 朗读任意文本。
     * 会自动清空当前播放队列（新内容优先）。
     */
    fun speak(text: String) {
        val engine = tts
        if (!isInitialized || engine == null) {
            Log.w(TAG, "TTS 未就绪，跳过播报: $text")
            return
        }
        // 清空队列，新内容优先播报
        engine.stop()
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (result == TextToSpeech.SUCCESS) {
            Log.i(TAG, "播报: $text")
        } else {
            Log.e(TAG, "播报失败，错误码: $result")
        }
    }

    /**
     * 释放 TTS 资源。
     * 应在 Activity.onDestroy 中调用。
     */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) { }
        isInitialized = false
        tts = null
        Log.i(TAG, "TTS 引擎已释放")
    }

    companion object {
        private const val TAG = "TtsManager"
        private const val UTTERANCE_ID = "nfc_tag_id"
    }
}
