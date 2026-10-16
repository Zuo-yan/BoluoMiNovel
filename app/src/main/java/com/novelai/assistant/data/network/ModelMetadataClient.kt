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
            else -> return null
        }
        val sourceUrl = if (host == "api.deepseek.com") "https://api-docs.deepseek.com/quick_start/pricing/"
            else "https://developers.openai.com/api/docs/models/${config.selectedModel.substringBefore("-202")}"
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
    suspend fun list(config: ApiProviderConfig): List<String> {
        val ids = linkedSetOf<String>(); var cursor = ""; val seen = mutableSetOf<String>()
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
                if (config.kind != ProviderKind.GOOGLE_GEMINI || m.list("supportedGenerationMethods").any { it.jsonPrimitive.content == "generateContent" }) {
                    val id = m.str(if (config.kind == ProviderKind.GOOGLE_GEMINI) "name" else "id").removePrefix("models/")
                    if (id.isNotBlank()) ids += id
                }
            }
            cursor = if (config.kind == ProviderKind.GOOGLE_GEMINI) obj.str("nextPageToken") else if (obj.str("has_more") == "true") obj.str("last_id") else ""
            if (cursor.isBlank()) return ids.toList().sorted()
            check(seen.add(cursor)) { "模型列表分页游标重复，请检查服务接口" }
        }
        error("模型列表分页次数异常，未返回不完整列表")
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
