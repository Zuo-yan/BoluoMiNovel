package com.novelai.assistant.data.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 听书定时停止控制器：
 * 独立管理定时模式、剩余秒数倒计时协程及到期回调，脱离 UI 生命周期。
 */
class TtsSleepTimer(
    private val scope: CoroutineScope,
    private val onExpired: () -> Unit
) {
    private val _timerMode = MutableStateFlow(TtsTimerMode.OFF)
    val timerMode: StateFlow<TtsTimerMode> = _timerMode.asStateFlow()

    private val _timerRemainingSeconds = MutableStateFlow<Int?>(null)
    val timerRemainingSeconds: StateFlow<Int?> = _timerRemainingSeconds.asStateFlow()

    private var timerJob: Job? = null

    fun setMode(mode: TtsTimerMode) {
        timerJob?.cancel()
        timerJob = null
        _timerMode.value = mode
        if (mode.minutes > 0) {
            val totalSeconds = mode.minutes * 60
            _timerRemainingSeconds.value = totalSeconds
            timerJob = scope.launch {
                var remaining = totalSeconds
                while (remaining > 0) {
                    delay(1000L)
                    remaining -= 1
                    _timerRemainingSeconds.value = remaining
                }
                timerJob = null
                _timerMode.value = TtsTimerMode.OFF
                _timerRemainingSeconds.value = null
                onExpired()
            }
        } else {
            _timerRemainingSeconds.value = null
        }
    }

    fun reset() {
        timerJob?.cancel()
        timerJob = null
        _timerMode.value = TtsTimerMode.OFF
        _timerRemainingSeconds.value = null
    }
}
