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
    OUTLINE("大纲与剧情规划", "剧情走向讨论、伏笔统筹与双层大纲梳理，推荐高阶推理模型；未单独指定时跟随续写"),
    CONTINUATION("续写场景", "长上下文拟合与文风模仿强的模型"),
    COMPANION("伴读场景", "响应快、成本低的小模型（同步用于章节记忆卡归档）"),
    EMBEDDING("向量重排（可选）", "配置支持 /embeddings 的服务商与模型以启用混合语义召回；留空则纯用本地段落 BM25"),
    TTS("AI 语音合成（可选）", "配置支持语音合成的服务商与 TTS 模型（如 Gemini TTS、OpenAI /audio/speech 兼容接口）以启用高拟真听书")
}

@Singleton
class AiConfigRepository @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _providers = MutableStateFlow(loadProviders())
    val providers: StateFlow<List<ApiProviderConfig>> = _providers.asStateFlow()

    private val _outlineRoute = MutableStateFlow(loadRoute(ROUTE_OUTLINE))
    val outlineRoute: StateFlow<ModelRoute> = _outlineRoute.asStateFlow()

    private val _continuationRoute = MutableStateFlow(loadRoute(ROUTE_CONTINUATION))
    val continuationRoute: StateFlow<ModelRoute> = _continuationRoute.asStateFlow()

    private val _companionRoute = MutableStateFlow(loadRoute(ROUTE_COMPANION))
    val companionRoute: StateFlow<ModelRoute> = _companionRoute.asStateFlow()

    private val _embeddingRoute = MutableStateFlow(loadRoute(ROUTE_EMBEDDING))
    val embeddingRoute: StateFlow<ModelRoute> = _embeddingRoute.asStateFlow()

    private val _ttsRoute = MutableStateFlow(loadRoute(ROUTE_TTS))
    val ttsRoute: StateFlow<ModelRoute> = _ttsRoute.asStateFlow()

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
        if (_outlineRoute.value.providerId == id) saveRoute(TaskScene.OUTLINE, ModelRoute())
        if (_continuationRoute.value.providerId == id) saveRoute(TaskScene.CONTINUATION, ModelRoute())
        if (_companionRoute.value.providerId == id) saveRoute(TaskScene.COMPANION, ModelRoute())
        if (_embeddingRoute.value.providerId == id) saveRoute(TaskScene.EMBEDDING, ModelRoute())
        if (_ttsRoute.value.providerId == id) saveRoute(TaskScene.TTS, ModelRoute())
    }

    fun getProvider(id: String?): ApiProviderConfig? = _providers.value.firstOrNull { it.id == id }

    fun enabledProviders(): List<ApiProviderConfig> = _providers.value.filter { it.isEnabled }

    fun route(scene: TaskScene): ModelRoute = when (scene) {
        TaskScene.OUTLINE -> _outlineRoute.value
        TaskScene.CONTINUATION -> _continuationRoute.value
        TaskScene.COMPANION -> _companionRoute.value
        TaskScene.EMBEDDING -> _embeddingRoute.value
        TaskScene.TTS -> _ttsRoute.value
    }

    fun saveRoute(scene: TaskScene, route: ModelRoute) {
        secureStorage.putPlain(
            when (scene) {
                TaskScene.OUTLINE -> ROUTE_OUTLINE
                TaskScene.CONTINUATION -> ROUTE_CONTINUATION
                TaskScene.COMPANION -> ROUTE_COMPANION
                TaskScene.EMBEDDING -> ROUTE_EMBEDDING
                TaskScene.TTS -> ROUTE_TTS
            },
            json.encodeToString(ModelRoute.serializer(), route)
        )
        when (scene) {
            TaskScene.OUTLINE -> _outlineRoute.value = route
            TaskScene.CONTINUATION -> _continuationRoute.value = route
            TaskScene.COMPANION -> _companionRoute.value = route
            TaskScene.EMBEDDING -> _embeddingRoute.value = route
            TaskScene.TTS -> _ttsRoute.value = route
        }
    }

    fun clearRoute(scene: TaskScene) {
        saveRoute(scene, ModelRoute())
    }

    /** 解析某场景当前生效的 (提供商, 模型)；EMBEDDING 与 TTS 不回退到聊天模型；OUTLINE 未单独指定时沿用续写模型 */
    fun resolveActive(scene: TaskScene): Pair<ApiProviderConfig, String>? =
        resolveRouteForScene(
            scene = scene,
            route = route(scene),
            providers = _providers.value,
            fallbackRoute = if (scene == TaskScene.OUTLINE) _continuationRoute.value else null
        )

    private fun loadRoute(key: String): ModelRoute {
        val raw = secureStorage.getPlain(key) ?: return ModelRoute()
        return runCatching { json.decodeFromString(ModelRoute.serializer(), raw) }.getOrDefault(ModelRoute())
    }

    companion object {
        private const val KEY_PROVIDERS = "api_providers_v1"
        private const val ROUTE_OUTLINE = "route_outline"
        private const val ROUTE_CONTINUATION = "route_continuation"
        private const val ROUTE_COMPANION = "route_companion"
        private const val ROUTE_EMBEDDING = "route_embedding"
        private const val ROUTE_TTS = "route_tts"

        val PRESETS get() = com.novelai.assistant.data.network.ProviderPresets.items

        internal fun resolveRouteForScene(
            scene: TaskScene,
            route: ModelRoute,
            providers: List<ApiProviderConfig>,
            fallbackRoute: ModelRoute? = null
        ): Pair<ApiProviderConfig, String>? {
            val enabled = providers.filter { it.isEnabled }
            if (scene == TaskScene.EMBEDDING || scene == TaskScene.TTS) {
                val provider = enabled.firstOrNull { it.id == route.providerId } ?: return null
                val model = route.model.trim().ifBlank { return null }
                return provider to model
            }
            val directProvider = enabled.firstOrNull { it.id == route.providerId }
            if (directProvider != null) {
                val model = route.model.ifBlank { directProvider.selectedModel }
                return directProvider to model
            }
            if (scene == TaskScene.OUTLINE && fallbackRoute != null) {
                val fallbackProvider = enabled.firstOrNull { it.id == fallbackRoute.providerId }
                if (fallbackProvider != null) {
                    val fallbackModel = fallbackRoute.model.ifBlank { fallbackProvider.selectedModel }
                    return fallbackProvider to fallbackModel
                }
            }
            val provider = enabled.firstOrNull() ?: return null
            val model = route.model.ifBlank { provider.selectedModel }
            return provider to model
        }
    }
}

/** In v0.15 the historical ANTHROPIC enum also sent Chat Completions. Preserve that behavior. */
fun migrateProviderConfig(config: ApiProviderConfig): ApiProviderConfig = if (config.configVersion < 2) {
    config.copy(configVersion = 2, kind = if (config.kind == ProviderKind.ANTHROPIC) ProviderKind.OPENAI_COMPATIBLE else config.kind,
        chatTokenField = "max_tokens")
} else config
