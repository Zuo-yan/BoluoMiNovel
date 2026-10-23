package com.novelai.assistant.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * 物理透镜折射 AGSL 着色器（Android 13+ / API 33+ 原生硬件着色器）
 *
 * 核心光学公式：
 * 1. 利用圆角超椭圆（SDF: Signed Distance Field）计算凸透镜表面法线向量；
 * 2. 对底层内容进行基于法线的 UV 弯曲采样（Displacement Refraction），产生真实物理折射与放大畸变；
 * 3. 菲涅尔视角纯白反射高光（Fresnel Rim Light）。
 */
private const val LIQUID_GLASS_AGSL = """
    uniform shader compositedImage;
    uniform float2 iResolution;
    uniform float iCornerRadius;
    uniform float iRefraction;

    float sdRoundedBox(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }

    half4 main(float2 fragCoord) {
        float2 halfRes = iResolution * 0.5;
        float2 p = fragCoord - halfRes;
        float d = sdRoundedBox(p, halfRes, iCornerRadius);

        if (d > 0.0) {
            return half4(0.0);
        }

        // 凸透镜折射带：边缘 26px 范围内产生纯净透镜弯曲
        float edgeWidth = min(26.0, min(iResolution.x, iResolution.y) * 0.25);
        float edgeFactor = smoothstep(-edgeWidth, 0.0, d);

        // 计算法线向量
        float eps = 1.0;
        float2 grad = float2(
            sdRoundedBox(p + float2(eps, 0.0), halfRes, iCornerRadius) - sdRoundedBox(p - float2(eps, 0.0), halfRes, iCornerRadius),
            sdRoundedBox(p + float2(0.0, eps), halfRes, iCornerRadius) - sdRoundedBox(p - float2(0.0, eps), halfRes, iCornerRadius)
        );
        float2 normal = normalize(grad + 0.0001);

        // 纯净物理折射 UV 位移（向透镜厚度中心弯曲）
        float2 disp = -normal * edgeFactor * iRefraction;
        half4 color = compositedImage.eval(fragCoord + disp);

        // 菲涅尔切面高光（纯白主光源入射）
        float2 lightDir = normalize(float2(-0.4, -0.9));
        float spec = pow(max(0.0, dot(normal, -lightDir)), 5.0) * edgeFactor * 0.45;
        color.rgb += half3(spec);

        return color;
    }
"""

class GlassStateHolder {
    val state: HazeState = HazeState()
}

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
 * 1. 【纯净透镜折射】Android 13+ (API 33+) 优先走 AGSL 原生凸透镜法线弯曲；
 * 2. 【通透水晶质感】浅色 36% 纯净水晶透光，深色 50% 黑曜玻璃，Haze 实时模糊透出底层内容；
 * 3. 【次表面高光】纯白中性光晕，呈现水滴凸透镜的物理厚度与立体微弧；
 * 4. 【双层物理边缘】顶部 1.2dp 锐利纯白高光切面，底部全反射折射暗边，勾勒玻璃边界。
 */
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

    // 纯净中性玻璃底色（无任何彩色干涉：浅色下 36% 纯净水晶透光，深色下 50% 烟熏黑曜玻璃）
    val resolvedTint = tint ?: if (isDark) {
        Color(0xFF16161E).copy(alpha = 0.50f)
    } else {
        Color(0xFFF7F8FC).copy(alpha = 0.36f)
    }

    val hazeModifier = if (state != null) {
        Modifier.hazeEffect(state = state) {
            this.style = HazeStyle(
                backgroundColor = Color.Transparent,
                tints = listOf(HazeTint(resolvedTint)),
                blurRadius = blurRadius
            )
        }
    } else {
        Modifier.background(resolvedTint, shape)
    }

    val agslModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AgslRefractionHelper.createModifier(26.dp)
    } else {
        Modifier
    }

    return this
        .clip(shape)
        .then(hazeModifier)
        .then(agslModifier)
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

/** 无 Haze 上下文时的静态液态玻璃卡 */
@Composable
fun Modifier.staticLiquidGlass(
    tint: Color? = null,
    shape: Shape = RoundedCornerShape(26.dp)
): Modifier = this.liquidGlass(
    state = null,
    tint = tint,
    shape = shape
)

/** 静态玻璃卡（兼容旧接口） */
fun Modifier.staticGlass(tint: Color, shape: Shape): Modifier = this
    .clip(shape)
    .background(tint, shape)
    .border(
        width = 0.75.dp,
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f))
        ),
        shape = shape
    )

/**
 * Android 13+ (API 33+) 原生 AGSL 物理透镜折射隔离辅助类
 * 独立于静态类加载器，防止在 API 32 及以下（如 Android 9 模拟器）发生 NoClassDefFoundError
 */
private object AgslRefractionHelper {
    private var cachedShader: Any? = null

    @Composable
    fun createModifier(cornerRadius: Dp): Modifier {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Modifier
        return runCatching {
            val shader = (cachedShader as? RuntimeShader) ?: RuntimeShader(LIQUID_GLASS_AGSL).also { cachedShader = it }
            Modifier.graphicsLayer {
                try {
                    shader.setFloatUniform("iResolution", size.width, size.height)
                    shader.setFloatUniform("iCornerRadius", cornerRadius.toPx())
                    shader.setFloatUniform("iRefraction", 12f)
                    renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "compositedImage").asComposeRenderEffect()
                } catch (_: Throwable) {}
            }
        }.getOrDefault(Modifier)
    }
}