package com.novelai.assistant.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 顶级页面底部需要预留的空间：
 * - 手机紧凑模式（底部悬浮胶囊栏）：悬浮栏高度（76dp）+ 系统导航栏高度 + 额外留白；
 * - 平板侧边导航轨模式（Navigation Rail）：无需在底部预留 76dp，仅保留系统导航栏高度 + 额外留白。
 */
@Composable
fun bottomBarContentPadding(extra: Dp = 20.dp): Dp {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val adaptive = LocalAdaptiveMetrics.current
    return if (adaptive.navigationType == AdaptiveNavigationType.NAVIGATION_RAIL) {
        navBottom + extra
    } else {
        navBottom + 76.dp + extra
    }
}

/**
 * 平板侧边悬浮 Navigation Rail 场景下的左侧内容避让宽度（非 Rail 模式返回 0.dp）。
 */
@Composable
fun navigationRailContentStartPadding(extra: Dp = 0.dp): Dp {
    val adaptive = LocalAdaptiveMetrics.current
    return if (adaptive.navigationType == AdaptiveNavigationType.NAVIGATION_RAIL) {
        adaptive.navigationRailWidth + adaptive.navigationRailInset * 2 + extra
    } else {
        0.dp
    }
}

/**
 * 通用自适应滚动列表 `contentPadding` 计算。
 */
@Composable
fun adaptiveContentPadding(
    top: Dp = 8.dp,
    bottomExtra: Dp = 20.dp
): PaddingValues {
    val adaptive = LocalAdaptiveMetrics.current
    return PaddingValues(
        start = adaptive.horizontalPadding,
        end = adaptive.horizontalPadding,
        top = top,
        bottom = bottomBarContentPadding(bottomExtra)
    )
}
