package com.novelai.assistant.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.ui.components.OriginBadge
import com.novelai.assistant.ui.components.SegmentedControl
import com.novelai.assistant.ui.theme.NovelAITheme
import com.novelai.assistant.ui.theme.NovelColors
import com.novelai.assistant.ui.theme.ReaderPalette

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BoxScope.ReaderTocDrawer(
    visible: Boolean,
    bgColor: Color,
    palette: ReaderPalette,
    isEffectiveDark: Boolean,
    accent: Color,
    statusBarPad: Dp,
    navBarPad: Dp,
    totalChapters: Int,
    tocFilter: TocFilter,
    tocItems: List<TocItem>,
    currentChapterId: String?,
    tocListState: LazyListState,
    onDismiss: () -> Unit,
    onSelectFilter: (TocFilter) -> Unit,
    onSelectChapter: (ChapterEntity) -> Unit,
    onLongClickChapter: (ChapterEntity) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.matchParentSize()
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
            AnimatedVisibility(
                visible = true,
                enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { -it },
                exit = slideOutHorizontally { -it },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Surface(
                    color = bgColor,
                    contentColor = Color(palette.text),
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.84f)
                ) {
                    Column(Modifier.padding(top = statusBarPad + 8.dp)) {
                        Text(
                            "目录 · $totalChapters 章",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isEffectiveDark) Color.White else Color(palette.text),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                        SegmentedControl(
                            options = TocFilter.entries.toList(),
                            selected = tocFilter,
                            labelOf = { it.label },
                            onSelect = onSelectFilter,
                            containerColor = if (isEffectiveDark) Color(0xFF22222E) else Color(palette.chrome).copy(alpha = 0.6f),
                            activeColor = if (isEffectiveDark) Color(0xFF323242) else Color.White,
                            selectedTextColor = if (isEffectiveDark) Color.White else Color(palette.text),
                            unselectedTextColor = if (isEffectiveDark) Color(0xFFA0A0AC) else Color(palette.secondaryText),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        LazyColumn(state = tocListState, modifier = Modifier.weight(1f)) {
                            itemsIndexed(tocItems, key = { _, item -> item.chapter.id }) { _, item ->
                                val isCurrent = item.chapter.id == currentChapterId
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isCurrent) accent.copy(alpha = 0.16f)
                                            else Color.Transparent
                                        )
                                        .combinedClickable(
                                            onClick = { onSelectChapter(item.chapter) },
                                            onLongClick = { onLongClickChapter(item.chapter) }
                                        )
                                        .padding(horizontal = 20.dp, vertical = 11.dp)
                                        .padding(start = (item.depth * 16).dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OriginBadge(item.chapter.originType.name)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        item.chapter.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCurrent) accent
                                        else if (isEffectiveDark) Color(0xFFDCDCE4)
                                        else Color(palette.text),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(navBarPad))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterActionSheet(
    chapter: ChapterEntity,
    isEffectiveDark: Boolean,
    onDismiss: () -> Unit,
    onRename: (ChapterEntity) -> Unit,
    onDelete: (ChapterEntity) -> Unit
) {
    NovelAITheme(darkTheme = isEffectiveDark) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = if (isEffectiveDark) Color(0xFF181820) else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (isEffectiveDark) Color(0xFFE8E8EE) else MaterialTheme.colorScheme.onSurface,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = if (isEffectiveDark) Color(0xFF5A5A66) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    OriginBadge(chapter.originType.name)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        chapter.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isEffectiveDark) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isEffectiveDark) Color(0xFF252532) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRename(chapter) }
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = if (isEffectiveDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "重命名章节",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isEffectiveDark) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isEffectiveDark) Color(0xFF361820) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDelete(chapter) }
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = if (isEffectiveDark) Color(0xFFFF6B81) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "删除章节",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isEffectiveDark) Color(0xFFFF6B81) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RenameChapterDialog(
    draft: String,
    isEffectiveDark: Boolean,
    onDraftChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    NovelAITheme(darkTheme = isEffectiveDark) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = if (isEffectiveDark) Color(0xFF20202A) else MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = if (isEffectiveDark) Color.White else MaterialTheme.colorScheme.onSurface,
            textContentColor = if (isEffectiveDark) Color(0xFFD0D0D8) else MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("重命名章节") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = { Text("章节名称") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = if (isEffectiveDark) Color.White else MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = if (isEffectiveDark) Color.White else MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = if (isEffectiveDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (isEffectiveDark) Color(0xFF4A4A58) else MaterialTheme.colorScheme.outline,
                        focusedLabelColor = if (isEffectiveDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = if (isEffectiveDark) Color(0xFFA0A0AC) else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    enabled = draft.isNotBlank()
                ) {
                    Text(
                        "保存",
                        color = if (draft.isNotBlank()) {
                            if (isEffectiveDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary
                        } else Color.Gray
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = if (isEffectiveDark) Color(0xFFA0A0AC) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
internal fun DeleteChapterConfirmDialog(
    chapterTitle: String,
    isEffectiveDark: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    NovelAITheme(darkTheme = isEffectiveDark) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = if (isEffectiveDark) Color(0xFF20202A) else MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = if (isEffectiveDark) Color.White else MaterialTheme.colorScheme.onSurface,
            textContentColor = if (isEffectiveDark) Color(0xFFD0D0D8) else MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("删除章节《$chapterTitle》？") },
            text = { Text("该章节内容将被永久删除，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = if (isEffectiveDark) Color(0xFFA0A0AC) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}
