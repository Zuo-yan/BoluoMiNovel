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
import dagger.hilt.android.lifecycle.HiltViewModel
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

    private val _pingResults = MutableStateFlow<Map<String, PingUiState>>(emptyMap())
    val pingResults: StateFlow<Map<String, PingUiState>> = _pingResults.asStateFlow()

    /** 任务分流弹窗：从提供商拉取的模型列表 */
    private val _routeModels = MutableStateFlow<List<String>>(emptyList())
    val routeModels: StateFlow<List<String>> = _routeModels.asStateFlow()

    private val _routeModelError = MutableStateFlow<String?>(null)
    val routeModelError: StateFlow<String?> = _routeModelError.asStateFlow()

    private val _loadingRouteModels = MutableStateFlow(false)
    val loadingRouteModels: StateFlow<Boolean> = _loadingRouteModels.asStateFlow()

    fun fetchModelsFor(providerId: String) {
        val config = aiConfigRepository.getProvider(providerId) ?: return
        _loadingRouteModels.value = true
        _routeModelError.value = null
        viewModelScope.launch {
            val result = aiClientFactory.clientFor(config.kind).listModels(config)
            if (result.isSuccess) {
                _routeModels.value = result.getOrDefault(emptyList())
            } else {
                _routeModels.value = emptyList()
                _routeModelError.value = result.exceptionOrNull()?.message ?: "拉取模型列表失败，请检查网络或 API Key"
            }
            _loadingRouteModels.value = false
        }
    }

    fun clearRouteModels() {
        _routeModels.value = emptyList()
        _routeModelError.value = null
    }

    /** 提供常见商用模型的快捷候选，免去手动输入 */
    fun getRecommendedModels(providerId: String): List<String> {
        val config = aiConfigRepository.getProvider(providerId) ?: return emptyList()
        val preset = config.presetId
        val host = runCatching { java.net.URI(config.baseUrl).host }.getOrNull().orEmpty()
        val candidates = when {
            preset == "deepseek" || host.contains("deepseek") -> listOf("deepseek-chat", "deepseek-reasoner")
            preset == "anthropic" || host.contains("anthropic") -> listOf("claude-3-7-sonnet-20250219", "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229")
            preset == "openai" || host.contains("openai") -> listOf("gpt-4o", "gpt-4o-mini", "o3-mini", "gpt-4.5-preview")
            preset == "google" || host.contains("googleapis") -> listOf("gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash")
            preset == "moonshot" || host.contains("moonshot") -> listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")
            preset == "zai" || host.contains("bigmodel.cn") || host.contains("z.ai") -> listOf("glm-4-plus", "glm-4-flash", "glm-4-long")
            preset == "aliyun" || host.contains("aliyuncs.com") -> listOf("qwen-max", "qwen-plus", "qwen-turbo")
            else -> if (config.selectedModel.isNotBlank()) listOf(config.selectedModel) else emptyList()
        }
        return if (config.selectedModel.isNotBlank() && !candidates.contains(config.selectedModel)) {
            listOf(config.selectedModel) + candidates
        } else candidates
    }

    fun pingProvider(providerId: String) {
        val config = aiConfigRepository.getProvider(providerId) ?: return
        _pingResults.value = _pingResults.value + (providerId to PingUiState.Testing)
        viewModelScope.launch {
            val result = aiClientFactory.clientFor(config.kind).ping(config)
            _pingResults.value = _pingResults.value + (providerId to when {
                result.isSuccess -> PingUiState.Ok(result.getOrDefault(0L))
                else -> PingUiState.Fail(result.exceptionOrNull()?.message ?: "失败")
            })
        }
    }

    fun toggleEnabled(config: ApiProviderConfig, enabled: Boolean) {
        aiConfigRepository.upsertProvider(config.copy(isEnabled = enabled))
    }

    fun deleteProvider(id: String) = aiConfigRepository.deleteProvider(id)

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
