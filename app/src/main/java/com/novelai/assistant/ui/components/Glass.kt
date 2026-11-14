package com.novelai.assistant.ui.components

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hihonor.uikit.hnmagicsystem.compatibility.TransparencyCompat
import com.hihonor.uikit.transparency.api.HnBlurGradientBlurDirection
import com.hihonor.uikit.transparency.api.HnGlassEffect
import com.hihonor.uikit.transparency.api.HnGradientBlurEffect
import com.hihonor.uikit.transparency.api.HnMaterialType
import com.hihonor.uikit.transparency.api.HnSceneType
import com.hihonor.uikit.transparency.api.HnVisualEffectLayout
import com.hihonor.uikit.transparency.api.HnWidgetSize
import com.hihonor.uikit.transparency.configuration.MachineConfig
import com.hihonor.uikit.transparency.configuration.MachineType
import com.hihonor.uikit.transparency.configuration.TransparencyFeatureConfig
import com.novelai.assistant.ui.theme.NovelColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.math.roundToInt

enum class GlassWidgetSize { SMALL, BIG }
enum class GlassSceneType { GENERAL, IMMERSIVE }
enum class GlassGradientDirection { TOP_TO_BOTTOM, BOTTOM_TO_TOP }

/**
 * 根级实时模糊状态 CompositionLocal：
 * 由 NovelApp（或 ReaderScreen 独立子作用域）提供，供所有浮层/侧栏/胶囊自动采样。
 */
val LocalGlassHazeState = staticCompositionLocalOf<HazeState?> { null }

private data class HonorGlassSpec(
    val radiusPx: Int,
    val widgetSize: GlassWidgetSize,
    val sceneType: GlassSceneType,
    val interactive: Boolean,
    val preferLiquidGlass: Boolean,
    val followSystemMaterial: Boolean,
    val skipRenderLayer: Boolean,
    val showShadow: Boolean,
    val transparencyLevel: Float
)

private data class HonorCapabilitiesSnapshot(
    val nativeEnabled: Boolean,
    val compatViewSupported: Boolean,
    val materialSupported: Boolean,
    val liquidGlassEnabled: Boolean,
    val machineType: MachineType?,
    val materialLevel: Int?,
    val agslSupported: Boolean
)

object HonorGlassDiagnostics {
    @Volatile var transparencySupported: Boolean? = null
    @Volatile var compatViewSupported: Boolean? = null
    @Volatile var liquidGlassEnabled: Boolean? = null
    @Volatile var materialLevel: Int? = null
    @Volatile var machineType: String? = null
    @Volatile var agslSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    @Volatile var activeMode: String = "未初始化"
    @Volatile var lastError: String? = null

    fun summary(): String {
        val sup = transparencySupported?.toString() ?: "?"
        val compat = compatViewSupported?.toString() ?: "?"
        val liq = liquidGlassEnabled?.toString() ?: "?"
        val tier = machineType ?: "?"
        val lvl = materialLevel?.toString() ?: "?"
        val agsl = if (agslSupported) "AGSL" else "Legacy"
        val err = lastError?.let { " | err=$it" } ?: ""
        return "$activeMode [$agsl] (sup=$sup, view=$compat, liquid=$liq, tier=$tier/$lvl$err)"
    }
}

private fun probeHonorCapabilities(context: Context): HonorCapabilitiesSnapshot {
    val agsl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    HonorGlassDiagnostics.agslSupported = agsl
    return try {
        val compatView = runCatching { TransparencyCompat.isSupportTransparencyView() }.getOrDefault(false)
        val materialSup = runCatching {
            TransparencyFeatureConfig.isSupportTransparencyMaterial(context)
        }.getOrDefault(false)
        val liquid = runCatching {
            TransparencyFeatureConfig.isLiquidGlassEnabled(context)
        }.getOrDefault(false)
        val level = runCatching {
            TransparencyFeatureConfig.getTransparentMaterialLevel(context)
        }.getOrNull()
        val machine = runCatching {
            MachineConfig.getInstance(context).getMachineType()
        }.getOrNull()

        HonorGlassDiagnostics.compatViewSupported = compatView
        HonorGlassDiagnostics.transparencySupported = materialSup
        HonorGlassDiagnostics.liquidGlassEnabled = liquid
        HonorGlassDiagnostics.materialLevel = level
        HonorGlassDiagnostics.machineType = machine?.name

        // 仅在 Honor 视图引擎存在且设备档位为 HIGH / MID 时启用原生 HnVisualEffectLayout，
        // LOW / COMPAT 档位直接交由 Compose Haze + AGSL 渲染，避免降级成不透明纯色。
        val tierSupported = machine == null || (machine != MachineType.LOW && machine != MachineType.COMPAT)
        val nativeEnabled = compatView && materialSup && tierSupported

        HonorCapabilitiesSnapshot(
            nativeEnabled = nativeEnabled,
            compatViewSupported = compatView,
            materialSupported = materialSup,
            liquidGlassEnabled = liquid,
            machineType = machine,
            materialLevel = level,
            agslSupported = agsl
        )
    } catch (t: Throwable) {
        HonorGlassDiagnostics.transparencySupported = false
        HonorGlassDiagnostics.compatViewSupported = false
        HonorGlassDiagnostics.lastError = t.javaClass.simpleName + ":" + (t.message ?: "")
        HonorCapabilitiesSnapshot(
            nativeEnabled = false,
            compatViewSupported = false,
            materialSupported = false,
            liquidGlassEnabled = false,
            machineType = null,
            materialLevel = null,
            agslSupported = agsl
        )
    }
}

@Composable
fun rememberHonorTransparencySupported(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        probeHonorCapabilities(context).nativeEnabled
    }
}

@Composable
fun rememberHonorLiquidGlassEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        probeHonorCapabilities(context).liquidGlassEnabled
    }
}

/**
 * 统一液态玻璃容器：
 * - 在支持通透材质的高/中端荣耀 MagicOS 设备上，使用官方 `HnVisualEffectLayout` + `HnGlassEffect`
 *   硬件级液态玻璃折光与物理交互动效（不叠加 Haze 24dp 模糊以免抹糊 1.5dp 折光透镜）；
 * - 在非荣耀 / 低端降级 / 异常回退场景下，接入 Compose `Haze` 实时高斯模糊 + 光学切面高光；
 * - 默认采样 `LocalGlassHazeState.current`，无需每处手动透传 `HazeState`。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiquidGlassBox(
    modifier: Modifier = Modifier,
    state: HazeState? = LocalGlassHazeState.current,
    cornerRadius: Dp = 26.dp,
    widgetSize: GlassWidgetSize = GlassWidgetSize.SMALL,
    sceneType: GlassSceneType = GlassSceneType.IMMERSIVE,
    interactive: Boolean = true,
    tint: Color? = null,
    shadowElevation: Dp = 12.dp,
    borderWidth: Dp = 0.8.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    skipRenderLayer: Boolean = false,
    followSystemMaterial: Boolean = false,
    transparencyLevel: Float = -1f,
    showShadow: Boolean = true,
    shape: Shape? = null,
    blurRadius: Dp = 26.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isHonorSupported = rememberHonorTransparencySupported()
    val isLiquidEnabled = rememberHonorLiquidGlassEnabled()
    val resolvedShape = shape ?: RoundedCornerShape(cornerRadius)
    var honorRenderFailed by remember { mutableStateOf(false) }

    if (isHonorSupported && !honorRenderFailed) {
        HonorGlassDiagnostics.activeMode =
            if (isLiquidEnabled) "荣耀原生液态玻璃 (MagicOS 11)" else "荣耀原生磨砂玻璃"
        val density = LocalDensity.current
        val radiusPx = with(density) { cornerRadius.roundToPx() }
        val spec = HonorGlassSpec(
            radiusPx = radiusPx,
            widgetSize = widgetSize,
            sceneType = sceneType,
            interactive = interactive,
            preferLiquidGlass = isLiquidEnabled,
            followSystemMaterial = followSystemMaterial,
            skipRenderLayer = skipRenderLayer,
            showShadow = showShadow,
            transparencyLevel = transparencyLevel
        )
        val layoutHolder = remember { arrayOfNulls<HnVisualEffectLayout>(1) }

        val touchForwardModifier = if (interactive) {
            Modifier.pointerInteropFilter { motionEvent ->
                layoutHolder[0]?.let { layout ->
                    runCatching {
                        val copy = MotionEvent.obtain(motionEvent)
                        val effect = layout.visualEffect
                        if (effect != null) {
                            effect.onTouchEvent(true, copy)
                        } else {
                            layout.dispatchTouchEvent(copy)
                        }
                        copy.recycle()
                    }
                }
                false
            }
        } else {
            Modifier
        }

        Box(
            modifier = modifier
                .clip(resolvedShape)
                .then(touchForwardModifier),
            contentAlignment = contentAlignment
        ) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { ctx ->
                    HnVisualEffectLayout(ctx).apply {
                        layoutHolder[0] = this
                        tag = spec
                        addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                            val newW = right - left
                            val newH = bottom - top
                            val oldW = oldRight - oldLeft
                            val oldH = oldBottom - oldTop
                            if (newW > 0 && newH > 0 && (oldW <= 0 || oldH <= 0 || newW != oldW || newH != oldH)) {
                                val layout = v as? HnVisualEffectLayout
                                val currentSpec = layout?.tag as? HonorGlassSpec
                                if (layout != null && currentSpec != null) {
                                    if (layout.visualEffect == null) {
                                        if (!applyHonorGlassSpecSafe(layout, currentSpec)) {
                                            honorRenderFailed = true
                                        }
                                    } else {
                                        runCatching { layout.visualEffect?.onChanged() }
                                    }
                                }
                            }
                        }
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
        HonorGlassDiagnostics.activeMode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Compose Haze + AGSL 液态玻璃"
            else "Compose Haze 光学玻璃"
        val shadowModifier = if (showShadow && shadowElevation > 0.dp) {
            Modifier.shadow(
                elevation = shadowElevation,
                shape = resolvedShape,
                ambientColor = Color.Black.copy(alpha = 0.14f),
                spotColor = Color.Black.copy(alpha = 0.22f)
            )
        } else {
            Modifier
        }
        Box(
            modifier = modifier
                .then(shadowModifier)
                .liquidGlass(
                    state = state,
                    tint = tint,
                    shape = resolvedShape,
                    blurRadius = blurRadius,
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
        val effect = if (spec.followSystemMaterial) {
            HnGlassEffect().apply { setSceneType(scene) }
        } else {
            val mat = if (spec.preferLiquidGlass) HnMaterialType.LIQUID_GLASS else HnMaterialType.FROSTED_GLASS
            HnGlassEffect(mat, scene)
        }.apply {
            setFollowSystemSettings(true)
            setAdaptToBackground(true)
            setWidgetSize(
                if (spec.widgetSize == GlassWidgetSize.BIG) HnWidgetSize.BIG else HnWidgetSize.SMALL
            )
            setRadius(spec.radiusPx)
            setSkipRenderLayer(spec.skipRenderLayer)
            if (spec.transparencyLevel in 0f..1f) {
                setTransparencyLevel(spec.transparencyLevel)
            }
            setInteractive(spec.interactive)
        }

        view.setVisualEffect(null)
        view.setRadius(spec.radiusPx)
        view.setCornerRadiusWithClipChildren(spec.radiusPx.toFloat(), true)
        view.setVisualEffect(effect)

        // 始终禁止 HnVisualEffectLayout 拦截/消费点击事件，防止 AndroidViewHolder 吞掉外层 Compose .clickable
        // （如添加提供商弹窗中的关闭按钮 X）；交互流光与形变由 touchForwardModifier 调用 effect.onTouchEvent(true, copy) 驱动。
        view.isClickable = false
        view.isFocusable = false
        view.setOnTouchListener { _, _ -> false }
        true
    } catch (t: Throwable) {
        HonorGlassDiagnostics.lastError = t.javaClass.simpleName + ":" + (t.message ?: "")
        Log.e("HonorLiquidGlass", "Failed to apply Honor visual effect, falling back to Compose", t)
        runCatching { view.setVisualEffect(null) }
        false
    }
}

/**
 * 贴边渐变模糊栏（使用荣耀原生 `HnGradientBlurEffect`，非荣耀机型回退为渐变 Haze + Scrim）。
 * 适用于顶部阅读条、底部贴边工具栏等边缘过渡区域。
 */
@Composable
fun GlassGradientBar(
    modifier: Modifier = Modifier,
    direction: GlassGradientDirection = GlassGradientDirection.TOP_TO_BOTTOM,
    state: HazeState? = LocalGlassHazeState.current,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val isHonorSupported = rememberHonorTransparencySupported()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var honorFailed by remember { mutableStateOf(false) }

    if (isHonorSupported && !honorFailed) {
        val hnDir = if (direction == GlassGradientDirection.TOP_TO_BOTTOM) {
            HnBlurGradientBlurDirection.TOP_TO_BOTTOM
        } else {
            HnBlurGradientBlurDirection.BOTTOM_TO_TOP
        }
        Box(modifier = modifier, contentAlignment = contentAlignment) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { ctx ->
                    HnVisualEffectLayout(ctx).apply {
                        isClickable = false
                        isFocusable = false
                        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {
                                runCatching {
                                    setVisualEffect(null)
                                    setVisualEffect(HnGradientBlurEffect(hnDir).apply {
                                        setFollowSystemSettings(true)
                                        setAdaptToBackground(true)
                                    })
                                }.onFailure { honorFailed = true }
                            }

                            override fun onViewDetachedFromWindow(v: View) {
                                runCatching { setVisualEffect(null) }
                            }
                        })
                    }
                },
                update = { view ->
                    if (view.isAttachedToWindow) {
                        val current = view.visualEffect as? HnGradientBlurEffect
                        if (current?.gradientBlurDirection != hnDir) {
                            runCatching {
                                view.setVisualEffect(null)
                                view.setVisualEffect(HnGradientBlurEffect(hnDir).apply {
                                    setFollowSystemSettings(true)
                                    setAdaptToBackground(true)
                                })
                            }.onFailure { honorFailed = true }
                        }
                    }
                }
            )
            content()
        }
    } else {
        val baseColor = if (isDark) Color(0xFF0E1017) else Color(0xFFF6F7FB)
        val gradientColors = if (direction == GlassGradientDirection.TOP_TO_BOTTOM) {
            listOf(baseColor.copy(alpha = 0.86f), baseColor.copy(alpha = 0.45f), Color.Transparent)
        } else {
            listOf(Color.Transparent, baseColor.copy(alpha = 0.45f), baseColor.copy(alpha = 0.88f))
        }
        val hazeMod = if (state != null) {
            Modifier.hazeEffect(
                state = state,
                style = HazeStyle(
                    backgroundColor = baseColor,
                    tint = HazeTint(baseColor.copy(alpha = 0.36f)),
                    blurRadius = 20.dp,
                    noiseFactor = 0.02f
                )
            )
        } else {
            Modifier
        }
        Box(
            modifier = modifier
                .then(hazeMod)
                .background(Brush.verticalGradient(gradientColors)),
            contentAlignment = contentAlignment,
            content = content
        )
    }
}

@Composable
fun rememberGlassState(): HazeState = remember { HazeState() }

fun Modifier.glassSource(state: HazeState): Modifier = this.hazeSource(state)

/** 柔和环境光晕层（Ambient Backdrop） */
@Composable
fun AmbientBackdrop(
    accent: Color = MaterialTheme.colorScheme.primary,
    secondary: Color = MaterialTheme.colorScheme.tertiary,
    modifier: Modifier = Modifier,
    intensity: Float = 0.72f
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val a1 = if (isDark) 0.14f else 0.12f
    val a2 = if (isDark) 0.10f else 0.09f
    val a3 = if (isDark) 0.14f else 0.12f
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
        bloom(Offset(w * 0.18f, h * 0.08f), w * 0.80f, accent, a1)
        bloom(Offset(w * 0.88f, h * 0.38f), w * 0.68f, secondary, a2)
        bloom(Offset(w * 0.45f, h * 0.92f), w * 0.85f, accent, a3)
    }
}

/**
 * 液态玻璃核心修饰符 (Compose Haze 实时模糊 + 235° 柔和光学高光切面)。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.liquidGlass(
    state: HazeState? = LocalGlassHazeState.current,
    tint: Color? = null,
    shape: Shape = RoundedCornerShape(26.dp),
    blurRadius: Dp = 26.dp,
    enableFlowSheen: Boolean = false,
    borderWidth: Dp = 0.8.dp
): Modifier {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val resolvedTint = tint?.let {
        if (it.alpha > 0.68f) it.copy(alpha = 0.58f) else it
    } ?: if (isDark) {
        Color(0xFF171922).copy(alpha = 0.56f)
    } else {
        Color(0xFFF9FAFD).copy(alpha = 0.64f)
    }

    val blurModifier = if (state != null) {
        Modifier.hazeEffect(
            state = state,
            style = HazeStyle(
                backgroundColor = if (isDark) Color(0xFF0E1016) else Color(0xFFF4F6FA),
                tint = HazeTint(resolvedTint),
                blurRadius = blurRadius,
                noiseFactor = 0.015f
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

            val specularBrush = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(
                        Color.White.copy(alpha = 0.07f),
                        Color.Transparent,
                        Color(0xFF9AC8FF).copy(alpha = 0.03f)
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.38f),
                        Color.White.copy(alpha = 0.05f),
                        Color(0xFFD8EEFF).copy(alpha = 0.14f)
                    )
                },
                start = Offset(0f, 0f),
                end = Offset(width * 0.85f, height)
            )

            val outline = shape.createOutline(size, layoutDirection, this)
            val innerBevelBrush = if (isDark) {
                Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.18f),
                    0.24f to Color.White.copy(alpha = 0.04f),
                    0.78f to Color.Transparent,
                    1.0f to Color.White.copy(alpha = 0.07f)
                )
            } else {
                Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.78f),
                    0.22f to Color.White.copy(alpha = 0.18f),
                    0.80f to Color.Transparent,
                    1.0f to Color.White.copy(alpha = 0.34f)
                )
            }

            onDrawWithContent {
                drawRect(brush = specularBrush)
                drawContent()
                drawOutline(
                    outline = outline,
                    brush = innerBevelBrush,
                    style = Stroke(width = 0.9.dp.toPx())
                )
            }
        }
        .border(
            width = borderWidth,
            brush = if (isDark) {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.05f),
                        Color.White.copy(alpha = 0.10f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.82f),
                        Color.White.copy(alpha = 0.22f),
                        Color.Black.copy(alpha = 0.07f)
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

// =============================================================================
// AGSL 液态玻璃折射胶囊透镜引擎 (API 33+ RuntimeShader + <33 Canvas 回退)
// =============================================================================

private const val LIQUID_LENS_AGSL = """
uniform shader composable;
uniform float2 uResolution;
uniform float2 uLensCenter;
uniform float2 uLensHalfSize;
uniform float uCornerRadius;
uniform float uIsDark;
uniform float uPressed;
uniform float uRefraction;
uniform float uDispersion;
uniform float4 uTintColor;

float sdRoundedBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float2 sdfNormal(float2 p, float2 b, float r) {
    float eps = 1.0;
    float dx = sdRoundedBox(p + float2(eps, 0.0), b, r) - sdRoundedBox(p - float2(eps, 0.0), b, r);
    float dy = sdRoundedBox(p + float2(0.0, eps), b, r) - sdRoundedBox(p - float2(0.0, eps), b, r);
    float2 g = float2(dx, dy);
    float len = length(g);
    return len > 0.0001 ? g / len : float2(0.0, 0.0);
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord - uLensCenter;
    float d = sdRoundedBox(p, uLensHalfSize, uCornerRadius);

    if (d > 0.0) {
        half4 base = composable.eval(fragCoord);
        float shadowAlpha = exp(-d * 0.28) * (0.05 + 0.07 * uPressed);
        half outA = clamp(base.a + shadowAlpha * (1.0 - base.a), 0.0, 1.0);
        return half4(clamp(base.rgb, 0.0, outA), outA);
    }

    float inward = -d;
    float rimWidth = max(min(uLensHalfSize.x, uLensHalfSize.y) * 0.72, 1.0);
    float edgeFactor = 1.0 - smoothstep(0.0, rimWidth, inward);

    float2 n = sdfNormal(p, uLensHalfSize, uCornerRadius);
    float refractPx = (9.5 + 8.5 * uPressed) * uRefraction * pow(edgeFactor, 1.25);
    float2 zoomOffset = p * (0.068 + 0.052 * uPressed);
    float2 baseUv = fragCoord - zoomOffset - n * refractPx;
    float dispPx = (2.6 + 2.4 * uPressed) * uDispersion * edgeFactor;

    float2 maxCoord = max(uResolution - float2(0.5), float2(1.0));
    float2 uvR = clamp(baseUv - n * dispPx, float2(0.5), maxCoord);
    float2 uvG = clamp(baseUv, float2(0.5), maxCoord);
    float2 uvB = clamp(baseUv + n * dispPx, float2(0.5), maxCoord);

    half4 cR = composable.eval(uvR);
    half4 cG = composable.eval(uvG);
    half4 cB = composable.eval(uvB);
    half4 refracted = half4(cR.r, cG.g, cB.b, cG.a);

    float verticalGrad = clamp(-p.y / max(uLensHalfSize.y, 1.0) * 0.5 + 0.5, 0.0, 1.0);
    // 高通透水滴透镜主体：避免灰雾遮挡底层色彩，保留微弱冷光与按压弹性明度
    float bodyAlpha = uIsDark > 0.5 ? (0.06 + 0.05 * uPressed) : (0.15 + 0.08 * uPressed);
    half3 darkTop = half3(0.46, 0.52, 0.62);
    half3 darkBot = half3(0.22, 0.26, 0.34);
    half3 bodyColor = uIsDark > 0.5 ? mix(darkBot, darkTop, verticalGrad) : half3(0.99, 0.995, 1.0);
    float centerGlow = (1.0 - edgeFactor * 0.75) * (0.10 + 0.10 * uPressed);
    bodyColor = mix(bodyColor, half3(uTintColor.rgb), centerGlow);

    // Src-over: refracted items OVER crystal-clear liquid glass lens body (premultiplied)
    half3 overRgb = refracted.rgb + bodyColor * bodyAlpha * (1.0 - refracted.a);
    half overA = refracted.a + bodyAlpha * (1.0 - refracted.a);

    // 235-degree directional Fresnel specular rim + upper convex meniscus highlight
    float2 lightDir = normalize(float2(-0.57, -0.82));
    float dirSpec = clamp(dot(n, lightDir) * 0.5 + 0.5, 0.0, 1.0);
    float rimMask = smoothstep(3.0, 0.0, inward);
    float topMeniscus = smoothstep(-uLensHalfSize.y, -uLensHalfSize.y * 0.25, p.y);
    float meniscusArc = (1.0 - topMeniscus) * (1.0 - edgeFactor * 0.65) * 0.16;
    float innerSheen = pow(edgeFactor, 1.95) * (0.24 + 0.28 * dirSpec);
    float specAlpha = clamp(
        rimMask * (0.50 + 0.46 * dirSpec + 0.18 * uPressed) + innerSheen * 0.38 + meniscusArc,
        0.0,
        0.96
    );
    half3 specColor = uIsDark > 0.5 ? half3(0.88, 0.94, 1.0) : half3(1.0, 1.0, 1.0);
    float specWeight = uIsDark > 0.5 ? 0.78 : 0.92;

    half3 finalRgb = overRgb + specColor * (specAlpha * specWeight) * (1.0 - overRgb);
    half finalA = clamp(overA + specAlpha * specWeight * (1.0 - overA), 0.0, 1.0);
    return half4(clamp(finalRgb, 0.0, finalA), finalA);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun rememberLiquidLensShader(): RuntimeShader =
    RuntimeShader(LIQUID_LENS_AGSL)

/**
 * 可拖拽/弹性形变液态玻璃透镜轨道（支持水平底栏、垂直 NavigationRail 与分段控制器）。
 * - API 33+ 使用 AGSL `RuntimeShader` 对轨道内图标/文字与底层光影执行实时胶囊边缘折射 + 色散 + 235° 菲涅尔高光；
 * - API <33 自动降级为 Compose 渐变高光胶囊 + 物理弹性缩放；
 * - 内置水平/垂直拖拽手势、越界磁吸与逐格微触觉反馈。
 */
@Composable
fun LiquidGlassLensTrack(
    itemCount: Int,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    orientation: Orientation = Orientation.Horizontal,
    slotCrossSize: Dp = 50.dp,
    pillCornerRadius: Dp = 25.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable (activeVisualIndex: Int, isDragging: Boolean) -> Unit
) {
    val count = itemCount.coerceAtLeast(1)
    val safeSelected = selectedIndex.coerceIn(0, count - 1)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    var isDragging by remember { mutableStateOf(false) }
    var dragIndexFloat by remember { mutableFloatStateOf(safeSelected.toFloat()) }
    var dragDeltaVelocity by remember { mutableFloatStateOf(0f) }
    var lastHapticIndex by remember { mutableIntStateOf(safeSelected) }

    val animatedIndex by animateFloatAsState(
        targetValue = if (isDragging) dragIndexFloat else safeSelected.toFloat(),
        animationSpec = if (isDragging) {
            spring(dampingRatio = 0.86f, stiffness = 920f)
        } else {
            spring(dampingRatio = 0.68f, stiffness = 420f)
        },
        label = "lensIndex"
    )
    val pressedProgress by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 460f),
        label = "lensPressed"
    )
    val velocityStretch by animateFloatAsState(
        targetValue = if (isDragging) (abs(dragDeltaVelocity) / 28f).coerceIn(0f, 0.14f) else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "lensStretch"
    )

    val activeVisualIndex = if (isDragging) {
        dragIndexFloat.roundToInt().coerceIn(0, count - 1)
    } else {
        safeSelected
    }

    val useAgsl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val lensShader = if (useAgsl) remember { rememberLiquidLensShader() } else null

    BoxWithConstraints(modifier = modifier) {
        val totalWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val totalHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val slotLengthPx = if (orientation == Orientation.Horizontal) {
            (totalWidthPx / count).coerceAtLeast(1f)
        } else {
            (totalHeightPx / count).coerceAtLeast(1f)
        }
        val slotCrossPx = with(density) { slotCrossSize.toPx() }.coerceAtLeast(1f)

        val dragGestureModifier = if (orientation == Orientation.Horizontal) {
            Modifier.pointerInput(count, slotLengthPx, safeSelected) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragDeltaVelocity = 0f
                        val idx = (offset.x / slotLengthPx).coerceIn(0f, (count - 1).toFloat())
                        dragIndexFloat = idx
                        lastHapticIndex = idx.roundToInt().coerceIn(0, count - 1)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragDeltaVelocity = dragAmount
                        val next = (dragIndexFloat + dragAmount / slotLengthPx)
                            .coerceIn(0f, (count - 1).toFloat())
                        dragIndexFloat = next
                        val hovered = next.roundToInt().coerceIn(0, count - 1)
                        if (hovered != lastHapticIndex) {
                            lastHapticIndex = hovered
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        val targetIdx = dragIndexFloat.roundToInt().coerceIn(0, count - 1)
                        isDragging = false
                        dragDeltaVelocity = 0f
                        if (targetIdx != safeSelected) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectIndex(targetIdx)
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        dragDeltaVelocity = 0f
                    }
                )
            }
        } else {
            Modifier.pointerInput(count, slotLengthPx, safeSelected) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragDeltaVelocity = 0f
                        val idx = (offset.y / slotLengthPx).coerceIn(0f, (count - 1).toFloat())
                        dragIndexFloat = idx
                        lastHapticIndex = idx.roundToInt().coerceIn(0, count - 1)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragDeltaVelocity = dragAmount
                        val next = (dragIndexFloat + dragAmount / slotLengthPx)
                            .coerceIn(0f, (count - 1).toFloat())
                        dragIndexFloat = next
                        val hovered = next.roundToInt().coerceIn(0, count - 1)
                        if (hovered != lastHapticIndex) {
                            lastHapticIndex = hovered
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        val targetIdx = dragIndexFloat.roundToInt().coerceIn(0, count - 1)
                        isDragging = false
                        dragDeltaVelocity = 0f
                        if (targetIdx != safeSelected) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectIndex(targetIdx)
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        dragDeltaVelocity = 0f
                    }
                )
            }
        }

        // 计算透镜实时中心与弹性形变半宽高（拖拽时沿拖动方向微拉长、垂直方向微膨胀）
        val alongScale = 1f + 0.05f * pressedProgress + velocityStretch
        val crossScale = 1f + 0.11f * pressedProgress
        val lensHalfW: Float
        val lensHalfH: Float
        val lensCenterX: Float
        val lensCenterY: Float

        if (orientation == Orientation.Horizontal) {
            lensHalfW = (slotLengthPx * 0.5f * alongScale).coerceAtLeast(4f)
            lensHalfH = (totalHeightPx * 0.5f * crossScale).coerceAtLeast(4f)
            lensCenterX = (animatedIndex + 0.5f) * slotLengthPx
            lensCenterY = totalHeightPx * 0.5f
        } else {
            lensHalfW = (totalWidthPx.coerceAtMost(slotCrossPx) * 0.5f * crossScale).coerceAtLeast(4f)
            lensHalfH = (slotLengthPx * 0.5f * alongScale).coerceAtLeast(4f)
            lensCenterX = totalWidthPx * 0.5f
            lensCenterY = (animatedIndex + 0.5f) * slotLengthPx
        }

        val radiusPx = with(density) { pillCornerRadius.toPx() }
            .coerceAtMost(minOf(lensHalfW, lensHalfH))

        val shaderModifier = if (useAgsl && lensShader != null) {
            Modifier.graphicsLayer {
                clip = false
                val argb = accentColor.toArgb()
                val r = ((argb shr 16) and 0xFF) / 255f
                val g = ((argb shr 8) and 0xFF) / 255f
                val b = (argb and 0xFF) / 255f
                lensShader.setFloatUniform("uResolution", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
                lensShader.setFloatUniform("uLensCenter", lensCenterX, lensCenterY)
                lensShader.setFloatUniform("uLensHalfSize", lensHalfW, lensHalfH)
                lensShader.setFloatUniform("uCornerRadius", radiusPx)
                lensShader.setFloatUniform("uIsDark", if (isDark) 1f else 0f)
                lensShader.setFloatUniform("uPressed", pressedProgress)
                lensShader.setFloatUniform("uRefraction", 0.90f + 0.50f * pressedProgress)
                lensShader.setFloatUniform("uDispersion", 0.40f + 0.25f * pressedProgress)
                lensShader.setFloatUniform("uTintColor", r, g, b, 1f)
                renderEffect = RenderEffect
                    .createRuntimeShaderEffect(lensShader, "composable")
                    .asComposeRenderEffect()
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(dragGestureModifier)
                .then(shaderModifier)
        ) {
            if (!useAgsl) {
                // API < 33 物理光学回退胶囊
                val pillWidthDp = with(density) { (lensHalfW * 2f).toDp() }
                val pillHeightDp = with(density) { (lensHalfH * 2f).toDp() }
                val offsetX = (lensCenterX - lensHalfW).roundToInt()
                val offsetY = (lensCenterY - lensHalfH).roundToInt()
                val pillShape = RoundedCornerShape(pillCornerRadius)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX, offsetY) }
                        .width(pillWidthDp)
                        .height(pillHeightDp)
                        .clip(pillShape)
                        .background(
                            brush = if (isDark) {
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.09f + 0.05f * pressedProgress),
                                        Color.White.copy(alpha = 0.03f)
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.26f),
                                        Color.White.copy(alpha = 0.14f)
                                    )
                                )
                            },
                            shape = pillShape
                        )
                        .border(
                            width = if (isDragging) 1.dp else 0.7.dp,
                            brush = Brush.verticalGradient(
                                if (isDark) {
                                    listOf(
                                        Color.White.copy(alpha = 0.42f),
                                        Color(0xFF9AC8FF).copy(alpha = 0.14f),
                                        Color.White.copy(alpha = 0.20f)
                                    )
                                } else {
                                    listOf(
                                        Color.White.copy(alpha = 0.96f),
                                        Color(0xFFAAD2FF).copy(alpha = 0.35f),
                                        Color.White.copy(alpha = 0.65f)
                                    )
                                }
                            ),
                            shape = pillShape
                        )
                )
            }

            content(activeVisualIndex, isDragging)
        }
    }
}

// =============================================================================
// 通用液态玻璃 UI 组件 (SectionCard / SearchCapsule / CircleButton / FilterPill / NavRail)
// =============================================================================

/**
 * 液态玻璃分组卡片：大圆角（22dp）+ 细微镜面描边 + 可选标题与右侧动作按钮。
 * 内部使用 `skipRenderLayer = true` 优化静态/卡片滚动性能。
 */
@Composable
fun GlassSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    footer: String? = null,
    cornerRadius: Dp = 22.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    state: HazeState? = LocalGlassHazeState.current,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null || actionLabel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (actionLabel != null && onActionClick != null) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onActionClick)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val cardShape = RoundedCornerShape(cornerRadius)
        val cardBrush = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    Color(0xFF1F212B).copy(alpha = 0.86f),
                    Color(0xFF15171F).copy(alpha = 0.82f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.94f),
                    Color(0xFFFBFBFE).copy(alpha = 0.88f)
                )
            )
        }
        val borderBrush = Brush.verticalGradient(
            if (isDark) {
                listOf(
                    Color.White.copy(alpha = 0.16f),
                    Color.White.copy(alpha = 0.05f)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.96f),
                    Color.Black.copy(alpha = 0.07f)
                )
            }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isDark) 3.dp else 6.dp,
                    shape = cardShape,
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.24f else 0.06f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.28f else 0.10f)
                )
                .clip(cardShape)
                .background(cardBrush, cardShape)
                .border(0.65.dp, borderBrush, cardShape)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp)
            )
        }
    }
}

/**
 * 悬浮玻璃搜索胶囊：支持只读点击跳转模式与内联输入模式。
 */
@Composable
fun GlassSearchCapsule(
    query: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    onQueryChange: ((String) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    state: HazeState? = LocalGlassHazeState.current,
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryTextColor = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }

    LiquidGlassBox(
        modifier = modifier
            .height(46.dp)
            .tactileScale(interactionSource, pressedScale = 0.985f)
            .then(
                if (onClick != null && onQueryChange == null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        state = state,
        cornerRadius = 23.dp,
        widgetSize = GlassWidgetSize.SMALL,
        sceneType = GlassSceneType.IMMERSIVE,
        interactive = true,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = placeholder,
                tint = secondaryTextColor,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (onQueryChange != null) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = primaryTextColor),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = if (query.isBlank()) placeholder else query,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (query.isBlank()) secondaryTextColor.copy(alpha = 0.8f) else primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onQueryChange != null && query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "清除",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

/** 悬浮圆形玻璃图标按钮 */
@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    state: HazeState? = LocalGlassHazeState.current
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    LiquidGlassBox(
        modifier = modifier
            .size(size)
            .tactileScale(interactionSource, pressedScale = 0.90f)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        state = state,
        cornerRadius = size / 2,
        shape = CircleShape,
        widgetSize = GlassWidgetSize.SMALL,
        sceneType = GlassSceneType.IMMERSIVE,
        interactive = true,
        shadowElevation = 8.dp,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 胶囊动作按钮（支持高亮主色与玻璃态次级按钮） */
@Composable
fun GlassCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    state: HazeState? = LocalGlassHazeState.current
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(percent = 50)
    val contentAlpha = if (enabled) 1f else 0.45f
    val interactionSource = remember { MutableInteractionSource() }
    if (filled) {
        val bg = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.45f)
        val fg = MaterialTheme.colorScheme.onPrimary
        Row(
            modifier = modifier
                .height(38.dp)
                .tactileScale(interactionSource, pressedScale = 0.95f)
                .clip(shape)
                .background(bg)
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    enabled = enabled
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = fg,
                maxLines = 1
            )
        }
    } else {
        LiquidGlassBox(
            modifier = modifier
                .height(38.dp)
                .tactileScale(interactionSource, pressedScale = 0.95f)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            state = state,
            cornerRadius = 19.dp,
            shape = shape,
            widgetSize = GlassWidgetSize.SMALL,
            sceneType = GlassSceneType.IMMERSIVE,
            interactive = enabled,
            shadowElevation = 6.dp,
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    maxLines = 1
                )
            }
        }
    }
}

/** 玻璃筛选胶囊标签 */
@Composable
fun GlassFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(percent = 50)
    val interactionSource = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.28f else 0.16f)
            isDark -> Color.White.copy(alpha = 0.08f)
            else -> Color.White.copy(alpha = 0.72f)
        },
        animationSpec = NovelMotion.bouncySpring(),
        label = "filterPillBg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            isDark -> Color.White.copy(alpha = 0.14f)
            else -> Color.Black.copy(alpha = 0.08f)
        },
        animationSpec = NovelMotion.spatialSpring(),
        label = "filterPillBorder"
    )
    val fgColor = if (selected) {
        if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .height(34.dp)
            .tactileScale(interactionSource, pressedScale = 0.93f)
            .clip(shape)
            .background(bg)
            .border(0.7.dp, borderColor, shape)
            .clickable(interactionSource = interactionSource, indication = androidx.compose.foundation.LocalIndication.current) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = fgColor, modifier = Modifier.size(15.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = fgColor,
            maxLines = 1
        )
    }
}

/** 导航目的地元数据描述（供 GlassBottomDock 与 GlassNavigationRail 复用） */
data class GlassNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/** 导航项共享渲染内容（供 GlassNavigationRail 与 GlassBottomDock 复用） */
@Composable
private fun GlassNavDestinationItem(
    item: GlassNavItem,
    active: Boolean,
    isDragging: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    labelSpacing: Dp = 0.dp,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val itemScale by animateFloatAsState(
        targetValue = if (active && isDragging) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 450f),
        label = "navItemScale_${item.route}"
    )
    val contentColor = when {
        active && isDark -> NovelColors.GlassAccentBlueDark
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    }
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        if (labelSpacing > 0.dp) {
            Spacer(Modifier.height(labelSpacing))
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1
        )
    }
}

/**
 * 平板端悬浮液态玻璃侧边导航轨 (Google Material 3 Adaptive Navigation Rail + MagicOS 11 折射胶囊透镜)
 */
@Composable
fun GlassNavigationRail(
    items: List<GlassNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: HazeState? = LocalGlassHazeState.current,
    footerContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val railWidth = 80.dp
    val itemSlotHeight = 62.dp

    LiquidGlassBox(
        modifier = modifier.width(railWidth),
        state = state,
        cornerRadius = 32.dp,
        widgetSize = GlassWidgetSize.SMALL,
        sceneType = GlassSceneType.IMMERSIVE,
        interactive = true,
        transparencyLevel = 0.96f,
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LiquidGlassLensTrack(
                itemCount = items.size,
                selectedIndex = selectedIndex,
                onSelectIndex = onSelect,
                orientation = Orientation.Vertical,
                slotCrossSize = railWidth - 12.dp,
                pillCornerRadius = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemSlotHeight * items.size)
            ) { activeVisualIndex, isDragging ->
                Column(modifier = Modifier.fillMaxSize()) {
                    items.forEachIndexed { index, item ->
                        GlassNavDestinationItem(
                            item = item,
                            active = index == activeVisualIndex,
                            isDragging = isDragging,
                            isDark = isDark,
                            labelSpacing = 3.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemSlotHeight),
                            onClick = { onSelect(index) }
                        )
                    }
                }
            }
            if (footerContent != null) {
                Spacer(Modifier.height(10.dp))
                footerContent()
            }
        }
    }
}

/**
 * 紧凑型底部悬浮液态玻璃胶囊导航栏 (带 AGSL 折射水滴透镜与拖拽手势)
 */
@Composable
fun GlassBottomDock(
    items: List<GlassNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: HazeState? = LocalGlassHazeState.current
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val barHeight = 52.dp

    LiquidGlassBox(
        modifier = modifier,
        state = state,
        cornerRadius = 32.dp,
        widgetSize = GlassWidgetSize.SMALL,
        sceneType = GlassSceneType.IMMERSIVE,
        interactive = true,
        transparencyLevel = 0.96f,
        shadowElevation = 14.dp
    ) {
        LiquidGlassLensTrack(
            itemCount = items.size,
            selectedIndex = selectedIndex,
            onSelectIndex = onSelect,
            orientation = Orientation.Horizontal,
            slotCrossSize = barHeight,
            pillCornerRadius = 26.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp)
                .height(barHeight)
        ) { activeVisualIndex, isDragging ->
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    GlassNavDestinationItem(
                        item = item,
                        active = index == activeVisualIndex,
                        isDragging = isDragging,
                        isDark = isDark,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onSelect(index) }
                    )
                }
            }
        }
    }
}
