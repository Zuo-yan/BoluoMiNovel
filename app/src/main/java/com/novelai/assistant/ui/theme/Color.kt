package com.novelai.assistant.ui.theme

import androidx.compose.ui.graphics.Color

// 低饱和度 iOS 风格配色：近黑近白主界面 + 精致点缀色
object NovelColors {
    // 主点缀：iOS 靛蓝
    val Indigo = Color(0xFF5856D6)
    val IndigoLight = Color(0xFF9A97FF)
    // 辅助点缀
    val Teal = Color(0xFF30B0C7)
    val Mint = Color(0xFF00C7BE)
    val Amber = Color(0xFFFFB340)
    val Pink = Color(0xFFFF6482)
    val Lemon = Color(0xFFFFD60A)

    // 原生 / AI 标识色
    val BadgeOriginal = Color(0xFF8E8E93)   // [原] 灰
    val BadgeAi = Color(0xFF5856D6)         // [AI] 靛蓝
    val BadgeBranch = Color(0xFFFF6482)     // [分] 粉

    // 液态玻璃表面与高对比度文本色（遵循深色 #F2F2F7 高亮可读规范）
    val GlassCanvasDark = Color(0xFF0A0A0F)
    val GlassSurfaceDark = Color(0xFF15161E)
    val GlassCanvasLight = Color(0xFFF2F3F7)
    val GlassSurfaceLight = Color(0xFFFFFFFF)
    val GlassOnDarkText = Color(0xFFF2F2F7)
    val GlassOnLightText = Color(0xFF1C1C22)
    val GlassAccentBlueDark = Color(0xFF66B2FF)
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
    val DARK = ReaderPalette(0xFF121216, 0xFFDCDCE6, 0xFF858594, 0xFF1B1C24, 0xFFF2F2F7)

    fun of(name: String): ReaderPalette = when (name) {
        "DARK", "BLACK" -> DARK
        else -> WHITE
    }
}
