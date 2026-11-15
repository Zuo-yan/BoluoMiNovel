package com.novelai.assistant.ui.tree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterOriginType
import com.novelai.assistant.data.repository.BookRepository
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.AdaptiveListDetailPane
import com.novelai.assistant.ui.components.EmptyState
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassCircleButton
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
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class BranchTreeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    bookRepository: BookRepository
) : ViewModel() {
    private val bookId: String = savedStateHandle["bookId"] ?: ""

    val book: StateFlow<BookEntity?> = bookRepository.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chapters: StateFlow<List<ChapterEntity>> = bookRepository.observeChapters(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun BranchTreeScreen(
    bookId: String,
    onOpenChapter: (chapterIndex: Int) -> Unit,
    onContinueChapter: (chapterId: String, mode: String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    viewModel: BranchTreeViewModel = hiltViewModel()
) {
    val adaptive = LocalAdaptiveMetrics.current
    val hazeState = LocalGlassHazeState.current
    val haptics = LocalHapticFeedback.current

    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()

    val byId = remember(chapters) { chapters.associateBy { it.id } }
    fun depth(chapter: ChapterEntity): Int {
        var d = 0
        var cur = chapter
        while (cur.parentChapterId != null) {
            d++
            cur = byId[cur.parentChapterId] ?: break
            if (d > 20) break
        }
        return d
    }

    // 树形层级排序：主线章节依次展开，AI 续写章紧跟对应父节点下方
    val treeItems = remember(chapters) {
        com.novelai.assistant.data.repository.BookRepository.flattenChapterTree(chapters)
            .map { it to depth(it) }
    }

    var selectedChapterId by remember { mutableStateOf<String?>(null) }

    // 平板双栏模式下自动预选首个分支节点或当前阅读节点
    LaunchedEffect(adaptive.twoPaneEnabled, treeItems) {
        if (adaptive.twoPaneEnabled && (selectedChapterId == null || byId[selectedChapterId] == null)) {
            val preferred = treeItems.firstOrNull { it.first.originType != ChapterOriginType.ORIGINAL }?.first
                ?: treeItems.firstOrNull()?.first
            selectedChapterId = preferred?.id
        }
    }

    val selectedChapter = selectedChapterId?.let { byId[it] }

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
                            text = "章节衍生图谱",
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
                    if (chapters.isNotEmpty()) {
                        val aiCount = chapters.count { it.originType != ChapterOriginType.ORIGINAL }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = "${chapters.size} 章 · $aiCount 续写",
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
        if (chapters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.CallMerge,
                    title = "暂无章节",
                    hint = "在阅读页使用「AI 续写」后\n章节层级关系会在这里展示"
                )
            }
        } else if (adaptive.twoPaneEnabled) {
            AdaptiveListDetailPane(
                showDetail = selectedChapter != null,
                twoPaneEnabled = true,
                listFraction = 0.52f,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
                    .padding(horizontal = adaptive.horizontalPadding, vertical = 8.dp),
                listPane = {
                    BranchTreeList(
                        treeItems = treeItems,
                        selectedChapterId = selectedChapterId,
                        hazeState = hazeState,
                        showQuickBranchButton = false,
                        onNodeClick = { chapter ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedChapterId = chapter.id
                        },
                        onContinueChapter = onContinueChapter
                    )
                },
                detailPane = {
                    BranchNodeInspectorPane(
                        chapter = selectedChapter,
                        parentChapter = selectedChapter?.parentChapterId?.let { byId[it] },
                        hazeState = hazeState,
                        onSelectParent = { parentId -> selectedChapterId = parentId },
                        onOpenChapter = onOpenChapter,
                        onContinueChapter = onContinueChapter
                    )
                }
            )
        } else {
            AdaptiveCenteredContainer(
                maxWidth = adaptive.maxFormWidth,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
            ) {
                BranchTreeList(
                    treeItems = treeItems,
                    selectedChapterId = null,
                    hazeState = hazeState,
                    showQuickBranchButton = true,
                    contentPadding = PaddingValues(
                        horizontal = adaptive.horizontalPadding,
                        vertical = 8.dp
                    ),
                    onNodeClick = { chapter -> onOpenChapter(chapter.chapterIndex) },
                    onContinueChapter = onContinueChapter
                )
            }
        }
    }
}

@Composable
private fun BranchTreeList(
    treeItems: List<Pair<ChapterEntity, Int>>,
    selectedChapterId: String?,
    hazeState: HazeState?,
    showQuickBranchButton: Boolean,
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp),
    onNodeClick: (ChapterEntity) -> Unit,
    onContinueChapter: (chapterId: String, mode: String) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(treeItems, key = { _, pair -> pair.first.id }) { _, (chapter, d) ->
            val isContinuation = chapter.originType == ChapterOriginType.AI_CONTINUATION
            val isSelected = selectedChapterId == chapter.id

            val nodeTint = when {
                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.24f else 0.14f)
                isContinuation -> NovelColors.BadgeAi.copy(alpha = if (isDark) 0.14f else 0.08f)
                else -> null
            }
            val borderModifier = if (isSelected) {
                Modifier.border(
                    width = 1.2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(18.dp)
                )
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (d * 20).dp)
            ) {
                LiquidGlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(borderModifier)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onNodeClick(chapter) },
                    state = hazeState,
                    cornerRadius = 18.dp,
                    widgetSize = GlassWidgetSize.SMALL,
                    sceneType = GlassSceneType.GENERAL,
                    interactive = false,
                    skipRenderLayer = true,
                    tint = nodeTint,
                    shadowElevation = if (isSelected) 8.dp else 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(Modifier.size(width = 14.dp, height = 22.dp)) {
                            if (d > 0) {
                                drawLine(
                                    color = NovelColors.BadgeAi,
                                    start = Offset(0f, size.height / 2),
                                    end = Offset(size.width - 4f, size.height / 2),
                                    strokeWidth = 3f
                                )
                            }
                        }
                        OriginBadge(chapter.originType.name)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!chapter.promptUsed.isNullOrBlank()) {
                                Text(
                                    text = "指令：${chapter.promptUsed}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (showQuickBranchButton) {
                            TextButton(
                                onClick = { onContinueChapter(chapter.id, "CONTINUATION") },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "+ 续写",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchNodeInspectorPane(
    chapter: ChapterEntity?,
    parentChapter: ChapterEntity?,
    hazeState: HazeState?,
    onSelectParent: (String) -> Unit,
    onOpenChapter: (chapterIndex: Int) -> Unit,
    onContinueChapter: (chapterId: String, mode: String) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText

    if (chapter == null) {
        LiquidGlassBox(
            modifier = Modifier.fillMaxSize(),
            state = hazeState,
            cornerRadius = 24.dp,
            widgetSize = GlassWidgetSize.BIG,
            sceneType = GlassSceneType.GENERAL,
            interactive = false,
            skipRenderLayer = true
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.CallMerge,
                    title = "选择章节节点查看详情",
                    hint = "点击左侧任意节点即可预览指令、摘录正文或从此章继续续写"
                )
            }
        }
        return
    }

    val originLabel = when (chapter.originType) {
        ChapterOriginType.AI_CONTINUATION -> "AI 续写章节"
        ChapterOriginType.ORIGINAL -> "原作章节"
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OriginBadge(chapter.originType.name)
                    Text(
                        text = "第 ${chapter.chapterIndex + 1} 章 · $originLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )

                if (parentChapter != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectParent(parentChapter.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.CallMerge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "续写自：${parentChapter.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    GlassCapsuleButton(
                        text = "打开阅读",
                        onClick = { onOpenChapter(chapter.chapterIndex) },
                        filled = true,
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        state = hazeState,
                        modifier = Modifier.weight(1f)
                    )
                    GlassCapsuleButton(
                        text = "从此章续写",
                        onClick = { onContinueChapter(chapter.id, "CONTINUATION") },
                        filled = false,
                        icon = Icons.Rounded.AutoAwesome,
                        state = hazeState,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (!chapter.promptUsed.isNullOrBlank()) {
            GlassSectionCard(
                title = "生成指令 / 设定",
                state = hazeState,
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(
                    text = chapter.promptUsed,
                    style = MaterialTheme.typography.bodyMedium,
                    color = primaryText
                )
            }
        }

        GlassSectionCard(
            title = "正文摘录 · ${chapter.content.length} 字",
            state = hazeState,
            contentPadding = PaddingValues(16.dp)
        ) {
            if (chapter.content.isBlank()) {
                Text(
                    text = "该章节暂无正文缓存，点击上方「打开阅读」可即时加载。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = chapter.content.take(1400) + if (chapter.content.length > 1400) "\n\n……" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = primaryText
                )
            }
        }
    }
}
