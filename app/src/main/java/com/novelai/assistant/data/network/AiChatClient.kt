package com.novelai.assistant.data.network

import com.novelai.assistant.data.repository.ApiProviderConfig
import kotlinx.coroutines.flow.Flow

/**
 * 统一 AI 客户端接口：连通性测试、模型列表、流式对话。
 * OpenAI 兼容协议与 Anthropic 协议各自实现，上层无感。
 */
interface AiChatClient {
    /** 连通性测试，返回耗时毫秒 */
    suspend fun ping(config: ApiProviderConfig): Result<Long>

    /** 拉取可用模型列表 */
    suspend fun listModels(
        config: ApiProviderConfig,
        purpose: ModelListPurpose = ModelListPurpose.CHAT
    ): Result<List<String>>

    /** 流式对话 */
    fun streamChat(
        config: ApiProviderConfig,
        messages: List<ChatMessage>,
        maxTokens: Int,
        temperature: Float
    ): Flow<StreamEvent>
}
