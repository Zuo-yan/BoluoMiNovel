package com.novelai.assistant.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novelai.assistant.data.db.BookSourceType
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.LocalInsideGlassOverlay
import com.novelai.assistant.ui.components.NovelMotion
import com.novelai.assistant.ui.theme.NovelColors
import com.novelai.assistant.ui.theme.ReaderPalette
import dev.chrisbanes.haze.HazeState
import java.util.Locale

private val READER_CAPSULE_MAX_WIDTH = 680.dp

/**
 * 统一的阅读器原生液态玻璃容器（保留荣耀 MagicOS HnVisualEffectLayout 光学折射与触控光晕）：
 * 通过 equalizeBackdrop + 柔和内层匀光膜消除纯白纸张上“外圈深灰阴影 + 中间白块”的断层；
 * 非荣耀设备自动回退至 Compose Haze 光学玻璃。
 */
@Composable
private fun ReaderGlassContainer(
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    isDark: Boolean,
    cornerRadius: Dp,
    shadowElevation: Dp,
    blurRadius: Dp = 56.dp,
    scrimAlpha: Float = 0.88f,
    nativeVeilAlpha: Float? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isHighContrastMenu = scrimAlpha >= 0.90f
    val resolvedNativeAlpha = nativeVeilAlpha ?: if (isHighContrastMenu) {
        if (isDark) 0.84f else 0.80f
    } else {
        if (isDark) 0.44f else 0.40f
    }
    val nativeVeilTint = if (isDark) {
        Color(0xFF161722).copy(alpha = resolvedNativeAlpha)
    } else {
        Color(0xFFF6F7FB).copy(alpha = resolvedNativeAlpha)
    }
    val composeFallbackTint = if (isDark) {
        Color(0xFF161722).copy(alpha = if (isHighContrastMenu) scrimAlpha else 0.72f)
    } else {
        Color(0xFFF3F4F8).copy(alpha = if (isHighContrastMenu) scrimAlpha else 0.68f)
    }

    CompositionLocalProvider(LocalInsideGlassOverlay provides true) {
        LiquidGlassBox(
            modifier = modifier,
            state = hazeState,
            cornerRadius = cornerRadius,
            widgetSize = GlassWidgetSize.SMALL,
            sceneType = GlassSceneType.IMMERSIVE,
            interactive = true,
            tint = composeFallbackTint,
            shadowElevation = shadowElevation,
            borderWidth = 0.75.dp,
            blurRadius = blurRadius,
            equalizeBackdrop = true,
            nativeOverlayTint = nativeVeilTint,
            isDarkBackdrop = isDark,
            content = content
        )
    }
}

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
    chapterIndex: Int,
    totalChapters: Int,
    overflowMenuOpen: Boolean,
    onBack: () -> Unit,
    onOpenDetailOrToc: () -> Unit,
    onToggleNightMode: () -> Unit,
    onToggleOverflowMenu: () -> Unit,
    onTopBarBottomChanged: (Dp) -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    val density = LocalDensity.current

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(NovelMotion.expressiveSpring()) { -it } + NovelMotion.fadeEnter(180),
        exit = slideOutVertically(NovelMotion.spatialSpring()) { -it } + NovelMotion.fadeExit(140),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .onGloballyPositioned { coords ->
                val bottomDp = with(density) { (coords.positionInParent().y + coords.size.height).toDp() }
                onTopBarBottomChanged(bottomDp)
            }
            .padding(top = statusBarPad + 8.dp)
            .padding(horizontal = 14.dp)
            .then(
                if (adaptive.isTablet) Modifier.widthIn(max = READER_CAPSULE_MAX_WIDTH) else Modifier
            )
            .fillMaxWidth()
    ) {
        ReaderGlassContainer(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState,
            isDark = isDarkReader,
            cornerRadius = 26.dp,
            shadowElevation = 12.dp
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
                    IconButton(onClick = onToggleOverflowMenu) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "更多",
                            tint = if (overflowMenuOpen) accent else onChromeColor
                        )
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

/**
 * 顶栏右上角「更多」液态玻璃弹出浮层：
 * 直接复用 ReaderScreen 的实时 hazeState，呈现与顶栏一致的大圆角、毛玻璃高光与分组分割线，
 * 避免系统默认 DropdownMenu 的直角白框遮挡顶栏。
 */
@Composable
internal fun BoxScope.ReaderOverflowMenu(
    visible: Boolean,
    topOffset: Dp,
    hazeState: HazeState,
    isDarkReader: Boolean,
    palette: ReaderPalette,
    onChromeColor: Color,
    secondaryColor: Color,
    accent: Color,
    sourceType: BookSourceType?,
    hasChapterContent: Boolean,
    selectionMode: Boolean,
    isCurrentBookmarked: Boolean,
    bookmarkCount: Int,
    onDismiss: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSfTools: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onExportMarkdown: () -> Unit,
    onExportTxt: () -> Unit
) {
    val dividerColor = onChromeColor.copy(alpha = if (isDarkReader) 0.12f else 0.09f)

    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            animationSpec = NovelMotion.expressiveSpring(),
            initialScale = 0.90f,
            transformOrigin = TransformOrigin(1f, 0f)
        ) + fadeIn(animationSpec = NovelMotion.expressiveSpring()),
        exit = scaleOut(
            animationSpec = NovelMotion.spatialSpring(),
            targetScale = 0.92f,
            transformOrigin = TransformOrigin(1f, 0f)
        ) + NovelMotion.fadeExit(120),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = topOffset + 8.dp, end = 14.dp)
            .widthIn(min = 224.dp, max = 264.dp)
    ) {
        ReaderGlassContainer(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState,
            isDark = isDarkReader,
            cornerRadius = 20.dp,
            shadowElevation = 16.dp,
            blurRadius = 72.dp,
            scrimAlpha = if (isDarkReader) 0.92f else 0.94f,
            nativeVeilAlpha = if (isDarkReader) 0.84f else 0.80f
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                ReaderOverflowMenuRow(
                    icon = if (isCurrentBookmarked) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                    label = if (isCurrentBookmarked) "移除当前书签" else "添加当前页书签",
                    iconTint = if (isCurrentBookmarked) accent else onChromeColor,
                    textColor = if (isCurrentBookmarked) accent else onChromeColor,
                    onClick = {
                        onDismiss()
                        onToggleBookmark()
                    }
                )
                ReaderOverflowMenuRow(
                    icon = Icons.Rounded.Bookmarks,
                    label = "书签列表",
                    iconTint = onChromeColor,
                    textColor = onChromeColor,
                    trailingText = if (bookmarkCount > 0) bookmarkCount.toString() else null,
                    secondaryColor = secondaryColor,
                    onClick = {
                        onDismiss()
                        onOpenBookmarks()
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    thickness = 0.6.dp,
                    color = dividerColor
                )

                if (sourceType == BookSourceType.BOLUOBAO) {
                    ReaderOverflowMenuRow(
                        icon = Icons.Rounded.Download,
                        label = "下载与导出",
                        trailingText = "TXT/MD/EPUB",
                        iconTint = onChromeColor,
                        textColor = onChromeColor,
                        secondaryColor = secondaryColor,
                        onClick = {
                            onDismiss()
                            onOpenSfTools()
                        }
                    )
                }
                ReaderOverflowMenuRow(
                    icon = if (selectionMode) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    label = if (selectionMode) "关闭文字选择模式" else "文字选择模式（可复制）",
                    iconTint = if (selectionMode) accent else onChromeColor,
                    textColor = if (selectionMode) accent else onChromeColor,
                    onClick = {
                        onDismiss()
                        onToggleSelectionMode()
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    thickness = 0.6.dp,
                    color = dividerColor
                )

                ReaderOverflowMenuRow(
                    icon = Icons.Rounded.Description,
                    label = "导出本章为 Markdown",
                    iconTint = if (hasChapterContent) onChromeColor else onChromeColor.copy(alpha = 0.38f),
                    textColor = if (hasChapterContent) onChromeColor else onChromeColor.copy(alpha = 0.38f),
                    enabled = hasChapterContent,
                    onClick = {
                        onDismiss()
                        onExportMarkdown()
                    }
                )
                ReaderOverflowMenuRow(
                    icon = Icons.AutoMirrored.Rounded.Article,
                    label = "导出本章为 TXT",
                    iconTint = if (hasChapterContent) onChromeColor else onChromeColor.copy(alpha = 0.38f),
                    textColor = if (hasChapterContent) onChromeColor else onChromeColor.copy(alpha = 0.38f),
                    enabled = hasChapterContent,
                    onClick = {
                        onDismiss()
                        onExportTxt()
                    }
                )
            }
        }
    }
}

@Composable
private fun ReaderOverflowMenuRow(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    textColor: Color,
    trailingText: String? = null,
    secondaryColor: Color = textColor.copy(alpha = 0.6f),
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!trailingText.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor,
                maxLines = 1
            )
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
    val adaptive = LocalAdaptiveMetrics.current
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(NovelMotion.expressiveSpring()) { it } + NovelMotion.fadeEnter(180),
        exit = slideOutVertically(NovelMotion.spatialSpring()) { it } + NovelMotion.fadeExit(140),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = (navBarPad + 12.dp).coerceAtLeast(16.dp))
            .padding(horizontal = 14.dp)
            .then(
                if (adaptive.isTablet) Modifier.widthIn(max = READER_CAPSULE_MAX_WIDTH) else Modifier
            )
            .fillMaxWidth()
    ) {
        ReaderGlassContainer(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState,
            isDark = isDarkReader,
            cornerRadius = 26.dp,
            shadowElevation = 14.dp,
            blurRadius = 60.dp,
            nativeVeilAlpha = if (isDarkReader) 0.54f else 0.50f
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                // 听书控制胶囊（仅属于当前正在朗读的书籍）
                AnimatedVisibility(visible = activeSpeaking || activePaused) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .background(Color(palette.text).copy(alpha = 0.05f), RoundedCornerShape(18.dp))
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
                            .clip(RoundedCornerShape(14.dp))
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
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

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
    val adaptive = LocalAdaptiveMetrics.current
    val isDark = isDarkReader || isEffectiveDark
    val pinkAccent = if (isDark) Color(0xFFFF7A9E) else Color(0xFFD81B60)
    val purpleAccent = if (isDark) Color(0xFFB39DDB) else Color(0xFF6200EA)
    val primaryChipColor = if (isDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary
    val secondaryChipColor = if (isDark) Color(0xFF8AC5D4) else MaterialTheme.colorScheme.secondary

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
            .then(
                if (adaptive.isTablet) Modifier.widthIn(max = READER_CAPSULE_MAX_WIDTH) else Modifier
            )
    ) {
        ReaderGlassContainer(
            hazeState = hazeState,
            isDark = isDark,
            cornerRadius = 24.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 固定单排布局：主操作“从此处朗读”钉在左侧高亮，次要操作在中间受约束横向滚动，关闭按钮固定右侧。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuoteChip(
                    label = "从此处朗读",
                    icon = Icons.Rounded.PlayArrow,
                    color = accent,
                    isDark = isDark,
                    filled = true,
                    onClick = onPlayFromHere
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isSfacgBook) {
                        QuoteChip("本章间贴", Icons.Rounded.Forum, pinkAccent, isDark) {
                            onOpenComments(quotedText.orEmpty())
                        }
                    }
                    QuoteChip("解析深意", Icons.Rounded.AutoAwesome, primaryChipColor, isDark) {
                        onAskAction(quotedText ?: "", "解析深意")
                    }
                    QuoteChip("情绪总结", Icons.Rounded.Tune, secondaryChipColor, isDark) {
                        onAskAction(quotedText ?: "", "情绪总结")
                    }
                    QuoteChip("AI吐槽", Icons.Rounded.AutoAwesome, pinkAccent, isDark) {
                        onAskAction(quotedText ?: "", "AI吐槽")
                    }
                    QuoteChip("选段伴读", Icons.Rounded.AutoAwesome, purpleAccent, isDark) {
                        onAskAction(quotedText ?: "", "")
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "取消",
                        modifier = Modifier.size(16.dp),
                        tint = if (isDark) Color(0xFFB5B5C2) else MaterialTheme.colorScheme.onSurfaceVariant
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
    isDark: Boolean = false,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (filled) {
        if (color.luminance() < 0.52f) Color.White else Color(0xFF141418)
    } else {
        color
    }
    val containerColor = if (filled) {
        color
    } else {
        color.copy(alpha = if (isDark) 0.20f else 0.12f)
    }
    val border = if (filled) {
        null
    } else {
        BorderStroke(0.6.dp, color.copy(alpha = if (isDark) 0.35f else 0.22f))
    }
    Surface(
        shape = CircleShape,
        color = containerColor,
        border = border,
        onClick = onClick
    ) {
        Row(
            Modifier.padding(
                horizontal = if (filled) 10.dp else 8.dp,
                vertical = 5.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(15.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
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
    hazeState: HazeState,
    isDarkReader: Boolean,
    palette: ReaderPalette,
    accent: Color,
    onExitSelectionMode: () -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    val textColor = if (isDarkReader) NovelColors.GlassOnDarkText else Color(palette.onChrome)

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(stiffness = 380f)) { it } + fadeIn(),
        exit = slideOutVertically(spring(stiffness = 380f)) { it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = (navBarPad + 16.dp).coerceAtLeast(20.dp), start = 14.dp, end = 14.dp)
            .then(
                if (adaptive.isTablet) Modifier.widthIn(max = READER_CAPSULE_MAX_WIDTH) else Modifier
            )
            .fillMaxWidth()
    ) {
        ReaderGlassContainer(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState,
            isDark = isDarkReader,
            cornerRadius = 22.dp,
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "文字选择模式中（长按拖拽光标选词）",
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onExitSelectionMode,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text("退出选择", color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
