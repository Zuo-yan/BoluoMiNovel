package com.novelai.assistant.ui.theme

import androidx.compose.ui.graphics.Color

// 低饱和度 iOS / 荣耀极简配色：OLED 纯黑 / 极简白主界面 + 统一极光靛蓝点缀
object NovelColors {
    // 主点缀：极光靛蓝 (Aurora Indigo)
    val Indigo = Color(0xFF5E5CE6)
    val IndigoLight = Color(0xFF7876FF)
    // 辅助点缀
    val Teal = Color(0xFF30B0C7)
    val Mint = Color(0xFF00C7BE)
    val Amber = Color(0xFFFFB340)
    val Pink = Color(0xFFFF6482)
    val Lemon = Color(0xFFFFD60A)

    // 原生 / AI 标识色
    val BadgeOriginal = Color(0xFF8E8E93)   // [原] 灰
    val BadgeAi = Color(0xFF5E5CE6)         // [AI] 极光靛蓝
    val BadgeBranch = Color(0xFFFF6482)     // [分] 粉

    // 液态玻璃表面与高对比度文本色（深色采用纯正 OLED #000000 黑底）
    val GlassCanvasDark = Color(0xFF000000)
    val GlassSurfaceDark = Color(0xFF121215)
    val GlassCanvasLight = Color(0xFFF2F3F7)
    val GlassSurfaceLight = Color(0xFFFFFFFF)
    val GlassOnDarkText = Color(0xFFF2F2F7)
    val GlassOnLightText = Color(0xFF1C1C22)
    val GlassAccentBlueDark = Color(0xFF8C8AFF)
}

// 阅读器背景主题
data class ReaderPalette(
    val background: Long,
    val text: Long,
    val secondaryText: Long,
    val chrome: Long,       // 顶栏/底栏底色
    val onChrome: Long
)

object ReaderPalettes {
    val WHITE = ReaderPalette(0xFFF7F7FA, 0xFF1C1C22, 0xFF8A8A94, 0xFFFFFFFF, 0xFF1C1C22)
    val DARK = ReaderPalette(0xFF000000, 0xFFDCDCE6, 0xFF858594, 0xFF0A0A0C, 0xFFF2F2F7)

    fun of(name: String): ReaderPalette = when (name) {
        "DARK", "BLACK" -> DARK
        else -> WHITE
    }
}
