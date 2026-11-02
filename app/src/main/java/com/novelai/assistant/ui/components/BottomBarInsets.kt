package com.novelai.assistant.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 顶级页面底部需要预留的空间：悬浮胶囊导航栏高度（约 76dp）+ 系统导航栏高度 + 额外留白，
 * 保证列表滑到底部时最后的内容不会被导航栏遮挡。
 */
@Composable
fun bottomBarContentPadding(extra: Dp = 20.dp): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 76.dp + extra