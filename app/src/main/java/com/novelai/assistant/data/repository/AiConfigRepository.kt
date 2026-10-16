package com.novelai.assistant.data.repository

import com.novelai.assistant.data.network.ProviderKind
import com.novelai.assistant.data.security.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ApiProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val providerName: String,
    val baseUrl: String,
    val apiKey: String,
    val selectedModel: String,
    val kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    val customHeaders: Map<String, String> = emptyMap(),
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val isEnabled: Boolean = true,
    val configVersion: Int = 1,
    val autoMaxTokens: Boolean = false,
    val temperatureEnabled: Boolean = true,
    val chatTokenField: String = "auto",
    val presetId: String = "",
    val platformId: String = "",
    val capability: com.novelai.assistant.data.network.ModelCapability? = null
)

/** 任务分流：某场景使用哪个提供商的哪个模型 */
@Serializable
data class ModelRoute(
    val providerId: String? = null,
    val model: String = ""
)

enum class TaskScene(val label: String, val hint: String) {
    CONTINUATION("续写场景", "长上下文拟合与文风模仿强的模型"),
    COMPANION("伴读场景", "响应快、成本低的小模型")
}

@Singleton
class AiConfigRepository @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _providers = MutableStateFlow(loadProviders())
    val providers: StateFlow<List<ApiProviderConfig>> = _providers.asStateFlow()

    private val _continuationRoute = MutableStateFlow(loadRoute(ROUTE_CONTINUATION))
    val continuationRoute: StateFlow<ModelRoute> = _continuationRoute.asStateFlow()

    private val _companionRoute = MutableStateFlow(loadRoute(ROUTE_COMPANION))
    val companionRoute: StateFlow<ModelRoute> = _companionRoute.asStateFlow()

    private fun loadProviders(): List<ApiProviderConfig> {
        val raw = secureStorage.getDecrypted(KEY_PROVIDERS) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ApiProviderConfig.serializer()), raw).map(::migrateProviderConfig)
        }.getOrDefault(emptyList())
    }

    private fun persistProviders(list: List<ApiProviderConfig>) {
        secureStorage.putEncrypted(KEY_PROVIDERS, json.encodeToString(ListSerializer(ApiProviderConfig.serializer()), list))
        _providers.value = list
    }

    fun upsertProvider(config: ApiProviderConfig) {
        val list = _providers.value.toMutableList()
        val index = list.indexOfFirst { it.id == config.id }
        if (index >= 0) list[index] = config else list.add(config)
        persistProviders(list)
    }

    fun deleteProvider(id: String) {
        persistProviders(_providers.value.filterNot { it.id == id })
        // 清理指向该提供商的路由
        if (_continuationRoute.value.providerId == id) saveRoute(TaskScene.CONTINUATION, ModelRoute())
        if (_companionRoute.value.providerId == id) saveRoute(TaskScene.COMPANION, ModelRoute())
    }

    fun getProvider(id: String?): ApiProviderConfig? = _providers.value.firstOrNull { it.id == id }

    fun enabledProviders(): List<ApiProviderConfig> = _providers.value.filter { it.isEnabled }

    fun route(scene: TaskScene): ModelRoute = when (scene) {
        TaskScene.CONTINUATION -> _continuationRoute.value
        TaskScene.COMPANION -> _companionRoute.value
    }

    fun saveRoute(scene: TaskScene, route: ModelRoute) {
        secureStorage.putPlain(
            when (scene) {
                TaskScene.CONTINUATION -> ROUTE_CONTINUATION
                TaskScene.COMPANION -> ROUTE_COMPANION
            },
            json.encodeToString(ModelRoute.serializer(), route)
        )
        when (scene) {
            TaskScene.CONTINUATION -> _continuationRoute.value = route
            TaskScene.COMPANION -> _companionRoute.value = route
        }
    }

    /** 解析某场景当前生效的 (提供商, 模型)，路由未配置时回退到第一个启用的提供商 */
    fun resolveActive(scene: TaskScene): Pair<ApiProviderConfig, String>? {
        val route = route(scene)
        val provider = getProvider(route.providerId) ?: enabledProviders().firstOrNull()
            ?: return null
        val model = route.model.ifBlank { provider.selectedModel }
        return provider to model
    }

    private fun loadRoute(key: String): ModelRoute {
        val raw = secureStorage.getPlain(key) ?: return ModelRoute()
        return runCatching { json.decodeFromString(ModelRoute.serializer(), raw) }.getOrDefault(ModelRoute())
    }

    companion object {
        private const val KEY_PROVIDERS = "api_providers_v1"
        private const val ROUTE_CONTINUATION = "route_continuation"
        private const val ROUTE_COMPANION = "route_companion"

        val PRESETS get() = com.novelai.assistant.data.network.ProviderPresets.items
    }
}

/** In v0.15 the historical ANTHROPIC enum also sent Chat Completions. Preserve that behavior. */
fun migrateProviderConfig(config: ApiProviderConfig): ApiProviderConfig = if (config.configVersion < 2) {
    config.copy(configVersion = 2, kind = if (config.kind == ProviderKind.ANTHROPIC) ProviderKind.OPENAI_COMPATIBLE else config.kind,
        chatTokenField = "max_tokens")
} else config
