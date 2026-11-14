package com.novelai.assistant

import com.novelai.assistant.data.tts.TtsSleepTimer
import com.novelai.assistant.data.tts.TtsTimerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TtsSleepTimerTest {

    @Test
    fun setModeUpdatesStateAndResetClearsIt() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var expired = false
        val timer = TtsSleepTimer(scope) { expired = true }

        assertEquals(TtsTimerMode.OFF, timer.timerMode.value)
        assertNull(timer.timerRemainingSeconds.value)

        timer.setMode(TtsTimerMode.END_OF_CHAPTER)
        assertEquals(TtsTimerMode.END_OF_CHAPTER, timer.timerMode.value)
        assertNull(timer.timerRemainingSeconds.value)

        timer.setMode(TtsTimerMode.MIN_15)
        assertEquals(TtsTimerMode.MIN_15, timer.timerMode.value)
        assertEquals(15 * 60, timer.timerRemainingSeconds.value)

        timer.reset()
        assertEquals(TtsTimerMode.OFF, timer.timerMode.value)
        assertNull(timer.timerRemainingSeconds.value)
        assertFalse(expired)

        scope.cancel()
    }
}
