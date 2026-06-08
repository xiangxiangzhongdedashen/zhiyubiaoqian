package com.example.myziyubiaoqian

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Build
import android.util.Log
import java.util.Locale

/**
 * NFC 标签读取模块。
 *
 * 使用方式：
 * 1. onCreate 中调用 [enableForegroundDispatch]
 * 2. onPause 中调用 [disableForegroundDispatch]
 * 3. onNewIntent 中调用 [handleIntent] 并接收返回的标签 ID
 */
class NfcReader(private val activity: Activity) {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    /** 设备是否支持 NFC */
    val isSupported: Boolean get() = nfcAdapter != null

    /** NFC 是否已开启 */
    val isEnabled: Boolean get() = nfcAdapter?.isEnabled == true

    /**
     * 开启前台调度，确保正在运行的 Activity 优先处理 NFC 事件。
     * 应在 Activity.onCreate 中调用。
     */
    fun enableForegroundDispatch() {
        val adapter = nfcAdapter ?: return
        try {
            val pendingIntent = PendingIntent.getActivity(
                activity,
                0,
                Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE
            )
            val filters = arrayOf(
                IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
            )
            val techList = arrayOf(
                arrayOf(NfcA::class.java.name),
                arrayOf(NfcB::class.java.name),
                arrayOf(NfcF::class.java.name),
                arrayOf(NfcV::class.java.name),
            )
            adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList)
        } catch (e: Exception) {
            Log.e(TAG, "启用 NFC 前台调度失败", e)
        }
    }

    /**
     * 关闭前台调度。
     * 应在 Activity.onPause 中调用。
     */
    fun disableForegroundDispatch() {
        try {
            nfcAdapter?.disableForegroundDispatch(activity)
        } catch (e: Exception) {
            Log.e(TAG, "关闭 NFC 前台调度失败", e)
        }
    }

    /**
     * 从 Intent 中解析 NFC 标签，返回标签 ID（十六进制字符串）。
     * 应在 Activity.onNewIntent 中调用。
     *
     * @return 标签 ID 的十六进制大写字符串，无 NFC 标签时返回 null
     */
    fun handleIntent(intent: Intent): NfcTagInfo? {
        return try {
            val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            tag?.let {
                NfcTagInfo(
                    id = it.id.toHexString(),
                    techList = it.techList?.toList() ?: emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取 NFC 标签失败", e)
            null
        }
    }

    companion object {
        private const val TAG = "NfcReader"

        /**
         * 工具方法：将 ByteArray 转为无空格的大写十六进制字符串。
         */
        private fun ByteArray.toHexString(): String {
            return joinToString("") { "%02X".format(Locale.ROOT, it) }
        }
    }
}

/**
 * 读取到的 NFC 标签信息。
 *
 * @param id 标签唯一 ID（十六进制字符串，如 "04A2F8B3"）
 * @param techList 标签支持的技术类型列表
 */
data class NfcTagInfo(
    val id: String,
    val techList: List<String>
)
