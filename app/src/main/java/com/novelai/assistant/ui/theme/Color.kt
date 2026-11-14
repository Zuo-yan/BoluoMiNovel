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
    val SEPIA = ReaderPalette(0xFFF5ECD8, 0xFF4A4234, 0xFF9A8F7A, 0xFFEFE3C8, 0xFF4A4234)
    val GREEN = ReaderPalette(0xFFCCE8CF, 0xFF2C3A2E, 0xFF6B8070, 0xFFBEE0C2, 0xFF2C3A2E)
    val DARK = ReaderPalette(0xFF1B1B20, 0xFFC7C7CF, 0xFF77777F, 0xFF232329, 0xFFC7C7CF)
    val BLACK = ReaderPalette(0xFF000000, 0xFF9A9AA2, 0xFF5A5A62, 0xFF121216, 0xFF9A9AA2)

    fun of(name: String): ReaderPalette = when (name) {
        "SEPIA" -> SEPIA
        "GREEN" -> GREEN
        "DARK" -> DARK
        "BLACK" -> BLACK
        else -> WHITE
    }
}
