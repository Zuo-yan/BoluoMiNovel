package com.novelai.assistant

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.novelai.assistant.ui.components.MarkdownBlock
import com.novelai.assistant.ui.components.buildMarkdownInlineAnnotatedString
import com.novelai.assistant.ui.components.parseMarkdownBlocks
import com.novelai.assistant.ui.components.stripMarkdownPlainPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `parseMarkdownBlocks recognizes headings lists blockquotes code and dividers`() {
        val sample = """
            ### 核心方向分析
            第一段普通说明文字。

            1. **情绪张力**：陆尘燃放烟火
            2. **外部危机**：暴雨围城
            - 子要点一
            - 子要点二

            > 引用一条关键伏笔提示

            ```kotlin
            val x = 1
            ```
            ---
        """.trimIndent()

        val blocks = parseMarkdownBlocks(sample)
        assertEquals(9, blocks.size)
        assertEquals(MarkdownBlock.Heading(3, "核心方向分析"), blocks[0])
        assertEquals(MarkdownBlock.Paragraph("第一段普通说明文字。"), blocks[1])
        assertEquals(MarkdownBlock.OrderedItem("1", "**情绪张力**：陆尘燃放烟火", 0), blocks[2])
        assertEquals(MarkdownBlock.OrderedItem("2", "**外部危机**：暴雨围城", 0), blocks[3])
        assertEquals(MarkdownBlock.BulletItem("子要点一", 0), blocks[4])
        assertEquals(MarkdownBlock.BulletItem("子要点二", 0), blocks[5])
        assertEquals(MarkdownBlock.BlockQuote("引用一条关键伏笔提示"), blocks[6])
        assertEquals(MarkdownBlock.CodeBlock("val x = 1", "kotlin"), blocks[7])
        assertEquals(MarkdownBlock.Divider, blocks[8])
    }

    @Test
    fun `parseMarkdownBlocks separates divider cleanly`() {
        val blocks = parseMarkdownBlocks("段落一\n---\n段落二")
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph("段落一"),
                MarkdownBlock.Divider,
                MarkdownBlock.Paragraph("段落二")
            ),
            blocks
        )
    }

    @Test
    fun `inline parser styles bold italic code strike and links without raw markers`() {
        val input = "**情绪张力**：_低语_ 和 `银月之心` ~~旧线~~ [查看](https://example.com)"
        val annotated = buildMarkdownInlineAnnotatedString(
            text = input,
            accentColor = Color.Blue,
            codeBackgroundColor = Color.LightGray,
            codeTextColor = Color.Black
        )

        assertEquals("情绪张力：低语 和 银月之心 旧线 查看", annotated.text)
        assertTrue(annotated.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(annotated.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(annotated.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough })
        assertTrue(annotated.spanStyles.any { it.item.textDecoration == TextDecoration.Underline })
    }

    @Test
    fun `inline parser leaves unclosed streaming bold marker harmless`() {
        val annotated = buildMarkdownInlineAnnotatedString(
            text = "正在构思：**未闭合加粗",
            accentColor = Color.Blue,
            codeBackgroundColor = Color.LightGray,
            codeTextColor = Color.Black
        )
        assertEquals("正在构思：**未闭合加粗", annotated.text)
    }

    @Test
    fun `stripMarkdownPlainPreview cleans heading and emphasis markers`() {
        val preview = stripMarkdownPlainPreview("### **主线总纲**\n> 第一卷 `银月` [设定](url)")
        assertEquals("主线总纲 第一卷 银月 设定", preview)
    }
}
