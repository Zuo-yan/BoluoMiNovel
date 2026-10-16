package com.novelai.assistant.ui.discover

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.ui.components.LargeTitleHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SfScreen(onOpenBook: (String) -> Unit, onOpenCatalog: (String) -> Unit, onOpenFanqie: () -> Unit, viewModel: SfViewModel = hiltViewModel()) {
    LaunchedEffect(viewModel) { viewModel.restoreSession() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val locals by viewModel.localBooks.collectAsStateWithLifecycle()
    val task by viewModel.task.collectAsStateWithLifecycle()
    val cache by viewModel.cacheBytes.collectAsStateWithLifecycle()
    var more by remember { mutableStateOf(false) }
    var login by remember { mutableStateOf(false) }
    var accountSheet by remember { mutableStateOf(false) }
    var inputSheet by remember { mutableStateOf(false) }
    var tasks by remember { mutableStateOf(false) }
    var clearCache by remember { mutableStateOf(false) }
    var toolsBook by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(state.localBookId, state.destination) {
        val id = state.localBookId ?: return@LaunchedEffect
        when (state.destination) {
            "read" -> { viewModel.consumeDestination(); onOpenBook(id) }
            "catalog" -> { viewModel.consumeDestination(); onOpenCatalog(id) }
            "tools" -> { toolsBook = id; viewModel.consumeDestination() }
        }
    }
    val progress = locals.associateBy { it.id }
    val recent = locals.firstOrNull { it.lastReadAt > 0 }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            LargeTitleHeader("菠萝包", subtitle = "好故事，接着读") {
                IconButton({ inputSheet = true }) { Icon(Icons.Rounded.Add, "打开书号或链接") }
                Box {
                    IconButton({ more = true }) { Icon(Icons.Rounded.MoreHoriz, "更多") }
                    DropdownMenu(more, { more = false }) {
                        DropdownMenuItem({ Text("番茄小说") }, { more = false; onOpenFanqie() }, leadingIcon = { Icon(Icons.Rounded.Explore, null) })
                        DropdownMenuItem({ Text("下载任务") }, { more = false; tasks = true }, leadingIcon = { Icon(Icons.Rounded.Download, null) })
                        DropdownMenuItem({ Text("账号与缓存") }, { more = false; accountSheet = true }, leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) })
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow).clickable { if (account == null) login = true else accountSheet = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.AccountCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(account?.name ?: "连接你的菠萝包书架", style = MaterialTheme.typography.titleSmall)
                    Text(if (account == null) "登录后查看收藏，阅读已购章节" else "书架已连接 · 阅读进度保存在本机", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, "账号")
            }
        }
        if (recent != null) item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("继续阅读", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
                Surface(onClick = { onOpenBook(recent.id) }, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f), shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SfCover(recent.title, recent.coverUri, Modifier.width(56.dp).height(78.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(recent.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("读到第 ${recent.currentReadingChapterIndex + 1} 章 · ${recent.readingProgressPercent.toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Rounded.PlayArrow, "继续阅读", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (account != null) "我的书架" else "本机菠萝包书籍", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (account != null) IconButton({ viewModel.refreshShelves() }, enabled = !state.busy) { Icon(Icons.Rounded.Refresh, "刷新书架") }
            }
        }
        if (state.shelves.isNotEmpty() && account != null) item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.shelves.forEach { (id, name) -> FilterChip(state.selectedShelf == id, { viewModel.selectShelf(id) }, enabled = !state.busy, label = { Text(name) }) }
            }
        }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) }
        state.error?.let { message -> item {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(message, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        } }
        if (account != null) {
            items(state.books, key = { it.id }) { book ->
                val local = progress["sf:${book.id}"]
                SfBookRow(book.title, book.author, local?.coverUri ?: book.cover,
                    local?.let { "读到第 ${it.currentReadingChapterIndex + 1} 章 · ${it.readingProgressPercent.toInt()}%" } ?: "尚未阅读",
                    onRead = { if (local != null) onOpenBook(local.id) else viewModel.openRemote(book.id) },
                    onAction = { destination -> if (local != null) viewModel.localAction(local.id, destination) else viewModel.openRemote(book.id, destination) })
            }
            if (state.hasMore) item { TextButton({ viewModel.loadMore() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text("加载更多作品") } }
        } else {
            items(locals, key = { it.id }) { book -> SfBookRow(book.title, book.author, book.coverUri,
                "${book.readingProgressPercent.toInt()}% · ${book.totalChapters} 章", { onOpenBook(book.id) }, { viewModel.localAction(book.id, it) }) }
        }
        if (!state.busy && (if (account != null) state.books.isEmpty() else locals.isEmpty())) item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.MenuBook, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = .7f), modifier = Modifier.size(46.dp))
                Text(if (account == null) "把喜欢的故事带进来" else "这个书架还没有作品", style = MaterialTheme.typography.titleMedium)
                Text("也可以通过书号或链接直接打开一本书。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({ inputSheet = true }) { Text("打开一本书") }
            }
        }
        if (task.chapterIds.isNotEmpty() && (task.running || task.paused || task.failures.isNotEmpty())) item {
            TextButton({ tasks = true }, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Icon(Icons.Rounded.Download, null); Text("  ${task.title} · ${task.completed}/${task.chapterIds.size} · 查看下载") }
        }
    }
    if (login) SfLoginDialog({ login = false }) { username, password -> login = false; viewModel.login(username, password) }
    if (inputSheet) ModalBottomSheet(onDismissRequest = { inputSheet = false }) {
        Column(Modifier.padding(horizontal = 24.dp).imePadding().padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("打开书籍", style = MaterialTheme.typography.headlineSmall)
            Text("输入菠萝包书号或官方书籍链接。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(query, { query = it }, label = { Text("书号 / 书籍链接") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button({ inputSheet = false; viewModel.openRemote(query) }, enabled = query.isNotBlank() && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("开始阅读") }
        }
    }
    if (accountSheet) ModalBottomSheet(onDismissRequest = { accountSheet = false }) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("账号与缓存", style = MaterialTheme.typography.headlineSmall)
            Text(account?.let { "已登录：${it.name}" } ?: "尚未登录菠萝包")
            Text("正文缓存 ${"%.2f".format(cache / 1048576.0)} MB", style = MaterialTheme.typography.titleMedium)
            Text("清理自动缓存会保留主动下载的正文、书籍和阅读进度。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton({ clearCache = true }, enabled = cache > 0, modifier = Modifier.fillMaxWidth()) { Text("清理已读缓存") }
            if (account != null) TextButton({ viewModel.logout(); accountSheet = false }) { Text("退出菠萝包账号") }
            else Button({ accountSheet = false; login = true }) { Text("登录菠萝包") }
            Text("登录会话在本机加密保存，不保存密码。退出后保留本地正文和进度。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (tasks) ModalBottomSheet(onDismissRequest = { tasks = false }) {
        Column(Modifier.padding(bottom = 32.dp)) { if (task.chapterIds.isEmpty()) Text("暂无下载任务", Modifier.padding(24.dp)) else SfTaskCard(viewModel) }
    }
    toolsBook?.let { id -> SfBookToolsDialog(id, progress[id]?.currentReadingChapterIndex ?: 0, { toolsBook = null }, viewModel) }
    if (clearCache) AlertDialog(onDismissRequest = { clearCache = false }, title = { Text("清理已读缓存？") }, text = { Text("主动下载的章节与阅读进度会保留。") },
        confirmButton = { TextButton({ viewModel.clearCache(); clearCache = false }) { Text("清理") } }, dismissButton = { TextButton({ clearCache = false }) { Text("取消") } })
}

@Composable
private fun SfBookRow(title: String, author: String, cover: String?, progress: String, onRead: () -> Unit, onAction: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onRead).padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SfCover(title, cover, Modifier.width(66.dp).height(92.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (author.isNotBlank()) Text(author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(progress, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "书籍更多：$title") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("章节目录") }, { menu = false; onAction("catalog") })
                DropdownMenuItem({ Text("下载与导出") }, { menu = false; onAction("tools") })
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 102.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
}

@Composable
private fun SfCover(title: String, uri: String?, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (uri.isNullOrBlank()) return@runCatching null
                val file = if (uri.startsWith("http://") || uri.startsWith("https://")) {
                    val cache = java.io.File(context.cacheDir, "sf-covers").apply { mkdirs() }
                    val name = java.security.MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
                    java.io.File(cache, name).also { target -> if (!target.exists()) {
                        val conn = java.net.URL(uri.replaceFirst("http://", "https://")).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }
                        val bytes = conn.getInputStream().use { input ->
                            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                            while (out.size() <= 2 * 1024 * 1024) { val n = input.read(buffer); if (n < 0) break; out.write(buffer, 0, n) }
                            out.toByteArray()
                        }
                        if (bytes.size > 2 * 1024 * 1024) return@runCatching null
                        target.writeBytes(bytes)
                    } }
                } else java.io.File(uri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 256).coerceAtLeast(1) })
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(title.take(4), Modifier.padding(8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SfCatalogScreen(bookId: String, onBack: () -> Unit, onRead: (Int) -> Unit, viewModel: SfViewModel = hiltViewModel()) {
    LaunchedEffect(bookId) { viewModel.selectLocal(bookId) }
    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("章节目录") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
        actions = { if (bookId.startsWith("sf:")) IconButton({ viewModel.refreshDirectory(bookId) }, enabled = !state.busy) { Icon(Icons.Rounded.Refresh, "刷新目录") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { Text(book?.title.orEmpty(), Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge) }
            state.error?.let { item { Text(it, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error) } }
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(chapters, key = { it.id }) { ch -> ListItem(headlineContent = { Text("${ch.chapterIndex + 1}. ${ch.title}") }, supportingContent = { Text(when { ch.downloaded -> "已下载"; ch.content.isNotBlank() -> "已缓存"; ch.isPaid -> "VIP · 读取时检查已购权限"; else -> "免费" }) }, modifier = Modifier.clickable { onRead(ch.chapterIndex) }) }
        }
    }
}
