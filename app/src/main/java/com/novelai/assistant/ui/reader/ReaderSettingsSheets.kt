package com.novelai.assistant.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novelai.assistant.data.prefs.PageMode
import com.novelai.assistant.data.prefs.ReaderBgTheme
import com.novelai.assistant.data.prefs.ReadingSettings
import com.novelai.assistant.data.tts.BatteryOptimizationHelper
import com.novelai.assistant.ui.theme.NovelColors
import com.novelai.assistant.ui.theme.ReaderPalettes

/** 阅读设置面板内容 */
@Composable
internal fun ReaderSettingsSheet(
    settings: ReadingSettings,
    brightness: Float,
    isDark: Boolean,
    timerMode: TtsTimerMode,
    timerRemainingSeconds: Int?,
    onBrightnessChange: (Float) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onHorizontalPaddingChange: (Int) -> Unit,
    onParagraphSpacingChange: (Int) -> Unit,
    onBgThemeChange: (ReaderBgTheme) -> Unit,
    onSerifChange: (Boolean) -> Unit,
    onPageModeChange: (PageMode) -> Unit,
    onOpenTimer: () -> Unit
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
                textAlign = TextAlign.End
            )
        }

        // 字号
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FormatSize, contentDescription = null, tint = labelColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("字号", style = MaterialTheme.typography.bodyLarge, color = labelColor, modifier = Modifier.width(52.dp))
            IconButton(onClick = { onFontSizeChange(settings.fontSizeSp - 1) }) {
                Text("A-", style = MaterialTheme.typography.titleMedium, color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "${settings.fontSizeSp}",
                style = MaterialTheme.typography.titleMedium,
                color = if (isDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { onFontSizeChange(settings.fontSizeSp + 1) }) {
                Text("A+", style = MaterialTheme.typography.titleMedium, color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface)
            }
        }

        // 行距
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("行距", style = MaterialTheme.typography.bodyLarge, color = labelColor, modifier = Modifier.width(80.dp))
            Slider(
                value = settings.lineSpacingMultiplier,
                onValueChange = { onLineSpacingChange((it * 10).toInt() / 10f) },
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
                onValueChange = { onHorizontalPaddingChange(it.toInt()) },
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
                onValueChange = { onParagraphSpacingChange(it.toInt()) },
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
                            .clickable { onBgThemeChange(theme) },
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
                onCheckedChange = onSerifChange,
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
                onClick = { onPageModeChange(PageMode.SCROLL) },
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
                onClick = { onPageModeChange(PageMode.PAGED) },
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
                .clickable(onClick = onOpenTimer)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TtsTimerSheet(
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
                        BorderStroke(1.dp, accent.copy(alpha = 0.8f))
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
            val sheetContext = LocalContext.current
            val isIgnoringBattery = remember { BatteryOptimizationHelper.isIgnoringBatteryOptimizations(sheetContext) }
            if (!isIgnoringBattery) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    onClick = { BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(sheetContext) },
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
                                fontWeight = FontWeight.Bold,
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
