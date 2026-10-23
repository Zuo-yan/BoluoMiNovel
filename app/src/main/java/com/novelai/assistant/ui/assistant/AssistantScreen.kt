package com.novelai.assistant.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.novelai.assistant.ui.components.liquidGlass
import dev.chrisbanes.haze.HazeState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.db.AiChatRecordEntity
import com.novelai.assistant.domain.PromptBuilder
import com.novelai.assistant.ui.components.EmptyState
import com.novelai.assistant.ui.components.LargeTitleHeader

@Composable
fun AssistantScreen(
    bookIdArg: String = "",
    quoteArg: String = "",
    viewModel: AssistantViewModel = hiltViewModel(),
    hazeState: HazeState? = null
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val currentBookId by viewModel.currentBookId.collectAsStateWithLifecycle()
    val chatRecords by viewModel.chatRecords.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val pendingQuote by viewModel.pendingQuote.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val spoilerFree by viewModel.spoilerFree.collectAsStateWithLifecycle()
    val retrievedHint by viewModel.retrievedHint.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
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

    var confirmClearChat by remember { mutableStateOf(false) }

    Column(
        Modifier
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

        // 主界面允许在书架的书籍之间横向切换
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 20.dp)
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
                FilterChip(
                    selected = spoilerFree,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.toggleSpoilerFree()
                    },
                    label = { Text(if (spoilerFree) "不剧透" else "全书模式") },
                    leadingIcon = {
                        Icon(
                            if (spoilerFree) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            modifier = Modifier.height(16.dp)
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 引文卡片（若有）
        pendingQuote?.let { quote ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        quote,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearQuote() }, modifier = Modifier.height(24.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "移除引文", Modifier.height(16.dp))
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                listOf(
                    "AI 解析深意" to PromptBuilder.QUICK_ANALYZE,
                    "AI 情绪总结" to PromptBuilder.QUICK_EMOTION,
                    "AI 吐槽" to PromptBuilder.QUICK_ROAST
                ).forEach { (label, prompt) ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.send(prompt)
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        // 消息列表
        if (chatRecords.isEmpty() && streamingText.isBlank()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "和 AI 一起读这本书",
                        hint = if (currentBookId.isBlank()) "先在书架导入一本书"
                        else "AI 会自动检索相关章节作答\n或在阅读页长按段落「划线即问」"
                    )
                    if (currentBookId.isNotBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.send("帮我回忆一下主角到目前为止的关键经历") },
                                label = { Text("情节回忆") }
                            )
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.send("分析本书主要人物的生平脉络与人物关系") },
                                label = { Text("人物分析") }
                            )
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.send("分析目前剧情的主线冲突与伏笔") },
                                label = { Text("剧情深读") }
                            )
                        }
                    }
                }
            }
        } else {
            retrievedHint?.let { hint ->
                Text(
                    "已参考：$hint",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(chatRecords, key = { _, r -> r.id }) { _, record ->
                    ChatBubble(record)
                }
                if (generating && streamingText.isBlank()) {
                    item(key = "thinking_indicator") {
                        ThinkingBubble()
                    }
                }
                if (streamingText.isNotBlank()) {
                    item(key = "streaming_bubble") {
                        ChatBubble(
                            AiChatRecordEntity(
                                id = "streaming", bookId = "", chapterId = null,
                                role = "assistant", content = streamingText
                            ),
                            isStreaming = true
                        )
                    }
                }
            }
        }

        // 错误提示
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val isImeVisible = imeBottom > 0.dp
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val inputBottomPadding = if (isImeVisible) 8.dp else (68.dp + navBarBottom * 0.5f)

        // 浮动液态玻璃输入岛：与底部胶囊导航栏保持 14dp 边距和圆角曲率完全同构统一
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = inputBottomPadding + 4.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.20f)
                )
                .liquidGlass(
                    state = hazeState,
                    shape = RoundedCornerShape(26.dp),
                    blurRadius = 26.dp,
                    enableFlowSheen = generating
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column {
                if (generating) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        maxLines = 4,
                        decorationBox = { innerTextField ->
                            if (input.isEmpty()) {
                                Text(
                                    text = if (generating) "AI 正在思考与回答中…" else "问问这本书…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f)
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (generating) {
                        IconButton(
                            onClick = { viewModel.stopGenerating() },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Icon(
                                Icons.Rounded.Stop,
                                contentDescription = "停止",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        val canSend = input.isNotBlank() || pendingQuote != null
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.send(input)
                                input = ""
                            },
                            enabled = canSend,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                        ) {
                            Icon(
                                Icons.Rounded.Send,
                                contentDescription = "发送",
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClearChat) {
        AlertDialog(
            onDismissRequest = { confirmClearChat = false },
            title = { Text("清空聊天记录？") },
            text = { Text("确定要清空当前书籍的所有伴读问答记录吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearChat = false
                        viewModel.clearChat()
                    }
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearChat = false }) {
                    Text("取消")
                }
            }
        )
    }
}
