package com.novelai.assistant.data.rag

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * 本地持久化 RAG 向量二进制编解码与章节内容哈希工具：
 * - FloatArray <-> ByteArray 大端序列化，紧凑且无 JSON 解析开销；
 * - 章节哈希用于判断原著/续写章节是否改动，实现增量缓存而非全书失效。
 */
object RagVectorCodec {

    fun encodeVector(vector: FloatArray): ByteArray {
        if (vector.isEmpty()) return ByteArray(0)
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.BIG_ENDIAN)
        for (v in vector) {
            buffer.putFloat(v)
        }
        return buffer.array()
    }

    fun decodeVector(bytes: ByteArray): FloatArray? {
        if (bytes.isEmpty() || bytes.size % 4 != 0) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val result = FloatArray(bytes.size / 4)
        for (i in result.indices) {
            result[i] = buffer.getFloat()
        }
        return result
    }

    fun chapterContentHash(title: String, content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(title.toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(content.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }.take(24)
    }

    fun snippetHash(snippet: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(snippet.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }.take(20)
    }
}
