package com.novelai.assistant.domain

/**
 * 分章细纲节拍模型：用于大纲解析、进度联动、AI 续写目标注入与章节标题推荐。
 */
data class OutlineBeat(
    val index: Int,
    val displayNumber: Int,
    val chapterTitle: String,
    val summary: String,
    val hook: String = "",
    val isCompleted: Boolean = false
) {
    fun toContinuationInstruction(): String = buildString {
        if (chapterTitle.isNotBlank()) {
            appendLine("本章目标：《${chapterTitle.trim()}》")
        }
        if (summary.isNotBlank()) {
            appendLine("剧情推进：${summary.trim()}")
        }
        if (hook.isNotBlank()) {
            append("章末钩子：${hook.trim()}")
        }
    }.trim()
}

/**
 * 分章细纲与章节标题编解码器：
 * - 兼容标准单行细纲、无复选框列表、Markdown 标题块、旧版纯文本列表；
 * - 支持与已生成章节标题归一化比对自动标记完成状态；
 * - 支持大纲节点切换完成态、续编追加去重、AI 标题推荐标签解析与本地保底拟题。
 */
object OutlineBeatCodec {

    private const val TITLES_START = "[[TITLES]]"
    private const val TITLES_END = "[[/TITLES]]"

    private val checkboxLineRegex = Regex("""^\s*(?:[-*•]\s*)?\[([ xX✓√✔])\]\s*(.+)$""")
    private val markdownHeadingRegex = Regex("""^\s*#{1,6}\s+(.+)$""")
    private val numberedStarterRegex = Regex("""^\s*(?:[-*•]\s*)?(\d+)\s*[.、)）]\s*(.+)$""")
    private val chineseStepStarterRegex = Regex(
        """^\s*(?:[-*•]\s*)?(?:第\s*([0-9零一二两三四五六七八九十百千万]+)\s*[章节回步幕卷节]\s*(?:[.\-—：:]\s*)?|((?:下[一二两三四五六七八九十\d]*章|随后几章|接下来|Chapter\s*\d+))\s*[\-—：:]?\s*)(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val boldPrefixedLineRegex = Regex("""^\s*(?:[-*•]\s*)?\*\*([^*]+)\*\*\s*(.*)$""")
    private val bulletLineRegex = Regex("""^\s*[-*•]\s+(.+)$""")

    private val subPropertySummaryRegex = Regex("""^(?:核心剧情|剧情|内容|概要|简介)\s*[：:]\s*(.+)$""")
    private val subPropertyHookRegex = Regex("""^(?:章末钩子|钩子|悬念|伏笔)\s*[：:]\s*(.+)$""")

    private val quotedTitleRegex = Regex("""^《([^》]+)》(.*)$""")
    private val inlineStepNumberRegex = Regex("""^(\d+)\s*[.、)）]\s*(.+)$""")
    private val inlineChineseStepRegex = Regex(
        """^(?:第\s*([0-9零一二两三四五六七八九十百千万]+)\s*[章节回步幕卷节]\s*(?:[.\-—：:]\s*)?|(?:下[一二两三四五六七八九十\d]*章|随后几章|接下来|Chapter\s*\d+)\s*[\-—：:]?\s*)(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val inlineHookSplitRegex = Regex("""(?:[|｜；;]|^)\s*(?:章末钩子|钩子)\s*[：:]\s*""")

    private val containerHeadingNames = setOf(
        "后续分章规划", "近期章节细纲", "分章细纲", "分章规划", "剧情规划", "近期规划", "章节规划", "大纲", "细纲"
    )
    private val sectionOverviewHeadingRegex = Regex(
        """^(?:\d+\s*章\s*)?(?:后续|近期|后续分章|推进|分章)?(?:推进细纲|分章细纲|分章规划|后续细纲|章节细纲|剧情规划)(?:[（(][^）)]*[）)])?$"""
    )
    private val boldInlineStepSplitRegex = Regex(
        """([^\n])\s*(?:[：:|｜]\s*|\s+)(?=(?:\*{1,2}|_{1,2})\s*(?:第\s*[0-9零一二两三四五六七八九十百千万]+\s*[章节回步幕卷节]|Chapter\s*\d+))""",
        RegexOption.IGNORE_CASE
    )
    private val plainInlineChapterSplitRegex = Regex(
        """([。！？!?；;])\s+(?=第\s*\d{1,5}\s*章\s*[：:《])"""
    )

    private val titleAiPrefixRegex = Regex("""^(?:AI\s*续写\s*[·\-—|/：:]*\s*)""", RegexOption.IGNORE_CASE)
    private val titleChapterPrefixRegex = Regex(
        """^(?:第\s*[0-9零一二两三四五六七八九十百千万]+\s*[章节回步幕卷节]\s*[、.\-—：:]?\s*|Chapter\s*\d+\s*[.\-—：:]?\s*|\d+\s*[.、)）\-—：:]\s*)""",
        RegexOption.IGNORE_CASE
    )

    private data class MutableBeatDraft(
        var displayNumber: Int? = null,
        var rawHeaderOrLine: String = "",
        var isHeadingBlock: Boolean = false,
        var explicitCompleted: Boolean = false,
        var explicitTitle: String = "",
        val summaryParts: MutableList<String> = mutableListOf(),
        val hookParts: MutableList<String> = mutableListOf()
    )

    fun parseBeats(
        upcomingPlan: String,
        existingChapterTitles: Set<String> = emptySet()
    ): List<OutlineBeat> {
        if (upcomingPlan.isBlank()) return emptyList()

        val completedTitleKeys = existingChapterTitles
            .map { normalizeTitleKey(it) }
            .filter { it.isNotBlank() }
            .toSet()

        val lines = expandSingleLineLegacySteps(upcomingPlan)
        val drafts = mutableListOf<MutableBeatDraft>()
        var current: MutableBeatDraft? = null
        var sawBlankAfterCurrent = false

        fun flushCurrent() {
            current?.let { drafts += it }
            current = null
            sawBlankAfterCurrent = false
        }

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                if (current != null) sawBlankAfterCurrent = true
                continue
            }

            // 1. 优先识别带复选框的细纲条目：- [ ] / - [✓] / [x]
            val checkboxMatch = checkboxLineRegex.matchEntire(trimmed)
            if (checkboxMatch != null) {
                flushCurrent()
                val marker = checkboxMatch.groupValues[1].firstOrNull() ?: ' '
                val body = checkboxMatch.groupValues[2].trim()
                val draft = parseSingleBeatLine(
                    rawBody = body,
                    explicitCompleted = marker in setOf('x', 'X', '✓', '√', '✔'),
                    isHeading = false
                )
                current = draft
                sawBlankAfterCurrent = false
                continue
            }

            // 2. Markdown 标题块或全篇总览标题行（如 ### 《6 章推进细纲（第198章 ~ 第203章）》）
            val headingMatch = markdownHeadingRegex.matchEntire(trimmed)
            if (headingMatch != null) {
                val headingBody = headingMatch.groupValues[1].trim()
                if (isSectionWrapperHeader(headingBody)) {
                    flushCurrent()
                    continue
                }
                flushCurrent()
                val draft = parseSingleBeatLine(
                    rawBody = headingBody,
                    explicitCompleted = false,
                    isHeading = true
                )
                current = draft
                sawBlankAfterCurrent = false
                continue
            }

            if (isSectionWrapperHeader(trimmed)) {
                flushCurrent()
                continue
            }

            // 2.5 加粗章节头行：**第198章：标题** 正文...
            val boldPrefixedMatch = boldPrefixedLineRegex.matchEntire(trimmed)
            if (boldPrefixedMatch != null) {
                val boldHead = boldPrefixedMatch.groupValues[1].trim()
                val boldRest = boldPrefixedMatch.groupValues[2].trim()
                val boldKey = stripQuotesAndSymbols(boldHead).trimEnd(':', '：')
                val isPropLabel = boldKey in setOf("核心剧情", "剧情", "内容", "概要", "简介", "章末钩子", "钩子", "悬念", "伏笔")
                if (!isPropLabel && (
                        chineseStepStarterRegex.matches(boldHead) ||
                            numberedStarterRegex.matches(boldHead) ||
                            boldHead.startsWith("《") ||
                            (boldRest.isNotBlank() && boldHead.length <= 32)
                        )
                ) {
                    if (isSectionWrapperHeader(boldHead)) {
                        flushCurrent()
                        if (boldRest.isNotBlank()) {
                            // 继续用 rest 作为后续正文行检查，不把包装标题当章节
                            val subDraft = parseSingleBeatLine(
                                rawBody = boldRest,
                                explicitCompleted = false,
                                isHeading = false
                            )
                            current = subDraft
                            sawBlankAfterCurrent = false
                        }
                        continue
                    }
                    flushCurrent()
                    val stepMatch = chineseStepStarterRegex.matchEntire(boldHead)
                    val numMatch = numberedStarterRegex.matchEntire(boldHead)
                    val presetNum = stepMatch?.groupValues?.get(1)?.let(::parseChineseOrDigitNumber)
                        ?: numMatch?.groupValues?.get(1)?.toIntOrNull()
                    val cleanBoldTitle = cleanTitleText(stripTitlePrefixes(boldHead))
                    val draft = MutableBeatDraft(
                        displayNumber = presetNum,
                        rawHeaderOrLine = boldHead,
                        isHeadingBlock = false,
                        explicitCompleted = false,
                        explicitTitle = cleanBoldTitle
                    )
                    if (boldRest.isNotBlank()) {
                        val pipeSegments = boldRest.split(Regex("""\s*[|｜]\s*"""))
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        for (seg in pipeSegments) {
                            val sumProp = subPropertySummaryRegex.matchEntire(seg)
                            val hookProp = subPropertyHookRegex.matchEntire(seg)
                            when {
                                sumProp != null -> draft.summaryParts += sumProp.groupValues[1].trim()
                                hookProp != null -> draft.hookParts += hookProp.groupValues[1].trim()
                                draft.summaryParts.isEmpty() -> draft.summaryParts += seg
                                else -> draft.hookParts += seg
                            }
                        }
                    }
                    current = draft
                    sawBlankAfterCurrent = false
                    continue
                }
            }

            // 3. 当前已有正在组装的节拍时，优先识别子属性行（- 剧情：/ - 钩子：/ 剧情：/ 钩子：）
            val active = current
            val bulletMatch = bulletLineRegex.matchEntire(trimmed)
            val candidatePropText = bulletMatch?.groupValues?.get(1)?.trim() ?: trimmed
            if (active != null) {
                val sumProp = subPropertySummaryRegex.matchEntire(candidatePropText)
                if (sumProp != null) {
                    active.summaryParts += sumProp.groupValues[1].trim()
                    sawBlankAfterCurrent = false
                    continue
                }
                val hookProp = subPropertyHookRegex.matchEntire(candidatePropText)
                if (hookProp != null) {
                    active.hookParts += hookProp.groupValues[1].trim()
                    sawBlankAfterCurrent = false
                    continue
                }
            }

            // 4. 带明确序号或“第X章/步/下一章”的条目
            val numMatch = numberedStarterRegex.matchEntire(trimmed)
            if (numMatch != null) {
                flushCurrent()
                val num = numMatch.groupValues[1].toIntOrNull()
                val body = numMatch.groupValues[2].trim()
                current = parseSingleBeatLine(
                    rawBody = body,
                    explicitCompleted = false,
                    isHeading = false,
                    presetNumber = num
                )
                sawBlankAfterCurrent = false
                continue
            }

            val stepMatch = chineseStepStarterRegex.matchEntire(trimmed)
            if (stepMatch != null) {
                flushCurrent()
                val parsedNum = parseChineseOrDigitNumber(stepMatch.groupValues[1])
                val body = stepMatch.groupValues[3].trim()
                current = parseSingleBeatLine(
                    rawBody = body,
                    explicitCompleted = false,
                    isHeading = false,
                    presetNumber = parsedNum
                )
                sawBlankAfterCurrent = false
                continue
            }

            // 5. 普通项目符号行（• / - / *）
            if (bulletMatch != null) {
                val bulletBody = bulletMatch.groupValues[1].trim()
                val activeNeedsBody = active != null &&
                    (active.isHeadingBlock || active.summaryParts.isEmpty()) &&
                    !bulletBody.startsWith("《") &&
                    !bulletBody.contains("|") &&
                    !bulletBody.contains("｜")
                if (activeNeedsBody && active != null) {
                    active.summaryParts += bulletBody
                    sawBlankAfterCurrent = false
                } else {
                    flushCurrent()
                    current = parseSingleBeatLine(
                        rawBody = bulletBody,
                        explicitCompleted = false,
                        isHeading = false
                    )
                    sawBlankAfterCurrent = false
                }
                continue
            }

            // 6. 纯文本行：若上一条是 Markdown 标题块或尚未填充剧情的标题行，归入其正文；否则按旧版纯文本条目标注
            if (active != null && !sawBlankAfterCurrent && (active.isHeadingBlock || active.summaryParts.isEmpty())) {
                active.summaryParts += trimmed
            } else {
                flushCurrent()
                current = parseSingleBeatLine(
                    rawBody = trimmed,
                    explicitCompleted = false,
                    isHeading = false
                )
                sawBlankAfterCurrent = false
            }
        }
        flushCurrent()

        return drafts.mapIndexedNotNull { idx, draft ->
            finalizeDraft(draft, idx, completedTitleKeys)
        }.mapIndexed { idx, beat ->
            beat.copy(index = idx)
        }
    }

    fun formatBeats(beats: List<OutlineBeat>): String {
        if (beats.isEmpty()) return ""
        return beats.mapIndexed { index, beat ->
            val check = if (beat.isCompleted) "✓" else " "
            val cleanTitle = cleanTitleText(beat.chapterTitle).ifBlank { "第${index + 1}章" }
            val cleanSummary = beat.summary.trim().ifBlank { cleanTitle }
            val hookSuffix = if (beat.hook.isNotBlank()) " | 章末钩子：${beat.hook.trim()}" else ""
            "- [$check] ${index + 1}. 《$cleanTitle》 | 核心剧情：$cleanSummary$hookSuffix"
        }.joinToString("\n")
    }

    fun markBeatCompleted(
        upcomingPlan: String,
        beatIndex: Int,
        completed: Boolean = true,
        existingChapterTitles: Set<String> = emptySet()
    ): String {
        val beats = parseBeats(upcomingPlan, existingChapterTitles)
        if (beats.isEmpty() || beatIndex !in beats.indices) return upcomingPlan
        val updated = beats.map { beat ->
            if (beat.index == beatIndex) beat.copy(isCompleted = completed) else beat
        }
        return formatBeats(updated)
    }

    fun appendBeats(existingPlan: String, additionalPlan: String): String {
        val existingBeats = parseBeats(existingPlan)
        val incomingBeats = parseBeats(additionalPlan)
        if (existingBeats.isEmpty()) return formatBeats(incomingBeats)
        if (incomingBeats.isEmpty()) return formatBeats(existingBeats)

        val merged = mutableListOf<OutlineBeat>()
        val keyToIndex = mutableMapOf<String, Int>()

        for (beat in existingBeats) {
            val key = normalizeTitleKey(beat.chapterTitle)
            if (key.isNotBlank() && key in keyToIndex) continue
            if (key.isNotBlank()) keyToIndex[key] = merged.size
            merged += beat
        }

        for (beat in incomingBeats) {
            val key = normalizeTitleKey(beat.chapterTitle)
            val existingIdx = if (key.isNotBlank()) keyToIndex[key] else null
            if (existingIdx != null) {
                val old = merged[existingIdx]
                merged[existingIdx] = old.copy(
                    summary = old.summary.ifBlank { beat.summary },
                    hook = old.hook.ifBlank { beat.hook },
                    isCompleted = old.isCompleted
                )
            } else {
                if (key.isNotBlank()) keyToIndex[key] = merged.size
                merged += beat
            }
        }

        val renumbered = merged.mapIndexed { idx, beat ->
            beat.copy(index = idx, displayNumber = idx + 1)
        }
        return formatBeats(renumbered)
    }

    fun parseTitleSuggestions(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()
        val blockText = extractTitlesBlock(rawText) ?: rawText
        val fromBlock = extractTitlesBlock(rawText) != null

        return blockText.lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                    !line.startsWith("[[") &&
                    !line.startsWith("```")
            }
            .map { line ->
                line.removePrefix("- ")
                    .removePrefix("* ")
                    .removePrefix("• ")
                    .replace(Regex("""^\d+\s*[.、)）]\s*"""), "")
                    .replace(Regex("""^\*\*(.+)\*\*$"""), "$1")
                    .trim()
                    .let(::stripQuotesAndSymbols)
                    .trim()
            }
            .filter { cleaned ->
                cleaned.isNotBlank() && (fromBlock || (cleaned.length in 2..28 && !cleaned.endsWith("：") && !cleaned.endsWith(":")))
            }
            .distinct()
            .take(6)
            .toList()
    }

    fun deriveFallbackTitles(
        draftText: String,
        activeBeat: OutlineBeat?,
        parentTitle: String?
    ): List<String> {
        val candidates = mutableListOf<String>()
        val seenKeys = mutableSetOf<String>()

        fun addCandidate(raw: String) {
            val cleaned = cleanTitleText(raw)
            if (cleaned.length !in 2..20) return
            val key = normalizeTitleKey(cleaned)
            if (key.isBlank() || key in seenKeys) return
            seenKeys += key
            candidates += cleaned
        }

        // 1. 优先把当前绑定的细纲节拍标题放在首位
        if (activeBeat != null && activeBeat.chapterTitle.isNotBlank()) {
            addCandidate(activeBeat.chapterTitle)
        }

        // 2. 从草稿正文首段、中段转折与结尾抽取短小精炼的候选标题
        if (draftText.isNotBlank()) {
            val clauses = draftText
                .replace(Regex("""^[#>*\s]+""", RegexOption.MULTILINE), "")
                .split(Regex("""[。！？!?；;\n]+"""))
                .map { it.trim() }
                .filter { it.length >= 4 }

            val narrativeClauses = clauses.filterNot {
                it.startsWith("“") || it.startsWith("\"") || it.startsWith("「")
            }.ifEmpty { clauses }

            val sampled = buildList {
                narrativeClauses.firstOrNull()?.let(::add)
                if (narrativeClauses.size >= 3) add(narrativeClauses[narrativeClauses.size / 2])
                if (narrativeClauses.size >= 2) add(narrativeClauses.last())
            }
            for (sentence in sampled) {
                deriveShortTitleFromClause(sentence)?.let(::addCandidate)
            }
        }

        // 3. 从细纲钩子与摘要补充候选
        if (activeBeat != null) {
            if (activeBeat.hook.isNotBlank()) {
                deriveShortTitleFromClause(activeBeat.hook)?.let(::addCandidate)
            }
            if (activeBeat.summary.isNotBlank()) {
                deriveShortTitleFromClause(activeBeat.summary)?.let(::addCandidate)
            }
        }

        // 4. 从父章节名推导兜底候选
        val cleanParent = cleanTitleText(stripTitlePrefixes(parentTitle.orEmpty()))
        if (cleanParent.isNotBlank() && candidates.size < 4) {
            val base = cleanParent.take(8)
            addCandidate("$base·余波")
            if (candidates.size < 4) {
                addCandidate("${base}之后")
            }
        }

        return candidates.take(6)
    }

    private fun expandSingleLineLegacySteps(upcomingPlan: String): List<String> {
        val preSplit = upcomingPlan
            .replace(boldInlineStepSplitRegex, "$1\n")
            .replace(plainInlineChapterSplitRegex, "$1\n")
            .lines()
            .filterNot { line ->
                val clean = line.trim().trimEnd(':', '：', '|', '｜', '-', '—', ' ')
                isSectionWrapperHeader(clean) ||
                    clean.matches(Regex("""^(?:-\s*\[[ xX✓√✔]\]\s*)?(?:\d+\s*[.、)）]\s*)?《?[^》\n]{0,36}(?:推进细纲|分章细纲|分章规划|后续细纲)[^》\n]*》?(?:\s*[|｜]\s*(?:核心剧情|剧情))?$"""))
            }
            .joinToString("\n")
        val rawLines = preSplit.lines()
        if (rawLines.count { it.isNotBlank() } != 1) return rawLines
        val single = rawLines.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val splitParts = single.split(
            Regex("""[；;]\s*(?=(?:第\s*[0-9零一二两三四五六七八九十百千万]+\s*[章节回步幕卷节]|下[一二两三四五六七八九十\d]*章|随后几章))""")
        )
        return if (splitParts.size > 1) splitParts else rawLines
    }

    private fun isSectionWrapperHeader(raw: String): Boolean {
        val clean = stripQuotesAndSymbols(raw).replace("**", "").trim().trimEnd(':', '：', '|', '｜', ' ')
        if (clean.isBlank()) return false
        if (clean in containerHeadingNames) return true
        if (sectionOverviewHeadingRegex.matches(clean)) return true
        val hasPlanKeyword = clean.contains("推进细纲") || clean.contains("分章细纲") ||
            clean.contains("分章规划") || clean.contains("后续细纲")
        val hasRangeHint = clean.contains("~") || clean.contains("～") ||
            clean.contains("至") || clean.matches(Regex("""^\d+\s*章.*"""))
        return (hasPlanKeyword && (hasRangeHint || clean.length <= 36))
    }

    private fun parseSingleBeatLine(
        rawBody: String,
        explicitCompleted: Boolean,
        isHeading: Boolean,
        presetNumber: Int? = null
    ): MutableBeatDraft {
        var working = rawBody.trim()
        var number = presetNumber

        val numMatch = inlineStepNumberRegex.matchEntire(working)
        if (numMatch != null) {
            if (number == null) number = numMatch.groupValues[1].toIntOrNull()
            working = numMatch.groupValues[2].trim()
        } else {
            val stepMatch = inlineChineseStepRegex.matchEntire(working)
            if (stepMatch != null) {
                if (number == null) number = parseChineseOrDigitNumber(stepMatch.groupValues[1])
                working = stepMatch.groupValues[2].trim()
            }
        }

        val draft = MutableBeatDraft(
            displayNumber = number,
            rawHeaderOrLine = working,
            isHeadingBlock = isHeading,
            explicitCompleted = explicitCompleted
        )

        val pipeSegments = working.split(Regex("""\s*[|｜]\s*"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (pipeSegments.isEmpty()) return draft

        val headPart = pipeSegments.first()
        parseHeadSegment(headPart, isHeading, draft)

        for (segment in pipeSegments.drop(1)) {
            val sumProp = subPropertySummaryRegex.matchEntire(segment)
            if (sumProp != null) {
                draft.summaryParts += sumProp.groupValues[1].trim()
                continue
            }
            val hookProp = subPropertyHookRegex.matchEntire(segment)
            if (hookProp != null) {
                draft.hookParts += hookProp.groupValues[1].trim()
                continue
            }
            if (draft.summaryParts.isEmpty()) {
                draft.summaryParts += segment
            } else {
                draft.hookParts += segment
            }
        }
        return draft
    }

    private fun parseHeadSegment(
        headPart: String,
        isHeading: Boolean,
        draft: MutableBeatDraft
    ) {
        val quotedMatch = quotedTitleRegex.matchEntire(headPart)
        if (quotedMatch != null) {
            draft.explicitTitle = quotedMatch.groupValues[1].trim()
            val tail = quotedMatch.groupValues[2]
                .trim()
                .trimStart(':', '：', '-', '—', ' ', '　')
                .trim()
            if (tail.isNotBlank()) {
                val sumProp = subPropertySummaryRegex.matchEntire(tail)
                draft.summaryParts += (sumProp?.groupValues?.get(1) ?: tail).trim()
            }
            return
        }

        // 检查内嵌《标题》但前面还残余短修饰语的情况（如 "新章 《雨夜》：..."）
        val innerQuoted = Regex("""^([^《]{0,12})《([^》]+)》(.*)$""").matchEntire(headPart)
        if (innerQuoted != null) {
            draft.explicitTitle = innerQuoted.groupValues[2].trim()
            val tail = innerQuoted.groupValues[3]
                .trim()
                .trimStart(':', '：', '-', '—', ' ', '　')
                .trim()
            if (tail.isNotBlank()) {
                val sumProp = subPropertySummaryRegex.matchEntire(tail)
                draft.summaryParts += (sumProp?.groupValues?.get(1) ?: tail).trim()
            }
            return
        }

        val sepMatch = Regex("""^(.{1,24}?)\s*(?:[：:]|[-—]{1,2})\s*(.+)$""").matchEntire(headPart)
        if (sepMatch != null) {
            val left = sepMatch.groupValues[1].trim()
            val right = sepMatch.groupValues[2].trim()
            if (left in setOf("核心剧情", "剧情", "内容", "概要", "简介")) {
                draft.summaryParts += right
                return
            }
            val cleanedLeft = cleanTitleText(left)
            if (cleanedLeft.length in 1..20) {
                draft.explicitTitle = cleanedLeft
                val sumProp = subPropertySummaryRegex.matchEntire(right)
                draft.summaryParts += (sumProp?.groupValues?.get(1) ?: right).trim()
                return
            }
        }

        if (isHeading) {
            draft.explicitTitle = cleanTitleText(headPart)
        } else {
            val cleaned = cleanTitleText(headPart)
            if (cleaned.length <= 16 && !cleaned.contains('，') && !cleaned.contains('。') && !cleaned.contains(',')) {
                draft.explicitTitle = cleaned
            } else {
                draft.summaryParts += headPart
            }
        }
    }

    private fun finalizeDraft(
        draft: MutableBeatDraft,
        index: Int,
        completedTitleKeys: Set<String>
    ): OutlineBeat? {
        var rawSummary = draft.summaryParts
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        var rawHook = draft.hookParts
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()

        if (rawHook.isBlank() && rawSummary.isNotBlank()) {
            val hookSplit = inlineHookSplitRegex.find(rawSummary)
            if (hookSplit != null) {
                val beforeHook = rawSummary.substring(0, hookSplit.range.first).trim()
                val afterHook = rawSummary.substring(hookSplit.range.last + 1).trim()
                if (afterHook.isNotBlank()) {
                    rawSummary = beforeHook
                    rawHook = afterHook
                }
            }
        }

        val resolvedTitle = cleanTitleText(draft.explicitTitle).ifBlank {
            deriveShortTitleFromClause(rawSummary) ?: cleanTitleText(draft.rawHeaderOrLine).take(14)
        }.ifBlank { "第${index + 1}章" }

        val resolvedSummary = rawSummary.ifBlank { resolvedTitle }
        val key = normalizeTitleKey(resolvedTitle)
        val completed = draft.explicitCompleted || (key.isNotBlank() && key in completedTitleKeys)

        return OutlineBeat(
            index = index,
            displayNumber = draft.displayNumber?.takeIf { it > 0 } ?: (index + 1),
            chapterTitle = resolvedTitle,
            summary = resolvedSummary,
            hook = rawHook,
            isCompleted = completed
        )
    }

    private fun extractTitlesBlock(rawText: String): String? {
        val start = rawText.indexOf(TITLES_START)
        if (start < 0) return null
        val contentStart = start + TITLES_START.length
        val end = rawText.indexOf(TITLES_END, startIndex = contentStart)
        val slice = if (end >= 0) {
            rawText.substring(contentStart, end)
        } else {
            rawText.substring(contentStart)
        }
        return slice.trim()
    }

    internal fun normalizeTitleKey(raw: String): String {
        var working = stripTitlePrefixes(raw)
        working = stripQuotesAndSymbols(working)
        return working.replace(Regex("""\s+"""), "").lowercase()
    }

    private fun stripTitlePrefixes(raw: String): String {
        var working = raw.trim()
        repeat(4) {
            val prev = working
            val afterAi = working.replace(titleAiPrefixRegex, "").trim()
            if (afterAi.isNotBlank() && stripQuotesAndSymbols(afterAi).isNotBlank()) {
                working = afterAi
            }
            val afterChap = working.replace(titleChapterPrefixRegex, "").trim()
            if (afterChap.isNotBlank() && stripQuotesAndSymbols(afterChap).isNotBlank()) {
                working = afterChap
            }
            if (working == prev) return@repeat
        }
        return working
    }

    private fun cleanTitleText(raw: String): String {
        return stripQuotesAndSymbols(raw)
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun stripQuotesAndSymbols(raw: String): String {
        return raw.trim()
            .replace("**", "")
            .replace("__", "")
            .replace("《", "")
            .replace("》", "")
            .replace("「", "")
            .replace("」", "")
            .replace("『", "")
            .replace("』", "")
            .replace("\"", "")
            .replace("“", "")
            .replace("”", "")
            .replace("'", "")
            .replace("‘", "")
            .replace("’", "")
            .trim()
    }

    private fun deriveShortTitleFromClause(raw: String): String? {
        val clean = cleanTitleText(raw)
        if (clean.isBlank()) return null
        val firstClause = clean
            .split(Regex("""[，,。！？!?；;：:\n]+"""))
            .map { it.trim() }
            .firstOrNull { it.length >= 2 }
            ?: clean
        val trimmedConnector = firstClause
            .replace(Regex("""^(?:随后|接着|此时|然而|但是|不过|就在这时|只见|主角|众人)"""), "")
            .trim()
            .ifBlank { firstClause }
        val result = if (trimmedConnector.length <= 14) {
            trimmedConnector
        } else {
            trimmedConnector.take(12)
        }
        return result.trimEnd('的', '了', '着', '与', '和').takeIf { it.length >= 2 }
    }

    private fun parseChineseOrDigitNumber(token: String): Int? {
        if (token.isBlank()) return null
        token.toIntOrNull()?.let { return it }
        val digits = mapOf(
            '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )
        if (token == "十") return 10
        if (token.length == 2 && token[0] == '十') {
            return 10 + (digits[token[1]] ?: return null)
        }
        if (token.length == 2 && token[1] == '十') {
            return (digits[token[0]] ?: return null) * 10
        }
        if (token.length == 3 && token[1] == '十') {
            val tens = digits[token[0]] ?: return null
            val ones = digits[token[2]] ?: return null
            return tens * 10 + ones
        }
        return digits[token.firstOrNull()]
    }
}
