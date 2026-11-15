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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.sf.SfComic
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.AsyncCover
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassCircleButton
import com.novelai.assistant.ui.components.GlassFilterPill
import com.novelai.assistant.ui.components.GlassGradientBar
import com.novelai.assistant.ui.components.GlassGradientDirection
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassSearchCapsule
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.glassSource
import com.novelai.assistant.ui.components.rememberGlassState
import com.novelai.assistant.ui.theme.NovelColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SfComicBrowseScreen(
    onBack: () -> Unit,
    onOpenComic: (String) -> Unit,
    viewModel: SfComicViewModel = hiltViewModel()
) {
    val state by viewModel.browseState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val gridState = rememberLazyGridState()
    val adaptive = LocalAdaptiveMetrics.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val accentText = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(Unit) {
        if (!state.loading && state.page == 0) viewModel.load(refresh = true)
    }
    LaunchedEffect(gridState, state.hasMore, state.loading, state.items.size) {
        snapshotFlow {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= layout.totalItemsCount - 3 && state.hasMore && !state.loading
        }.collect { shouldLoad ->
            if (shouldLoad) viewModel.loadMore()
        }
    }

    fun submitSearch() {
        if (query.isNotBlank()) viewModel.search(query) else viewModel.selectCategory(null)
    }

    Scaffold(
        containerColor = Color.Transparent,
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassCircleButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            onClick = onBack
                        )
                        GlassSearchCapsule(
                            query = query,
                            placeholder = "搜索漫画",
                            onQueryChange = {
                                query = it
                                if (it.isEmpty() && state.search.isNotBlank()) {
                                    viewModel.selectCategory(state.categoryId)
                                }
                            },
                            onSearch = { if (!state.loading) submitSearch() },
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(max = 640.dp),
                            trailingContent = {
                                if (query.isNotBlank()) {
                                    IconButton(
                                        onClick = { if (!state.loading) submitSearch() },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = "搜索",
                                            tint = accentText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        )
                        GlassCircleButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = "刷新",
                            onClick = { if (!state.loading) viewModel.load(refresh = true) },
                            tint = if (state.loading) secondaryText.copy(alpha = 0.45f) else primaryText
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.categories.forEach { (id, name) ->
                            GlassFilterPill(
                                label = name,
                                selected = state.categoryId == id && state.search.isBlank(),
                                onClick = { query = ""; viewModel.selectCategory(id) }
                            )
                        }
                    }

                    if (state.loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    state.error?.let { error ->
                        GlassSectionCard(contentPadding = PaddingValues(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton({ viewModel.load(refresh = true) }) { Text("重试") }
                            }
                        }
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val extraSide = ((maxWidth - adaptive.maxContentWidth) / 2).coerceAtLeast(0.dp)
                val horizontalPad = adaptive.horizontalPadding + extraSide

                LazyVerticalGrid(
                    columns = GridCells.Fixed(adaptive.listGridColumns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = horizontalPad,
                        end = horizontalPad,
                        top = 6.dp,
                        bottom = navBottom + 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items, key = { it.slug }) { comic ->
                        SfComicRow(comic) { onOpenComic(comic.slug) }
                    }
                    if (state.items.isEmpty() && !state.loading && state.error == null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GlassSectionCard(contentPadding = PaddingValues(24.dp)) {
                                Text(
                                    text = if (state.search.isBlank()) "暂无漫画" else "未找到漫画，可换关键词或清除搜索。",
                                    color = secondaryText,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    if (state.loading && state.items.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("继续加载", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                            }
                        }
                    } else if (!state.hasMore && state.items.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "已显示全部 ${state.items.size} 部漫画",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                color = secondaryText,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SfComicReaderScreen(
    onBack: () -> Unit,
    viewModel: SfComicViewModel = hiltViewModel()
) {
    val state by viewModel.detailState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val comicHaze = rememberGlassState()
    var showChapterPicker by rememberSaveable { mutableStateOf(false) }
    val canPrevious = !state.loading && state.currentChapterIndex > 0
    val chapterCount = state.info?.chapters?.size ?: 0
    val canNext = !state.loading && state.currentChapterIndex < chapterCount - 1
    val currentTitle = state.info?.chapters?.getOrNull(state.currentChapterIndex)?.title.orEmpty()
    val currentNumber = state.currentChapterIndex + 1

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val accentText = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(state.info) {
        val info = state.info
        if (info != null && state.pages.isEmpty() && state.error == null) viewModel.openChapter(0)
    }
    LaunchedEffect(state.loadedChapterIndex) {
        if (state.loadedChapterIndex != null && state.pages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(listState, state.loadedChapterIndex, state.pages.size, state.loading, chapterCount) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val endVisible = layout.visibleItemsInfo.any { it.key == "chapter-end" }
            val atBottom = endVisible && !listState.canScrollForward &&
                state.loadedChapterIndex == state.currentChapterIndex &&
                state.pages.isNotEmpty() && !state.loading
            Triple(atBottom, listState.isScrollInProgress, state.currentChapterIndex)
        }.distinctUntilChanged().collect { (atBottom, scrolling, _) ->
            if (atBottom) {
                viewModel.markEndVisible()
                if (!scrolling) viewModel.armNextChapter()
            } else {
                viewModel.clearEndReached()
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.loading && state.info == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.info == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlassSectionCard(
                            modifier = Modifier.padding(24.dp).widthIn(max = 420.dp),
                            contentPadding = PaddingValues(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                                GlassCapsuleButton(
                                    text = "重试",
                                    onClick = { viewModel.loadDetail() },
                                    filled = false
                                )
                            }
                        }
                    }
                }
                state.info != null -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .glassSource(comicHaze)
                            .pointerInput(state.currentChapterIndex, state.loadedChapterIndex) {
                                awaitPointerEventScope {
                                    var dragStartY: Float? = null
                                    var draggedUp = false
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                        when (event.type) {
                                            PointerEventType.Press -> {
                                                dragStartY = event.changes.firstOrNull()?.position?.y
                                                draggedUp = false
                                            }
                                            PointerEventType.Move -> {
                                                val currentY = event.changes.firstOrNull()?.position?.y
                                                val startY = dragStartY
                                                if (startY != null && currentY != null && startY - currentY > viewConfiguration.touchSlop) {
                                                    draggedUp = true
                                                }
                                            }
                                            PointerEventType.Release -> {
                                                if (draggedUp && event.changes.none { it.pressed }) {
                                                    viewModel.advanceIfAtEnd()
                                                }
                                                if (event.changes.none { it.pressed }) {
                                                    dragStartY = null
                                                    draggedUp = false
                                                }
                                            }
                                            else -> {
                                                if (event.changes.none { it.pressed }) {
                                                    dragStartY = null
                                                    draggedUp = false
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                        state = listState,
                        contentPadding = PaddingValues(
                            top = statusTop + 72.dp,
                            bottom = navBottom + 144.dp
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                GlassSectionCard(
                                    modifier = Modifier.widthIn(max = 840.dp),
                                    contentPadding = PaddingValues(16.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        AsyncCover(
                                            title = state.info!!.comic.title,
                                            uri = state.info!!.comic.cover,
                                            modifier = Modifier
                                                .width(76.dp)
                                                .height(102.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            maxDimension = 512
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                text = state.info!!.comic.title,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = primaryText
                                            )
                                            Text(
                                                text = "共 ${state.info!!.chapters.size} 话",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = secondaryText
                                            )
                                            val chapter = state.info!!.chapters.getOrNull(state.currentChapterIndex)
                                            if (chapter != null) {
                                                Text(
                                                    text = chapter.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = accentText,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (state.loading) {
                            item(key = "chapter-loading") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LinearProgressIndicator(Modifier.widthIn(max = 840.dp).fillMaxWidth())
                                }
                            }
                        }
                        if (state.loading && state.pages.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "切换到第 ${state.currentChapterIndex + 1} 话",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryText
                                    )
                                }
                            }
                        }
                        state.error?.let { error ->
                            item(key = "chapter-error") {
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = 840.dp)
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(error, color = MaterialTheme.colorScheme.error)
                                    GlassCapsuleButton(
                                        text = "重试本章",
                                        onClick = { viewModel.openChapter(state.currentChapterIndex) },
                                        filled = false
                                    )
                                }
                            }
                        }

                        items(state.pages, key = { it }) { page ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncCover(
                                    title = state.info!!.comic.title,
                                    uri = page,
                                    modifier = Modifier
                                        .widthIn(max = 840.dp)
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    contentScale = ContentScale.FillWidth,
                                    maxDimension = 2048,
                                    placeholder = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(420.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(26.dp),
                                                strokeWidth = 2.5.dp
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        if (state.pages.isEmpty() && !state.loading && state.error == null) {
                            item(key = "preparing") {
                                Text(
                                    text = "正在准备本章图片",
                                    modifier = Modifier.padding(20.dp),
                                    color = secondaryText
                                )
                            }
                        }
                        if (state.pages.isNotEmpty() && !state.loading) {
                            item(key = "chapter-end") {
                                val atLastChapter = state.currentChapterIndex >= chapterCount - 1
                                LaunchedEffect(state.currentChapterIndex, atLastChapter) {
                                    if (!atLastChapter) {
                                        delay(650)
                                        viewModel.armNextChapter()
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = 840.dp)
                                        .fillMaxWidth()
                                ) {
                                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                                    Text(
                                        text = "本话结束",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = currentTitle,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        color = secondaryText,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = if (!atLastChapter) "继续下滑，自动阅读下一话" else "已经读完最后一话",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        color = if (canNext) accentText else secondaryText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(24.dp))
                                    Box(Modifier.fillMaxWidth().height(48.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Floating Top GlassGradientBar HUD
            GlassGradientBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                direction = GlassGradientDirection.TOP_TO_BOTTOM,
                state = comicHaze
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCircleButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                        state = comicHaze
                    )
                    LiquidGlassBox(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 520.dp),
                        state = comicHaze,
                        cornerRadius = 22.dp,
                        widgetSize = GlassWidgetSize.SMALL,
                        sceneType = GlassSceneType.IMMERSIVE,
                        interactive = false,
                        shadowElevation = 6.dp
                    ) {
                        Text(
                            text = state.info?.comic?.title ?: "漫画阅读",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                        )
                    }
                    GlassCircleButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "刷新",
                        onClick = { if (!state.loading) viewModel.loadDetail() },
                        tint = if (state.loading) secondaryText.copy(alpha = 0.45f) else primaryText,
                        state = comicHaze
                    )
                }
            }

            // Floating Bottom LiquidGlassBox Chapter HUD
            if (state.info != null) {
                LiquidGlassBox(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .widthIn(max = 640.dp)
                        .fillMaxWidth(),
                    state = comicHaze,
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
                        GlassCapsuleButton(
                            text = "上一话",
                            onClick = { viewModel.openChapter(state.currentChapterIndex - 1) },
                            filled = false,
                            enabled = canPrevious,
                            state = comicHaze
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showChapterPicker = true }
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "第 $currentNumber / $chapterCount 话 · 目录",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText
                            )
                            Text(
                                text = currentTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryText
                            )
                        }
                        GlassCapsuleButton(
                            text = "下一话",
                            onClick = { viewModel.openChapter(state.currentChapterIndex + 1) },
                            filled = true,
                            enabled = canNext,
                            state = comicHaze
                        )
                    }
                }
            }

            if (showChapterPicker && state.info != null) {
                val chapters = state.info!!.chapters
                val pickerState = rememberLazyListState(
                    initialFirstVisibleItemIndex = (state.currentChapterIndex - 2).coerceAtLeast(0)
                )
                Dialog(onDismissRequest = { showChapterPicker = false }) {
                    GlassSectionCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 520.dp)
                            .heightIn(max = 520.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "章节目录（共 ${chapters.size} 话）",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { showChapterPicker = false }) {
                                    Text("关闭")
                                }
                            }
                            HorizontalDivider()
                            LazyColumn(
                                state = pickerState,
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(chapters, key = { idx, item -> "${idx}_${item.id}" }) { index, chap ->
                                    val selected = index == state.currentChapterIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                showChapterPicker = false
                                                viewModel.openChapter(index)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${index + 1}. ${chap.title}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (selected) accentText else primaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (chap.isVip) {
                                            Text(
                                                text = "VIP",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = secondaryText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SfComicRow(comic: SfComic, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val accentText = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary

    LiquidGlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        cornerRadius = 20.dp,
        widgetSize = GlassWidgetSize.SMALL,
        sceneType = GlassSceneType.GENERAL,
        interactive = false,
        skipRenderLayer = true,
        shadowElevation = 5.dp,
        borderWidth = 0.7.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AsyncCover(
                title = comic.title,
                uri = comic.cover,
                modifier = Modifier
                    .width(62.dp)
                    .height(84.dp),
                shape = RoundedCornerShape(10.dp),
                maxDimension = 384
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = comic.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (comic.author.isNotBlank()) {
                    Text(
                        text = comic.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (comic.category.isNotBlank()) {
                    Text(
                        text = comic.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
