package com.novelai.assistant.ui.continuation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.domain.ContinuationLengthMode
import com.novelai.assistant.domain.OutlineBeat
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
import com.novelai.assistant.ui.components.MarkdownText
import com.novelai.assistant.ui.components.OriginBadge
import com.novelai.assistant.ui.theme.NovelColors
import dev.chrisbanes.haze.HazeState

private val ContinuationPresets = listOf(
    "冲突升级" to "重点描写主角与反派/对手的正面交锋与局势升级，节奏紧凑。",
    "伏笔回收" to "自然串联前文埋下的线索与人物动机，揭示一层出乎意料的真相。",
    "相反抉择" to "如果主角在这个关键节点做出了与原本完全不同的选择，局势会如何反转？",
    "反派视角" to "切换至关键对手或配角的隐秘视角，揭示另一面的动机与暗线行动。",
    "细腻互动" to "放慢节奏刻画角色间的微表情、潜台词与情感张力。",
    "章末悬念" to "推进主线并在章节末尾留下强烈的反转悬念钩子。"
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
    val lengthMode by viewModel.lengthMode.collectAsStateWithLifecycle()
    val retrievedTitles by viewModel.retrievedChapterTitles.collectAsStateWithLifecycle()
    val retrievedUsedEmbedding by viewModel.retrievedUsedEmbedding.collectAsStateWithLifecycle()
    val activeModelLabel by viewModel.activeModelLabel.collectAsStateWithLifecycle()
    val outlineModelLabel by viewModel.outlineModelLabel.collectAsStateWithLifecycle()
    val allChapters by viewModel.allChapters.collectAsStateWithLifecycle()
    val continuousCount by viewModel.continuousCount.collectAsStateWithLifecycle()
    val latestCh by viewModel.latestChapter.collectAsStateWithLifecycle()
    val currentReadingCh by viewModel.currentReadingChapter.collectAsStateWithLifecycle()
    val anchorMode by viewModel.anchorMode.collectAsStateWithLifecycle()

    val outline by viewModel.outline.collectAsStateWithLifecycle()
    val outlineBeats by viewModel.outlineBeats.collectAsStateWithLifecycle()
    val selectedBeatIndex by viewModel.selectedBeatIndex.collectAsStateWithLifecycle()
    val activeOutlineBeat by viewModel.activeOutlineBeat.collectAsStateWithLifecycle()
    val nextBeatAfterCurrent by viewModel.nextBeatAfterCurrent.collectAsStateWithLifecycle()
    val remainingUnwrittenBeatsCount by viewModel.remainingUnwrittenBeatsCount.collectAsStateWithLifecycle()
    val recommendedTitles by viewModel.recommendedTitles.collectAsStateWithLifecycle()
    val suggestingTitles by viewModel.suggestingTitles.collectAsStateWithLifecycle()
    val titleSuggestionError by viewModel.titleSuggestionError.collectAsStateWithLifecycle()
    val pendingOutlineProposal by viewModel.pendingOutlineProposal.collectAsStateWithLifecycle()
    val discussionMessages by viewModel.discussionMessages.collectAsStateWithLifecycle()
    val discussionStreamingText by viewModel.discussionStreamingText.collectAsStateWithLifecycle()
    val discussionGenerating by viewModel.discussionGenerating.collectAsStateWithLifecycle()
    val discussionError by viewModel.discussionError.collectAsStateWithLifecycle()
    val validSummaryCount by viewModel.validSummaryCount.collectAsStateWithLifecycle()
    val persistedVectorCount by viewModel.persistedVectorCount.collectAsStateWithLifecycle()
    val isWarmingSummaries by viewModel.isWarmingSummaries.collectAsStateWithLifecycle()
    val memoryStatusMessage by viewModel.memoryStatusMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 通知权限结果 */ }
    val ensureNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var showChapterPicker by remember { mutableStateOf(false) }
    var showPlotDiscussion by remember { mutableStateOf(false) }
    var showOutlineEditor by remember { mutableStateOf(false) }

    val renderControls: @Composable () -> Unit = {
        ContinuationControlsColumn(
            outline = outline,
            outlineBeats = outlineBeats,
            selectedBeatIndex = selectedBeatIndex,
            activeOutlineBeat = activeOutlineBeat,
            remainingUnwrittenBeatsCount = remainingUnwrittenBeatsCount,
            discussionCount = discussionMessages.size,
            validSummaryCount = validSummaryCount,
            totalChapterCount = allChapters.size,
            persistedVectorCount = persistedVectorCount,
            onToggleUseOutline = { viewModel.toggleUseOutline() },
            onSelectBeat = { viewModel.selectOutlineBeat(it) },
            onClearSelectedBeat = { viewModel.clearSelectedBeat() },
            onToggleBeatCompleted = { viewModel.toggleBeatCompleted(it) },
            onOpenDiscussion = { showPlotDiscussion = true },
            onExtendOutlinePlan = {
                showPlotDiscussion = true
                if (!discussionGenerating) {
                    viewModel.requestExtendOutlinePlan(6)
                }
            },
            onOpenOutlineEditor = { showOutlineEditor = true },
            continuousCount = continuousCount,
            anchorMode = anchorMode,
            latestCh = latestCh,
            currentReadingCh = currentReadingCh,
            parent = parent,
            customTitle = customTitle,
            recommendedTitles = recommendedTitles,
            suggestingTitles = suggestingTitles,
            titleSuggestionError = titleSuggestionError,
            instruction = instruction,
            useCraft = useCraft,
            lengthMode = lengthMode,
            retrievedTitles = retrievedTitles,
            retrievedUsedEmbedding = retrievedUsedEmbedding,
            streaming = streaming,
            finished = finished,
            error = error,
            hazeState = hazeState,
            onSelectLatest = { viewModel.selectLatestChapter() },
            onSelectCurrent = { viewModel.selectCurrentReadingChapter() },
            onSelectSpecific = { showChapterPicker = true },
            onOpenChapterPicker = { showChapterPicker = true },
            onCustomTitleChange = { viewModel.customTitle.value = it },
            onApplyRecommendedTitle = { viewModel.applyRecommendedTitle(it) },
            onSuggestTitles = { viewModel.suggestChapterTitles() },
            onInstructionChange = { viewModel.instruction.value = it },
            onToggleCraft = { viewModel.toggleWorkshopCraft() },
            onSelectLengthMode = { viewModel.selectLengthMode(it) },
            onGenerate = {
                ensureNotificationPermission()
                viewModel.generate()
            },
            onStop = { viewModel.stop() },
            onMinimizeToRead = onBack
        )
    }

    val renderPreview: @Composable (Boolean) -> Unit = { showEmptyPlaceholder ->
        ContinuationPreviewPane(
            parent = parent,
            activeOutlineBeat = activeOutlineBeat,
            nextBeatAfterCurrent = nextBeatAfterCurrent,
            recommendedTitles = recommendedTitles,
            suggestingTitles = suggestingTitles,
            titleSuggestionError = titleSuggestionError,
            streaming = streaming,
            streamingText = streamingText,
            finished = finished,
            customTitle = customTitle,
            hazeState = hazeState,
            showEmptyPlaceholder = showEmptyPlaceholder,
            onCustomTitleChange = { viewModel.customTitle.value = it },
            onApplyRecommendedTitle = { viewModel.applyRecommendedTitle(it) },
            onSuggestTitles = { viewModel.suggestChapterTitles() },
            onStreamingTextChange = { viewModel.updateGeneratedText(it) },
            onContinueGenerating = { viewModel.continueGenerating() },
            onSaveAndContinue = { viewModel.saveAndContinue(autoGenerateNext = false) },
            onSaveAndExit = { viewModel.save(onSaved) },
            onAppendToParent = { viewModel.appendToParentChapter(onSaved) },
            onReset = { viewModel.reset() }
        )
    }

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
                            text = "AI 续写工作台",
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
                        Text(
                            text = "当前模型：$activeModelLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (activeModelLabel == "未配置模型") {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
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
                            renderControls()
                        }
                    },
                    secondaryPane = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 24.dp)
                        ) {
                            renderPreview(true)
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
                        renderControls()

                        if (streamingText.isNotBlank() || streaming || finished) {
                            renderPreview(false)
                        }
                    }
                }
            }
        }
    }

    if (showChapterPicker) {
        ChapterAnchorPickerDialog(
            allChapters = allChapters,
            latestChapter = latestCh,
            currentReadingChapter = currentReadingCh,
            onSelectLatest = { viewModel.selectLatestChapter() },
            onSelectCurrent = { viewModel.selectCurrentReadingChapter() },
            onSelectChapter = { ch -> viewModel.selectParentChapter(ch) },
            onDismiss = { showChapterPicker = false }
        )
    }

    if (showPlotDiscussion) {
        PlotDiscussionDialog(
            bookTitle = book?.title.orEmpty(),
            anchorChapter = parent,
            anchorChapterTitle = parent?.title ?: "未选章节",
            totalChapters = allChapters.size,
            anchorMode = anchorMode,
            latestChapter = latestCh,
            currentReadingChapter = currentReadingCh,
            allChapters = allChapters,
            activeModelLabel = outlineModelLabel,
            hasExistingUpcomingPlan = !outline?.upcomingPlan.isNullOrBlank() || outlineBeats.isNotEmpty(),
            pendingOutlineProposal = pendingOutlineProposal,
            messages = discussionMessages,
            streamingText = discussionStreamingText,
            generating = discussionGenerating,
            error = discussionError,
            hazeState = hazeState,
            decodeQuickOptions = viewModel::decodeQuickOptions,
            onSelectLatestAnchor = { viewModel.selectLatestChapter() },
            onSelectCurrentAnchor = { viewModel.selectCurrentReadingChapter() },
            onSelectCustomAnchor = { ch -> viewModel.selectParentChapter(ch) },
            onSendMessage = { text -> viewModel.sendPlotDiscussion(text, requestSynthesizeOutline = false) },
            onSynthesizeOutline = {
                viewModel.sendPlotDiscussion("", requestSynthesizeOutline = true)
            },
            onExtendOutlinePlan = {
                viewModel.requestExtendOutlinePlan(6)
            },
            onAdoptOutline = { master, upcoming, appendBeats ->
                viewModel.adoptProposedOutline(master, upcoming, appendBeats = appendBeats)
            },
            onAcceptProposalAndGenerate = {
                ensureNotificationPermission()
                viewModel.acceptOutlineProposalAndGenerate()
            },
            onApplyProposalOnly = {
                viewModel.applyOutlineProposalOnly()
            },
            onDismissProposal = {
                viewModel.dismissOutlineProposal()
            },
            onStopGenerating = { viewModel.stopPlotDiscussion() },
            onClearDiscussion = { viewModel.clearDiscussion() },
            onDismiss = { showPlotDiscussion = false }
        )
    }

    if (showOutlineEditor) {
        OutlineEditorDialog(
            outline = outline,
            validSummaryCount = validSummaryCount,
            totalChapterCount = allChapters.size,
            persistedVectorCount = persistedVectorCount,
            isWarmingSummaries = isWarmingSummaries,
            memoryStatusMessage = memoryStatusMessage,
            hazeState = hazeState,
            onToggleUseOutline = { viewModel.toggleUseOutline() },
            onSaveOutline = { master, upcoming ->
                viewModel.saveOutlineManual(master, upcoming, proposeContinuation = true)
            },
            onClearOutline = { viewModel.clearOutline() },
            onWarmUpSummaries = { viewModel.warmUpMissingSummaries() },
            onDismiss = {
                viewModel.dismissMemoryStatusMessage()
                showOutlineEditor = false
            }
        )
    }

    val externalProposal = pendingOutlineProposal
    if (externalProposal != null && !showPlotDiscussion && !showOutlineEditor) {
        AdaptiveGlassDialog(
            onDismissRequest = { viewModel.dismissOutlineProposal() },
            maxWidth = adaptive.maxDialogSheetWidth,
            contentPadding = PaddingValues(10.dp)
        ) {
            PendingOutlineProposalCard(
                proposal = externalProposal,
                shadowElevation = 0.dp,
                onAcceptAndGenerate = {
                    ensureNotificationPermission()
                    viewModel.acceptOutlineProposalAndGenerate()
                },
                onApplyOnly = {
                    viewModel.applyOutlineProposalOnly()
                },
                onDismiss = {
                    viewModel.dismissOutlineProposal()
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContinuationControlsColumn(
    outline: com.novelai.assistant.data.db.BookOutlineEntity?,
    outlineBeats: List<OutlineBeat>,
    selectedBeatIndex: Int?,
    activeOutlineBeat: OutlineBeat?,
    remainingUnwrittenBeatsCount: Int,
    discussionCount: Int,
    validSummaryCount: Int,
    totalChapterCount: Int,
    persistedVectorCount: Int,
    onToggleUseOutline: () -> Unit,
    onSelectBeat: (OutlineBeat) -> Unit,
    onClearSelectedBeat: () -> Unit,
    onToggleBeatCompleted: (OutlineBeat) -> Unit,
    onOpenDiscussion: () -> Unit,
    onExtendOutlinePlan: () -> Unit,
    onOpenOutlineEditor: () -> Unit,
    continuousCount: Int,
    anchorMode: AnchorChapterMode,
    latestCh: ChapterEntity?,
    currentReadingCh: ChapterEntity?,
    parent: ChapterEntity?,
    customTitle: String,
    recommendedTitles: List<String>,
    suggestingTitles: Boolean,
    titleSuggestionError: String?,
    instruction: String,
    useCraft: Boolean,
    lengthMode: ContinuationLengthMode,
    retrievedTitles: List<String>,
    retrievedUsedEmbedding: Boolean,
    streaming: Boolean,
    finished: Boolean,
    error: String?,
    hazeState: HazeState?,
    onSelectLatest: () -> Unit,
    onSelectCurrent: () -> Unit,
    onSelectSpecific: () -> Unit,
    onOpenChapterPicker: () -> Unit,
    onCustomTitleChange: (String) -> Unit,
    onApplyRecommendedTitle: (String) -> Unit,
    onSuggestTitles: () -> Unit,
    onInstructionChange: (String) -> Unit,
    onToggleCraft: () -> Unit,
    onSelectLengthMode: (ContinuationLengthMode) -> Unit,
    onGenerate: () -> Unit,
    onStop: () -> Unit,
    onMinimizeToRead: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accentColor = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    val presets = ContinuationPresets

    OutlineAndMemoryCard(
        outline = outline,
        outlineBeats = outlineBeats,
        selectedBeatIndex = selectedBeatIndex,
        activeOutlineBeat = activeOutlineBeat,
        remainingUnwrittenBeatsCount = remainingUnwrittenBeatsCount,
        discussionCount = discussionCount,
        validSummaryCount = validSummaryCount,
        totalChapterCount = totalChapterCount,
        persistedVectorCount = persistedVectorCount,
        hazeState = hazeState,
        onToggleUseOutline = onToggleUseOutline,
        onSelectBeat = onSelectBeat,
        onClearSelectedBeat = onClearSelectedBeat,
        onToggleBeatCompleted = onToggleBeatCompleted,
        onOpenDiscussion = onOpenDiscussion,
        onExtendOutlinePlan = onExtendOutlinePlan,
        onOpenOutlineEditor = onOpenOutlineEditor
    )

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
                    "连续创作中 · 已连续生成并保存 $continuousCount 章，当前正基于上一章衔接！",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // 基准章节卡（集成三态切换栏 + 选中章节正文预览）
    GlassSectionCard(
        title = "基准衔接章节",
        actionLabel = "目录选择",
        onActionClick = onOpenChapterPicker,
        state = hazeState,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AnchorChapterSelectorBar(
                anchorMode = anchorMode,
                latestChapter = latestCh,
                currentReadingChapter = currentReadingCh,
                activeChapter = parent,
                totalChapters = totalChapterCount,
                showActiveSummary = false,
                onSelectLatest = onSelectLatest,
                onSelectCurrent = onSelectCurrent,
                onSelectCustom = onSelectSpecific
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
            Row(verticalAlignment = Alignment.Top) {
                OriginBadge(parent?.originType?.name ?: "ORIGINAL")
                Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = parent?.title ?: "加载中…",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (parent != null && totalChapterCount > 0) {
                            Text(
                                text = "#${parent.chapterIndex + 1}/$totalChapterCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        text = (parent?.content ?: "").takeLast(160),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (anchorMode) {
                            AnchorChapterMode.LATEST -> "⚡ 自动追踪全书最新末尾无缝续写"
                            AnchorChapterMode.CURRENT -> "📖 基于当前阅读章节衍生分支，保存后紧跟在其后"
                            AnchorChapterMode.CUSTOM -> "🎯 基于指定章节衍生分支，保存后紧跟在其后"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // 写前「目标章节标题」直采区
    GlassSectionCard(
        title = "目标章节标题（写前可定）",
        state = hazeState,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (activeOutlineBeat != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.24f else 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "细纲第${activeOutlineBeat.displayNumber}步：《${activeOutlineBeat.chapterTitle}》",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = accentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "提前拟定章名，AI 正文将紧扣章名展开",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                SuggestTitleCapsuleButton(
                    label = "换拟标题",
                    suggesting = suggestingTitles,
                    onClick = onSuggestTitles
                )
            }

            OutlinedTextField(
                value = customTitle,
                onValueChange = onCustomTitleChange,
                label = { Text("预设章节标题（来自大纲或自定义）") },
                placeholder = {
                    Text(
                        activeOutlineBeat?.chapterTitle?.takeIf { it.isNotBlank() }?.let { "推荐：$it" }
                            ?: "留空自动命名，或点选下方推荐标题"
                    )
                },
                singleLine = true,
                trailingIcon = if (customTitle.isNotBlank()) {
                    {
                        IconButton(onClick = { onCustomTitleChange("") }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "清空标题",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (!titleSuggestionError.isNullOrBlank()) {
                Text(
                    text = titleSuggestionError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (recommendedTitles.isNotEmpty()) {
                RecommendedTitleChipsRow(
                    recommendedTitles = recommendedTitles,
                    selectedTitle = customTitle,
                    activeOutlineBeatTitle = activeOutlineBeat?.chapterTitle,
                    onSelectTitle = onApplyRecommendedTitle
                )
            }
        }
    }

    // 指令与预设灵感
    GlassSectionCard(
        title = "续写要求与灵感预设",
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
                    Text("补充要求（可留空）：例如「重点写主角与反派的正面冲突」或假设不同走向")
                },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // 原著高仿 · 深度去 AI 味开关 + 续写篇幅 + 生成按钮
    GlassSectionCard(
        state = hazeState,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("原著高仿 · 深度去 AI 味", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "100% 复刻原著口吻与人物声纹，融合 Sepia 叙事松绑与去 AI 味清洗",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = useCraft, onCheckedChange = { onToggleCraft() })
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "续写篇幅",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ContinuationLengthMode.entries.forEach { mode ->
                        GlassFilterPill(
                            label = mode.label,
                            selected = lengthMode == mode,
                            onClick = { onSelectLengthMode(mode) }
                        )
                    }
                }
                Text(
                    text = "${lengthMode.wordHint}（请求输出上限保底 ${lengthMode.minMaxTokens} tokens）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (streaming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassCapsuleButton(
                        text = "停止生成",
                        onClick = onStop,
                        filled = false,
                        icon = Icons.Rounded.Stop,
                        state = hazeState,
                        modifier = Modifier.weight(1f)
                    )
                    GlassCapsuleButton(
                        text = "收起去阅读",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMinimizeToRead()
                        },
                        filled = true,
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        state = hazeState,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "已开启后台保活：可直接返回继续看书，边缘灵动胶囊会实时显示生成字数",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!finished) {
                GlassCapsuleButton(
                    text = "开始续写",
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

    if (retrievedTitles.isNotEmpty()) {
        LiquidGlassBox(
            modifier = Modifier.fillMaxWidth(),
            state = hazeState,
            cornerRadius = 16.dp,
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
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "已召回前文记忆${if (retrievedUsedEmbedding) "（语义混合）" else ""}：${retrievedTitles.joinToString("、")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
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
    parent: ChapterEntity?,
    activeOutlineBeat: OutlineBeat?,
    nextBeatAfterCurrent: OutlineBeat?,
    recommendedTitles: List<String>,
    suggestingTitles: Boolean,
    titleSuggestionError: String?,
    streaming: Boolean,
    streamingText: String,
    finished: Boolean,
    customTitle: String,
    hazeState: HazeState?,
    showEmptyPlaceholder: Boolean,
    onCustomTitleChange: (String) -> Unit,
    onApplyRecommendedTitle: (String) -> Unit,
    onSuggestTitles: () -> Unit,
    onStreamingTextChange: (String) -> Unit,
    onContinueGenerating: () -> Unit,
    onSaveAndContinue: () -> Unit,
    onSaveAndExit: () -> Unit,
    onAppendToParent: () -> Unit,
    onReset: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bodyTextColor = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val accentColor = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    var isEditing by remember { mutableStateOf(false) }

    LaunchedEffect(streaming, finished) {
        if (streaming || !finished) {
            isEditing = false
        }
    }

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
        if (streamingText.isBlank() && !streaming && !finished && showEmptyPlaceholder) {
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
                    text = "AI 续写实时预览区",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = bodyTextColor
                )
                Text(
                    text = "在左侧选择细纲节点、目标章名与灵感预设后点击「开始续写」，生成正文将在此流式呈现并可直接编辑保存。",
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
                    if (finished && !streaming && streamingText.isNotBlank()) {
                        TextButton(
                            onClick = { isEditing = !isEditing },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = if (isEditing) "完成编辑" else "编辑正文",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
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

                if (isEditing && finished && !streaming) {
                    OutlinedTextField(
                        value = streamingText,
                        onValueChange = onStreamingTextChange,
                        label = { Text("编辑生成正文") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = bodyTextColor),
                        minLines = 8,
                        maxLines = 24,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (streamingText.isNotBlank()) {
                    SelectionContainer {
                        MarkdownText(
                            markdown = streamingText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = bodyTextColor,
                            secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (finished) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    GlassCapsuleButton(
                        text = "接着往下写",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isEditing = false
                            onContinueGenerating()
                        },
                        filled = false,
                        icon = Icons.Rounded.AutoAwesome,
                        state = hazeState,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "保存已生成内容",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = bodyTextColor
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "推荐章节名",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        SuggestTitleCapsuleButton(
                            label = "AI 拟定标题",
                            suggesting = suggestingTitles,
                            onClick = onSuggestTitles
                        )
                    }

                    if (!titleSuggestionError.isNullOrBlank()) {
                        Text(
                            text = titleSuggestionError,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (recommendedTitles.isNotEmpty()) {
                        RecommendedTitleChipsRow(
                            recommendedTitles = recommendedTitles,
                            selectedTitle = customTitle,
                            activeOutlineBeatTitle = activeOutlineBeat?.chapterTitle,
                            onSelectTitle = onApplyRecommendedTitle
                        )
                    }

                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = onCustomTitleChange,
                        label = { Text("章节标题（可自定义，留空自动命名）") },
                        placeholder = {
                            Text(
                                activeOutlineBeat?.chapterTitle?.takeIf { it.isNotBlank() }
                                    ?: "AI 续写 · ${parent?.title?.take(12) ?: "新章"}"
                            )
                        },
                        singleLine = true,
                        trailingIcon = if (customTitle.isNotBlank()) {
                            {
                                IconButton(onClick = { onCustomTitleChange("") }) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "清空标题",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (nextBeatAfterCurrent != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.09f),
                            border = BorderStroke(0.65.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "保存后将自动衔接下一节细纲：《${nextBeatAfterCurrent.chapterTitle}》",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = accentColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

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
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAppendToParent()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("追加到本章末", maxLines = 1) }
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
