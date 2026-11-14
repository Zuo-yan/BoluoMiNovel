package com.novelai.assistant.data.tts

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
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.novelai.assistant.MainActivity
import com.novelai.assistant.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 听书前台媒体服务：
 * 直接观测 TtsPlayer 的 StateFlow（播放状态、语速、书名、章节、段落进度），
 * 提供前台保活、锁屏媒体控制与通知栏媒体卡片。
 */
@AndroidEntryPoint
class TtsPlaybackService : Service() {

    @Inject
    lateinit var ttsPlayer: TtsPlayer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSessionCompat? = null
    private lateinit var notificationManager: NotificationManager

    private enum class TransportAction {
        PLAY, PAUSE, PREVIOUS, NEXT, STOP
    }

    private data class PlaybackMeta(
        val speaking: Boolean,
        val paused: Boolean,
        val rate: Float,
        val position: Int,
        val totalParagraphs: Int
    )

    private data class MediaSnapshot(
        val isSpeaking: Boolean,
        val isPaused: Boolean,
        val rate: Float,
        val bookId: String,
        val bookTitle: String,
        val chapterTitle: String,
        val position: Int,
        val totalParagraphs: Int
    ) {
        val isActive: Boolean get() = isSpeaking || isPaused
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // 初始化 MediaSession
        mediaSession = MediaSessionCompat(this, "NovelAiTtsSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setPlaybackToLocal(android.media.AudioManager.STREAM_MUSIC)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = dispatchTransportAction(TransportAction.PLAY)
                override fun onPause() = dispatchTransportAction(TransportAction.PAUSE)
                override fun onSkipToNext() = dispatchTransportAction(TransportAction.NEXT)
                override fun onSkipToPrevious() = dispatchTransportAction(TransportAction.PREVIOUS)
                override fun onStop() = dispatchTransportAction(TransportAction.STOP)
            })
            isActive = true
        }

        // 必须在 onCreate 中立即启动前台通知，避免系统 5 秒前台超时限制
        val initialSnapshot = currentMediaSnapshot()
        startForegroundCompat(buildNotification(initialSnapshot))
        updatePlaybackState(initialSnapshot)
        observePlayerState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> dispatchTransportAction(TransportAction.PLAY)
            ACTION_PAUSE -> dispatchTransportAction(TransportAction.PAUSE)
            ACTION_PREVIOUS -> dispatchTransportAction(TransportAction.PREVIOUS)
            ACTION_NEXT -> dispatchTransportAction(TransportAction.NEXT)
            ACTION_STOP -> {
                dispatchTransportAction(TransportAction.STOP)
                return START_NOT_STICKY
            }
            ACTION_UPDATE_STATE, null -> {
                val snapshot = currentMediaSnapshot()
                if (snapshot.isActive) {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(snapshot))
                    updatePlaybackState(snapshot)
                }
            }
        }
        return START_STICKY
    }

    private fun dispatchTransportAction(action: TransportAction) {
        when (action) {
            TransportAction.PLAY -> ttsPlayer.resume()
            TransportAction.PAUSE -> ttsPlayer.pause()
            TransportAction.PREVIOUS -> ttsPlayer.playPreviousChapterAuto()
            TransportAction.NEXT -> ttsPlayer.playNextChapterAuto()
            TransportAction.STOP -> {
                ttsPlayer.stop()
                ttsPlayer.resetTimer()
                stopForegroundAndSelf()
            }
        }
    }

    private fun currentMediaSnapshot(): MediaSnapshot = MediaSnapshot(
        isSpeaking = ttsPlayer.speaking.value,
        isPaused = ttsPlayer.paused.value,
        rate = ttsPlayer.rate.value,
        bookId = ttsPlayer.currentBookId.value,
        bookTitle = ttsPlayer.currentBookTitle.value,
        chapterTitle = ttsPlayer.currentChapterTitle.value,
        position = ttsPlayer.position.value,
        totalParagraphs = ttsPlayer.totalParagraphs.value
    )

    private fun observePlayerState() {
        val playbackFlow = combine(
            ttsPlayer.speaking,
            ttsPlayer.paused,
            ttsPlayer.rate,
            ttsPlayer.position,
            ttsPlayer.totalParagraphs
        ) { speaking, paused, rate, position, totalParagraphs ->
            PlaybackMeta(speaking, paused, rate, position, totalParagraphs)
        }

        val metadataFlow = combine(
            ttsPlayer.currentBookId,
            ttsPlayer.currentBookTitle,
            ttsPlayer.currentChapterTitle
        ) { bookId, bookTitle, chapterTitle ->
            Triple(bookId, bookTitle, chapterTitle)
        }

        serviceScope.launch {
            combine(playbackFlow, metadataFlow) { playback, (bookId, bookTitle, chapterTitle) ->
                MediaSnapshot(
                    isSpeaking = playback.speaking,
                    isPaused = playback.paused,
                    rate = playback.rate,
                    bookId = bookId,
                    bookTitle = bookTitle,
                    chapterTitle = chapterTitle,
                    position = playback.position,
                    totalParagraphs = playback.totalParagraphs
                )
            }
                .distinctUntilChanged()
                .collect { snapshot ->
                    if (snapshot.isActive) {
                        notificationManager.notify(NOTIFICATION_ID, buildNotification(snapshot))
                        updatePlaybackState(snapshot)
                    } else {
                        stopForegroundAndSelf()
                    }
                }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updatePlaybackState(snapshot: MediaSnapshot) {
        val session = mediaSession ?: return

        val meta = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, snapshot.chapterTitle.ifBlank { "正在朗读" })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, snapshot.bookTitle.ifBlank { "小说阅读" })
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "菠萝蜜小说 · 听书")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
            .build()
        session.setMetadata(meta)

        val state = if (snapshot.isPaused) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        val stateCompat = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, snapshot.rate)
            .build()
        session.setPlaybackState(stateCompat)
    }

    private fun buildNotification(snapshot: MediaSnapshot): Notification {
        val sessionToken = mediaSession?.sessionToken

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_ROUTE", "listening")
            putExtra(EXTRA_BOOK_ID, snapshot.bookId)
        }
        val pendingContent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPending = createActionPendingIntent(ACTION_PREVIOUS, 1)
        val playPausePending = if (snapshot.isPaused) {
            createActionPendingIntent(ACTION_PLAY, 2)
        } else {
            createActionPendingIntent(ACTION_PAUSE, 2)
        }
        val nextPending = createActionPendingIntent(ACTION_NEXT, 3)
        val stopPending = createActionPendingIntent(ACTION_STOP, 4)

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(stopPending)

        val subText = if (snapshot.totalParagraphs > 0) {
            val currentPara = (snapshot.position + 1).coerceIn(1, snapshot.totalParagraphs)
            "第 $currentPara/${snapshot.totalParagraphs} 段 · 菠萝蜜小说"
        } else {
            "菠萝蜜小说 · 听书"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tts_notification)
            .setContentTitle(snapshot.chapterTitle.ifBlank { "正在朗读" })
            .setContentText(snapshot.bookTitle.ifBlank { "小说阅读" })
            .setSubText(subText)
            .setContentIntent(pendingContent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setOngoing(!snapshot.isPaused)
            .setStyle(mediaStyle)
            .addAction(
                android.R.drawable.ic_media_previous,
                "上一章",
                prevPending
            )
            .addAction(
                if (snapshot.isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (snapshot.isPaused) "播放" else "暂停",
                playPausePending
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "下一章",
                nextPending
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止",
                stopPending
            )
            .build()
    }

    private fun createActionPendingIntent(actionStr: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TtsPlaybackService::class.java).apply {
            action = actionStr
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "听书播放与系统媒体控制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "在通知栏与控制中心展示听书媒体卡片，支持上一章、下一章、播放暂停等控制"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun clearForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun stopForegroundAndSelf() {
        clearForegroundNotification()
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        clearForegroundNotification()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        var instance: TtsPlaybackService? = null
            private set

        const val CHANNEL_ID = "novelai_tts_playback_channel"
        const val NOTIFICATION_ID = 2026

        const val ACTION_PLAY = "com.novelai.assistant.action.TTS_PLAY"
        const val ACTION_PAUSE = "com.novelai.assistant.action.TTS_PAUSE"
        const val ACTION_PREVIOUS = "com.novelai.assistant.action.TTS_PREVIOUS"
        const val ACTION_NEXT = "com.novelai.assistant.action.TTS_NEXT"
        const val ACTION_STOP = "com.novelai.assistant.action.TTS_STOP"
        const val ACTION_UPDATE_STATE = "com.novelai.assistant.action.TTS_UPDATE_STATE"

        const val EXTRA_BOOK_ID = "EXTRA_BOOK_ID"
    }
}
