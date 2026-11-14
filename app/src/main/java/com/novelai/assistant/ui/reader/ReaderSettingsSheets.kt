package com.novelai.assistant.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.novelai.assistant.ui.components.AdaptiveGlassSheet
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.theme.NovelColors
import com.novelai.assistant.ui.theme.ReaderPalettes
import java.util.Locale

@Composable
private fun SettingsGlassGroup(
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.035f)
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = BorderStroke(0.7.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/** 阅读设置面板（自适应玻璃弹窗 / 底板） */
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
    onOpenTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    val accent = if (isDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary
    val sliderColors = SliderDefaults.colors(
        thumbColor = accent,
        activeTrackColor = accent,
        inactiveTrackColor = if (isDark) Color(0xFF2C2C3A) else MaterialTheme.colorScheme.surfaceVariant
    )
    val labelColor = if (isDark) NovelColors.GlassOnDarkText else MaterialTheme.colorScheme.onSurface
    val secondaryColor = if (isDark) Color(0xFFA8A8B6) else MaterialTheme.colorScheme.onSurfaceVariant

    AdaptiveGlassSheet(
        onDismissRequest = onDismiss,
        maxWidth = adaptive.maxDialogSheetWidth,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            Modifier
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
                    "阅读设置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = labelColor,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = secondaryColor)
                }
            }

            // 亮度与字号/排版卡片
            SettingsGlassGroup(isDark = isDark) {
                // 亮度
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("亮度", style = MaterialTheme.typography.bodyMedium, color = labelColor, modifier = Modifier.width(64.dp))
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
                        color = secondaryColor,
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.End
                    )
                }

                // 字号
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FormatSize, contentDescription = null, tint = labelColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("字号", style = MaterialTheme.typography.bodyMedium, color = labelColor, modifier = Modifier.width(40.dp))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onFontSizeChange(settings.fontSizeSp - 1) }) {
                        Text("A-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = labelColor)
                    }
                    Text(
                        "${settings.fontSizeSp}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { onFontSizeChange(settings.fontSizeSp + 1) }) {
                        Text("A+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = labelColor)
                    }
                }

                // 行距
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("行距", style = MaterialTheme.typography.bodyMedium, color = labelColor, modifier = Modifier.width(64.dp))
                    Slider(
                        value = settings.lineSpacingMultiplier,
                        onValueChange = { onLineSpacingChange((it * 10).toInt() / 10f) },
                        valueRange = 1.2f..2.4f,
                        colors = sliderColors,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        String.format(Locale.getDefault(), "%.1f", settings.lineSpacingMultiplier),
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryColor,
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.End
                    )
                }

                // 边距
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("边距", style = MaterialTheme.typography.bodyMedium, color = labelColor, modifier = Modifier.width(64.dp))
                    Slider(
                        value = settings.horizontalPaddingDp.toFloat(),
                        onValueChange = { onHorizontalPaddingChange(it.toInt()) },
                        valueRange = 8f..48f,
                        colors = sliderColors,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${settings.horizontalPaddingDp}",
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryColor,
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.End
                    )
                }

                // 段距
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("段距", style = MaterialTheme.typography.bodyMedium, color = labelColor, modifier = Modifier.width(64.dp))
                    Slider(
                        value = settings.paragraphSpacingDp.toFloat(),
                        onValueChange = { onParagraphSpacingChange(it.toInt()) },
                        valueRange = 2f..28f,
                        colors = sliderColors,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${settings.paragraphSpacingDp}",
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryColor,
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.End
                    )
                }
            }

            // 纸张主题 & 字体卡片
            SettingsGlassGroup(isDark = isDark) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("背景", style = MaterialTheme.typography.bodyMedium, color = labelColor, modifier = Modifier.width(64.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReaderBgTheme.entries.forEach { theme ->
                            val p = ReaderPalettes.of(theme.name)
                            val selected = settings.bgTheme == theme
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(p.background), CircleShape)
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) accent
                                        else (if (isDark) Color(0xFF565666) else MaterialTheme.colorScheme.outline),
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("衬线字体", style = MaterialTheme.typography.bodyMedium, color = labelColor, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.serifFont,
                        onCheckedChange = onSerifChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = if (isDark) Color(0xFFB5B5C2) else Color.White,
                            uncheckedTrackColor = if (isDark) Color(0xFF2C2C3A) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                Text("翻页模式", style = MaterialTheme.typography.labelMedium, color = secondaryColor)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = settings.pageMode == PageMode.SCROLL,
                        onClick = { onPageModeChange(PageMode.SCROLL) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = accent.copy(alpha = if (isDark) 0.28f else 0.16f),
                            activeContentColor = if (isDark) NovelColors.GlassOnDarkText else MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = if (isDark) Color(0xFF232531) else Color.Transparent,
                            inactiveContentColor = secondaryColor,
                            activeBorderColor = accent.copy(alpha = 0.65f),
                            inactiveBorderColor = if (isDark) Color(0xFF3A3C4C) else MaterialTheme.colorScheme.outline
                        )
                    ) { Text("上下滚动") }
                    SegmentedButton(
                        selected = settings.pageMode == PageMode.PAGED,
                        onClick = { onPageModeChange(PageMode.PAGED) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = accent.copy(alpha = if (isDark) 0.28f else 0.16f),
                            activeContentColor = if (isDark) NovelColors.GlassOnDarkText else MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = if (isDark) Color(0xFF232531) else Color.Transparent,
                            inactiveContentColor = secondaryColor,
                            activeBorderColor = accent.copy(alpha = 0.65f),
                            inactiveBorderColor = if (isDark) Color(0xFF3A3C4C) else MaterialTheme.colorScheme.outline
                        )
                    ) { Text("左右翻页") }
                }
            }

            // 听书定时卡片
            SettingsGlassGroup(isDark = isDark) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenTimer)
                        .padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("听书定时", style = MaterialTheme.typography.bodyMedium, color = labelColor, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            timerRemainingSeconds != null -> "倒计时 ${formatRemaining(timerRemainingSeconds)}"
                            timerMode == TtsTimerMode.END_OF_CHAPTER -> "听完本章停止"
                            else -> timerMode.label
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (timerMode != TtsTimerMode.OFF) accent else secondaryColor
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = secondaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun TtsTimerSheet(
    currentMode: TtsTimerMode,
    remainingSeconds: Int?,
    isDark: Boolean,
    onSelectMode: (TtsTimerMode) -> Unit,
    onDismiss: () -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    val labelColor = if (isDark) NovelColors.GlassOnDarkText else MaterialTheme.colorScheme.onSurface
    val secondaryColor = if (isDark) Color(0xFFA8A8B6) else MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (isDark) NovelColors.IndigoLight else MaterialTheme.colorScheme.primary
    val unselectedBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.035f)
    val unselectedBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)

    AdaptiveGlassSheet(
        onDismissRequest = onDismiss,
        maxWidth = adaptive.maxDialogSheetWidth,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 2.dp)
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
                    fontWeight = FontWeight.SemiBold,
                    color = labelColor,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = secondaryColor)
                }
            }

            if (remainingSeconds != null && remainingSeconds > 0) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accent.copy(alpha = if (isDark) 0.20f else 0.12f),
                    border = BorderStroke(0.7.dp, accent.copy(alpha = 0.35f)),
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
                    shape = RoundedCornerShape(14.dp),
                    color = accent.copy(alpha = if (isDark) 0.20f else 0.12f),
                    border = BorderStroke(0.7.dp, accent.copy(alpha = 0.35f)),
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
                color = secondaryColor
            )

            TtsTimerMode.entries.forEach { mode ->
                val isSelected = mode == currentMode
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) accent.copy(alpha = if (isDark) 0.22f else 0.14f) else unselectedBg,
                    border = BorderStroke(
                        if (isSelected) 1.dp else 0.7.dp,
                        if (isSelected) accent.copy(alpha = 0.75f) else unselectedBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
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
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
                Spacer(Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = if (isDark) 0.42f else 0.55f),
                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
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
