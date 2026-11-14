package com.novelai.assistant.ui.reader

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.db.BookSourceType
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.prefs.PageMode
import com.novelai.assistant.data.prefs.ReaderBgTheme
import com.novelai.assistant.data.sf.SfFailureKind
import com.novelai.assistant.ui.components.glassSource
import com.novelai.assistant.ui.components.rememberGlassState
import com.novelai.assistant.ui.discover.SfBookToolsDialog
import com.novelai.assistant.ui.theme.NovelAITheme
import com.novelai.assistant.ui.theme.ReaderPalettes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    onGenerate: (bookId: String, chapterId: String, mode: String) -> Unit,
    onOpenTree: (String) -> Unit,
    onAsk: (bookId: String, quote: String, action: String) -> Unit,
    onOpenDetail: ((String) -> Unit)? = null,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val chapterIndex by viewModel.chapterIndex.collectAsStateWithLifecycle()
    val currentChapter by viewModel.currentChapter.collectAsStateWithLifecycle()
    val chapterError by viewModel.chapterError.collectAsStateWithLifecycle()
    val chapterFailureKind by viewModel.chapterFailureKind.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
    var sfToolsOpen by remember { mutableStateOf(false) }
    val chromeVisible by viewModel.chromeVisible.collectAsStateWithLifecycle()
    val tocOpen by viewModel.tocOpen.collectAsStateWithLifecycle()
    val settingsOpen by viewModel.settingsOpen.collectAsStateWithLifecycle()
    val tocFilter by viewModel.tocFilter.collectAsStateWithLifecycle()
    val tocItems by viewModel.tocItems.collectAsStateWithLifecycle()
    val quotedText by viewModel.quotedText.collectAsStateWithLifecycle()
    val paragraphCommentsQuote by viewModel.paragraphCommentsQuote.collectAsStateWithLifecycle()
    val paragraphComments by viewModel.paragraphComments.collectAsStateWithLifecycle()
    val paragraphCommentsLoading by viewModel.paragraphCommentsLoading.collectAsStateWithLifecycle()
    val paragraphCommentsLoadingMore by viewModel.paragraphCommentsLoadingMore.collectAsStateWithLifecycle()
    val paragraphCommentsError by viewModel.paragraphCommentsError.collectAsStateWithLifecycle()
    val paragraphCommentsSort by viewModel.paragraphCommentsSort.collectAsStateWithLifecycle()
    val paragraphCommentsOpen by viewModel.paragraphCommentsOpen.collectAsStateWithLifecycle()
    val paragraphCommentCounts by viewModel.paragraphCommentCounts.collectAsStateWithLifecycle()
    val replyThreads by viewModel.replyThreads.collectAsStateWithLifecycle()
    val expandedParagraphReplies by viewModel.expandedParagraphReplies.collectAsStateWithLifecycle()
    val ttsSpeaking by viewModel.ttsSpeaking.collectAsStateWithLifecycle()
    val ttsPaused by viewModel.ttsPaused.collectAsStateWithLifecycle()
    val ttsReady by viewModel.ttsReady.collectAsStateWithLifecycle()
    val ttsPosition by viewModel.ttsPosition.collectAsStateWithLifecycle()
    val ttsSpeed by viewModel.ttsSpeed.collectAsStateWithLifecycle()
    val ttsTimerMode by viewModel.ttsTimerMode.collectAsStateWithLifecycle()
    val ttsTimerRemainingSeconds by viewModel.ttsTimerRemainingSeconds.collectAsStateWithLifecycle()
    val isCurrentBookSpeaking by viewModel.isCurrentBookSpeaking.collectAsStateWithLifecycle()
    val activeSpeaking = isCurrentBookSpeaking && ttsSpeaking
    val activePaused = isCurrentBookSpeaking && ttsPaused
    val activePosition = if (isCurrentBookSpeaking) ttsPosition else -1
    var timerSheetOpen by remember { mutableStateOf(false) }

    val palette = ReaderPalettes.of(settings.bgTheme.name)
    val bgColor = Color(palette.background)

    val isReaderBgDark = settings.bgTheme == ReaderBgTheme.DARK || settings.bgTheme == ReaderBgTheme.BLACK
    val isThemeDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // 弹窗与控制栏深浅色：阅读底色暗色时强制暗色，否则跟随应用主题（修复跟随系统与应用主题切换被覆盖问题）
    val isEffectiveDark = isReaderBgDark || isThemeDark
    val isDarkReader = isEffectiveDark

    // 控制栏文本与图标色：深色控制栏固定使用高对比度浅色文本，浅色控制栏使用调色板配置
    val onChromeColor = if (isDarkReader) Color(0xFFF2F2F7) else Color(palette.onChrome)
    val secondaryColor = if (isDarkReader) Color(0xFFA5A5B2) else Color(palette.secondaryText)
    val haptics = LocalHapticFeedback.current

    var exportMarkdown by remember { mutableStateOf(true) }
    var selectionMode by remember { mutableStateOf(false) }
    var actionChapter by remember { mutableStateOf<ChapterEntity?>(null) }
    var renameChapterTarget by remember { mutableStateOf<ChapterEntity?>(null) }
    var renameTitleDraft by remember { mutableStateOf("") }
    var confirmDeleteChapter by remember { mutableStateOf<ChapterEntity?>(null) }
    var currentVisibleParaIndex by remember { mutableIntStateOf(0) }

    // 阅读亮度（1.0 = 跟随系统）
    var brightness by remember { mutableFloatStateOf(1f) }
    val view = LocalView.current
    // 后台支持持续听书，由前台服务与通知栏控制，不随页面销毁强制中断
    DisposableEffect(brightness) {
        val window = (view.context as? Activity)?.window
        val old = window?.attributes?.screenBrightness
        if (window != null) {
            window.attributes = window.attributes.apply {
                screenBrightness = if (brightness >= 0.99f) {
                    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                } else brightness
            }
        }
        onDispose {
            if (window != null && old != null) {
                window.attributes = window.attributes.apply { screenBrightness = old }
            }
        }
    }

    // 状态栏图标：依据当前书页背景真实亮度自适应（浅色背景用深色图标，深色背景用浅色图标）
    val isBgDark = bgColor.luminance() < 0.5f
    DisposableEffect(isBgDark) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val original = controller?.isAppearanceLightStatusBars
        if (controller != null) controller.isAppearanceLightStatusBars = !isBgDark
        onDispose {
            if (controller != null && original != null) controller.isAppearanceLightStatusBars = original
        }
    }

    val context = LocalContext.current
    LaunchedEffect(exportMessage) {
        exportMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearExportMessage()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 通知权限授权结果 */ }

    fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> viewModel.exportChapter(uri, exportMarkdown) }

    // 目录列表滚动状态：打开抽屉时自动定位到当前阅读章节
    val tocListState = rememberLazyListState()
    LaunchedEffect(tocOpen, tocFilter) {
        if (tocOpen && tocItems.isNotEmpty()) {
            val targetIdx = tocItems.indexOfFirst { it.chapter.id == currentChapter?.id }
            if (targetIdx >= 0) {
                val scrollPos = (targetIdx - 2).coerceAtLeast(0)
                tocListState.scrollToItem(scrollPos)
            }
        }
    }

    BackHandler(
        enabled = selectionMode || tocOpen || settingsOpen || timerSheetOpen ||
            quotedText != null || paragraphCommentsOpen ||
            actionChapter != null || renameChapterTarget != null || confirmDeleteChapter != null
    ) {
        when {
            selectionMode -> {
                selectionMode = false
                viewModel.clearQuote()
            }
            actionChapter != null -> actionChapter = null
            renameChapterTarget != null -> renameChapterTarget = null
            confirmDeleteChapter != null -> confirmDeleteChapter = null
            timerSheetOpen -> timerSheetOpen = false
            tocOpen -> viewModel.closeToc()
            settingsOpen -> viewModel.closeSettings()
            paragraphCommentsOpen -> viewModel.closeParagraphComments()
            quotedText != null -> viewModel.clearQuote()
        }
    }

    val statusBarPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val readerHazeState = rememberGlassState()
    val accent = if (isDarkReader) Color(0xFF9A97FF) else MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // ---------- 内容（标记为 Haze 实时背景采样源） ----------
        Box(
            Modifier
                .fillMaxSize()
                .glassSource(readerHazeState)
        ) {
            currentChapter?.takeIf { it.content.isNotBlank() }?.let { chapter ->
                val paragraphs = remember(chapter.id, chapter.content) { Paginator.splitParagraphs(chapter.content) }
                val content: @Composable () -> Unit = {
                    if (settings.pageMode == PageMode.PAGED) {
                        PagedContent(
                            chapterId = chapter.id,
                            readPosition = { viewModel.readPosition(chapter.id) },
                            savePosition = { viewModel.savePosition(chapter.id, it) },
                            paragraphs = paragraphs,
                            settings = settings,
                            palette = palette,
                            statusBarPad = statusBarPad,
                            navBarPad = navBarPad,
                            chromeVisible = chromeVisible,
                            ttsSpeaking = activeSpeaking,
                            ttsPaused = activePaused,
                            ttsPosition = activePosition,
                            isDarkReader = isDarkReader,
                            onToggleChrome = { viewModel.toggleChrome() },
                            onQuote = { text, idx -> viewModel.quoteParagraph(text, idx) },
                            commentCounts = paragraphCommentCounts,
                            onOpenComments = { text, idx ->
                                viewModel.openParagraphComments(quote = text, row = idx)
                            },
                            onPageTurn = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.scheduleProgressSave()
                            },
                            onVisibleParaChange = { currentVisibleParaIndex = it },
                            onNextChapter = { viewModel.openNextChapter() },
                            onPrevChapter = { viewModel.openPreviousChapter() },
                            hasNextChapter = chapterIndex < chapters.lastIndex,
                            hasPrevChapter = chapterIndex > 0,
                            selectionMode = selectionMode,
                            targetPageProvider = { viewModel.consumeTargetPage() }
                        )
                    } else {
                        ScrollContent(
                            chapterId = chapter.id,
                            readPosition = { viewModel.readPosition(chapter.id) },
                            savePosition = { viewModel.savePosition(chapter.id, it) },
                            paragraphs = paragraphs,
                            settings = settings,
                            palette = palette,
                            statusBarPad = statusBarPad,
                            navBarPad = navBarPad,
                            ttsSpeaking = activeSpeaking,
                            ttsPaused = activePaused,
                            ttsPosition = activePosition,
                            isDarkReader = isDarkReader,
                            onToggleChrome = { viewModel.toggleChrome() },
                            onQuote = { text, idx -> viewModel.quoteParagraph(text, idx) },
                            commentCounts = paragraphCommentCounts,
                            onOpenComments = { text, idx ->
                                viewModel.openParagraphComments(quote = text, row = idx)
                            },
                            onProgress = { viewModel.scheduleProgressSave() },
                            onVisibleParaChange = { currentVisibleParaIndex = it },
                            selectionMode = selectionMode
                        )
                    }
                }
                if (selectionMode) {
                    SelectionContainer(modifier = Modifier.fillMaxSize()) { content() }
                } else {
                    content()
                }
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (chapterError == null) {
                        CircularProgressIndicator(color = Color(palette.text))
                    } else {
                        Text(chapterError.orEmpty(), color = Color(palette.text))
                        TextButton(onClick = { viewModel.retryChapter() }) { Text("重新加载") }
                        if (chapterFailureKind == SfFailureKind.AUTH) {
                            Text("请返回菠萝包页登录账号", color = Color(palette.text), style = MaterialTheme.typography.bodySmall)
                        } else if (chapterFailureKind == SfFailureKind.SIGNATURE) {
                            Text("这不是账号封禁提示。请检查设备日期与时间，稍后重试。", color = Color(palette.text), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // ---------- 顶栏（浮动液态玻璃岛） ----------
        ReaderTopBar(
            visible = chromeVisible && !tocOpen,
            statusBarPad = statusBarPad,
            hazeState = readerHazeState,
            isDarkReader = isDarkReader,
            palette = palette,
            onChromeColor = onChromeColor,
            secondaryColor = secondaryColor,
            accent = accent,
            bookTitle = book?.title ?: "",
            chapterTitle = currentChapter?.title ?: "",
            sourceType = book?.sourceType,
            hasChapterContent = currentChapter?.content?.isNotBlank() == true,
            chapterIndex = chapterIndex,
            totalChapters = chapters.size,
            selectionMode = selectionMode,
            onBack = onBack,
            onOpenDetailOrToc = { onOpenDetail?.invoke(bookId) ?: viewModel.openToc() },
            onToggleNightMode = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.toggleNightMode()
            },
            onOpenSfTools = { sfToolsOpen = true },
            onToggleSelectionMode = {
                selectionMode = !selectionMode
                if (selectionMode) viewModel.clearQuote()
            },
            onExportMarkdown = {
                exportMarkdown = true
                exportLauncher.launch("${currentChapter?.title ?: "chapter"}.md")
            },
            onExportTxt = {
                exportMarkdown = false
                exportLauncher.launch("${currentChapter?.title ?: "chapter"}.txt")
            }
        )

        // ---------- 底栏（浮动液态玻璃控制岛） ----------
        ReaderBottomBar(
            visible = chromeVisible && !tocOpen,
            navBarPad = navBarPad,
            hazeState = readerHazeState,
            isDarkReader = isDarkReader,
            palette = palette,
            onChromeColor = onChromeColor,
            secondaryColor = secondaryColor,
            accent = accent,
            isCurrentBookSpeaking = isCurrentBookSpeaking,
            activeSpeaking = activeSpeaking,
            activePaused = activePaused,
            activePosition = activePosition,
            ttsReady = ttsReady,
            ttsSpeed = ttsSpeed,
            ttsTimerMode = ttsTimerMode,
            ttsTimerRemainingSeconds = ttsTimerRemainingSeconds,
            onOpenTimer = { timerSheetOpen = true },
            onResumeTts = { viewModel.resumeTts() },
            onPauseTts = { viewModel.pauseTts() },
            onStopOrToggleTts = { viewModel.toggleTts() },
            onSetTtsSpeed = { viewModel.setTtsSpeed(it) },
            onOpenToc = { viewModel.openToc() },
            onPrevChapter = { if (chapterIndex > 0) viewModel.setChapterIndex(chapterIndex - 1) },
            onToggleTts = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                checkNotificationPermission()
                viewModel.toggleTts(currentVisibleParaIndex)
            },
            onGenerate = {
                currentChapter?.let { ch ->
                    onGenerate(viewModel.bookId, ch.id, "CONTINUATION")
                }
            },
            onNextChapter = { if (chapterIndex < chapters.lastIndex) viewModel.setChapterIndex(chapterIndex + 1) },
            onOpenSettings = { viewModel.openSettings() }
        )

        // ---------- 划线即问（浮动液态玻璃胶囊） ----------
        val quoteBottomPadding = when {
            chromeVisible && (activeSpeaking || activePaused) -> navBarPad + 180.dp
            chromeVisible -> navBarPad + 104.dp
            activeSpeaking || activePaused -> navBarPad + 104.dp
            else -> navBarPad + 28.dp
        }
        ReaderQuoteBar(
            quotedText = quotedText,
            bottomPadding = quoteBottomPadding,
            hazeState = readerHazeState,
            isDarkReader = isDarkReader,
            isEffectiveDark = isEffectiveDark,
            palette = palette,
            accent = accent,
            isSfacgBook = book?.sourceType == BookSourceType.BOLUOBAO,
            onPlayFromHere = {
                checkNotificationPermission()
                viewModel.playFromQuotedParagraph()
            },
            onAskAction = { text, action ->
                viewModel.clearQuote()
                onAsk(viewModel.bookId, text, action)
            },
            onOpenComments = { quote ->
                val row = viewModel.quotedParaIndex.value
                viewModel.clearQuote()
                viewModel.openParagraphComments(quote = quote, row = row)
            },
            onDismiss = { viewModel.clearQuote() }
        )

        // ---------- 文字选择模式底部提示浮岛 ----------
        ReaderSelectionHintBar(
            visible = selectionMode && quotedText == null,
            navBarPad = navBarPad,
            isDarkReader = isDarkReader,
            onExitSelectionMode = {
                selectionMode = false
                viewModel.clearQuote()
            }
        )

        if (paragraphCommentsOpen) {
            ParagraphCommentsSheet(
                comments = paragraphComments,
                loading = paragraphCommentsLoading,
                loadingMore = paragraphCommentsLoadingMore,
                error = paragraphCommentsError,
                sort = paragraphCommentsSort,
                paragraph = paragraphCommentsQuote,
                chapterTitle = currentChapter?.title.orEmpty(),
                expandedReplies = expandedParagraphReplies,
                replyThreads = replyThreads,
                onRetryReplies = viewModel::loadReplies,
                onSort = { viewModel.openParagraphComments(it) },
                onLoadMore = { viewModel.loadMoreParagraphComments() },
                onToggleReplies = { viewModel.toggleParagraphReplies(it) },
                onRetry = { viewModel.openParagraphComments(quote = paragraphCommentsQuote) },
                onDismiss = { viewModel.closeParagraphComments() }
            )
        }

        // ---------- 目录抽屉 ----------
        ReaderTocDrawer(
            visible = tocOpen,
            bgColor = bgColor,
            palette = palette,
            isEffectiveDark = isEffectiveDark,
            accent = accent,
            statusBarPad = statusBarPad,
            navBarPad = navBarPad,
            totalChapters = chapters.size,
            tocFilter = tocFilter,
            tocItems = tocItems,
            currentChapterId = currentChapter?.id,
            tocListState = tocListState,
            onDismiss = { viewModel.closeToc() },
            onSelectFilter = { viewModel.setTocFilter(it) },
            onSelectChapter = { ch ->
                viewModel.setChapterIndex(ch.chapterIndex)
                viewModel.closeToc()
            },
            onLongClickChapter = { ch ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                actionChapter = ch
            }
        )

        // ---------- 目录长按操作底板与弹窗 ----------
        actionChapter?.let { ch ->
            ChapterActionSheet(
                chapter = ch,
                isEffectiveDark = isEffectiveDark,
                onDismiss = { actionChapter = null },
                onRename = { target ->
                    actionChapter = null
                    renameChapterTarget = target
                    renameTitleDraft = target.title
                },
                onDelete = { target ->
                    actionChapter = null
                    confirmDeleteChapter = target
                }
            )
        }

        renameChapterTarget?.let { target ->
            RenameChapterDialog(
                draft = renameTitleDraft,
                isEffectiveDark = isEffectiveDark,
                onDraftChange = { renameTitleDraft = it },
                onConfirm = {
                    viewModel.renameChapter(target.id, renameTitleDraft)
                    renameChapterTarget = null
                },
                onDismiss = { renameChapterTarget = null }
            )
        }

        confirmDeleteChapter?.let { target ->
            DeleteChapterConfirmDialog(
                chapterTitle = target.title,
                isEffectiveDark = isEffectiveDark,
                onConfirm = {
                    viewModel.deleteChapter(target.id)
                    confirmDeleteChapter = null
                },
                onDismiss = { confirmDeleteChapter = null }
            )
        }

        // ---------- 阅读设置 ----------
        if (settingsOpen) {
            NovelAITheme(darkTheme = isEffectiveDark) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.closeSettings() },
                    containerColor = if (isEffectiveDark) Color(0xFF181820) else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = if (isEffectiveDark) Color(0xFFE8E8EE) else MaterialTheme.colorScheme.onSurface,
                    dragHandle = {
                        BottomSheetDefaults.DragHandle(
                            color = if (isEffectiveDark) Color(0xFF5A5A66) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                ) {
                    ReaderSettingsSheet(
                        settings = settings,
                        brightness = brightness,
                        isDark = isEffectiveDark,
                        timerMode = ttsTimerMode,
                        timerRemainingSeconds = ttsTimerRemainingSeconds,
                        onBrightnessChange = { brightness = it },
                        onFontSizeChange = viewModel::setFontSize,
                        onLineSpacingChange = viewModel::setLineSpacing,
                        onHorizontalPaddingChange = viewModel::setHorizontalPadding,
                        onParagraphSpacingChange = viewModel::setParagraphSpacing,
                        onBgThemeChange = viewModel::setBgTheme,
                        onSerifChange = viewModel::setSerif,
                        onPageModeChange = viewModel::setPageMode,
                        onOpenTimer = {
                            viewModel.closeSettings()
                            timerSheetOpen = true
                        }
                    )
                }
            }
        }

        if (sfToolsOpen) {
            SfBookToolsDialog(bookId, chapterIndex.coerceAtLeast(0), { sfToolsOpen = false })
        }

        // ---------- 听书定时面板 ----------
        if (timerSheetOpen) {
            NovelAITheme(darkTheme = isEffectiveDark) {
                TtsTimerSheet(
                    currentMode = ttsTimerMode,
                    remainingSeconds = ttsTimerRemainingSeconds,
                    isDark = isEffectiveDark,
                    onSelectMode = { viewModel.setTtsTimerMode(it) },
                    onDismiss = { timerSheetOpen = false }
                )
            }
        }
    }
}
