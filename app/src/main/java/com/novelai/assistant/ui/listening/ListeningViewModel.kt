package com.novelai.assistant.ui.listening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.network.AiTtsVoice
import com.novelai.assistant.data.prefs.TtsEngineMode
import com.novelai.assistant.data.repository.AiConfigRepository
import com.novelai.assistant.data.repository.BookRepository
import com.novelai.assistant.data.repository.TaskScene
import com.novelai.assistant.data.tts.TtsPlayer
import com.novelai.assistant.data.tts.TtsTimerMode
import com.novelai.assistant.data.tts.TtsVoiceOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListeningViewModel @Inject constructor(
    private val ttsPlayer: TtsPlayer,
    private val bookRepository: BookRepository,
    private val aiConfigRepository: AiConfigRepository
) : ViewModel() {

    val speaking: StateFlow<Boolean> = ttsPlayer.speaking
    val paused: StateFlow<Boolean> = ttsPlayer.paused
    val position: StateFlow<Int> = ttsPlayer.position
    val rate: StateFlow<Float> = ttsPlayer.rate
    val currentBookId: StateFlow<String> = ttsPlayer.currentBookId
    val currentBookTitle: StateFlow<String> = ttsPlayer.currentBookTitle
    val currentChapterIndex: StateFlow<Int> = ttsPlayer.currentChapterIndex
    val currentChapterTitle: StateFlow<String> = ttsPlayer.currentChapterTitle
    val currentParagraphText: StateFlow<String> = ttsPlayer.currentParagraphText

    val voices: StateFlow<List<TtsVoiceOption>> = ttsPlayer.voices
    val voiceName: StateFlow<String?> = ttsPlayer.voiceName

    val engineMode: StateFlow<TtsEngineMode> = ttsPlayer.engineMode
    val aiVoice: StateFlow<String> = ttsPlayer.aiVoice
    val aiVoices: StateFlow<List<AiTtsVoice>> = ttsPlayer.aiVoices
    val aiBuffering: StateFlow<Boolean> = ttsPlayer.aiBuffering
    val aiFallbackEnabled: StateFlow<Boolean> = ttsPlayer.aiFallbackEnabled
    val ttsLastError: StateFlow<String?> = ttsPlayer.lastError

    /** 当前生效的 AI TTS 模型描述（null 表示尚未在任务分流中配置） */
    val activeAiTtsLabel: StateFlow<String?> = combine(
        aiConfigRepository.ttsRoute,
        aiConfigRepository.providers
    ) { _, _ ->
        aiConfigRepository.resolveActive(TaskScene.TTS)?.let { (provider, model) ->
            "${provider.providerName} · $model"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val timerMode: StateFlow<TtsTimerMode> = ttsPlayer.timerMode
    val timerRemainingSeconds: StateFlow<Int?> = ttsPlayer.timerRemainingSeconds

    val currentBook: StateFlow<BookEntity?> = ttsPlayer.currentBookId
        .flatMapLatest { id ->
            if (id.isNotBlank()) bookRepository.observeBook(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun togglePlayPause() {
        if (speaking.value && !paused.value) {
            ttsPlayer.pause()
        } else {
            ttsPlayer.resume()
        }
    }

    fun playPrevious() {
        ttsPlayer.playPreviousChapterAuto()
    }

    fun playNext() {
        ttsPlayer.playNextChapterAuto()
    }

    fun stop() {
        ttsPlayer.stop()
    }

    fun setRate(speed: Float) {
        ttsPlayer.setRate(speed)
    }

    fun setVoice(name: String?) {
        ttsPlayer.setVoice(name)
    }

    fun auditionVoice(name: String?) {
        ttsPlayer.auditionVoice(name)
    }

    fun setEngineMode(mode: TtsEngineMode) {
        ttsPlayer.setEngineMode(mode)
    }

    fun setAiVoice(voiceId: String) {
        ttsPlayer.setAiVoice(voiceId)
    }

    fun auditionAiVoice(voiceId: String) {
        ttsPlayer.auditionAiVoice(voiceId)
    }

    fun refreshAiVoices() {
        ttsPlayer.refreshAiVoices()
    }

    fun setAiFallbackEnabled(enabled: Boolean) {
        ttsPlayer.setAiFallbackToSystem(enabled)
    }

    fun clearTtsError() {
        ttsPlayer.clearError()
    }

    fun setTimerMode(mode: TtsTimerMode) {
        ttsPlayer.setTimerMode(mode)
    }
}
