package com.novelai.assistant.data.network

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
