package com.novelai.assistant.ui.assistant

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
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
import com.novelai.assistant.ui.components.AdaptiveGlassDialog
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.LocalGlassHazeState
import com.novelai.assistant.ui.theme.NovelColors
import dev.chrisbanes.haze.HazeState

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
    val adaptive = LocalAdaptiveMetrics.current
    AdaptiveGlassDialog(
        onDismissRequest = onDismiss,
        maxWidth = adaptive.maxDialogSheetWidth
    ) {
        Text(
            "清空聊天记录？",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "确定要清空当前书籍的所有伴读问答记录吗？此操作无法撤销。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onConfirm) {
                Text("清空", color = MaterialTheme.colorScheme.error)
            }
        }
    }
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
    maxBubbleWidth: Dp = if (LocalAdaptiveMetrics.current.twoPaneEnabled) 520.dp else 330.dp,
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
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 2.dp)
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
                    ChatBubble(record = record, maxBubbleWidth = maxBubbleWidth)
                }
                if (generating && streamingText.isBlank()) {
                    item(key = "thinking_indicator") {
                        ThinkingBubble(maxBubbleWidth = maxBubbleWidth.coerceAtMost(420.dp))
                    }
                }
                if (streamingText.isNotBlank()) {
                    item(key = "streaming_bubble") {
                        ChatBubble(
                            record = AiChatRecordEntity(
                                id = "streaming",
                                bookId = bookId,
                                chapterId = null,
                                role = "assistant",
                                content = streamingText
                            ),
                            isStreaming = true,
                            maxBubbleWidth = maxBubbleWidth
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
    maxBubbleWidth: Dp = if (LocalAdaptiveMetrics.current.twoPaneEnabled) 520.dp else 330.dp,
    modifier: Modifier = Modifier
) {
    val isUser = record.role == "user"
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val parsedQuote = if (isUser) QuotePromptCodec.decode(record.content) else null

    val bubbleBg = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else if (isDark) {
        Color.White.copy(alpha = 0.09f)
    } else {
        Color.White.copy(alpha = 0.78f)
    }
    val bubbleBorder = if (isUser) {
        null
    } else if (isDark) {
        BorderStroke(0.7.dp, Color.White.copy(alpha = 0.14f))
    } else {
        BorderStroke(0.7.dp, Color.Black.copy(alpha = 0.07f))
    }
    val assistantTextColor = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText

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
            color = bubbleBg,
            border = bubbleBorder,
            modifier = Modifier
                .widthIn(max = maxBubbleWidth)
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
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary else assistantTextColor
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
    maxBubbleWidth: Dp = if (LocalAdaptiveMetrics.current.twoPaneEnabled) 420.dp else 310.dp,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
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
            color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.72f),
            border = BorderStroke(0.7.dp, if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.07f)),
            modifier = Modifier.widthIn(max = maxBubbleWidth)
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

/**
 * 悬浮液态玻璃问答输入胶囊（供 AssistantScreen 与 ReaderCompanionScreen 共用）。
 */
@Composable
fun GlassChatInputCapsule(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    generating: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalGlassHazeState.current
) {
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val inputTextColor = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText

    LiquidGlassBox(
        modifier = modifier.fillMaxWidth(),
        state = hazeState,
        cornerRadius = 26.dp,
        widgetSize = GlassWidgetSize.SMALL,
        sceneType = GlassSceneType.IMMERSIVE,
        interactive = true,
        shadowElevation = 10.dp
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
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
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = inputTextColor),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    maxLines = 4,
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
                        innerTextField()
                    }
                )

                if (generating) {
                    IconButton(
                        onClick = onStop,
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
                    IconButton(
                        onClick = {
                            if (canSend) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSend()
                            }
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
