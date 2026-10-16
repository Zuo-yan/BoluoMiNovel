package com.novelai.assistant.data.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 统一网络/大模型服务错误解析工具
 * 自动提取 OpenAI/Claude/自定义兼容网关在 4xx/5xx 响应中返回的结构化错误信息
 */
object AiErrorParser {
    fun parse(code: Int, body: String, providerLabel: String): String {
        val trimmed = body.trim()
        val parsed = runCatching {
            val element = Json.parseToJsonElement(trimmed)
            val obj = element as? JsonObject ?: element.jsonObject
            val errObj = obj["error"] as? JsonObject
            errObj?.text("message")?.ifBlank { null }
                ?: obj.text("message").ifBlank { null }
                ?: obj.text("msg").ifBlank { null }
                ?: obj.text("detail").ifBlank { null }
                ?: errObj?.text("type")?.ifBlank { null }
        }.getOrNull()

        return if (parsed != null && parsed.isNotBlank()) {
            "$providerLabel (HTTP $code): $parsed"
        } else {
            when (code) {
                401 -> "$providerLabel 返回 HTTP 401：API Key 无效或未授权，请检查密钥"
                403 -> "$providerLabel 返回 HTTP 403：访问受限或模型无权限"
                404 -> "$providerLabel 返回 HTTP 404：模型不存在或接口地址有误"
                429 -> "$providerLabel 返回 HTTP 429：额度耗尽或请求过于频繁"
                else -> "$providerLabel 返回 HTTP $code，请检查地址、模型、密钥权限和参数"
            }
        }
    }

    private fun JsonObject.text(key: String): String =
        (get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
}
