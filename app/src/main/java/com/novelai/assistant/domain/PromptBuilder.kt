package com.novelai.assistant.domain

import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.BookOutlineEntity
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterSummaryEntity
import com.novelai.assistant.data.network.ChatMessage
import com.novelai.assistant.data.rag.RagVectorCodec
import com.novelai.assistant.data.rag.RetrievedSnippet

/**
 * Prompt 工程构建器：
 * - 续写：近期章节脉络大纲 + RAG 关联记忆召回 + Few-Shot 风格样本 + 解耦文风/篇幅档位 + 用户指令
 * - 继续生成（无缝续接草稿）：锚定前文与当前未完草稿末尾，零重复接续
 * - 伴读：携带近期章节上下文与引文
 */
object PromptBuilder {

    private const val STYLE_SAMPLE_CHARS = 1200
    private const val CONTEXT_HEAD_CHARS = 600
    private const val CONTEXT_TAIL_CHARS = 2500
    private const val LONG_CHAPTER_THRESHOLD = 3200
    private const val DRAFT_TAIL_CHARS = 3500
    private const val RECENT_TIMELINE_CHAPTERS = 12
    private const val EARLY_MILESTONE_LIMIT = 18
    private const val CHARACTER_LEDGER_LIMIT = 10

    fun continuationMessages(
        book: BookEntity,
        previousChapters: List<ChapterEntity>,
        instruction: String,
        useWorkshopCraft: Boolean = true,
        styleMode: WorkshopStyleMode = WorkshopStyleMode.ORIGINAL_MIMIC,
        lengthMode: ContinuationLengthMode = ContinuationLengthMode.STANDARD_CHAPTER,
        retrievedChapters: List<ChapterEntity> = emptyList(),
        retrievedSnippets: List<RetrievedSnippet> = emptyList(),
        outline: BookOutlineEntity? = null,
        chapterSummaries: Map<String, ChapterSummaryEntity> = emptyMap(),
        targetChapterTitle: String = "",
        activeOutlineBeat: OutlineBeat? = null
    ): List<ChatMessage> {
        val samples = previousChapters.takeLast(4).joinToString("\n\n") { ch ->
            "【《${book.title}》${ch.title}节选】\n" + ch.content.takeLast(STYLE_SAMPLE_CHARS)
        }
        val timeline = buildRecentTimeline(previousChapters, chapterSummaries)
        val lastChapter = previousChapters.lastOrNull()
        val prev2Chapter = if (previousChapters.size >= 2) previousChapters[previousChapters.size - 2] else null
        val system = buildString {
            appendLine("你是资深小说作者，正在为《${book.title}》${if (book.author.isNotBlank()) "（作者：${book.author}）" else ""}续写下一章正文。")
            appendLine("请严格模仿以下原文片段的行文风格：叙事视角、口吻、用词习惯、句式长短、对话风格与语气词（若原著偏好平实白描，勿强行拔高为繁复文青腔）。")
            appendLine()
            appendLongTermOutlineBlock(outline)
            appendTargetBeatBlock(targetChapterTitle, activeOutlineBeat)
            appendStoryMilestonesAndCharacterLedger(previousChapters, chapterSummaries)
            appendTimelineBlock(timeline, "=== 近期章节剧情脉络（最近 $RECENT_TIMELINE_CHAPTERS 章）===")
            appendRetrievedMemoryBlock(retrievedSnippets, retrievedChapters)
            appendLine("=== 原文风格样本（最近章节结尾）===")
            appendLine(samples.ifBlank { "（无样本，请使用自然流畅、细节扎实的中文小说文风）" })
            appendLine("=== 样本结束 ===")
            appendLine()
            appendLine(lengthMode.promptGuidance)
            appendLine()
            appendLine("续写要求：")
            appendLine("1. 直接衔接上文继续写下一章正文，不要复述或改写已有内容；")
            appendLine("2. 输出纯正文，不要任何解释、前言、标题标注或元评论；")
            appendLine("3. 保持人物性格、称谓习惯与既定世界观设定前后一致，严禁吃书；")
            val hasActiveOutline = outline?.useInContinuation == true &&
                (outline.masterOutline.isNotBlank() || outline.upcomingPlan.isNotBlank())
            val hasTargetBeat = targetChapterTitle.isNotBlank() || activeOutlineBeat != null
            if (instruction.isNotBlank()) {
                appendLine("4. 作者本章核心剧情指令（最高优先级）：$instruction")
                if (hasActiveOutline || hasTargetBeat) {
                    appendLine("5. 在落实上述本章指令的同时，整体剧情走向须与前文「长期大纲与剧情规划」及「本章指定目标与细纲节拍」保持一致。")
                }
            } else if (hasTargetBeat) {
                appendLine("4. 请严格紧扣上文「本章指定目标与细纲节拍」展开本章剧情，并在章末留下自然衔接的悬念或转折。")
            } else if (hasActiveOutline) {
                appendLine("4. 请严格围绕上文「长期大纲与剧情规划」推进本章剧情，并在章末留下自然衔接的悬念或转折。")
            } else {
                appendLine("4. 情节自然推进，围绕当前核心矛盾展开并留下恰当的章末悬念或转折。")
            }
            appendWorkshopCraftBlock(useWorkshopCraft, styleMode)
        }
        val user = buildString {
            if (prev2Chapter != null && prev2Chapter.content.isNotBlank()) {
                appendLine("【前情衔接参考：上一轮章节《${prev2Chapter.title}》】")
                appendLine(formatCondensedThreePointExcerpt(prev2Chapter.content, headLen = 280, midLen = 260, tailLen = 500))
                appendLine()
            }
            if (lastChapter != null) {
                appendLine("以下是当前最后一章《${lastChapter.title}》的内容，请从其结尾处自然衔接续写下一章：")
                appendLine("【${lastChapter.title}】")
                append(formatChapterContextExcerpt(lastChapter.content))
            } else {
                append("请开始创作本书的第一章。")
            }
        }
        return listOf(ChatMessage("system", system), ChatMessage("user", user))
    }

    fun continueGeneratingMessages(
        book: BookEntity,
        parentChapter: ChapterEntity?,
        previousChapters: List<ChapterEntity>,
        currentDraft: String,
        instruction: String,
        useWorkshopCraft: Boolean = true,
        styleMode: WorkshopStyleMode = WorkshopStyleMode.ORIGINAL_MIMIC,
        lengthMode: ContinuationLengthMode = ContinuationLengthMode.STANDARD_CHAPTER,
        retrievedChapters: List<ChapterEntity> = emptyList(),
        retrievedSnippets: List<RetrievedSnippet> = emptyList(),
        outline: BookOutlineEntity? = null,
        chapterSummaries: Map<String, ChapterSummaryEntity> = emptyMap(),
        targetChapterTitle: String = "",
        activeOutlineBeat: OutlineBeat? = null
    ): List<ChatMessage> {
        val contextChapters = if (previousChapters.isNotEmpty()) {
            previousChapters
        } else {
            listOfNotNull(parentChapter)
        }
        val anchorChapter = parentChapter ?: previousChapters.lastOrNull()
        val samples = contextChapters.takeLast(3).joinToString("\n\n") { ch ->
            "【《${book.title}》${ch.title}节选】\n" + ch.content.takeLast(STYLE_SAMPLE_CHARS)
        }
        val timeline = buildRecentTimeline(contextChapters, chapterSummaries)

        val system = buildString {
            appendLine("你是资深小说作者，正在为《${book.title}》${if (book.author.isNotBlank()) "（作者：${book.author}）" else ""}无缝续写当前尚未写完的章节草稿。")
            appendLine("请严格保持与原著及当前草稿完全一致的叙事视角、行文口吻、句式节奏与对话风格（保留原著平实度与语气词习惯）。")
            appendLine()
            appendLongTermOutlineBlock(outline)
            appendTargetBeatBlock(targetChapterTitle, activeOutlineBeat)
            appendStoryMilestonesAndCharacterLedger(contextChapters, chapterSummaries)
            appendTimelineBlock(timeline, "=== 近期章节剧情脉络（最近 $RECENT_TIMELINE_CHAPTERS 章）===")
            appendRetrievedMemoryBlock(retrievedSnippets, retrievedChapters)
            if (samples.isNotBlank()) {
                appendLine("=== 原文风格样本 ===")
                appendLine(samples)
                appendLine("=== 样本结束 ===")
                appendLine()
            }
            appendLine(lengthMode.promptGuidance)
            appendLine()
            appendLine("【无缝续接核心规则（铁律）】")
            appendLine("1. 必须从用户给出的「当前已生成草稿末尾」的最后一个字/最后一句话之后直接往下续写（若末尾停在半句话或对话中途，请先自然补完该句再继续推进）；")
            appendLine("2. 绝对禁止重复、复述或改写草稿中已经存在的任何段落或句子；")
            appendLine("3. 绝对禁止从头重写本章，禁止输出章节标题、前言、过渡说明或元评论，只输出紧接草稿末尾的新增纯正文；")
            if (instruction.isNotBlank()) {
                appendLine("4. 作者补充推进要求：$instruction")
            } else {
                appendLine("4. 顺着当前草稿的场景与冲突自然往下推进，写出实质性剧情进展与细节。")
            }
            appendWorkshopCraftBlock(useWorkshopCraft, styleMode)
        }

        val user = buildString {
            if (anchorChapter != null) {
                appendLine("【上一章《${anchorChapter.title}》结尾锚点】")
                appendLine(anchorChapter.content.takeLast(800))
                appendLine()
            }
            if (instruction.isNotBlank()) {
                appendLine("【本章写作方向 / 补充指令】$instruction")
                appendLine()
            }
            appendLine("【当前已生成草稿末尾（请紧接最后一句无缝继续往下写，严禁重复已有文字）】")
            append(currentDraft.takeLast(DRAFT_TAIL_CHARS))
        }
        return listOf(ChatMessage("system", system), ChatMessage("user", user))
    }

    private fun StringBuilder.appendTimelineBlock(
        timeline: String,
        header: String = "=== 近期章节剧情脉络 ==="
    ) {
        if (timeline.isBlank()) return
        appendLine(header)
        appendLine(timeline)
        appendLine("=== 脉络结束 ===")
        appendLine()
    }

    private fun StringBuilder.appendWorkshopCraftBlock(
        useWorkshopCraft: Boolean,
        styleMode: WorkshopStyleMode
    ) {
        if (!useWorkshopCraft) return
        appendLine()
        appendLine(styleMode.promptRules)
        appendLine()
        appendLine(AiSkills.SEPIA_NARRATIVE_DISCOURSE_RULES)
        appendLine()
        appendLine(AiSkills.ANTI_AI_CLICHE_RULES)
    }

    private fun formatChapterContextExcerpt(content: String): String {
        return if (content.length > LONG_CHAPTER_THRESHOLD) {
            content.take(CONTEXT_HEAD_CHARS) + "\n……（中略）……\n" + content.takeLast(CONTEXT_TAIL_CHARS)
        } else {
            content.takeLast(CONTEXT_TAIL_CHARS)
        }
    }

    private fun StringBuilder.appendRetrievedMemoryBlock(
        retrievedSnippets: List<RetrievedSnippet>,
        retrievedChapters: List<ChapterEntity>
    ) {
        if (retrievedSnippets.isNotEmpty()) {
            appendLine("=== 关联前文记忆召回（防吃书参考）===")
            for (item in retrievedSnippets) {
                appendLine("——《${item.chapter.title}》：${item.snippet}")
            }
            appendLine("=== 召回结束 ===")
            appendLine()
        } else if (retrievedChapters.isNotEmpty()) {
            appendLine("=== 关联前文记忆召回（防吃书参考）===")
            for (ch in retrievedChapters) {
                appendLine("——《${ch.title}》：${ch.content.take(700)}")
            }
            appendLine("=== 召回结束 ===")
            appendLine()
        }
    }

    private fun StringBuilder.appendLongTermOutlineBlock(outline: BookOutlineEntity?) {
        if (outline == null || !outline.useInContinuation) return
        val hasMaster = outline.masterOutline.isNotBlank()
        val hasPlan = outline.upcomingPlan.isNotBlank()
        if (!hasMaster && !hasPlan) return
        appendLine("=== 作者长期大纲与剧情规划（最高方向指引） ===")
        if (hasMaster) {
            appendLine("【全书主线总纲 / 核心设定】")
            appendLine(outline.masterOutline.trim())
        }
        if (hasPlan) {
            if (hasMaster) appendLine()
            appendLine("【后续分卷 / 近期章节细纲】")
            appendLine(outline.upcomingPlan.trim())
        }
        appendLine("=== 长期大纲结束 ===")
        appendLine()
    }

    private fun StringBuilder.appendTargetBeatBlock(
        targetChapterTitle: String,
        activeOutlineBeat: OutlineBeat?
    ) {
        val resolvedTitle = targetChapterTitle.trim().ifBlank {
            activeOutlineBeat?.chapterTitle?.trim().orEmpty()
        }
        if (resolvedTitle.isBlank() && activeOutlineBeat == null) return
        appendLine("=== 【本章指定目标与细纲节拍】 ===")
        if (resolvedTitle.isNotBlank()) {
            appendLine("目标章节名：《$resolvedTitle》")
        }
        if (activeOutlineBeat != null) {
            if (activeOutlineBeat.summary.isNotBlank()) {
                appendLine("本章核心剧情节拍：${activeOutlineBeat.summary.trim()}")
            }
            if (activeOutlineBeat.hook.isNotBlank()) {
                appendLine("预期章末钩子：${activeOutlineBeat.hook.trim()}")
            }
        }
        appendLine("执行要求：请让本章正文紧扣上述目标章节名与细纲节拍推进，落实核心剧情事件与钩子；但仍严禁 AI 自己在正文开头重复输出章节标题、前言或任何说明。")
        appendLine("=== 本章目标结束 ===")
        appendLine()
    }

    private fun validSummaryFor(
        chapter: ChapterEntity,
        chapterSummaries: Map<String, ChapterSummaryEntity>
    ): ChapterSummaryEntity? = chapterSummaries[chapter.id]?.takeIf {
        it.contentHash == RagVectorCodec.chapterContentHash(chapter.title, chapter.content) &&
            it.plotSummary.isNotBlank()
    }

    /** 三段式浓缩采样（开头 + 中段关键节点 + 结尾），避免未生成记忆卡的章节完全丢失中段剧情细节 */
    private fun formatCondensedThreePointExcerpt(
        rawContent: String,
        headLen: Int,
        midLen: Int,
        tailLen: Int
    ): String {
        val clean = rawContent.replace(Regex("\\s+"), " ").trim()
        val fullBudget = headLen + midLen + tailLen
        if (clean.length <= fullBudget + 80) return clean
        val midStart = ((clean.length - midLen) / 2).coerceIn(headLen, (clean.length - tailLen - midLen).coerceAtLeast(headLen))
        val head = clean.take(headLen)
        val mid = clean.substring(midStart, (midStart + midLen).coerceAtMost(clean.length - tailLen))
        val tail = clean.takeLast(tailLen)
        return "$head … $mid … $tail"
    }

    /** 早期章节里程碑与人物伏笔台账：让 12 章之外已归档的记忆卡与人物状态持续发挥防吃书作用 */
    private fun StringBuilder.appendStoryMilestonesAndCharacterLedger(
        chapters: List<ChapterEntity>,
        chapterSummaries: Map<String, ChapterSummaryEntity>
    ) {
        if (chapters.isEmpty() || chapterSummaries.isEmpty()) return

        val olderChapters = if (chapters.size > RECENT_TIMELINE_CHAPTERS) {
            chapters.dropLast(RECENT_TIMELINE_CHAPTERS)
        } else {
            emptyList()
        }
        val olderCached = olderChapters.mapNotNull { ch ->
            validSummaryFor(ch, chapterSummaries)?.let { ch to it }
        }
        if (olderCached.isNotEmpty()) {
            val sampledOlder = if (olderCached.size <= EARLY_MILESTONE_LIMIT) {
                olderCached
            } else {
                val keepIndices = linkedSetOf<Int>()
                val step = (olderCached.size - 1).toDouble() / (EARLY_MILESTONE_LIMIT - 1)
                for (i in 0 until EARLY_MILESTONE_LIMIT) {
                    keepIndices += (i * step).toInt().coerceIn(0, olderCached.lastIndex)
                }
                keepIndices.map { olderCached[it] }
            }
            appendLine("=== 更早剧情里程碑（已归档长期记忆） ===")
            for ((ch, summary) in sampledOlder) {
                appendLine("• 《${ch.title}》：${summary.plotSummary.trim()}")
            }
            appendLine("=== 早期里程碑结束 ===")
            appendLine()
        }

        val stateEntries = chapters.mapNotNull { ch ->
            validSummaryFor(ch, chapterSummaries)
                ?.takeIf { it.characterStates.isNotBlank() }
                ?.let { ch to it.characterStates.trim() }
        }.takeLast(CHARACTER_LEDGER_LIMIT)
        if (stateEntries.isNotEmpty()) {
            appendLine("=== 人物状态与伏笔追踪台账（防吃书清单） ===")
            for ((ch, stateText) in stateEntries) {
                appendLine("• 《${ch.title}》：$stateText")
            }
            appendLine("=== 追踪台账结束 ===")
            appendLine()
        }
    }

    private fun buildRecentTimeline(
        chapters: List<ChapterEntity>,
        chapterSummaries: Map<String, ChapterSummaryEntity> = emptyMap()
    ): String {
        if (chapters.isEmpty()) return ""
        val window = chapters.takeLast(RECENT_TIMELINE_CHAPTERS)
        return window.mapIndexed { idx, ch ->
            val cachedSummary = validSummaryFor(ch, chapterSummaries)
            if (cachedSummary != null) {
                val statePart = if (cachedSummary.characterStates.isNotBlank()) {
                    " [状态：${cachedSummary.characterStates.trim()}]"
                } else {
                    ""
                }
                "• 《${ch.title}》：${cachedSummary.plotSummary.trim()}$statePart"
            } else {
                val isVeryRecent = idx >= window.size - 4
                val cue = if (isVeryRecent) {
                    formatCondensedThreePointExcerpt(ch.content, headLen = 160, midLen = 160, tailLen = 180)
                } else {
                    formatCondensedThreePointExcerpt(ch.content, headLen = 100, midLen = 90, tailLen = 110)
                }
                "• 《${ch.title}》：$cue"
            }
        }.joinToString("\n")
    }

    /**
     * AI 剧情走向互动讨论 Prompt：
     * 引导 AI 主动提问、提供可点击的多分支方向选项，并支持在收敛后生成可一键采纳的双层大纲草案。
     */
    fun plotDiscussionMessages(
        book: BookEntity,
        anchorChapter: ChapterEntity?,
        previousChapters: List<ChapterEntity>,
        currentOutline: BookOutlineEntity?,
        chapterSummaries: Map<String, ChapterSummaryEntity> = emptyMap(),
        retrievedSnippets: List<RetrievedSnippet> = emptyList(),
        history: List<ChatMessage> = emptyList(),
        userMessage: String,
        requestSynthesizeOutline: Boolean = false,
        extendExistingPlan: Boolean = false,
        targetExtendCount: Int = 6
    ): List<ChatMessage> {
        val timeline = buildRecentTimeline(previousChapters, chapterSummaries)
        val existingBeats = OutlineBeatCodec.parseBeats(currentOutline?.upcomingPlan.orEmpty())
        val system = buildString {
            appendLine("你是《${book.title}》${if (book.author.isNotBlank()) "（作者：${book.author}）" else ""}的资深剧情策划主编，正在与作者共同梳理后续剧情走向并制定长期大纲。")
            appendLine()
            if (currentOutline != null && (currentOutline.masterOutline.isNotBlank() || currentOutline.upcomingPlan.isNotBlank())) {
                appendLine("=== 当前已保存的长期大纲 ===")
                if (currentOutline.masterOutline.isNotBlank()) {
                    appendLine("【全书主线总纲】${currentOutline.masterOutline.trim()}")
                }
                if (currentOutline.upcomingPlan.isNotBlank()) {
                    appendLine("【近期章节细纲】")
                    appendLine(currentOutline.upcomingPlan.trim())
                }
                appendLine("=== 已有大纲结束 ===")
                appendLine()
            } else {
                appendLine("（本书目前尚未定稿长期大纲，本次讨论需要帮作者逐步明确主线方向与接下来几章的推进节奏。）")
                appendLine()
            }
            appendStoryMilestonesAndCharacterLedger(previousChapters, chapterSummaries)
            appendTimelineBlock(timeline, "=== 近期章节剧情脉络（最近 $RECENT_TIMELINE_CHAPTERS 章）===")
            appendRetrievedMemoryBlock(retrievedSnippets, emptyList())
            if (anchorChapter != null) {
                appendLine("=== 当前讨论锚点章节《${anchorChapter.title}》关键上下文 ===")
                appendLine(formatChapterContextExcerpt(anchorChapter.content))
                appendLine("=== 锚点结束 ===")
                appendLine()
            }
            appendLine("【合作讨论规则】")
            when {
                extendExistingPlan -> {
                    val tailBeats = existingBeats.takeLast(4)
                    appendLine("1. 作者希望在【已有近期章节细纲】之后无缝续编后续章节规划；")
                    if (tailBeats.isNotEmpty()) {
                        appendLine("2. 请紧扣已有细纲末尾几步的因果与悬念衔接往下推演（已有末尾节拍参考）：")
                        tailBeats.forEach { beat ->
                            appendLine("   - 第${beat.displayNumber}步《${beat.chapterTitle}》：${beat.summary}${if (beat.hook.isNotBlank()) "（钩子：${beat.hook}）" else ""}")
                        }
                    } else {
                        appendLine("2. 请紧扣已有主线总纲与当前剧情最新锚点继续往下推演；")
                    }
                    appendLine("3. 请在回复中简述续编思路，并在末尾**必须附上** `[[UPCOMING_PLAN]]...[[/UPCOMING_PLAN]]` 标签块，仅输出新增的接下来 **$targetExtendCount 章** 可持续推进的新细纲（不要重复已有章节），严格按每行一条以下格式输出：")
                    appendLine("   - [ ] 1. 《章节标题》 | 核心剧情：... | 章末钩子：...")
                    appendLine("4. 若主线总纲需要微调或保持一致，可同步附上 `[[MASTER_OUTLINE]]...[[/MASTER_OUTLINE]]`；最后可附 `[[DIRECTIONS]]` 给出 2 个微调方向供作者确认。")
                }
                requestSynthesizeOutline -> {
                    appendLine("1. 作者希望把目前讨论的共识整理成可长期保存的正式双层大纲；")
                    appendLine("2. 请结合前文与上述全部讨论，保留作者否决或修正的偏好，在正文中简述整理要点，并在回复末尾**必须同时附上**以下两个标签块供一键写入大纲：")
                    appendLine("   - `[[MASTER_OUTLINE]]`：全书主线总纲（核心主线、主要人物弧光与关键伏笔，结构清晰，200~450 字）`[[/MASTER_OUTLINE]]`")
                    appendLine("   - `[[UPCOMING_PLAN]]`：至少 **6~8 章** 可持续推进的分章细纲（彻底覆盖中短期主线节奏，不要只写两三章），严格按每行一条以下格式输出（每章必须有具体章节标题、核心剧情与章末钩子）：")
                    appendLine("     - [ ] 1. 《章节标题》 | 核心剧情：... | 章末钩子：...")
                    appendLine("     - [ ] 2. 《章节标题》 | 核心剧情：... | 章末钩子：...")
                    appendLine("     ……（依次排满 6~8 章）`[[/UPCOMING_PLAN]]`")
                    appendLine("3. 最后可附带 `[[DIRECTIONS]]` 给出 2 个微调方向供作者进一步确认；")
                }
                else -> {
                    appendLine("1. 结合当前矛盾、伏笔与人物动机做简练剖析（避免空泛套话与单线工具化推演，适度保留信息后置、配角独立动机或微妙灰度），明确指出接下来的核心戏剧抉择；")
                    appendLine("2. 你要主动向作者提出 1~2 个聚焦的关键问题（如人物抉择、危机升级方式、感情线节奏或反转时机），引导作者决策；")
                    appendLine("3. 每次回复末尾**必须**附上 `[[DIRECTIONS]]` 区块，用 `- ` 给出 2~4 个彼此差异鲜明、可直接落地的新走向或回答选项（每条 15~45 字，便于作者一键点选，同时作者若都不满意也会自己输入想法）；")
                    appendLine("4. 如果当前方向已经足够清晰或作者明确要求定稿，可一并附上 `[[MASTER_OUTLINE]]...[[/MASTER_OUTLINE]]` 与 `[[UPCOMING_PLAN]]...[[/UPCOMING_PLAN]]`（分章细纲按 `- [ ] 1. 《章节标题》 | 核心剧情：... | 章末钩子：...` 逐行编写 6~8 章）供一键采纳；仍在探讨发散时不必过早输出草案；")
                }
            }
            appendLine()
            appendLine("标签格式示例（严格按标记名书写，不要包在 Markdown 代码块内）：")
            appendLine("[[DIRECTIONS]]")
            appendLine("- 选项一的简明描述")
            appendLine("- 选项二的简明描述")
            appendLine("[[/DIRECTIONS]]")
        }

        val messages = mutableListOf(ChatMessage("system", system))
        messages.addAll(history.takeLast(12))
        val finalUser = when {
            extendExistingPlan -> userMessage.ifBlank {
                "请紧接现有细纲末尾的剧情脉络，无缝续编接下来 $targetExtendCount 章的具体章节标题、核心剧情与章末钩子，并输出 [[UPCOMING_PLAN]] 标签块。"
            }
            requestSynthesizeOutline -> userMessage.ifBlank {
                "请根据我们刚才的讨论以及小说前文，整理并输出更新后的「全书主线总纲」和「6~8 章分章细纲」草案。"
            }
            else -> userMessage
        }
        messages.add(ChatMessage("user", finalUser))
        return messages
    }

    /**
     * AI 章节标题推荐 Prompt：结合草稿正文、前章锚点、细纲节拍与作者指令，生成 4 个贴合文风的章节标题。
     */
    fun suggestChapterTitlesMessages(
        book: BookEntity,
        draftText: String,
        parentChapter: ChapterEntity? = null,
        outline: BookOutlineEntity? = null,
        activeBeat: OutlineBeat? = null,
        instruction: String = ""
    ): List<ChatMessage> {
        val system = buildString {
            appendLine("你是《${book.title}》${if (book.author.isNotBlank()) "（作者：${book.author}）" else ""}的资深网文与轻小说策划编辑，负责为当前新章拟定 4 个精准抓人、贴合原著文风的章节标题。")
            appendLine()
            appendLine("拟题要求：")
            appendLine("1. 紧扣本章实际场景、人物核心动作、关键意象或章末转折，保持与前文章节命名一致的节奏与气质；")
            appendLine("2. 严禁使用「命运的转折」「暗流涌动」「风云突变」「新的开始」「风暴前夜」等空洞 AI 俗套标题；")
            appendLine("3. 每个标题控制在 2~12 个汉字以内，不要带「第X章」、序号、书名号《》或任何解释；")
            appendLine("4. 严格按以下标签格式返回 4 个候选标题，不要输出多余说明：")
            appendLine("[[TITLES]]")
            appendLine("- 标题1")
            appendLine("- 标题2")
            appendLine("- 标题3")
            appendLine("- 标题4")
            appendLine("[[/TITLES]]")
        }

        val user = buildString {
            if (outline != null && outline.useInContinuation && outline.masterOutline.isNotBlank()) {
                appendLine("【全书主线背景】")
                appendLine(outline.masterOutline.trim().take(600))
                appendLine()
            }
            if (parentChapter != null) {
                appendLine("【上一章参考：《${parentChapter.title}》结尾】")
                appendLine(parentChapter.content.takeLast(500))
                appendLine()
            }
            if (activeBeat != null) {
                appendLine("【当前对应细纲节拍】")
                appendLine("原定标题：《${activeBeat.chapterTitle}》")
                if (activeBeat.summary.isNotBlank()) appendLine("核心剧情：${activeBeat.summary}")
                if (activeBeat.hook.isNotBlank()) appendLine("章末钩子：${activeBeat.hook}")
                appendLine()
            }
            if (instruction.isNotBlank()) {
                appendLine("【作者本章写作指令】")
                appendLine(instruction.trim())
                appendLine()
            }
            if (draftText.isNotBlank()) {
                appendLine("【本章正文内容】")
                val excerpt = if (draftText.length > 2800) {
                    draftText.take(1000) + "\n……（中略）……\n" + draftText.takeLast(1800)
                } else {
                    draftText
                }
                append(excerpt)
            } else {
                append("（当前正文尚未生成，请根据上述细纲节拍、写作指令与上一章脉络直接拟定 4 个候选章节标题。）")
            }
        }
        return listOf(ChatMessage("system", system), ChatMessage("user", user))
    }

    /** 生成单章精炼剧情记忆卡 Prompt，用于长期持久化缓存 */
    fun chapterSummaryMessages(
        book: BookEntity,
        chapter: ChapterEntity
    ): List<ChatMessage> {
        val system = """
            你是小说剧情归档助手。请将《${book.title}》的一章正文提炼为精简的长期记忆卡，供后续 AI 续写和剧情讨论检索参考。
            严格按以下两行格式输出，不要任何多余解释或剧透：
            【剧情摘要】：<80~140字，客观交代本章核心事件、因果与结尾悬念>
            【人物与伏笔状态】：<40~90字，记录重要人物当下状态/位置/态度变化或埋下的关键伏笔>
        """.trimIndent()
        val user = "章节标题：《${chapter.title}》\n正文：\n${formatChapterContextExcerpt(chapter.content)}"
        return listOf(ChatMessage("system", system), ChatMessage("user", user))
    }

    fun companionMessages(
        book: BookEntity,
        currentChapter: ChapterEntity?,
        history: List<ChatMessage>,
        question: String,
        quotedText: String? = null,
        retrievedChapters: List<com.novelai.assistant.data.db.ChapterEntity> = emptyList(),
        spoilerFree: Boolean = true,
        skillPrompt: String = "",
        retrievedSnippets: List<RetrievedSnippet> = emptyList(),
        recentChapters: List<ChapterEntity> = emptyList(),
        chapterSummaries: Map<String, ChapterSummaryEntity> = emptyMap(),
        outline: BookOutlineEntity? = null
    ): List<ChatMessage> {
        val system = buildString {
            appendLine("你是《${book.title}》${if (book.author.isNotBlank()) "（作者：${book.author}）" else ""}的智能伴读助手。")
            if (!spoilerFree) {
                appendLongTermOutlineBlock(outline)
            }
            if (recentChapters.isNotEmpty() && chapterSummaries.isNotEmpty()) {
                val timeline = buildRecentTimeline(recentChapters, chapterSummaries)
                appendStoryMilestonesAndCharacterLedger(recentChapters, chapterSummaries)
                appendTimelineBlock(timeline, "=== 已读前文剧情脉络（供核对人物与前情） ===")
            }
            if (retrievedSnippets.isNotEmpty()) {
                appendLine("以下是按用户问题精准定位到的相关原文片段（供参考作答）：")
                for (item in retrievedSnippets) {
                    appendLine("——《${item.chapter.title}》：${item.snippet}")
                }
            } else if (retrievedChapters.isNotEmpty()) {
                appendLine("以下是按用户问题检索到的相关原文片段（供参考作答）：")
                for (ch in retrievedChapters) {
                    appendLine("——《${ch.title}》：${ch.content.take(600)}")
                }
            }
            if (currentChapter != null) {
                appendLine("用户当前正读到《${currentChapter.title}》，其结尾如下：")
                appendLine(currentChapter.content.takeLast(CONTEXT_TAIL_CHARS))
            }
            appendLine("回答要求：准确、简洁、有依据；引用原文时注明章节名。")
            if (spoilerFree) {
                appendLine("严格模式：用户尚未阅读之后的内容，禁止提及或暗示任何后续章节的情节发展。")
            } else {
                appendLine("全书回忆模式：允许引用全书内容回答，包括用户尚未读到的部分。")
            }
            if (skillPrompt.isNotBlank()) {
                appendLine()
                appendLine("【用户选择的技能，请按其要求作答】")
                appendLine(skillPrompt)
            } else {
                appendLine()
                appendLine(AiSkills.COMMENT_SMART_REPLY.systemPrompt)
            }
        }
        val messages = mutableListOf(ChatMessage("system", system))
        messages.addAll(history.takeLast(10))
        val userContent = if (quotedText.isNullOrBlank()) question
        else "【用户划线选段】\n$quotedText\n\n【用户的问题】\n$question"
        messages.add(ChatMessage("user", userContent))
        return messages
    }

    /** 划线即问的快捷指令 */
    const val QUICK_ANALYZE = "请解析这段文字的深意、伏笔与表达技巧。"
    const val QUICK_EMOTION = "请总结这段文字的情绪基调，并分析人物当下的心理状态。"
    const val QUICK_ROAST = "请用轻松幽默的口吻吐槽这段文字，可以玩梗，但不要恶意。"
}
