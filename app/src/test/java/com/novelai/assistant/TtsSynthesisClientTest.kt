package com.novelai.assistant

import com.novelai.assistant.data.network.ProviderKind
import com.novelai.assistant.data.network.TtsSynthesisClient
import com.novelai.assistant.data.repository.ApiProviderConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class TtsSynthesisClientTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    private fun geminiConfig(baseUrl: String) = ApiProviderConfig(
        id = "gem-1",
        providerName = "Google Gemini",
        presetId = "google",
        platformId = "developer",
        baseUrl = baseUrl,
        apiKey = "gem-secret",
        selectedModel = "gemini-3.8-flash-tts",
        kind = ProviderKind.GOOGLE_GEMINI
    )

    private fun openAiConfig(baseUrl: String) = ApiProviderConfig(
        id = "oa-1",
        providerName = "OpenAI Relay",
        presetId = "openai",
        platformId = "global",
        baseUrl = baseUrl,
        apiKey = "oa-secret",
        selectedModel = "tts-1",
        kind = ProviderKind.OPENAI_COMPATIBLE
    )

    private fun anthropicConfig() = ApiProviderConfig(
        id = "ant-1",
        providerName = "Anthropic",
        presetId = "anthropic",
        platformId = "global",
        baseUrl = "https://api.anthropic.com/v1",
        apiKey = "ant-secret",
        selectedModel = "claude-3-7-sonnet-20250219",
        kind = ProviderKind.ANTHROPIC
    )

    @Test
    fun gemini38UsesInteractionsEndpointAndDecodesWav() = runBlocking {
        val server = MockWebServer()
        val fakeWav = "RIFF\u0024\u0000\u0000\u0000WAVEfmt ".toByteArray(Charsets.US_ASCII)
        val b64 = Base64.getEncoder().encodeToString(fakeWav)
        val responseJson = """
            {
              "steps": [
                {
                  "type": "model_output",
                  "content": [
                    { "type": "audio", "mime_type": "audio/wav", "data": "$b64" }
                  ]
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(responseJson).build())
        server.start()
        val http = HttpClient(OkHttp)
        try {
            val client = TtsSynthesisClient(http, json)
            val result = client.synthesize(
                config = geminiConfig(server.url("/v1beta").toString()),
                model = "gemini-3.8-flash-tts",
                text = "你好，菠萝蜜小说。",
                voice = "Kore"
            )
            assertTrue(result.isSuccess)
            val audio = result.getOrThrow()
            assertEquals("wav", audio.extension)
            assertArrayEquals(fakeWav, audio.bytes)

            val recorded = server.takeRequest()
            assertEquals("/v1beta/interactions", recorded.url.encodedPath)
            assertEquals("gem-secret", recorded.headers["x-goog-api-key"])
            val reqBody = recorded.body?.utf8().orEmpty()
            assertTrue(reqBody.contains("gemini-3.8-flash-tts"))
            assertTrue(reqBody.contains("Kore"))
        } finally {
            http.close()
            server.close()
        }
    }

    @Test
    fun geminiFallsBackToGenerateContentOn404AndWrapsPcmAsWav() = runBlocking {
        val server = MockWebServer()
        val pcmSamples = byteArrayOf(0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x04, 0x00)
        val b64 = Base64.getEncoder().encodeToString(pcmSamples)
        val genContentResp = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "inlineData": {
                          "mimeType": "audio/L16;rate=24000",
                          "data": "$b64"
                        }
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(404).body("""{"error":{"message":"not found"}}""").build())
        server.enqueue(MockResponse.Builder().code(200).body(genContentResp).build())
        server.start()
        val http = HttpClient(OkHttp)
        try {
            val client = TtsSynthesisClient(http, json)
            val result = client.synthesize(
                config = geminiConfig(server.url("/v1beta").toString()),
                model = "gemini-3.8-flash-tts",
                text = "测试兼容回退。",
                voice = "Puck"
            )
            assertTrue(result.isSuccess)
            val audio = result.getOrThrow()
            assertEquals("wav", audio.extension)
            assertEquals(44 + pcmSamples.size, audio.bytes.size)
            assertEquals("RIFF", String(audio.bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(audio.bytes, 8, 4, Charsets.US_ASCII))

            val firstReq = server.takeRequest()
            val secondReq = server.takeRequest()
            assertEquals("/v1beta/interactions", firstReq.url.encodedPath)
            assertEquals("/v1beta/models/gemini-3.8-flash-tts:generateContent", secondReq.url.encodedPath)
        } finally {
            http.close()
            server.close()
        }
    }

    @Test
    fun geminiListVoicesMergesExtendedLibrary() = runBlocking {
        val server = MockWebServer()
        val resp = """
            {
              "voices": [
                {
                  "id": "voice_custom_zh_1",
                  "display_name": "清冷剑修",
                  "language_code": "zh-CN",
                  "gender": "male",
                  "description": "沉稳低音"
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(resp).build())
        server.start()
        val http = HttpClient(OkHttp)
        try {
            val client = TtsSynthesisClient(http, json)
            val voices = client.listVoices(geminiConfig(server.url("/v1beta").toString()))
            assertTrue(voices.any { it.id == "Kore" })
            assertTrue(voices.any { it.id == "voice_custom_zh_1" && it.displayName == "清冷剑修" })
        } finally {
            http.close()
            server.close()
        }
    }

    @Test
    fun openAiSpeechPostsBinaryAudioAndParsesErrors() = runBlocking {
        val server = MockWebServer()
        val mp3Bytes = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x11)
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "audio/mpeg")
                .body(Buffer().write(mp3Bytes))
                .build()
        )
        server.enqueue(
            MockResponse.Builder()
                .code(401)
                .body("""{"error":{"message":"Invalid API key"}}""")
                .build()
        )
        server.start()
        val http = HttpClient(OkHttp)
        try {
            val client = TtsSynthesisClient(http, json)
            val cfg = openAiConfig(server.url("/v1").toString())

            val ok = client.synthesize(cfg, "FunAudioLLM/CosyVoice2-0.5B", "一段正文", "nova")
            assertTrue(ok.isSuccess)
            assertEquals("mp3", ok.getOrThrow().extension)
            assertArrayEquals(mp3Bytes, ok.getOrThrow().bytes)

            val recorded = server.takeRequest()
            assertEquals("/v1/audio/speech", recorded.url.encodedPath)
            assertEquals("Bearer oa-secret", recorded.headers["Authorization"])

            val fail = client.synthesize(cfg, "tts-1", "失败测试", "alloy")
            assertTrue(fail.isFailure)
            assertTrue(fail.exceptionOrNull()?.message?.contains("Invalid API key") == true)
        } finally {
            http.close()
            server.close()
        }
    }

    @Test
    fun anthropicProviderIsRejectedClearly() = runBlocking {
        val http = HttpClient(OkHttp)
        try {
            val client = TtsSynthesisClient(http, json)
            val res = client.synthesize(anthropicConfig(), "claude-3-7-sonnet", "测试", "alloy")
            assertTrue(res.isFailure)
            assertTrue(res.exceptionOrNull()?.message?.contains("Anthropic") == true)
        } finally {
            http.close()
        }
    }

    @Test
    fun deepSeekToolsTtsPostsExpectedPayloadAndListsVoices() = runBlocking {
        val server = MockWebServer()
        val rawPcm = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "audio/pcm")
                .body(Buffer().write(rawPcm))
                .build()
        )
        server.start()
        val http = HttpClient(OkHttp)
        try {
            val dsConfig = ApiProviderConfig(
                id = "ds-1",
                providerName = "DeepSeek",
                presetId = "deepseek",
                platformId = "standard",
                baseUrl = server.url("/v1").toString().trimEnd('/'),
                apiKey = "ds-secret",
                selectedModel = "deepseek-chat",
                kind = ProviderKind.OPENAI_COMPATIBLE
            )
            val client = TtsSynthesisClient(http, json)
            val voices = client.listVoices(dsConfig)
            assertEquals(listOf("stella", "tide"), voices.map { it.id })

            val res = client.synthesize(dsConfig, "deepseek-tts", "你好，我是海星。", "stella")
            assertTrue(res.isSuccess)
            val audio = res.getOrThrow()
            assertEquals("wav", audio.extension)
            assertEquals("audio/wav", audio.mimeType)
            assertEquals(44 + rawPcm.size, audio.bytes.size)
            assertEquals('R'.code.toByte(), audio.bytes[0])
            assertEquals('I'.code.toByte(), audio.bytes[1])
            assertEquals('F'.code.toByte(), audio.bytes[2])
            assertEquals('F'.code.toByte(), audio.bytes[3])

            val req = server.takeRequest()
            assertEquals("/v1/tools/tts", req.url.encodedPath)
            assertEquals("Bearer ds-secret", req.headers["Authorization"])
            assertTrue(req.headers["Content-Type"]?.startsWith("application/json") == true)
            val bodyText = req.body?.utf8().orEmpty()
            assertTrue(bodyText.contains(""""voice_id":"stella""""))
            assertTrue(bodyText.contains(""""format":"pcm""""))
            assertTrue(bodyText.contains(""""filter_markdown":true"""))
        } finally {
            http.close()
            server.close()
        }
    }

    @Test
    fun repairStreamingWavHeaderPatchesZeroMaxUintAndHugePlaceholderSizes() {
        val pcm = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val broken = TtsSynthesisClient.wrapPcmAsWav(pcm, sampleRate = 24_000).copyOf()
        // 模拟流式 WAV 将 RIFF size (4..7) 写为 0x7FFFF000，data size (40..43) 写为 0
        val bufBroken = java.nio.ByteBuffer.wrap(broken).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bufBroken.putInt(4, 0x7FFFF000)
        bufBroken.putInt(40, 0)

        val repaired = TtsSynthesisClient.repairStreamingWavHeaderIfNeeded(broken)
        val buf = java.nio.ByteBuffer.wrap(repaired).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(repaired.size - 8, buf.getInt(4))
        assertEquals(pcm.size, buf.getInt(40))
    }

    @Test
    fun deepSeekRejectsHttp200JsonErrorPayload() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""{"error":{"message":"invalid balance"}}""")
                .build()
        )
        server.start()
        val http = HttpClient(OkHttp)
        try {
            val dsConfig = ApiProviderConfig(
                id = "ds-1",
                providerName = "DeepSeek",
                presetId = "deepseek",
                platformId = "standard",
                baseUrl = server.url("/v1").toString().trimEnd('/'),
                apiKey = "ds-secret",
                selectedModel = "deepseek-chat",
                kind = ProviderKind.OPENAI_COMPATIBLE
            )
            val client = TtsSynthesisClient(http, json)
            val res = client.synthesize(dsConfig, "deepseek-tts", "测试失败场景", "stella")
            assertTrue(res.isFailure)
            assertTrue(res.exceptionOrNull()?.message?.contains("invalid balance") == true)
        } finally {
            http.close()
            server.close()
        }
    }

    @Test
    fun geminiMissingAudioMessageExtractsFinishReasonAndText() {
        val root = json.parseToJsonElement(
            """
            {
              "candidates": [
                {
                  "finishReason": "OTHER",
                  "content": {
                    "parts": [{ "text": "Model refused audio output" }]
                  }
                }
              ]
            }
            """.trimIndent()
        ).jsonObject
        val msg = TtsSynthesisClient.buildGeminiMissingAudioMessage(root)
        assertTrue(msg.contains("OTHER"))
        assertTrue(msg.contains("Model refused audio output"))
    }
}
