package com.novelai.assistant

import com.novelai.assistant.data.network.ModelListPurpose
import com.novelai.assistant.data.network.ModelMetadataClient
import com.novelai.assistant.data.network.ProviderKind
import com.novelai.assistant.data.network.ProviderPresets
import com.novelai.assistant.data.repository.ApiProviderConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelMetadataFilterTest {

    private fun geminiConfig(baseUrl: String) = ApiProviderConfig(
        id = "gemini-test",
        providerName = "Google Gemini",
        presetId = "google",
        platformId = "developer",
        baseUrl = baseUrl,
        apiKey = "test-key",
        selectedModel = "gemini-3.8-flash",
        kind = ProviderKind.GOOGLE_GEMINI
    )

    private fun openaiConfig(baseUrl: String) = ApiProviderConfig(
        id = "openai-test",
        providerName = "OpenAI Relay",
        presetId = "custom",
        platformId = "custom",
        baseUrl = baseUrl,
        apiKey = "test-key",
        selectedModel = "gpt-4.1",
        kind = ProviderKind.OPENAI_COMPATIBLE
    )

    @Test
    fun geminiEmbeddingPurposeIncludesGeminiEmbedding2AndSortsNewestFirst() = runBlocking {
        val server = MockWebServer()
        val page1 = """
            {
              "models": [
                {"name": "models/gemini-1.5-pro", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-3.8-flash", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-embedding-001", "supportedGenerationMethods": ["embedContent", "countTextTokens"]},
                {"name": "models/gemini-3.8-flash-tts", "supportedGenerationMethods": ["generateContent"]}
              ],
              "nextPageToken": "p2"
            }
        """.trimIndent()
        val page2 = """
            {
              "models": [
                {"name": "models/gemini-embedding-2-preview", "supportedGenerationMethods": ["embedContent", "batchEmbedContents"]},
                {"name": "models/gemini-embedding-2", "supportedGenerationMethods": ["embedContent", "batchEmbedContents"]},
                {"name": "models/aqa", "supportedGenerationMethods": ["generateAnswer"]}
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(page1).build())
        server.enqueue(MockResponse.Builder().body(page2).build())
        server.start()
        val http = HttpClient(OkHttp)
        try {
            val metadata = ModelMetadataClient(http)
            val result = metadata.list(geminiConfig(server.url("/v1beta").toString()), ModelListPurpose.EMBEDDING)
            assertEquals(
                listOf("gemini-embedding-2", "gemini-embedding-2-preview", "gemini-embedding-001"),
                result
            )
        } finally {
            http.close()
            server.close()
        }
    }

    @Test
    fun geminiChatPurposeFiltersSpecializedClutterAndSortsNewestFirst() = runBlocking {
        val server = MockWebServer()
        val body = """
            {
              "models": [
                {"name": "models/gemini-1.5-pro", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-2.5-pro", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-2.5-flash", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-3.8-flash", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-3.8-flash-tts", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-3.8-live", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-3-pro-image", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-robotics-er-2-preview", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-2.5-computer-use-preview-10-2025", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/deep-research-pro-preview-12-2025", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/veo-3.0-generate-001", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-4-argon", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-embedding-2", "supportedGenerationMethods": ["embedContent"]}
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(body).build())
        server.start()
        val http = HttpClient(OkHttp)
        try {
            val metadata = ModelMetadataClient(http)
            val result = metadata.list(geminiConfig(server.url("/v1beta").toString()), ModelListPurpose.CHAT)
            assertTrue(result.contains("gemini-4-argon"))
            assertTrue(result.contains("gemini-3.8-flash"))
            assertTrue(result.contains("gemini-2.5-pro"))
            assertTrue(result.contains("gemini-1.5-pro"))
            assertFalse(result.any { it.contains("tts") || it.contains("live") || it.contains("image") || it.contains("robotics") || it.contains("computer-use") || it.contains("deep-research") || it.startsWith("veo-") || it.contains("embedding") })
            assertEquals("gemini-4-argon", result.first())
            assertTrue(result.indexOf("gemini-3.8-flash") < result.indexOf("gemini-2.5-pro"))
            assertTrue(result.indexOf("gemini-2.5-pro") < result.indexOf("gemini-1.5-pro"))
        } finally {
            http.close()
            server.close()
        }
    }

    @Test
    fun openAiRelayEmbeddingFilteringExcludesRerankerAndChatModels() = runBlocking {
        val server = MockWebServer()
        val body = """
            {
              "data": [
                {"id": "gpt-4o"},
                {"id": "BAAI/bge-reranker-v2-m3"},
                {"id": "text-embedding-ada-002"},
                {"id": "text-embedding-3-small"},
                {"id": "text-embedding-3-large"},
                {"id": "BAAI/bge-m3"}
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(body).build())
        server.start()
        val http = HttpClient(OkHttp)
        try {
            val metadata = ModelMetadataClient(http)
            val result = metadata.list(openaiConfig(server.url("/v1").toString()), ModelListPurpose.EMBEDDING)
            assertFalse("gpt-4o" in result)
            assertFalse("BAAI/bge-reranker-v2-m3" in result)
            assertTrue("text-embedding-3-large" in result)
            assertTrue("text-embedding-3-small" in result)
            assertTrue("BAAI/bge-m3" in result)
            assertTrue(result.indexOf("text-embedding-3-large") < result.indexOf("text-embedding-ada-002"))
        } finally {
            http.close()
            server.close()
        }
    }

    @Test
    fun googleRecommendedModelsPreferLatestVersions() {
        val googleModels = ProviderPresets.recommendedModels("google", "https://generativelanguage.googleapis.com/v1beta")
        assertTrue(googleModels.first().startsWith("gemini-4") || googleModels.first().startsWith("gemini-3"))
        assertFalse(googleModels.any { it.contains("1.5") })
    }

    @Test
    fun ttsPurposeFiltersTtsModelsAndExcludesChatModels() = runBlocking {
        val server = MockWebServer()
        val geminiJson = """
            {
              "models": [
                {"name": "models/gemini-3.8-flash", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-3.8-flash-tts", "supportedGenerationMethods": ["createInteraction"]},
                {"name": "models/gemini-2.5-flash-preview-tts", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-embedding-001", "supportedGenerationMethods": ["embedContent"]}
              ]
            }
        """.trimIndent()
        val openaiJson = """
            {
              "data": [
                {"id": "gpt-4o"},
                {"id": "tts-1"},
                {"id": "FunAudioLLM/CosyVoice2-0.5B"},
                {"id": "text-embedding-3-large"}
              ]
            }
        """.trimIndent()
        val deepSeekJson = """
            {
              "data": [
                {"id": "deepseek-chat"},
                {"id": "deepseek-reasoner"}
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse(body = geminiJson, headers = okhttp3.Headers.headersOf("Content-Type", "application/json")))
        server.enqueue(MockResponse(body = openaiJson, headers = okhttp3.Headers.headersOf("Content-Type", "application/json")))
        server.enqueue(MockResponse(body = deepSeekJson, headers = okhttp3.Headers.headersOf("Content-Type", "application/json")))
        server.start()
        try {
            val client = HttpClient(OkHttp)
            val base = server.url("/v1beta").toString().trimEnd('/')
            val geminiModels = ModelMetadataClient(client).list(geminiConfig(base), ModelListPurpose.TTS)
            assertTrue(geminiModels.contains("gemini-3.8-flash-tts"))
            assertTrue(geminiModels.contains("gemini-2.5-flash-preview-tts"))
            assertFalse(geminiModels.contains("gemini-3.8-flash"))
            assertFalse(geminiModels.contains("gemini-embedding-001"))

            val openaiBase = server.url("/v1").toString().trimEnd('/')
            val openaiModels = ModelMetadataClient(client).list(openaiConfig(openaiBase), ModelListPurpose.TTS)
            assertTrue(openaiModels.contains("tts-1"))
            assertTrue(openaiModels.contains("FunAudioLLM/CosyVoice2-0.5B"))
            assertFalse(openaiModels.contains("gpt-4o"))
            assertFalse(openaiModels.contains("text-embedding-3-large"))

            val dsConfig = openaiConfig(openaiBase).copy(presetId = "deepseek", providerName = "DeepSeek")
            val dsModels = ModelMetadataClient(client).list(dsConfig, ModelListPurpose.TTS)
            assertEquals(listOf(ModelMetadataClient.DEEPSEEK_TTS_VIRTUAL_MODEL), dsModels)
            client.close()
        } finally {
            server.close()
        }
    }
}
