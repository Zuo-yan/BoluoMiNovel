package com.novelai.assistant.data.generation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.novelai.assistant.MainActivity
import com.novelai.assistant.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI 续写/分支后台保活前台服务：
 * - 生成中显示进度通知并持有前台优先级，切回阅读器、切出应用或锁屏也不易被系统回收；
 * - 生成结束后自动退出前台常驻，若生成有正文则投递可滑动清除的「生成完成」提醒通知。
 */
@AndroidEntryPoint
class GenerationForegroundService : Service() {

    @Inject
    lateinit var taskManager: GenerationTaskManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val initial = taskManager.state.value
        startForegroundCompat(buildRunningNotification(initial))
        observeGenerationState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                taskManager.stop()
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val cur = taskManager.state.value
                if (cur?.isRunning == true) {
                    notificationManager.notify(FOREGROUND_NOTIFICATION_ID, buildRunningNotification(cur))
                } else {
                    stopForegroundAndSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun observeGenerationState() {
        serviceScope.launch {
            var lastStatus: GenerationTaskStatus? = null
            var lastBucket = -1
            var lastNotifyMs = 0L

            taskManager.state.collect { state ->
                if (state == null) {
                    stopForegroundAndSelf()
                    return@collect
                }
                if (state.isRunning) {
                    val bucket = state.text.length / NOTIFY_CHAR_STEP
                    val now = System.currentTimeMillis()
                    if (lastStatus != state.status || bucket != lastBucket || now - lastNotifyMs >= NOTIFY_INTERVAL_MS) {
                        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, buildRunningNotification(state))
                        lastStatus = state.status
                        lastBucket = bucket
                        lastNotifyMs = now
                    }
                } else {
                    // 从生成中自然完成或出错时，投递一条可点击跳转、可划掉的提醒通知
                    if (lastStatus == GenerationTaskStatus.RUNNING) {
                        when {
                            state.status == GenerationTaskStatus.COMPLETED && state.hasDraft -> {
                                notificationManager.notify(
                                    COMPLETION_NOTIFICATION_ID,
                                    buildCompletedNotification(state)
                                )
                            }
                            state.status == GenerationTaskStatus.FAILED && !state.error.isNullOrBlank() -> {
                                notificationManager.notify(
                                    COMPLETION_NOTIFICATION_ID,
                                    buildFailedNotification(state)
                                )
                            }
                        }
                    }
                    stopForegroundAndSelf()
                }
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun buildRunningNotification(state: BackgroundGenerationState?): Notification {
        val actionLabel = "AI 续写"
        val bookTitle = state?.bookTitle?.takeIf { it.isNotBlank() } ?: "小说创作"
        val chars = state?.text?.length ?: 0
        val contentText = if (chars > 0) {
            "已生成 $chars 字 · ${state?.modelLabel.orEmpty()}"
        } else {
            "正在召回前文记忆 · ${state?.modelLabel.orEmpty()}"
        }

        val stopIntent = Intent(this, GenerationForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tts_notification)
            .setContentTitle("正在$actionLabel · 《$bookTitle》")
            .setContentText(contentText)
            .setContentIntent(buildOpenAppPendingIntent(state))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止生成", stopPending)
            .build()
    }

    private fun buildCompletedNotification(state: BackgroundGenerationState): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tts_notification)
            .setContentTitle("AI 续写完成 · 《${state.bookTitle}》")
            .setContentText("已生成 ${state.text.length} 字，点击查看并保存章节")
            .setContentIntent(buildOpenAppPendingIntent(state))
            .setAutoCancel(true)
            .setOngoing(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun buildFailedNotification(state: BackgroundGenerationState): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tts_notification)
            .setContentTitle("AI 生成中断 · 《${state.bookTitle}》")
            .setContentText(state.error ?: "请点击返回工作台查看")
            .setContentIntent(buildOpenAppPendingIntent(state))
            .setAutoCancel(true)
            .setOngoing(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun buildOpenAppPendingIntent(state: BackgroundGenerationState?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (state != null) {
                putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_GENERATE)
                putExtra(MainActivity.EXTRA_BOOK_ID, state.bookId)
                putExtra(MainActivity.EXTRA_CHAPTER_ID, state.parentChapterId)
                putExtra(MainActivity.EXTRA_MODE, "CONTINUATION")
            }
        }
        return PendingIntent.getActivity(
            this,
            202,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI 续写与后台生成进度",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "在后台继续生成小说章节时保留通知进度，防止系统中断请求"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun stopForegroundAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        var instance: GenerationForegroundService? = null
            private set

        private const val CHANNEL_ID = "novelai_generation_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 2027
        private const val COMPLETION_NOTIFICATION_ID = 2028
        private const val NOTIFY_CHAR_STEP = 60
        private const val NOTIFY_INTERVAL_MS = 900L

        const val ACTION_START = "com.novelai.assistant.action.GEN_START"
        const val ACTION_STOP = "com.novelai.assistant.action.GEN_STOP"

        fun startIntent(context: Context): Intent =
            Intent(context, GenerationForegroundService::class.java).apply {
                action = ACTION_START
            }

        fun stopService(context: Context) {
            instance?.stopForegroundAndSelf()
        }

        fun cancelCompletionNotification(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(COMPLETION_NOTIFICATION_ID)
        }
    }
}
