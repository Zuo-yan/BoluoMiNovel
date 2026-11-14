package com.novelai.assistant.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset

/**
 * 菠萝蜜小说统一物理动效规范（融合 iOS 物理弹性、Material 3 Expressive 空间过渡、MagicOS 轻盈响应）。
 */
object NovelMotion {
    /** 快速起步、极柔阻尼的流体曲线（类 iOS UIViewPropertyAnimator / M3 Emphasized Decelerate） */
    val fluidDecelerate = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

    /** 苹果式推进曲线 */
    val iosPushEasing = CubicBezierEasing(0.32f, 0.72f, 0.0f, 1.0f)

    /** 空间位移动效（滑入/列表重排/容器尺寸变化） */
    fun <T> spatialSpring() = spring<T>(
        dampingRatio = 0.84f,
        stiffness = 380f
    )

    /** 弹性微交互动效（按压回弹、胶囊选中、开关切换） */
    fun <T> bouncySpring() = spring<T>(
        dampingRatio = 0.58f,
        stiffness = 500f
    )

    /** 温柔展开动效（HUD 显隐、抽屉展开） */
    fun <T> expressiveSpring() = spring<T>(
        dampingRatio = 0.80f,
        stiffness = 320f
    )

    /** 柔和淡入 */
    fun fadeEnter(durationMs: Int = 200): EnterTransition =
        fadeIn(tween(durationMs, easing = fluidDecelerate))

    /** 柔和淡出 */
    fun fadeExit(durationMs: Int = 150): ExitTransition =
        fadeOut(tween(durationMs))

    /** 顶级标签切换：轻量淡入 + 微缩放聚拢 */
    fun topLevelEnter(): EnterTransition =
        fadeIn(tween(200, easing = fluidDecelerate)) +
            scaleIn(
                initialScale = 0.975f,
                transformOrigin = TransformOrigin.Center,
                animationSpec = tween(220, easing = fluidDecelerate)
            )

    fun topLevelExit(): ExitTransition =
        fadeOut(tween(140)) +
            scaleOut(
                targetScale = 0.985f,
                transformOrigin = TransformOrigin.Center,
                animationSpec = tween(140)
            )

    /** 详情页推进与返回（类 iOS 水平层级推移） */
    fun pushEnter(): EnterTransition =
        slideInHorizontally(
            animationSpec = spring(
                dampingRatio = 0.86f,
                stiffness = 390f
            )
        ) { fullWidth -> (fullWidth * 0.18f).toInt() } +
            fadeIn(tween(220, easing = fluidDecelerate))

    fun pushExit(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(220, easing = iosPushEasing)
        ) { fullWidth -> -(fullWidth * 0.06f).toInt() } +
            fadeOut(tween(160))

    fun popEnter(): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(220, easing = fluidDecelerate)
        ) { fullWidth -> -(fullWidth * 0.06f).toInt() } +
            fadeIn(tween(200, easing = fluidDecelerate))

    fun popExit(): ExitTransition =
        slideOutHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 420f
            )
        ) { fullWidth -> (fullWidth * 0.20f).toInt() } +
            fadeOut(tween(170))

    /** LazyList / LazyGrid 项目位置变更弹性插值 */
    val itemPlacementSpec = spring<IntOffset>(
        dampingRatio = 0.82f,
        stiffness = 380f
    )
}

/**
 * 跟随 [InteractionSource] 的物理按压回弹缩放（苹果 / 荣耀胶囊触感）。
 * 保持与 clickable / combinedClickable 的 InteractionSource 共享，不截断任何手势。
 */
@Composable
fun rememberPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.95f
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = NovelMotion.bouncySpring(),
        label = "tactilePressScale"
    )
    return scale
}

fun Modifier.tactileScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.95f
): Modifier = composed {
    val scale = rememberPressScale(interactionSource, pressedScale)
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
