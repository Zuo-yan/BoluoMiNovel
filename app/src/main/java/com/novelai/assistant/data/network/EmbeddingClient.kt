package com.novelai.assistant.data.network

import com.novelai.assistant.data.repository.ApiProviderConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * 可选在线 Embedding 客户端：
 * - 支持 OpenAI /v1/embeddings 兼容协议（含中转站、SiliconFlow、Ollama、DeepSeek 等）
 * - 支持 Google Gemini :batchEmbedContents 协议
 * - 所有网络/协议错误均返回 Result.failure，由上层无感退回本地段落 BM25。
 */
@Singleton
class EmbeddingClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json
) {
    suspend fun embed(
        config: ApiProviderConfig,
        model: String,
        texts: List<String>
    ): Result<List<FloatArray>> {
        if (texts.isEmpty()) return Result.success(emptyList())
        if (model.isBlank()) return Result.failure(IllegalArgumentException("Embedding 模型名不能为空"))
        if (config.kind == ProviderKind.ANTHROPIC) {
            return Result.failure(IllegalStateException("Anthropic Messages 协议不支持向量接口，请选择 OpenAI 兼容或 Gemini 服务商"))
        }

        return try {
            val endpoint = embeddingEndpoint(config, model)
            val body = embeddingBody(config, model, texts)
            val response = withTimeoutOrNull(EMBEDDING_TIMEOUT_MS) {
                httpClient.post(endpoint) {
                    AiProtocol.headers(config).forEach { (k, v) -> headers.append(k, v) }
                    setBody(body.toString())
                }
            } ?: return Result.failure(IllegalStateException("向量接口请求超时"))

            val raw = response.bodyAsText()
            if (response.status.value !in 200..299) {
                val msg = AiErrorParser.parse(response.status.value, raw, config.kind.label)
                return Result.failure(IllegalStateException(msg))
            }

            val root = json.parseToJsonElement(raw).jsonObject
            val vectors = parseEmbeddingResponse(config.kind, root, texts.size)
            if (vectors.size != texts.size) {
                Result.failure(IllegalStateException("返回向量数量与输入不一致（${vectors.size}/${texts.size}）"))
            } else {
                Result.success(vectors)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun test(config: ApiProviderConfig, model: String): Result<Int> =
        embed(config, model, listOf("向量连通性测试")).map { list ->
            list.firstOrNull()?.size ?: 0
        }

    companion object {
        private const val EMBEDDING_TIMEOUT_MS = 12_000L

        internal fun embeddingEndpoint(config: ApiProviderConfig, model: String): String {
            val base = AiProtocol.base(config)
            return when (config.kind) {
                ProviderKind.GOOGLE_GEMINI ->
                    "$base/models/${encodePath(model.removePrefix("models/"))}:batchEmbedContents"
                ProviderKind.OPENAI_COMPATIBLE,
                ProviderKind.OPENAI_RESPONSES ->
                    "$base/embeddings"
                ProviderKind.ANTHROPIC ->
                    throw IllegalStateException("Anthropic 协议不支持向量接口")
            }
        }

        internal fun embeddingBody(
            config: ApiProviderConfig,
            model: String,
            texts: List<String>
        ): JsonObject = when (config.kind) {
            ProviderKind.GOOGLE_GEMINI -> {
                val cleanModel = "models/${model.removePrefix("models/")}"
                buildJsonObject {
                    putJsonArray("requests") {
                        texts.forEach { text ->
                            add(
                                buildJsonObject {
                                    put("model", cleanModel)
                                    put(
                                        "content",
                                        buildJsonObject {
                                            putJsonArray("parts") {
                                                add(buildJsonObject { put("text", text) })
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
            ProviderKind.OPENAI_COMPATIBLE,
            ProviderKind.OPENAI_RESPONSES -> buildJsonObject {
                put("model", model)
                putJsonArray("input") {
                    texts.forEach { add(JsonPrimitive(it)) }
                }
            }
            ProviderKind.ANTHROPIC -> buildJsonObject {}
        }

        internal fun parseEmbeddingResponse(
            kind: ProviderKind,
            root: JsonObject,
            expectedCount: Int
        ): List<FloatArray> = when (kind) {
            ProviderKind.GOOGLE_GEMINI -> {
                val arr = root["embeddings"] as? JsonArray ?: emptyList()
                arr.map { item ->
                    val values = item.jsonObject["values"]?.jsonArray ?: emptyList()
                    FloatArray(values.size) { idx ->
                        values[idx].jsonPrimitive.doubleOrNull?.toFloat() ?: 0f
                    }
                }
            }
            ProviderKind.OPENAI_COMPATIBLE,
            ProviderKind.OPENAI_RESPONSES -> {
                val data = root["data"] as? JsonArray ?: emptyList()
                data.mapIndexed { fallbackIdx, elem ->
                    val obj = elem.jsonObject
                    val index = obj["index"]?.jsonPrimitive?.intOrNull ?: fallbackIdx
                    val values = obj["embedding"]?.jsonArray ?: emptyList()
                    val vec = FloatArray(values.size) { idx ->
                        values[idx].jsonPrimitive.doubleOrNull?.toFloat() ?: 0f
                    }
                    index to vec
                }
                    .sortedBy { it.first }
                    .take(expectedCount)
                    .map { it.second }
            }
            ProviderKind.ANTHROPIC -> emptyList()
        }

        fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
            if (a.isEmpty() || a.size != b.size) return 0.0
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in a.indices) {
                val x = a[i].toDouble()
                val y = b[i].toDouble()
                dot += x * y
                normA += x * x
                normB += y * y
            }
            val denom = sqrt(normA) * sqrt(normB)
            return if (denom <= 1e-12) 0.0 else (dot / denom).coerceIn(-1.0, 1.0)
        }
    }
}
