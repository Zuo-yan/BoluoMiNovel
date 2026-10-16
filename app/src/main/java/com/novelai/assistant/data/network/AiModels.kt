package com.novelai.assistant.data.network

import kotlinx.serialization.Serializable

/** 四种协议适配后的流式事件；Usage 表示本次请求的累计用量。 */
sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class Usage(val promptTokens: Long, val completionTokens: Long) : StreamEvent
    data class Done(val finishReason: String?) : StreamEvent
    data class Error(val message: String) : StreamEvent
}

data class ChatMessage(
    val role: String, // system | user | assistant
    val content: String
)

enum class ProviderKind(val label: String) {
    OPENAI_COMPATIBLE("OpenAI Chat Completions"),
    OPENAI_RESPONSES("OpenAI Responses"),
    ANTHROPIC("Anthropic Messages"),
    GOOGLE_GEMINI("Google Gemini")
}

/** SSE 行解析共通工具 */
internal fun parseSseDataLines(line: String): List<String> {
    val trimmed = line.trimEnd('\r')
    if (!trimmed.startsWith("data:")) return emptyList()
    val payload = trimmed.removePrefix("data:").trim()
    return listOf(payload)
}

internal fun isSseCommentOrEmpty(line: String): Boolean {
    val t = line.trim()
    return t.isEmpty() || t.startsWith(":") || t.startsWith("event:") || t.startsWith("id:")
}

// ---------- OpenAI 协议 DTO ----------

@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    val max_tokens: Int? = null,
    val stream_options: StreamOptions? = null
)

@Serializable
data class StreamOptions(val include_usage: Boolean = true)

@Serializable
data class OpenAiMessage(val role: String, val content: String)

@Serializable
data class OpenAiChatChunk(
    val choices: List<OpenAiChoice> = emptyList(),
    val usage: OpenAiUsage? = null
)

@Serializable
data class OpenAiChoice(val delta: OpenAiDelta? = null, val finish_reason: String? = null)

@Serializable
data class OpenAiDelta(val content: String? = null)

@Serializable
data class OpenAiUsage(val prompt_tokens: Long = 0, val completion_tokens: Long = 0)

@Serializable
data class OpenAiModelsResponse(val data: List<OpenAiModelRef> = emptyList())

@Serializable
data class OpenAiModelRef(val id: String)
