package com.novelai.assistant.data.network

import com.novelai.assistant.data.repository.ApiProviderConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI 兼容协议客户端：/chat/completions SSE 流式 + /models。
 * 覆盖 OpenAI、DeepSeek、Moonshot、SiliconFlow、Ollama 以及各类中转站。
 */
@Singleton
class OpenAiCompatClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json
) : AiChatClient {

    private fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/')

    private fun io.ktor.client.request.HttpRequestBuilder.withConfig(config: ApiProviderConfig) {
        contentType(ContentType.Application.Json)
        if (config.apiKey.isNotBlank()) header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
        config.customHeaders.forEach { (k, v) -> header(k, v) }
    }

    override suspend fun ping(config: ApiProviderConfig): Result<Long> {
        val start = System.currentTimeMillis()
        return try {
            val resp = httpClient.get("${normalizeBaseUrl(config.baseUrl)}/models") {
                withConfig(config)
            }
            if (resp.status.value in 200..299) {
                Result.success(System.currentTimeMillis() - start)
            } else {
                Result.failure(IllegalStateException("HTTP ${resp.status.value}: ${resp.bodyAsText().take(300)}"))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun listModels(config: ApiProviderConfig): Result<List<String>> {
        return try {
            val resp = httpClient.get("${normalizeBaseUrl(config.baseUrl)}/models") {
                withConfig(config)
            }
            if (resp.status.value !in 200..299) {
                return Result.failure(IllegalStateException("HTTP ${resp.status.value}"))
            }
            val body = resp.bodyAsText()
            val parsed = json.decodeFromString<OpenAiModelsResponse>(body)
            Result.success(parsed.data.map { it.id }.distinct().sorted())
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override fun streamChat(
        config: ApiProviderConfig,
        messages: List<ChatMessage>,
        maxTokens: Int,
        temperature: Float
    ): Flow<StreamEvent> = flow {
        val url = "${normalizeBaseUrl(config.baseUrl)}/chat/completions"
        val isOfficialOpenAi = runCatching { java.net.URI(config.baseUrl).host == "api.openai.com" }.getOrDefault(false)
        val request = OpenAiChatRequest(
            model = config.selectedModel,
            messages = messages.map { OpenAiMessage(it.role, it.content) },
            stream = true,
            temperature = temperature,
            max_tokens = maxTokens,
            stream_options = if (isOfficialOpenAi) StreamOptions(include_usage = true) else null
        )
        try {
            httpClient.preparePost(url) {
                withConfig(config)
                setBody(json.encodeToString(OpenAiChatRequest.serializer(), request))
            }.execute { response ->
                if (response.status.value !in 200..299) {
                    val err = response.bodyAsText().take(500)
                    emit(StreamEvent.Error("HTTP ${response.status.value}: $err"))
                    return@execute
                }
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line(1024 * 1024) ?: break
                    if (isSseCommentOrEmpty(line)) continue
                    for (payload in parseSseDataLines(line)) {
                        if (payload == "[DONE]") {
                            emit(StreamEvent.Done(null))
                            return@execute
                        }
                        val chunk = runCatching {
                            json.decodeFromString(OpenAiChatChunk.serializer(), payload)
                        }.getOrNull() ?: continue
                        chunk.usage?.let {
                            if (it.prompt_tokens > 0 || it.completion_tokens > 0) {
                                emit(StreamEvent.Usage(it.prompt_tokens, it.completion_tokens))
                            }
                        }
                        val choice = chunk.choices.firstOrNull() ?: continue
                        choice.delta?.content?.takeIf { it.isNotEmpty() }?.let { emit(StreamEvent.Delta(it)) }
                        choice.finish_reason?.let { emit(StreamEvent.Done(it)) }
                    }
                }
                emit(StreamEvent.Done(null))
            }
        } catch (e: ClientRequestException) {
            emit(StreamEvent.Error("请求被拒绝：${e.message}"))
        } catch (t: Throwable) {
            emit(StreamEvent.Error("网络错误：${t.message ?: t.javaClass.simpleName}"))
        }
    }
}
