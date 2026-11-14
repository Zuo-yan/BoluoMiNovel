package com.novelai.assistant.ui.listening

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.novelai.assistant.ui.components.AdaptiveNavigationType
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
 * 听书 App 内贴边吸附悬浮胶囊：
 * 1. 在阅读页外有听书进行时显示；
 * 2. 支持手指任意拖动，松手自动物理回弹吸附至可用区域最近侧边缘（自动避让平板左侧 Navigation Rail 与手机底栏）；
 * 3. 位置跨启动与屏幕缩放比例持久化（由调用方通过 onSavePosition 保存）；
 * 4. 主体点击进入专门听书模块，右侧内嵌播放/暂停快捷开关。
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
    hazeState: HazeState? = LocalGlassHazeState.current,
    railVisible: Boolean = LocalAdaptiveMetrics.current.navigationType == AdaptiveNavigationType.NAVIGATION_RAIL,
    bottomDockVisible: Boolean = LocalAdaptiveMetrics.current.navigationType == AdaptiveNavigationType.BOTTOM_BAR
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

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
        visible = isVisible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut() + scaleOut()
    ) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }

            val capsuleWidthDp = 192.dp
            val capsuleHeightDp = 52.dp
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
            val capsuleShape = RoundedCornerShape(26.dp)

            LiquidGlassBox(
                modifier = Modifier
                    .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
                    .width(capsuleWidthDp)
                    .height(capsuleHeightDp)
                    .graphicsLayer {
                        scaleX = dragScale
                        scaleY = dragScale
                    }
                    .border(
                        width = if (isDragging) 1.1.dp else 0.8.dp,
                        brush = rimBrush,
                        shape = capsuleShape
                    )
                    .pointerInput(minXPx, maxXPx, minYPx, maxYPx) {
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
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenListening()
                    },
                state = hazeState,
                cornerRadius = 26.dp,
                shape = capsuleShape,
                widgetSize = GlassWidgetSize.SMALL,
                sceneType = GlassSceneType.IMMERSIVE,
                interactive = true,
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧图标徽章
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accentInk.copy(alpha = if (isDark) 0.22f else 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = accentInk,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // 书名与章节名信息
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
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

                    // 播放 / 暂停 小按钮
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTogglePlayPause()
                        },
                        modifier = Modifier.size(36.dp)
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
