package com.novelai.assistant

import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.BookSourceType
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterOriginType
import com.novelai.assistant.data.network.EmbeddingClient
import com.novelai.assistant.data.network.ProviderKind
import com.novelai.assistant.data.rag.ChapterSearchIndex
import com.novelai.assistant.data.rag.RetrievalService
import com.novelai.assistant.data.rag.RetrievedSnippet
import com.novelai.assistant.data.repository.AiConfigRepository
import com.novelai.assistant.data.repository.ApiProviderConfig
import com.novelai.assistant.data.repository.ModelRoute
import com.novelai.assistant.data.repository.TaskScene
import com.novelai.assistant.domain.PromptBuilder
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RagHybridRetrievalTest {

    private fun book() = BookEntity(
        id = "book-rag",
        title = "星陨手记",
        author = "陈墨",
        coverUri = null,
        sourcePath = "local/rag.txt",
        sourceType = BookSourceType.LOCAL_TXT,
        totalChapters = 5
    )

    private fun chapter(
        id: String,
        index: Int,
        title: String,
        content: String
    ) = ChapterEntity(
        id = id,
        bookId = "book-rag",
        chapterIndex = index,
        title = title,
        content = content,
        originType = ChapterOriginType.ORIGINAL
    )

    @Test
    fun paragraphChunkingLocatesLateChapterClueSnippet() {
        val filler1 = "开篇描写清晨薄雾笼罩着石桥与古镇街道，茶铺掌柜正在擦拭柜台，行人稀少，风声轻微拂过瓦片。"
            .repeat(12)
        val filler2 = "午后集市逐渐喧闹起来，商贩叫卖声此起彼伏，主角站在街角观察往来马车与巡逻守卫。"
            .repeat(12)
        val clueParagraph = "直到夜深人静时，林昼点亮油灯仔细端详那枚旧物，终于看清青铜戒指内侧刻着一行小字：星陨历七年赠予陆沉。"
        val longContent = "$filler1\n\n$filler2\n\n$clueParagraph"
        assertTrue(longContent.length > 900)

        val ch = chapter("ch-12", 11, "第十二章 旧物微光", longContent)
        val index = ChapterSearchIndex.build(listOf(ch))

        val hits = index.searchSnippets(
            query = "青铜戒指内侧刻着什么小字",
            topK = 2
        )

        assertTrue(hits.isNotEmpty())
        assertEquals("ch-12", hits.first().chapter.id)
        assertTrue(hits.first().snippet.contains("青铜戒指内侧刻着一行小字"))
        assertFalse(hits.first().snippet.startsWith("开篇描写清晨薄雾"))
        assertFalse(hits.first().usedEmbedding)
    }

    @Test
    fun embeddingClientHandlesOpenAiGeminiAndRejectsAnthropic() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse(
                    code = 200,
                    body = """
                        {
                          "data": [
                            {"index": 1, "embedding": [0.1, 0.9]},
                            {"index": 0, "embedding": [0.8, 0.2]}
                          ]
                        }
                    """.trimIndent()
                )
            )
            server.enqueue(
                MockResponse(
                    code = 200,
                    body = """
                        {
                          "embeddings": [
                            {"values": [0.5, 0.6, 0.7]}
                          ]
                        }
                    """.trimIndent()
                )
            )

            val client = HttpClient(OkHttp)
            val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
            val embeddingClient = EmbeddingClient(client, json)

            val openAiConfig = ApiProviderConfig(
                id = "p-openai",
                providerName = "OpenAI",
                baseUrl = server.url("/v1").toString(),
                apiKey = "sk-test",
                selectedModel = "gpt-4o-mini",
                kind = ProviderKind.OPENAI_COMPATIBLE
            )
            val openAiResult = embeddingClient.embed(openAiConfig, "text-embedding-3-small", listOf("甲", "乙"))
            assertTrue(openAiResult.isSuccess)
            val vectors = openAiResult.getOrThrow()
            assertEquals(2, vectors.size)
            assertEquals(0.8f, vectors[0][0], 0.0001f)
            assertEquals(0.9f, vectors[1][1], 0.0001f)

            val req1 = server.takeRequest()
            assertEquals("/v1/embeddings", req1.target)
            assertTrue(req1.body!!.utf8().contains("text-embedding-3-small"))

            val geminiConfig = ApiProviderConfig(
                id = "p-gemini",
                providerName = "Gemini",
                baseUrl = server.url("/v1beta").toString(),
                apiKey = "AIzaTest",
                selectedModel = "gemini-2.5-flash",
                kind = ProviderKind.GOOGLE_GEMINI
            )
            val geminiResult = embeddingClient.embed(geminiConfig, "text-embedding-004", listOf("查询测试"))
            assertTrue(geminiResult.isSuccess)
            assertEquals(3, geminiResult.getOrThrow().first().size)

            val req2 = server.takeRequest()
            assertEquals("/v1beta/models/text-embedding-004:batchEmbedContents", req2.target)

            val anthropicConfig = ApiProviderConfig(
                id = "p-claude",
                providerName = "Claude",
                baseUrl = server.url("/v1").toString(),
                apiKey = "sk-ant-test",
                selectedModel = "claude-sonnet-4-5",
                kind = ProviderKind.ANTHROPIC
            )
            val anthropicResult = embeddingClient.embed(anthropicConfig, "any", listOf("hi"))
            assertTrue(anthropicResult.isFailure)

            client.close()
        } finally {
            server.close()
        }
    }

    @Test
    fun cosineSimilarityHandlesEdgeCases() {
        assertEquals(1.0, EmbeddingClient.cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(3f, 0f)), 1e-6)
        assertEquals(0.0, EmbeddingClient.cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6)
        assertEquals(0.0, EmbeddingClient.cosineSimilarity(floatArrayOf(1f), floatArrayOf(1f, 2f)), 1e-6)
        assertEquals(0.0, EmbeddingClient.cosineSimilarity(floatArrayOf(), floatArrayOf()), 1e-6)
    }

    @Test
    fun aiConfigRepositoryEmbeddingRouteIsStrictlyOptIn() {
        val enabled = ApiProviderConfig(
            id = "p-1",
            providerName = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-1",
            selectedModel = "gpt-4o-mini",
            isEnabled = true
        )
        val disabled = enabled.copy(id = "p-2", isEnabled = false)

        // 聊天场景未配置路由时允许回退到首个启用提供商
        val cont = AiConfigRepository.resolveRouteForScene(TaskScene.CONTINUATION, ModelRoute(), listOf(enabled))
        assertNotNull(cont)

        // 向量场景未配置路由、模型留空、或提供商停用时必须返回 null
        assertNull(AiConfigRepository.resolveRouteForScene(TaskScene.EMBEDDING, ModelRoute(), listOf(enabled)))
        assertNull(AiConfigRepository.resolveRouteForScene(TaskScene.EMBEDDING, ModelRoute("p-1", "   "), listOf(enabled)))
        assertNull(
            AiConfigRepository.resolveRouteForScene(
                TaskScene.EMBEDDING,
                ModelRoute("p-2", "text-embedding-3-small"),
                listOf(enabled, disabled)
            )
        )

        val activeEmb = AiConfigRepository.resolveRouteForScene(
            TaskScene.EMBEDDING,
            ModelRoute("p-1", "text-embedding-3-small"),
            listOf(enabled)
        )
        assertEquals("p-1", activeEmb?.first?.id)
        assertEquals("text-embedding-3-small", activeEmb?.second)
    }

    @Test
    fun aiConfigRepositoryOutlineRouteFallsBackToContinuationWhenUnconfigured() {
        val contProvider = ApiProviderConfig(
            id = "p-cont",
            providerName = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            apiKey = "sk-cont",
            selectedModel = "deepseek-chat",
            isEnabled = true
        )
        val outlineProvider = ApiProviderConfig(
            id = "p-out",
            providerName = "Claude",
            baseUrl = "https://api.anthropic.com/v1",
            apiKey = "sk-out",
            selectedModel = "claude-sonnet-4-20250514",
            isEnabled = true
        )
        val providers = listOf(contProvider, outlineProvider)
        val contRoute = ModelRoute("p-cont", "deepseek-chat")

        // 大纲未单独分流时，自动继承续写路由（而非退回列表首个 provider）
        val inherited = AiConfigRepository.resolveRouteForScene(
            scene = TaskScene.OUTLINE,
            route = ModelRoute(),
            providers = providers,
            fallbackRoute = contRoute
        )
        assertEquals("p-cont", inherited?.first?.id)
        assertEquals("deepseek-chat", inherited?.second)

        // 为大纲指定高阶模型后，优先使用大纲专属模型
        val dedicated = AiConfigRepository.resolveRouteForScene(
            scene = TaskScene.OUTLINE,
            route = ModelRoute("p-out", "claude-opus-4-20250514"),
            providers = providers,
            fallbackRoute = contRoute
        )
        assertEquals("p-out", dedicated?.first?.id)
        assertEquals("claude-opus-4-20250514", dedicated?.second)
    }

    @Test
    fun hybridRerankBoostsSemanticMatchAndDeduplicatesChapters() {
        val ch1 = chapter("c1", 0, "第一章 噪音", "表面字面词堆叠")
        val ch2 = chapter("c2", 1, "第二章 真正线索", "语义高度相关的伏笔片段")
        val candidates = listOf(
            RetrievedSnippet(ch1, "字面重复片段 A", score = 5.0),
            RetrievedSnippet(ch1, "字面重复片段 B", score = 4.6),
            RetrievedSnippet(ch2, "语义真正命中片段", score = 4.0)
        )
        val queryVec = floatArrayOf(1f, 0f)
        val candidateVecs = listOf(
            floatArrayOf(0f, 1f),
            floatArrayOf(0.1f, 0.99f),
            floatArrayOf(0.99f, 0.1f)
        )

        val reranked = RetrievalService.rerankSnippetsWithVectors(
            candidates = candidates,
            queryVector = queryVec,
            candidateVectors = candidateVecs,
            topK = 3
        )

        assertEquals(2, reranked.size) // 按章节去重
        assertEquals("c2", reranked.first().chapter.id)
        assertTrue(reranked.all { it.usedEmbedding })
    }

    @Test
    fun promptBuilderPrefersRetrievedSnippetsOverChapterHead() {
        val ch = chapter(
            id = "c1",
            index = 0,
            title = "第五章 地下密室",
            content = "章节开头只是平常散步。" + "无关日常".repeat(220) + "真正的暗门机关位于书架第三排左移三寸。"
        )
        val snippet = RetrievedSnippet(
            chapter = ch,
            snippet = "真正的暗门机关位于书架第三排左移三寸。",
            score = 8.4,
            usedEmbedding = true
        )

        val contPrompt = PromptBuilder.continuationMessages(
            book = book(),
            previousChapters = listOf(chapter("c2", 1, "第六章", "两人走入客厅。")),
            instruction = "寻找密室入口",
            retrievedSnippets = listOf(snippet)
        ).first().content

        assertTrue(contPrompt.contains("真正的暗门机关位于书架第三排左移三寸"))
        assertFalse(contPrompt.contains("章节开头只是平常散步"))

        val compPrompt = PromptBuilder.companionMessages(
            book = book(),
            currentChapter = null,
            history = emptyList(),
            question = "密室机关在哪？",
            retrievedSnippets = listOf(snippet)
        ).first().content

        assertTrue(compPrompt.contains("真正的暗门机关位于书架第三排左移三寸"))
        assertFalse(compPrompt.contains("章节开头只是平常散步"))
    }
}
