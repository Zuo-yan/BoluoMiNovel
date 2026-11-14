package com.novelai.assistant.data.tts

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 听书全局媒体服务桥接器：
 * 负责启动/停止前台服务；服务运行后直接订阅 TtsPlayer 的 StateFlow 并分发媒体控制动作。
 */
object TtsMediaManager {

    /** 启动前台听书服务（若尚未启动）；服务运行后将直接订阅 TtsPlayer 的 StateFlow */
    fun startService(context: Context) {
        if (TtsPlaybackService.instance != null) return
        val intent = Intent(context, TtsPlaybackService::class.java).apply {
            action = TtsPlaybackService.ACTION_UPDATE_STATE
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 停止听书服务并移除通知栏媒体卡片 */
    fun stopService(context: Context) {
        TtsPlaybackService.instance?.stopForegroundAndSelf()
    }
}
