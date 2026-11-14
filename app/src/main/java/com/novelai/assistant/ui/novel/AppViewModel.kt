package com.novelai.assistant.ui.novel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.prefs.AppThemeMode
import com.novelai.assistant.data.prefs.ReadingPreferencesRepository
import com.novelai.assistant.data.tts.ListeningNavBus
import com.novelai.assistant.data.tts.TtsPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 应用级状态：新手引导完成标记、全局主题模式、全局听书悬浮组件与事件分发 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val readingPreferencesRepository: ReadingPreferencesRepository,
    private val ttsPlayer: TtsPlayer,
    val listeningNavBus: ListeningNavBus
) : ViewModel() {

    /** null = 加载中；true = 已完成引导 */
    val onboardingDone: StateFlow<Boolean?> = readingPreferencesRepository.onboardingDoneFlow
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val appThemeMode: StateFlow<AppThemeMode> = readingPreferencesRepository.settings
        .map { it.appThemeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeMode.FOLLOW_SYSTEM)

    val ttsSpeaking: StateFlow<Boolean> = ttsPlayer.speaking
    val ttsPaused: StateFlow<Boolean> = ttsPlayer.paused
    val ttsBookId: StateFlow<String> = ttsPlayer.currentBookId
    val ttsBookTitle: StateFlow<String> = ttsPlayer.currentBookTitle
    val ttsChapterTitle: StateFlow<String> = ttsPlayer.currentChapterTitle

    val listenPillPosition: StateFlow<Pair<Float, Float>> = readingPreferencesRepository.listenPillPosition
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.78f to 0.52f)

    fun toggleTtsPlayPause() {
        if (ttsPlayer.paused.value) ttsPlayer.resume() else ttsPlayer.pause()
    }

    fun saveListenPillPosition(xFrac: Float, yFrac: Float) {
        viewModelScope.launch { readingPreferencesRepository.setListenPillPosition(xFrac, yFrac) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { readingPreferencesRepository.setOnboardingDone() }
    }
}
