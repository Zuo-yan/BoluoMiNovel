package com.novelai.assistant

import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.BookSourceType
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterOriginType
import com.novelai.assistant.data.repository.BookRepository
import com.novelai.assistant.domain.AiSkills
import com.novelai.assistant.domain.ContinuationLengthMode
import com.novelai.assistant.domain.PromptBuilder
import com.novelai.assistant.domain.WorkshopStyleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuationOptimizationTest {

    private fun book() = BookEntity(
        id = "book-1",
        title = "深空余烬",
        author = "林昼",
        coverUri = null,
        sourcePath = "local/test.txt",
        sourceType = BookSourceType.LOCAL_TXT,
        totalChapters = 10
    )

    private fun ch(
        id: String,
        index: Int,
        title: String = "第${index + 1}章",
        content: String = "正文内容-$id",
        origin: ChapterOriginType = ChapterOriginType.ORIGINAL,
        parentId: String? = null,
        createdAt: Long = index.toLong()
    ) = ChapterEntity(
        id = id,
        bookId = "book-1",
        chapterIndex = index,
        title = title,
        content = content,
        originType = origin,
        parentChapterId = parentId,
        createdAt = createdAt
    )

    @Test
    fun workshopSingleFusedOriginalMimicModeRespectsCraftToggle() {
        val prev = listOf(ch("c1", 0, "第一章", "雨夜巷口，铁门生锈发响。"))

        assertEquals(listOf(WorkshopStyleMode.ORIGINAL_MIMIC), WorkshopStyleMode.entries)
        assertEquals(WorkshopStyleMode.ORIGINAL_MIMIC, WorkshopStyleMode.fromId("serial_climax"))
        assertEquals(WorkshopStyleMode.ORIGINAL_MIMIC, WorkshopStyleMode.fromId(null))

        val mimicPrompt = PromptBuilder.continuationMessages(
            book = book(),
            previousChapters = prev,
            instruction = "继续调查旧仓库",
            useWorkshopCraft = true,
            styleMode = WorkshopStyleMode.ORIGINAL_MIMIC
        ).first().content

        assertTrue(mimicPrompt.contains(WorkshopStyleMode.ORIGINAL_MIMIC.promptRules))
        assertTrue(mimicPrompt.contains(AiSkills.SEPIA_NARRATIVE_DISCOURSE_RULES))
        assertTrue(mimicPrompt.contains(AiSkills.ANTI_AI_CLICHE_RULES))
        assertFalse(mimicPrompt.contains("网文爽点"))
        assertFalse(mimicPrompt.contains("反派绝非弱智"))
        assertFalse(mimicPrompt.contains("顶级白金网文作家"))

        val noCraftPrompt = PromptBuilder.continuationMessages(
            book = book(),
            previousChapters = prev,
            instruction = "平实推进",
            useWorkshopCraft = false,
            styleMode = WorkshopStyleMode.ORIGINAL_MIMIC
        ).first().content

        assertFalse(noCraftPrompt.contains(WorkshopStyleMode.ORIGINAL_MIMIC.promptRules))
        assertFalse(noCraftPrompt.contains(AiSkills.SEPIA_NARRATIVE_DISCOURSE_RULES))
        assertFalse(noCraftPrompt.contains(AiSkills.ANTI_AI_CLICHE_RULES))
        assertFalse(noCraftPrompt.contains("【去 AI 味强制清洗（铁律）】"))
    }

    @Test
    fun antiAiClicheRulesContainRequiredConcreteBansAndWorkshopOmitsHardcodedLength() {
        val rules = AiSkills.ANTI_AI_CLICHE_RULES
        val narrativeRules = AiSkills.SEPIA_NARRATIVE_DISCOURSE_RULES
        assertTrue(rules.contains("在这个充满挑战的时代"))
        assertTrue(rules.contains("气势恢宏、震耳欲聋、摄人心魄"))
        assertTrue(rules.contains("命运的齿轮悄然转动"))
        assertTrue(rules.contains("瞳孔微缩"))
        assertTrue(rules.contains("他之所以这么做是因为"))
        assertTrue(rules.contains("Leave Slack"))
        assertTrue(rules.contains("进行讨论"))

        assertTrue(narrativeRules.contains("禁止直白点题与说教"))
        assertTrue(narrativeRules.contains("因果松绑"))
        assertTrue(narrativeRules.contains("全生理表演"))
        assertTrue(narrativeRules.contains("Rule of Three"))

        assertTrue(AiSkills.NOVEL_WORKSHOP.systemPrompt.contains(rules))
        assertTrue(AiSkills.NOVEL_WORKSHOP.systemPrompt.contains(narrativeRules))
        assertFalse(AiSkills.NOVEL_WORKSHOP.systemPrompt.contains("3000-5000字"))
    }

    @Test
    fun skillsRegistryKeepsSingleFusedNovelSkillAndCommentReply() {
        assertEquals(listOf(AiSkills.NOVEL_WORKSHOP, AiSkills.COMMENT_SMART_REPLY), AiSkills.all)
        assertEquals(AiSkills.NOVEL_WORKSHOP, AiSkills.byId("novel_workshop"))
        assertEquals(AiSkills.COMMENT_SMART_REPLY, AiSkills.byId("comment_smart_reply"))
        assertEquals(null, AiSkills.byId("novel_serial_epic"))
        assertEquals(null, AiSkills.byId("novel_sepia_refine"))
    }

    @Test
    fun continuationLengthModeThresholdsAndPromptGuidanceAreInjected() {
        assertEquals(3072, ContinuationLengthMode.SHORT_SCENE.minMaxTokens)
        assertEquals(5120, ContinuationLengthMode.STANDARD_CHAPTER.minMaxTokens)
        assertEquals(8192, ContinuationLengthMode.LONG_DEEP.minMaxTokens)

        val prev = listOf(ch("c1", 0, "第一章", "钟楼停摆，风雪灌入长廊。"))
        for (mode in ContinuationLengthMode.entries) {
            val contSystem = PromptBuilder.continuationMessages(
                book = book(),
                previousChapters = prev,
                instruction = "",
                lengthMode = mode
            ).first().content
            assertTrue(contSystem.contains(mode.promptGuidance))
        }
    }

    @Test
    fun continuationMessagesInjectRetrievedChaptersAndLongChapterHeadTailAnchors() {
        val headMarker = "HEAD_ANCHOR_" + "甲".repeat(608)
        val middleMarker = "MIDDLE_SKIPPED_SEGMENT_" + "乙".repeat(2500)
        val tailMarker = "TAIL_ANCHOR_" + "丙".repeat(400)
        val longContent = headMarker + middleMarker + tailMarker
        assertTrue(longContent.length > 3200)

        val lastChapter = ch("c9", 8, "第九章 长街夜巡", longContent)
        val recalled = ch("c2", 1, "第二章 青铜钥匙", "旧巷修表匠曾把刻有七芒星的青铜钥匙交给沈默。")

        val contMessages = PromptBuilder.continuationMessages(
            book = book(),
            previousChapters = listOf(lastChapter),
            instruction = "追踪钥匙下落",
            retrievedChapters = listOf(recalled)
        )
        assertTrue(contMessages[0].content.contains("关联前文记忆召回"))
        assertTrue(contMessages[0].content.contains("第二章 青铜钥匙"))
        assertTrue(contMessages[0].content.contains("刻有七芒星的青铜钥匙"))

        val contUser = contMessages[1].content
        assertTrue(contUser.contains("HEAD_ANCHOR_"))
        assertTrue(contUser.contains("……（中略）……"))
        assertTrue(contUser.contains("TAIL_ANCHOR_"))
        assertFalse(contUser.contains("MIDDLE_SKIPPED_SEGMENT_"))
    }

    @Test
    fun continueGeneratingMessagesPreserveDraftTailAndForbidRepetition() {
        val parent = ch("c3", 2, "第三章 渡口风声", "渡船靠岸时，雾气已经漫过石阶。")
        val droppedPrefix = "EARLY_DRAFT_PREFIX_" + "旧".repeat(3600)
        val keptTail = "DRAFT_TAIL_ANCHOR_林默按住腰间短刀，听见楼上木梯传来半声轻响，"
        val currentDraft = droppedPrefix + keptTail

        val messages = PromptBuilder.continueGeneratingMessages(
            book = book(),
            parentChapter = parent,
            previousChapters = listOf(parent),
            currentDraft = currentDraft,
            instruction = "揭晓来者身份",
            useWorkshopCraft = true,
            styleMode = WorkshopStyleMode.ORIGINAL_MIMIC,
            lengthMode = ContinuationLengthMode.LONG_DEEP,
            retrievedChapters = listOf(ch("c1", 0, "第一章 雾夜", "十年前失踪的船老大左手缺了半截小指。"))
        )

        val system = messages[0].content
        val user = messages[1].content
        assertTrue(system.contains("无缝续接核心规则"))
        assertTrue(system.contains("绝对禁止重复"))
        assertTrue(system.contains("绝对禁止从头重写本章"))
        assertTrue(system.contains(WorkshopStyleMode.ORIGINAL_MIMIC.promptRules))
        assertTrue(system.contains(ContinuationLengthMode.LONG_DEEP.promptGuidance))
        assertTrue(system.contains("第一章 雾夜"))

        assertTrue(user.contains(keptTail))
        assertFalse(user.contains("EARLY_DRAFT_PREFIX_"))
        assertTrue(user.contains("第三章 渡口风声"))
        assertTrue(user.contains("揭晓来者身份"))
    }

    @Test
    fun resolveChapterLineagePreservesOriginalChaptersForAiContinuationWithOrWithoutParent() {
        val o1 = ch("o1", 0, origin = ChapterOriginType.ORIGINAL)
        val o2 = ch("o2", 1, origin = ChapterOriginType.ORIGINAL)
        val b1 = ch("b1", 2, origin = ChapterOriginType.AI_CONTINUATION, parentId = "o1")
        val aiNullParent = ch("ai-null", 3, origin = ChapterOriginType.AI_CONTINUATION, parentId = null)
        val aiWithParent = ch("ai-linked", 4, origin = ChapterOriginType.AI_CONTINUATION, parentId = "o2")
        val aiChained = ch("ai-chained", 5, origin = ChapterOriginType.AI_CONTINUATION, parentId = "ai-linked")
        val o3 = ch("o3", 6, origin = ChapterOriginType.ORIGINAL)
        val b2 = ch("b2", 7, origin = ChapterOriginType.AI_CONTINUATION, parentId = "b1")

        val all = listOf(o1, o2, b1, aiNullParent, aiWithParent, aiChained, o3, b2)

        assertEquals(
            listOf("o1", "o2", "ai-null"),
            BookRepository.resolveChapterLineage(all, "ai-null").map { it.id }
        )
        assertEquals(
            listOf("o1", "o2", "ai-linked"),
            BookRepository.resolveChapterLineage(all, "ai-linked").map { it.id }
        )
        assertEquals(
            listOf("o1", "o2", "ai-linked", "ai-chained"),
            BookRepository.resolveChapterLineage(all, "ai-chained").map { it.id }
        )
        assertEquals(
            listOf("o1", "b1", "b2"),
            BookRepository.resolveChapterLineage(all, "b2").map { it.id }
        )
        assertEquals(
            listOf("o1", "o2", "o3"),
            BookRepository.resolveChapterLineage(all, "o3").map { it.id }
        )
    }

    @Test
    fun normalizeChaptersOrderPlacesGeneratedAndSiblingChaptersDirectlyAfterParent() {
        val o1 = ch("o1", 0, origin = ChapterOriginType.ORIGINAL, createdAt = 100)
        val o2 = ch("o2", 1, origin = ChapterOriginType.ORIGINAL, createdAt = 200)
        val o3 = ch("o3", 2, origin = ChapterOriginType.ORIGINAL, createdAt = 300)
        // Simulate legacy tail-appended chapters at indices 99, 100, 101 referencing o1
        val a1 = ch("a1", 99, origin = ChapterOriginType.AI_CONTINUATION, parentId = "o1", createdAt = 400)
        val a2 = ch("a2", 100, origin = ChapterOriginType.AI_CONTINUATION, parentId = "o1", createdAt = 500)
        val a1Child = ch("a1-child", 101, origin = ChapterOriginType.AI_CONTINUATION, parentId = "a1", createdAt = 600)

        val normalized = BookRepository.normalizeChaptersOrder(listOf(o1, o2, o3, a1, a2, a1Child))
        assertEquals(listOf("o1", "a1", "a1-child", "a2", "o2", "o3"), normalized.map { it.id })
        assertEquals(listOf(0, 1, 2, 3, 4, 5), normalized.map { it.chapterIndex })
    }
}
