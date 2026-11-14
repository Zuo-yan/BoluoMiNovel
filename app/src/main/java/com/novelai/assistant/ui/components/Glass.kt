package com.novelai.assistant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun rememberGlassState(): HazeState = remember { HazeState() }

fun Modifier.glassSource(state: HazeState): Modifier = this.hazeSource(state)

/**
 * 柔和环境光晕层（Ambient Backdrop）
 */
@Composable
fun AmbientBackdrop(
    accent: Color = MaterialTheme.colorScheme.primary,
    secondary: Color = MaterialTheme.colorScheme.tertiary,
    modifier: Modifier = Modifier,
    intensity: Float = 0.75f
) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        fun bloom(center: Offset, radius: Float, color: Color, a: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = a * intensity), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        bloom(Offset(w * 0.18f, h * 0.08f), w * 0.85f, accent, 0.16f)
        bloom(Offset(w * 0.90f, h * 0.38f), w * 0.70f, secondary, 0.12f)
        bloom(Offset(w * 0.45f, h * 0.92f), w * 0.90f, accent, 0.18f)
    }
}

/**
 * 液态玻璃核心修饰符 (Liquid Glass)
 *
 * 遵循极简高级光学质感（去除动态彩色流光，专注通透与物理折射）：
 * 1. 【通透磨砂底色】浅色 82% / 深色 85% 半透明表面，避免底部透出伪影；
 * 2. 【次表面高光】纯白中性光晕，呈现水滴凸透镜的物理厚度与立体微弧；
 * 3. 【双层物理边缘】顶部 1.2dp 锐利纯白高光切面，底部微弱折射暗边，勾勒玻璃边界。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.liquidGlass(
    state: HazeState? = null,
    tint: Color? = null,
    shape: Shape = RoundedCornerShape(26.dp),
    blurRadius: Dp = 28.dp,
    enableFlowSheen: Boolean = false,
    borderWidth: Dp = 1.dp
): Modifier {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val resolvedTint = tint ?: if (isDark) {
        Color(0xFF1C1C24).copy(alpha = 0.85f)
    } else {
        Color(0xFFF7F8FC).copy(alpha = 0.82f)
    }

    return this
        .clip(shape)
        .background(resolvedTint, shape)
        .drawWithCache {
            val width = size.width
            val height = size.height

            // 1. 次表面透镜反射微光（纯白中性光，赋予水滴透镜的 3D 凸面感）
            val innerRefractionBrush = Brush.radialGradient(
                colors = if (isDark) {
                    listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.32f),
                        Color.Transparent
                    )
                },
                center = Offset(width * 0.30f, height * 0.15f),
                radius = width.coerceAtLeast(height) * 0.95f
            )

            // 2. 纯净内凹透镜轮廓（顶部 1.2dp 强高光切面，底部微弱暗部折射，营造厚度感）
            val outline = shape.createOutline(size, layoutDirection, this)
            val innerBevelBrush = if (isDark) {
                Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.50f),
                    0.35f to Color.White.copy(alpha = 0.10f),
                    0.80f to Color.Transparent,
                    1.0f to Color.White.copy(alpha = 0.15f)
                )
            } else {
                Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.85f),
                    0.25f to Color.White.copy(alpha = 0.20f),
                    0.80f to Color.Black.copy(alpha = 0.04f),
                    1.0f to Color.Black.copy(alpha = 0.09f)
                )
            }

            onDrawWithContent {
                // 绘制透镜次表面微光
                drawRect(brush = innerRefractionBrush)

                // 绘制主体内容（文字、图标）
                drawContent()

                // 绘制内层透镜倒角折射光芒（纯白色倒角，无任何彩色）
                drawOutline(
                    outline = outline,
                    brush = innerBevelBrush,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }
        // 外层菲涅尔物理边界描边（纯净切面高光与折射分界线）
        .border(
            width = borderWidth,
            brush = if (isDark) {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.65f),
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.28f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color.Black.copy(alpha = 0.09f),
                        Color.Black.copy(alpha = 0.14f)
                    )
                )
            },
            shape = shape
        )
}

/** 悬浮玻璃面板（兼容旧接口） */
@Composable
fun Modifier.glassEffect(
    state: HazeState,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
    blurRadius: Dp = 26.dp,
    shape: Shape = RoundedCornerShape(0.dp)
): Modifier = this.liquidGlass(
    state = state,
    tint = tint,
    shape = shape,
    blurRadius = blurRadius
)
