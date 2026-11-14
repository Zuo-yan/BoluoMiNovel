package com.novelai.assistant.ui.continuation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.AdaptiveGlassDialog
import com.novelai.assistant.ui.components.AdaptiveTwoPane
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassCircleButton
import com.novelai.assistant.ui.components.GlassFilterPill
import com.novelai.assistant.ui.components.GlassGradientBar
import com.novelai.assistant.ui.components.GlassGradientDirection
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.LocalGlassHazeState
import com.novelai.assistant.ui.components.OriginBadge
import com.novelai.assistant.ui.theme.NovelColors
import dev.chrisbanes.haze.HazeState

private val ContinuationPresets = listOf(
    "冲突升级" to "重点描写主角与反派/对手的正面交锋与局势升级，节奏紧凑。",
    "伏笔回收" to "自然串联前文埋下的线索与人物动机，揭示一层出乎意料的真相。",
    "细腻互动" to "放慢节奏刻画角色间的微表情、潜台词与情感张力。",
    "章末悬念" to "推进主线并在章节末尾留下强烈的反转悬念钩子。"
)

private val BranchPresets = listOf(
    "相反抉择" to "如果主角在这个关键节点做出了与原作完全相反的决定，剧情会如何走向？",
    "反派视角" to "切换至关键对手或配角的隐秘视角，揭示另一面的动机与暗线行动。",
    "真相提前" to "如果隐藏的核心秘密在这一章提前暴露，所有人物关系将如何剧变？",
    "意外变故" to "引入一个合乎设定但出人意料的突发危机，彻底改写原有局势。"
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun ContinuationScreen(
    bookId: String,
    parentChapterId: String,
    mode: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ContinuationViewModel = hiltViewModel()
) {
    val adaptive = LocalAdaptiveMetrics.current
    val hazeState = LocalGlassHazeState.current

    val book by viewModel.book.collectAsStateWithLifecycle()
    val parent by viewModel.parentChapter.collectAsStateWithLifecycle()
    val instruction by viewModel.instruction.collectAsStateWithLifecycle()
    val customTitle by viewModel.customTitle.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val useCraft by viewModel.useWorkshopCraft.collectAsStateWithLifecycle()
    val allChapters by viewModel.allChapters.collectAsStateWithLifecycle()
    val continuousCount by viewModel.continuousCount.collectAsStateWithLifecycle()
    val latestCh by viewModel.latestChapter.collectAsStateWithLifecycle()
    val useLatest by viewModel.useLatestChapter.collectAsStateWithLifecycle()

    var showChapterPicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GlassGradientBar(
                direction = GlassGradientDirection.TOP_TO_BOTTOM,
                state = hazeState,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassCircleButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                        size = 40.dp,
                        state = hazeState
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (viewModel.isBranchMode) "剧情分支 · What-if" else "AI 续写工作台",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "《${book?.title ?: ""}》",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (continuousCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "连写 $continuousCount 章",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding()
        ) {
            if (adaptive.twoPaneEnabled) {
                AdaptiveTwoPane(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = adaptive.horizontalPadding, vertical = 8.dp),
                    splitEnabled = true,
                    primaryFraction = 0.42f,
                    primaryPane = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ContinuationControlsColumn(
                                isBranchMode = viewModel.isBranchMode,
                                continuousCount = continuousCount,
                                useLatest = useLatest,
                                latestCh = latestCh,
                                parent = parent,
                                instruction = instruction,
                                useCraft = useCraft,
                                streaming = streaming,
                                finished = finished,
                                error = error,
                                hazeState = hazeState,
                                onSelectLatest = { viewModel.setUseLatestChapter(true) },
                                onSelectSpecific = {
                                    viewModel.setUseLatestChapter(false)
                                    showChapterPicker = true
                                },
                                onOpenChapterPicker = { showChapterPicker = true },
                                onInstructionChange = { viewModel.instruction.value = it },
                                onToggleCraft = { viewModel.toggleWorkshopCraft() },
                                onGenerate = { viewModel.generate() },
                                onStop = { viewModel.stop() }
                            )
                        }
                    },
                    secondaryPane = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 24.dp)
                        ) {
                            ContinuationPreviewPane(
                                isBranchMode = viewModel.isBranchMode,
                                parent = parent,
                                instruction = instruction,
                                streaming = streaming,
                                streamingText = streamingText,
                                finished = finished,
                                customTitle = customTitle,
                                hazeState = hazeState,
                                showEmptyPlaceholder = true,
                                onCustomTitleChange = { viewModel.customTitle.value = it },
                                onSaveAndContinue = { viewModel.saveAndContinue(autoGenerateNext = false) },
                                onSaveAndExit = { viewModel.save(onSaved) },
                                onAppendToParent = { viewModel.appendToParentChapter(onSaved) },
                                onReset = { viewModel.reset() }
                            )
                        }
                    }
                )
            } else {
                AdaptiveCenteredContainer(
                    maxWidth = adaptive.maxFormWidth,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = adaptive.horizontalPadding, vertical = 10.dp)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ContinuationControlsColumn(
                            isBranchMode = viewModel.isBranchMode,
                            continuousCount = continuousCount,
                            useLatest = useLatest,
                            latestCh = latestCh,
                            parent = parent,
                            instruction = instruction,
                            useCraft = useCraft,
                            streaming = streaming,
                            finished = finished,
                            error = error,
                            hazeState = hazeState,
                            onSelectLatest = { viewModel.setUseLatestChapter(true) },
                            onSelectSpecific = {
                                viewModel.setUseLatestChapter(false)
                                showChapterPicker = true
                            },
                            onOpenChapterPicker = { showChapterPicker = true },
                            onInstructionChange = { viewModel.instruction.value = it },
                            onToggleCraft = { viewModel.toggleWorkshopCraft() },
                            onGenerate = { viewModel.generate() },
                            onStop = { viewModel.stop() }
                        )

                        if (streamingText.isNotBlank() || streaming) {
                            ContinuationPreviewPane(
                                isBranchMode = viewModel.isBranchMode,
                                parent = parent,
                                instruction = instruction,
                                streaming = streaming,
                                streamingText = streamingText,
                                finished = finished,
                                customTitle = customTitle,
                                hazeState = hazeState,
                                showEmptyPlaceholder = false,
                                onCustomTitleChange = { viewModel.customTitle.value = it },
                                onSaveAndContinue = { viewModel.saveAndContinue(autoGenerateNext = false) },
                                onSaveAndExit = { viewModel.save(onSaved) },
                                onAppendToParent = { viewModel.appendToParentChapter(onSaved) },
                                onReset = { viewModel.reset() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showChapterPicker) {
        AdaptiveGlassDialog(
            onDismissRequest = { showChapterPicker = false },
            maxWidth = adaptive.maxDialogSheetWidth
        ) {
            Text(
                "选择基准章节",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                item {
                    TextButton(
                        onClick = {
                            viewModel.selectLatestChapter()
                            showChapterPicker = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "⚡ 全书最新章（第${(allChapters.maxByOrNull { it.chapterIndex }?.chapterIndex ?: 0) + 1}章 · 自动追踪）",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                items(allChapters, key = { it.id }) { ch ->
                    TextButton(
                        onClick = {
                            viewModel.selectParentChapter(ch)
                            showChapterPicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "${ch.chapterIndex + 1}. ${ch.title}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showChapterPicker = false }) { Text("取消") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContinuationControlsColumn(
    isBranchMode: Boolean,
    continuousCount: Int,
    useLatest: Boolean,
    latestCh: ChapterEntity?,
    parent: ChapterEntity?,
    instruction: String,
    useCraft: Boolean,
    streaming: Boolean,
    finished: Boolean,
    error: String?,
    hazeState: HazeState?,
    onSelectLatest: () -> Unit,
    onSelectSpecific: () -> Unit,
    onOpenChapterPicker: () -> Unit,
    onInstructionChange: (String) -> Unit,
    onToggleCraft: () -> Unit,
    onGenerate: () -> Unit,
    onStop: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val presets = if (isBranchMode) BranchPresets else ContinuationPresets

    if (continuousCount > 0) {
        LiquidGlassBox(
            modifier = Modifier.fillMaxWidth(),
            state = hazeState,
            cornerRadius = 18.dp,
            widgetSize = GlassWidgetSize.SMALL,
            sceneType = GlassSceneType.GENERAL,
            interactive = false,
            skipRenderLayer = true,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "连续创作中 · 已连续生成并保存 $continuousCount 章，当前正基于最新章节衔接！",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (!isBranchMode) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassFilterPill(
                label = if (latestCh != null) "⚡ 全书最新章（第${latestCh.chapterIndex + 1}章）"
                else "⚡ 直接基于最新章续写",
                selected = useLatest,
                onClick = onSelectLatest,
                leadingIcon = if (useLatest) Icons.Rounded.Check else null
            )
            GlassFilterPill(
                label = "指定章节",
                selected = !useLatest,
                onClick = onSelectSpecific,
                leadingIcon = if (!useLatest) Icons.Rounded.Check else null
            )
        }
    }

    // 基准章节卡
    GlassSectionCard(
        title = if (isBranchMode) "剧情分岔原点" else "基准衔接章节",
        actionLabel = "切换",
        onActionClick = onOpenChapterPicker,
        state = hazeState,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            OriginBadge(if (isBranchMode) "AI_BRANCH" else parent?.originType?.name ?: "ORIGINAL")
            Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = parent?.title ?: "加载中…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = (parent?.content ?: "").takeLast(160),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isBranchMode) "剧情从这一章分岔推演"
                    else if (useLatest) "⚡ 自动追踪全书最新末尾无缝续写"
                    else "正文从这一章末尾续写",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // 指令与预设灵感
    GlassSectionCard(
        title = if (isBranchMode) "What-if 假设与灵感预设" else "续写要求与灵感预设",
        state = hazeState,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { (label, prompt) ->
                    GlassFilterPill(
                        label = label,
                        selected = instruction.trim() == prompt,
                        onClick = { onInstructionChange(prompt) }
                    )
                }
            }
            OutlinedTextField(
                value = instruction,
                onValueChange = onInstructionChange,
                placeholder = {
                    Text(
                        if (isBranchMode) "输入假设：例如「如果主角没有救女二，剧情会怎样？」"
                        else "补充要求（可留空）：例如「重点写主角与反派的正面冲突」"
                    )
                },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // 小说工坊技法 + 生成按钮
    GlassSectionCard(
        state = hazeState,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("小说工坊技法", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "展示而非讲述、潜台词对话、章末钩子、去 AI 味",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = useCraft, onCheckedChange = { onToggleCraft() })
            }

            if (streaming) {
                GlassCapsuleButton(
                    text = "停止生成",
                    onClick = onStop,
                    filled = false,
                    icon = Icons.Rounded.Stop,
                    state = hazeState,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (!finished) {
                GlassCapsuleButton(
                    text = if (isBranchMode) "推演平行分支" else "开始续写",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onGenerate()
                    },
                    filled = true,
                    icon = Icons.Rounded.AutoAwesome,
                    state = hazeState,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    error?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ContinuationPreviewPane(
    isBranchMode: Boolean,
    parent: ChapterEntity?,
    instruction: String,
    streaming: Boolean,
    streamingText: String,
    finished: Boolean,
    customTitle: String,
    hazeState: HazeState?,
    showEmptyPlaceholder: Boolean,
    onCustomTitleChange: (String) -> Unit,
    onSaveAndContinue: () -> Unit,
    onSaveAndExit: () -> Unit,
    onAppendToParent: () -> Unit,
    onReset: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bodyTextColor = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText

    LiquidGlassBox(
        modifier = Modifier.fillMaxWidth(),
        state = hazeState,
        cornerRadius = 24.dp,
        widgetSize = GlassWidgetSize.BIG,
        sceneType = GlassSceneType.GENERAL,
        interactive = false,
        skipRenderLayer = true,
        shadowElevation = 8.dp
    ) {
        if (streamingText.isBlank() && !streaming && showEmptyPlaceholder) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Rounded.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = if (isBranchMode) "平行分支实时预览区" else "AI 续写实时预览区",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = bodyTextColor
                )
                Text(
                    text = "在左侧选择章节与灵感预设后点击「${if (isBranchMode) "推演平行分支" else "开始续写"}」，生成正文将在此流式呈现并可直接编辑保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (streaming) "生成中…" else "生成完成",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (streamingText.isNotBlank()) {
                        Text(
                            text = "${streamingText.length} 字",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (streaming) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(14.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                if (streamingText.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text = streamingText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = bodyTextColor
                        )
                    }
                }

                if (finished) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        "保存已生成内容",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = bodyTextColor
                    )
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = onCustomTitleChange,
                        label = { Text("章节标题（可自定义，留空自动命名）") },
                        placeholder = {
                            Text(
                                if (isBranchMode) "分支：${instruction.take(10).ifBlank { "平行世界" }}"
                                else "AI 续写 · ${parent?.title?.take(12) ?: "新章"}"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GlassCapsuleButton(
                            text = "保存并续写下一章",
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSaveAndContinue()
                            },
                            filled = true,
                            icon = Icons.Rounded.AutoAwesome,
                            state = hazeState,
                            modifier = Modifier.weight(1.3f)
                        )
                        GlassCapsuleButton(
                            text = "保存并退出",
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSaveAndExit()
                            },
                            filled = false,
                            state = hazeState,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!isBranchMode) {
                            OutlinedButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAppendToParent()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("追加到本章末", maxLines = 1) }
                        }
                        OutlinedButton(
                            onClick = onReset,
                            modifier = Modifier.weight(1f)
                        ) { Text("重写本章", maxLines = 1) }
                    }
                }
            }
        }
    }
}
