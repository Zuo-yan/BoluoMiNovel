package com.novelai.assistant.data.network

import com.novelai.assistant.data.repository.ApiProviderConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.URI
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ModelCapability(val baseUrl: String, val model: String, val protocol: ProviderKind,
    val output: Int? = null, val context: Int? = null, val source: String = "未知", val checkedAt: Long = 0L, val sourceUrl: String = "") {
    fun matches(config: ApiProviderConfig) = baseUrl == config.baseUrl.trim().trimEnd('/') && model == config.selectedModel && protocol == config.kind
}

enum class ModelListPurpose {
    CHAT,
    EMBEDDING,
    TTS,
    ALL
}

object ModelMetadata {
    private fun positive(obj: JsonObject, vararg keys: String): Int? = keys.firstNotNullOfOrNull { key -> obj.str(key).toIntOrNull()?.takeIf { it > 0 } }
    fun parse(config: ApiProviderConfig, obj: JsonObject, source: String = "接口返回"): ModelCapability {
        return ModelCapability(config.baseUrl.trim().trimEnd('/'), config.selectedModel, config.kind,
            output = positive(obj, "outputTokenLimit", "max_tokens", "max_output_tokens", "max_output_token_limit"),
            context = positive(obj, "inputTokenLimit", "max_input_tokens", "context_window", "context_length"),
            source = source, checkedAt = System.currentTimeMillis())
    }
    /** Exact model IDs on the official endpoint only. Never infer relay limits from names. */
    fun builtin(config: ApiProviderConfig): ModelCapability? {
        val host = runCatching { URI(config.baseUrl).host }.getOrNull()
        val pair = when {
            host == "api.openai.com" && config.kind in listOf(ProviderKind.OPENAI_COMPATIBLE, ProviderKind.OPENAI_RESPONSES) -> when (config.selectedModel) {
                "gpt-4o-mini", "gpt-4o-mini-2024-07-18" -> 128000 to 16384
                "gpt-4.1", "gpt-4.1-2025-04-14" -> 1047576 to 32768
                "gpt-6-sol", "gpt-6.1-sol" -> 1050000 to 128000
                else -> return null
            }
            host == "api.deepseek.com" && config.kind != ProviderKind.GOOGLE_GEMINI && config.selectedModel in listOf("deepseek-flash", "deepseek-v4-pro") -> null to 393216
            host == "api.anthropic.com" && config.kind == ProviderKind.ANTHROPIC -> when {
                config.selectedModel.startsWith("claude-3-7-sonnet") -> 200000 to 8192
                config.selectedModel.startsWith("claude-3-5-sonnet") -> 200000 to 8192
                config.selectedModel.startsWith("claude-3-5-haiku") -> 200000 to 8192
                config.selectedModel.startsWith("claude-3-opus") -> 200000 to 4096
                else -> 200000 to 4096
            }
            else -> return null
        }
        val sourceUrl = when {
            host == "api.deepseek.com" -> "https://api-docs.deepseek.com/quick_start/pricing/"
            host == "api.anthropic.com" -> "https://docs.anthropic.com/en/docs/about-claude/models"
            else -> "https://developers.openai.com/api/docs/models/${config.selectedModel.substringBefore("-202")}"
        }
        return ModelCapability(config.baseUrl.trim().trimEnd('/'), config.selectedModel, config.kind, pair.second, pair.first,
            "内置官方资料 · 2026-10-16", System.currentTimeMillis(), sourceUrl)
    }
}

@Singleton
class ModelMetadataClient @Inject constructor(client: HttpClient) {
    private val http = client.config { followRedirects = false; expectSuccess = false }
    private suspend fun get(config: ApiProviderConfig, path: String): JsonObject = withTimeoutOrNull(20_000) {
        val response = http.get(AiProtocol.base(config) + path) { AiProtocol.headers(config).forEach { (k, v) -> headers.append(k, v) } }
        check(response.status.value in 200..299) { "模型接口返回 HTTP ${response.status.value}" }
        Json.parseToJsonElement(response.bodyAsText()).jsonObject
    } ?: throw IOException("模型接口请求超时，请检查网络或稍后重试")
    suspend fun list(
        config: ApiProviderConfig,
        purpose: ModelListPurpose = ModelListPurpose.CHAT
    ): List<String> {
        val ids = linkedSetOf<String>(); var cursor = ""; val seen = mutableSetOf<String>()
        try {
            repeat(1000) {
                val path = when (config.kind) {
                    ProviderKind.GOOGLE_GEMINI -> "/models?pageSize=100" + if (cursor.isBlank()) "" else "&pageToken=${encodePath(cursor)}"
                    ProviderKind.ANTHROPIC -> "/models?limit=100" + if (cursor.isBlank()) "" else "&after_id=${encodePath(cursor)}"
                    else -> "/models" + if (cursor.isBlank()) "" else "?after=${encodePath(cursor)}"
                }
                val obj = get(config, path)
                val entries = obj.list(if (config.kind == ProviderKind.GOOGLE_GEMINI) "models" else "data")
                entries.forEach { entry ->
                    val m = entry.jsonObject
                    val id = m.str(if (config.kind == ProviderKind.GOOGLE_GEMINI) "name" else "id").removePrefix("models/")
                    if (id.isNotBlank() && matchesGeminiPurpose(config, m, id, purpose)) {
                        ids += id
                    }
                }
                cursor = if (config.kind == ProviderKind.GOOGLE_GEMINI) obj.str("nextPageToken") else if (obj.str("has_more") == "true") obj.str("last_id") else ""
                if (cursor.isBlank()) {
                    val formatted = formatModelIds(ids, purpose)
                    if (purpose == ModelListPurpose.TTS && formatted.isEmpty() && isDeepSeekProvider(config)) {
                        return listOf(DEEPSEEK_TTS_VIRTUAL_MODEL)
                    }
                    return formatted
                }
                check(seen.add(cursor)) { "模型列表分页游标重复，请检查服务接口" }
            }
        } catch (e: Exception) {
            if (config.kind == ProviderKind.ANTHROPIC && ids.isEmpty() && purpose != ModelListPurpose.EMBEDDING) {
                return listOf(
                    "claude-3-7-sonnet-20250219",
                    "claude-3-5-sonnet-20241022",
                    "claude-3-5-haiku-20241022",
                    "claude-3-opus-20240229"
                )
            }
            throw e
        }
        error("模型列表分页次数异常，未返回不完整列表")
    }

    companion object {
        const val DEEPSEEK_TTS_VIRTUAL_MODEL = "deepseek-tts"
        private val VERSION_TOKEN_REGEX = Regex("""\d+(?:\.\d+)*|[a-z]+|[-_/:.]""")
        private val EMBED_WORDS = setOf("embed", "embedding", "embeddings", "bge", "gte", "e5", "minilm")
        private val TTS_WORDS = setOf(
            "tts", "speech", "cosyvoice", "cosyvoice2", "indextts", "kokoro",
            "fish", "f5", "chattts", "gpt-sovits", "sovits", "vits"
        )
        private val NON_CHAT_WORDS = setOf(
            "tts", "live", "realtime", "image", "imagen", "veo", "lyria",
            "transcribe", "whisper", "robotics", "moderation", "aqa", "rerank", "reranker", "bisheng"
        )
        private val NON_CHAT_PHRASES = listOf("native-audio", "computer-use", "deep-research", "nano-banana", "audio-preview", "omni-flash")

        /** 判断是否为 DeepSeek 官方接入点 */
        internal fun isDeepSeekProvider(config: ApiProviderConfig): Boolean {
            if (config.presetId.equals("deepseek", ignoreCase = true)) return true
            val host = runCatching { URI(config.baseUrl.trim()).host }.getOrNull().orEmpty()
            return host.contains("deepseek", ignoreCase = true)
        }

        private fun matchesGeminiPurpose(
            config: ApiProviderConfig,
            modelObj: JsonObject,
            id: String,
            purpose: ModelListPurpose
        ): Boolean {
            if (config.kind != ProviderKind.GOOGLE_GEMINI) return true
            val methods = modelObj.list("supportedGenerationMethods")
                .mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                .toSet()
            return when (purpose) {
                ModelListPurpose.EMBEDDING ->
                    methods.any { it == "embedContent" || it == "batchEmbedContents" } ||
                        (methods.isEmpty() && isEmbeddingModelId(id))
                ModelListPurpose.TTS ->
                    // Gemini 3.8+ TTS 等模型可能走 interactions 或专用音频方法，只要模型 ID 命中 TTS 特征即保留
                    isTtsModelId(id)
                ModelListPurpose.CHAT ->
                    (methods.isEmpty() || "generateContent" in methods) &&
                        !isNonChatSpecializedModelId(id)
                ModelListPurpose.ALL ->
                    methods.isEmpty() ||
                        "generateContent" in methods ||
                        "embedContent" in methods ||
                        "batchEmbedContents" in methods ||
                        isTtsModelId(id)
            }
        }

        internal fun isEmbeddingModelId(id: String): Boolean {
            val lower = id.lowercase()
            val words = lower.split('-', '_', '/', ':', '.')
            if ("rerank" in words || "reranker" in words) return false
            return words.any { it in EMBED_WORDS } || lower.contains("embedding")
        }

        /** 判断模型 ID 是否属于常见语音合成（TTS）模型 */
        internal fun isTtsModelId(id: String): Boolean {
            val lower = id.lowercase()
            val words = lower.split('-', '_', '/', ':', '.')
            return words.any { it in TTS_WORDS } ||
                lower.contains("tts") ||
                lower.contains("cosyvoice") ||
                lower.contains("indextts") ||
                lower.contains("kokoro")
        }

        internal fun isNonChatSpecializedModelId(id: String): Boolean {
            if (isEmbeddingModelId(id)) return true
            val lower = id.lowercase()
            val words = lower.split('-', '_', '/', ':', '.')
            if (words.any { it in NON_CHAT_WORDS }) return true
            return NON_CHAT_PHRASES.any { lower.contains(it) }
        }

        internal fun formatModelIds(rawIds: Collection<String>, purpose: ModelListPurpose): List<String> {
            val distinct = rawIds.distinct()
            val filtered = when (purpose) {
                ModelListPurpose.EMBEDDING ->
                    distinct.filter(::isEmbeddingModelId).ifEmpty { distinct }
                ModelListPurpose.TTS ->
                    distinct.filter(::isTtsModelId)
                ModelListPurpose.CHAT ->
                    distinct.filterNot(::isNonChatSpecializedModelId).ifEmpty { distinct }
                ModelListPurpose.ALL -> distinct
            }
            return filtered.sortedWith(MODEL_NEWEST_FIRST_COMPARATOR)
        }

        private sealed interface SortToken {
            data class Num(val parts: List<Int>, val raw: String) : SortToken
            data class Word(val value: String) : SortToken
            data class Delim(val value: String) : SortToken
        }

        private fun tokenizeForSort(id: String): List<SortToken> {
            val lower = id.lowercase()
            val out = ArrayList<SortToken>()
            var idx = 0
            for (m in VERSION_TOKEN_REGEX.findAll(lower)) {
                if (m.range.first > idx) {
                    out += SortToken.Word(lower.substring(idx, m.range.first))
                }
                val v = m.value
                out += when {
                    v[0].isDigit() -> SortToken.Num(v.split('.').map { it.toIntOrNull() ?: 0 }, v)
                    v.length == 1 && v[0] in "-_/:." -> SortToken.Delim(v)
                    else -> SortToken.Word(v)
                }
                idx = m.range.last + 1
            }
            if (idx < lower.length) {
                out += SortToken.Word(lower.substring(idx))
            }
            return out
        }

        private fun compareVersionPartsDesc(a: List<Int>, b: List<Int>): Int {
            val max = maxOf(a.size, b.size)
            for (i in 0 until max) {
                val av = a.getOrElse(i) { 0 }
                val bv = b.getOrElse(i) { 0 }
                if (av != bv) return bv.compareTo(av)
            }
            return 0
        }

        private val MODEL_NEWEST_FIRST_COMPARATOR = Comparator<String> { left, right ->
            if (left == right) return@Comparator 0
            val lt = tokenizeForSort(left)
            val rt = tokenizeForSort(right)
            val common = minOf(lt.size, rt.size)
            for (i in 0 until common) {
                val a = lt[i]
                val b = rt[i]
                val cmp = when {
                    a is SortToken.Num && b is SortToken.Num -> compareVersionPartsDesc(a.parts, b.parts)
                    a is SortToken.Num -> -1
                    b is SortToken.Num -> 1
                    a is SortToken.Delim && b is SortToken.Delim -> a.value.compareTo(b.value)
                    a is SortToken.Delim -> -1
                    b is SortToken.Delim -> 1
                    a is SortToken.Word && b is SortToken.Word -> a.value.compareTo(b.value)
                    else -> 0
                }
                if (cmp != 0) return@Comparator cmp
            }
            if (lt.size != rt.size) lt.size.compareTo(rt.size) else left.compareTo(right)
        }
    }
    suspend fun detect(config: ApiProviderConfig): ModelCapability {
        if (config.selectedModel.isBlank()) return ModelCapability(config.baseUrl, "", config.kind)
        try {
            val id = encodePath(config.selectedModel.removePrefix("models/"))
            val obj = get(config, "/models/$id")
            val actual = if (obj["data"] is JsonObject) obj.node("data") else obj
            val capability = ModelMetadata.parse(config, actual)
            if (capability.output != null) return capability
            if (capability.context != null) {
                val fallback = ModelMetadata.builtin(config)
                return if (fallback == null) capability else capability.copy(output = fallback.output,
                    source = "接口上下文 + ${fallback.source}（输出）", sourceUrl = fallback.sourceUrl)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Metadata may be unsupported; only a labelled official fallback is allowed. */ }
        return ModelMetadata.builtin(config) ?: ModelCapability(config.baseUrl.trim().trimEnd('/'), config.selectedModel, config.kind,
            source = "未取得上限 · 保留当前值，可手动设置", checkedAt = System.currentTimeMillis())
    }
}
