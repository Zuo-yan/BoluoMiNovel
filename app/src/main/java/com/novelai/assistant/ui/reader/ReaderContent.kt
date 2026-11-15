package com.novelai.assistant.ui.reader

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelai.assistant.data.prefs.ReadingSettings
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.theme.ReaderPalette
import com.novelai.assistant.ui.theme.readerFontFamily
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun ttsParagraphHighlightColor(
    isActive: Boolean,
    isCurrentParagraph: Boolean,
    isDarkReader: Boolean
): Color = if (isActive && isCurrentParagraph) {
    if (isDarkReader) Color(0xFF9A97FF).copy(alpha = 0.22f)
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
} else {
    Color.Transparent
}

/**
 * A quiet, persistent affordance beside paragraphs that have discussions.
 * Zero-count rows remain visually empty, so local/plain chapters do not gain
 * noise or misleading controls.
 */
@Composable
internal fun InlineTsukkomiMarker(
    count: Int,
    dark: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return
    val accent = if (dark) Color(0xFFFF8AA4) else Color(0xFFD94F70)
    Box(
        modifier = modifier
            .width(26.dp)
            .heightIn(min = 32.dp)
            .clickable(onClick = onOpen)
            .semantics(mergeDescendants = true) {
                contentDescription = "$count 条段落吐槽，点击查看"
            },
        contentAlignment = Alignment.TopEnd
    ) {
        Icon(
            Icons.Rounded.Forum,
            contentDescription = null,
            tint = accent.copy(alpha = 0.82f),
            modifier = Modifier
                .padding(top = 5.dp)
                .size(17.dp)
        )
        Badge(
            containerColor = accent,
            contentColor = Color.White,
            modifier = Modifier.semantics { contentDescription = "" }
        ) {
            Text(
                if (count > 99) "99+" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = "" }
            )
        }
    }
}

/** 翻页模式 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PagedContent(
    chapterId: String,
    readPosition: () -> ReadingAnchor,
    savePosition: (ReadingAnchor) -> Unit,
    paragraphs: List<String>,
    settings: ReadingSettings,
    palette: ReaderPalette,
    statusBarPad: Dp,
    navBarPad: Dp,
    chromeVisible: Boolean,
    ttsSpeaking: Boolean,
    ttsPaused: Boolean,
    ttsPosition: Int,
    isDarkReader: Boolean,
    onToggleChrome: () -> Unit,
    onQuote: (String, Int) -> Unit,
    commentCounts: Map<Int, Int>,
    onOpenComments: (String, Int) -> Unit,
    onPageTurn: () -> Unit,
    onVisibleParaChange: (Int) -> Unit,
    onNextChapter: () -> Unit,
    onPrevChapter: () -> Unit,
    hasNextChapter: Boolean,
    hasPrevChapter: Boolean,
    selectionMode: Boolean,
    targetPageProvider: () -> Int?,
    anchorVersion: Long = 0L
) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val adaptive = LocalAdaptiveMetrics.current

    val readerStyle = TextStyle(
        fontSize = settings.fontSizeSp.sp,
        lineHeight = (settings.fontSizeSp * settings.lineSpacingMultiplier).sp,
        fontFamily = readerFontFamily(settings.serifFont),
        color = Color(palette.text)
    )
    val hPadPx = with(density) { settings.horizontalPaddingDp.dp.roundToPx() }
    val paraSpacingPx = with(density) { settings.paragraphSpacingDp.dp.roundToPx() }
    val markerReservePx = with(density) { 28.dp.roundToPx() }
    val columnGutterDp = 40.dp
    val columnGutterPx = with(density) { columnGutterDp.roundToPx() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 横屏展开态平板（且可用画布宽度与长宽比充足时）启用双栏书页展开；否则回退为舒适单栏页宽
        val isDualPage = adaptive.dualPageReaderEnabled &&
            maxWidth >= 720.dp &&
            maxWidth >= maxHeight * 1.1f

        val singleContainerWidthDp = maxWidth.coerceAtMost(adaptive.maxReadableWidth)
        val dualMaxSpreadDp = adaptive.maxReadableWidth * 2 + columnGutterDp + (settings.horizontalPaddingDp * 2).dp
        val spreadContainerWidthDp = maxWidth.coerceAtMost(dualMaxSpreadDp)

        val singleWidthPx = with(density) { singleContainerWidthDp.roundToPx() }.coerceAtMost(constraints.maxWidth)
        val spreadWidthPx = with(density) { spreadContainerWidthDp.roundToPx() }.coerceAtMost(constraints.maxWidth)

        val measureWidthPx = if (isDualPage) {
            Paginator.dualPageColumnWidthPx(spreadWidthPx, hPadPx, columnGutterPx) - markerReservePx
        } else {
            singleWidthPx - hPadPx * 2 - markerReservePx
        }
        val chromeTopPx = with(density) { (statusBarPad + 72.dp).roundToPx() }
        val chromeBottomPx = with(density) { (navBarPad + 64.dp).roundToPx() }
        val heightPx = constraints.maxHeight - chromeTopPx - chromeBottomPx

        val pages = remember(
            chapterId, paragraphs, settings.fontSizeSp, settings.lineSpacingMultiplier,
            settings.serifFont, measureWidthPx, heightPx, settings.paragraphSpacingDp
        ) {
            Paginator.paginate(
                paragraphs,
                textMeasurer,
                readerStyle,
                measureWidthPx,
                heightPx,
                paraSpacingPx
            )
        }

        val pagerCount = Paginator.spreadCount(pages.size, isDualPage)
        val pagerState = rememberPagerState(pageCount = { pagerCount })

        var positionReady by remember(chapterId, pages, isDualPage) { mutableStateOf(false) }
        LaunchedEffect(chapterId, pages, isDualPage, anchorVersion) {
            positionReady = false
            val anchor = readPosition()
            val target = targetPageProvider()
            if (target == -1) {
                pagerState.scrollToPage((pagerCount - 1).coerceAtLeast(0))
            } else if (target != null && target >= 0) {
                val targetSpread = Paginator.spreadIndexForPage(
                    target.coerceIn(0, pages.lastIndex.coerceAtLeast(0)),
                    isDualPage
                )
                pagerState.scrollToPage(targetSpread)
            } else {
                val restored = Paginator.pageForAnchor(pages, anchor)
                pagerState.scrollToPage(Paginator.spreadIndexForPage(restored, isDualPage))
            }
            positionReady = true
        }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { onPageTurn() }
        }

        // 通知当前页起始段落（供听书从当前页起播）
        LaunchedEffect(pagerState.currentPage, pages, positionReady, isDualPage) {
            if (!positionReady) return@LaunchedEffect
            val leftPageIndex = if (isDualPage) pagerState.currentPage * 2 else pagerState.currentPage
            pages.getOrNull(leftPageIndex)?.fragments?.firstOrNull()?.let {
                savePosition(ReadingAnchor(it.paraIndex, it.startOffset))
            }
            val firstPara = pages.getOrNull(leftPageIndex)?.fragments?.firstOrNull()?.paraIndex ?: 0
            onVisibleParaChange(firstPara)
        }

        // 听书进度驱动翻页（双栏模式自动定位到包含目标段落的那一屏）
        LaunchedEffect(ttsPosition, ttsSpeaking, ttsPaused, isDualPage, pages) {
            if ((ttsSpeaking || ttsPaused) && ttsPosition >= 0) {
                val targetPage = pages.indexOfFirst { page ->
                    page.fragments.any { it.paraIndex == ttsPosition }
                }
                if (targetPage >= 0) {
                    val targetSpread = Paginator.spreadIndexForPage(targetPage, isDualPage)
                    if (targetSpread != pagerState.currentPage) {
                        pagerState.animateScrollToPage(targetSpread)
                    }
                }
            }
        }

        fun handleTap(screenX: Float, screenWidth: Float) {
            if (chromeVisible) {
                onToggleChrome()
            } else {
                when {
                    screenX < screenWidth * 0.33f -> {
                        // 左侧点击 -> 上一页（双栏退 2 页）/ 上一章
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        } else if (hasPrevChapter) {
                            onPrevChapter()
                        } else {
                            Toast.makeText(context, "已经是第一章了", Toast.LENGTH_SHORT).show()
                        }
                    }
                    screenX > screenWidth * 0.67f -> {
                        // 右侧点击 -> 下一页（双栏进 2 页）/ 下一章
                        if (pagerState.currentPage < pagerCount - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else if (hasNextChapter) {
                            onNextChapter()
                        } else {
                            Toast.makeText(context, "已经是最后一章了", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        // 中间点击 -> 呼出/隐藏菜单
                        onToggleChrome()
                    }
                }
            }
        }

        val swipeModifier = if (selectionMode) Modifier else Modifier.pointerInput(
            chapterId, pagerCount, hasNextChapter, hasPrevChapter, pagerState.currentPage
        ) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    var totalDx = 0f
                    var totalDy = 0f
                    val pointerId = down.id
                    val isAtEnd = pagerState.currentPage >= pagerCount - 1
                    val isAtStart = pagerState.currentPage == 0

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val dragChange = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!dragChange.pressed) {
                            val threshold = 50.dp.toPx()
                            val isHorizontalSwipe = kotlin.math.abs(totalDx) > threshold &&
                                kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy) * 1.3f
                            if (isHorizontalSwipe) {
                                if (totalDx < -threshold && isAtEnd) {
                                    if (hasNextChapter) {
                                        onNextChapter()
                                    } else {
                                        Toast.makeText(context, "已经是最后一章了", Toast.LENGTH_SHORT).show()
                                    }
                                } else if (totalDx > threshold && isAtStart) {
                                    if (hasPrevChapter) {
                                        onPrevChapter()
                                    } else {
                                        Toast.makeText(context, "已经是第一章了", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            break
                        }
                        val delta = dragChange.position - dragChange.previousPosition
                        totalDx += delta.x
                        totalDy += delta.y
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(swipeModifier)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { spreadIndex ->
                val leftIndex = if (isDualPage) spreadIndex * 2 else spreadIndex
                val leftPage = pages.getOrNull(leftIndex) ?: return@HorizontalPager
                val rightPage = if (isDualPage) pages.getOrNull(leftIndex + 1) else null

                var pageLeftX by remember { mutableFloatStateOf(0f) }
                var pageWidthPx by remember { mutableFloatStateOf(1f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coords ->
                            pageLeftX = coords.positionInRoot().x
                            pageWidthPx = coords.size.width.toFloat().coerceAtLeast(1f)
                        }
                        .pointerInput(selectionMode, chromeVisible, pagerState.currentPage, pagerCount, hasNextChapter, hasPrevChapter) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val screenX = offset.x
                                    val screenWidth = size.width.toFloat()
                                    if (!selectionMode) {
                                        handleTap(screenX, screenWidth)
                                    } else {
                                        // 选择模式下点击屏幕中央区域依然可呼出/收起控制栏，防止控制菜单唤醒失效
                                        if (screenX in (screenWidth * 0.33f)..(screenWidth * 0.67f)) {
                                            handleTap(screenX, screenWidth)
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (isDualPage && pages.size > 1) {
                        Row(
                            modifier = Modifier
                                .width(spreadContainerWidthDp)
                                .fillMaxHeight()
                                .padding(horizontal = settings.horizontalPaddingDp.dp)
                                .padding(top = statusBarPad + 72.dp, bottom = navBarPad + 64.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            ReaderPageColumn(
                                page = leftPage,
                                paragraphs = paragraphs,
                                readerStyle = readerStyle,
                                paragraphSpacingDp = settings.paragraphSpacingDp.dp,
                                heightPx = heightPx,
                                ttsSpeaking = ttsSpeaking,
                                ttsPaused = ttsPaused,
                                ttsPosition = ttsPosition,
                                isDarkReader = isDarkReader,
                                selectionMode = selectionMode,
                                commentCounts = commentCounts,
                                onQuote = onQuote,
                                onOpenComments = onOpenComments,
                                onTapAtRootX = { rootX -> handleTap(rootX - pageLeftX, pageWidthPx) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            // 中缝脊线分隔（0.5.dp 微妙书脊线）
                            Box(
                                modifier = Modifier
                                    .width(columnGutterDp)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(0.5.dp)
                                        .fillMaxHeight(0.92f)
                                        .background(Color(palette.secondaryText).copy(alpha = 0.22f))
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                if (rightPage != null) {
                                    ReaderPageColumn(
                                        page = rightPage,
                                        paragraphs = paragraphs,
                                        readerStyle = readerStyle,
                                        paragraphSpacingDp = settings.paragraphSpacingDp.dp,
                                        heightPx = heightPx,
                                        ttsSpeaking = ttsSpeaking,
                                        ttsPaused = ttsPaused,
                                        ttsPosition = ttsPosition,
                                        isDarkReader = isDarkReader,
                                        selectionMode = selectionMode,
                                        commentCounts = commentCounts,
                                        onQuote = onQuote,
                                        onOpenComments = onOpenComments,
                                        onTapAtRootX = { rootX -> handleTap(rootX - pageLeftX, pageWidthPx) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    } else if (isDualPage) {
                        val dualSingleColWidthDp = (spreadContainerWidthDp - columnGutterDp) / 2
                        ReaderPageColumn(
                            page = leftPage,
                            paragraphs = paragraphs,
                            readerStyle = readerStyle,
                            paragraphSpacingDp = settings.paragraphSpacingDp.dp,
                            heightPx = heightPx,
                            ttsSpeaking = ttsSpeaking,
                            ttsPaused = ttsPaused,
                            ttsPosition = ttsPosition,
                            isDarkReader = isDarkReader,
                            selectionMode = selectionMode,
                            commentCounts = commentCounts,
                            onQuote = onQuote,
                            onOpenComments = onOpenComments,
                            onTapAtRootX = { rootX -> handleTap(rootX - pageLeftX, pageWidthPx) },
                            modifier = Modifier
                                .width(dualSingleColWidthDp)
                                .fillMaxHeight()
                                .padding(horizontal = settings.horizontalPaddingDp.dp)
                                .padding(top = statusBarPad + 72.dp, bottom = navBarPad + 64.dp)
                        )
                    } else {
                        ReaderPageColumn(
                            page = leftPage,
                            paragraphs = paragraphs,
                            readerStyle = readerStyle,
                            paragraphSpacingDp = settings.paragraphSpacingDp.dp,
                            heightPx = heightPx,
                            ttsSpeaking = ttsSpeaking,
                            ttsPaused = ttsPaused,
                            ttsPosition = ttsPosition,
                            isDarkReader = isDarkReader,
                            selectionMode = selectionMode,
                            commentCounts = commentCounts,
                            onQuote = onQuote,
                            onOpenComments = onOpenComments,
                            onTapAtRootX = { rootX -> handleTap(rootX - pageLeftX, pageWidthPx) },
                            modifier = Modifier
                                .width(singleContainerWidthDp)
                                .fillMaxHeight()
                                .padding(horizontal = settings.horizontalPaddingDp.dp)
                                .padding(top = statusBarPad + 72.dp, bottom = navBarPad + 64.dp)
                        )
                    }
                }
            }

            // 页码指示（控制栏展开时淡出，避免与底部控制胶囊重叠；双栏显示页码范围）
            val activeLeftIndex = if (isDualPage) pagerState.currentPage * 2 else pagerState.currentPage
            val pageIndicator = if (isDualPage && activeLeftIndex + 1 < pages.size) {
                "${activeLeftIndex + 1}-${activeLeftIndex + 2} / ${pages.size}"
            } else {
                "${(activeLeftIndex + 1).coerceAtMost(pages.size)} / ${pages.size}"
            }
            val indicatorAlpha by animateFloatAsState(
                targetValue = if (chromeVisible) 0f else 1f,
                animationSpec = tween(durationMillis = 160),
                label = "pageIndicatorAlpha"
            )
            Text(
                pageIndicator,
                style = MaterialTheme.typography.labelMedium,
                color = Color(palette.secondaryText),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navBarPad + 22.dp)
                    .graphicsLayer { alpha = indicatorAlpha }
            )
        }
    }
}

@Composable
private fun ReaderPageColumn(
    page: ReaderPage,
    paragraphs: List<String>,
    readerStyle: TextStyle,
    paragraphSpacingDp: Dp,
    heightPx: Int,
    ttsSpeaking: Boolean,
    ttsPaused: Boolean,
    ttsPosition: Int,
    isDarkReader: Boolean,
    selectionMode: Boolean,
    commentCounts: Map<Int, Int>,
    onQuote: (String, Int) -> Unit,
    onOpenComments: (String, Int) -> Unit,
    onTapAtRootX: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val latestOnQuote by rememberUpdatedState(onQuote)
    val latestOnTapAtRootX by rememberUpdatedState(onTapAtRootX)
    Column(modifier = modifier) {
        var lastParaIndex = -1
        page.fragments.forEach { frag ->
            if (lastParaIndex != -1 && frag.paraIndex != lastParaIndex) {
                Spacer(Modifier.height(paragraphSpacingDp))
            }
            lastParaIndex = frag.paraIndex
            val highlightBg = ttsParagraphHighlightColor(
                isActive = ttsSpeaking || ttsPaused,
                isCurrentParagraph = frag.paraIndex == ttsPosition,
                isDarkReader = isDarkReader
            )

            var textLeftRootX by remember(frag.paraIndex, frag.startOffset) { mutableFloatStateOf(0f) }
            val textModifier = if (selectionMode) {
                Modifier
                    .fillMaxWidth()
                    .background(highlightBg, MaterialTheme.shapes.small)
            } else {
                Modifier
                    .fillMaxWidth()
                    .background(highlightBg, MaterialTheme.shapes.small)
                    .onGloballyPositioned { textLeftRootX = it.positionInRoot().x }
                    .pointerInput(selectionMode, frag.paraIndex) {
                        detectTapGestures(
                            onLongPress = { latestOnQuote(paragraphs[frag.paraIndex], frag.paraIndex) },
                            onTap = { offset -> latestOnTapAtRootX(textLeftRootX + offset.x) }
                        )
                    }
            }
            val markerCount = commentCounts[frag.paraIndex] ?: 0
            val illustration = ReaderIllustration.parse(frag.text)
            if (illustration != null) {
                Box(Modifier.fillMaxWidth().height(with(density) { heightPx.toDp() })) {
                    ReaderIllustrationView(illustration, Modifier.fillMaxSize().padding(end = 28.dp))
                    InlineTsukkomiMarker(
                        count = markerCount,
                        dark = isDarkReader,
                        onOpen = { onOpenComments(paragraphs[frag.paraIndex], frag.paraIndex) },
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            } else {
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        text = frag.text,
                        style = readerStyle,
                        modifier = textModifier.padding(end = 28.dp)
                    )
                    InlineTsukkomiMarker(
                        count = markerCount,
                        dark = isDarkReader,
                        onOpen = { onOpenComments(paragraphs[frag.paraIndex], frag.paraIndex) },
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
        }
    }
}

/** 滚动模式 */
@Composable
internal fun ScrollContent(
    chapterId: String,
    readPosition: () -> ReadingAnchor,
    savePosition: (ReadingAnchor) -> Unit,
    paragraphs: List<String>,
    settings: ReadingSettings,
    palette: ReaderPalette,
    statusBarPad: Dp,
    navBarPad: Dp,
    ttsSpeaking: Boolean,
    ttsPaused: Boolean,
    ttsPosition: Int,
    isDarkReader: Boolean,
    onToggleChrome: () -> Unit,
    onQuote: (String, Int) -> Unit,
    commentCounts: Map<Int, Int>,
    onOpenComments: (String, Int) -> Unit,
    onProgress: () -> Unit,
    onVisibleParaChange: (Int) -> Unit,
    selectionMode: Boolean,
    anchorVersion: Long = 0L
) {
    val listState = rememberLazyListState()
    val adaptive = LocalAdaptiveMetrics.current

    LaunchedEffect(chapterId, paragraphs, anchorVersion) {
        val anchor = readPosition()
        listState.scrollToItem(anchor.paragraph.coerceIn(0, paragraphs.lastIndex.coerceAtLeast(0)), anchor.scroll)
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged().collect { (index, offset) ->
                savePosition(ReadingAnchor(index, scroll = offset))
                onProgress()
                onVisibleParaChange(index)
            }
    }

    LaunchedEffect(ttsPosition, ttsSpeaking, ttsPaused) {
        if ((ttsSpeaking || ttsPaused) && ttsPosition in paragraphs.indices) {
            listState.animateScrollToItem(ttsPosition)
        }
    }

    val textStyle = TextStyle(
        fontSize = settings.fontSizeSp.sp,
        lineHeight = (settings.fontSizeSp * settings.lineSpacingMultiplier).sp,
        fontFamily = readerFontFamily(settings.serifFont),
        color = Color(palette.text)
    )

    LazyColumn(
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            start = settings.horizontalPaddingDp.dp,
            end = settings.horizontalPaddingDp.dp,
            top = statusBarPad + 76.dp,
            bottom = navBarPad + 72.dp
        ),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onToggleChrome() }) }
    ) {
        itemsIndexed(paragraphs) { index, para ->
            if (para.isBlank()) {
                Spacer(Modifier.height(settings.paragraphSpacingDp.dp))
                return@itemsIndexed
            }
            val highlightBg = ttsParagraphHighlightColor(
                isActive = ttsSpeaking || ttsPaused,
                isCurrentParagraph = index == ttsPosition,
                isDarkReader = isDarkReader
            )

            Column(
                modifier = Modifier
                    .widthIn(max = adaptive.maxReadableWidth)
                    .fillMaxWidth()
            ) {
                ParagraphText(
                    text = para,
                    style = textStyle,
                    highlightBg = highlightBg,
                    selectionMode = selectionMode,
                    onTap = onToggleChrome,
                    onLongPress = { onQuote(para, index) },
                    commentCount = commentCounts[index] ?: 0,
                    dark = isDarkReader,
                    onOpenComments = { onOpenComments(para, index) }
                )
                Spacer(Modifier.height(settings.paragraphSpacingDp.dp))
            }
        }
    }
}

/**
 * 单个段落：默认模式 tap=呼出菜单、长按=划线即问；
 * 选择模式下交给 SelectionContainer 处理自由选择与复制。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ParagraphText(
    text: String,
    style: TextStyle,
    highlightBg: Color = Color.Transparent,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    commentCount: Int = 0,
    dark: Boolean = false,
    onOpenComments: () -> Unit = {}
) {
    Box(Modifier.fillMaxWidth()) {
        val textModifier = if (selectionMode) {
            Modifier
                .fillMaxWidth()
                .background(highlightBg, MaterialTheme.shapes.small)
        } else {
            Modifier
                .fillMaxWidth()
                .background(highlightBg, MaterialTheme.shapes.small)
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress
                )
        }
        val illustration = ReaderIllustration.parse(text)
        if (illustration != null) {
            ReaderIllustrationView(illustration, Modifier.fillMaxWidth().height(420.dp))
        } else {
            Text(
                text = text,
                style = style,
                modifier = if (commentCount > 0) textModifier.padding(end = 28.dp) else textModifier
            )
        }
        if (commentCount > 0) {
            InlineTsukkomiMarker(
                count = commentCount,
                dark = dark,
                onOpen = onOpenComments,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}
