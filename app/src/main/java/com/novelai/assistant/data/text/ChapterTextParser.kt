package com.novelai.assistant.data.text

import java.net.URI

/**
 * 章节正文纯文本解析工具：
 * - 段落切分（保留空行以保持与 SF 服务器 `tsukkomi.row` 行号一一对应）
 * - 插图标记 `[img=...]url[/img]` 解析、URL 安全校验及占位替换
 */
object ChapterTextParser {
    private val illustrationTag = Regex(
        "\\[img(?:=[^]]*)?]\\s*(https?://[^\\s\\[]+)\\s*\\[/img]",
        RegexOption.IGNORE_CASE
    )

    /**
     * Preserve server paragraph rows. SF's `tsukkomi.row` index counts blank
     * rows too; filtering blanks would shift every later badge onto the wrong
     * paragraph. Consumers skip blank paragraphs while rendering/speaking.
     */
    fun splitParagraphs(content: String): List<String> =
        content.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n')
            .map { it.trim() }

    /**
     * Parse a standalone `[img=...]https://...[/img]` paragraph into a validated
     * HTTPS URL, or `null` if the text is not a standalone illustration tag or
     * contains an unsafe URL (non-http(s), blank host, or embedded credentials).
     */
    fun parseIllustrationUrl(text: String): String? {
        val match = illustrationTag.matchEntire(text.trim()) ?: return null
        return normalizeIllustrationUrl(match.groupValues[1])
    }

    fun isIllustration(text: String): Boolean = parseIllustrationUrl(text) != null

    /**
     * Replace `[img]...[/img]` tags within [text] using [transform] (defaults to `[插图]`).
     * Tags with unsafe URLs are left unchanged.
     */
    fun replaceIllustrationTags(
        text: String,
        transform: (url: String) -> String = { "[插图]" }
    ): String = illustrationTag.replace(text) { match ->
        normalizeIllustrationUrl(match.groupValues[1])?.let(transform) ?: match.value
    }

    private fun normalizeIllustrationUrl(rawUrl: String): String? {
        val url = rawUrl.replaceFirst("http://", "https://", ignoreCase = true)
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
        return url
    }
}
