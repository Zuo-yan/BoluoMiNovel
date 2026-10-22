package com.novelai.assistant.ui.discover

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SfBookDetailScreen(
    bookId: String,
    onBack: () -> Unit,
    onOpenReader: (String, Int) -> Unit,
    onOpenCatalog: (String) -> Unit,
    onOpenRelatedLists: (String) -> Unit,
    viewModel: SfViewModel = hiltViewModel()
) {
    LaunchedEffect(bookId) {
        viewModel.loadBookDetail(bookId)
        viewModel.loadComments(bookId)
    }

    val detail by viewModel.detailBook.collectAsStateWithLifecycle()
    val isDetailLoading by viewModel.isDetailLoading.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val isCommentsLoading by viewModel.isCommentsLoading.collectAsStateWithLifecycle()
    val localBook by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    val title = detail?.title?.ifBlank { localBook?.title } ?: localBook?.title ?: "书籍详情"
    val author = detail?.author?.ifBlank { localBook?.author } ?: localBook?.author ?: ""
    val cover = detail?.cover ?: localBook?.coverUri
    val isSfBook = bookId.startsWith("sf:") || bookId.matches(Regex("\\d+"))

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
                        onClick = {
                            val id = localBook?.id ?: "sf:$rawId"
                            viewModel.toggleBookshelf(id)
                        },
                        modifier = Modifier.weight(1.1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = if (inShelf) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonColors()
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
                        onClick = { showTools = true },
                        modifier = Modifier.weight(0.9f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("下载")
                    }
                    OutlinedButton(
                        onClick = { onOpenCatalog("sf:$rawId") },
                        modifier = Modifier.weight(0.9f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Menu, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("目录")
                    }
                    Button(
                        onClick = {
                            val readingIndex = localBook?.currentReadingChapterIndex ?: 0
                            onOpenReader("sf:$rawId", readingIndex)
                        },
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
                // 顶部大封面与元数据头部
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        com.novelai.assistant.ui.components.AsyncCover(
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
                                Text(
                                    text = "作者：$author",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            // 连载状态与类型
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (detail?.isFinish == true) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
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
                            // 字数与总章数
                            val words = detail?.charCount ?: 0L
                            val wordsText = if (words >= 10000) "%.1f万字".format(words / 10000.0) else "$words 字"
                            val chapterCount = if ((detail?.totalChapters ?: 0) > 0) detail?.totalChapters else chapters.size
                            Text(
                                text = "$wordsText · 共 $chapterCount 章",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // 菠萝包专属：收藏量与人气
                            val mk = detail?.markCount ?: 0L
                            val vt = detail?.viewTimes ?: 0L
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (mk > 0L) {
                                    val markText = if (mk >= 10000) "%.1f万+".format(mk / 10000.0) else "$mk"
                                    Text(
                                        text = "$markText 收藏",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (vt > 0L) {
                                    val viewsText = if (vt >= 100000000) "%.1f亿".format(vt / 100000000.0) else if (vt >= 10000) "%.1f万".format(vt / 10000.0) else "$vt"
                                    Text(
                                        text = "$viewsText 人气",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 官方三大核心指标卡片（字数/状态 · 月票 · 点赞）
                item {
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
                                val words = detail?.charCount ?: 0L
                                val wordsText = if (words >= 10000) "%.0f万字".format(words / 10000.0) else "$words 字"
                                Text(wordsText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(if (detail?.isFinish == true) "完结" else "连载", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val tickets = detail?.ticketCount ?: 0
                                Text("$tickets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("月票", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val favs = detail?.favCount ?: 0L
                                Text(if (favs > 0) "$favs" else "-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("点赞", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // 题材标签列表
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

                // 故事简介卡片
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

                // 目录与最新章节预览卡片
                item {
                    Surface(
                        onClick = { onOpenCatalog("sf:$rawId") },
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

                if (isSfBook) item {
                    OutlinedButton(onClick = { onOpenRelatedLists(bookId) }, modifier = Modifier.fillMaxWidth()) {
                        Text("看看收录这部作品的相关书单")
                    }
                }

                // 读者书评与精彩讨论模块
                if (isSfBook) {
                    item {
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
                                        "读者书评" + if (comments.isNotEmpty()) " (${comments.size})" else "",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (isCommentsLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    }
                                }
                                if (comments.isNotEmpty()) {
                                    comments.take(10).forEach { c ->
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
                                        }
                                    }
                                } else if (!isCommentsLoading) {
                                    Text("暂无书评内容", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTools) {
        SfBookToolsDialog(
            bookId = "sf:$rawId",
            currentChapterIndex = localBook?.currentReadingChapterIndex ?: 0,
            onDismiss = { showTools = false },
            viewModel = viewModel
        )
    }
}

