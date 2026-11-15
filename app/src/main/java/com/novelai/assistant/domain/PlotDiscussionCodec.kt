package com.novelai.assistant.domain

data class ParsedPlotReply(
    val displayContent: String,
    val directions: List<String> = emptyList(),
    val proposedMasterOutline: String? = null,
    val proposedUpcomingPlan: String? = null
)

/**
 * AI 剧情讨论结构化协议编解码器：
 * 兼容所有文本模型，允许 AI 在自然对话末尾附带可选结构化标记：
 * - [[DIRECTIONS]] ... [[/DIRECTIONS]]：2~4 个可供作者直接点击的剧情方向/选择
 * - [[MASTER_OUTLINE]] ... [[/MASTER_OUTLINE]]：更新后的全书主线总纲草案
 * - [[UPCOMING_PLAN]] ... [[/UPCOMING_PLAN]]：更新后的近期分章规划草案
 */
object PlotDiscussionCodec {

    private const val DIR_START = "[[DIRECTIONS]]"
    private const val DIR_END = "[[/DIRECTIONS]]"
    private const val MASTER_START = "[[MASTER_OUTLINE]]"
    private const val MASTER_END = "[[/MASTER_OUTLINE]]"
    private const val PLAN_START = "[[UPCOMING_PLAN]]"
    private const val PLAN_END = "[[/UPCOMING_PLAN]]"
    private const val TITLES_START = "[[TITLES]]"
    private const val TITLES_END = "[[/TITLES]]"

    private val blockMarkers = listOf(DIR_START, MASTER_START, PLAN_START, TITLES_START)

    fun parse(rawText: String): ParsedPlotReply {
        if (rawText.isBlank()) return ParsedPlotReply("")

        var working = rawText
        val master = extractBlock(working, MASTER_START, MASTER_END)?.also {
            working = removeBlock(working, MASTER_START, MASTER_END)
        }
        val upcoming = extractBlock(working, PLAN_START, PLAN_END)?.also {
            working = removeBlock(working, PLAN_START, PLAN_END)
        }
        val dirBlock = extractBlock(working, DIR_START, DIR_END)?.also {
            working = removeBlock(working, DIR_START, DIR_END)
        }
        working = removeBlock(working, TITLES_START, TITLES_END)

        val directions = dirBlock
            ?.lineSequence()
            ?.map { line ->
                line.trim()
                    .removePrefix("- ")
                    .removePrefix("• ")
                    .replace(Regex("^\\d+[.、)）]\\s*"), "")
                    .trim()
            }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.take(4)
            ?.toList()
            .orEmpty()

        return ParsedPlotReply(
            displayContent = stripTrailingPartialTag(working).trim(),
            directions = directions,
            proposedMasterOutline = master?.takeIf { it.isNotBlank() },
            proposedUpcomingPlan = upcoming?.takeIf { it.isNotBlank() }
        )
    }

    /** 流式输出过程中实时剥离已完整或半截的协议标记，防止界面闪烁原始标签 */
    fun stripStreamingTags(rawText: String): String {
        if (rawText.isBlank()) return ""
        var text = rawText
        text = removeBlock(text, MASTER_START, MASTER_END)
        text = removeBlock(text, PLAN_START, PLAN_END)
        text = removeBlock(text, DIR_START, DIR_END)
        text = removeBlock(text, TITLES_START, TITLES_END)

        val earliestOpen = blockMarkers
            .mapNotNull { marker -> text.indexOf(marker).takeIf { it >= 0 } }
            .minOrNull()
        if (earliestOpen != null) {
            text = text.substring(0, earliestOpen)
        }
        return stripTrailingPartialTag(text).trimEnd()
    }

    private fun extractBlock(source: String, startTag: String, endTag: String): String? {
        val start = source.indexOf(startTag)
        if (start < 0) return null
        val contentStart = start + startTag.length
        val end = source.indexOf(endTag, startIndex = contentStart)
        val raw = if (end >= 0) {
            source.substring(contentStart, end)
        } else {
            source.substring(contentStart)
        }
        return raw.trim()
    }

    private fun removeBlock(source: String, startTag: String, endTag: String): String {
        val start = source.indexOf(startTag)
        if (start < 0) return source
        val end = source.indexOf(endTag, startIndex = start + startTag.length)
        return if (end >= 0) {
            source.removeRange(start, end + endTag.length)
        } else {
            source.substring(0, start)
        }
    }

    private fun stripTrailingPartialTag(text: String): String {
        val trimmedEnd = text.trimEnd()
        val lastOpen = trimmedEnd.lastIndexOf("[[")
        if (lastOpen < 0) return text
        val tail = trimmedEnd.substring(lastOpen)
        val looksLikeTagPrefix = blockMarkers.any { marker ->
            marker.startsWith(tail) || tail.startsWith("[[") && !tail.contains("]]")
        }
        return if (looksLikeTagPrefix) {
            trimmedEnd.substring(0, lastOpen)
        } else {
            text
        }
    }
}
