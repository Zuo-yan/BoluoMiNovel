package com.novelai.assistant.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.novelai.assistant.data.sf.ShelfUpdate
import com.novelai.assistant.ui.components.AdaptiveGlassDialog
import com.novelai.assistant.ui.components.AdaptiveGlassSheet
import com.novelai.assistant.ui.components.AsyncCover
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassCircleButton
import com.novelai.assistant.ui.components.GlassFilterPill
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassSearchCapsule
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LargeTitleHeader
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.SfAvatar
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.bottomBarContentPadding
import com.novelai.assistant.ui.theme.NovelColors

@Composable
fun SfScreen(
    onOpenBook: (String) -> Unit,
    onOpenCatalog: (String) -> Unit,
    onOpenFanqie: () -> Unit,
    onOpenNovelBrowse: () -> Unit,
    onOpenComics: () -> Unit,
    onOpenRecommendations: () -> Unit,
    viewModel: SfHomeViewModel = hiltViewModel()
) {
    LaunchedEffect(viewModel) { viewModel.restoreSession() }
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val checkingUpdates by viewModel.checkingUpdates.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val safety by viewModel.safety.collectAsStateWithLifecycle()
    val wealth by viewModel.wealth.collectAsStateWithLifecycle()
    val locals by viewModel.localBooks.collectAsStateWithLifecycle()
    val task by viewModel.task.collectAsStateWithLifecycle()
    val cache by viewModel.cacheBytes.collectAsStateWithLifecycle()

    var login by remember { mutableStateOf(false) }
    var accountSheet by remember { mutableStateOf(false) }
    var tasks by remember { mutableStateOf(false) }
    var clearCache by remember { mutableStateOf(false) }
    var toolsBook by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.localBookId, state.destination) {
        val id = state.localBookId ?: return@LaunchedEffect
        when (state.destination) {
            "read" -> { viewModel.consumeDestination(); onOpenBook(id) }
            "catalog" -> { viewModel.consumeDestination(); onOpenCatalog(id) }
            "tools" -> { toolsBook = id; viewModel.consumeDestination() }
        }
    }

    val adaptive = LocalAdaptiveMetrics.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val accentText = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary

    val progress = remember(locals) { locals.associateBy { it.id } }
    val recent = remember(locals) { locals.firstOrNull { it.lastReadAt > 0 } }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = bottomBarContentPadding(24.dp)

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val extraSide = ((maxWidth - adaptive.maxContentWidth) / 2).coerceAtLeast(0.dp)
            val horizontalPad = adaptive.horizontalPadding + extraSide

            LazyVerticalGrid(
                columns = GridCells.Fixed(adaptive.listGridColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = horizontalPad,
                    end = horizontalPad,
                    top = statusTop + 8.dp,
                    bottom = bottomInset
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LargeTitleHeader(
                            title = "菠萝包",
                            subtitle = "好故事，接着读",
                            horizontalPadding = 0.dp
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassCircleButton(
                                    icon = Icons.Rounded.Explore,
                                    contentDescription = "番茄小说",
                                    onClick = onOpenFanqie
                                )
                                GlassCircleButton(
                                    icon = Icons.Rounded.Download,
                                    contentDescription = "下载任务",
                                    onClick = { tasks = true },
                                    tint = if (task.running) accentText else primaryText
                                )
                                GlassCircleButton(
                                    icon = Icons.Rounded.AccountCircle,
                                    contentDescription = "账号与缓存",
                                    onClick = { accountSheet = true },
                                    tint = if (account != null) accentText else primaryText
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassSearchCapsule(
                                query = "",
                                placeholder = "搜索书名 / 作者 / 书号或链接",
                                onClick = onOpenNovelBrowse,
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(max = 640.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassFilterPill(
                                label = "分类找书",
                                selected = false,
                                leadingIcon = Icons.Rounded.Search,
                                onClick = onOpenNovelBrowse
                            )
                            GlassFilterPill(
                                label = "新书推荐",
                                selected = false,
                                leadingIcon = Icons.Rounded.AutoAwesome,
                                onClick = onOpenRecommendations
                            )
                            GlassFilterPill(
                                label = "漫画专区",
                                selected = false,
                                leadingIcon = Icons.Rounded.Image,
                                onClick = onOpenComics
                            )
                            GlassFilterPill(
                                label = "番茄小说",
                                selected = false,
                                leadingIcon = Icons.Rounded.Explore,
                                onClick = onOpenFanqie
                            )
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    GlassSectionCard(
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (account == null) login = true else accountSheet = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SfAvatar(account?.avatar, account?.name.orEmpty(), Modifier.size(40.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = account?.let {
                                        it.name + (wealth?.let { w -> " · 火券 ${w.fireMoney} · 代券 ${w.coupons}" } ?: "")
                                    } ?: "连接你的菠萝包书架",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText
                                )
                                Text(
                                    text = if (account == null) "登录后查看收藏，阅读已购章节" else "书架已连接 · 阅读进度保存在本机",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryText
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "账号", tint = secondaryText)
                        }
                    }
                }

                if (safety.blocked) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SfSafetyNotice(
                            protection = safety,
                            onAcknowledgeCooldown = { viewModel.acknowledgeCooldown() },
                            onForceBypass = { viewModel.forceBypassSafety() },
                            onLogin = { login = true }
                        )
                    }
                }

                if (recent != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GlassSectionCard(
                            title = "继续阅读",
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenBook(recent.id) }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                AsyncCover(
                                    title = recent.title,
                                    uri = recent.coverUri,
                                    modifier = Modifier
                                        .width(56.dp)
                                        .height(78.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    maxDimension = 256
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = recent.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "读到第 ${recent.currentReadingChapterIndex + 1} 章 · ${recent.readingProgressPercent.toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accentText
                                    )
                                }
                                GlassCircleButton(
                                    icon = Icons.Rounded.PlayArrow,
                                    contentDescription = "继续阅读",
                                    onClick = { onOpenBook(recent.id) },
                                    tint = accentText
                                )
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    GlassSectionCard(
                        title = if (account != null) "我的书架" else "本机菠萝包书籍",
                        actionLabel = if (checkingUpdates) "正在检查 · 点击停止" else "检查更新",
                        onActionClick = {
                            if (checkingUpdates) viewModel.cancelUpdateCheck() else viewModel.checkUpdates()
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (account != null) {
                                        "云端书架共 ${state.books.size} 部"
                                    } else {
                                        "本地已导入 ${locals.size} 部"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryText
                                )
                                if (account != null) {
                                    GlassCircleButton(
                                        icon = Icons.Rounded.Refresh,
                                        contentDescription = "刷新书架",
                                        onClick = { if (!state.busy) viewModel.refreshShelves() },
                                        size = 36.dp,
                                        tint = if (state.busy) secondaryText.copy(alpha = 0.45f) else primaryText
                                    )
                                }
                            }
                            if (state.shelves.isNotEmpty() && account != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    state.shelves.forEach { (id, name) ->
                                        GlassFilterPill(
                                            label = name,
                                            selected = state.selectedShelf == id,
                                            onClick = { if (!state.busy) viewModel.selectShelf(id) }
                                        )
                                    }
                                }
                            }
                            if (state.busy) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                            state.error?.let { message ->
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        text = message,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                if (account != null) {
                    items(state.books, key = { it.id }) { book ->
                        val local = progress["sf:${book.id}"]
                        val update = updates["sf:${book.id}"]
                        SfShelfBookGlassCard(
                            title = book.title,
                            author = book.author,
                            cover = local?.coverUri ?: book.cover,
                            progressText = local?.let {
                                "读到第 ${it.currentReadingChapterIndex + 1} 章 · ${it.readingProgressPercent.toInt()}%"
                            } ?: "尚未阅读",
                            update = update,
                            onDownloadUpdate = { viewModel.downloadUpdates("sf:${book.id}") },
                            onRead = { onOpenBook(local?.id ?: "sf:${book.id}") },
                            onAction = { destination ->
                                if (local != null) viewModel.localAction(local.id, destination)
                                else viewModel.openRemote(book.id, destination)
                            }
                        )
                    }
                    if (state.hasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                GlassCapsuleButton(
                                    text = "加载更多作品",
                                    onClick = { viewModel.loadMore() },
                                    filled = false,
                                    enabled = !state.busy
                                )
                            }
                        }
                    }
                } else {
                    items(locals, key = { it.id }) { book ->
                        val update = updates[book.id]
                        SfShelfBookGlassCard(
                            title = book.title,
                            author = book.author,
                            cover = book.coverUri,
                            progressText = "${book.readingProgressPercent.toInt()}% · ${book.totalChapters} 章",
                            update = update,
                            onDownloadUpdate = { viewModel.downloadUpdates(book.id) },
                            onRead = { onOpenBook(book.id) },
                            onAction = { viewModel.localAction(book.id, it) }
                        )
                    }
                }

                if (!state.busy && (if (account != null) state.books.isEmpty() else locals.isEmpty())) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GlassSectionCard(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MenuBook,
                                    contentDescription = null,
                                    tint = accentText.copy(alpha = 0.85f),
                                    modifier = Modifier.size(46.dp)
                                )
                                Text(
                                    text = if (account == null) "把喜欢的故事带进来" else "这个书架还没有作品",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText
                                )
                                Text(
                                    text = "可以通过搜索、分类排行榜，或直接输入书号/链接打开一本书。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryText
                                )
                                GlassCapsuleButton(
                                    text = "搜索或分类找小说",
                                    onClick = onOpenNovelBrowse,
                                    filled = true,
                                    icon = Icons.Rounded.Search
                                )
                            }
                        }
                    }
                }

                if (task.chapterIds.isNotEmpty() && (task.running || task.paused || task.failures.isNotEmpty())) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GlassSectionCard(contentPadding = PaddingValues(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { tasks = true },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, tint = accentText)
                                Text(
                                    text = "${task.title} · ${task.completed}/${task.chapterIds.size} · 查看下载",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = primaryText,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = secondaryText)
                            }
                        }
                    }
                }
            }
        }
    }

    if (login) {
        SfLoginDialog({ login = false }, safety) { username, password ->
            login = false
            viewModel.login(username, password)
        }
    }

    if (accountSheet) {
        AdaptiveGlassSheet(onDismissRequest = { accountSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "账号与资产",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )
                Text(
                    text = "请求保护：全局串行；普通请求至少间隔 2 秒，正文请求后至少等待 4 秒；每 30 分钟最多请求 50 章。",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText
                )
                SfSafetyNotice(
                    protection = safety,
                    onAcknowledgeCooldown = { viewModel.acknowledgeCooldown() },
                    onForceBypass = { viewModel.forceBypassSafety() },
                    onLogin = { accountSheet = false; login = true }
                )
                if (account != null) {
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        widgetSize = GlassWidgetSize.BIG,
                        sceneType = GlassSceneType.GENERAL,
                        interactive = false,
                        skipRenderLayer = true,
                        shadowElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SfAvatar(account?.avatar, account?.name.orEmpty(), Modifier.size(42.dp))
                                    Column {
                                        Text(
                                            text = account?.name.orEmpty(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryText
                                        )
                                        Text(
                                            text = "VIP ${wealth?.vipLevel ?: account?.vipLevel ?: 0}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accentText
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("火券", style = MaterialTheme.typography.labelSmall, color = secondaryText)
                                    Text("${wealth?.fireMoney ?: 0}", style = MaterialTheme.typography.titleSmall, color = primaryText)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("代金券", style = MaterialTheme.typography.labelSmall, color = secondaryText)
                                    Text("${wealth?.coupons ?: 0}", style = MaterialTheme.typography.titleSmall, color = accentText)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("月票", style = MaterialTheme.typography.labelSmall, color = secondaryText)
                                    Text("${wealth?.tickets ?: 0}", style = MaterialTheme.typography.titleSmall, color = primaryText)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "每日签到领代券请在官方 App 进行，资产实时同步",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryText,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        GlassCapsuleButton(
                            text = "刷新资产",
                            onClick = { viewModel.refreshWealth() },
                            filled = false,
                            enabled = !state.busy,
                            icon = Icons.Rounded.Refresh
                        )
                    }
                } else {
                    Text("尚未登录菠萝包账号", style = MaterialTheme.typography.bodyMedium, color = primaryText)
                }

                Text(
                    text = "正文缓存 ${"%.2f".format(cache / 1048576.0)} MB",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText
                )
                Text(
                    text = "清理自动缓存会保留主动下载的正文、书籍和阅读进度。",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCapsuleButton(
                        text = "清理已读缓存",
                        onClick = { clearCache = true },
                        filled = false,
                        enabled = cache > 0,
                        modifier = Modifier.weight(1f)
                    )
                    if (account != null) {
                        GlassCapsuleButton(
                            text = "退出账号",
                            onClick = { viewModel.logout(); accountSheet = false },
                            filled = false,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        GlassCapsuleButton(
                            text = "登录菠萝包",
                            onClick = { accountSheet = false; login = true },
                            filled = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    text = "会话在本机加密并排除备份，不保存密码。退出仅清除本机登录，保留正文和进度；如怀疑会话泄露，请在官方端处理账号安全。限速不能保证避免平台限制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText
                )
            }
        }
    }

    if (tasks) {
        AdaptiveGlassSheet(onDismissRequest = { tasks = false }) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "下载任务",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )
                if (task.chapterIds.isEmpty()) {
                    Text(
                        text = "暂无下载任务",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryText,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    SfTaskCard(
                        task = task,
                        safety = safety,
                        onPause = { viewModel.downloads.pause() },
                        onResume = { viewModel.downloads.resume() },
                        onAcknowledgeCooldown = { viewModel.acknowledgeCooldown() },
                        onForceBypassSafety = { viewModel.forceBypassSafety() }
                    )
                }
            }
        }
    }

    toolsBook?.let { id ->
        SfBookToolsDialog(id, progress[id]?.currentReadingChapterIndex ?: 0, { toolsBook = null })
    }

    if (clearCache) {
        AdaptiveGlassDialog(onDismissRequest = { clearCache = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "清理已读缓存？",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )
                Text(
                    text = "主动下载的章节与阅读进度会保留。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryText
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCapsuleButton(
                        text = "取消",
                        onClick = { clearCache = false },
                        filled = false,
                        modifier = Modifier.weight(1f)
                    )
                    GlassCapsuleButton(
                        text = "清理",
                        onClick = { viewModel.clearCache(); clearCache = false },
                        filled = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SfShelfBookGlassCard(
    title: String,
    author: String,
    cover: String?,
    progressText: String,
    update: ShelfUpdate?,
    onDownloadUpdate: () -> Unit,
    onRead: () -> Unit,
    onAction: (String) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val accentText = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary

    LiquidGlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onRead),
        cornerRadius = 20.dp,
        widgetSize = GlassWidgetSize.SMALL,
        sceneType = GlassSceneType.GENERAL,
        interactive = false,
        skipRenderLayer = true,
        shadowElevation = 5.dp,
        borderWidth = 0.7.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncCover(
                    title = title,
                    uri = cover,
                    modifier = Modifier
                        .width(62.dp)
                        .height(86.dp),
                    shape = RoundedCornerShape(10.dp),
                    maxDimension = 256
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (author.isNotBlank()) {
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "书籍更多：$title",
                            tint = secondaryText
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("章节目录") },
                            onClick = { menu = false; onAction("catalog") }
                        )
                        DropdownMenuItem(
                            text = { Text("下载与导出") },
                            onClick = { menu = false; onAction("tools") }
                        )
                    }
                }
            }
            if (update != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = update.label(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (update.error != null) MaterialTheme.colorScheme.error else secondaryText,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (update.added.isNotEmpty()) {
                        TextButton(
                            onClick = onDownloadUpdate,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("下载新章", style = MaterialTheme.typography.labelSmall, color = accentText)
                        }
                    }
                }
            }
        }
    }
}
