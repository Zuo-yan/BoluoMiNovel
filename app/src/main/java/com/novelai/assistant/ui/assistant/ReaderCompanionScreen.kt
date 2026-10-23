package com.novelai.assistant.ui.assistant

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import com.novelai.assistant.ui.components.AmbientBackdrop
import com.novelai.assistant.ui.components.liquidGlass
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.db.AiChatRecordEntity
import com.novelai.assistant.domain.PromptBuilder

@Composable
fun ReaderCompanionScreen(
    bookId: String,
    quote: String,
    action: String = "",
    onBack: () -> Unit,
    viewModel: AssistantViewModel = hiltViewModel()
) {
    val currentBook by viewModel.currentBook.collectAsStateWithLifecycle()
    val currentChapter by viewModel.currentChapter.collectAsStateWithLifecycle()
    val chatRecords by viewModel.chatRecords.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val spoilerFree by viewModel.spoilerFree.collectAsStateWithLifecycle()
    val retrievedHint by viewModel.retrievedHint.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var isQuoteExpanded by remember { mutableStateOf(false) }
    var confirmClearChat by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // 初始化：绑定当前小说与选段，若有 action 则自动触发提问
    LaunchedEffect(bookId, quote, action) {
        viewModel.initializeForQuote(bookId, quote, action)
    }

    // 新回复产生或正在打字时自动平滑滚动到底部
    LaunchedEffect(chatRecords.size, streamingText, generating) {
        val count = chatRecords.size + (if (generating || streamingText.isNotBlank()) 1 else 0)
        if (count > 0) {
            listState.animateScrollToItem((count - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ---------- 沉浸式顶栏：返回 + 当前小说/章节 + 防剧透开关 + 清空记录 ----------
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回阅读",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = currentBook?.title?.takeIf { it.isNotBlank() } ?: "选段伴读",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentChapter?.title?.let { "章节：$it" } ?: "深度伴读与解析",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 不剧透模式胶囊切换
                FilterChip(
                    selected = spoilerFree,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.toggleSpoilerFree()
                    },
                    label = {
                        Text(
                            if (spoilerFree) "不剧透" else "全书模式",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (spoilerFree) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    modifier = Modifier.height(32.dp)
                )

                IconButton(onClick = { confirmClearChat = true }) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "清空对话记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ---------- 划选文本展示卡片 ----------
        if (quote.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .animateContentSize()
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.FormatQuote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "当前选段",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(quote))
                                Toast.makeText(context, "选段已复制", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("复制", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = quote,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (isQuoteExpanded) 20 else 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (quote.length > 70) {
                        Text(
                            text = if (isQuoteExpanded) "收起" else "展开全文",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { isQuoteExpanded = !isQuoteExpanded }
                                .padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // ---------- 快捷指令选项条 ----------
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            val quickActions = listOf(
                Triple("AI吐槽", Icons.Rounded.AutoAwesome, Color(0xFFE91E63)),
                Triple("解析深意", Icons.Rounded.AutoAwesome, Color(0xFF6750A4)),
                Triple("情绪总结", Icons.Rounded.Tune, Color(0xFF625B71)),
                Triple("剧情伏笔", Icons.Rounded.AutoAwesome, Color(0xFF009688)),
                Triple("人物心理", Icons.Rounded.Tune, Color(0xFFFF9800))
            )
            items(quickActions) { (label, icon, color) ->
                FilterChip(
                    selected = false,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val prompt = when (label) {
                            "AI吐槽" -> PromptBuilder.QUICK_ROAST
                            "解析深意" -> PromptBuilder.QUICK_ANALYZE
                            "情绪总结" -> PromptBuilder.QUICK_EMOTION
                            "剧情伏笔" -> "请结合上下文，分析这段文字中可能隐藏的伏笔与情节走向暗示。"
                            "人物心理" -> "请深度剖析这段文字中出场人物的微妙心理反应与潜台词。"
                            else -> label
                        }
                        viewModel.send(prompt, customQuote = quote.takeIf { it.isNotBlank() })
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                    },
                    modifier = Modifier.height(30.dp)
                )
            }
        }

        // ---------- 参考章节提示 ----------
        retrievedHint?.let { hint ->
            Text(
                text = "已参考相关章节：$hint",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }

        // ---------- 对话消息列表 ----------
        Box(modifier = Modifier.weight(1f)) {
            if (chatRecords.isEmpty() && streamingText.isBlank() && !generating) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "选择上方快捷指令或直接追问",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
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
                                    id = "streaming",
                                    bookId = bookId,
                                    chapterId = null,
                                    role = "assistant",
                                    content = streamingText
                                ),
                                isStreaming = true
                            )
                        }
                    }
                }
            }
        }

        // ---------- 错误提示 ----------
        error?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        // ---------- 底部追问输入框（无底部导航栏） ----------
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                if (generating) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = {
                            Text(
                                if (generating) "AI 正在思考与回答中…" else "追问这段文字或这本书…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp)
                    )

                    if (generating) {
                        IconButton(
                            onClick = { viewModel.stopGenerating() },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Icon(
                                Icons.Rounded.Stop,
                                contentDescription = "停止",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        val canSend = input.isNotBlank()
                        IconButton(
                            onClick = {
                                if (canSend) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.send(input, customQuote = quote.takeIf { it.isNotBlank() })
                                    input = ""
                                }
                            },
                            enabled = canSend,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (canSend) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        ) {
                            Icon(
                                Icons.Rounded.Send,
                                contentDescription = "发送",
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
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
            title = { Text("清空对话记录？") },
            text = { Text("确定要清空本书的所有伴读问答记录吗？此操作无法撤销。") },
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
