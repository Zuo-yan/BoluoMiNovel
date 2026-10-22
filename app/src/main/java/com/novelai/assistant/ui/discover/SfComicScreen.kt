package com.novelai.assistant.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.sf.SfComic
import com.novelai.assistant.ui.components.AsyncCover
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private val sfComicCategories = listOf(
    null to "全部", 1 to "热血", 2 to "校园", 3 to "推理", 4 to "机战", 5 to "冒险",
    6 to "运动", 7 to "纯爱", 8 to "搞笑", 9 to "科幻", 10 to "魔幻",
    12 to "社会", 13 to "爱情", 14 to "武侠", 15 to "温情", 16 to "惊悚"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SfComicBrowseScreen(
    onBack: () -> Unit, onOpenComic: (String) -> Unit,
    viewModel: SfComicViewModel = hiltViewModel()
) {
    val state by viewModel.browseState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (!state.loading && state.page == 0) viewModel.load(refresh = true) }
    LaunchedEffect(listState, state.hasMore, state.loading, state.items.size) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= layout.totalItemsCount - 3 && state.hasMore && !state.loading
        }.collect { shouldLoad ->
            if (shouldLoad) viewModel.loadMore()
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("漫画") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
            actions = { IconButton({ viewModel.load(refresh = true) }, enabled = !state.loading) { Icon(Icons.Rounded.Refresh, "刷新") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, singleLine = true,
                    placeholder = { Text("搜索漫画") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(56.dp))
                Button({ if (query.isNotBlank()) viewModel.search(query) else viewModel.selectCategory(null) },
                    enabled = !state.loading,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.height(56.dp)) { Text("搜索") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sfComicCategories.forEach { (id, name) ->
                    FilterChip(selected = state.categoryId == id && state.search.isBlank(),
                        onClick = { query = ""; viewModel.selectCategory(id) }, label = { Text(name) })
                }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { error ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(16.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f))
                        TextButton({ viewModel.load(refresh = true) }) { Text("重试") }
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.items, key = { it.slug }) { comic ->
                    SfComicRow(comic) { onOpenComic(comic.slug) }
                }
                if (state.items.isEmpty() && !state.loading && state.error == null) {
                    item { Text(if (state.search.isBlank()) "暂无漫画" else "未找到漫画，可换关键词或清除搜索。",
                        modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (state.loading && state.items.isNotEmpty()) item {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("继续加载", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (!state.hasMore && state.items.isNotEmpty()) item {
                    Text("已显示全部 ${state.items.size} 部漫画", Modifier.fillMaxWidth().padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SfComicReaderScreen(
    onBack: () -> Unit,
    viewModel: SfComicViewModel = hiltViewModel()
) {
    val state by viewModel.detailState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val canPrevious = !state.loading && state.currentChapterIndex > 0
    val chapterCount = state.info?.chapters?.size ?: 0
    val canNext = !state.loading && state.currentChapterIndex < chapterCount - 1
    val currentTitle = state.info?.chapters?.getOrNull(state.currentChapterIndex)?.title.orEmpty()
    val currentNumber = state.currentChapterIndex + 1
    val endAnchorIndex = state.endAnchorIndex
    LaunchedEffect(state.info) {
        val info = state.info
        if (info != null && state.pages.isEmpty() && state.error == null) viewModel.openChapter(0)
    }
    LaunchedEffect(state.loadedChapterIndex) {
        if (state.loadedChapterIndex != null && state.pages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(listState, state.loadedChapterIndex, state.pages.size, state.loading, chapterCount, endAnchorIndex) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val endItem = layout.visibleItemsInfo.firstOrNull { it.index == endAnchorIndex }
            val endVisible = endItem != null &&
                endItem.offset + endItem.size > layout.viewportStartOffset
            Triple(endVisible, listState.isScrollInProgress, state.currentChapterIndex)
        }.distinctUntilChanged().collect { (endVisible, scrolling, _) ->
            if (endVisible) {
                viewModel.markEndVisible()
                if (!scrolling) viewModel.armNextChapter()
            }
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(state.info?.comic?.title ?: "漫画阅读") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
            actions = { IconButton({ viewModel.loadDetail() }, enabled = !state.loading) { Icon(Icons.Rounded.Refresh, "刷新") } })
    }) { padding ->
        when {
            state.loading && state.info == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null && state.info == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error.orEmpty())
                    TextButton({ viewModel.loadDetail() }) { Text("重试") }
                }
            }
            state.info == null -> Unit
            else -> Box(
                Modifier.fillMaxSize().padding(padding).pointerInput(state.currentChapterIndex, state.loadedChapterIndex) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                            viewModel.advanceIfAtEnd()
                        }
                    }
                }
            ) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 168.dp)) {
                item(key = "chapter-header") {
                    androidx.compose.runtime.LaunchedEffect(state.currentChapterIndex) {
                        viewModel.setEndAnchorIndex(null)
                    }
                    Unit
                }
                item {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AsyncCover(state.info!!.comic.title, state.info!!.comic.cover,
                            Modifier.width(76.dp).height(102.dp), RoundedCornerShape(10.dp), maxDimension = 512)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(state.info!!.comic.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("共 ${state.info!!.chapters.size} 话", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val chapter = state.info!!.chapters.getOrNull(state.currentChapterIndex)
                            if (chapter != null) Text(chapter.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (state.loading) item(key = "chapter-loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
                if (state.loading && state.pages.isNotEmpty()) item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("切换到第 ${state.currentChapterIndex + 1} 话", style = MaterialTheme.typography.bodySmall)
                    }
                }
                var hasErrorItem = false
                state.error?.let { error -> hasErrorItem = true; item(key = "chapter-error") {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    TextButton({ viewModel.openChapter(state.currentChapterIndex) }, Modifier.padding(horizontal = 16.dp)) { Text("重试本章") }
                } }
                items(state.pages, key = { it }) { page ->
                    AsyncCover(state.info!!.comic.title, page, Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        RoundedCornerShape(4.dp), ContentScale.FillWidth, maxDimension = 2048)
                }
                val pageItems = state.pages.size
                if (state.pages.isEmpty() && !state.loading && state.error == null) item(key = "preparing") {
                    Text("正在准备本章图片", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.pages.isNotEmpty() && !state.loading) item(key = "chapter-end") {
                    val atLastChapter = state.currentChapterIndex >= chapterCount - 1
                    LaunchedEffect(pageItems, state.currentChapterIndex) {
                        viewModel.setEndAnchorIndex(pageItems + if (hasErrorItem) 3 else 2)
                    }
                    LaunchedEffect(state.currentChapterIndex, atLastChapter) {
                        if (!atLastChapter) {
                            delay(650)
                            viewModel.armNextChapter()
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    Text("本话结束", Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(currentTitle, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(
                        if (!atLastChapter) "继续下滑，自动阅读下一话" else "已经读完最后一话",
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        color = if (canNext) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.fillMaxWidth().height(48.dp))
                }
            }
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    tonalElevation = 3.dp,
                    shadowElevation = 10.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ viewModel.openChapter(state.currentChapterIndex - 1) },
                            enabled = canPrevious) { Text("上一话") }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("第 $currentNumber / $chapterCount 话",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(currentTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Button({ viewModel.openChapter(state.currentChapterIndex + 1) },
                            enabled = canNext) { Text("下一话") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SfComicRow(comic: SfComic, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AsyncCover(comic.title, comic.cover, Modifier.width(62.dp).height(84.dp),
                RoundedCornerShape(8.dp), maxDimension = 384)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(comic.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (comic.author.isNotBlank()) Text(comic.author,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (comic.category.isNotBlank()) Text(comic.category,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
