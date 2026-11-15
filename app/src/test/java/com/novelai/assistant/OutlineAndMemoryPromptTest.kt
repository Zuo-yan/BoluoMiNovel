package com.novelai.assistant

import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.BookOutlineEntity
import com.novelai.assistant.data.db.BookSourceType
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterOriginType
import com.novelai.assistant.data.db.ChapterSummaryEntity
import com.novelai.assistant.data.rag.RagVectorCodec
import com.novelai.assistant.domain.OutlineBeat
import com.novelai.assistant.domain.PromptBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlineAndMemoryPromptTest {

    private fun sampleBook() = BookEntity(
        id = "b1",
        title = "苍穹旧事",
        author = "顾明",
        coverUri = null,
        sourcePath = "local/book.txt",
        sourceType = BookSourceType.LOCAL_TXT,
        totalChapters = 3
    )

    private fun sampleChapter(id: String, index: Int, title: String, content: String) = ChapterEntity(
        id = id,
        bookId = "b1",
        chapterIndex = index,
        title = title,
        content = content,
        originType = ChapterOriginType.ORIGINAL
    )

    @Test
    fun `continuationMessages injects outline when enabled and omits when disabled`() {
        val book = sampleBook()
        val ch1 = sampleChapter("c1", 0, "第一章", "长街落雨，剑客收鞘而立。")
        val outline = BookOutlineEntity(
            bookId = book.id,
            masterOutline = "主角终将揭开皇城禁卫失踪之谜。",
            upcomingPlan = "下一章在茶楼遭遇假扮使臣的刺客。",
            useInContinuation = true
        )

        val withOutline = PromptBuilder.continuationMessages(
            book = book,
            previousChapters = listOf(ch1),
            instruction = "",
            outline = outline
        ).first { it.role == "system" }.content

        assertTrue(withOutline.contains("作者长期大纲与剧情规划"))
        assertTrue(withOutline.contains("主角终将揭开皇城禁卫失踪之谜"))
        assertTrue(withOutline.contains("下一章在茶楼遭遇假扮使臣的刺客"))

        val withoutOutline = PromptBuilder.continuationMessages(
            book = book,
            previousChapters = listOf(ch1),
            instruction = "",
            outline = outline.copy(useInContinuation = false)
        ).first { it.role == "system" }.content

        assertFalse(withoutOutline.contains("作者长期大纲与剧情规划"))
    }

    @Test
    fun `continuationMessages prefers valid cached chapter summary over raw excerpt`() {
        val book = sampleBook()
        val ch1 = sampleChapter("c1", 0, "第一章 雨夜试探", "原始长文前段内容" + "字".repeat(300))
        val validSummary = ChapterSummaryEntity(
            chapterId = ch1.id,
            bookId = book.id,
            contentHash = RagVectorCodec.chapterContentHash(ch1.title, ch1.content),
            plotSummary = "雨夜茶楼暗流涌动，主角识破伪装信使。",
            characterStates = "主角暂居客栈，反派尚未察觉身份败露"
        )

        val systemPrompt = PromptBuilder.continuationMessages(
            book = book,
            previousChapters = listOf(ch1),
            instruction = "继续",
            chapterSummaries = mapOf(ch1.id to validSummary)
        ).first { it.role == "system" }.content

        assertTrue(systemPrompt.contains("雨夜茶楼暗流涌动，主角识破伪装信使"))
        assertTrue(systemPrompt.contains("主角暂居客栈，反派尚未察觉身份败露"))

        val staleSummary = validSummary.copy(contentHash = "outdated-hash")
        val fallbackPrompt = PromptBuilder.continuationMessages(
            book = book,
            previousChapters = listOf(ch1),
            instruction = "继续",
            chapterSummaries = mapOf(ch1.id to staleSummary)
        ).first { it.role == "system" }.content

        assertFalse(fallbackPrompt.contains("雨夜茶楼暗流涌动，主角识破伪装信使"))
    }

    @Test
    fun `plotDiscussionMessages instructs AI to ask questions and offer clickable directions`() {
        val book = sampleBook()
        val ch1 = sampleChapter("c1", 0, "第一章", "雨夜伏笔。")
        val msgs = PromptBuilder.plotDiscussionMessages(
            book = book,
            anchorChapter = ch1,
            previousChapters = listOf(ch1),
            currentOutline = null,
            userMessage = "帮我想想后面怎么走",
            requestSynthesizeOutline = false
        )
        val system = msgs.first { it.role == "system" }.content
        assertTrue(system.contains("[[DIRECTIONS]]"))
        assertTrue(system.contains("主动向作者提出 1~2 个聚焦的关键问题"))
    }

    @Test
    fun `continuationMessages preserves early milestones ledger three point sampling and companion spoiler guard`() {
        val book = sampleBook()
        // 构造 15 章：前 3 章落在 12 章近景窗口之外，其中第 1 章有已归档记忆卡
        val chapters = (0 until 15).map { idx ->
            val longBody = buildString {
                append("第${idx + 1}章开头密室线索。")
                append("占位过渡文本".repeat(60))
                append("第${idx + 1}章中段关键抉择与人物名牌。")
                append("占位过渡文本".repeat(60))
                append("第${idx + 1}章章末突围悬念。")
            }
            sampleChapter("c$idx", idx, "第${idx + 1}章", longBody)
        }
        val earlySummary = ChapterSummaryEntity(
            chapterId = "c0",
            bookId = book.id,
            contentHash = RagVectorCodec.chapterContentHash(chapters[0].title, chapters[0].content),
            plotSummary = "第一章归档：林墨在钟楼地下取得青铜密钥。",
            characterStates = "林墨：持有青铜密钥，怀疑师兄。"
        )
        val uncachedLongIndex = 5 // 落在近 12 章内，且无记忆卡 -> 应走三段抽样保留中段关键句
        val msgs = PromptBuilder.continuationMessages(
            book = book,
            previousChapters = chapters,
            instruction = "",
            chapterSummaries = mapOf("c0" to earlySummary)
        )
        val system = msgs.first { it.role == "system" }.content
        val user = msgs.last { it.role == "user" }.content

        assertTrue(system.contains("更早剧情里程碑"))
        assertTrue(system.contains("林墨在钟楼地下取得青铜密钥"))
        assertTrue(system.contains("人物状态与伏笔追踪台账"))
        assertTrue(system.contains("林墨：持有青铜密钥"))
        assertTrue(system.contains("第${uncachedLongIndex + 1}章中段关键抉择与人物名牌"))
        assertTrue(user.contains("前情衔接参考：上一轮章节《第14章》"))

        // 伴读防剧透开关验证
        val outline = BookOutlineEntity(
            bookId = book.id,
            masterOutline = "全书终极幕后黑手是大长老。",
            upcomingPlan = "下一幕揭穿伪装。"
        )
        val spoilerSafeSys = PromptBuilder.companionMessages(
            book = book,
            currentChapter = chapters[2],
            history = emptyList(),
            question = "林墨拿到什么道具？",
            recentChapters = chapters.take(3),
            chapterSummaries = mapOf("c0" to earlySummary),
            outline = outline,
            spoilerFree = true
        ).first { it.role == "system" }.content
        assertTrue(spoilerSafeSys.contains("林墨在钟楼地下取得青铜密钥"))
        assertFalse(spoilerSafeSys.contains("全书终极幕后黑手是大长老"))

        val fullMemorySys = PromptBuilder.companionMessages(
            book = book,
            currentChapter = chapters[2],
            history = emptyList(),
            question = "全局伏笔是什么？",
            recentChapters = chapters.take(3),
            chapterSummaries = mapOf("c0" to earlySummary),
            outline = outline,
            spoilerFree = false
        ).first { it.role == "system" }.content
        assertTrue(fullMemorySys.contains("全书终极幕后黑手是大长老"))
    }

    @Test
    fun `continuationMessages and continueGeneratingMessages inject active beat and target chapter title`() {
        val book = sampleBook()
        val ch1 = sampleChapter("c1", 0, "第一章", "长街落雨，剑客收鞘而立。")
        val beat = OutlineBeat(
            index = 0,
            displayNumber = 1,
            chapterTitle = "雨夜截杀",
            summary = "茶楼识破假使臣，借暴雨脱身",
            hook = "巷口遗落半枚玉佩"
        )

        val contSys = PromptBuilder.continuationMessages(
            book = book,
            previousChapters = listOf(ch1),
            instruction = beat.toContinuationInstruction(),
            targetChapterTitle = "雨夜截杀",
            activeOutlineBeat = beat
        ).first { it.role == "system" }.content

        assertTrue(contSys.contains("【本章指定目标与细纲节拍】"))
        assertTrue(contSys.contains("目标章节名：《雨夜截杀》"))
        assertTrue(contSys.contains("茶楼识破假使臣，借暴雨脱身"))
        assertTrue(contSys.contains("巷口遗落半枚玉佩"))
        assertTrue(contSys.contains("严禁 AI 自己在正文开头重复输出章节标题"))

        val resumeSys = PromptBuilder.continueGeneratingMessages(
            book = book,
            parentChapter = ch1,
            previousChapters = listOf(ch1),
            currentDraft = "茶楼木梯咯吱作响，林墨按住了剑柄。",
            instruction = "",
            targetChapterTitle = "雨夜截杀",
            activeOutlineBeat = beat
        ).first { it.role == "system" }.content

        assertTrue(resumeSys.contains("【本章指定目标与细纲节拍】"))
        assertTrue(resumeSys.contains("目标章节名：《雨夜截杀》"))
        assertTrue(resumeSys.contains("严禁 AI 自己在正文开头重复输出章节标题"))
    }

    @Test
    fun `plotDiscussionMessages supports 6 to 8 chapter synthesis and seamless plan extension`() {
        val book = sampleBook()
        val ch1 = sampleChapter("c1", 0, "第一章", "雨夜伏笔。")
        val existingOutline = BookOutlineEntity(
            bookId = book.id,
            masterOutline = "主角调查皇城失踪案。",
            upcomingPlan = "- [✓] 1. 《雨夜茶楼》 | 核心剧情：识破假使臣 | 章末钩子：半枚玉佩\n- [ ] 2. 《废弃染坊》 | 核心剧情：追踪徽记线索 | 章末钩子：二楼亮灯"
        )

        val synthesizeSys = PromptBuilder.plotDiscussionMessages(
            book = book,
            anchorChapter = ch1,
            previousChapters = listOf(ch1),
            currentOutline = existingOutline,
            userMessage = "",
            requestSynthesizeOutline = true
        ).first { it.role == "system" }.content

        assertTrue(synthesizeSys.contains("6~8 章"))
        assertTrue(synthesizeSys.contains("- [ ] 1. 《章节标题》 | 核心剧情：... | 章末钩子：..."))
        assertTrue(synthesizeSys.contains("[[UPCOMING_PLAN]]"))

        val extendMsgs = PromptBuilder.plotDiscussionMessages(
            book = book,
            anchorChapter = ch1,
            previousChapters = listOf(ch1),
            currentOutline = existingOutline,
            userMessage = "",
            requestSynthesizeOutline = false,
            extendExistingPlan = true,
            targetExtendCount = 6
        )
        val extendSys = extendMsgs.first { it.role == "system" }.content
        val extendUser = extendMsgs.last { it.role == "user" }.content

        assertTrue(extendSys.contains("无缝续编后续章节规划"))
        assertTrue(extendSys.contains("废弃染坊"))
        assertTrue(extendSys.contains("6 章"))
        assertTrue(extendSys.contains("[[UPCOMING_PLAN]]"))
        assertTrue(extendUser.contains("6 章"))
    }

    @Test
    fun `suggestChapterTitlesMessages formats title prompt with beat draft and anti-cliche rules`() {
        val book = sampleBook()
        val parent = sampleChapter("c1", 0, "第一章 灰烬", "夜风卷过长街。")
        val beat = OutlineBeat(
            index = 0,
            displayNumber = 1,
            chapterTitle = "旧约余烬",
            summary = "在废弃染坊发现当年信物",
            hook = "石门后有人点灯"
        )
        val msgs = PromptBuilder.suggestChapterTitlesMessages(
            book = book,
            draftText = "染坊梁柱早已腐朽，林墨挑开蛛网，只见供桌上摆着一盏刚熄灭的油灯。",
            parentChapter = parent,
            outline = BookOutlineEntity(bookId = book.id, masterOutline = "揭开旧案真相"),
            activeBeat = beat,
            instruction = "突出悬疑现场感"
        )
        assertEquals(2, msgs.size)
        val sys = msgs[0].content
        val user = msgs[1].content
        assertTrue(sys.contains("[[TITLES]]"))
        assertTrue(sys.contains("[[/TITLES]]"))
        assertTrue(sys.contains("命运的转折"))
        assertTrue(user.contains("旧约余烬"))
        assertTrue(user.contains("供桌上摆着一盏刚熄灭的油灯"))
        assertTrue(user.contains("突出悬疑现场感"))
    }
}
