package com.novelai.assistant.data.network

import javax.inject.Inject
import javax.inject.Singleton

/** 按配置的独立协议选择客户端；中转站也遵循用户明确选择的协议。 */
@Singleton
class AiClientFactory @Inject constructor(client: io.ktor.client.HttpClient, metadata: ModelMetadataClient) {
    private val clients = ProviderKind.entries.associateWith { ProtocolAiClient(client, it, metadata) }
    fun clientFor(kind: ProviderKind): AiChatClient = clients.getValue(kind)
}
