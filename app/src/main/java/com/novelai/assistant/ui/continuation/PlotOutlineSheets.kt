package com.novelai.assistant.ui.continuation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novelai.assistant.data.db.BookOutlineEntity
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.OutlineDiscussionEntity
import com.novelai.assistant.domain.OutlineBeat
import com.novelai.assistant.ui.components.AdaptiveGlassDialog
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassCircleButton
import com.novelai.assistant.ui.components.GlassFilterPill
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.LocalInsideGlassOverlay
import com.novelai.assistant.ui.components.MarkdownText
import com.novelai.assistant.ui.components.OriginBadge
import com.novelai.assistant.ui.components.buildMarkdownInlineAnnotatedString
import com.novelai.assistant.ui.components.stripMarkdownPlainPreview
import com.novelai.assistant.ui.theme.NovelColors
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OutlineAndMemoryCard(
    outline: BookOutlineEntity?,
    outlineBeats: List<OutlineBeat>,
    selectedBeatIndex: Int?,
    activeOutlineBeat: OutlineBeat?,
    remainingUnwrittenBeatsCount: Int,
    discussionCount: Int,
    validSummaryCount: Int,
    totalChapterCount: Int,
    persistedVectorCount: Int,
    hazeState: HazeState?,
    onToggleUseOutline: () -> Unit,
    onSelectBeat: (OutlineBeat) -> Unit,
    onClearSelectedBeat: () -> Unit,
    onToggleBeatCompleted: (OutlineBeat) -> Unit,
    onOpenDiscussion: () -> Unit,
    onExtendOutlinePlan: () -> Unit,
    onOpenOutlineEditor: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = if (isDark) Color(0xFFB5B5C2) else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary

    val useOutline = outline?.useInContinuation ?: true
    val hasMaster = !outline?.masterOutline.isNullOrBlank()
    val hasPlan = !outline?.upcomingPlan.isNullOrBlank()
    val hasOutline = hasMaster || hasPlan || outlineBeats.isNotEmpty()

    GlassSectionCard(
        title = "大纲 × 续写双轮联动控制台",
        actionLabel = "编辑 / 缓存",
        onActionClick = onOpenOutlineEditor,
        state = hazeState,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 顶部状态与总开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (hasOutline) "续写自动遵循长期大纲" else "尚未制定长期大纲",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryText
                        )
                        if (outlineBeats.isNotEmpty()) {
                            val lowRemaining = remainingUnwrittenBeatsCount <= 2
                            val badgeBg = when {
                                remainingUnwrittenBeatsCount == 0 -> NovelColors.Amber.copy(alpha = if (isDark) 0.24f else 0.16f)
                                lowRemaining -> NovelColors.Amber.copy(alpha = if (isDark) 0.20f else 0.14f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.24f else 0.13f)
                            }
                            val badgeFg = when {
                                remainingUnwrittenBeatsCount == 0 || lowRemaining -> if (isDark) NovelColors.Amber else Color(0xFF9C5B00)
                                else -> accentColor
                            }
                            Surface(
                                shape = CircleShape,
                                color = badgeBg
                            ) {
                                Text(
                                    text = "细纲余 $remainingUnwrittenBeatsCount/${outlineBeats.size} 章",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = badgeFg,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (hasOutline) {
                            buildString {
                                if (hasMaster) append("总纲：${stripMarkdownPlainPreview(outline?.masterOutline.orEmpty()).take(40)}")
                                if (!hasMaster && hasPlan) append("细纲：${stripMarkdownPlainPreview(outline?.upcomingPlan.orEmpty()).take(40)}")
                                if (hasMaster && outlineBeats.isEmpty() && hasPlan) {
                                    append(" · 近期：${stripMarkdownPlainPreview(outline?.upcomingPlan.orEmpty()).take(26)}")
                                }
                            }.ifBlank { "已加载长期大纲与分章细纲" }
                        } else {
                            "先用 AI 梳理 6~8 章细纲并直接带入续写，点选即可同步章名与剧情要点"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = useOutline,
                    onCheckedChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleUseOutline()
                    }
                )
            }

            // 记忆缓存 & 快捷动作胶囊行
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.50f else 0.65f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Cached,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                        val vectorPart = if (persistedVectorCount > 0) " · 向量 $persistedVectorCount 条" else ""
                        Text(
                            text = "记忆缓存 $validSummaryCount/${totalChapterCount.coerceAtLeast(1)} 章$vectorPart",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (discussionCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.72f else 0.85f)
                    ) {
                        Text(
                            text = "已讨论 $discussionCount 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.11f),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenDiscussion()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Forum,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "剧情讨论",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = accentColor
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                    border = BorderStroke(
                        0.6.dp,
                        if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.09f)
                    ),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenOutlineEditor()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.EditNote,
                            contentDescription = null,
                            tint = secondaryText,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "编辑",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = primaryText
                        )
                    }
                }
            }

            if (hasOutline) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "分章细纲推进队列",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText,
                        modifier = Modifier.weight(1f)
                    )
                    if (activeOutlineBeat != null) {
                        val isManuallyPinned = selectedBeatIndex != null && selectedBeatIndex == activeOutlineBeat.index
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.11f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isManuallyPinned) "已选第${activeOutlineBeat.displayNumber}步" else "下一目标：第${activeOutlineBeat.displayNumber}步",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor
                                )
                                if (isManuallyPinned) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "取消锁定细纲",
                                        tint = accentColor,
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onClearSelectedBeat()
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                if (outlineBeats.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(
                            items = outlineBeats,
                            key = { "${it.index}_${it.chapterTitle}" }
                        ) { beat ->
                            val isCurrentTarget = activeOutlineBeat?.index == beat.index && !beat.isCompleted
                            OutlineBeatQueueCard(
                                beat = beat,
                                isCurrentTarget = isCurrentTarget,
                                isExplicitlySelected = selectedBeatIndex == beat.index,
                                isDark = isDark,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                accentColor = accentColor,
                                onSelect = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectBeat(beat)
                                },
                                onToggleCompleted = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleBeatCompleted(beat)
                                }
                            )
                        }
                        item(key = "extend_outline_tail") {
                            ExtendOutlineQueueCard(
                                remainingUnwrittenBeatsCount = remainingUnwrittenBeatsCount,
                                isDark = isDark,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                accentColor = accentColor,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onExtendOutlinePlan()
                                }
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.14f else 0.07f),
                        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onExtendOutlinePlan()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "尚未拆分为分章细纲节拍",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = primaryText
                                )
                                Text(
                                    text = "点击一键让 AI 按当前总纲生成 6 章可执行细纲，直接点选填入续写",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryText
                                )
                            }
                        }
                    }
                }
            } else {
                // 未建立大纲时，提供醒目引导 CTA 卡片
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.09f),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenDiscussion()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.30f else 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "先用 AI 梳理 6~8 章细纲并直接带入续写",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText
                            )
                            Text(
                                text = "聊两句确认主线走向，自动生成分章标题与剧情节点，点选即开写",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryText
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassCapsuleButton(
                    text = "AI 讨论剧情",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenDiscussion()
                    },
                    filled = true,
                    icon = Icons.Rounded.Forum,
                    state = hazeState,
                    modifier = Modifier.weight(1.15f)
                )
                GlassCapsuleButton(
                    text = if (hasOutline) "续编下6章" else "手写大纲",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (hasOutline) onExtendOutlinePlan() else onOpenOutlineEditor()
                    },
                    filled = false,
                    icon = if (hasOutline) Icons.Rounded.AutoAwesome else Icons.Rounded.EditNote,
                    state = hazeState,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OutlineBeatQueueCard(
    beat: OutlineBeat,
    isCurrentTarget: Boolean,
    isExplicitlySelected: Boolean,
    isDark: Boolean,
    primaryText: Color,
    secondaryText: Color,
    accentColor: Color,
    onSelect: () -> Unit,
    onToggleCompleted: () -> Unit
) {
    val activeHighlight = isCurrentTarget || isExplicitlySelected
    val shape = RoundedCornerShape(18.dp)
    val cardBg = when {
        activeHighlight && isDark -> Color(0xFF232544).copy(alpha = 0.88f)
        activeHighlight -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        beat.isCompleted && isDark -> Color(0xFF171923).copy(alpha = 0.64f)
        beat.isCompleted -> Color.White.copy(alpha = 0.50f)
        isDark -> Color(0xFF1C1E2C).copy(alpha = 0.82f)
        else -> Color.White.copy(alpha = 0.72f)
    }
    val cardBorder = when {
        activeHighlight -> BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.78f else 0.55f))
        beat.isCompleted -> BorderStroke(0.6.dp, if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f))
        else -> BorderStroke(0.7.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.09f))
    }

    val statusBg = when {
        beat.isCompleted -> NovelColors.Mint.copy(alpha = if (isDark) 0.22f else 0.15f)
        activeHighlight -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.30f else 0.16f)
        isDark -> Color.White.copy(alpha = 0.08f)
        else -> Color.Black.copy(alpha = 0.06f)
    }
    val statusFg = when {
        beat.isCompleted -> if (isDark) NovelColors.Mint else Color(0xFF007E77)
        activeHighlight -> accentColor
        else -> secondaryText
    }
    val statusText = when {
        beat.isCompleted -> "已写 ✓"
        activeHighlight -> "当前目标"
        else -> "待写"
    }

    Surface(
        shape = shape,
        color = cardBg,
        border = cardBorder,
        onClick = onSelect,
        modifier = Modifier
            .width(210.dp)
            .height(136.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
                ) {
                    Text(
                        text = "第${beat.displayNumber}步",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = primaryText,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = statusBg,
                    onClick = onToggleCompleted
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (beat.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = "切换细纲完成状态",
                            tint = statusFg,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusFg,
                            maxLines = 1
                        )
                    }
                }
            }

            Text(
                text = "《${beat.chapterTitle.ifBlank { "第${beat.displayNumber}章" }}》",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (activeHighlight) accentColor else primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = beat.summary.ifBlank { "点击代入该节拍剧情到续写工作台" },
                style = MaterialTheme.typography.bodySmall,
                color = if (beat.isCompleted) secondaryText.copy(alpha = 0.85f) else secondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (beat.hook.isNotBlank()) {
                Text(
                    text = "钩子：${beat.hook}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = 0.90f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ExtendOutlineQueueCard(
    remainingUnwrittenBeatsCount: Int,
    isDark: Boolean,
    primaryText: Color,
    secondaryText: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    val lowRemaining = remainingUnwrittenBeatsCount <= 2
    val dashColor = if (lowRemaining) {
        if (isDark) NovelColors.Amber.copy(alpha = 0.72f) else Color(0xFFB56E00).copy(alpha = 0.60f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.65f else 0.48f)
    }
    val cardBg = if (lowRemaining) {
        NovelColors.Amber.copy(alpha = if (isDark) 0.14f else 0.10f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.14f else 0.08f)
    }
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .width(196.dp)
            .height(136.dp)
            .clip(shape)
            .background(cardBg, shape)
            .drawBehind {
                val strokeWidth = 1.2.dp.toPx()
                val radius = 18.dp.toPx()
                drawRoundRect(
                    color = dashColor,
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                    )
                )
            }
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (lowRemaining) {
                Surface(
                    shape = CircleShape,
                    color = NovelColors.Amber.copy(alpha = if (isDark) 0.25f else 0.18f)
                ) {
                    Text(
                        text = "细纲快写完了 · 一键续编后续 6 章",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) NovelColors.Amber else Color(0xFF8C5200),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.24f else 0.14f)
                ) {
                    Text(
                        text = "无限续编",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.28f else 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "续编后续细纲",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText,
                    maxLines = 1
                )
            }

            Text(
                text = "沿现有细纲末尾继续生成 6 章标题与钩子",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun SuggestTitleCapsuleButton(
    label: String,
    suggesting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accentColor = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.12f),
        border = BorderStroke(0.65.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
        enabled = !suggesting,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (suggesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.8.dp,
                    color = accentColor
                )
                Text(
                    text = "拟题中…",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = accentColor,
                    maxLines = 1,
                    softWrap = false
                )
            } else {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
internal fun RecommendedTitleChipsRow(
    recommendedTitles: List<String>,
    selectedTitle: String,
    activeOutlineBeatTitle: String?,
    onSelectTitle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (recommendedTitles.isEmpty()) return
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val accentColor = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(recommendedTitles, key = { it }) { title ->
            val isSelected = selectedTitle.trim().isNotBlank() && selectedTitle.trim() == title.trim()
            val isFromActiveBeat = !activeOutlineBeatTitle.isNullOrBlank() &&
                activeOutlineBeatTitle.trim() == title.trim()
            val chipBg = when {
                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.30f else 0.16f)
                isDark -> Color.White.copy(alpha = 0.08f)
                else -> Color.White.copy(alpha = 0.70f)
            }
            val chipBorder = when {
                isSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                isDark -> BorderStroke(0.65.dp, Color.White.copy(alpha = 0.14f))
                else -> BorderStroke(0.65.dp, Color.Black.copy(alpha = 0.09f))
            }

            Surface(
                shape = CircleShape,
                color = chipBg,
                border = chipBorder,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectTitle(title)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isFromActiveBeat) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.34f else 0.18f)
                        ) {
                            Text(
                                text = "大纲",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) accentColor else primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun PendingOutlineProposalCard(
    proposal: PendingOutlineContinuationProposal,
    onAcceptAndGenerate: () -> Unit,
    onApplyOnly: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    shadowElevation: Dp = 6.dp
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = if (isDark) Color(0xFFB5B5C2) else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) Color(0xFF202238).copy(alpha = 0.94f) else Color(0xFFF0F1FF).copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.52f else 0.36f)),
        shadowElevation = shadowElevation,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = if (proposal.fromDiscussion) "🎯 大纲已采纳 · 立即按细纲开写？" else "🎯 大纲已更新 · 立即按细纲开写？",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "关闭提案",
                        tint = secondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (proposal.suggestedTitle.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (proposal.nextBeat != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.26f else 0.14f)
                        ) {
                            Text(
                                text = "第${proposal.nextBeat.displayNumber}步",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "《${proposal.suggestedTitle}》",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (proposal.suggestedInstruction.isNotBlank()) {
                Text(
                    text = proposal.suggestedInstruction,
                    style = MaterialTheme.typography.bodySmall,
                    color = primaryText.copy(alpha = 0.90f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val metaLine = buildString {
                if (proposal.totalBeatCount > 0) {
                    append("待写细纲剩余 ${proposal.remainingBeatCount}/${proposal.totalBeatCount} 章")
                }
                if (proposal.masterSummaryPreview.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(proposal.masterSummaryPreview.take(40))
                }
            }
            if (metaLine.isNotBlank()) {
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassCapsuleButton(
                    text = "立即按大纲开写",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAcceptAndGenerate()
                    },
                    filled = true,
                    icon = Icons.Rounded.AutoAwesome,
                    state = null,
                    modifier = Modifier.weight(1.2f)
                )
                GlassCapsuleButton(
                    text = "填入工作台微调",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onApplyOnly()
                    },
                    filled = false,
                    state = null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun AnchorChapterSelectorBar(
    anchorMode: AnchorChapterMode,
    latestChapter: ChapterEntity?,
    currentReadingChapter: ChapterEntity?,
    activeChapter: ChapterEntity? = null,
    totalChapters: Int = 0,
    showActiveSummary: Boolean = true,
    onSelectLatest: () -> Unit,
    onSelectCurrent: () -> Unit,
    onSelectCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val haptics = LocalHapticFeedback.current
    val trackColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.045f)
    val trackBorder = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)

    val segments = buildList {
        add(Triple(AnchorChapterMode.LATEST, "⚡ 全书最新", onSelectLatest))
        if (currentReadingChapter != null) {
            add(Triple(AnchorChapterMode.CURRENT, "📖 当前章节", onSelectCurrent))
        }
        add(Triple(AnchorChapterMode.CUSTOM, "🎯 指定章节", onSelectCustom))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = trackColor,
            border = BorderStroke(0.6.dp, trackBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                segments.forEach { (mode, label, onClick) ->
                    val selected = anchorMode == mode
                    val segBg = when {
                        selected && isDark -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                        selected -> Color.White.copy(alpha = 0.94f)
                        else -> Color.Transparent
                    }
                    val segBorder = when {
                        selected && isDark -> BorderStroke(0.75.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        selected -> BorderStroke(0.75.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                        else -> null
                    }
                    val segTextColor = when {
                        selected && isDark -> NovelColors.GlassAccentBlueDark
                        selected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = segBg,
                        border = segBorder,
                        shadowElevation = if (selected && !isDark) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onClick()
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = segTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (showActiveSummary && activeChapter != null) {
            val idxTag = if (totalChapters > 0) " #${activeChapter.chapterIndex + 1}/$totalChapters" else ""
            val modePrefix = when (anchorMode) {
                AnchorChapterMode.LATEST -> "最新末章"
                AnchorChapterMode.CURRENT -> "当前阅读"
                AnchorChapterMode.CUSTOM -> "指定章节"
            }
            Text(
                text = "$modePrefix · ${activeChapter.title}$idxTag",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
internal fun ChapterAnchorPickerDialog(
    allChapters: List<ChapterEntity>,
    latestChapter: ChapterEntity?,
    currentReadingChapter: ChapterEntity?,
    onSelectLatest: () -> Unit,
    onSelectCurrent: () -> Unit,
    onSelectChapter: (ChapterEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    AdaptiveGlassDialog(
        onDismissRequest = onDismiss,
        maxWidth = adaptive.maxDialogSheetWidth
    ) {
        Text(
            "选择基准章节",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
        ) {
            item {
                TextButton(
                    onClick = {
                        onSelectLatest()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "⚡ 全书最新章（第${(latestChapter?.chapterIndex ?: 0) + 1}章 · 自动追踪）",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            if (currentReadingChapter != null) {
                item {
                    TextButton(
                        onClick = {
                            onSelectCurrent()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "📖 当前章节（第${currentReadingChapter.chapterIndex + 1}章 · ${currentReadingChapter.title}）",
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            items(allChapters, key = { it.id }) { ch ->
                TextButton(
                    onClick = {
                        onSelectChapter(ch)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OriginBadge(ch.originType.name)
                        Text(
                            "${ch.chapterIndex + 1}. ${ch.title}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    }
}

@Composable
internal fun PlotDiscussionDialog(
    bookTitle: String,
    anchorChapter: ChapterEntity?,
    anchorChapterTitle: String,
    totalChapters: Int,
    anchorMode: AnchorChapterMode,
    latestChapter: ChapterEntity?,
    currentReadingChapter: ChapterEntity?,
    allChapters: List<ChapterEntity>,
    activeModelLabel: String,
    hasExistingUpcomingPlan: Boolean,
    pendingOutlineProposal: PendingOutlineContinuationProposal?,
    messages: List<OutlineDiscussionEntity>,
    streamingText: String,
    generating: Boolean,
    error: String?,
    hazeState: HazeState?,
    decodeQuickOptions: (String) -> List<String>,
    onSelectLatestAnchor: () -> Unit,
    onSelectCurrentAnchor: () -> Unit,
    onSelectCustomAnchor: (ChapterEntity) -> Unit,
    onSendMessage: (String) -> Unit,
    onSynthesizeOutline: () -> Unit,
    onExtendOutlinePlan: () -> Unit,
    onAdoptOutline: (master: String?, upcoming: String?, appendBeats: Boolean) -> Unit,
    onAcceptProposalAndGenerate: () -> Unit,
    onApplyProposalOnly: () -> Unit,
    onDismissProposal: () -> Unit,
    onStopGenerating: () -> Unit,
    onClearDiscussion: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bodyTextColor = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryTextColor = if (isDark) Color(0xFFB5B5C2) else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    val dialogShape = RoundedCornerShape(28.dp)
    val dialogBg = if (isDark) Color(0xFF151622) else Color(0xFFF7F8FC)
    val dialogBorder = Brush.verticalGradient(
        if (isDark) {
            listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f))
        } else {
            listOf(Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.05f))
        }
    )

    var input by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var showAnchorPicker by remember { mutableStateOf(false) }
    var adoptedMessageIds by remember { mutableStateOf(setOf<String>()) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(messages.size, streamingText, generating) {
        val count = messages.size + (if (generating || streamingText.isNotBlank()) 1 else 0)
        if (count > 0) {
            listState.animateScrollToItem((count - 1).coerceAtLeast(0))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalInsideGlassOverlay provides true) {
            Surface(
                shape = dialogShape,
                color = dialogBg,
                tonalElevation = 0.dp,
                shadowElevation = 20.dp,
                border = BorderStroke(0.8.dp, dialogBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 18.dp)
                    .widthIn(max = 760.dp)
                    .fillMaxHeight(0.92f)
                    .imePadding()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 顶部标题与操作栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Forum,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "剧情走向讨论",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = bodyTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "《$bookTitle》· 锚点：$anchorChapterTitle · $activeModelLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (messages.isNotEmpty()) {
                            IconButton(
                                onClick = { confirmClear = true },
                                enabled = !generating,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "清空讨论记录",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        GlassCircleButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            onClick = onDismiss,
                            size = 34.dp,
                            state = null
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 讨论锚点章节快速切换栏（全书最新 / 当前章节 / 指定章节）
                    AnchorChapterSelectorBar(
                        anchorMode = anchorMode,
                        latestChapter = latestChapter,
                        currentReadingChapter = currentReadingChapter,
                        activeChapter = anchorChapter,
                        totalChapters = totalChapters,
                        showActiveSummary = false,
                        onSelectLatest = onSelectLatestAnchor,
                        onSelectCurrent = onSelectCurrentAnchor,
                        onSelectCustom = { showAnchorPicker = true },
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp)
                    )

                    // 细纲生成与续编快捷工具条
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val canSynthesize = !generating && messages.isNotEmpty()
                        val canExtend = !generating && (hasExistingUpcomingPlan || messages.isNotEmpty())
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(
                                alpha = if (canSynthesize) (if (isDark) 0.24f else 0.12f) else 0.06f
                            ),
                            border = BorderStroke(
                                0.65.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = if (canSynthesize) 0.34f else 0.12f)
                            ),
                            enabled = canSynthesize,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSynthesizeOutline()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (canSynthesize) accentColor else secondaryTextColor.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "整理6~8章细纲",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (canSynthesize) accentColor else secondaryTextColor.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (canExtend) {
                                if (isDark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.85f)
                            } else {
                                Color.Transparent
                            },
                            border = BorderStroke(
                                0.65.dp,
                                if (canExtend) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                }
                            ),
                            enabled = canExtend,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onExtendOutlinePlan()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = if (canExtend) bodyTextColor else secondaryTextColor.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "续编下6章",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (canExtend) bodyTextColor else secondaryTextColor.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))

                    // 对话消息区
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (messages.isEmpty() && !generating) {
                            item {
                                PlotDiscussionEmptyGuide(
                                    bodyTextColor = bodyTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    onStarterClick = { prompt ->
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSendMessage(prompt)
                                    },
                                    onCustomDirectionClick = {
                                        if (input.isBlank()) input = "我想自己定一个剧情方向："
                                        runCatching { focusRequester.requestFocus() }
                                    }
                                )
                            }
                        }

                        itemsIndexed(messages, key = { _, m -> m.id }) { idx, msg ->
                            val isLatestAssistant = msg.role == "assistant" &&
                                idx == messages.indexOfLast { it.role == "assistant" }
                            if (msg.role == "user") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                                        modifier = Modifier.widthIn(max = 520.dp)
                                    ) {
                                        Text(
                                            text = msg.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            } else {
                                val quickOptions = remember(msg.id, msg.quickOptionsJson) {
                                    decodeQuickOptions(msg.quickOptionsJson)
                                }
                                AssistantPlotMessageCard(
                                    message = msg,
                                    quickOptions = quickOptions,
                                    isLatestAssistant = isLatestAssistant,
                                    generating = generating,
                                    isAdopted = msg.id in adoptedMessageIds,
                                    hasExistingUpcomingPlan = hasExistingUpcomingPlan,
                                    isDark = isDark,
                                    bodyTextColor = bodyTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    onSelectDirection = { option ->
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSendMessage("我倾向这个方向：$option")
                                    },
                                    onCustomDirection = {
                                        if (input.isBlank()) input = "我对这几个选项都不太满意，我自己的方向是："
                                        runCatching { focusRequester.requestFocus() }
                                    },
                                    onAdoptOutline = { master, upcoming, appendBeats ->
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAdoptOutline(master, upcoming, appendBeats)
                                        adoptedMessageIds = adoptedMessageIds + msg.id
                                    }
                                )
                            }
                        }

                        if (generating || streamingText.isNotBlank()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (isDark) Color(0xFF1F2030).copy(alpha = 0.86f)
                                    else Color.White.copy(alpha = 0.78f),
                                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = "AI 正在构思走向与细纲…",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(
                                                onClick = onStopGenerating,
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(3.dp))
                                                Text("停止", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                        if (streamingText.isNotBlank()) {
                                            SelectionContainer {
                                                MarkdownText(
                                                    markdown = streamingText,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = bodyTextColor,
                                                    secondaryColor = secondaryTextColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (pendingOutlineProposal != null) {
                        PendingOutlineProposalCard(
                            proposal = pendingOutlineProposal,
                            onAcceptAndGenerate = {
                                onAcceptProposalAndGenerate()
                                onDismiss()
                            },
                            onApplyOnly = {
                                onApplyProposalOnly()
                                onDismiss()
                            },
                            onDismiss = onDismissProposal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 底部输入栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = {
                                Text("回答 AI 提问，或直接提出你想要的剧情方向…")
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = if (isDark) Color(0xFF1F2030) else Color.White.copy(alpha = 0.90f),
                                unfocusedContainerColor = if (isDark) Color(0xFF1C1D2A) else Color.White.copy(alpha = 0.78f)
                            ),
                            minLines = 1,
                            maxLines = 4,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                        )
                        GlassCircleButton(
                            icon = if (generating) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                            contentDescription = if (generating) "停止生成" else "发送",
                            onClick = {
                                if (generating) {
                                    onStopGenerating()
                                } else if (input.isNotBlank()) {
                                    val text = input
                                    input = ""
                                    onSendMessage(text)
                                }
                            },
                            size = 46.dp,
                            tint = if (generating || input.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                secondaryTextColor
                            },
                            state = null
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空剧情讨论记录？") },
            text = { Text("只会清空本页对话记录，已写入「长期大纲」的内容会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearDiscussion()
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }

    if (showAnchorPicker) {
        ChapterAnchorPickerDialog(
            allChapters = allChapters,
            latestChapter = latestChapter,
            currentReadingChapter = currentReadingChapter,
            onSelectLatest = onSelectLatestAnchor,
            onSelectCurrent = onSelectCurrentAnchor,
            onSelectChapter = onSelectCustomAnchor,
            onDismiss = { showAnchorPicker = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlotDiscussionEmptyGuide(
    bodyTextColor: Color,
    secondaryTextColor: Color,
    onStarterClick: (String) -> Unit,
    onCustomDirectionClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "不知道接下来往哪走？可以先让 AI 帮你梳理方向并主动向你提问，也可以直接输入你的脑洞。",
            style = MaterialTheme.typography.bodyMedium,
            color = bodyTextColor
        )
        Text(
            text = "点击快速开聊：",
            style = MaterialTheme.typography.labelMedium,
            color = secondaryTextColor
        )
        val starters = listOf(
            "帮我分析接下来最合理的 3 种剧情走向，并向我提问确认细节",
            "结合前文伏笔与人物性格，设计下一阶段核心冲突与高潮",
            "我想让主角走一条反套路/黑色幽默路线，请给几个建议并问我关键设定"
        )
        starters.forEach { prompt ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                onClick = { onStarterClick(prompt) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = bodyTextColor
                    )
                }
            }
        }
        OutlinedButton(
            onClick = onCustomDirectionClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("都不满意 / 我自己提出一个方向")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantPlotMessageCard(
    message: OutlineDiscussionEntity,
    quickOptions: List<String>,
    isLatestAssistant: Boolean,
    generating: Boolean,
    isAdopted: Boolean,
    hasExistingUpcomingPlan: Boolean,
    isDark: Boolean,
    bodyTextColor: Color,
    secondaryTextColor: Color,
    onSelectDirection: (String) -> Unit,
    onCustomDirection: () -> Unit,
    onAdoptOutline: (master: String?, upcoming: String?, appendBeats: Boolean) -> Unit
) {
    val cardColor = if (isDark) Color(0xFF1F2030).copy(alpha = 0.86f) else Color.White.copy(alpha = 0.82f)
    val hasDraft = !message.proposedMasterOutline.isNullOrBlank() ||
        !message.proposedUpcomingPlan.isNullOrBlank()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardColor,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = message.modelUsed?.takeIf { it.isNotBlank() } ?: "剧情策划 AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            SelectionContainer {
                MarkdownText(
                    markdown = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = bodyTextColor,
                    secondaryColor = secondaryTextColor
                )
            }

            if (hasDraft) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.16f else 0.09f),
                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "大纲更新草案",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!message.proposedMasterOutline.isNullOrBlank()) {
                            Text(
                                text = "【全书主线总纲】",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            MarkdownText(
                                markdown = message.proposedMasterOutline,
                                style = MaterialTheme.typography.bodySmall,
                                color = bodyTextColor,
                                secondaryColor = secondaryTextColor
                            )
                        }
                        if (!message.proposedUpcomingPlan.isNullOrBlank()) {
                            Text(
                                text = "【近期分章规划】",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            MarkdownText(
                                markdown = message.proposedUpcomingPlan,
                                style = MaterialTheme.typography.bodySmall,
                                color = bodyTextColor,
                                secondaryColor = secondaryTextColor
                            )
                        }
                        if (hasExistingUpcomingPlan && !message.proposedUpcomingPlan.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GlassCapsuleButton(
                                    text = if (isAdopted) "已追加" else "追加细纲",
                                    onClick = {
                                        onAdoptOutline(
                                            message.proposedMasterOutline,
                                            message.proposedUpcomingPlan,
                                            true
                                        )
                                    },
                                    filled = !isAdopted,
                                    enabled = !generating,
                                    icon = if (isAdopted) Icons.Rounded.Check else Icons.Rounded.Add,
                                    modifier = Modifier.weight(1f)
                                )
                                GlassCapsuleButton(
                                    text = "覆盖并开写",
                                    onClick = {
                                        onAdoptOutline(
                                            message.proposedMasterOutline,
                                            message.proposedUpcomingPlan,
                                            false
                                        )
                                    },
                                    filled = false,
                                    enabled = !generating,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            GlassCapsuleButton(
                                text = if (isAdopted) "已采纳并准备开写" else "采纳并准备开写",
                                onClick = {
                                    onAdoptOutline(
                                        message.proposedMasterOutline,
                                        message.proposedUpcomingPlan,
                                        false
                                    )
                                },
                                filled = !isAdopted,
                                enabled = !generating,
                                icon = if (isAdopted) Icons.Rounded.Check else Icons.Rounded.AutoAwesome,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (quickOptions.isNotEmpty() || isLatestAssistant) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                if (quickOptions.isNotEmpty()) {
                    Text(
                        text = "点选倾向的走向（或自行输入修正）：",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryTextColor
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        quickOptions.forEach { option ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.10f),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                                enabled = !generating,
                                onClick = { onSelectDirection(option) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = buildMarkdownInlineAnnotatedString(
                                        text = option,
                                        accentColor = MaterialTheme.colorScheme.primary,
                                        codeBackgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
                                        codeTextColor = bodyTextColor
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = bodyTextColor,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
                if (isLatestAssistant) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
                            enabled = !generating,
                            onClick = onCustomDirection
                        ) {
                            Text(
                                text = "都不满意，我自己定方向",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OutlineEditorDialog(
    outline: BookOutlineEntity?,
    validSummaryCount: Int,
    totalChapterCount: Int,
    persistedVectorCount: Int,
    isWarmingSummaries: Boolean,
    memoryStatusMessage: String?,
    hazeState: HazeState?,
    onToggleUseOutline: () -> Unit,
    onSaveOutline: (master: String, upcoming: String) -> Unit,
    onClearOutline: () -> Unit,
    onWarmUpSummaries: () -> Unit,
    onDismiss: () -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bodyTextColor = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    var masterText by remember(outline?.bookId, outline?.masterOutline) {
        mutableStateOf(outline?.masterOutline.orEmpty())
    }
    var upcomingText by remember(outline?.bookId, outline?.upcomingPlan) {
        mutableStateOf(outline?.upcomingPlan.orEmpty())
    }
    var confirmClear by remember { mutableStateOf(false) }

    AdaptiveGlassDialog(
        onDismissRequest = onDismiss,
        maxWidth = adaptive.maxDialogSheetWidth
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "长期大纲与记忆缓存",
                    style = MaterialTheme.typography.titleLarge,
                    color = bodyTextColor,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = bodyTextColor)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "续写时自动遵循长期大纲",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = bodyTextColor
                    )
                    Text(
                        text = "开启后每次生成下一章都会优先注入下方总纲与近期规划",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = outline?.useInContinuation ?: true,
                    onCheckedChange = { onToggleUseOutline() }
                )
            }

            OutlinedTextField(
                value = masterText,
                onValueChange = { masterText = it },
                label = { Text("全书主线总纲 / 核心设定") },
                placeholder = { Text("例如：核心主线、主角终极目标、关键人物关系与长线伏笔…") },
                minLines = 3,
                maxLines = 7,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = upcomingText,
                onValueChange = { upcomingText = it },
                label = { Text("后续分章细纲（推荐 6~8 章，支持复选节拍格式）") },
                placeholder = { Text("例如：\n- [ ] 1. 《雨夜截杀》 | 核心剧情：... | 章末钩子：...") },
                minLines = 4,
                maxLines = 9,
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "本地长期缓存状态（按章节哈希增量保存，不因保存新章而清空）",
                        style = MaterialTheme.typography.labelLarge,
                        color = bodyTextColor
                    )
                    Text(
                        text = "• 章节剧情记忆卡：$validSummaryCount / ${totalChapterCount.coerceAtLeast(1)} 章",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• 持久化 Embedding 向量：$persistedVectorCount 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!memoryStatusMessage.isNullOrBlank()) {
                        Text(
                            text = memoryStatusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(
                        onClick = onWarmUpSummaries,
                        enabled = !isWarmingSummaries && totalChapterCount > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isWarmingSummaries) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (isWarmingSummaries) "正在生成并长期保存记忆卡…"
                            else "补全前文剧情记忆卡（每次最多 8 章）"
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (masterText.isNotBlank() || upcomingText.isNotBlank()) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text("清空", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("取消") }
                GlassCapsuleButton(
                    text = "保存大纲",
                    onClick = {
                        onSaveOutline(masterText, upcomingText)
                        onDismiss()
                    },
                    filled = true,
                    state = hazeState
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空本书长期大纲？") },
            text = { Text("将清除当前保存的全书总纲与近期规划，剧情讨论记录仍可保留。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    masterText = ""
                    upcomingText = ""
                    onClearOutline()
                    onDismiss()
                }) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}
