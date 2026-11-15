package com.novelai.assistant.ui.listening

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelai.assistant.data.generation.BackgroundGenerationState
import com.novelai.assistant.data.generation.GenerationTaskStatus
import com.novelai.assistant.ui.components.AdaptiveGlassDialog
import com.novelai.assistant.ui.components.AdaptiveNavigationType
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.LocalGlassHazeState
import com.novelai.assistant.ui.components.bottomBarContentPadding
import com.novelai.assistant.ui.components.navigationRailContentStartPadding
import com.novelai.assistant.ui.theme.NovelColors
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

/**
 * App 内贴边吸附统一「灵动单胶囊」：
 * 1. 同屏永远只存在 1 个胶囊：自动承载 听书 / 后台 AI 续写生成 / 两者并行双态；
 * 2. 支持手指任意拖动，松手自动物理回弹吸附至可用区域最近侧边缘（避让平板 Navigation Rail 与手机底栏）；
 * 3. 位置跨启动持久化；双态并行时点击胶囊弹出轻量卡片，一键进入听书或返回草稿。
 */
@Composable
fun ListeningFloatingCapsule(
    isVisible: Boolean,
    paused: Boolean,
    bookTitle: String,
    chapterTitle: String,
    positionFrac: Pair<Float, Float>,
    onTogglePlayPause: () -> Unit,
    onSavePosition: (xFrac: Float, yFrac: Float) -> Unit,
    onOpenListening: () -> Unit,
    modifier: Modifier = Modifier,
    showTts: Boolean = isVisible,
    generationState: BackgroundGenerationState? = null,
    onOpenGeneration: (BackgroundGenerationState) -> Unit = {},
    onStopGeneration: () -> Unit = {},
    onDismissGeneration: () -> Unit = {},
    hazeState: HazeState? = LocalGlassHazeState.current,
    railVisible: Boolean = LocalAdaptiveMetrics.current.navigationType == AdaptiveNavigationType.NAVIGATION_RAIL,
    bottomDockVisible: Boolean = LocalAdaptiveMetrics.current.navigationType == AdaptiveNavigationType.BOTTOM_BAR
) {
    val adaptive = LocalAdaptiveMetrics.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val activeGen = generationState?.takeIf { it.hasDisplayableState }
    val showGen = activeGen != null
    val isDualMode = showTts && showGen

    var showDualSheet by remember { mutableStateOf(false) }
    LaunchedEffect(isDualMode) {
        if (!isDualMode) showDualSheet = false
    }

    val pulseAlpha by rememberInfiniteTransition(label = "genPulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "genPulseAlpha"
    )

    val primaryInk = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryInk = if (isDark) Color(0xFFBEBECA) else Color(0xFF5B5B66)
    val accentInk = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary

    val railStartClearanceDp = if (railVisible) navigationRailContentStartPadding() else 0.dp
    val statusTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomClearanceDp = if (bottomDockVisible) {
        bottomBarContentPadding(extra = 12.dp)
    } else {
        navBottomDp + 16.dp
    }

    AnimatedVisibility(
        visible = isVisible && (showTts || showGen),
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut() + scaleOut()
    ) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }

            val capsuleWidthDp = when {
                isDualMode -> 228.dp
                showGen -> 208.dp
                else -> 192.dp
            }
            val capsuleHeightDp = if (isDualMode) 56.dp else 52.dp
            val capsuleWidthPx = with(density) { capsuleWidthDp.toPx() }
            val capsuleHeightPx = with(density) { capsuleHeightDp.toPx() }

            val marginPx = with(density) { 12.dp.toPx() }
            val railClearancePx = with(density) { railStartClearanceDp.toPx() }
            val minXPx = (railClearancePx + marginPx).coerceAtMost((screenWidthPx - capsuleWidthPx - marginPx).coerceAtLeast(marginPx))
            val maxXPx = (screenWidthPx - capsuleWidthPx - marginPx).coerceAtLeast(minXPx)

            val minYPx = with(density) { maxOf(statusTopDp + 12.dp, 52.dp).toPx() }
            val maxYPx = (screenHeightPx - capsuleHeightPx - with(density) { bottomClearanceDp.toPx() })
                .coerceAtLeast(minYPx)

            var isDragging by remember { mutableStateOf(false) }
            var offsetX by remember { mutableFloatStateOf(minXPx + (maxXPx - minXPx) * positionFrac.first) }
            var offsetY by remember { mutableFloatStateOf(minYPx + (maxYPx - minYPx) * positionFrac.second) }

            // 监听持久化坐标与屏幕/导航轨避让边界变化
            LaunchedEffect(positionFrac, minXPx, maxXPx, minYPx, maxYPx) {
                if (!isDragging) {
                    offsetX = (minXPx + (maxXPx - minXPx) * positionFrac.first).coerceIn(minXPx, maxXPx)
                    offsetY = (minYPx + (maxYPx - minYPx) * positionFrac.second).coerceIn(minYPx, maxYPx)
                }
            }

            val animatedX by animateFloatAsState(
                targetValue = offsetX,
                animationSpec = if (isDragging) {
                    spring(dampingRatio = 0.92f, stiffness = 1400f)
                } else {
                    spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow)
                },
                label = "capsuleX"
            )
            val animatedY by animateFloatAsState(
                targetValue = offsetY,
                animationSpec = if (isDragging) {
                    spring(dampingRatio = 0.92f, stiffness = 1400f)
                } else {
                    spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
                },
                label = "capsuleY"
            )
            val dragScale by animateFloatAsState(
                targetValue = if (isDragging) 1.05f else 1f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
                label = "capsuleDragScale"
            )

            val rimBrush = if (isDark) {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDragging) 0.42f else 0.24f),
                        Color(0xFF9AC8FF).copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.14f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color(0xFFAAD2FF).copy(alpha = if (isDragging) 0.48f else 0.24f),
                        Color.White.copy(alpha = 0.60f)
                    )
                )
            }
            val capsuleShape = RoundedCornerShape(if (isDualMode) 28.dp else 26.dp)
            val capsuleInteractionSource = remember { MutableInteractionSource() }

            LiquidGlassBox(
                modifier = Modifier
                    .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
                    .width(capsuleWidthDp)
                    .height(capsuleHeightDp)
                    .graphicsLayer {
                        scaleX = dragScale
                        scaleY = dragScale
                        shape = capsuleShape
                        clip = true
                    }
                    .clip(capsuleShape)
                    .border(
                        width = if (isDragging) 1.1.dp else 0.8.dp,
                        brush = rimBrush,
                        shape = capsuleShape
                    )
                    .pointerInput(minXPx, maxXPx, minYPx, maxYPx, capsuleWidthPx) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount.x).coerceIn(minXPx, maxXPx)
                                offsetY = (offsetY + dragAmount.y).coerceIn(minYPx, maxYPx)
                            },
                            onDragEnd = {
                                isDragging = false
                                val availableCenterX = (minXPx + maxXPx + capsuleWidthPx) / 2f
                                val capsuleCenterX = offsetX + capsuleWidthPx / 2f
                                val snapToLeft = capsuleCenterX < availableCenterX
                                val targetX = if (snapToLeft) minXPx else maxXPx
                                offsetX = targetX
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                val xFrac = if (snapToLeft) 0f else 1f
                                val yRange = (maxYPx - minYPx).coerceAtLeast(1f)
                                val yFrac = ((offsetY - minYPx) / yRange).coerceIn(0f, 1f)
                                onSavePosition(xFrac, yFrac)
                            },
                            onDragCancel = {
                                isDragging = false
                                val availableCenterX = (minXPx + maxXPx + capsuleWidthPx) / 2f
                                val snapToLeft = (offsetX + capsuleWidthPx / 2f) < availableCenterX
                                offsetX = if (snapToLeft) minXPx else maxXPx
                            }
                        )
                    }
                    .clickable(
                        interactionSource = capsuleInteractionSource,
                        indication = null
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        when {
                            isDualMode -> showDualSheet = true
                            activeGen != null && !showTts -> onOpenGeneration(activeGen)
                            else -> onOpenListening()
                        }
                    },
                state = hazeState,
                cornerRadius = if (isDualMode) 28.dp else 26.dp,
                shape = capsuleShape,
                widgetSize = GlassWidgetSize.SMALL,
                sceneType = GlassSceneType.IMMERSIVE,
                interactive = true,
                shadowElevation = 12.dp,
                borderWidth = 0.dp,
                blurRadius = 48.dp,
                equalizeBackdrop = true,
                nativeOverlayTint = if (isDark) {
                    Color(0xFF181A24).copy(alpha = 0.34f)
                } else {
                    Color(0xFFF6F7FB).copy(alpha = 0.36f)
                },
                isDarkBackdrop = isDark
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val badgeIcon = when {
                        showTts -> Icons.Rounded.Headphones
                        activeGen?.status == GenerationTaskStatus.COMPLETED -> Icons.Rounded.CheckCircle
                        activeGen?.status == GenerationTaskStatus.FAILED -> Icons.Rounded.ErrorOutline
                        else -> Icons.Rounded.AutoAwesome
                    }
                    val badgeTint = when {
                        !showTts && activeGen?.status == GenerationTaskStatus.FAILED -> MaterialTheme.colorScheme.error
                        !showTts && activeGen?.isRunning == true -> accentInk.copy(alpha = pulseAlpha)
                        else -> accentInk
                    }

                    // 左侧图标徽章（双态运行时角落叠加脉冲指示点）
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accentInk.copy(alpha = if (isDark) 0.22f else 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            badgeIcon,
                            contentDescription = null,
                            tint = badgeTint,
                            modifier = Modifier.size(18.dp)
                        )
                        if (isDualMode && activeGen != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (activeGen.isRunning) accentInk.copy(alpha = pulseAlpha)
                                        else if (activeGen.status == GenerationTaskStatus.FAILED) MaterialTheme.colorScheme.error
                                        else Color(0xFF34C759)
                                    )
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    if (isDualMode && activeGen != null) {
                        // 双态融合展示：上方听书，下方 AI 后台生成实时字数
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = (if (paused) "已暂停 · " else "") + bookTitle.ifBlank { "正在听书" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatGenerationHeadline(activeGen),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (activeGen.status == GenerationTaskStatus.FAILED) MaterialTheme.colorScheme.error else accentInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 双态右侧保留最常用的音频播控按钮；完整切换通过点击胶囊呼出浮层
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTogglePlayPause()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = if (paused) "继续" else "暂停",
                                tint = accentInk,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (!showTts && activeGen != null) {
                        // 仅 AI 生成展示
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatGenerationHeadline(activeGen),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (activeGen.status == GenerationTaskStatus.FAILED && !activeGen.error.isNullOrBlank()) {
                                    activeGen.error
                                } else {
                                    "《${activeGen.bookTitle}》· 点此回看"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (activeGen.status == GenerationTaskStatus.FAILED) MaterialTheme.colorScheme.error else secondaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (activeGen.isRunning) onStopGeneration() else onDismissGeneration()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = if (activeGen.isRunning) Icons.Rounded.Stop else Icons.Rounded.Close,
                                contentDescription = if (activeGen.isRunning) "停止生成" else "关闭悬浮提示",
                                tint = if (activeGen.isRunning) accentInk else secondaryInk,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        // 仅听书展示（保持原有体验）
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bookTitle.ifBlank { "正在听书" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (paused) "已暂停" else chapterTitle.ifBlank { "正在朗读" },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (paused) secondaryInk else accentInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTogglePlayPause()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = if (paused) "继续" else "暂停",
                                tint = accentInk,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDualSheet && isDualMode && activeGen != null) {
        AdaptiveGlassDialog(
            onDismissRequest = { showDualSheet = false },
            maxWidth = adaptive.maxDialogSheetWidth
        ) {
            Text(
                "后台并行任务",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 听书控制行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.Headphones, contentDescription = null, tint = accentInk)
                    Column(Modifier.weight(1f)) {
                        Text(
                            bookTitle.ifBlank { "正在听书" },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (paused) "已暂停 · ${chapterTitle.ifBlank { "听书" }}" else chapterTitle.ifBlank { "正在朗读" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onTogglePlayPause) {
                        Icon(
                            if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (paused) "继续" else "暂停",
                            tint = accentInk
                        )
                    }
                    GlassCapsuleButton(
                        text = "听书",
                        onClick = {
                            showDualSheet = false
                            onOpenListening()
                        },
                        filled = false
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 2. AI 后台生成行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = accentInk)
                    Column(Modifier.weight(1f)) {
                        Text(
                            formatGenerationHeadline(activeGen),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "《${activeGen.bookTitle}》 · ${activeGen.modelLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            if (activeGen.isRunning) {
                                onStopGeneration()
                            } else {
                                onDismissGeneration()
                                showDualSheet = false
                            }
                        }
                    ) {
                        Icon(
                            if (activeGen.isRunning) Icons.Rounded.Stop else Icons.Rounded.Close,
                            contentDescription = if (activeGen.isRunning) "停止生成" else "移除提示",
                            tint = secondaryInk
                        )
                    }
                    GlassCapsuleButton(
                        text = "查看",
                        onClick = {
                            showDualSheet = false
                            onOpenGeneration(activeGen)
                        },
                        filled = true
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showDualSheet = false }) { Text("收起") }
            }
        }
    }
}

private fun formatGenerationHeadline(gen: BackgroundGenerationState): String {
    return when (gen.status) {
        GenerationTaskStatus.RUNNING ->
            if (gen.text.isEmpty()) "AI 续写召回中…" else "AI 续写中 · ${gen.text.length}字"
        GenerationTaskStatus.COMPLETED -> "续写完成 · ${gen.text.length}字"
        GenerationTaskStatus.FAILED -> "续写中断 · ${gen.text.length}字"
        GenerationTaskStatus.CANCELLED -> "续写草稿 · ${gen.text.length}字"
    }
}
