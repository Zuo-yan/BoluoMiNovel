package com.novelai.assistant.data.network

import com.novelai.assistant.data.repository.ApiProviderConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.jsonObject

class ProtocolAiClient(client: HttpClient, private val kind: ProviderKind, private val metadata: ModelMetadataClient) : AiChatClient {
    private val http = client.config { followRedirects = false; expectSuccess = false }
    override suspend fun ping(config: ApiProviderConfig): Result<Long> {
        val start = System.currentTimeMillis()
        return try {
            metadata.list(config.copy(kind = kind))
            Result.success(System.currentTimeMillis() - start)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }
    override suspend fun listModels(config: ApiProviderConfig): Result<List<String>> = try { Result.success(metadata.list(config.copy(kind = kind))) }
        catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    override fun streamChat(config: ApiProviderConfig, messages: List<ChatMessage>, maxTokens: Int, temperature: Float): Flow<StreamEvent> = flow {
        val active = config.copy(kind = kind)
        val decoder = AiStreamDecoder(kind)
        val frames = SseFrameReader()
        suspend fun deliver(frame: Pair<String, String>) {
            for (event in decoder.decode(frame.first, frame.second)) {
                if (event is StreamEvent.Done && !decoder.hasText) emit(StreamEvent.Error("模型已结束，但没有返回可显示的正文，请检查输出预算或模型能力"))
                else emit(event)
            }
        }
        try {
            val outputBudget = if (active.autoMaxTokens) {
                active.capability?.takeIf { it.matches(active) }?.let { it.output ?: maxTokens }
                    ?: (metadata.detect(active).output ?: maxTokens)
            } else maxTokens
            http.preparePost(AiProtocol.endpoint(active)) {
                AiProtocol.headers(active).forEach { (key, value) -> headers.append(key, value) }
                setBody(AiProtocol.body(active, messages, outputBudget, temperature).toString())
            }.execute { response ->
                if (response.status.value !in 200..299) {
                    val code = response.status.value
                    if (code == 400) {
                        val rawErr = runCatching { response.bodyAsText() }.getOrDefault("")
                        val parsedMessage = runCatching {
                            val obj = kotlinx.serialization.json.Json.parseToJsonElement(rawErr).jsonObject
                            obj.node("error").str("message").ifBlank { obj.str("message") }.ifBlank { obj.str("msg") }
                        }.getOrNull()
                        val detail = parsedMessage?.takeIf { it.isNotBlank() } ?: rawErr.take(150)
                        val msg = if (detail.isNotBlank()) "${kind.label} (HTTP 400): $detail"
                            else "${kind.label} 返回 HTTP 400，请检查地址、模型、密钥权限和参数"
                        emit(StreamEvent.Error(msg))
                    } else {
                        emit(StreamEvent.Error("${kind.label} 返回 HTTP $code，请检查地址、模型、密钥权限和参数"))
                    }
                    return@execute
                }
                val channel = response.bodyAsChannel()
                while (!decoder.finished && !channel.isClosedForRead) {
                    val line = channel.readUTF8Line(4 * 1024 * 1024) ?: break
                    frames.line(line)?.let { deliver(it) }
                }
                if (!decoder.finished) frames.flush()?.let { deliver(it) }
                if (!decoder.finished) emit(StreamEvent.Error("生成连接提前结束，已收到的文字已保留，请重试"))
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { emit(StreamEvent.Error("生成请求失败，请检查网络、服务地址及协议，或稍后重试")) }
    }
}
