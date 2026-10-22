package com.novelai.assistant.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints

/** 页内片段：某段落的可见部分 */
data class ReadingAnchor(val paragraph: Int = 0, val character: Int = 0, val scroll: Int = 0)

data class PageFragment(val paraIndex: Int, val text: String, val startOffset: Int = 0)

data class ReaderPage(val fragments: List<PageFragment>)

/**
 * 段落级分页器：逐段测量文本布局，按行高累积填充页面；
 * 超页长段落按行拆分。保证页内文本与测量布局一致（所见即所分）。
 */
object Paginator {
    fun pageForAnchor(pages: List<ReaderPage>, anchor: ReadingAnchor): Int =
        pages.indexOfLast { page -> page.fragments.firstOrNull()?.let {
            it.paraIndex < anchor.paragraph || (it.paraIndex == anchor.paragraph && it.startOffset <= anchor.character)
        } == true }.coerceAtLeast(0)


    fun paginate(
        paragraphs: List<String>,
        textMeasurer: TextMeasurer,
        style: TextStyle,
        widthPx: Int,
        heightPx: Int,
        paragraphSpacingPx: Int
    ): List<ReaderPage> {
        if (widthPx <= 0 || heightPx <= 0) return listOf(ReaderPage(emptyList()))

        val pages = mutableListOf<ReaderPage>()
        var current = mutableListOf<PageFragment>()
        var usedHeight = 0

        fun flush() {
            if (current.isNotEmpty()) {
                pages.add(ReaderPage(current.toList()))
                current = mutableListOf()
            }
            usedHeight = 0
        }

        val constraints = Constraints(minWidth = widthPx, maxWidth = widthPx)

        paragraphs.forEachIndexed { pi, para ->
            if (para.isBlank()) return@forEachIndexed
            if (ReaderIllustration.parse(para) != null) {
                flush()
                pages.add(ReaderPage(listOf(PageFragment(pi, para))))
                return@forEachIndexed
            }
            val layout: TextLayoutResult = textMeasurer.measure(
                text = AnnotatedString(para),
                style = style,
                constraints = constraints,
                maxLines = Int.MAX_VALUE
            )
            val lineCount = layout.lineCount
            if (lineCount == 0) return@forEachIndexed

            var startLine = 0
            while (startLine < lineCount) {
                val isNewParagraph = (startLine == 0)
                val spacing = if (isNewParagraph && current.isNotEmpty()) paragraphSpacingPx else 0
                val firstLineH = (layout.getLineBottom(startLine) - layout.getLineTop(startLine)).toFloat()

                // 如果当前页已有内容，且放入新段距+第一行后超出页面高度，则先换页
                if (current.isNotEmpty() && (usedHeight + spacing + firstLineH > heightPx)) {
                    flush()
                }

                val effectiveSpacing = if (startLine == 0 && current.isNotEmpty()) paragraphSpacingPx else 0
                val availableForLines = heightPx - usedHeight - effectiveSpacing

                var fitLines = 0
                var accHeight = 0f
                for (line in startLine until lineCount) {
                    val lineH = (layout.getLineBottom(line) - layout.getLineTop(line)).toFloat()
                    if (accHeight + lineH > availableForLines) {
                        if (fitLines > 0) {
                            break
                        } else {
                            // 页面为空但单行超出 heightPx 时强制放入 1 行防死循环
                            accHeight += lineH
                            fitLines = 1
                            break
                        }
                    }
                    accHeight += lineH
                    fitLines++
                }

                if (fitLines == 0) {
                    if (current.isNotEmpty()) {
                        flush()
                        continue
                    } else {
                        fitLines = 1
                        accHeight = firstLineH
                    }
                }

                val endLine = (startLine + fitLines).coerceAtMost(lineCount)
                val startOffset = layout.getLineStart(startLine).coerceIn(0, para.length)
                val endOffset = layout.getLineEnd(endLine - 1, visibleEnd = true).coerceIn(startOffset, para.length)
                val text = if (endOffset > startOffset) para.substring(startOffset, endOffset).trimEnd() else ""

                usedHeight += effectiveSpacing + accHeight.toInt()
                if (text.isNotBlank()) {
                    current.add(PageFragment(pi, text, startOffset))
                }

                startLine = endLine

                if (usedHeight >= heightPx) {
                    flush()
                }
            }
        }
        flush()
        return pages.ifEmpty { listOf(ReaderPage(emptyList())) }
    }

    /**
     * Preserve server paragraph rows. SF's `tsukkomi.row` index counts blank
     * rows too; filtering blanks would shift every later badge onto the wrong
     * paragraph. Consumers skip blank paragraphs while rendering/speaking.
     */
    fun splitParagraphs(content: String): List<String> =
        content.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
}
