package com.novelai.assistant.ui.components

import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hihonor.uikit.transparency.api.HnGlassEffect
import com.hihonor.uikit.transparency.api.HnMaterialType
import com.hihonor.uikit.transparency.api.HnSceneType
import com.hihonor.uikit.transparency.api.HnVisualEffectLayout
import com.hihonor.uikit.transparency.api.HnWidgetSize
import com.hihonor.uikit.transparency.configuration.TransparencyFeatureConfig
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

enum class GlassWidgetSize { SMALL, BIG }
enum class GlassSceneType { GENERAL, IMMERSIVE }

private data class HonorGlassSpec(
    val radiusPx: Int,
    val widgetSize: GlassWidgetSize,
    val sceneType: GlassSceneType,
    val interactive: Boolean,
    val preferLiquidGlass: Boolean
)

object HonorGlassDiagnostics {
    @Volatile var transparencySupported: Boolean? = null
    @Volatile var liquidGlassEnabled: Boolean? = null
    @Volatile var materialLevel: Int? = null
    @Volatile var activeMode: String = "未初始化"
    @Volatile var lastError: String? = null

    fun summary(): String {
        val sup = transparencySupported?.toString() ?: "?"
        val liq = liquidGlassEnabled?.toString() ?: "?"
        val lvl = materialLevel?.toString() ?: "?"
        val err = lastError?.let { " | err=$it" } ?: ""
        return "$activeMode (support=$sup, liquid=$liq, level=$lvl$err)"
    }
}

@Composable
fun rememberHonorTransparencySupported(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val supported = TransparencyFeatureConfig.isSupportTransparencyMaterial(context)
            HonorGlassDiagnostics.transparencySupported = supported
            HonorGlassDiagnostics.materialLevel =
                runCatching { TransparencyFeatureConfig.getTransparentMaterialLevel(context) }.getOrNull()
            supported
        } catch (t: Throwable) {
            HonorGlassDiagnostics.transparencySupported = false
            HonorGlassDiagnostics.lastError = t.javaClass.simpleName + ":" + (t.message ?: "")
            false
        }
    }
}

@Composable
fun rememberHonorLiquidGlassEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val enabled = TransparencyFeatureConfig.isLiquidGlassEnabled(context)
            HonorGlassDiagnostics.liquidGlassEnabled = enabled
            enabled
        } catch (t: Throwable) {
            HonorGlassDiagnostics.liquidGlassEnabled = false
            HonorGlassDiagnostics.lastError = t.javaClass.simpleName + ":" + (t.message ?: "")
            false
        }
    }
}

/**
 * 统一液态玻璃容器：
 * - 底层始终在 Compose 层接入 Haze 实时高斯模糊（当 state != null 时），确保同一 ComposeView
 *   内的列表、书封、正文在任何机型上都能被实时虚化；
 * - 在支持通透材质的荣耀 MagicOS 设备上，叠加官方 HnVisualEffectLayout + HnGlassEffect
 *   提供硬件级液态玻璃折光与按压流光反馈；若不支持或发生异常则自动回退到纯 Compose 光学玻璃。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiquidGlassBox(
    modifier: Modifier = Modifier,
    state: HazeState? = null,
    cornerRadius: Dp = 26.dp,
    widgetSize: GlassWidgetSize = GlassWidgetSize.SMALL,
    sceneType: GlassSceneType = GlassSceneType.IMMERSIVE,
    interactive: Boolean = true,
    tint: Color? = null,
    shadowElevation: Dp = 12.dp,
    borderWidth: Dp = 0.8.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val isHonorSupported = rememberHonorTransparencySupported()
    val isLiquidEnabled = rememberHonorLiquidGlassEnabled()
    val shape = RoundedCornerShape(cornerRadius)
    var honorRenderFailed by remember { mutableStateOf(false) }

    if (isHonorSupported && !honorRenderFailed) {
        HonorGlassDiagnostics.activeMode = if (isLiquidEnabled) "荣耀液态玻璃 + Haze" else "荣耀磨砂 + Haze"
        val density = LocalDensity.current
        val radiusPx = with(density) { cornerRadius.roundToPx() }
        val spec = HonorGlassSpec(
            radiusPx = radiusPx,
            widgetSize = widgetSize,
            sceneType = sceneType,
            interactive = interactive,
            preferLiquidGlass = isLiquidEnabled
        )
        val layoutHolder = remember { arrayOfNulls<HnVisualEffectLayout>(1) }

        val touchForwardModifier = if (interactive) {
            Modifier.pointerInteropFilter { motionEvent ->
                layoutHolder[0]?.let { layout ->
                    runCatching {
                        val copy = MotionEvent.obtain(motionEvent)
                        layout.dispatchTouchEvent(copy)
                        copy.recycle()
                    }
                }
                false
            }
        } else {
            Modifier
        }

        val baseModifier = if (state != null) {
            modifier
                .clip(shape)
                .hazeEffect(
                    state = state,
                    style = HazeStyle(
                        backgroundColor = Color.Transparent,
                        tint = HazeTint(Color.Transparent),
                        blurRadius = 24.dp,
                        noiseFactor = 0.02f
                    )
                )
        } else {
            modifier
        }

        Box(
            modifier = baseModifier.then(touchForwardModifier),
            contentAlignment = contentAlignment
        ) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { ctx ->
                    HnVisualEffectLayout(ctx).apply {
                        isClickable = false
                        isFocusable = false
                        layoutHolder[0] = this
                        tag = spec
                        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {
                                val currentSpec = (v.tag as? HonorGlassSpec) ?: return
                                if (!applyHonorGlassSpecSafe(this@apply, currentSpec)) {
                                    honorRenderFailed = true
                                }
                            }

                            override fun onViewDetachedFromWindow(v: View) {
                                runCatching { setVisualEffect(null) }
                            }
                        })
                    }
                },
                update = { view ->
                    layoutHolder[0] = view
                    if (view.tag != spec) {
                        view.tag = spec
                        if (view.isAttachedToWindow) {
                            if (!applyHonorGlassSpecSafe(view, spec)) {
                                honorRenderFailed = true
                            }
                        }
                    }
                }
            )
            content()
        }
    } else {
        HonorGlassDiagnostics.activeMode = "Compose Haze 液态玻璃"
        Box(
            modifier = modifier
                .shadow(
                    elevation = shadowElevation,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.14f),
                    spotColor = Color.Black.copy(alpha = 0.22f)
                )
                .liquidGlass(
                    state = state,
                    tint = tint,
                    shape = shape,
                    borderWidth = borderWidth
                ),
            contentAlignment = contentAlignment,
            content = content
        )
    }
}

private fun applyHonorGlassSpecSafe(view: HnVisualEffectLayout, spec: HonorGlassSpec): Boolean {
    return try {
        val scene = if (spec.sceneType == GlassSceneType.IMMERSIVE) {
            HnSceneType.IMMERSIVE
        } else {
            HnSceneType.GENERAL
        }
        val effect = if (spec.preferLiquidGlass) {
            HnGlassEffect(HnMaterialType.LIQUID_GLASS, scene)
        } else {
            HnGlassEffect().apply {
                setSceneType(scene)
            }
        }.apply {
            setFollowSystemSettings(true)
            setAdaptToBackground(true)
            setWidgetSize(
                if (spec.widgetSize == GlassWidgetSize.BIG) HnWidgetSize.BIG else HnWidgetSize.SMALL
            )
            setRadius(spec.radiusPx)
            setInteractive(spec.interactive)
        }
        view.setVisualEffect(null)
        view.setRadius(spec.radiusPx)
        view.setVisualEffect(effect)
        view.isClickable = false
        view.isFocusable = false
        true
    } catch (t: Throwable) {
        HonorGlassDiagnostics.lastError = t.javaClass.simpleName + ":" + (t.message ?: "")
        Log.e("HonorLiquidGlass", "Failed to apply Honor visual effect, falling back to Compose", t)
        runCatching { view.setVisualEffect(null) }
        false
    }
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
 * 液态玻璃核心修饰符 (Liquid Glass — Compose 实时硬件模糊 + 光学折射实现)
 *
 * 1. 【真实实时背景模糊】接入 Haze 硬件级实时高斯模糊（blurRadius 默认 26.dp），柔和虚化底层图文；
 * 2. 【水润通透玻璃体】深色 48% / 浅色 52% 低饱和半透明底色，既保留通透色彩又避免文字粘连；
 * 3. 【柔和光学高光切面】去除生硬白色全包线框，改用顶部微弧高光 + 极淡轮廓，接近原生液态玻璃质感。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.liquidGlass(
    state: HazeState? = null,
    tint: Color? = null,
    shape: Shape = RoundedCornerShape(26.dp),
    blurRadius: Dp = 26.dp,
    enableFlowSheen: Boolean = false,
    borderWidth: Dp = 0.8.dp
): Modifier {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val resolvedTint = tint?.let {
        if (it.alpha > 0.65f) it.copy(alpha = 0.56f) else it
    } ?: if (isDark) {
        Color(0xFF1A1C24).copy(alpha = 0.48f)
    } else {
        Color(0xFFF6F8FC).copy(alpha = 0.54f)
    }

    val blurModifier = if (state != null) {
        Modifier.hazeEffect(
            state = state,
            style = HazeStyle(
                backgroundColor = if (isDark) Color(0xFF12131A) else Color(0xFFF5F7FB),
                tint = HazeTint(resolvedTint),
                blurRadius = blurRadius,
                noiseFactor = 0.02f
            )
        )
    } else {
        Modifier.background(resolvedTint, shape)
    }

    return this
        .clip(shape)
        .then(blurModifier)
        .drawWithCache {
            val width = size.width
            val height = size.height

            // 1. 液态玻璃表面微弧反光（柔和斜向镜面光）
            val specularBrush = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(
                        Color.White.copy(alpha = 0.09f),
                        Color.Transparent,
                        Color(0xFFA8D8FF).copy(alpha = 0.04f)
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.42f),
                        Color.White.copy(alpha = 0.06f),
                        Color(0xFFD8EEFF).copy(alpha = 0.16f)
                    )
                },
                start = Offset(0f, 0f),
                end = Offset(width * 0.85f, height)
            )

            // 2. 柔和内倒角高光（不再使用刺眼的粗白线框）
            val outline = shape.createOutline(size, layoutDirection, this)
            val innerBevelBrush = if (isDark) {
                Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.26f),
                    0.22f to Color.White.copy(alpha = 0.06f),
                    0.78f to Color.Transparent,
                    1.0f to Color.White.copy(alpha = 0.10f)
                )
            } else {
                Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.82f),
                    0.22f to Color.White.copy(alpha = 0.22f),
                    0.80f to Color.Transparent,
                    1.0f to Color.White.copy(alpha = 0.40f)
                )
            }

            onDrawWithContent {
                drawRect(brush = specularBrush)
                drawContent()
                drawOutline(
                    outline = outline,
                    brush = innerBevelBrush,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
        // 极细柔和光学外轮廓
        .border(
            width = borderWidth,
            brush = if (isDark) {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.24f),
                        Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.14f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.25f),
                        Color.Black.copy(alpha = 0.08f)
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
