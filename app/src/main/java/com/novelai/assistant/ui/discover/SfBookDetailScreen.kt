package com.novelai.assistant.ui.discover

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.sf.SfBookDetail
import com.novelai.assistant.data.sf.SfComment
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.AdaptiveTwoPane
import com.novelai.assistant.ui.components.AsyncCover
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassCircleButton
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.SfAvatar
import com.novelai.assistant.ui.components.formatChineseCount
import com.novelai.assistant.ui.theme.NovelColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SfBookDetailScreen(
    bookId: String,
    onBack: () -> Unit,
    onOpenReader: (String, Int) -> Unit,
    onOpenCatalog: (String) -> Unit,
    onOpenRelatedLists: (String) -> Unit,
    onOpenAuthor: (String, String) -> Unit,
    viewModel: SfBookDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(bookId) {
        viewModel.loadBookDetail(bookId)
        viewModel.loadComments(bookId)
    }

    val detail by viewModel.detailBook.collectAsStateWithLifecycle()
    val isDetailLoading by viewModel.isDetailLoading.collectAsStateWithLifecycle()
    val detailError by viewModel.detailError.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val isCommentsLoading by viewModel.isCommentsLoading.collectAsStateWithLifecycle()
    val isCommentsLoadingMore by viewModel.isCommentsLoadingMore.collectAsStateWithLifecycle()
    val commentsHasMore by viewModel.commentsHasMore.collectAsStateWithLifecycle()
    val commentsError by viewModel.commentsError.collectAsStateWithLifecycle()
    val localBook by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val cloudMsg by viewModel.cloudShelfMsg.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(cloudMsg) {
        cloudMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearCloudShelfMsg()
        }
    }

    var introExpanded by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }

    val rawId = bookId.removePrefix("sf:")
    val isSfBook = bookId.startsWith("sf:") || bookId.matches(Regex("\\d+"))
    val targetBookId = if (isSfBook) "sf:$rawId" else (localBook?.id ?: bookId)
    val title = detail?.title?.ifBlank { localBook?.title } ?: localBook?.title ?: "书籍详情"
    val author = detail?.author?.ifBlank { localBook?.author } ?: localBook?.author ?: ""
    val cover = detail?.cover ?: localBook?.coverUri

    val adaptive = LocalAdaptiveMetrics.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val accentText = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val openReaderAction = {
        val readingIndex = localBook?.currentReadingChapterIndex ?: 0
        onOpenReader(targetBookId, readingIndex)
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            AdaptiveCenteredContainer(
                maxWidth = adaptive.maxContentWidth,
                horizontalPadding = adaptive.horizontalPadding
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCircleButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSfBook) {
                        GlassCircleButton(
                            icon = Icons.Rounded.CloudUpload,
                            contentDescription = "加入菠萝包云端书架",
                            onClick = { if (!isDetailLoading) viewModel.addToCloudShelf(rawId) },
                            tint = if (isDetailLoading) secondaryText.copy(alpha = 0.45f) else primaryText
                        )
                    }
                    GlassCircleButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "刷新",
                        onClick = { if (!isDetailLoading) viewModel.loadBookDetail(bookId) },
                        tint = if (isDetailLoading) secondaryText.copy(alpha = 0.45f) else primaryText
                    )
                }
            }

            if (isDetailLoading && detail == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    GlassSectionCard(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(28.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "正在获取书籍完整信息...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryText
                            )
                        }
                    }
                }
            } else if (adaptive.twoPaneEnabled) {
                AdaptiveCenteredContainer(
                    maxWidth = adaptive.maxContentWidth,
                    horizontalPadding = adaptive.horizontalPadding,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AdaptiveTwoPane(
                        splitEnabled = true,
                        primaryFraction = 0.40f,
                        primaryPane = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 6.dp, bottom = navBottom + 20.dp)
                            ) {
                                LiquidGlassBox(
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 26.dp,
                                    widgetSize = GlassWidgetSize.BIG,
                                    sceneType = GlassSceneType.GENERAL,
                                    interactive = false,
                                    skipRenderLayer = true,
                                    shadowElevation = 10.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState())
                                            .padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        SfBookHeader(
                                            title = title,
                                            author = author,
                                            cover = cover,
                                            isSfBook = isSfBook,
                                            detail = detail,
                                            fallbackChapterCount = chapters.size,
                                            onOpenAuthor = { onOpenAuthor(rawId, author) }
                                        )

                                        val tags = detail?.tags.orEmpty()
                                        if (tags.isNotEmpty()) {
                                            SfBookTagsRow(tags = tags)
                                        }

                                        val progress = localBook?.readingProgressPercent?.toInt() ?: 0
                                        val readingIndex = localBook?.currentReadingChapterIndex ?: 0
                                        val inShelf = localBook?.inBookshelf == true

                                        GlassCapsuleButton(
                                            text = if (progress > 0) "继续阅读 (第${readingIndex + 1}章)" else "开始阅读",
                                            onClick = openReaderAction,
                                            filled = true,
                                            icon = Icons.Rounded.PlayArrow,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            GlassCapsuleButton(
                                                text = if (inShelf) "已在书架" else "加入书架",
                                                onClick = { viewModel.toggleBookshelf(targetBookId) },
                                                filled = false,
                                                icon = if (inShelf) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                                                modifier = Modifier.weight(1.1f)
                                            )
                                            GlassCapsuleButton(
                                                text = "下载",
                                                onClick = { showTools = true },
                                                filled = false,
                                                icon = Icons.Rounded.Download,
                                                modifier = Modifier.weight(0.95f)
                                            )
                                            GlassCapsuleButton(
                                                text = "目录",
                                                onClick = { onOpenCatalog(targetBookId) },
                                                filled = false,
                                                icon = Icons.Rounded.Menu,
                                                modifier = Modifier.weight(0.95f)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        secondaryPane = {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 6.dp, bottom = navBottom + 24.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                detailError?.let { error ->
                                    item {
                                        SfDetailErrorCard(
                                            error = error,
                                            onRetry = { viewModel.loadBookDetail(bookId) }
                                        )
                                    }
                                }

                                item {
                                    SfBookMetricsCard(detail = detail)
                                }

                                val introText = detail?.intro?.ifBlank { "暂无简介内容" } ?: "暂无简介内容"
                                item {
                                    SfBookIntroCard(
                                        introText = introText,
                                        expanded = introExpanded,
                                        onToggle = { introExpanded = !introExpanded }
                                    )
                                }

                                item {
                                    SfBookCatalogPreviewCard(
                                        latestChapterTitle = detail?.latestChapterTitle?.ifBlank { chapters.lastOrNull()?.title },
                                        chapterCount = chapters.size,
                                        onClick = { onOpenCatalog(targetBookId) }
                                    )
                                }

                                if (isSfBook) {
                                    item {
                                        GlassCapsuleButton(
                                            text = "看看收录这部作品的相关书单",
                                            onClick = { onOpenRelatedLists(bookId) },
                                            filled = false,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    item {
                                        SfBookCommentsSection(
                                            comments = comments,
                                            isLoading = isCommentsLoading,
                                            isLoadingMore = isCommentsLoadingMore,
                                            hasMore = commentsHasMore,
                                            error = commentsError,
                                            onRetryInitial = { viewModel.loadComments(bookId) },
                                            onLoadMore = { viewModel.loadMoreComments() }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = adaptive.horizontalPadding,
                            end = adaptive.horizontalPadding,
                            top = 6.dp,
                            bottom = navBottom + 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        detailError?.let { error ->
                            item {
                                SfDetailErrorCard(
                                    error = error,
                                    onRetry = { viewModel.loadBookDetail(bookId) }
                                )
                            }
                        }

                        item {
                            GlassSectionCard(contentPadding = PaddingValues(16.dp)) {
                                SfBookHeader(
                                    title = title,
                                    author = author,
                                    cover = cover,
                                    isSfBook = isSfBook,
                                    detail = detail,
                                    fallbackChapterCount = chapters.size,
                                    onOpenAuthor = { onOpenAuthor(rawId, author) }
                                )
                            }
                        }

                        item {
                            SfBookMetricsCard(detail = detail)
                        }

                        val tags = detail?.tags.orEmpty()
                        if (tags.isNotEmpty()) {
                            item {
                                SfBookTagsRow(tags = tags)
                            }
                        }

                        val introText = detail?.intro?.ifBlank { "暂无简介内容" } ?: "暂无简介内容"
                        item {
                            SfBookIntroCard(
                                introText = introText,
                                expanded = introExpanded,
                                onToggle = { introExpanded = !introExpanded }
                            )
                        }

                        item {
                            SfBookCatalogPreviewCard(
                                latestChapterTitle = detail?.latestChapterTitle?.ifBlank { chapters.lastOrNull()?.title },
                                chapterCount = chapters.size,
                                onClick = { onOpenCatalog(targetBookId) }
                            )
                        }

                        if (isSfBook) {
                            item {
                                GlassCapsuleButton(
                                    text = "看看收录这部作品的相关书单",
                                    onClick = { onOpenRelatedLists(bookId) },
                                    filled = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            item {
                                SfBookCommentsSection(
                                    comments = comments,
                                    isLoading = isCommentsLoading,
                                    isLoadingMore = isCommentsLoadingMore,
                                    hasMore = commentsHasMore,
                                    error = commentsError,
                                    onRetryInitial = { viewModel.loadComments(bookId) },
                                    onLoadMore = { viewModel.loadMoreComments() }
                                )
                            }
                        }
                    }

                    SfBookDetailBottomBar(
                        localBook = localBook,
                        onToggleBookshelf = { viewModel.toggleBookshelf(targetBookId) },
                        onShowTools = { showTools = true },
                        onOpenCatalog = { onOpenCatalog(targetBookId) },
                        onOpenReader = openReaderAction,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    if (showTools) {
        SfBookToolsDialog(
            bookId = targetBookId,
            currentChapterIndex = localBook?.currentReadingChapterIndex ?: 0,
            onDismiss = { showTools = false }
        )
    }
}

private fun formatWordCount(words: Long): String =
    if (words >= 10_000L) "%.1f万字".format(words / 10_000.0) else "$words 字"

@Composable
private fun SfDetailErrorCard(
    error: String,
    onRetry: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    LiquidGlassBox(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        widgetSize = GlassWidgetSize.SMALL,
        sceneType = GlassSceneType.GENERAL,
        interactive = false,
        skipRenderLayer = true,
        tint = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.45f else 0.65f),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun SfBookHeader(
    title: String,
    author: String,
    cover: String?,
    isSfBook: Boolean,
    detail: SfBookDetail?,
    fallbackChapterCount: Int,
    onOpenAuthor: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val accentText = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    val pillBg = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AsyncCover(
            title = title,
            uri = cover,
            modifier = Modifier
                .width(112.dp)
                .height(156.dp),
            shape = RoundedCornerShape(14.dp),
            maxDimension = 512
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (author.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.12f))
                        .clickable(enabled = isSfBook, onClick = onOpenAuthor)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "作者：$author",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accentText,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isSfBook) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "查看作者作品集",
                                tint = accentText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (detail?.isFinish == true) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
                            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (detail?.isFinish == true) "已完结" else "连载中",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryText
                    )
                }
                if (!detail?.typeName.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(pillBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = detail?.typeName.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryText
                        )
                    }
                }
            }
            val wordsText = formatWordCount(detail?.charCount ?: 0L)
            val chapterCount = if ((detail?.totalChapters ?: 0) > 0) detail?.totalChapters else fallbackChapterCount
            Text(
                text = "$wordsText · 共 $chapterCount 章",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText
            )

            val mk = detail?.markCount ?: 0L
            val vt = detail?.viewTimes ?: 0L
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (mk > 0L) {
                    val markSuffix = if (mk >= 10_000L) "+" else ""
                    Text(
                        text = "${formatChineseCount(mk)}$markSuffix 收藏",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = accentText
                    )
                }
                if (vt > 0L) {
                    Text(
                        text = "${formatChineseCount(vt)} 人气",
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryText
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SfBookTagsRow(tags: List<String>) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.72f)
    val border = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
    val textColor = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        tags.forEach { tag ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(0.7.dp, border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun SfBookMetricsCard(detail: SfBookDetail?) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant

    GlassSectionCard(contentPadding = PaddingValues(vertical = 14.dp, horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatWordCount(detail?.charCount ?: 0L),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )
                Text(
                    text = if (detail?.isFinish == true) "完结" else "连载",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryText
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val tickets = detail?.ticketCount ?: 0
                Text(
                    text = "$tickets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )
                Text("月票", style = MaterialTheme.typography.labelSmall, color = secondaryText)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val favs = detail?.favCount ?: 0L
                Text(
                    text = if (favs > 0) "$favs" else "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )
                Text("点赞", style = MaterialTheme.typography.labelSmall, color = secondaryText)
            }
        }
    }
}

@Composable
private fun SfBookIntroCard(
    introText: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val accentText = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary

    GlassSectionCard(
        title = "作品简介",
        actionLabel = if (introText.length > 80) {
            if (expanded) "收起" else "展开"
        } else null,
        onActionClick = if (introText.length > 80) onToggle else null,
        contentPadding = PaddingValues(16.dp)
    ) {
        Text(
            text = introText,
            style = MaterialTheme.typography.bodyMedium,
            color = primaryText.copy(alpha = 0.88f),
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
        )
        if (introText.length > 80) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (expanded) "收起简介" else "展开完整简介",
                style = MaterialTheme.typography.labelMedium,
                color = accentText,
                modifier = Modifier.clickable(onClick = onToggle)
            )
        }
    }
}

@Composable
private fun SfBookCatalogPreviewCard(
    latestChapterTitle: String?,
    chapterCount: Int,
    onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val accentText = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary

    GlassSectionCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.FormatListBulleted, contentDescription = null, tint = accentText)
            Column(Modifier.weight(1f)) {
                Text(
                    text = "章节目录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText
                )
                Text(
                    text = if (!latestChapterTitle.isNullOrBlank()) "最新：$latestChapterTitle" else "共 $chapterCount 章",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "查看完整目录", tint = secondaryText)
        }
    }
}

@Composable
private fun SfBookCommentsSection(
    comments: List<SfComment>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    onRetryInitial: () -> Unit,
    onLoadMore: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val accentText = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    val itemBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)

    GlassSectionCard(
        title = "读者书评" + if (comments.isNotEmpty()) " (${comments.size}${if (hasMore) "+" else ""})" else "",
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("正在读取读者书评…", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                }
            }
            if (comments.isNotEmpty()) {
                comments.forEach { c ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(itemBg)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SfAvatar(c.avatar, c.displayName, Modifier.size(24.dp))
                                Text(
                                    text = c.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryText
                                )
                                if (c.vipLevel > 0) {
                                    Text(
                                        text = "VIP ${c.vipLevel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accentText
                                    )
                                }
                            }
                            Text(
                                text = c.createTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryText
                            )
                        }
                        Text(
                            text = c.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = primaryText
                        )
                        if (c.favCount > 0 || c.replyNum > 0 || c.isStick) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (c.isStick) {
                                    Text("置顶", style = MaterialTheme.typography.labelSmall, color = accentText)
                                }
                                if (c.favCount > 0) {
                                    Text("有用 ${c.favCount}", style = MaterialTheme.typography.labelSmall, color = secondaryText)
                                }
                                if (c.replyNum > 0) {
                                    Text("回复 ${c.replyNum}", style = MaterialTheme.typography.labelSmall, color = secondaryText)
                                }
                            }
                        }
                    }
                }
                if (error != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onLoadMore) { Text("重试加载更多") }
                    }
                } else if (hasMore) {
                    GlassCapsuleButton(
                        text = if (isLoadingMore) "正在加载历史书评…" else "加载更多历史书评",
                        onClick = onLoadMore,
                        filled = false,
                        enabled = !isLoadingMore,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "已显示全部可读取书评",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryText,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            } else if (!isLoading) {
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    GlassCapsuleButton(text = "重试", onClick = onRetryInitial, filled = false)
                } else {
                    Text("暂无书评内容", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                }
            }
        }
    }
}

@Composable
private fun SfBookDetailBottomBar(
    localBook: BookEntity?,
    onToggleBookshelf: () -> Unit,
    onShowTools: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenReader: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val accentText = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    val inShelf = localBook?.inBookshelf == true
    val progress = localBook?.readingProgressPercent?.toInt() ?: 0
    val readingIndex = localBook?.currentReadingChapterIndex ?: 0

    LiquidGlassBox(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        cornerRadius = 28.dp,
        widgetSize = GlassWidgetSize.SMALL,
        sceneType = GlassSceneType.IMMERSIVE,
        interactive = true,
        shadowElevation = 14.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassCircleButton(
                icon = if (inShelf) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                contentDescription = if (inShelf) "已在书架" else "加入书架",
                onClick = onToggleBookshelf,
                size = 42.dp,
                tint = if (inShelf) accentText else primaryText
            )
            GlassCircleButton(
                icon = Icons.Rounded.Download,
                contentDescription = "下载与导出",
                onClick = onShowTools,
                size = 42.dp,
                tint = primaryText
            )
            GlassCircleButton(
                icon = Icons.Rounded.Menu,
                contentDescription = "章节目录",
                onClick = onOpenCatalog,
                size = 42.dp,
                tint = primaryText
            )
            GlassCapsuleButton(
                text = if (progress > 0) "继续(${readingIndex + 1}章)" else "开始阅读",
                onClick = onOpenReader,
                filled = true,
                icon = Icons.Rounded.PlayArrow,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
