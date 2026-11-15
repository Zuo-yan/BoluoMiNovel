package com.novelai.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.prefs.AppThemeMode
import com.novelai.assistant.data.prefs.ReaderBgTheme
import com.novelai.assistant.data.prefs.ReadingPreferencesRepository
import com.novelai.assistant.data.prefs.TtsEngineMode
import com.novelai.assistant.data.repository.AiConfigRepository
import com.novelai.assistant.data.repository.ApiProviderConfig
import com.novelai.assistant.data.repository.ModelRoute
import com.novelai.assistant.data.repository.TaskScene
import com.novelai.assistant.data.repository.TokenUsage
import com.novelai.assistant.data.repository.UsageRepository
import com.novelai.assistant.data.network.AiClientFactory
import com.novelai.assistant.data.network.EmbeddingClient
import com.novelai.assistant.data.network.ModelListPurpose
import com.novelai.assistant.data.network.ProviderKind
import com.novelai.assistant.data.network.ProviderPresets
import com.novelai.assistant.data.network.TtsSynthesisClient
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

sealed interface EmbeddingTestUiState {
    data object Idle : EmbeddingTestUiState
    data object Testing : EmbeddingTestUiState
    data class Ok(val dimensions: Int, val latencyMs: Long) : EmbeddingTestUiState
    data class Fail(val message: String) : EmbeddingTestUiState
}

sealed interface TtsTestUiState {
    data object Idle : TtsTestUiState
    data object Testing : TtsTestUiState
    data class Ok(val latencyMs: Long) : TtsTestUiState
    data class Fail(val message: String) : TtsTestUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val aiConfigRepository: AiConfigRepository,
    private val usageRepository: UsageRepository,
    private val readingPreferencesRepository: ReadingPreferencesRepository,
    private val aiClientFactory: AiClientFactory,
    private val embeddingClient: EmbeddingClient,
    private val ttsSynthesisClient: TtsSynthesisClient
) : ViewModel() {

    val providers = aiConfigRepository.providers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outlineRoute: StateFlow<ModelRoute> = aiConfigRepository.outlineRoute
    val continuationRoute: StateFlow<ModelRoute> = aiConfigRepository.continuationRoute
    val companionRoute: StateFlow<ModelRoute> = aiConfigRepository.companionRoute
    val embeddingRoute: StateFlow<ModelRoute> = aiConfigRepository.embeddingRoute
    val ttsRoute: StateFlow<ModelRoute> = aiConfigRepository.ttsRoute

    private val _embeddingTestState = MutableStateFlow<EmbeddingTestUiState>(EmbeddingTestUiState.Idle)
    val embeddingTestState: StateFlow<EmbeddingTestUiState> = _embeddingTestState.asStateFlow()
    private var embeddingTestJob: Job? = null

    private val _ttsTestState = MutableStateFlow<TtsTestUiState>(TtsTestUiState.Idle)
    val ttsTestState: StateFlow<TtsTestUiState> = _ttsTestState.asStateFlow()
    private var ttsTestJob: Job? = null

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

    fun fetchModelsFor(providerId: String, scene: TaskScene = TaskScene.CONTINUATION) {
        fetchModelsJob?.cancel()
        val config = aiConfigRepository.getProvider(providerId) ?: return
        activeFetchProviderId = providerId
        _loadingRouteModels.value = true
        _routeModelError.value = null
        val purpose = when (scene) {
            TaskScene.EMBEDDING -> ModelListPurpose.EMBEDDING
            TaskScene.TTS -> ModelListPurpose.TTS
            else -> ModelListPurpose.CHAT
        }
        fetchModelsJob = viewModelScope.launch {
            try {
                val result = aiClientFactory.clientFor(config.kind).listModels(config, purpose)
                if (activeFetchProviderId != providerId) return@launch
                if (result.isSuccess) {
                    val fetched = result.getOrDefault(emptyList())
                    _routeModels.value = fetched
                    if (fetched.isEmpty() && scene == TaskScene.TTS) {
                        _routeModelError.value = "接口已连通，但 /models 未返回标识为 TTS 的模型，可直接点选上方推荐或手动输入"
                    }
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
    fun getRecommendedModels(providerId: String, scene: TaskScene = TaskScene.CONTINUATION): List<String> {
        val config = aiConfigRepository.getProvider(providerId) ?: return emptyList()
        if (scene == TaskScene.EMBEDDING) {
            return when {
                config.kind == ProviderKind.GOOGLE_GEMINI ||
                    config.presetId == "google" ||
                    config.baseUrl.contains("googleapis", ignoreCase = true) ->
                    listOf("gemini-embedding-2", "gemini-embedding-001")
                config.presetId == "ollama" || config.baseUrl.contains("11434") ->
                    listOf("bge-m3", "nomic-embed-text", "mxbai-embed-large")
                config.presetId == "siliconflow" || config.baseUrl.contains("siliconflow", ignoreCase = true) ->
                    listOf("Qwen/Qwen3-Embedding-8B", "BAAI/bge-m3", "BAAI/bge-large-zh-v1.5")
                config.kind == ProviderKind.ANTHROPIC -> emptyList()
                else ->
                    listOf("text-embedding-3-large", "text-embedding-3-small", "BAAI/bge-m3", "nomic-embed-text")
            }
        }
        if (scene == TaskScene.TTS) {
            return TtsSynthesisClient.recommendedTtsModels(config)
        }
        return ProviderPresets.recommendedModels(config.presetId, config.baseUrl, config.selectedModel)
    }

    fun testTts(providerId: String, model: String, voice: String = "") {
        ttsTestJob?.cancel()
        val config = aiConfigRepository.getProvider(providerId)
        val trimmedModel = model.trim()
        if (config == null || trimmedModel.isBlank()) {
            _ttsTestState.value = TtsTestUiState.Fail("请先选择服务商并填写 TTS 模型 ID")
            return
        }
        _ttsTestState.value = TtsTestUiState.Testing
        ttsTestJob = viewModelScope.launch {
            val result = ttsSynthesisClient.test(config, trimmedModel, voice)
            _ttsTestState.value = result.fold(
                onSuccess = { latencyMs -> TtsTestUiState.Ok(latencyMs) },
                onFailure = { err -> TtsTestUiState.Fail(err.message ?: "语音合成测试失败") }
            )
        }
    }

    fun clearTtsTest() {
        ttsTestJob?.cancel()
        _ttsTestState.value = TtsTestUiState.Idle
    }

    fun testEmbedding(providerId: String, model: String) {
        embeddingTestJob?.cancel()
        val config = aiConfigRepository.getProvider(providerId)
        val trimmedModel = model.trim()
        if (config == null || trimmedModel.isBlank()) {
            _embeddingTestState.value = EmbeddingTestUiState.Fail("请先选择服务商并填写向量模型 ID")
            return
        }
        _embeddingTestState.value = EmbeddingTestUiState.Testing
        embeddingTestJob = viewModelScope.launch {
            val started = System.currentTimeMillis()
            val result = embeddingClient.test(config, trimmedModel)
            val elapsed = System.currentTimeMillis() - started
            _embeddingTestState.value = result.fold(
                onSuccess = { dims -> EmbeddingTestUiState.Ok(dims, elapsed) },
                onFailure = { err -> EmbeddingTestUiState.Fail(err.message ?: "向量接口测试失败") }
            )
        }
    }

    fun clearEmbeddingTest() {
        embeddingTestJob?.cancel()
        _embeddingTestState.value = EmbeddingTestUiState.Idle
    }

    fun clearRoute(scene: TaskScene) {
        aiConfigRepository.clearRoute(scene)
        if (scene == TaskScene.TTS) {
            viewModelScope.launch {
                readingPreferencesRepository.setTtsEngineMode(TtsEngineMode.SYSTEM)
            }
        }
    }

    fun toggleEnabled(config: ApiProviderConfig, enabled: Boolean) {
        aiConfigRepository.upsertProvider(config.copy(isEnabled = enabled))
    }

    /** 一键将指定提供商设为全局默认（同步应用到续写与伴读场景；已单独指派的高阶大纲模型保留不变） */
    fun setDefaultProvider(config: ApiProviderConfig) {
        val model = config.selectedModel
        aiConfigRepository.saveRoute(TaskScene.CONTINUATION, ModelRoute(config.id, model))
        aiConfigRepository.saveRoute(TaskScene.COMPANION, ModelRoute(config.id, model))
    }

    fun saveRoute(scene: TaskScene, providerId: String, model: String) {
        val trimmedModel = model.trim()
        aiConfigRepository.saveRoute(scene, ModelRoute(providerId, trimmedModel))
        if (scene == TaskScene.TTS && providerId.isNotBlank() && trimmedModel.isNotBlank()) {
            viewModelScope.launch {
                readingPreferencesRepository.setTtsEngineMode(TtsEngineMode.AI_MODEL)
            }
        }
    }

    fun setAppTheme(mode: AppThemeMode) {
        viewModelScope.launch {
            readingPreferencesRepository.setAppThemeMode(mode)
            if (mode == AppThemeMode.DARK) {
                readingPreferencesRepository.setBgTheme(ReaderBgTheme.DARK)
            } else if (mode == AppThemeMode.LIGHT) {
                readingPreferencesRepository.setBgTheme(ReaderBgTheme.WHITE)
            }
        }
    }

    fun resetUsage() {
        viewModelScope.launch { usageRepository.reset() }
    }
}
