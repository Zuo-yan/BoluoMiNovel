package com.novelai.assistant.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelai.assistant.data.sf.SfParagraphComment
import com.novelai.assistant.ui.components.SfAvatar

@OptIn(ExperimentalMaterial3Api::class)
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
    onRetryReplies: (String) -> Unit,
    onSort: (String) -> Unit,
    onLoadMore: () -> Unit,
    onToggleReplies: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 680.dp)) {
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
                        Text("本章间贴", style = MaterialTheme.typography.titleLarge)
                        if (chapterTitle.isNotBlank()) {
                            Text(
                                chapterTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
                }
                // 引文与排序并排：引文占剩余宽度，排序贴右下，避免整行留白
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (paragraph.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                            MaterialTheme.colorScheme.primary.copy(alpha = .55f),
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                                Text(
                                    paragraph,
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else Spacer(Modifier.weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(Modifier.padding(3.dp)) {
                            listOf("hot" to "最热", "new" to "最新").forEach { (value, label) ->
                                val selected = sort == value
                                Surface(
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
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
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 6.dp))
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp))
            if (error != null) {
                Text(error, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
                TextButton(onRetry, Modifier.padding(horizontal = 12.dp)) { Text("重试") }
            } else if (!loading && comments.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "本章暂无间贴",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
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
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (comment.roleName.isNotBlank()) {
                                        Text(
                                            comment.roleName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(comment.content, style = MaterialTheme.typography.bodyMedium)
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        comment.createTime,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
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
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    } else {
                                        Text(
                                            "赞 ${comment.favCount}" + if (comment.replyNum > 0) " · 回复 ${comment.replyNum}" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
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
                                            TextButton({ onRetryReplies(comment.id) }) { Text("重试回复") }
                                        }
                                        thread.replies.forEach { reply ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                            if (reply.roleName.isNotBlank()) {
                                                                Text(
                                                                    reply.roleName,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            reply.content,
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                        Text(
                                                            reply.createTime,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.outline
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (thread.loaded) {
                                            Text(
                                                "已显示 ${thread.replies.size} 条可见回复",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (index < comments.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                        }
                    }
                    if (comments.size >= 20) {
                        item("load-more") {
                            TextButton(
                                onClick = onLoadMore,
                                enabled = !loadingMore,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (loadingMore) "正在加载更多…" else "加载更多间贴")
                            }
                        }
                    }
                }
            }
        }
    }
}
