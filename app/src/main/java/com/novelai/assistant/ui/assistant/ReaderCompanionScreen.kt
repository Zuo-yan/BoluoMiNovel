package com.novelai.assistant.ui.assistant

import android.widget.Toast
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.GlassCircleButton
import com.novelai.assistant.ui.components.GlassFilterPill
import com.novelai.assistant.ui.components.GlassGradientBar
import com.novelai.assistant.ui.components.GlassGradientDirection
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.LocalGlassHazeState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderCompanionScreen(
    bookId: String,
    quote: String,
    action: String = "",
    onBack: () -> Unit,
    viewModel: AssistantViewModel = hiltViewModel()
) {
    val adaptive = LocalAdaptiveMetrics.current
    val hazeState = LocalGlassHazeState.current

    val currentBook by viewModel.currentBook.collectAsStateWithLifecycle()
    val currentChapter by viewModel.currentChapter.collectAsStateWithLifecycle()
    val chatRecords by viewModel.chatRecords.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val spoilerFree by viewModel.spoilerFree.collectAsStateWithLifecycle()
    val retrievedHint by viewModel.retrievedHint.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var isQuoteExpanded by remember { mutableStateOf(false) }
    var confirmClearChat by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(bookId, quote, action) {
        viewModel.initializeForQuote(bookId, quote, action)
    }

    LaunchedEffect(chatRecords.size, streamingText, generating) {
        val count = chatRecords.size + (if (generating || streamingText.isNotBlank()) 1 else 0)
        if (count > 0) {
            listState.animateScrollToItem((count - 1).coerceAtLeast(0))
        }
    }

    val hasQuoteInChat = remember(chatRecords, quote) {
        quote.isNotBlank() && chatRecords.any { it.role == "user" && it.content.contains(quote) }
    }
    val quickActions = remember {
        listOf(
            "AI吐槽" to Icons.Rounded.AutoAwesome,
            "神评回复" to Icons.Rounded.AutoAwesome,
            "解析深意" to Icons.Rounded.AutoAwesome,
            "情绪总结" to Icons.Rounded.Tune,
            "剧情伏笔" to Icons.Rounded.AutoAwesome,
            "人物心理" to Icons.Rounded.Tune
        )
    }

    val triggerQuickAction: (String) -> Unit = { label ->
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        viewModel.sendAction(
            action = label,
            quoteText = quote.takeIf { it.isNotBlank() && !hasQuoteInChat }
        )
    }

    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomClearance = if (imeBottom > 0.dp) 8.dp else navBottom + 12.dp

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GlassGradientBar(
                direction = GlassGradientDirection.TOP_TO_BOTTOM,
                state = hazeState,
                modifier = Modifier.fillMaxWidth()
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
                        contentDescription = "返回阅读",
                        onClick = onBack,
                        size = 40.dp,
                        state = hazeState
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentBook?.title?.takeIf { it.isNotBlank() } ?: "选段伴读",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentChapter?.title?.let { "章节：$it" } ?: "深度伴读与解析",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    SpoilerFreeChip(
                        spoilerFree = spoilerFree,
                        onToggle = { viewModel.toggleSpoilerFree() },
                        compact = true
                    )

                    GlassCircleButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "清空对话记录",
                        onClick = { confirmClearChat = true },
                        size = 40.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        state = hazeState
                    )
                }
            }
        }
    ) { padding ->
        if (adaptive.twoPaneEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .padding(horizontal = adaptive.horizontalPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(adaptive.paneSpacing)
            ) {
                // 左侧玻璃侧栏（308.dp）：选段展示 + 快捷分析胶囊
                Column(
                    modifier = Modifier
                        .width(308.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = bottomClearance),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (quote.isNotBlank()) {
                        QuoteGlassCard(
                            quote = quote,
                            expanded = true,
                            allowToggle = false,
                            onToggleExpand = {},
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(quote))
                                Toast.makeText(context, "选段已复制", Toast.LENGTH_SHORT).show()
                            },
                            hazeState = hazeState
                        )
                    }

                    GlassSectionCard(
                        title = "快捷解析指令",
                        state = hazeState,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickActions.forEach { (label, icon) ->
                                GlassFilterPill(
                                    label = label,
                                    selected = false,
                                    onClick = { triggerQuickAction(label) },
                                    leadingIcon = icon
                                )
                            }
                        }
                    }
                }

                // 右侧主对话区（居中限宽 780.dp）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    AdaptiveCenteredContainer(
                        maxWidth = 780.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            CompanionMessageList(
                                bookId = bookId,
                                chatRecords = chatRecords,
                                streamingText = streamingText,
                                generating = generating,
                                retrievedHint = retrievedHint,
                                listState = listState,
                                horizontalPadding = 8.dp,
                                retrievedHintPrefix = "已参考相关章节：",
                                maxBubbleWidth = 520.dp,
                                modifier = Modifier.weight(1f)
                            ) {
                                CompanionEmptyState()
                            }

                            error?.let { err ->
                                Text(
                                    text = err,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }

                            GlassChatInputCapsule(
                                value = input,
                                onValueChange = { input = it },
                                placeholder = if (generating) "AI 正在思考与回答中…" else "追问这段文字或这本书…",
                                generating = generating,
                                canSend = input.isNotBlank(),
                                onSend = {
                                    viewModel.send(input)
                                    input = ""
                                },
                                onStop = { viewModel.stopGenerating() },
                                hazeState = hazeState,
                                modifier = Modifier.padding(bottom = bottomClearance)
                            )
                        }
                    }
                }
            }
        } else {
            // 手机紧凑单栏布局
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
            ) {
                if (quote.isNotBlank()) {
                    QuoteGlassCard(
                        quote = quote,
                        expanded = isQuoteExpanded,
                        allowToggle = quote.length > 70,
                        onToggleExpand = { isQuoteExpanded = !isQuoteExpanded },
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(quote))
                            Toast.makeText(context, "选段已复制", Toast.LENGTH_SHORT).show()
                        },
                        hazeState = hazeState,
                        modifier = Modifier.padding(horizontal = adaptive.horizontalPadding, vertical = 6.dp)
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = adaptive.horizontalPadding, vertical = 4.dp)
                ) {
                    items(quickActions) { (label, icon) ->
                        GlassFilterPill(
                            label = label,
                            selected = false,
                            onClick = { triggerQuickAction(label) },
                            leadingIcon = icon
                        )
                    }
                }

                CompanionMessageList(
                    bookId = bookId,
                    chatRecords = chatRecords,
                    streamingText = streamingText,
                    generating = generating,
                    retrievedHint = retrievedHint,
                    listState = listState,
                    horizontalPadding = adaptive.horizontalPadding,
                    retrievedHintPrefix = "已参考相关章节：",
                    maxBubbleWidth = 330.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    CompanionEmptyState()
                }

                error?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = adaptive.horizontalPadding, vertical = 4.dp)
                    )
                }

                GlassChatInputCapsule(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = if (generating) "AI 正在思考与回答中…" else "追问这段文字或这本书…",
                    generating = generating,
                    canSend = input.isNotBlank(),
                    onSend = {
                        viewModel.send(input)
                        input = ""
                    },
                    onStop = { viewModel.stopGenerating() },
                    hazeState = hazeState,
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .padding(bottom = bottomClearance)
                )
            }
        }
    }

    if (confirmClearChat) {
        ClearChatDialog(
            onDismiss = { confirmClearChat = false },
            onConfirm = {
                confirmClearChat = false
                viewModel.clearChat()
            }
        )
    }
}

@Composable
private fun QuoteGlassCard(
    quote: String,
    expanded: Boolean,
    allowToggle: Boolean,
    onToggleExpand: () -> Unit,
    onCopy: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState?,
    modifier: Modifier = Modifier
) {
    LiquidGlassBox(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        state = hazeState,
        cornerRadius = 20.dp,
        widgetSize = GlassWidgetSize.BIG,
        sceneType = GlassSceneType.GENERAL,
        interactive = false,
        skipRenderLayer = true,
        shadowElevation = 6.dp
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.FormatQuote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "当前选段",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("复制", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = quote,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (allowToggle) {
                Text(
                    text = if (expanded) "收起" else "展开全文",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onToggleExpand)
                        .padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun CompanionEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "选择快捷指令或直接追问",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
