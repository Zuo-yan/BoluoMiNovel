package com.novelai.assistant.data.network

import com.novelai.assistant.data.repository.ApiProviderConfig
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLEncoder

internal fun JsonObject.str(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.node(key: String) = get(key) as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.list(key: String) = (get(key) as? JsonArray).orEmpty()
internal fun JsonObject.count(key: String) = str(key).toLongOrNull() ?: 0L
internal fun encodePath(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

object AiProtocol {
    fun base(config: ApiProviderConfig): String {
        var value = config.baseUrl.trim().trimEnd('/')
        val uri = URI(value)
        require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "请填写有效的 API 基础地址，不包含密钥、查询参数或完整生成路径" }
        listOf("/chat/completions", "/responses", "/messages").firstOrNull { value.endsWith(it) }?.let { value = value.removeSuffix(it) }
        if (config.kind == ProviderKind.ANTHROPIC && !value.endsWith("/v1")) value += "/v1"
        if (uri.host == "api.openai.com" && (uri.path.isNullOrEmpty() || uri.path == "/")) value += "/v1"
        if (uri.host == "generativelanguage.googleapis.com" && (uri.path.isNullOrEmpty() || uri.path == "/")) value += "/v1beta"
        return value
    }

    fun endpoint(config: ApiProviderConfig): String = base(config) + when (config.kind) {
        ProviderKind.OPENAI_COMPATIBLE -> "/chat/completions"
        ProviderKind.OPENAI_RESPONSES -> "/responses"
        ProviderKind.ANTHROPIC -> "/messages"
        ProviderKind.GOOGLE_GEMINI -> "/models/${encodePath(config.selectedModel.removePrefix("models/"))}:streamGenerateContent?alt=sse"
    }

    fun headers(config: ApiProviderConfig): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        when (config.kind) {
            ProviderKind.ANTHROPIC -> { put("x-api-key", config.apiKey); put("anthropic-version", "2023-06-01") }
            ProviderKind.GOOGLE_GEMINI -> put("x-goog-api-key", config.apiKey)
            else -> if (config.apiKey.isNotBlank()) put("Authorization", "Bearer ${config.apiKey}")
        }
        config.customHeaders.forEach { (key, value) ->
            require(!key.contains('\n') && !value.contains('\n') && !key.contains('\r') && !value.contains('\r')) { "请求头格式无效" }
            put(key, value)
        }
    }

    fun body(config: ApiProviderConfig, messages: List<ChatMessage>, maxTokens: Int, temperature: Float): JsonObject = buildJsonObject {
        require(maxTokens > 0 && config.selectedModel.isNotBlank()) { "请先选择模型并设置有效的输出 Token" }
        val system = messages.filter { it.role in listOf("system", "developer") }.joinToString("\n\n") { it.content }
        val turns = messages.filter { it.role in listOf("user", "assistant") }
        when (config.kind) {
            ProviderKind.OPENAI_COMPATIBLE -> {
                put("model", config.selectedModel); put("stream", true)
                putJsonArray("messages") { messages.forEach { add(buildJsonObject { put("role", it.role); put("content", it.content) }) } }
                val modern = config.chatTokenField == "max_completion_tokens" ||
                    (config.chatTokenField == "auto" && URI(config.baseUrl).host == "api.openai.com")
                put(if (modern) "max_completion_tokens" else "max_tokens", maxTokens)
                putJsonObject("stream_options") { put("include_usage", true) }
                if (config.temperatureEnabled) put("temperature", temperature)
            }
            ProviderKind.OPENAI_RESPONSES -> {
                put("model", config.selectedModel); put("stream", true); put("store", false); put("max_output_tokens", maxTokens)
                putJsonArray("input") { messages.forEach { add(buildJsonObject { put("role", it.role); put("content", it.content) }) } }
                if (config.temperatureEnabled) put("temperature", temperature)
            }
            ProviderKind.ANTHROPIC -> {
                put("model", config.selectedModel); put("stream", true); put("max_tokens", maxTokens)
                if (system.isNotBlank()) put("system", system)
                putJsonArray("messages") { turns.forEach { add(buildJsonObject { put("role", it.role); put("content", it.content) }) } }
                if (config.temperatureEnabled) put("temperature", temperature)
            }
            ProviderKind.GOOGLE_GEMINI -> {
                if (system.isNotBlank()) putJsonObject("systemInstruction") { putJsonArray("parts") { add(buildJsonObject { put("text", system) }) } }
                putJsonArray("contents") { turns.forEach { turn -> add(buildJsonObject {
                    put("role", if (turn.role == "assistant") "model" else "user")
                    putJsonArray("parts") { add(buildJsonObject { put("text", turn.content) }) }
                }) } }
                putJsonObject("generationConfig") { put("maxOutputTokens", maxTokens); if (config.temperatureEnabled) put("temperature", temperature) }
            }
        }
    }
}

/** Stateful per-request decoding keeps usage totals and finish events in the correct order. */
class AiStreamDecoder(private val kind: ProviderKind) {
    var finished = false; private set
    var hasText = false; private set
    private var finishReason: String? = null
    private var input = 0L
    private var output = 0L
    fun decode(payload: String, event: String = ""): List<StreamEvent> {
        if (finished) return emptyList()
        if (payload == "[DONE]") { finished = true; return listOf(StreamEvent.Done(finishReason)) }
        val obj = Json.parseToJsonElement(payload).jsonObject
        val events = mutableListOf<StreamEvent>()
        fun delta(text: String) { if (text.isNotEmpty()) { hasText = true; events += StreamEvent.Delta(text) } }
        fun usage(i: Long, o: Long) { input = maxOf(input, i); output = maxOf(output, o); if (input > 0 || output > 0) events += StreamEvent.Usage(input, output) }
        fun done(reason: String?) { finished = true; events += StreamEvent.Done(reason) }
        if (obj["error"] is JsonObject || obj.str("type") == "error") {
            finished = true; return listOf(StreamEvent.Error("服务返回错误，请检查模型、密钥权限或请求参数"))
        }
        when (kind) {
            ProviderKind.OPENAI_COMPATIBLE -> {
                val u = obj.node("usage"); usage(u.count("prompt_tokens"), u.count("completion_tokens"))
                val choice = obj.list("choices").firstOrNull() as? JsonObject
                if (choice != null) {
                    delta(choice.node("delta").str("content"))
                    choice.str("finish_reason").takeIf { it.isNotBlank() }?.let { finishReason = it }
                }
            }
            ProviderKind.OPENAI_RESPONSES -> when (obj.str("type").ifBlank { event }) {
                "response.output_text.delta" -> delta(obj.str("delta"))
                "response.completed", "response.incomplete" -> {
                    val r = obj.node("response"); val u = r.node("usage"); usage(u.count("input_tokens"), u.count("output_tokens"))
                    done(r.node("incomplete_details").str("reason").ifBlank { "stop" })
                }
                "response.failed", "error" -> { finished = true; events += StreamEvent.Error("生成失败，请检查模型或稍后重试") }
            }
            ProviderKind.ANTHROPIC -> when (obj.str("type").ifBlank { event }) {
                "message_start" -> { val u = obj.node("message").node("usage"); usage(u.count("input_tokens") + u.count("cache_creation_input_tokens") + u.count("cache_read_input_tokens"), u.count("output_tokens")) }
                "content_block_delta" -> { val d = obj.node("delta"); if (d.str("type") == "text_delta") delta(d.str("text")) }
                "content_block_start" -> { val b = obj.node("content_block"); if (b.str("type") == "text") delta(b.str("text")) }
                "message_delta" -> { val u = obj.node("usage"); usage(u.count("input_tokens"), u.count("output_tokens")); finishReason = obj.node("delta").str("stop_reason") }
                "message_stop" -> done(finishReason)
            }
            ProviderKind.GOOGLE_GEMINI -> {
                obj.list("candidates").firstOrNull()?.jsonObject?.let { candidate ->
                    candidate.node("content").list("parts").forEach { part -> val p = part.jsonObject; if (p.str("thought") != "true") delta(p.str("text")) }
                    candidate.str("finishReason").takeIf { it.isNotBlank() }?.let { finishReason = it }
                }
                val u = obj.node("usageMetadata"); usage(u.count("promptTokenCount"), u.count("candidatesTokenCount") + u.count("thoughtsTokenCount"))
                val blocked = obj.node("promptFeedback").str("blockReason")
                if (blocked.isNotBlank()) { finished = true; events += StreamEvent.Error("请求被模型拒绝：$blocked") }
                else if (finishReason != null) done(finishReason)
            }
        }
        return events
    }
}

class SseFrameReader {
    private val data = mutableListOf<String>()
    private var event = ""
    fun line(line: String): Pair<String, String>? {
        val clean = line.trimEnd('\r')
        if (clean.isEmpty()) return flush()
        if (clean.startsWith("data:")) data += clean.substring(5).removePrefix(" ")
        if (clean.startsWith("event:")) event = clean.substring(6).trim()
        return null
    }
    fun flush(): Pair<String, String>? {
        val result = if (data.isEmpty()) null else data.joinToString("\n") to event
        data.clear(); event = ""; return result
    }
}
