package com.novelai.assistant.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.EmptyState
import com.novelai.assistant.ui.components.GlassCircleButton
import com.novelai.assistant.ui.components.GlassFilterPill
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LargeTitleHeader
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.LocalGlassHazeState
import com.novelai.assistant.ui.components.adaptiveContentPadding
import com.novelai.assistant.ui.components.bottomBarContentPadding
import com.novelai.assistant.ui.theme.NovelColors
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    bookIdArg: String = "",
    quoteArg: String = "",
    viewModel: AssistantViewModel = hiltViewModel(),
    hazeState: HazeState? = null
) {
    val adaptive = LocalAdaptiveMetrics.current
    val resolvedHazeState = hazeState ?: LocalGlassHazeState.current

    val books by viewModel.books.collectAsStateWithLifecycle()
    val currentBookId by viewModel.currentBookId.collectAsStateWithLifecycle()
    val currentBook by viewModel.currentBook.collectAsStateWithLifecycle()
    val currentChapter by viewModel.currentChapter.collectAsStateWithLifecycle()
    val chatRecords by viewModel.chatRecords.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val pendingQuote by viewModel.pendingQuote.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val spoilerFree by viewModel.spoilerFree.collectAsStateWithLifecycle()
    val retrievedHint by viewModel.retrievedHint.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var confirmClearChat by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(bookIdArg, quoteArg) {
        if (bookIdArg.isNotBlank()) viewModel.selectBook(bookIdArg)
        if (quoteArg.isNotBlank()) viewModel.setPendingQuote(quoteArg)
    }

    LaunchedEffect(books) {
        if (currentBookId.isBlank() && books.isNotEmpty()) {
            viewModel.selectBook(books.first().id)
        }
    }

    LaunchedEffect(chatRecords.size, streamingText, generating) {
        val count = chatRecords.size + (if (generating || streamingText.isNotBlank()) 1 else 0)
        if (count > 0) listState.animateScrollToItem((count - 1).coerceAtLeast(0))
    }

    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val isImeVisible = imeBottom > 0.dp
    val inputBottomPadding = if (isImeVisible) 8.dp else bottomBarContentPadding(4.dp)

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        if (adaptive.twoPaneEnabled) {
            val contentPad = adaptiveContentPadding(top = 8.dp, bottomExtra = 12.dp)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .padding(
                        start = adaptive.horizontalPadding,
                        end = adaptive.horizontalPadding,
                        top = contentPad.calculateTopPadding()
                    ),
                horizontalArrangement = Arrangement.spacedBy(adaptive.paneSpacing)
            ) {
                // 左侧玻璃侧栏（280.dp–320.dp）：书架选书卡片 + 防剧透开关 + 快捷提问胶囊
                AssistantSidebarPane(
                    books = books,
                    currentBookId = currentBookId,
                    spoilerFree = spoilerFree,
                    canClearChat = chatRecords.isNotEmpty(),
                    hazeState = resolvedHazeState,
                    bottomPadding = bottomBarContentPadding(12.dp),
                    onSelectBook = { bookId ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.selectBook(bookId)
                    },
                    onToggleSpoilerFree = { viewModel.toggleSpoilerFree() },
                    onQuickPrompt = { prompt -> viewModel.send(prompt) },
                    onRequestClearChat = { confirmClearChat = true },
                    modifier = Modifier
                        .width(304.dp)
                        .fillMaxHeight()
                )

                // 右侧主对话流（居中限宽 780.dp，气泡最宽 520.dp + 底部悬浮输入胶囊）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    AdaptiveCenteredContainer(
                        maxWidth = 780.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 精简上下文顶部玻璃条
                            LiquidGlassBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                state = resolvedHazeState,
                                cornerRadius = 20.dp,
                                widgetSize = GlassWidgetSize.SMALL,
                                sceneType = GlassSceneType.GENERAL,
                                interactive = false,
                                skipRenderLayer = true,
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.MenuBook,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = currentBook?.title ?: "未选择书籍",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = currentChapter?.title?.let { "当前章节：$it" }
                                                ?: if (spoilerFree) "不剧透伴读 · 仅检索已读章节" else "全书回忆 · 检索全书内容",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (chatRecords.isNotEmpty()) {
                                        GlassCircleButton(
                                            icon = Icons.Rounded.DeleteOutline,
                                            contentDescription = "清空聊天记录",
                                            onClick = { confirmClearChat = true },
                                            size = 36.dp,
                                            state = resolvedHazeState
                                        )
                                    }
                                }
                            }

                            PendingQuoteSection(
                                pendingQuote = pendingQuote,
                                hazeState = resolvedHazeState,
                                horizontalPadding = 0.dp,
                                onClearQuote = { viewModel.clearQuote() },
                                onAction = { action ->
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.sendAction(action)
                                }
                            )

                            CompanionMessageList(
                                bookId = currentBookId,
                                chatRecords = chatRecords,
                                streamingText = streamingText,
                                generating = generating,
                                retrievedHint = retrievedHint,
                                listState = listState,
                                horizontalPadding = 4.dp,
                                maxBubbleWidth = 520.dp,
                                modifier = Modifier.weight(1f)
                            ) {
                                AssistantEmptyPlaceholder(
                                    hasBook = currentBookId.isNotBlank(),
                                    onQuickPrompt = { viewModel.send(it) }
                                )
                            }

                            error?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            GlassChatInputCapsule(
                                value = input,
                                onValueChange = { input = it },
                                placeholder = if (generating) "AI 正在思考与回答中…" else "问问这本书…",
                                generating = generating,
                                canSend = input.isNotBlank() || pendingQuote != null,
                                onSend = {
                                    viewModel.send(input)
                                    input = ""
                                },
                                onStop = { viewModel.stopGenerating() },
                                hazeState = resolvedHazeState,
                                modifier = Modifier.padding(bottom = inputBottomPadding)
                            )
                        }
                    }
                }
            }
        } else {
            // 手机紧凑单栏布局
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
            ) {
                LargeTitleHeader(
                    title = "AI 助手",
                    subtitle = "伴读 · 划线即问 · 全书回忆",
                    actions = {
                        if (chatRecords.isNotEmpty()) {
                            IconButton(onClick = { confirmClearChat = true }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "清空聊天记录",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = adaptive.horizontalPadding)
                ) {
                    items(books, key = { it.id }) { book ->
                        FilterChip(
                            selected = book.id == currentBookId,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.selectBook(book.id)
                            },
                            label = {
                                Text(
                                    book.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 140.dp)
                                )
                            }
                        )
                    }
                    item {
                        SpoilerFreeChip(
                            spoilerFree = spoilerFree,
                            onToggle = { viewModel.toggleSpoilerFree() }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                PendingQuoteSection(
                    pendingQuote = pendingQuote,
                    hazeState = resolvedHazeState,
                    horizontalPadding = adaptive.horizontalPadding,
                    onClearQuote = { viewModel.clearQuote() },
                    onAction = { action ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.sendAction(action)
                    }
                )

                CompanionMessageList(
                    bookId = currentBookId,
                    chatRecords = chatRecords,
                    streamingText = streamingText,
                    generating = generating,
                    retrievedHint = retrievedHint,
                    listState = listState,
                    horizontalPadding = adaptive.horizontalPadding,
                    maxBubbleWidth = 330.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    AssistantEmptyPlaceholder(
                        hasBook = currentBookId.isNotBlank(),
                        onQuickPrompt = { viewModel.send(it) }
                    )
                }

                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = adaptive.horizontalPadding, vertical = 4.dp)
                    )
                }

                GlassChatInputCapsule(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = if (generating) "AI 正在思考与回答中…" else "问问这本书…",
                    generating = generating,
                    canSend = input.isNotBlank() || pendingQuote != null,
                    onSend = {
                        viewModel.send(input)
                        input = ""
                    },
                    onStop = { viewModel.stopGenerating() },
                    hazeState = resolvedHazeState,
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .padding(bottom = inputBottomPadding + 4.dp)
                )
            }
        }
    }

    if (confirmClearChat) {
        ClearChatDialog(
            onDismiss = { confirmClearChat = false },
            onConfirm = {
                confirmClearChat = false
                viewModel.clearChat()
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantSidebarPane(
    books: List<BookEntity>,
    currentBookId: String,
    spoilerFree: Boolean,
    canClearChat: Boolean,
    hazeState: HazeState?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onSelectBook: (String) -> Unit,
    onToggleSpoilerFree: () -> Unit,
    onQuickPrompt: (String) -> Unit,
    onRequestClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        modifier = modifier.padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "AI 助手",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "伴读 · 划线即问 · 全书回忆",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canClearChat) {
                GlassCircleButton(
                    icon = Icons.Rounded.DeleteOutline,
                    contentDescription = "清空聊天记录",
                    onClick = onRequestClearChat,
                    size = 38.dp,
                    state = hazeState
                )
            }
        }

        // 防剧透模式 + 快捷提问胶囊
        GlassSectionCard(
            title = "伴读策略与快捷提问",
            state = hazeState,
            contentPadding = PaddingValues(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (spoilerFree) "仅参考已读章节" else "检索全书章节",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SpoilerFreeChip(
                        spoilerFree = spoilerFree,
                        onToggle = onToggleSpoilerFree,
                        compact = true
                    )
                }
                if (currentBookId.isNotBlank()) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GlassFilterPill(
                            label = "情节回忆",
                            selected = false,
                            onClick = { onQuickPrompt("帮我回忆一下主角到目前为止的关键经历") }
                        )
                        GlassFilterPill(
                            label = "人物分析",
                            selected = false,
                            onClick = { onQuickPrompt("分析本书主要人物的生平脉络与人物关系") }
                        )
                        GlassFilterPill(
                            label = "剧情深读",
                            selected = false,
                            onClick = { onQuickPrompt("分析目前剧情的主线冲突与伏笔") }
                        )
                    }
                }
            }
        }

        // 书架书籍选择卡片列表
        GlassSectionCard(
            title = "选择伴读书籍 (${books.size})",
            state = hazeState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp)
        ) {
            if (books.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "书架暂无书籍",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(books, key = { it.id }) { book ->
                        val selected = book.id == currentBookId
                        val cardBg = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.24f else 0.14f)
                        } else {
                            Color.Transparent
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                            RoundedCornerShape(14.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { onSelectBook(book.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) {
                                    if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${book.author.ifBlank { "未知作者" }} · 已读 ${(book.readingProgressPercent * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingQuoteSection(
    pendingQuote: String?,
    hazeState: HazeState?,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onClearQuote: () -> Unit,
    onAction: (String) -> Unit
) {
    if (pendingQuote == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = pendingQuote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClearQuote, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "移除引文", modifier = Modifier.size(16.dp))
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("AI 解析深意", "AI 情绪总结", "AI 吐槽").forEach { action ->
                GlassFilterPill(
                    label = action,
                    selected = false,
                    onClick = { onAction(action) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantEmptyPlaceholder(
    hasBook: Boolean,
    onQuickPrompt: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(
            icon = Icons.Rounded.AutoAwesome,
            title = "和 AI 一起读这本书",
            hint = if (!hasBook) "先在书架导入一本书"
            else "AI 会自动检索相关章节作答\n或在阅读页长按段落「划线即问」"
        )
        if (hasBook) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp)
            ) {
                GlassFilterPill(
                    label = "情节回忆",
                    selected = false,
                    onClick = { onQuickPrompt("帮我回忆一下主角到目前为止的关键经历") }
                )
                GlassFilterPill(
                    label = "人物分析",
                    selected = false,
                    onClick = { onQuickPrompt("分析本书主要人物的生平脉络与人物关系") }
                )
                GlassFilterPill(
                    label = "剧情深读",
                    selected = false,
                    onClick = { onQuickPrompt("分析目前剧情的主线冲突与伏笔") }
                )
            }
        }
    }
}
