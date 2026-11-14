package com.novelai.assistant.ui.assistant

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novelai.assistant.data.db.AiChatRecordEntity

object QuotePromptCodec {
    private const val QUOTE_PREFIX = "【划线选段】\n"
    private const val QUESTION_SEP = "\n\n【提问】\n"

    data class ParsedQuotePrompt(
        val quote: String,
        val question: String
    )

    fun encode(quote: String?, question: String): String {
        return if (quote.isNullOrBlank()) question
        else "$QUOTE_PREFIX$quote$QUESTION_SEP$question"
    }

    fun decode(content: String): ParsedQuotePrompt? {
        val normalized = content.replace("\r\n", "\n")
        if (normalized.startsWith(QUOTE_PREFIX)) {
            val parts = normalized.removePrefix(QUOTE_PREFIX).split(QUESTION_SEP, limit = 2)
            return ParsedQuotePrompt(
                quote = parts.getOrNull(0).orEmpty(),
                question = parts.getOrNull(1).orEmpty()
            )
        }
        if (normalized.contains("【提问】")) {
            val parts = normalized.split(QUESTION_SEP, limit = 2)
            if (parts.size == 2) {
                return ParsedQuotePrompt(quote = "", question = parts[1])
            }
        }
        return null
    }
}

@Composable
fun SpoilerFreeChip(
    spoilerFree: Boolean,
    onToggle: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    FilterChip(
        selected = spoilerFree,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggle()
        },
        label = {
            Text(
                text = if (spoilerFree) "不剧透" else "全书模式",
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge
            )
        },
        leadingIcon = {
            Icon(
                if (spoilerFree) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp)
            )
        },
        modifier = if (compact) modifier.height(32.dp) else modifier
    )
}

@Composable
fun ClearChatDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空聊天记录？") },
        text = { Text("确定要清空当前书籍的所有伴读问答记录吗？此操作无法撤销。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("清空", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun CompanionMessageList(
    bookId: String,
    chatRecords: List<AiChatRecordEntity>,
    streamingText: String,
    generating: Boolean,
    retrievedHint: String?,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    retrievedHintPrefix: String = "已参考：",
    emptyContent: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        if (chatRecords.isEmpty() && streamingText.isBlank() && !generating) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                emptyContent()
            }
        } else {
            retrievedHint?.let { hint ->
                Text(
                    text = "$retrievedHintPrefix$hint",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    record: AiChatRecordEntity,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isUser = record.role == "user"
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val parsedQuote = if (isUser) QuotePromptCodec.decode(record.content) else null

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            },
            modifier = Modifier
                .widthIn(max = 330.dp)
                .combinedClickable(
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val copyContent = parsedQuote?.question?.ifEmpty { record.content } ?: record.content
                        clipboardManager.setText(AnnotatedString(copyContent))
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    onClick = {}
                )
        ) {
            SelectionContainer {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (parsedQuote != null) {
                        if (parsedQuote.quote.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Text(
                                    text = "“${parsedQuote.quote}”",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                        if (parsedQuote.question.isNotBlank()) {
                            Text(
                                text = parsedQuote.question,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Text(
                            text = if (isStreaming) "${record.content} ▌" else record.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingBubble(
    text: String = "AI 正在思考并检索小说章节…",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 4.dp,
                bottomEnd = 18.dp
            ),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
