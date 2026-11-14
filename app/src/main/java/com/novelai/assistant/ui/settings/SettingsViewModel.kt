package com.novelai.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.prefs.AppThemeMode
import com.novelai.assistant.data.prefs.ReaderBgTheme
import com.novelai.assistant.data.prefs.ReadingPreferencesRepository
import com.novelai.assistant.data.repository.AiConfigRepository
import com.novelai.assistant.data.repository.ApiProviderConfig
import com.novelai.assistant.data.repository.ModelRoute
import com.novelai.assistant.data.repository.TaskScene
import com.novelai.assistant.data.repository.TokenUsage
import com.novelai.assistant.data.repository.UsageRepository
import com.novelai.assistant.data.network.AiClientFactory
import com.novelai.assistant.data.network.ProviderPresets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PingUiState {
    data object Testing : PingUiState
    data class Ok(val latencyMs: Long) : PingUiState
    data class Fail(val message: String) : PingUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val aiConfigRepository: AiConfigRepository,
    private val usageRepository: UsageRepository,
    private val readingPreferencesRepository: ReadingPreferencesRepository,
    private val aiClientFactory: AiClientFactory
) : ViewModel() {

    val providers = aiConfigRepository.providers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continuationRoute: StateFlow<ModelRoute> = aiConfigRepository.continuationRoute
    val companionRoute: StateFlow<ModelRoute> = aiConfigRepository.companionRoute

    val usage: StateFlow<TokenUsage> = usageRepository.usage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TokenUsage())

    val appThemeMode: StateFlow<AppThemeMode> = readingPreferencesRepository.settings
        .map { it.appThemeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppThemeMode.FOLLOW_SYSTEM)

    /** 任务分流弹窗：从提供商拉取的模型列表 */
    private val _routeModels = MutableStateFlow<List<String>>(emptyList())
    val routeModels: StateFlow<List<String>> = _routeModels.asStateFlow()

    private val _routeModelError = MutableStateFlow<String?>(null)
    val routeModelError: StateFlow<String?> = _routeModelError.asStateFlow()

    private val _loadingRouteModels = MutableStateFlow(false)
    val loadingRouteModels: StateFlow<Boolean> = _loadingRouteModels.asStateFlow()

    private var fetchModelsJob: Job? = null
    private var activeFetchProviderId: String? = null

    fun fetchModelsFor(providerId: String) {
        fetchModelsJob?.cancel()
        val config = aiConfigRepository.getProvider(providerId) ?: return
        activeFetchProviderId = providerId
        _loadingRouteModels.value = true
        _routeModelError.value = null
        fetchModelsJob = viewModelScope.launch {
            try {
                val result = aiClientFactory.clientFor(config.kind).listModels(config)
                if (activeFetchProviderId != providerId) return@launch
                if (result.isSuccess) {
                    _routeModels.value = result.getOrDefault(emptyList())
                } else {
                    _routeModels.value = emptyList()
                    _routeModelError.value = result.exceptionOrNull()?.message ?: "拉取模型列表失败，请检查网络或 API Key"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (activeFetchProviderId == providerId) {
                    _routeModels.value = emptyList()
                    _routeModelError.value = t.message ?: "拉取模型列表失败，请检查网络或 API Key"
                }
            } finally {
                if (activeFetchProviderId == providerId) {
                    _loadingRouteModels.value = false
                }
            }
        }
    }

    fun clearRouteModels() {
        fetchModelsJob?.cancel()
        activeFetchProviderId = null
        _loadingRouteModels.value = false
        _routeModels.value = emptyList()
        _routeModelError.value = null
    }

    /** 提供常见商用模型的快捷候选，免去手动输入 */
    fun getRecommendedModels(providerId: String): List<String> {
        val config = aiConfigRepository.getProvider(providerId) ?: return emptyList()
        return ProviderPresets.recommendedModels(config.presetId, config.baseUrl, config.selectedModel)
    }

    fun toggleEnabled(config: ApiProviderConfig, enabled: Boolean) {
        aiConfigRepository.upsertProvider(config.copy(isEnabled = enabled))
    }

    /** 一键将指定提供商设为全局默认（同步应用到续写与伴读场景） */
    fun setDefaultProvider(config: ApiProviderConfig) {
        val model = config.selectedModel
        aiConfigRepository.saveRoute(TaskScene.CONTINUATION, ModelRoute(config.id, model))
        aiConfigRepository.saveRoute(TaskScene.COMPANION, ModelRoute(config.id, model))
    }

    fun saveRoute(scene: TaskScene, providerId: String, model: String) {
        aiConfigRepository.saveRoute(scene, ModelRoute(providerId, model.trim()))
    }

    fun setAppTheme(mode: AppThemeMode) {
        viewModelScope.launch {
            readingPreferencesRepository.setAppThemeMode(mode)
            val cur = readingPreferencesRepository.current()
            if (mode == AppThemeMode.DARK && (cur.bgTheme == ReaderBgTheme.WHITE || cur.bgTheme == ReaderBgTheme.SEPIA || cur.bgTheme == ReaderBgTheme.GREEN)) {
                readingPreferencesRepository.setBgTheme(ReaderBgTheme.DARK)
            } else if (mode == AppThemeMode.LIGHT && (cur.bgTheme == ReaderBgTheme.DARK || cur.bgTheme == ReaderBgTheme.BLACK)) {
                readingPreferencesRepository.setBgTheme(ReaderBgTheme.WHITE)
            }
        }
    }

    fun resetUsage() {
        viewModelScope.launch { usageRepository.reset() }
    }
}
