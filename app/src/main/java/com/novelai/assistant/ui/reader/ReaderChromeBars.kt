package com.novelai.assistant.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novelai.assistant.data.db.BookSourceType
import com.novelai.assistant.ui.components.liquidGlass
import com.novelai.assistant.ui.theme.ReaderPalette
import dev.chrisbanes.haze.HazeState
import java.util.Locale

internal fun formatRemaining(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return ""
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

@Composable
internal fun BoxScope.ReaderTopBar(
    visible: Boolean,
    statusBarPad: Dp,
    hazeState: HazeState,
    isDarkReader: Boolean,
    palette: ReaderPalette,
    onChromeColor: Color,
    secondaryColor: Color,
    accent: Color,
    bookTitle: String,
    chapterTitle: String,
    sourceType: BookSourceType?,
    hasChapterContent: Boolean,
    chapterIndex: Int,
    totalChapters: Int,
    selectionMode: Boolean,
    onBack: () -> Unit,
    onOpenDetailOrToc: () -> Unit,
    onToggleNightMode: () -> Unit,
    onOpenSfTools: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onExportMarkdown: () -> Unit,
    onExportTxt: () -> Unit
) {
    var showExportMenu by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(stiffness = 400f)) { -it } + fadeIn(),
        exit = slideOutVertically(spring(stiffness = 400f)) { -it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(top = statusBarPad + 8.dp)
            .padding(horizontal = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = Color.Black.copy(alpha = 0.16f),
                    spotColor = Color.Black.copy(alpha = 0.24f)
                )
                .liquidGlass(
                    state = hazeState,
                    tint = if (isDarkReader) Color(0xFF14141E).copy(alpha = 0.85f) else Color(palette.chrome).copy(alpha = 0.86f),
                    shape = RoundedCornerShape(26.dp),
                    blurRadius = 28.dp,
                    enableFlowSheen = false
                )
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = onChromeColor)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            bookTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            chapterTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onChromeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onOpenDetailOrToc) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = "书籍详情", tint = onChromeColor)
                    }
                    // 夜间模式快捷切换
                    IconButton(onClick = onToggleNightMode) {
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
                            if (sourceType == BookSourceType.BOLUOBAO) {
                                DropdownMenuItem(
                                    text = { Text("下载与导出（TXT / MD / EPUB）") },
                                    onClick = { showExportMenu = false; onOpenSfTools() }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (selectionMode) "关闭文字选择模式" else "文字选择模式（可复制）") },
                                onClick = {
                                    showExportMenu = false
                                    onToggleSelectionMode()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导出本章为 Markdown") },
                                enabled = hasChapterContent,
                                onClick = {
                                    showExportMenu = false
                                    onExportMarkdown()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导出本章为 TXT") },
                                enabled = hasChapterContent,
                                onClick = {
                                    showExportMenu = false
                                    onExportTxt()
                                }
                            )
                        }
                    }
                }
                // 嵌入式晶体微光进度条
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.25.dp))
                        .background(secondaryColor.copy(alpha = 0.20f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(totalChapters.coerceAtLeast(1).let { total ->
                                ((chapterIndex + 1).coerceIn(1, total)).toFloat() / total
                            })
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(accent.copy(alpha = 0.7f), accent)
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.ReaderBottomBar(
    visible: Boolean,
    navBarPad: Dp,
    hazeState: HazeState,
    isDarkReader: Boolean,
    palette: ReaderPalette,
    onChromeColor: Color,
    secondaryColor: Color,
    accent: Color,
    isCurrentBookSpeaking: Boolean,
    activeSpeaking: Boolean,
    activePaused: Boolean,
    activePosition: Int,
    ttsReady: Boolean,
    ttsSpeed: Float,
    ttsTimerMode: TtsTimerMode,
    ttsTimerRemainingSeconds: Int?,
    onOpenTimer: () -> Unit,
    onResumeTts: () -> Unit,
    onPauseTts: () -> Unit,
    onStopOrToggleTts: () -> Unit,
    onSetTtsSpeed: (Float) -> Unit,
    onOpenToc: () -> Unit,
    onPrevChapter: () -> Unit,
    onToggleTts: () -> Unit,
    onGenerate: () -> Unit,
    onNextChapter: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(stiffness = 400f)) { it } + fadeIn(),
        exit = slideOutVertically(spring(stiffness = 400f)) { it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(bottom = (navBarPad + 12.dp).coerceAtLeast(16.dp))
            .padding(horizontal = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = Color.Black.copy(alpha = 0.16f),
                    spotColor = Color.Black.copy(alpha = 0.25f)
                )
                .liquidGlass(
                    state = hazeState,
                    tint = if (isDarkReader) Color(0xFF14141E).copy(alpha = 0.85f) else Color(palette.chrome).copy(alpha = 0.88f),
                    shape = RoundedCornerShape(26.dp),
                    blurRadius = 28.dp,
                    enableFlowSheen = isCurrentBookSpeaking
                )
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                // 听书控制胶囊（仅属于当前正在朗读的书籍）
                AnimatedVisibility(visible = activeSpeaking || activePaused) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .background(Color(palette.text).copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (activePaused) "已暂停 · 第 ${activePosition + 1} 段"
                                else "朗读中 · 第 ${activePosition + 1} 段",
                                style = MaterialTheme.typography.labelMedium,
                                color = onChromeColor,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // 定时状态与设置按钮
                            val timerActive = ttsTimerMode != TtsTimerMode.OFF
                            Surface(
                                shape = CircleShape,
                                color = if (timerActive) accent.copy(alpha = 0.22f) else onChromeColor.copy(alpha = 0.08f),
                                border = if (timerActive) BorderStroke(1.dp, accent.copy(alpha = 0.6f)) else null,
                                modifier = Modifier.clickable(onClick = onOpenTimer)
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
                                        ttsTimerMode == TtsTimerMode.END_OF_CHAPTER -> "本章停"
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
                                if (activePaused) onResumeTts() else onPauseTts()
                            }) {
                                Icon(
                                    if (activePaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    contentDescription = if (activePaused) "继续" else "暂停",
                                    tint = onChromeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = onStopOrToggleTts) {
                                Icon(
                                    Icons.Rounded.Stop,
                                    contentDescription = "停止朗读",
                                    tint = onChromeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        // 语速调节
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
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selected) accent.copy(alpha = 0.18f) else Color.Transparent
                                        )
                                        .clickable { onSetTtsSpeed(r) }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // 核心按钮栏
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReaderBarAction(Icons.Rounded.Menu, "目录", onChromeColor, onOpenToc)
                    ReaderBarAction(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "上一章", onChromeColor, onPrevChapter)
                    ReaderBarAction(
                        if (isCurrentBookSpeaking) Icons.Rounded.Stop else Icons.Rounded.Headphones,
                        "听书",
                        if (isCurrentBookSpeaking) accent else onChromeColor
                    ) {
                        if (ttsReady) onToggleTts()
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onGenerate)
                            .padding(vertical = 4.dp)
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
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                    }
                    ReaderBarAction(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "下一章", onChromeColor, onNextChapter)
                    ReaderBarAction(Icons.Rounded.Tune, "设置", onChromeColor, onOpenSettings)
                }
            }
        }
    }
}

@Composable
private fun RowScope.ReaderBarAction(
    icon: ImageVector,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoxScope.ReaderQuoteBar(
    quotedText: String?,
    bottomPadding: Dp,
    hazeState: HazeState,
    isDarkReader: Boolean,
    isEffectiveDark: Boolean,
    palette: ReaderPalette,
    accent: Color,
    isSfacgBook: Boolean,
    onPlayFromHere: () -> Unit,
    onAskAction: (text: String, action: String) -> Unit,
    onOpenComments: (quote: String) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = quotedText != null,
        enter = slideInVertically(spring(stiffness = 380f)) { it } + fadeIn(),
        exit = slideOutVertically(spring(stiffness = 380f)) { it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                bottom = bottomPadding,
                start = 14.dp,
                end = 14.dp
            )
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.16f),
                    spotColor = Color.Black.copy(alpha = 0.25f)
                )
                .liquidGlass(
                    state = hazeState,
                    tint = if (isDarkReader) Color(0xFF161622).copy(alpha = 0.85f) else Color(palette.chrome).copy(alpha = 0.88f),
                    shape = RoundedCornerShape(24.dp),
                    blurRadius = 24.dp,
                    enableFlowSheen = false
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // 用 FlowRow + 定宽上限：横向滚动会让 Row 按不限宽测量，
            // 外层 Surface 随之撑出屏幕，末尾的“本章间贴”被推到不可点区域。
            FlowRow(
                Modifier.fillMaxWidth(),
                maxItemsInEachRow = 3,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                QuoteChip("从此处朗读", Icons.Rounded.PlayArrow, accent, onPlayFromHere)
                QuoteChip("解析深意", Icons.Rounded.AutoAwesome, MaterialTheme.colorScheme.primary) {
                    onAskAction(quotedText ?: "", "解析深意")
                }
                QuoteChip("情绪总结", Icons.Rounded.Tune, MaterialTheme.colorScheme.secondary) {
                    onAskAction(quotedText ?: "", "情绪总结")
                }
                QuoteChip("AI吐槽", Icons.Rounded.AutoAwesome, Color(0xFFE91E63)) {
                    onAskAction(quotedText ?: "", "AI吐槽")
                }
                if (isSfacgBook) {
                    QuoteChip("本章间贴", Icons.Rounded.Forum, Color(0xFFE91E63)) {
                        onOpenComments(quotedText.orEmpty())
                    }
                }
                QuoteChip("选段伴读", Icons.Rounded.AutoAwesome, Color(0xFF7C4DFF)) {
                    onAskAction(quotedText ?: "", "")
                }
                IconButton(
                    onClick = onDismiss,
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
}

@Composable
private fun QuoteChip(
    label: String,
    icon: ImageVector? = null,
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
internal fun BoxScope.ReaderSelectionHintBar(
    visible: Boolean,
    navBarPad: Dp,
    isDarkReader: Boolean,
    onExitSelectionMode: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(stiffness = 380f)) { it } + fadeIn(),
        exit = slideOutVertically(spring(stiffness = 380f)) { it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = (navBarPad + 16.dp).coerceAtLeast(20.dp), start = 14.dp, end = 14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = if (isDarkReader) Color(0xFF1E1E2A) else MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            border = BorderStroke(
                1.dp,
                if (isDarkReader) Color(0xFF383846) else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "文字选择模式中（长按拖拽光标选词）",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDarkReader) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onExitSelectionMode,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("退出选择", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
