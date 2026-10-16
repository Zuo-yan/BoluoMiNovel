package com.novelai.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.network.*
import com.novelai.assistant.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ProviderEditState(
    val isNew: Boolean = true, val config: ApiProviderConfig = newConfig(),
    val pingState: PingUiState? = null, val models: List<String> = emptyList(),
    val loadingModels: Boolean = false, val loadingCapability: Boolean = false,
    val modelsRequest: Int = 0,
    val maxTokensDraft: String = "4096", val error: String? = null, val saved: Boolean = false
) {
    companion object { fun newConfig() = ApiProviderConfig(providerName = "", baseUrl = "", apiKey = "", selectedModel = "",
        configVersion = 2, autoMaxTokens = true, temperatureEnabled = false) }
}

@HiltViewModel
class ProviderEditViewModel @Inject constructor(
    private val repository: AiConfigRepository, private val factory: AiClientFactory, private val metadata: ModelMetadataClient
) : ViewModel() {
    private val _state = MutableStateFlow(ProviderEditState())
    val state = _state.asStateFlow()
    private var loadedId: String? = null
    private var detection: Job? = null
    private var networkJob: Job? = null
    private var revision = 0
    fun load(id: String) {
        if (loadedId == id) return
        loadedId = id
        val config = repository.getProvider(id)
        _state.value = if (config == null) ProviderEditState() else ProviderEditState(isNew = false, config = config, maxTokensDraft = config.maxTokens.toString())
        if (config != null) detectCapability()
    }
    private fun identity(config: ApiProviderConfig) = listOf(config.baseUrl, config.selectedModel, config.kind.name, config.apiKey, config.customHeaders)
    fun update(transform: (ApiProviderConfig) -> ApiProviderConfig) {
        val before = state.value.config; val next = transform(before)
        val changed = identity(before) != identity(next)
        if (changed) { revision++; networkJob?.cancel() }
        _state.update { it.copy(config = if (changed) next.copy(capability = null) else next, error = null,
            models = if (changed) emptyList() else it.models, pingState = if (changed) null else it.pingState, loadingModels = if (changed) false else it.loadingModels) }
        if (changed) detectCapability()
    }
    fun setMaxTokens(text: String) {
        val draft = text.filter(Char::isDigit)
        _state.update { it.copy(maxTokensDraft = draft, config = it.config.copy(autoMaxTokens = false,
            maxTokens = draft.toIntOrNull()?.takeIf { n -> n > 0 } ?: it.config.maxTokens), error = null) }
    }
    fun setAuto(value: Boolean) {
        update { it.copy(autoMaxTokens = value) }
        if (value) detectCapability(immediate = true)
    }
    fun applyPreset(preset: ProviderPreset, platform: ProviderPlatform, workspace: String) {
        val address = platform.baseUrl.replace("{workspace}", workspace.trim())
        if (platform.workspace && !workspace.matches(Regex("[A-Za-z0-9-]+"))) { _state.update { it.copy(error = "请填写有效的 Workspace ID") }; return }
        update { it.copy(providerName = preset.name, presetId = preset.id, platformId = platform.id, baseUrl = address,
            kind = platform.kind, selectedModel = "", apiKey = "", customHeaders = emptyMap(), temperatureEnabled = false, configVersion = 2) }
    }
    fun detectCapability(immediate: Boolean = false) {
        detection?.cancel()
        val config = state.value.config; val current = revision
        if (config.baseUrl.isBlank() || config.selectedModel.isBlank()) { _state.update { it.copy(loadingCapability = false) }; return }
        detection = viewModelScope.launch {
            if (!immediate) delay(650)
            _state.update { it.copy(loadingCapability = true) }
            try {
                val cap = metadata.detect(config)
                if (revision == current && identity(state.value.config) == identity(config)) _state.update { old ->
                    val value = if (old.config.autoMaxTokens) cap.output else null
                    old.copy(config = old.config.copy(capability = cap, maxTokens = value ?: old.config.maxTokens),
                        maxTokensDraft = value?.toString() ?: old.maxTokensDraft)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (revision == current) _state.update { it.copy(error = "未能读取模型上限，已保留当前输出值") } }
            finally { if (revision == current) _state.update { it.copy(loadingCapability = false) } }
        }
    }
    fun testConnection() {
        networkJob?.cancel(); val config = state.value.config; val current = revision
        _state.update { it.copy(pingState = PingUiState.Testing) }
        networkJob = viewModelScope.launch {
            val result = factory.clientFor(config.kind).ping(config)
            if (revision == current) _state.update { it.copy(pingState = result.fold({ ms -> PingUiState.Ok(ms) }, { e -> PingUiState.Fail(e.message ?: "模型接口连接失败") })) }
        }
    }
    fun fetchModels() {
        networkJob?.cancel(); val config = state.value.config; val current = revision
        _state.update { it.copy(loadingModels = true) }
        networkJob = viewModelScope.launch {
            try {
                val result = factory.clientFor(config.kind).listModels(config)
                if (revision == current) _state.update { it.copy(models = result.getOrDefault(emptyList()), modelsRequest = it.modelsRequest + 1,
                    error = result.exceptionOrNull()?.message ?: if (result.getOrDefault(emptyList()).isEmpty()) "服务未返回模型列表，可手动填写模型 ID" else null) }
            } finally { if (revision == current) _state.update { it.copy(loadingModels = false) } }
        }
    }
    fun save(onDone: () -> Unit) {
        val config = state.value.config
        val error = when {
            config.providerName.isBlank() -> "请填写配置名称"
            state.value.isNew && (config.presetId.isBlank() || config.platformId.isBlank()) -> "请先选择厂商及平台／地域"
            runCatching { AiProtocol.base(config) }.isFailure -> "请填写有效的 Base URL"
            config.selectedModel.isBlank() -> "请选择或填写模型 ID"
            state.value.maxTokensDraft.toIntOrNull()?.takeIf { it > 0 } == null -> "请填写有效的最大输出 Token"
            else -> null
        }
        if (error != null) { _state.update { it.copy(error = error) }; return }
        repository.upsertProvider(config.copy(selectedModel = config.selectedModel.trim(), configVersion = 2))
        _state.update { it.copy(saved = true) }; onDone()
    }
    fun delete(onDone: () -> Unit) { repository.deleteProvider(state.value.config.id); onDone() }
}
