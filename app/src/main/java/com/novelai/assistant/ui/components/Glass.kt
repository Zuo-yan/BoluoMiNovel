package com.novelai.assistant.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
 * 物理透镜折射与三棱镜色散 AGSL 着色器（Android 13+ / API 33+ 原生硬件着色器）
 *
 * 核心光学公式：
 * 1. 利用圆角超椭圆（SDF: Signed Distance Field）计算凸透镜表面法线向量；
 * 2. 对底层内容进行基于法线的 UV 弯曲采样（Displacement Refraction），产生真实放大畸变；
 * 3. 分别分离 R、G、B 三个波长的折射偏移（Chromatic Aberration），模拟三棱镜色散；
 * 4. 菲涅尔视角反射高光（Fresnel Rim Light）。
 */
private const val LIQUID_GLASS_AGSL = """
    uniform shader compositedImage;
    uniform float2 iResolution;
    uniform float iCornerRadius;
    uniform float iRefraction;
    uniform float iDispersion;

    float sdRoundedBox(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }

    half4 main(float2 fragCoord) {
        float2 halfRes = iResolution * 0.5;
        float2 p = fragCoord - halfRes;
        float2 b = halfRes;
        float d = sdRoundedBox(p, b, iCornerRadius);

        if (d > 0.0) {
            return half4(0.0);
        }

        // 凸透镜折射带：边缘 24px 范围内产生强烈透镜弯曲与色散
        float edgeWidth = min(26.0, min(iResolution.x, iResolution.y) * 0.25);
        float edgeFactor = smoothstep(-edgeWidth, 0.0, d);

        // 计算法线向量
        float eps = 1.0;
        float2 grad = float2(
            sdRoundedBox(p + float2(eps, 0.0), b, iCornerRadius) - sdRoundedBox(p - float2(eps, 0.0), b, iCornerRadius),
            sdRoundedBox(p + float2(0.0, eps), b, iCornerRadius) - sdRoundedBox(p - float2(0.0, eps), b, iCornerRadius)
        );
        float2 normal = normalize(grad + 0.0001);

        // 物理折射 UV 位移（向透镜厚度中心弯曲）
        float2 disp = -normal * edgeFactor * iRefraction;

        // 三棱镜色散（RGB 分离折射率）
        float2 dispR = disp * (1.0 + iDispersion * 0.15);
        float2 dispG = disp;
        float2 dispB = disp * (1.0 - iDispersion * 0.15);

        half4 colR = compositedImage.eval(fragCoord + dispR);
        half4 colG = compositedImage.eval(fragCoord + dispG);
        half4 colB = compositedImage.eval(fragCoord + dispB);

        half4 color = half4(colR.r, colG.g, colB.b, colG.a);

        // 菲涅尔切面高光（左上角主光源入射）
        float2 lightDir = normalize(float2(-0.4, -0.9));
        float spec = max(0.0, dot(normal, -lightDir));
        spec = pow(spec, 5.0) * edgeFactor * 0.55;
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
 * 物理光学特性：
 * 1. 【AGSL 物理折射】在 Android 13+ (API 33+) 上优先启用原生 AGSL Shader，执行凸透镜法线弯曲与三棱镜色散；
 * 2. 【多层光谱色散（全版本兼容）】在 Compose Canvas 绘制层叠加三棱镜边缘色散光辉（青/紫/琥珀分色高光）；
 * 3. 【凸透镜内凹倒角与焦散暗线】顶部 1.2dp 强高光切面，底部 1.5dp 全反射折射暗线，勾勒玻璃厚度；
 * 4. 【动态液态幻彩流光】平滑流光在子内容下方扫过表面，兼具通透感与可读性；
 * 5. 【Haze 实时模糊透视】38% 极高透水滴底色，彻底杜绝塑料白板感。
 */
@Composable
fun Modifier.liquidGlass(
    state: HazeState? = null,
    tint: Color? = null,
    shape: Shape = RoundedCornerShape(26.dp),
    blurRadius: Dp = 28.dp,
    enableFlowSheen: Boolean = true,
    sheenDurationMillis: Int = 6000,
    borderWidth: Dp = 1.dp
): Modifier {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val resolvedTint = tint ?: if (isDark) {
        Color(0xFF16161E).copy(alpha = 0.52f)
    } else {
        Color(0xFFF7F8FC).copy(alpha = 0.38f)
    }

    val transition = rememberInfiniteTransition(label = "liquidGlassSheen")
    val shimmerOffset by if (enableFlowSheen) {
        transition.animateFloat(
            initialValue = -0.6f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = sheenDurationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
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

            // 1. 次表面透镜反射微光
            val innerRefractionBrush = Brush.radialGradient(
                colors = if (isDark) {
                    listOf(
                        Color.White.copy(alpha = 0.12f),
                        primaryColor.copy(alpha = 0.05f),
                        Color.Transparent
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.35f),
                        primaryColor.copy(alpha = 0.06f),
                        Color.Transparent
                    )
                },
                center = Offset(width * 0.30f, height * 0.15f),
                radius = width.coerceAtLeast(height) * 0.95f
            )

            // 2. 三棱镜边缘色散光谱轮廓（Chromatic Aberration Fringe）
            val outline = shape.createOutline(size, layoutDirection, this)
            val chromaticFringeBrush = Brush.sweepGradient(
                0.0f to Color(0xFF00E5FF).copy(alpha = 0.22f), // 青色光辉
                0.25f to Color.White.copy(alpha = 0.65f),      // 顶部白色强高光
                0.50f to Color(0xFFD500F9).copy(alpha = 0.18f), // 紫色色散
                0.75f to Color(0xFFFF9100).copy(alpha = 0.14f), // 琥珀橙色散
                1.0f to Color(0xFF00E5FF).copy(alpha = 0.22f),
                center = Offset(width * 0.5f, height * 0.5f)
            )

            // 3. 内凹透镜轮廓（顶部 1.2dp 强烈高光切面，底部全反射暗边）
            val innerBevelBrush = if (isDark) {
                Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.55f),
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

            // 4. 动态流动的液态幻彩光斑
            val sheenBrush = Brush.linearGradient(
                0.0f to Color.Transparent,
                0.38f to Color.White.copy(alpha = 0.04f),
                0.46f to primaryColor.copy(alpha = if (isDark) 0.18f else 0.14f),
                0.50f to Color.White.copy(alpha = if (isDark) 0.35f else 0.45f),
                0.54f to tertiaryColor.copy(alpha = if (isDark) 0.18f else 0.14f),
                0.62f to Color.White.copy(alpha = 0.04f),
                1.0f to Color.Transparent,
                start = Offset(width * (shimmerOffset - 0.45f), 0f),
                end = Offset(width * (shimmerOffset + 0.45f), height)
            )

            onDrawWithContent {
                // 绘制透镜次表面微光
                drawRect(brush = innerRefractionBrush)

                // 绘制动态液态流光
                if (enableFlowSheen) {
                    drawRect(brush = sheenBrush)
                }

                // 绘制主体内容（文字、图标）
                drawContent()

                // 绘制色散边缘光辉（模拟三棱镜分色）
                drawOutline(
                    outline = outline,
                    brush = chromaticFringeBrush,
                    style = Stroke(width = 1.8.dp.toPx())
                )

                // 绘制内层透镜倒角折射光芒
                drawOutline(
                    outline = outline,
                    brush = innerBevelBrush,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }
        // 外层菲涅尔物理边界描边
        .border(
            width = borderWidth,
            brush = if (isDark) {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.70f),
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.30f)
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
    blurRadius = blurRadius,
    enableFlowSheen = false
)

/** 无 Haze 上下文时的静态液态玻璃卡 */
@Composable
fun Modifier.staticLiquidGlass(
    tint: Color? = null,
    shape: Shape = RoundedCornerShape(26.dp),
    enableFlowSheen: Boolean = true
): Modifier = this.liquidGlass(
    state = null,
    tint = tint,
    shape = shape,
    enableFlowSheen = enableFlowSheen
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
                    shader.setFloatUniform("iDispersion", 3.5f)
                    renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "compositedImage").asComposeRenderEffect()
                } catch (_: Throwable) {}
            }
        }.getOrDefault(Modifier)
    }
}