package com.novelai.assistant.ui.reader

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novelai.assistant.data.sf.SfParagraphComment
import com.novelai.assistant.ui.components.AdaptiveGlassSheet
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.LocalGlassHazeState
import com.novelai.assistant.ui.components.SfAvatar
import com.novelai.assistant.ui.theme.NovelColors
import com.novelai.assistant.ui.theme.ReaderPalette

/**
 * 平板双栏 Supporting Pane 模式的右侧液态玻璃间贴面板（与左侧正文并排对照阅读）。
 */
@Composable
internal fun ParagraphCommentsSupportingPane(
    comments: List<SfParagraphComment>,
    loading: Boolean,
    loadingMore: Boolean,
    error: String?,
    sort: String,
    paragraph: String,
    chapterTitle: String,
    expandedReplies: Set<String>,
    replyThreads: Map<String, ReplyThread>,
    isEffectiveDark: Boolean,
    palette: ReaderPalette,
    statusBarPad: Dp,
    navBarPad: Dp,
    onRetryReplies: (String) -> Unit,
    onSort: (String) -> Unit,
    onLoadMore: () -> Unit,
    onToggleReplies: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val hazeState = LocalGlassHazeState.current
    val paneTint = if (isEffectiveDark) {
        Color(0xFF151620).copy(alpha = 0.86f)
    } else {
        Color(palette.chrome).copy(alpha = 0.88f)
    }

    LiquidGlassBox(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = statusBarPad + 12.dp, bottom = navBarPad + 12.dp, end = 12.dp),
        state = hazeState,
        tint = paneTint,
        cornerRadius = 28.dp,
        widgetSize = GlassWidgetSize.BIG,
        sceneType = GlassSceneType.GENERAL,
        interactive = false,
        skipRenderLayer = true,
        shadowElevation = 14.dp
    ) {
        ParagraphCommentsContent(
            comments = comments,
            loading = loading,
            loadingMore = loadingMore,
            error = error,
            sort = sort,
            paragraph = paragraph,
            chapterTitle = chapterTitle,
            expandedReplies = expandedReplies,
            replyThreads = replyThreads,
            isEffectiveDark = isEffectiveDark,
            onRetryReplies = onRetryReplies,
            onSort = onSort,
            onLoadMore = onLoadMore,
            onToggleReplies = onToggleReplies,
            onRetry = onRetry,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 14.dp, bottom = 6.dp)
        )
    }
}

/**
 * 紧凑/窄屏模式下的自适应玻璃间贴弹窗/底板。
 */
@Composable
internal fun ParagraphCommentsSheet(
    comments: List<SfParagraphComment>,
    loading: Boolean,
    loadingMore: Boolean,
    error: String?,
    sort: String,
    paragraph: String,
    chapterTitle: String,
    expandedReplies: Set<String>,
    replyThreads: Map<String, ReplyThread>,
    isEffectiveDark: Boolean,
    onRetryReplies: (String) -> Unit,
    onSort: (String) -> Unit,
    onLoadMore: () -> Unit,
    onToggleReplies: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    AdaptiveGlassSheet(
        onDismissRequest = onDismiss,
        maxWidth = adaptive.maxDialogSheetWidth,
        contentPadding = PaddingValues(top = 10.dp, bottom = 8.dp)
    ) {
        ParagraphCommentsContent(
            comments = comments,
            loading = loading,
            loadingMore = loadingMore,
            error = error,
            sort = sort,
            paragraph = paragraph,
            chapterTitle = chapterTitle,
            expandedReplies = expandedReplies,
            replyThreads = replyThreads,
            isEffectiveDark = isEffectiveDark,
            onRetryReplies = onRetryReplies,
            onSort = onSort,
            onLoadMore = onLoadMore,
            onToggleReplies = onToggleReplies,
            onRetry = onRetry,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 340.dp, max = 640.dp)
        )
    }
}

@Composable
private fun ParagraphCommentsContent(
    comments: List<SfParagraphComment>,
    loading: Boolean,
    loadingMore: Boolean,
    error: String?,
    sort: String,
    paragraph: String,
    chapterTitle: String,
    expandedReplies: Set<String>,
    replyThreads: Map<String, ReplyThread>,
    isEffectiveDark: Boolean,
    onRetryReplies: (String) -> Unit,
    onSort: (String) -> Unit,
    onLoadMore: () -> Unit,
    onToggleReplies: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryText = if (isEffectiveDark) NovelColors.GlassOnDarkText else MaterialTheme.colorScheme.onSurface
    val secondaryText = if (isEffectiveDark) Color(0xFFA8A8B6) else MaterialTheme.colorScheme.onSurfaceVariant
    val mutedText = if (isEffectiveDark) Color(0xFF8C8C9B) else MaterialTheme.colorScheme.outline
    val accent = if (isEffectiveDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary
    val cardBg = if (isEffectiveDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.045f)
    val cardBorder = if (isEffectiveDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
    val dividerColor = if (isEffectiveDark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Column(modifier = modifier) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "本章间贴",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText
                    )
                    if (chapterTitle.isNotBlank()) {
                        Text(
                            chapterTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onDismiss) {
                    Icon(Icons.Rounded.Close, "关闭", tint = secondaryText)
                }
            }
            // 引文与排序并排：引文占剩余宽度，排序贴右下，避免整行留白
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                if (paragraph.isNotBlank()) {
                    Surface(
                        color = cardBg,
                        border = BorderStroke(0.6.dp, cardBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .height(14.dp)
                                    .background(
                                        accent.copy(alpha = 0.72f),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                            Text(
                                paragraph,
                                maxLines = 2,
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryText,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else Spacer(Modifier.weight(1f))
                Surface(
                    color = cardBg,
                    border = BorderStroke(0.6.dp, cardBorder),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(Modifier.padding(3.dp)) {
                        listOf("hot" to "最热", "new" to "最新").forEach { (value, label) ->
                            val selected = sort == value
                            Surface(
                                color = if (selected) accent.copy(alpha = if (isEffectiveDark) 0.28f else 0.16f)
                                else Color.Transparent,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.clickable {
                                    if (!selected) onSort(value)
                                }
                            ) {
                                Text(
                                    label,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) accent else secondaryText
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp), color = dividerColor)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp))
        if (error != null) {
            Text(error, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
            TextButton(onRetry, Modifier.padding(horizontal = 12.dp)) {
                Text("重试", color = accent)
            }
        } else if (!loading && comments.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "本章暂无间贴",
                    color = secondaryText
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(comments, key = { _, comment -> comment.id }) { index, comment ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        SfAvatar(comment.avatar, comment.displayName, Modifier.size(36.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    comment.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryText
                                )
                                if (comment.roleName.isNotBlank()) {
                                    Text(
                                        comment.roleName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent
                                    )
                                }
                            }
                            Text(
                                comment.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = primaryText
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    comment.createTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mutedText
                                )
                                if (comment.replyNum > 0 || comment.replies.isNotEmpty()) {
                                    TextButton(
                                        onClick = { onToggleReplies(comment.id) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.heightIn(min = 36.dp)
                                    ) {
                                        Text(
                                            if (comment.id in expandedReplies) "收起回复"
                                            else "查看回复（${comment.replyNum}）",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accent
                                        )
                                    }
                                } else {
                                    Text(
                                        "赞 ${comment.favCount}" + if (comment.replyNum > 0) " · 回复 ${comment.replyNum}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = mutedText
                                    )
                                }
                            }
                            if (comment.id in expandedReplies) {
                                val thread = replyThreads[comment.id] ?: ReplyThread()
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp, top = 2.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (thread.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                    thread.error?.let { message ->
                                        Text(message, color = MaterialTheme.colorScheme.error)
                                        TextButton({ onRetryReplies(comment.id) }) {
                                            Text("重试回复", color = accent)
                                        }
                                    }
                                    thread.replies.forEach { reply ->
                                        Surface(
                                            color = cardBg,
                                            border = BorderStroke(0.6.dp, cardBorder),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                SfAvatar(reply.avatar, reply.displayName, Modifier.size(26.dp))
                                                Column(
                                                    Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            reply.displayName,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Medium,
                                                            color = primaryText
                                                        )
                                                        if (reply.roleName.isNotBlank()) {
                                                            Text(
                                                                reply.roleName,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = accent
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        reply.content,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = primaryText
                                                    )
                                                    Text(
                                                        reply.createTime,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = mutedText
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (thread.loaded) {
                                        Text(
                                            "已显示 ${thread.replies.size} 条可见回复",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = secondaryText
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (index < comments.lastIndex) {
                        HorizontalDivider(color = dividerColor)
                    }
                }
                if (comments.size >= 20) {
                    item("load-more") {
                        TextButton(
                            onClick = onLoadMore,
                            enabled = !loadingMore,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (loadingMore) "正在加载更多…" else "加载更多间贴",
                                color = accent
                            )
                        }
                    }
                }
            }
        }
    }
}
