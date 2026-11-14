package com.novelai.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Material 3 Adaptive 窗口宽度分级 */
enum class WindowWidthClass {
    /** 手机竖屏 / 窄分屏 (< 600.dp) */
    COMPACT,
    /** 折叠屏展开 / 小尺寸平板竖屏 (600.dp .. 839.dp) */
    MEDIUM,
    /** 平板横屏 / 大屏设备 (>= 840.dp) */
    EXPANDED
}

/** Material 3 Adaptive 窗口高度分级 */
enum class WindowHeightClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

/** 自适应主导航形态 */
enum class AdaptiveNavigationType {
    /** 手机/窄窗：底部悬浮液态玻璃胶囊导航栏 */
    BOTTOM_BAR,
    /** 中宽/展开平板：左侧悬浮液态玻璃 Navigation Rail */
    NAVIGATION_RAIL
}

/**
 * 全局平板与大屏响应式布局度量（Material 3 Adaptive 规范）。
 */
@Immutable
data class AdaptiveMetrics(
    val widthDp: Dp,
    val heightDp: Dp,
    val widthClass: WindowWidthClass,
    val heightClass: WindowHeightClass,
    val isLandscape: Boolean,
    val isTablet: Boolean,
    val navigationType: AdaptiveNavigationType,
    val twoPaneEnabled: Boolean,
    val dualPageReaderEnabled: Boolean,
    val listGridColumns: Int,
    val bookshelfMinCardWidth: Dp,
    val horizontalPadding: Dp,
    val paneSpacing: Dp,
    val navigationRailWidth: Dp = 80.dp,
    val navigationRailInset: Dp = 12.dp,
    val maxDockWidth: Dp = 440.dp,
    val maxContentWidth: Dp = 1120.dp,
    val maxFormWidth: Dp = 720.dp,
    val maxReadableWidth: Dp = 700.dp,
    val maxDialogSheetWidth: Dp = 540.dp,
    val supportingPaneWidth: Dp = 380.dp,
    val listPaneWidth: Dp = 400.dp,
    val tocDrawerMaxWidth: Dp = 400.dp
)

fun computeAdaptiveMetrics(widthDp: Dp, heightDp: Dp): AdaptiveMetrics {
    val widthClass = when {
        widthDp < 600.dp -> WindowWidthClass.COMPACT
        widthDp < 840.dp -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.EXPANDED
    }
    val heightClass = when {
        heightDp < 480.dp -> WindowHeightClass.COMPACT
        heightDp < 900.dp -> WindowHeightClass.MEDIUM
        else -> WindowHeightClass.EXPANDED
    }
    val isLandscape = widthDp > heightDp
    val isTablet = widthClass != WindowWidthClass.COMPACT
    val navigationType = if (isTablet) {
        AdaptiveNavigationType.NAVIGATION_RAIL
    } else {
        AdaptiveNavigationType.BOTTOM_BAR
    }
    val twoPaneEnabled = widthClass == WindowWidthClass.EXPANDED ||
        (widthDp >= 720.dp && isLandscape)
    val dualPageReaderEnabled = widthClass == WindowWidthClass.EXPANDED && isLandscape
    val listGridColumns = when (widthClass) {
        WindowWidthClass.COMPACT -> 1
        WindowWidthClass.MEDIUM -> 2
        WindowWidthClass.EXPANDED -> 3
    }
    val bookshelfMinCardWidth = if (widthClass == WindowWidthClass.EXPANDED) 156.dp else 148.dp
    val horizontalPadding = when (widthClass) {
        WindowWidthClass.COMPACT -> 20.dp
        WindowWidthClass.MEDIUM -> 24.dp
        WindowWidthClass.EXPANDED -> 32.dp
    }
    val paneSpacing = if (widthClass == WindowWidthClass.EXPANDED) 20.dp else 16.dp

    return AdaptiveMetrics(
        widthDp = widthDp,
        heightDp = heightDp,
        widthClass = widthClass,
        heightClass = heightClass,
        isLandscape = isLandscape,
        isTablet = isTablet,
        navigationType = navigationType,
        twoPaneEnabled = twoPaneEnabled,
        dualPageReaderEnabled = dualPageReaderEnabled,
        listGridColumns = listGridColumns,
        bookshelfMinCardWidth = bookshelfMinCardWidth,
        horizontalPadding = horizontalPadding,
        paneSpacing = paneSpacing
    )
}

val LocalAdaptiveMetrics = staticCompositionLocalOf {
    computeAdaptiveMetrics(412.dp, 915.dp)
}

@Composable
fun rememberAdaptiveMetrics(): AdaptiveMetrics {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val (widthDp, heightDp) = if (containerSize.width > 0 && containerSize.height > 0) {
        with(density) { containerSize.width.toDp() to containerSize.height.toDp() }
    } else {
        configuration.screenWidthDp.dp to configuration.screenHeightDp.dp
    }
    return computeAdaptiveMetrics(widthDp, heightDp)
}

@Composable
fun ProvideAdaptiveMetrics(content: @Composable () -> Unit) {
    val metrics = rememberAdaptiveMetrics()
    CompositionLocalProvider(LocalAdaptiveMetrics provides metrics, content = content)
}

/**
 * 居中最大宽度容器：在平板/大屏上约束单栏页面或表单的可读最大宽度，防止文本或按钮横向拉爆。
 */
@Composable
fun AdaptiveCenteredContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = LocalAdaptiveMetrics.current.maxContentWidth,
    horizontalPadding: Dp = 0.dp,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            content = content
        )
    }
}

/**
 * 通用双栏容器（Material 3 Canonical Two-Pane）：
 * 展开态左右并排展示主从双栏；紧凑态仅渲染主栏或由调用方控制路由。
 */
@Composable
fun AdaptiveTwoPane(
    modifier: Modifier = Modifier,
    splitEnabled: Boolean = LocalAdaptiveMetrics.current.twoPaneEnabled,
    primaryFraction: Float = 0.42f,
    spacing: Dp = LocalAdaptiveMetrics.current.paneSpacing,
    primaryPane: @Composable BoxScope.() -> Unit,
    secondaryPane: @Composable BoxScope.() -> Unit
) {
    if (splitEnabled) {
        val clamped = primaryFraction.coerceIn(0.25f, 0.70f)
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            Box(
                modifier = Modifier
                    .weight(clamped)
                    .fillMaxHeight(),
                content = primaryPane
            )
            Box(
                modifier = Modifier
                    .weight(1f - clamped)
                    .fillMaxHeight(),
                content = secondaryPane
            )
        }
    } else {
        Box(modifier = modifier.fillMaxSize(), content = primaryPane)
    }
}

/**
 * Material 3 Canonical List-Detail 双栏布局（适用于设置、AI 伴读、分支树、书目详情）：
 * - 双栏模式下左右常驻并排显示 `listPane` 与 `detailPane`；
 * - 单栏模式下根据 `showDetail` 切换列表或详情页。
 */
@Composable
fun AdaptiveListDetailPane(
    showDetail: Boolean,
    modifier: Modifier = Modifier,
    twoPaneEnabled: Boolean = LocalAdaptiveMetrics.current.twoPaneEnabled,
    listFraction: Float = 0.38f,
    spacing: Dp = LocalAdaptiveMetrics.current.paneSpacing,
    listPane: @Composable BoxScope.() -> Unit,
    detailPane: @Composable BoxScope.() -> Unit
) {
    if (twoPaneEnabled) {
        AdaptiveTwoPane(
            modifier = modifier,
            splitEnabled = true,
            primaryFraction = listFraction,
            spacing = spacing,
            primaryPane = listPane,
            secondaryPane = detailPane
        )
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            if (showDetail) detailPane() else listPane()
        }
    }
}

/**
 * Material 3 Canonical Supporting Pane 布局（适用于阅读器右侧段评/目录侧栏、工作台辅助面板）：
 * - 双栏模式下主阅读/编辑区居左自适应，辅助面板以可动画收放的固定宽度侧栏停靠在右侧；
 * - 紧凑态仅展示 `mainPane`，由调用方在 `showSupporting` 时弹窗/抽屉展示辅助面板。
 */
@Composable
fun AdaptiveSupportingPane(
    showSupporting: Boolean,
    modifier: Modifier = Modifier,
    twoPaneEnabled: Boolean = LocalAdaptiveMetrics.current.twoPaneEnabled,
    supportingWidth: Dp = LocalAdaptiveMetrics.current.supportingPaneWidth,
    spacing: Dp = LocalAdaptiveMetrics.current.paneSpacing,
    mainPane: @Composable BoxScope.() -> Unit,
    supportingPane: @Composable BoxScope.() -> Unit
) {
    if (twoPaneEnabled) {
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                content = mainPane
            )
            AnimatedVisibility(
                visible = showSupporting,
                enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .width(supportingWidth)
                        .fillMaxHeight(),
                    content = supportingPane
                )
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize(), content = mainPane)
    }
}

/**
 * 平板端居中限宽液态玻璃弹窗（替代铺满宽屏的原生 AlertDialog）。
 */
@Composable
fun AdaptiveGlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = LocalAdaptiveMetrics.current.maxDialogSheetWidth,
    cornerRadius: Dp = 28.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalInsideGlassOverlay provides true) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                LiquidGlassBox(
                    modifier = modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth(),
                    cornerRadius = cornerRadius,
                    widgetSize = GlassWidgetSize.BIG,
                    sceneType = GlassSceneType.GENERAL,
                    interactive = false,
                    skipRenderLayer = true,
                    shadowElevation = 20.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(contentPadding),
                        content = content
                    )
                }
            }
        }
    }
}

/**
 * 自适应玻璃浮层表单：
 * - 手机端呈现为标准受限宽度 `ModalBottomSheet`（内部包裹液态玻璃卡片）；
 * - 平板端默认自动转为居中限宽 `AdaptiveGlassDialog`，避免底部表单在 1200dp 宽屏上拉成扁长条。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveGlassSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = LocalAdaptiveMetrics.current.maxDialogSheetWidth,
    useDialogOnTablet: Boolean = true,
    cornerRadius: Dp = 28.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    if (useDialogOnTablet && adaptive.isTablet) {
        AdaptiveGlassDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            maxWidth = maxWidth,
            cornerRadius = cornerRadius,
            contentPadding = contentPadding,
            content = content
        )
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            sheetMaxWidth = maxWidth,
            containerColor = Color.Transparent,
            dragHandle = null
        ) {
            CompositionLocalProvider(LocalInsideGlassOverlay provides true) {
                LiquidGlassBox(
                    modifier = modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .navigationBarsPadding(),
                    cornerRadius = cornerRadius,
                    widgetSize = GlassWidgetSize.BIG,
                    sceneType = GlassSceneType.GENERAL,
                    interactive = false,
                    skipRenderLayer = true,
                    shadowElevation = 18.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(contentPadding)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 12.dp)
                                .width(38.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        )
                        content()
                    }
                }
            }
        }
    }
}
