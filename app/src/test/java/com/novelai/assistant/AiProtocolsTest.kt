package com.novelai.assistant

import com.novelai.assistant.data.network.*
import com.novelai.assistant.data.repository.*
import mockwebserver3.MockWebServer
import mockwebserver3.MockResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class AiProtocolsTest {
    private fun config(kind: ProviderKind, url: String = "https://example.test/v1") = ApiProviderConfig(
        providerName = "Test", baseUrl = url, apiKey = "test-key", selectedModel = "test-model", kind = kind,
        configVersion = 2, temperatureEnabled = false)
    private val messages = listOf(ChatMessage("system", "系统指令"), ChatMessage("user", "问题"), ChatMessage("assistant", "前文"), ChatMessage("user", "续写"))
    private fun obj(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test fun fourRequestBodiesHaveNativeStructuresAndDifferentTokenFields() {
        val chat = AiProtocol.body(config(ProviderKind.OPENAI_COMPATIBLE), messages, 1234, .7f)
        assertEquals(1234, chat["max_tokens"]!!.jsonPrimitive.int); assertNull(chat["temperature"])
        assertEquals(4, chat["messages"]!!.jsonArray.size)
        val official = AiProtocol.body(config(ProviderKind.OPENAI_COMPATIBLE, "https://api.openai.com/v1"), messages, 1234, .7f)
        assertNotNull(official["max_completion_tokens"]); assertNull(official["max_tokens"])
        val responses = AiProtocol.body(config(ProviderKind.OPENAI_RESPONSES), messages, 2000, .7f)
        assertEquals(2000, responses["max_output_tokens"]!!.jsonPrimitive.int)
        assertFalse(responses["store"]!!.jsonPrimitive.boolean); assertNotNull(responses["input"]); assertNull(responses["messages"])
        val anthropic = AiProtocol.body(config(ProviderKind.ANTHROPIC), messages, 3000, .7f)
        assertEquals("系统指令", anthropic["system"]!!.jsonPrimitive.content)
        assertEquals(3, anthropic["messages"]!!.jsonArray.size)
        assertFalse(anthropic["messages"]!!.jsonArray.any { it.jsonObject["role"]!!.jsonPrimitive.content == "system" })
        val google = AiProtocol.body(config(ProviderKind.GOOGLE_GEMINI), messages, 4000, .7f)
        assertEquals("model", google["contents"]!!.jsonArray[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals(4000, google["generationConfig"]!!.jsonObject["maxOutputTokens"]!!.jsonPrimitive.int)
        assertNotNull(google["systemInstruction"]); assertNull(google["stream"])
    }

    @Test fun authenticationAndEndpointNormalizationDoNotLeakKeysIntoUrls() {
        val anthropic = config(ProviderKind.ANTHROPIC, "https://api.anthropic.com")
        assertEquals("https://api.anthropic.com/v1/messages", AiProtocol.endpoint(anthropic))
        assertEquals("test-key", AiProtocol.headers(anthropic)["x-api-key"])
        assertNull(AiProtocol.headers(anthropic)["Authorization"])
        val google = config(ProviderKind.GOOGLE_GEMINI, "https://generativelanguage.googleapis.com/v1beta").copy(selectedModel = "models/gemini-test")
        assertTrue(AiProtocol.endpoint(google).endsWith("/models/gemini-test:streamGenerateContent?alt=sse"))
        assertFalse(AiProtocol.endpoint(google).contains("test-key")); assertEquals("test-key", AiProtocol.headers(google)["x-goog-api-key"])
        assertEquals("https://relay.test/anthropic/v1/messages", AiProtocol.endpoint(config(ProviderKind.ANTHROPIC, "https://relay.test/anthropic")))
    }

    @Test fun legacyAnthropicConfigKeepsItsHistoricalChatBehavior() {
        val old = Json.decodeFromString<ApiProviderConfig>("""{"providerName":"Legacy","baseUrl":"https://relay.test/v1","apiKey":"test","selectedModel":"claude-test","kind":"ANTHROPIC","maxTokens":6000}""")
        val migrated = migrateProviderConfig(old)
        assertEquals(ProviderKind.OPENAI_COMPATIBLE, migrated.kind)
        assertEquals(6000, migrated.maxTokens); assertFalse(migrated.autoMaxTokens)
        assertEquals("max_tokens", migrated.chatTokenField)
        val current = config(ProviderKind.ANTHROPIC)
        assertEquals(ProviderKind.ANTHROPIC, migrateProviderConfig(current).kind)
    }

    @Test fun metadataRejectsNullZeroAndNegativeLimitsAndBindsToExactConnection() {
        val c = config(ProviderKind.ANTHROPIC)
        val invalid = ModelMetadata.parse(c, obj("""{"max_tokens":0,"max_input_tokens":null}"""))
        assertNull(invalid.output); assertNull(invalid.context)
        val valid = ModelMetadata.parse(c, obj("""{"max_tokens":64000,"max_input_tokens":200000}"""))
        assertEquals(64000, valid.output); assertEquals(200000, valid.context)
        assertTrue(valid.matches(c)); assertFalse(valid.matches(c.copy(selectedModel = "other")))
        assertFalse(valid.matches(c.copy(baseUrl = "https://other.test/v1")))
        assertFalse(valid.matches(c.copy(kind = ProviderKind.OPENAI_COMPATIBLE)))
        val google = ModelMetadata.parse(config(ProviderKind.GOOGLE_GEMINI), obj("""{"inputTokenLimit":1000000,"outputTokenLimit":8192}"""))
        assertEquals(8192, google.output); assertEquals(1000000, google.context)
        assertNull(ModelMetadata.parse(c, obj("""{"max_tokens":-1}""")).output)
    }

    @Test fun officialFallbackDoesNotPretendToDetectRelayLimits() {
        val official = config(ProviderKind.OPENAI_RESPONSES, "https://api.openai.com/v1").copy(selectedModel = "gpt-4.1")
        assertEquals(32768, ModelMetadata.builtin(official)!!.output)
        assertTrue(ModelMetadata.builtin(official)!!.source.startsWith("内置"))
        assertNull(ModelMetadata.builtin(official.copy(baseUrl = "https://relay.test/v1")))
        assertNull(ModelMetadata.builtin(official.copy(selectedModel = "unknown-model")))
        assertEquals(128000, ModelMetadata.builtin(official.copy(selectedModel = "gpt-6.1-sol"))!!.output)
        val deepseek = config(ProviderKind.OPENAI_COMPATIBLE, "https://api.deepseek.com/v1").copy(selectedModel = "deepseek-flash")
        assertEquals(393216, ModelMetadata.builtin(deepseek)!!.output)
        assertNull(ModelMetadata.builtin(deepseek.copy(baseUrl = "https://relay.test/v1")))
    }

    @Test fun presetCatalogContainsAllNineVendorsAndExplicitPlatforms() {
        assertEquals(9, ProviderPresets.items.count { it.id != "custom" })
        assertTrue(ProviderPresets.items.all { it.platforms.isNotEmpty() })
        assertEquals(ProviderPresets.items.size, ProviderPresets.items.map { it.id }.distinct().size)
        for (p in ProviderPresets.items.filter { it.id != "custom" }) for (platform in p.platforms) {
            assertTrue(platform.baseUrl.startsWith("https://"))
            assertFalse(platform.baseUrl.contains("coding"))
            val base = platform.baseUrl.replace("{workspace}", "workspace123")
            assertTrue(AiProtocol.endpoint(config(platform.kind, base)).startsWith("https://"))
        }
    }

    @Test fun multilineSseFramesAndCommentsAreHandledAtEventBoundaries() {
        val reader = SseFrameReader()
        assertNull(reader.line(": ping")); assertNull(reader.line("event: response.output_text.delta"))
        assertNull(reader.line("data: {\"type\":\"response.output_text.delta\","))
        assertNull(reader.line("data: \"delta\":\"正文\"}"))
        val frame = reader.line("")!!
        assertEquals("response.output_text.delta", frame.second)
        assertEquals("正文", (AiStreamDecoder(ProviderKind.OPENAI_RESPONSES).decode(frame.first).single() as StreamEvent.Delta).text)
    }

    private data class Fixture(val kind: ProviderKind, val suffix: String, val frames: List<String>)
    private val fixtures = listOf(
        Fixture(ProviderKind.OPENAI_COMPATIBLE, "/chat/completions", listOf(
            """{"choices":[{"delta":{"content":"正文"},"finish_reason":null}]}""",
            """{"choices":[{"delta":{},"finish_reason":"stop"}]}""",
            """{"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":20}}""", "[DONE]")),
        Fixture(ProviderKind.OPENAI_RESPONSES, "/responses", listOf(
            """{"type":"response.reasoning_summary_text.delta","delta":"不可混入正文"}""",
            """{"type":"response.output_text.delta","delta":"正文"}""",
            """{"type":"response.completed","response":{"usage":{"input_tokens":10,"output_tokens":20}}}""")),
        Fixture(ProviderKind.ANTHROPIC, "/messages", listOf(
            """{"type":"message_start","message":{"usage":{"input_tokens":10,"output_tokens":0}}}""",
            """{"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"不可混入正文"}}""",
            """{"type":"content_block_delta","delta":{"type":"text_delta","text":"正文"}}""",
            """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":20}}""",
            """{"type":"message_stop"}""")),
        Fixture(ProviderKind.GOOGLE_GEMINI, ":streamGenerateContent", listOf(
            """{"candidates":[{"content":{"parts":[{"text":"不可混入正文","thought":true},{"text":"正文"}]}}]}""",
            """{"candidates":[{"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":15,"thoughtsTokenCount":5}}"""))
    )

    @Test fun allFourProtocolsStreamOverHttpWithUsageAndExactlyOneFinish() = runBlocking {
        val server = MockWebServer()
        server.start()
        val http = HttpClient(OkHttp)
        try {
            for (fixture in fixtures) {
                repeat(2) { server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body(fixture.frames.joinToString("") { "data: $it\n\n" }).build()) }
                val c = config(fixture.kind, server.url("/v1").toString())
                val client = ProtocolAiClient(http, fixture.kind, ModelMetadataClient(http))
                val events = client.streamChat(c, messages, 2048, .7f).toList()
                assertEquals(fixture.kind.name, "正文", events.filterIsInstance<StreamEvent.Delta>().joinToString("") { it.text })
                assertTrue(events.none { it is StreamEvent.Error })
                assertEquals(1, events.count { it is StreamEvent.Done })
                assertEquals(StreamEvent.Usage(10, 20), events.filterIsInstance<StreamEvent.Usage>().last())
                val stopped = client.streamChat(c, messages, 2048, .7f).take(1).toList()
                assertEquals(1, stopped.size)
                repeat(2) {
                    val observed = server.takeRequest(5, TimeUnit.SECONDS)!!
                    assertTrue(observed.url.encodedPath.endsWith(fixture.suffix))
                    assertEquals("POST", observed.method)
                    val request = obj(observed.body!!.utf8())
                    when (fixture.kind) {
                        ProviderKind.ANTHROPIC -> { assertEquals("test-key", observed.headers["x-api-key"]); assertNotNull(request["system"]) }
                        ProviderKind.GOOGLE_GEMINI -> { assertEquals("test-key", observed.headers["x-goog-api-key"]); assertNotNull(request["contents"]) }
                        else -> assertEquals("Bearer test-key", observed.headers["Authorization"])
                    }
                }
            }
            assertEquals(8, server.requestCount)
        } finally { http.close(); server.close() }
    }

    @Test fun metadataPaginationAndAutoBudgetUseCurrentEndpoint() = runBlocking {
        val server = MockWebServer()
        fun enqueue(body: String) = server.enqueue(MockResponse.Builder().body(body).build())
        val capability = """{"id":"test-model","max_output_tokens":12345,"context_length":50000}"""
        val completion = "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n\n"
        enqueue("""{"data":[{"id":"first"}],"has_more":true,"last_id":"cursor"}""")
        enqueue("""{"data":[{"id":"second"}],"has_more":false}""")
        enqueue(capability); enqueue(capability); enqueue(completion); enqueue(completion)
        server.start(); val http = HttpClient(OkHttp)
        try {
            val metadata = ModelMetadataClient(http)
            val c = config(ProviderKind.OPENAI_COMPATIBLE, server.url("/v1").toString()).copy(autoMaxTokens = true)
            assertEquals(listOf("first", "second"), metadata.list(c))
            assertEquals(12345, metadata.detect(c).output)
            ProtocolAiClient(http, c.kind, metadata).streamChat(c, messages, 4096, .7f).toList()
            ProtocolAiClient(http, c.kind, metadata).streamChat(c.copy(autoMaxTokens = false), messages, 555, .7f).toList()
            val requests = (1..6).map { server.takeRequest(5, TimeUnit.SECONDS)!! }
            assertTrue(requests[1].url.toString().contains("after=cursor"))
            assertEquals(12345, obj(requests[4].body!!.utf8())["max_tokens"]!!.jsonPrimitive.int)
            assertEquals(555, obj(requests[5].body!!.utf8())["max_tokens"]!!.jsonPrimitive.int)
        } finally { http.close(); server.close() }
    }

    @Test fun refusalAndPrematureEndDoNotBecomeSuccessfulOutput() {
        val decoder = AiStreamDecoder(ProviderKind.OPENAI_RESPONSES)
        assertTrue(decoder.decode("""{"type":"response.failed","response":{}}""").single() is StreamEvent.Error)
        assertTrue(decoder.finished)
        val google = AiStreamDecoder(ProviderKind.GOOGLE_GEMINI)
        assertTrue(google.decode("""{"promptFeedback":{"blockReason":"SAFETY"}}""").single() is StreamEvent.Error)
    }

    @Test fun httpErrorsTruncatedStreamsAndEmptyRepliesPreserveFailureState() = runBlocking {
        val server = MockWebServer(); server.start(); val http = HttpClient(OkHttp)
        try {
            val c = config(ProviderKind.OPENAI_COMPATIBLE, server.url("/v1").toString())
            val client = ProtocolAiClient(http, c.kind, ModelMetadataClient(http))
            server.enqueue(MockResponse.Builder().code(401).body("sensitive provider detail").build())
            val rejected = client.streamChat(c, messages, 500, .7f).toList()
            assertTrue(rejected.single() is StreamEvent.Error)
            assertFalse((rejected.single() as StreamEvent.Error).message.contains("sensitive"))
            server.enqueue(MockResponse.Builder().body("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n").build())
            val partial = client.streamChat(c, messages, 500, .7f).toList()
            assertEquals("partial", (partial.first() as StreamEvent.Delta).text)
            assertTrue(partial.last() is StreamEvent.Error); assertFalse(partial.any { it is StreamEvent.Done })
            server.enqueue(MockResponse.Builder().body("data: [DONE]\n\n").build())
            assertTrue(client.streamChat(c, messages, 500, .7f).toList().single() is StreamEvent.Error)
        } finally { http.close(); server.close() }
    }
}
