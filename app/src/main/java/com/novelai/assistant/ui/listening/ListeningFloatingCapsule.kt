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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.prefs.ReadingPreferencesRepository
import com.novelai.assistant.data.tts.TtsPlayer
import androidx.compose.ui.graphics.Color
import com.novelai.assistant.ui.components.liquidGlass
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 听书 App 内贴边吸附悬浮胶囊：
 * 1. 在阅读页外有听书进行时显示；
 * 2. 支持手指任意拖动，松手自动物理回弹吸附至屏幕最近侧边缘；
 * 3. 位置跨启动与屏幕缩放比例持久化（DataStore）；
 * 4. 主体点击进入专门听书模块，右侧内嵌播放/暂停快捷开关。
 */
@Composable
fun ListeningFloatingCapsule(
    ttsPlayer: TtsPlayer,
    prefs: ReadingPreferencesRepository,
    onOpenListening: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    val speaking by ttsPlayer.speaking.collectAsStateWithLifecycle()
    val paused by ttsPlayer.paused.collectAsStateWithLifecycle()
    val bookTitle by ttsPlayer.currentBookTitle.collectAsStateWithLifecycle()
    val chapterTitle by ttsPlayer.currentChapterTitle.collectAsStateWithLifecycle()
    val positionFrac by prefs.listenPillPosition.collectAsStateWithLifecycle(initialValue = 0.90f to 0.65f)

    val isVisible = speaking || paused
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut() + scaleOut()
    ) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }

            val capsuleWidthDp = 186.dp
            val capsuleHeightDp = 50.dp
            val capsuleWidthPx = with(density) { capsuleWidthDp.toPx() }
            val capsuleHeightPx = with(density) { capsuleHeightDp.toPx() }

            val marginPx = with(density) { 12.dp.toPx() }
            val minXPx = marginPx
            val maxXPx = (screenWidthPx - capsuleWidthPx - marginPx).coerceAtLeast(minXPx)
            val minYPx = with(density) { 64.dp.toPx() }
            val maxYPx = (screenHeightPx - capsuleHeightPx - with(density) { 90.dp.toPx() }).coerceAtLeast(minYPx)

            var offsetX by remember { mutableFloatStateOf(minXPx + (maxXPx - minXPx) * positionFrac.first) }
            var offsetY by remember { mutableFloatStateOf(minYPx + (maxYPx - minYPx) * positionFrac.second) }

            // 监听持久化坐标初始化
            LaunchedEffect(positionFrac, screenWidthPx, screenHeightPx) {
                offsetX = (minXPx + (maxXPx - minXPx) * positionFrac.first).coerceIn(minXPx, maxXPx)
                offsetY = (minYPx + (maxYPx - minYPx) * positionFrac.second).coerceIn(minYPx, maxYPx)
            }

            val animatedX by animateFloatAsState(
                targetValue = offsetX,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "capsuleX"
            )
            val animatedY by animateFloatAsState(
                targetValue = offsetY,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "capsuleY"
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
                    .width(capsuleWidthDp)
                    .height(capsuleHeightDp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(25.dp),
                        ambientColor = Color.Black.copy(alpha = 0.18f),
                        spotColor = Color.Black.copy(alpha = 0.28f)
                    )
                    .liquidGlass(
                        state = hazeState,
                        shape = RoundedCornerShape(25.dp),
                        blurRadius = 28.dp,
                        
                    )
                    .pointerInput(screenWidthPx, screenHeightPx) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount.x).coerceIn(minXPx, maxXPx)
                                offsetY = (offsetY + dragAmount.y).coerceIn(minYPx, maxYPx)
                            },
                            onDragEnd = {
                                // 松手时自动向最近侧屏幕吸附
                                val centerX = offsetX + capsuleWidthPx / 2f
                                val snapToLeft = centerX < screenWidthPx / 2f
                                val targetX = if (snapToLeft) minXPx else maxXPx
                                offsetX = targetX

                                val xFrac = if (snapToLeft) 0.02f else 0.98f
                                val yFrac = ((offsetY - minYPx) / (maxYPx - minYPx)).coerceIn(0f, 1f)
                                scope.launch {
                                    prefs.setListenPillPosition(xFrac, yFrac)
                                }
                            }
                        )
                    }
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenListening()
                    }
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
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
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
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (paused) "已暂停" else chapterTitle.ifBlank { "正在朗读" },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (paused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 播放 / 暂停 小按钮
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (paused) {
                                ttsPlayer.resume()
                            } else {
                                ttsPlayer.pause()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (paused) "继续" else "暂停",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
