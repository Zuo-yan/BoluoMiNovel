package com.novelai.assistant.ui.reader

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Check
import java.util.Locale
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.prefs.ReaderBgTheme
import com.novelai.assistant.data.prefs.ReadingSettings
import com.novelai.assistant.data.prefs.PageMode
import com.novelai.assistant.ui.components.OriginBadge
import com.novelai.assistant.ui.components.SegmentedControl
import com.novelai.assistant.ui.theme.ReaderPalettes
import com.novelai.assistant.ui.theme.readerFontFamily
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.luminance
import com.novelai.assistant.ui.theme.NovelColors
import com.novelai.assistant.ui.theme.NovelAITheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var sfToolsOpen by remember { mutableStateOf(false) }
    val chromeVisible by viewModel.chromeVisible.collectAsStateWithLifecycle()
    val tocOpen by viewModel.tocOpen.collectAsStateWithLifecycle()
    val settingsOpen by viewModel.settingsOpen.collectAsStateWithLifecycle()
    val tocFilter by viewModel.tocFilter.collectAsStateWithLifecycle()
    val tocItems by viewModel.tocItems.collectAsStateWithLifecycle()
    val quotedText by viewModel.quotedText.collectAsStateWithLifecycle()
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
    val onChromeColor = Color(palette.onChrome)
    val secondaryColor = Color(palette.secondaryText)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var showExportMenu by remember { mutableStateOf(false) }
    var showGenMenu by remember { mutableStateOf(false) }
    var exportMarkdown by remember { mutableStateOf(true) }
    var selectionMode by remember { mutableStateOf(false) }
    var actionChapter by remember { mutableStateOf<com.novelai.assistant.data.db.ChapterEntity?>(null) }
    var renameChapterTarget by remember { mutableStateOf<com.novelai.assistant.data.db.ChapterEntity?>(null) }
    var renameTitleDraft by remember { mutableStateOf("") }
    var confirmDeleteChapter by remember { mutableStateOf<com.novelai.assistant.data.db.ChapterEntity?>(null) }
    var currentVisibleParaIndex by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // 阅读亮度（1.0 = 跟随系统）
    var brightness by remember { mutableStateOf(1f) }
    val view = androidx.compose.ui.platform.LocalView.current
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

    // 暗色阅读背景下切换状态栏图标为浅色
        val isSystemDark = isSystemInDarkTheme()
    val isThemeDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isReaderBgDark = settings.bgTheme == ReaderBgTheme.DARK || settings.bgTheme == ReaderBgTheme.BLACK
    val isEffectiveDark = isSystemDark || isThemeDark || isReaderBgDark
    val isDarkReader = isEffectiveDark
    DisposableEffect(isDarkReader) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val original = controller?.isAppearanceLightStatusBars
        if (controller != null) controller.isAppearanceLightStatusBars = !isDarkReader
        onDispose {
            if (controller != null && original != null) controller.isAppearanceLightStatusBars = original
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 通知权限授权结果 */ }

    fun checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
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
                // 向上偏移 2 项，让当前章节正好位于屏幕上半部分居中位置
                val scrollPos = (targetIdx - 2).coerceAtLeast(0)
                tocListState.scrollToItem(scrollPos)
            }
        }
    }

    BackHandler(enabled = tocOpen || settingsOpen || timerSheetOpen || quotedText != null || actionChapter != null || renameChapterTarget != null || confirmDeleteChapter != null) {
        when {
            actionChapter != null -> actionChapter = null
            renameChapterTarget != null -> renameChapterTarget = null
            confirmDeleteChapter != null -> confirmDeleteChapter = null
            timerSheetOpen -> timerSheetOpen = false
            tocOpen -> viewModel.closeToc()
            settingsOpen -> viewModel.closeSettings()
            quotedText != null -> viewModel.clearQuote()
        }
    }

    val statusBarPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // ---------- 内容 ----------
        currentChapter?.takeIf { it.content.isNotBlank() }?.let { chapter ->
            val paragraphs = remember(chapter.id, chapter.content) { Paginator.splitParagraphs(chapter.content) }
            val contentModifier: Modifier = Modifier
            val content: @Composable () -> Unit = {
                if (settings.pageMode == PageMode.PAGED) {
                    PagedContent(
                        chapterId = chapter.id,
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
                if (chapterError == null) CircularProgressIndicator(color = Color(palette.text))
                else {
                    Text(chapterError.orEmpty(), color = Color(palette.text))
                    TextButton(onClick = { viewModel.retryChapter() }) { Text("重新加载") }
                    if (chapterFailureKind == com.novelai.assistant.data.sf.SfFailureKind.AUTH) {
                        Text("请返回菠萝包页登录账号", color = Color(palette.text), style = MaterialTheme.typography.bodySmall)
                    } else if (chapterFailureKind == com.novelai.assistant.data.sf.SfFailureKind.SIGNATURE) {
                        Text("这不是账号封禁提示。请检查设备日期与时间，稍后重试。", color = Color(palette.text), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // ---------- 顶栏 ----------
        AnimatedVisibility(
            visible = chromeVisible && !tocOpen,
            enter = slideInVertically(spring(stiffness = 400f)) { -it } + fadeIn(),
            exit = slideOutVertically(spring(stiffness = 400f)) { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color(palette.chrome).copy(alpha = 0.96f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(top = statusBarPad)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = onChromeColor)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                book?.title ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = secondaryColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                currentChapter?.title ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                color = onChromeColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onOpenDetail?.invoke(bookId) ?: viewModel.openToc() }) {
                            Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = "书籍详情", tint = onChromeColor)
                        }
                        // 夜间模式快捷切换
                        IconButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleNightMode()
                        }) {
                            Icon(
                                Icons.Rounded.DarkMode,
                                contentDescription = "夜间模式",
                                tint = if (isDarkReader) Color(0xFFFFD60A) else onChromeColor
                            )
                        }
                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = onChromeColor)
                            }
                            DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                                if (book?.sourceType == com.novelai.assistant.data.db.BookSourceType.BOLUOBAO) {
                                    DropdownMenuItem(text = { Text("下载与导出（TXT / MD / EPUB）") }, onClick = { showExportMenu = false; sfToolsOpen = true })
                                }
                                DropdownMenuItem(
                                    text = { Text(if (selectionMode) "关闭文字选择模式" else "文字选择模式（可复制）") },
                                    onClick = {
                                        showExportMenu = false
                                        selectionMode = !selectionMode
                                        if (selectionMode) viewModel.clearQuote()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("导出本章为 Markdown") },
                                    enabled = currentChapter?.content?.isNotBlank() == true,
                                    onClick = {
                                        showExportMenu = false
                                        exportMarkdown = true
                                        exportLauncher.launch("${currentChapter?.title ?: "chapter"}.md")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("导出本章为 TXT") },
                                    enabled = currentChapter?.content?.isNotBlank() == true,
                                    onClick = {
                                        showExportMenu = false
                                        exportMarkdown = false
                                        exportLauncher.launch("${currentChapter?.title ?: "chapter"}.txt")
                                    }
                                )
                            }
                        }
                    }
                    // 进度条
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(2.dp)
                            .background(secondaryColor.copy(alpha = 0.25f), CircleShape)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((chapters.size.coerceAtLeast(1)).let { total ->
                                    ((chapterIndex + 1).coerceIn(1, total)).toFloat() / total
                                })
                                .height(2.dp)
                                .background(Color(palette.text).copy(alpha = 0.5f), CircleShape)
                        )
                    }
                }
            }
        }

        // ---------- 底栏 ----------
        val accent = if (isDarkReader) Color(0xFF9A97FF) else MaterialTheme.colorScheme.primary
        AnimatedVisibility(
            visible = chromeVisible && !tocOpen,
            enter = slideInVertically(spring(stiffness = 400f)) { it } + fadeIn(),
            exit = slideOutVertically(spring(stiffness = 400f)) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(color = Color(palette.chrome).copy(alpha = 0.96f), modifier = Modifier.fillMaxWidth()) {
                Column {
                // 听书控制胶囊（仅属于当前正在朗读的书籍）
                androidx.compose.animation.AnimatedVisibility(visible = activeSpeaking || activePaused) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .background(Color(palette.text).copy(alpha = 0.08f), MaterialTheme.shapes.large),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (ttsPaused) "已暂停 · 第 ${ttsPosition + 1} 段" else "朗读中 · 第 ${ttsPosition + 1} 段",
                            style = MaterialTheme.typography.labelMedium,
                            color = onChromeColor,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // 定时状态与设置按钮
                        val timerActive = ttsTimerMode != com.novelai.assistant.ui.reader.TtsTimerMode.OFF
                        Surface(
                            shape = CircleShape,
                            color = if (timerActive) accent.copy(alpha = 0.22f) else onChromeColor.copy(alpha = 0.08f),
                            border = if (timerActive) androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.6f)) else null,
                            modifier = Modifier.clickable { timerSheetOpen = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Timer,
                                    contentDescription = "定时",
                                    tint = if (timerActive) accent else onChromeColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                val timerLabel = when {
                                    ttsTimerRemainingSeconds != null -> formatRemaining(ttsTimerRemainingSeconds)
                                    ttsTimerMode == com.novelai.assistant.ui.reader.TtsTimerMode.END_OF_CHAPTER -> "本章停"
                                    else -> "定时"
                                }
                                Text(
                                    timerLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (timerActive) accent else onChromeColor
                                )
                            }
                        }
                        IconButton(onClick = {
                            if (ttsPaused) viewModel.resumeTts() else viewModel.pauseTts()
                        }) {
                            Icon(
                                if (ttsPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = if (ttsPaused) "继续" else "暂停",
                                tint = onChromeColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { viewModel.toggleTts() }) {
                            Icon(
                                Icons.Rounded.Stop,
                                contentDescription = "停止朗读",
                                tint = onChromeColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                // 语速调节
                androidx.compose.animation.AnimatedVisibility(visible = activeSpeaking || activePaused) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "语速",
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryColor
                        )
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { r ->
                            val selected = kotlin.math.abs(ttsSpeed - r) < 0.01f
                            Text(
                                if (r == 1.0f) "1x" else "${r}x",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) accent else onChromeColor.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        if (selected) accent.copy(alpha = 0.18f) else Color.Transparent
                                    )
                                    .clickable { viewModel.setTtsSpeed(r) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .padding(bottom = navBarPad, top = 6.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReaderBarAction(Icons.Rounded.Menu, "目录", onChromeColor) { viewModel.openToc() }
                    ReaderBarAction(Icons.Rounded.KeyboardArrowLeft, "上一章", onChromeColor) {
                        if (chapterIndex > 0) viewModel.setChapterIndex(chapterIndex - 1)
                    }
                    ReaderBarAction(
                        if (isCurrentBookSpeaking) Icons.Rounded.Stop else Icons.Rounded.Headphones,
                        "听书", if (isCurrentBookSpeaking) accent else onChromeColor
                    ) {
                        if (ttsReady) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            checkNotificationPermission()
                            viewModel.toggleTts(currentVisibleParaIndex)
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                currentChapter?.let { ch ->
                                    onGenerate(viewModel.bookId, ch.id, "CONTINUATION")
                                }
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = "AI 续写",
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            "AI 续写",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                    }
                    ReaderBarAction(Icons.Rounded.KeyboardArrowRight, "下一章", onChromeColor) {
                        if (chapterIndex < chapters.lastIndex) viewModel.setChapterIndex(chapterIndex + 1)
                    }
                    ReaderBarAction(Icons.Rounded.Tune, "设置", onChromeColor) { viewModel.openSettings() }
                }
                }
            }
        }

        // ---------- 划线即问 ----------
        val quoteBottomPadding = when {
            chromeVisible && (ttsSpeaking || ttsPaused) -> navBarPad + 168.dp
            chromeVisible -> navBarPad + 84.dp
            ttsSpeaking || ttsPaused -> navBarPad + 84.dp
            else -> navBarPad + 28.dp
        }
        AnimatedVisibility(
            visible = quotedText != null,
            enter = slideInVertically(spring(stiffness = 380f)) { it } + fadeIn(),
            exit = slideOutVertically(spring(stiffness = 380f)) { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = quoteBottomPadding,
                    start = 16.dp,
                    end = 16.dp
                )
        ) {
            Surface(
                shape = CircleShape,
                color = if (isEffectiveDark) Color(0xFF22222E) else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.98f),
                shadowElevation = 10.dp,
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    if (isEffectiveDark) Color(0xFF404050) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    QuoteChip("从此处朗读", Icons.Rounded.PlayArrow, accent) {
                        checkNotificationPermission()
                        viewModel.playFromQuotedParagraph()
                    }
                    QuoteChip("解析深意", Icons.Rounded.AutoAwesome, MaterialTheme.colorScheme.primary) {
                        val text = quotedText ?: ""
                        viewModel.clearQuote()
                        onAsk(viewModel.bookId, text, "解析深意")
                    }
                    QuoteChip("情绪总结", Icons.Rounded.Tune, MaterialTheme.colorScheme.secondary) {
                        val text = quotedText ?: ""
                        viewModel.clearQuote()
                        onAsk(viewModel.bookId, text, "情绪总结")
                    }
                    QuoteChip("AI吐槽", Icons.Rounded.AutoAwesome, Color(0xFFE91E63)) {
                        val text = quotedText ?: ""
                        viewModel.clearQuote()
                        onAsk(viewModel.bookId, text, "AI吐槽")
                    }
                    QuoteChip("选段伴读", Icons.Rounded.AutoAwesome, Color(0xFF7C4DFF)) {
                        val text = quotedText ?: ""
                        viewModel.clearQuote()
                        onAsk(viewModel.bookId, text, "")
                    }
                    IconButton(
                        onClick = { viewModel.clearQuote() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "取消",
                            modifier = Modifier.size(16.dp),
                            tint = if (isEffectiveDark) Color(0xFFA0A0AC) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ---------- 目录抽屉 ----------
        AnimatedVisibility(
            visible = tocOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .pointerInput(Unit) { detectTapGestures { viewModel.closeToc() } }
                )
                AnimatedVisibility(
                    visible = true,
                    enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { -it },
                    exit = slideOutHorizontally { -it },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Surface(
                        color = bgColor,
                        contentColor = Color(palette.text),
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.84f)
                    ) {
                        Column(Modifier.padding(top = statusBarPad + 8.dp)) {
                            Text(
                                "目录 · ${chapters.size} 章",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (isEffectiveDark) Color.White else Color(palette.text),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                            SegmentedControl(
                                options = TocFilter.entries.toList(),
                                selected = tocFilter,
                                labelOf = { it.label },
                                onSelect = { viewModel.setTocFilter(it) },
                                containerColor = if (isEffectiveDark) Color(0xFF22222E) else Color(palette.chrome).copy(alpha = 0.6f),
                                activeColor = if (isEffectiveDark) Color(0xFF323242) else Color.White,
                                selectedTextColor = if (isEffectiveDark) Color.White else Color(palette.text),
                                unselectedTextColor = if (isEffectiveDark) Color(0xFFA0A0AC) else Color(palette.secondaryText),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            LazyColumn(state = tocListState, modifier = Modifier.weight(1f)) {
                                itemsIndexed(tocItems, key = { _, item -> item.chapter.id }) { _, item ->
                                    val isCurrent = item.chapter.id == currentChapter?.id
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isCurrent) accent.copy(alpha = 0.16f)
                                                else Color.Transparent
                                            )
                                            .combinedClickable(
                                                onClick = {
                                                    viewModel.setChapterIndex(item.chapter.chapterIndex)
                                                    viewModel.closeToc()
                                                },
                                                onLongClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    actionChapter = item.chapter
                                                }
                                            )
                                            .padding(horizontal = 20.dp, vertical = 11.dp)
                                            .padding(start = (item.depth * 16).dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OriginBadge(item.chapter.originType.name)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            item.chapter.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isCurrent) accent
                                            else if (isEffectiveDark) Color(0xFFDCDCE4)
                                            else Color(palette.text),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(navBarPad))
                        }
                    }
                }
            }
        }

        // ---------- 目录长按操作底板与弹窗 ----------
        actionChapter?.let { ch ->
            NovelAITheme(darkTheme = isEffectiveDark) {
                ModalBottomSheet(
                    onDismissRequest = { actionChapter = null },
                    containerColor = if (isEffectiveDark) Color(0xFF181820) else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = if (isEffectiveDark) Color(0xFFE8E8EE) else MaterialTheme.colorScheme.onSurface,
                    dragHandle = {
                        BottomSheetDefaults.DragHandle(
                            color = if (isEffectiveDark) Color(0xFF5A5A66) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            OriginBadge(ch.originType.name)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                ch.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isEffectiveDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (isEffectiveDark) Color(0xFF252532) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val target = actionChapter
                                    actionChapter = null
                                    if (target != null) {
                                        renameChapterTarget = target
                                        renameTitleDraft = target.title
                                    }
                                }
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
                                    color = if (isEffectiveDark) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (isEffectiveDark) Color(0xFF361820) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val target = actionChapter
                                    actionChapter = null
                                    confirmDeleteChapter = target
                                }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = if (isEffectiveDark) Color(0xFFFF6B81) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "删除章节",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isEffectiveDark) Color(0xFFFF6B81) else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        renameChapterTarget?.let { target ->
            NovelAITheme(darkTheme = isEffectiveDark) {
                AlertDialog(
                    onDismissRequest = { renameChapterTarget = null },
                    containerColor = if (isEffectiveDark) Color(0xFF20202A) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = if (isEffectiveDark) Color.White else MaterialTheme.colorScheme.onSurface,
                    textContentColor = if (isEffectiveDark) Color(0xFFD0D0D8) else MaterialTheme.colorScheme.onSurfaceVariant,
                    title = { Text("重命名章节") },
                    text = {
                        OutlinedTextField(
                            value = renameTitleDraft,
                            onValueChange = { renameTitleDraft = it },
                            label = { Text("章节名称") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = if (isEffectiveDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = if (isEffectiveDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = if (isEffectiveDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = if (isEffectiveDark) Color(0xFF4A4A58) else MaterialTheme.colorScheme.outline,
                                focusedLabelColor = if (isEffectiveDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = if (isEffectiveDark) Color(0xFFA0A0AC) else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.renameChapter(target.id, renameTitleDraft)
                                renameChapterTarget = null
                            },
                            enabled = renameTitleDraft.isNotBlank()
                        ) {
                            Text(
                                "保存",
                                color = if (renameTitleDraft.isNotBlank()) {
                                    if (isEffectiveDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary
                                } else Color.Gray
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { renameChapterTarget = null }) {
                            Text("取消", color = if (isEffectiveDark) Color(0xFFA0A0AC) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }
        }

        confirmDeleteChapter?.let { target ->
            NovelAITheme(darkTheme = isEffectiveDark) {
                AlertDialog(
                    onDismissRequest = { confirmDeleteChapter = null },
                    containerColor = if (isEffectiveDark) Color(0xFF20202A) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = if (isEffectiveDark) Color.White else MaterialTheme.colorScheme.onSurface,
                    textContentColor = if (isEffectiveDark) Color(0xFFD0D0D8) else MaterialTheme.colorScheme.onSurfaceVariant,
                    title = { Text("删除章节《${target.title}》？") },
                    text = { Text("该章节内容将被永久删除，此操作无法撤销。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteChapter(target.id)
                                confirmDeleteChapter = null
                            }
                        ) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDeleteChapter = null }) {
                            Text("取消", color = if (isEffectiveDark) Color(0xFFA0A0AC) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }
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
                        viewModel = viewModel,
                        brightness = brightness,
                        isDark = isEffectiveDark,
                        timerMode = ttsTimerMode,
                        timerRemainingSeconds = ttsTimerRemainingSeconds,
                        onOpenTimer = {
                            viewModel.closeSettings()
                            timerSheetOpen = true
                        },
                        onBrightnessChange = { brightness = it }
                    )
                }
            }
        }

        if (sfToolsOpen) {
            com.novelai.assistant.ui.discover.SfBookToolsDialog(bookId, chapterIndex.coerceAtLeast(0), { sfToolsOpen = false })
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

@Composable
private fun QuoteChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
        onClick = onClick
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ReaderBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** 翻页模式 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagedContent(
    chapterId: String,
    paragraphs: List<String>,
    settings: ReadingSettings,
    palette: com.novelai.assistant.ui.theme.ReaderPalette,
    statusBarPad: androidx.compose.ui.unit.Dp,
    navBarPad: androidx.compose.ui.unit.Dp,
    chromeVisible: Boolean,
    ttsSpeaking: Boolean,
    ttsPaused: Boolean,
    ttsPosition: Int,
    isDarkReader: Boolean,
    onToggleChrome: () -> Unit,
    onQuote: (String, Int) -> Unit,
    onPageTurn: () -> Unit,
    onVisibleParaChange: (Int) -> Unit,
    onNextChapter: () -> Unit,
    onPrevChapter: () -> Unit,
    hasNextChapter: Boolean,
    hasPrevChapter: Boolean,
    selectionMode: Boolean,
    targetPageProvider: () -> Int?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val readerStyle = TextStyle(
        fontSize = settings.fontSizeSp.sp,
        lineHeight = (settings.fontSizeSp * settings.lineSpacingMultiplier).sp,
        fontFamily = readerFontFamily(settings.serifFont),
        color = Color(palette.text)
    )
    val hPadPx = with(density) { settings.horizontalPaddingDp.dp.roundToPx() }
    val paraSpacingPx = with(density) { settings.paragraphSpacingDp.dp.roundToPx() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth - hPadPx * 2
        val chromeTopPx = with(density) { (statusBarPad + 72.dp).roundToPx() }
        val chromeBottomPx = with(density) { (navBarPad + 64.dp).roundToPx() }
        val heightPx = constraints.maxHeight - chromeTopPx - chromeBottomPx

        val pages = remember(
            chapterId, paragraphs, settings.fontSizeSp, settings.lineSpacingMultiplier,
            settings.serifFont, widthPx, heightPx, settings.paragraphSpacingDp
        ) {
            Paginator.paginate(paragraphs, textMeasurer, readerStyle, widthPx, heightPx, paraSpacingPx)
        }

        val pagerState = rememberPagerState(pageCount = { pages.size })

        LaunchedEffect(chapterId, pages) {
            val target = targetPageProvider()
            if (target == -1) {
                pagerState.scrollToPage(pages.lastIndex.coerceAtLeast(0))
            } else if (target != null && target >= 0) {
                pagerState.scrollToPage(target.coerceIn(0, pages.lastIndex.coerceAtLeast(0)))
            } else {
                pagerState.scrollToPage(0)
            }
        }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { onPageTurn() }
        }

        // 通知当前页起始段落（供听书从当前页起播）
        LaunchedEffect(pagerState.currentPage, pages) {
            val firstPara = pages.getOrNull(pagerState.currentPage)?.fragments?.firstOrNull()?.paraIndex ?: 0
            onVisibleParaChange(firstPara)
        }

        // 听书进度驱动翻页
        LaunchedEffect(ttsPosition, ttsSpeaking, ttsPaused) {
            if ((ttsSpeaking || ttsPaused) && ttsPosition >= 0) {
                val targetPage = pages.indexOfFirst { page ->
                    page.fragments.any { it.paraIndex == ttsPosition }
                }
                if (targetPage >= 0 && targetPage != pagerState.currentPage) {
                    pagerState.animateScrollToPage(targetPage)
                }
            }
        }

        fun handleTap(screenX: Float, screenWidth: Float) {
            if (chromeVisible) {
                onToggleChrome()
            } else {
                when {
                    screenX < screenWidth * 0.33f -> {
                        // 左侧点击 -> 上一页 / 上一章
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        } else if (hasPrevChapter) {
                            onPrevChapter()
                        } else {
                            Toast.makeText(context, "已经是第一章了", Toast.LENGTH_SHORT).show()
                        }
                    }
                    screenX > screenWidth * 0.67f -> {
                        // 右侧点击 -> 下一页 / 下一章
                        if (pagerState.currentPage < pages.lastIndex) {
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
            chapterId, pages.size, hasNextChapter, hasPrevChapter, pagerState.currentPage
        ) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    var totalDx = 0f
                    var totalDy = 0f
                    val pointerId = down.id
                    val isAtEnd = pagerState.currentPage >= pages.lastIndex
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
            ) { pageIndex ->
                val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(selectionMode, chromeVisible, pagerState.currentPage, pages.size, hasNextChapter, hasPrevChapter) {
                            if (!selectionMode) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        handleTap(offset.x, size.width.toFloat())
                                    }
                                )
                            }
                        }
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = settings.horizontalPaddingDp.dp,
                                vertical = 0.dp
                            )
                            .padding(top = statusBarPad + 72.dp, bottom = navBarPad + 64.dp)
                    ) {
                        var lastParaIndex = -1
                        page.fragments.forEach { frag ->
                            if (lastParaIndex != -1 && frag.paraIndex != lastParaIndex) {
                                Spacer(Modifier.height(settings.paragraphSpacingDp.dp))
                            }
                            lastParaIndex = frag.paraIndex
                            val isTtsSpeakingThis = (ttsSpeaking || ttsPaused) && frag.paraIndex == ttsPosition
                            val highlightBg = if (isTtsSpeakingThis) {
                                if (isDarkReader) Color(0xFF9A97FF).copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else Color.Transparent

                            if (selectionMode) {
                                Text(
                                    text = frag.text,
                                    style = readerStyle,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(highlightBg, MaterialTheme.shapes.small)
                                )
                            } else {
                                Text(
                                    text = frag.text,
                                    style = readerStyle,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(highlightBg, MaterialTheme.shapes.small)
                                        .pointerInput(selectionMode, chromeVisible, pagerState.currentPage, pages.size, hasNextChapter, hasPrevChapter) {
                                            detectTapGestures(
                                                onLongPress = { onQuote(paragraphs[frag.paraIndex], frag.paraIndex) },
                                                onTap = { offset ->
                                                    val screenX = offset.x + with(density) { settings.horizontalPaddingDp.dp.toPx() }
                                                    val screenWidth = size.width + with(density) { (settings.horizontalPaddingDp * 2).dp.toPx() }
                                                    handleTap(screenX, screenWidth)
                                                }
                                            )
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // 页码
            Text(
                "${pagerState.currentPage + 1} / ${pages.size}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(palette.secondaryText),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navBarPad + 40.dp)
            )
        }
    }
}

/** 滚动模式 */
@Composable
private fun ScrollContent(
    chapterId: String,
    paragraphs: List<String>,
    settings: ReadingSettings,
    palette: com.novelai.assistant.ui.theme.ReaderPalette,
    statusBarPad: androidx.compose.ui.unit.Dp,
    navBarPad: androidx.compose.ui.unit.Dp,
    ttsSpeaking: Boolean,
    ttsPaused: Boolean,
    ttsPosition: Int,
    isDarkReader: Boolean,
    onToggleChrome: () -> Unit,
    onQuote: (String, Int) -> Unit,
    onProgress: () -> Unit,
    onVisibleParaChange: (Int) -> Unit,
    selectionMode: Boolean
) {
    val listState = rememberLazyListState()

    LaunchedEffect(chapterId) { listState.scrollToItem(0) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect {
                onProgress()
                onVisibleParaChange(it)
            }
    }

    LaunchedEffect(ttsPosition, ttsSpeaking, ttsPaused) {
        if ((ttsSpeaking || ttsPaused) && ttsPosition in paragraphs.indices) {
            listState.animateScrollToItem(ttsPosition)
        }
    }

    LazyColumn(
        state = listState,
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
            val isTtsSpeakingThis = (ttsSpeaking || ttsPaused) && index == ttsPosition
            val highlightBg = if (isTtsSpeakingThis) {
                if (isDarkReader) Color(0xFF9A97FF).copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else Color.Transparent

            ParagraphText(
                text = para,
                style = TextStyle(
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = (settings.fontSizeSp * settings.lineSpacingMultiplier).sp,
                    fontFamily = readerFontFamily(settings.serifFont),
                    color = Color(palette.text)
                ),
                highlightBg = highlightBg,
                selectionMode = selectionMode,
                onTap = onToggleChrome,
                onLongPress = { onQuote(para, index) }
            )
            Spacer(Modifier.height(settings.paragraphSpacingDp.dp))
        }
    }
}

/**
 * 单个段落：默认模式 tap=呼出菜单、长按=划线即问；
 * 选择模式下交给 SelectionContainer 处理自由选择与复制。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParagraphText(
    text: String,
    style: TextStyle,
    highlightBg: Color = Color.Transparent,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    if (selectionMode) {
        Text(
            text = text,
            style = style,
            modifier = Modifier
                .fillMaxWidth()
                .background(highlightBg, MaterialTheme.shapes.small)
        )
    } else {
        Text(
            text = text,
            style = style,
            modifier = Modifier
                .fillMaxWidth()
                .background(highlightBg, MaterialTheme.shapes.small)
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress
                )
        )
    }
}

/** 阅读设置面板内容 */
@Composable
private fun ReaderSettingsSheet(
    settings: ReadingSettings,
    viewModel: ReaderViewModel,
    brightness: Float,
    isDark: Boolean,
    timerMode: TtsTimerMode,
    timerRemainingSeconds: Int?,
    onOpenTimer: () -> Unit,
    onBrightnessChange: (Float) -> Unit
) {
    val sliderColors = SliderDefaults.colors(
        thumbColor = if (isDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary,
        activeTrackColor = if (isDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary,
        inactiveTrackColor = if (isDark) Color(0xFF2C2C3A) else MaterialTheme.colorScheme.surfaceVariant
    )
    val labelColor = if (isDark) Color(0xFFE8E8EE) else MaterialTheme.colorScheme.onSurface

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("阅读设置", style = MaterialTheme.typography.titleLarge, color = labelColor)

        // 亮度
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("亮度", style = MaterialTheme.typography.bodyLarge, color = labelColor, modifier = Modifier.width(80.dp))
            Slider(
                value = brightness,
                onValueChange = { onBrightnessChange((it * 100).toInt() / 100f) },
                valueRange = 0.05f..1f,
                colors = sliderColors,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (brightness >= 0.99f) "系统" else "${(brightness * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) Color(0xFFA0A0AC) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(44.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }

        // 字号
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FormatSize, contentDescription = null, tint = labelColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("字号", style = MaterialTheme.typography.bodyLarge, color = labelColor, modifier = Modifier.width(52.dp))
            IconButton(onClick = { viewModel.setFontSize(settings.fontSizeSp - 1) }) {
                Text("A-", style = MaterialTheme.typography.titleMedium, color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "${settings.fontSizeSp}",
                style = MaterialTheme.typography.titleMedium,
                color = if (isDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(40.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = { viewModel.setFontSize(settings.fontSizeSp + 1) }) {
                Text("A+", style = MaterialTheme.typography.titleMedium, color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface)
            }
        }

        // 行距
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("行距", style = MaterialTheme.typography.bodyLarge, color = labelColor, modifier = Modifier.width(80.dp))
            Slider(
                value = settings.lineSpacingMultiplier,
                onValueChange = { viewModel.setLineSpacing((it * 10).toInt() / 10f) },
                valueRange = 1.2f..2.4f,
                colors = sliderColors,
                modifier = Modifier.weight(1f)
            )
        }

        // 边距
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("边距", style = MaterialTheme.typography.bodyLarge, color = labelColor, modifier = Modifier.width(80.dp))
            Slider(
                value = settings.horizontalPaddingDp.toFloat(),
                onValueChange = { viewModel.setHorizontalPadding(it.toInt()) },
                valueRange = 8f..48f,
                colors = sliderColors,
                modifier = Modifier.weight(1f)
            )
        }

        // 段距
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("段距", style = MaterialTheme.typography.bodyLarge, color = labelColor, modifier = Modifier.width(80.dp))
            Slider(
                value = settings.paragraphSpacingDp.toFloat(),
                onValueChange = { viewModel.setParagraphSpacing(it.toInt()) },
                valueRange = 2f..28f,
                colors = sliderColors,
                modifier = Modifier.weight(1f)
            )
        }

        // 背景主题
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("背景", style = MaterialTheme.typography.bodyLarge, color = labelColor, modifier = Modifier.width(80.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReaderBgTheme.entries.forEach { theme ->
                    val p = ReaderPalettes.of(theme.name)
                    val selected = settings.bgTheme == theme
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(Color(p.background), CircleShape)
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) (if (isDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary)
                                else (if (isDark) Color(0xFF4A4A58) else MaterialTheme.colorScheme.outline),
                                shape = CircleShape
                            )
                            .clickable { viewModel.setBgTheme(theme) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("文", color = Color(p.text), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // 衬线
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("衬线字体", style = MaterialTheme.typography.bodyLarge, color = labelColor, modifier = Modifier.weight(1f))
            Switch(
                checked = settings.serifFont,
                onCheckedChange = { viewModel.setSerif(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = if (isDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = if (isDark) Color(0xFFA0A0AC) else Color.White,
                    uncheckedTrackColor = if (isDark) Color(0xFF2C2C3A) else MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        // 翻页模式
        Text("翻页模式", style = MaterialTheme.typography.labelMedium, color = if (isDark) Color(0xFFA0A0AC) else MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = settings.pageMode == PageMode.SCROLL,
                onClick = { viewModel.setPageMode(PageMode.SCROLL) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = if (isDark) NovelColors.Indigo.copy(alpha = 0.35f) else MaterialTheme.colorScheme.secondaryContainer,
                    activeContentColor = if (isDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.onSecondaryContainer,
                    inactiveContainerColor = if (isDark) Color(0xFF252532) else Color.Transparent,
                    inactiveContentColor = if (isDark) Color(0xFFB0B0BC) else MaterialTheme.colorScheme.onSurfaceVariant,
                    activeBorderColor = if (isDark) NovelColors.IndigoLight.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline,
                    inactiveBorderColor = if (isDark) Color(0xFF383846) else MaterialTheme.colorScheme.outline
                )
            ) { Text("上下滚动") }
            SegmentedButton(
                selected = settings.pageMode == PageMode.PAGED,
                onClick = { viewModel.setPageMode(PageMode.PAGED) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = if (isDark) NovelColors.Indigo.copy(alpha = 0.35f) else MaterialTheme.colorScheme.secondaryContainer,
                    activeContentColor = if (isDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.onSecondaryContainer,
                    inactiveContainerColor = if (isDark) Color(0xFF252532) else Color.Transparent,
                    inactiveContentColor = if (isDark) Color(0xFFB0B0BC) else MaterialTheme.colorScheme.onSurfaceVariant,
                    activeBorderColor = if (isDark) NovelColors.IndigoLight.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline,
                    inactiveBorderColor = if (isDark) Color(0xFF383846) else MaterialTheme.colorScheme.outline
                )
            ) { Text("左右翻页") }
        }

        // 听书定时
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable { onOpenTimer() }
                .padding(vertical = 4.dp)
        ) {
            Icon(Icons.Rounded.Timer, contentDescription = null, tint = labelColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("听书定时", style = MaterialTheme.typography.bodyLarge, color = labelColor, modifier = Modifier.weight(1f))
            Text(
                when {
                    timerRemainingSeconds != null -> "倒计时 ${formatRemaining(timerRemainingSeconds)}"
                    timerMode == TtsTimerMode.END_OF_CHAPTER -> "听完本章停止"
                    else -> timerMode.label
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (timerMode != TtsTimerMode.OFF) (if (isDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary)
                else (if (isDark) Color(0xFFA0A0AC) else MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = if (isDark) Color(0xFFA0A0AC) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


private fun formatRemaining(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return ""
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsTimerSheet(
    currentMode: TtsTimerMode,
    remainingSeconds: Int?,
    isDark: Boolean,
    onSelectMode: (TtsTimerMode) -> Unit,
    onDismiss: () -> Unit
) {
    val labelColor = if (isDark) Color(0xFFE8E8EE) else MaterialTheme.colorScheme.onSurface
    val accent = if (isDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF181820) else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = labelColor,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = if (isDark) Color(0xFF5A5A66) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "听书定时停止",
                    style = MaterialTheme.typography.titleLarge,
                    color = labelColor
                )
            }

            if (remainingSeconds != null && remainingSeconds > 0) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = accent.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.HourglassBottom,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "倒计时中：将在 ${formatRemaining(remainingSeconds)} 后停止朗读",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accent
                        )
                    }
                }
            } else if (currentMode == TtsTimerMode.END_OF_CHAPTER) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = accent.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.HourglassBottom,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "本章读完后将自动停止朗读，不再连播下一章",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accent
                        )
                    }
                }
            }

            Text(
                "定时选项",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) Color(0xFFA0A0AC) else MaterialTheme.colorScheme.onSurfaceVariant
            )

            TtsTimerMode.entries.forEach { mode ->
                val isSelected = mode == currentMode
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) {
                        if (isDark) Color(0xFF2B2B38) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    } else {
                        if (isDark) Color(0xFF22222C) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    border = if (isSelected) {
                        androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.8f))
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectMode(mode)
                            onDismiss()
                        }
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            mode.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) accent else labelColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "已选",
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 针对息屏自动停止的系统电池优化检测与一键引导
            val sheetContext = androidx.compose.ui.platform.LocalContext.current
            val isIgnoringBattery = remember { com.novelai.assistant.data.tts.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(sheetContext) }
            if (!isIgnoringBattery) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    onClick = { com.novelai.assistant.data.tts.BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(sheetContext) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "防息屏中断：设置后台无限制",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "手机熄屏易被系统省电杀除，点此开启无限制运行",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        Icon(
                            Icons.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
