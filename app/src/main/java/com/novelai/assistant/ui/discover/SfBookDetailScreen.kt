package com.novelai.assistant.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.sf.SfBookDetail
import com.novelai.assistant.data.sf.SfComment
import com.novelai.assistant.ui.components.AsyncCover
import com.novelai.assistant.ui.components.SfAvatar
import com.novelai.assistant.ui.components.formatChineseCount

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isSfBook) {
                        IconButton(
                            onClick = { viewModel.addToCloudShelf(rawId) },
                            enabled = !isDetailLoading
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = "加入菠萝包云端书架")
                        }
                    }
                    IconButton(onClick = { viewModel.loadBookDetail(bookId) }, enabled = !isDetailLoading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        bottomBar = {
            SfBookDetailBottomBar(
                localBook = localBook,
                onToggleBookshelf = { viewModel.toggleBookshelf(targetBookId) },
                onShowTools = { showTools = true },
                onOpenCatalog = { onOpenCatalog(targetBookId) },
                onOpenReader = {
                    val readingIndex = localBook?.currentReadingChapterIndex ?: 0
                    onOpenReader(targetBookId, readingIndex)
                }
            )
        }
    ) { innerPadding ->
        if (isDetailLoading && detail == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("正在获取书籍完整信息...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                detailError?.let { error ->
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.loadBookDetail(bookId) }) {
                                    Text("重试")
                                }
                            }
                        }
                    }
                }

                item {
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

                item {
                    SfBookMetricsCard(detail = detail)
                }

                val tags = detail?.tags.orEmpty()
                if (tags.isNotEmpty()) {
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            tags.forEach { tag ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(tag) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }

                val introText = detail?.intro?.ifBlank { "暂无简介内容" } ?: "暂无简介内容"
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("作品简介", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = introText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (introExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { introExpanded = !introExpanded }
                            )
                            if (introText.length > 80) {
                                Text(
                                    text = if (introExpanded) "收起简介" else "展开完整简介",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { introExpanded = !introExpanded }
                                )
                            }
                        }
                    }
                }

                item {
                    Surface(
                        onClick = { onOpenCatalog(targetBookId) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.FormatListBulleted, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text("章节目录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                val latest = detail?.latestChapterTitle?.ifBlank { chapters.lastOrNull()?.title }
                                Text(
                                    text = if (!latest.isNullOrBlank()) "最新：$latest" else "共 ${chapters.size} 章",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, "查看完整目录")
                        }
                    }
                }

                if (isSfBook) {
                    item {
                        OutlinedButton(onClick = { onOpenRelatedLists(bookId) }, modifier = Modifier.fillMaxWidth()) {
                            Text("看看收录这部作品的相关书单")
                        }
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
private fun SfBookHeader(
    title: String,
    author: String,
    cover: String?,
    isSfBook: Boolean,
    detail: SfBookDetail?,
    fallbackChapterCount: Int,
    onOpenAuthor: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AsyncCover(
            title = title,
            uri = cover,
            modifier = Modifier
                .width(115.dp)
                .height(160.dp),
            shape = RoundedCornerShape(14.dp),
            maxDimension = 512
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (author.isNotBlank()) {
                Surface(
                    onClick = onOpenAuthor,
                    enabled = isSfBook,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "作者：$author",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        if (isSfBook) {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                "查看作者作品集",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (detail?.isFinish == true) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = if (detail?.isFinish == true) "已完结" else "连载中",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (!detail?.typeName.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = detail?.typeName.orEmpty(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            val wordsText = formatWordCount(detail?.charCount ?: 0L)
            val chapterCount = if ((detail?.totalChapters ?: 0) > 0) detail?.totalChapters else fallbackChapterCount
            Text(
                text = "$wordsText · 共 $chapterCount 章",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (vt > 0L) {
                    Text(
                        text = "${formatChineseCount(vt)} 人气",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SfBookMetricsCard(detail: SfBookDetail?) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatWordCount(detail?.charCount ?: 0L),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (detail?.isFinish == true) "完结" else "连载",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val tickets = detail?.ticketCount ?: 0
                Text("$tickets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("月票", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val favs = detail?.favCount ?: 0L
                Text(
                    if (favs > 0) "$favs" else "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("点赞", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "读者书评" + if (comments.isNotEmpty()) " (${comments.size}${if (hasMore) "+" else ""})" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
            if (comments.isNotEmpty()) {
                comments.forEach { c ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .padding(10.dp),
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
                                Text(c.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                                if (c.vipLevel > 0) {
                                    Text("VIP ${c.vipLevel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text(c.createTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Text(c.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        if (c.favCount > 0 || c.replyNum > 0 || c.isStick) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (c.isStick) {
                                    Text("置顶", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (c.favCount > 0) {
                                    Text("有用 ${c.favCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (c.replyNum > 0) {
                                    Text("回复 ${c.replyNum}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (error != null) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onLoadMore) { Text("重试加载更多") }
                    }
                } else if (hasMore) {
                    OutlinedButton(
                        onClick = onLoadMore,
                        enabled = !isLoadingMore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isLoadingMore) "正在加载历史书评…" else "加载更多历史书评")
                    }
                } else {
                    Text(
                        "已显示全部可读取书评",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            } else if (!isLoading) {
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetryInitial) { Text("重试") }
                } else {
                    Text("暂无书评内容", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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
    onOpenReader: () -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val inShelf = localBook?.inBookshelf == true
            OutlinedButton(
                onClick = onToggleBookshelf,
                modifier = Modifier.weight(1.1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
                colors = if (inShelf) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                else ButtonDefaults.outlinedButtonColors()
            ) {
                Icon(
                    if (inShelf) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                    null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (inShelf) "已在书架" else "加入书架", maxLines = 1)
            }
            OutlinedButton(
                onClick = onShowTools,
                modifier = Modifier.weight(0.9f),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(2.dp))
                Text("下载")
            }
            OutlinedButton(
                onClick = onOpenCatalog,
                modifier = Modifier.weight(0.9f),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Rounded.Menu, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(2.dp))
                Text("目录")
            }
            Button(
                onClick = onOpenReader,
                modifier = Modifier.weight(1.5f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(2.dp))
                val progress = localBook?.readingProgressPercent?.toInt() ?: 0
                val readingIndex = localBook?.currentReadingChapterIndex ?: 0
                Text(
                    if (progress > 0) "继续(${readingIndex + 1}章)" else "开始阅读",
                    maxLines = 1
                )
            }
        }
    }
}
