package com.novelai.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletItem(val text: String, val indentLevel: Int = 0) : MarkdownBlock
    data class OrderedItem(val number: String, val text: String, val indentLevel: Int = 0) : MarkdownBlock
    data class BlockQuote(val text: String) : MarkdownBlock
    data class CodeBlock(val code: String, val language: String = "") : MarkdownBlock
    data object Divider : MarkdownBlock
}

internal fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    if (raw.isBlank()) return emptyList()
    val lines = raw.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraphBuffer = mutableListOf<String>()
    val quoteBuffer = mutableListOf<String>()
    var inCodeBlock = false
    var codeLang = ""
    val codeBuffer = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraphBuffer.joinToString("\n"))
            paragraphBuffer.clear()
        }
    }

    fun flushQuote() {
        if (quoteBuffer.isNotEmpty()) {
            blocks += MarkdownBlock.BlockQuote(quoteBuffer.joinToString("\n"))
            quoteBuffer.clear()
        }
    }

    val headingRegex = Regex("""^(#{1,6})\s+(.+)$""")
    val dividerRegex = Regex("""^\s*([-*_])(?:\s*\1){2,}\s*$""")
    val bulletRegex = Regex("""^(\s*)[-*+]\s+(.+)$""")
    val orderedRegex = Regex("""^(\s*)(\d+)[.)]\s+(.+)$""")

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            flushParagraph()
            flushQuote()
            if (!inCodeBlock) {
                inCodeBlock = true
                codeLang = trimmed.removePrefix("```").trim()
                codeBuffer.clear()
            } else {
                blocks += MarkdownBlock.CodeBlock(codeBuffer.joinToString("\n"), codeLang)
                codeBuffer.clear()
                codeLang = ""
                inCodeBlock = false
            }
            continue
        }
        if (inCodeBlock) {
            codeBuffer += line
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            flushQuote()
            continue
        }

        if (dividerRegex.matches(line)) {
            flushParagraph()
            flushQuote()
            blocks += MarkdownBlock.Divider
            continue
        }

        headingRegex.matchEntire(trimmed)?.let { m ->
            flushParagraph()
            flushQuote()
            blocks += MarkdownBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim())
            continue
        }

        if (trimmed.startsWith(">")) {
            flushParagraph()
            quoteBuffer += trimmed.removePrefix(">").trimStart()
            continue
        } else {
            flushQuote()
        }

        bulletRegex.matchEntire(line)?.let { m ->
            flushParagraph()
            val indent = (m.groupValues[1].length / 2).coerceAtMost(3)
            blocks += MarkdownBlock.BulletItem(m.groupValues[2].trim(), indent)
            continue
        }

        orderedRegex.matchEntire(line)?.let { m ->
            flushParagraph()
            val indent = (m.groupValues[1].length / 2).coerceAtMost(3)
            blocks += MarkdownBlock.OrderedItem(m.groupValues[2], m.groupValues[3].trim(), indent)
            continue
        }

        paragraphBuffer += line
    }

    if (inCodeBlock && codeBuffer.isNotEmpty()) {
        blocks += MarkdownBlock.CodeBlock(codeBuffer.joinToString("\n"), codeLang)
    }
    flushParagraph()
    flushQuote()
    return blocks
}

internal fun buildMarkdownInlineAnnotatedString(
    text: String,
    accentColor: Color,
    codeBackgroundColor: Color,
    codeTextColor: Color
): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    return buildAnnotatedString {
        var i = 0
        val n = text.length
        while (i < n) {
            // 转义字符 \* \_ \`
            if (text[i] == '\\' && i + 1 < n && text[i + 1] in charArrayOf('*', '_', '`', '~', '[', ']', '\\')) {
                append(text[i + 1])
                i += 2
                continue
            }

            // 行内代码 `code`
            if (text[i] == '`') {
                val close = text.indexOf('`', i + 1)
                if (close > i + 1) {
                    val code = text.substring(i + 1, close)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackgroundColor,
                            color = codeTextColor
                        )
                    )
                    append(code)
                    pop()
                    i = close + 1
                    continue
                }
            }

            // 链接 [label](url)
            if (text[i] == '[') {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket > i + 1 && closeBracket + 1 < n && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket + 2) {
                        val label = text.substring(i + 1, closeBracket)
                        pushStyle(
                            SpanStyle(
                                color = accentColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        append(
                            buildMarkdownInlineAnnotatedString(
                                label,
                                accentColor,
                                codeBackgroundColor,
                                codeTextColor
                            )
                        )
                        pop()
                        i = closeParen + 1
                        continue
                    }
                }
            }

            // 粗斜体 ***text*** 或 ___text___
            if ((text.startsWith("***", i) || text.startsWith("___", i)) && i + 3 < n) {
                val marker = text.substring(i, i + 3)
                val close = text.indexOf(marker, i + 3)
                if (close > i + 3) {
                    val inner = text.substring(i + 3, close)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                    append(
                        buildMarkdownInlineAnnotatedString(
                            inner,
                            accentColor,
                            codeBackgroundColor,
                            codeTextColor
                        )
                    )
                    pop()
                    i = close + 3
                    continue
                }
            }

            // 加粗 **text** 或 __text__
            if ((text.startsWith("**", i) || text.startsWith("__", i)) && i + 2 < n) {
                val marker = text.substring(i, i + 2)
                val close = text.indexOf(marker, i + 2)
                if (close > i + 2) {
                    val inner = text.substring(i + 2, close)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(
                        buildMarkdownInlineAnnotatedString(
                            inner,
                            accentColor,
                            codeBackgroundColor,
                            codeTextColor
                        )
                    )
                    pop()
                    i = close + 2
                    continue
                }
            }

            // 删除线 ~~text~~
            if (text.startsWith("~~", i) && i + 2 < n) {
                val close = text.indexOf("~~", i + 2)
                if (close > i + 2) {
                    val inner = text.substring(i + 2, close)
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(
                        buildMarkdownInlineAnnotatedString(
                            inner,
                            accentColor,
                            codeBackgroundColor,
                            codeTextColor
                        )
                    )
                    pop()
                    i = close + 2
                    continue
                }
            }

            // 斜体 *text* 或 _text_（避免把英文蛇形变量名 a_b 误判为斜体）
            if ((text[i] == '*' || text[i] == '_') && i + 1 < n && text[i + 1] != text[i] && !text[i + 1].isWhitespace()) {
                val marker = text[i]
                val canOpenUnderscore = marker != '_' || i == 0 || !text[i - 1].isLetterOrDigit()
                if (canOpenUnderscore) {
                    var close = text.indexOf(marker, i + 1)
                    while (close != -1) {
                        val validClose = !text[close - 1].isWhitespace() &&
                            (marker != '_' || close + 1 >= n || !text[close + 1].isLetterOrDigit()) &&
                            (close + 1 >= n || text[close + 1] != marker)
                        if (validClose) break
                        close = text.indexOf(marker, close + 1)
                    }
                    if (close > i + 1) {
                        val inner = text.substring(i + 1, close)
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(
                            buildMarkdownInlineAnnotatedString(
                                inner,
                                accentColor,
                                codeBackgroundColor,
                                codeTextColor
                            )
                        )
                        pop()
                        i = close + 1
                        continue
                    }
                }
            }

            append(text[i])
            i++
        }
    }
}

/** 去除常见 Markdown 标记，适合单行列表/卡片摘要预览 */
fun stripMarkdownPlainPreview(raw: String): String {
    if (raw.isBlank()) return ""
    return raw
        .replace(Regex("""^#{1,6}\s+""", RegexOption.MULTILINE), "")
        .replace(Regex("""^\s*>\s?""", RegexOption.MULTILINE), "")
        .replace(Regex("""\*\*\*|___|\*\*|__|~~|`"""), "")
        .replace(Regex("""\[([^]]+)]\([^)]+\)"""), "$1")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

/**
 * 轻量级 Compose 原生 Markdown 渲染器：
 * 支持标题、无序/有序列表、引用块、分割线、代码块，以及行内粗体/斜体/代码/链接/删除线。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    secondaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val codeBg = accentColor.copy(alpha = 0.11f)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val headingStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        3 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        else -> MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = buildMarkdownInlineAnnotatedString(block.text, accentColor, codeBg, color),
                        style = headingStyle,
                        color = color,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildMarkdownInlineAnnotatedString(block.text, accentColor, codeBg, color),
                        style = style,
                        color = color
                    )
                }

                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indentLevel * 12).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = style.copy(fontWeight = FontWeight.Bold),
                            color = accentColor,
                            modifier = Modifier.padding(start = 2.dp, end = 7.dp)
                        )
                        Text(
                            text = buildMarkdownInlineAnnotatedString(block.text, accentColor, codeBg, color),
                            style = style,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.OrderedItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indentLevel * 12).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${block.number}.",
                            style = style.copy(fontWeight = FontWeight.SemiBold),
                            color = accentColor,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = buildMarkdownInlineAnnotatedString(block.text, accentColor, codeBg, color),
                            style = style,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.BlockQuote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .background(accentColor.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(accentColor.copy(alpha = 0.72f), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = buildMarkdownInlineAnnotatedString(block.text, accentColor, codeBg, secondaryColor),
                            style = style,
                            color = secondaryColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = color.copy(alpha = 0.07f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = color,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = secondaryColor.copy(alpha = 0.22f)
                    )
                }
            }
        }
    }
}
