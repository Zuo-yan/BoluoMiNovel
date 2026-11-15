package com.novelai.assistant.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import com.novelai.assistant.data.db.BookmarkEntity
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.ui.components.AdaptiveGlassDialog
import com.novelai.assistant.ui.components.AdaptiveGlassSheet
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.LocalGlassHazeState
import com.novelai.assistant.ui.components.OriginBadge
import com.novelai.assistant.ui.components.SegmentedControl
import com.novelai.assistant.ui.theme.NovelAITheme
import com.novelai.assistant.ui.theme.NovelColors
import com.novelai.assistant.ui.theme.ReaderPalette

@Suppress("UNUSED_PARAMETER")
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
    drawerTab: ReaderDrawerTab,
    tocFilter: TocFilter,
    tocItems: List<TocItem>,
    bookmarks: List<BookmarkEntity>,
    isCurrentBookmarked: Boolean,
    currentChapterId: String?,
    tocListState: LazyListState,
    onDismiss: () -> Unit,
    onSelectDrawerTab: (ReaderDrawerTab) -> Unit,
    onSelectFilter: (TocFilter) -> Unit,
    onSelectChapter: (ChapterEntity) -> Unit,
    onLongClickChapter: (ChapterEntity) -> Unit,
    onToggleCurrentBookmark: () -> Unit,
    onSelectBookmark: (BookmarkEntity) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    val isTwoPane = adaptive.twoPaneEnabled
    val hazeState = LocalGlassHazeState.current
    val primaryText = if (isEffectiveDark) NovelColors.GlassOnDarkText else Color(palette.text)
    val secondaryText = if (isEffectiveDark) Color(0xFFA8A8B6) else Color(palette.secondaryText)
    val drawerTint = if (isEffectiveDark) {
        Color(0xFF14151E).copy(alpha = 0.80f)
    } else {
        Color(palette.chrome).copy(alpha = 0.84f)
    }
    val drawerShape = if (isTwoPane) {
        RoundedCornerShape(28.dp)
    } else {
        RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    }

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
                    .background(Color.Black.copy(alpha = if (isTwoPane) 0.16f else 0.35f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
            AnimatedVisibility(
                visible = true,
                enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { -it },
                exit = slideOutHorizontally { -it },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                val panelModifier = if (isTwoPane) {
                    Modifier
                        .fillMaxHeight()
                        .padding(start = 16.dp, top = statusBarPad + 12.dp, bottom = navBarPad + 12.dp)
                        .fillMaxWidth(0.36f)
                        .widthIn(min = 320.dp, max = adaptive.tocDrawerMaxWidth)
                } else {
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.84f)
                        .widthIn(min = 320.dp, max = adaptive.tocDrawerMaxWidth)
                }

                LiquidGlassBox(
                    modifier = panelModifier,
                    state = hazeState,
                    tint = drawerTint,
                    shape = drawerShape,
                    cornerRadius = 28.dp,
                    widgetSize = GlassWidgetSize.BIG,
                    sceneType = GlassSceneType.IMMERSIVE,
                    interactive = false,
                    skipRenderLayer = true,
                    shadowElevation = 18.dp
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(top = if (isTwoPane) 14.dp else statusBarPad + 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (drawerTab == ReaderDrawerTab.TOC) "目录 · $totalChapters 章"
                                else "书签 · ${bookmarks.size} 条",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "关闭",
                                    tint = secondaryText
                                )
                            }
                        }
                        val segContainer = if (isEffectiveDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                        val segActive = if (isEffectiveDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.90f)
                        SegmentedControl(
                            options = ReaderDrawerTab.entries.toList(),
                            selected = drawerTab,
                            labelOf = { tab ->
                                when (tab) {
                                    ReaderDrawerTab.TOC -> "目录 ($totalChapters)"
                                    ReaderDrawerTab.BOOKMARKS -> "书签 (${bookmarks.size})"
                                }
                            },
                            onSelect = onSelectDrawerTab,
                            containerColor = segContainer,
                            activeColor = segActive,
                            selectedTextColor = primaryText,
                            unselectedTextColor = secondaryText,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        if (drawerTab == ReaderDrawerTab.TOC) {
                            SegmentedControl(
                                options = TocFilter.entries.toList(),
                                selected = tocFilter,
                                labelOf = { it.label },
                                onSelect = onSelectFilter,
                                containerColor = segContainer,
                                activeColor = segActive,
                                selectedTextColor = primaryText,
                                unselectedTextColor = secondaryText,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(
                                state = tocListState,
                                contentPadding = PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 4.dp
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                itemsIndexed(tocItems, key = { _, item -> item.chapter.id }) { _, item ->
                                    val isCurrent = item.chapter.id == currentChapterId
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (isCurrent) accent.copy(alpha = 0.18f)
                                                else Color.Transparent
                                            )
                                            .combinedClickable(
                                                onClick = { onSelectChapter(item.chapter) },
                                                onLongClick = { onLongClickChapter(item.chapter) }
                                            )
                                            .padding(horizontal = 12.dp, vertical = 11.dp)
                                            .padding(start = (item.depth * 16).dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OriginBadge(item.chapter.originType.name)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            item.chapter.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isCurrent) accent else primaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        } else {
                            // 书签快捷添加/移除按钮
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = accent.copy(alpha = if (isCurrentBookmarked) 0.18f else 0.12f),
                                border = BorderStroke(0.7.dp, accent.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(onClick = onToggleCurrentBookmark)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isCurrentBookmarked) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isCurrentBookmarked) "当前页已添加书签（点击移除）" else "添加当前页为书签",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accent
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (bookmarks.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Bookmarks,
                                            contentDescription = null,
                                            tint = secondaryText.copy(alpha = 0.55f),
                                            modifier = Modifier.size(34.dp)
                                        )
                                        Text(
                                            "暂无书签",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = primaryText
                                        )
                                        Text(
                                            "点击上方按钮，或点右上角「更多」随时记录当前阅读位置",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryText
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(bookmarks, key = { it.id }) { bm ->
                                        val isSameChapter = bm.chapterId == currentChapterId
                                        val cardBg = if (isEffectiveDark) {
                                            Color.White.copy(alpha = if (isSameChapter) 0.10f else 0.06f)
                                        } else {
                                            Color.Black.copy(alpha = if (isSameChapter) 0.06f else 0.035f)
                                        }
                                        val cardBorder = if (isSameChapter) {
                                            accent.copy(alpha = 0.35f)
                                        } else if (isEffectiveDark) {
                                            Color.White.copy(alpha = 0.10f)
                                        } else {
                                            Color.Black.copy(alpha = 0.06f)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = cardBg,
                                            border = BorderStroke(0.6.dp, cardBorder),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { onSelectBookmark(bm) }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = bm.chapterTitle,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isSameChapter) accent else primaryText,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = ReaderBookmarkHelper.formatBookmarkTime(bm.createdAt),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = secondaryText
                                                    )
                                                    IconButton(
                                                        onClick = { onDeleteBookmark(bm) },
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.DeleteOutline,
                                                            contentDescription = "删除书签",
                                                            tint = secondaryText,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                if (bm.excerpt.isNotBlank()) {
                                                    Text(
                                                        text = bm.excerpt,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = secondaryText,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(end = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (!isTwoPane) {
                            Spacer(Modifier.height(navBarPad))
                        } else {
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChapterActionSheet(
    chapter: ChapterEntity,
    isEffectiveDark: Boolean,
    onDismiss: () -> Unit,
    onRename: (ChapterEntity) -> Unit,
    onDelete: (ChapterEntity) -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    val primaryText = if (isEffectiveDark) NovelColors.GlassOnDarkText else MaterialTheme.colorScheme.onSurface
    val cardBg = if (isEffectiveDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.045f)
    val cardBorder = if (isEffectiveDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
    val deleteTint = if (isEffectiveDark) Color(0xFFFF6B81) else MaterialTheme.colorScheme.error

    NovelAITheme(darkTheme = isEffectiveDark) {
        AdaptiveGlassSheet(
            onDismissRequest = onDismiss,
            maxWidth = adaptive.maxDialogSheetWidth,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column(
                Modifier.fillMaxWidth(),
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
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(0.7.dp, cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
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
                            color = primaryText
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isEffectiveDark) Color(0xFF3A1A24).copy(alpha = 0.72f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    border = BorderStroke(0.7.dp, deleteTint.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onDelete(chapter) }
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = deleteTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "删除章节",
                            style = MaterialTheme.typography.bodyLarge,
                            color = deleteTint
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
    val primaryText = if (isEffectiveDark) NovelColors.GlassOnDarkText else MaterialTheme.colorScheme.onSurface
    val secondaryText = if (isEffectiveDark) Color(0xFFA8A8B6) else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = if (isEffectiveDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary

    NovelAITheme(darkTheme = isEffectiveDark) {
        AdaptiveGlassDialog(onDismissRequest = onDismiss) {
            Text(
                "重命名章节",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = primaryText
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = { Text("章节名称") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = primaryText,
                    unfocusedTextColor = primaryText,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = if (isEffectiveDark) Color(0xFF4A4A58) else MaterialTheme.colorScheme.outline,
                    focusedLabelColor = accentColor,
                    unfocusedLabelColor = secondaryText
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = secondaryText)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onConfirm,
                    enabled = draft.isNotBlank()
                ) {
                    Text(
                        "保存",
                        fontWeight = FontWeight.SemiBold,
                        color = if (draft.isNotBlank()) accentColor else secondaryText.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DeleteChapterConfirmDialog(
    chapterTitle: String,
    isEffectiveDark: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val primaryText = if (isEffectiveDark) NovelColors.GlassOnDarkText else MaterialTheme.colorScheme.onSurface
    val secondaryText = if (isEffectiveDark) Color(0xFFA8A8B6) else MaterialTheme.colorScheme.onSurfaceVariant
    val errorColor = if (isEffectiveDark) Color(0xFFFF6B81) else MaterialTheme.colorScheme.error

    NovelAITheme(darkTheme = isEffectiveDark) {
        AdaptiveGlassDialog(onDismissRequest = onDismiss) {
            Text(
                "删除章节《$chapterTitle》？",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = primaryText
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "该章节内容将被永久删除，此操作无法撤销。",
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryText
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = secondaryText)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onConfirm) {
                    Text("删除", fontWeight = FontWeight.SemiBold, color = errorColor)
                }
            }
        }
    }
}
