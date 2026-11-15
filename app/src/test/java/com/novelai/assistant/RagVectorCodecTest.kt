package com.novelai.assistant

import com.novelai.assistant.data.rag.RagVectorCodec
import com.novelai.assistant.data.rag.StoryMemoryService
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RagVectorCodecTest {

    @Test
    fun `encodeVector and decodeVector roundtrip accurately`() {
        val vector = floatArrayOf(0.125f, -0.875f, 0f, 1.5f, -3.25f)
        val encoded = RagVectorCodec.encodeVector(vector)
        val decoded = RagVectorCodec.decodeVector(encoded)
        assertNotNull(decoded)
        assertArrayEquals(vector, decoded!!, 1e-6f)
    }

    @Test
    fun `decodeVector rejects empty or corrupted byte lengths`() {
        assertNull(RagVectorCodec.decodeVector(ByteArray(0)))
        assertNull(RagVectorCodec.decodeVector(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `chapterContentHash is deterministic and sensitive to title or content changes`() {
        val h1 = RagVectorCodec.chapterContentHash("第一章 初入星门", "夜色深沉，星舰缓缓驶入港湾。")
        val h2 = RagVectorCodec.chapterContentHash("第一章 初入星门", "夜色深沉，星舰缓缓驶入港湾。")
        val h3 = RagVectorCodec.chapterContentHash("第一章 初入星门", "夜色深沉，星舰突然加速！")
        val h4 = RagVectorCodec.chapterContentHash("第一章 改名", "夜色深沉，星舰缓缓驶入港湾。")
        assertEquals(h1, h2)
        assertNotEquals(h1, h3)
        assertNotEquals(h1, h4)
    }

    @Test
    fun `parseSummaryReply parses tagged summary and fallback`() {
        val formatted = """
            【剧情摘要】：主角在黑市拍卖会拍下残破罗盘，意外触发追杀。
            【人物与伏笔状态】：主角暗伤未愈；神秘买家身份待揭晓。
        """.trimIndent()
        val parsed = StoryMemoryService.parseSummaryReply(formatted)
        assertNotNull(parsed)
        assertEquals("主角在黑市拍卖会拍下残破罗盘，意外触发追杀。", parsed!!.first)
        assertEquals("主角暗伤未愈；神秘买家身份待揭晓。", parsed.second)

        val fallback = StoryMemoryService.parseSummaryReply("仅输出一段纯文本摘要。")
        assertNotNull(fallback)
        assertEquals("仅输出一段纯文本摘要。", fallback!!.first)
        assertEquals("", fallback.second)
    }
}
