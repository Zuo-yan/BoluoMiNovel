package com.novelai.assistant.ui.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.AdaptiveTwoPane
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.LocalGlassHazeState
import com.novelai.assistant.ui.theme.NovelColors
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val badge: String,
    val title: String,
    val subtitle: String,
    val body: List<String>
)

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: AppViewModel = hiltViewModel()
) {
    val pages = remember {
        listOf(
            OnboardingPage(
                icon = Icons.Rounded.AutoAwesome,
                badge = "欢迎使用",
                title = "菠萝蜜小说",
                subtitle = "菠萝包轻小说阅读器 × 深度 AI 伴读 × 沉浸式续写与平行分支",
                body = listOf(
                    "BYOK 模式：接入你自己的模型密钥，所有数据只留在你的设备上",
                    "支持本地 TXT / Markdown / EPUB 导入，与菠萝包书架无缝同步阅读",
                    "集成系统级 TTS 听书与自由悬浮播放胶囊，随时切换阅读与听书"
                )
            ),
            OnboardingPage(
                icon = Icons.Rounded.Key,
                badge = "STEP 01",
                title = "第一步 · 接入你的 AI",
                subtitle = "自带密钥直连主流大模型，零中继更私密",
                body = listOf(
                    "1. 打开「设置 → 添加提供商」",
                    "2. 选择厂商及平台／地域，或选择自定义服务",
                    "3. 填写 API Key，确认协议、服务地址与模型 ID",
                    "4. 可自动识别最大输出；连接测试读取模型接口，密钥经 Keystore 加密存储"
                )
            ),
            OnboardingPage(
                icon = Icons.Rounded.UploadFile,
                badge = "STEP 02",
                title = "第二步 · 阅读与创作",
                subtitle = "从划线伴读到平行宇宙分支，全程由你掌控",
                body = listOf(
                    "· 书架右上角导入 TXT / Markdown / EPUB",
                    "·「菠萝包」登录账号查看书架，点书直接继续阅读",
                    "· 阅读时长按段落「划线即问」，AI 自动检索相关章节作答",
                    "· 底栏「AI 续写」：单章续写，或 What-if 推演平行世界分支",
                    "· 分支树中随时回看你的 AI 衍生剧情"
                )
            )
        )
    }

    val adaptive = LocalAdaptiveMetrics.current
    val hazeState = LocalGlassHazeState.current
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryInk = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryInk = if (isDark) Color(0xFFBEBECA) else MaterialTheme.colorScheme.onSurfaceVariant
    val accentInk = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    fun finish() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.completeOnboarding()
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = adaptive.horizontalPadding, vertical = 12.dp)
    ) {
        // 顶部跳过栏
        AdaptiveCenteredContainer(
            maxWidth = if (adaptive.twoPaneEnabled) adaptive.maxContentWidth else 640.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accentInk.copy(alpha = if (isDark) 0.18f else 0.10f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = accentInk,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "BYOK 本地隐私架构",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentInk,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.weight(1f))
                GlassCapsuleButton(
                    text = "跳过",
                    onClick = { finish() },
                    filled = false,
                    state = hazeState
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (adaptive.twoPaneEnabled) {
            val activePage = pages[pagerState.currentPage]
            AdaptiveCenteredContainer(
                maxWidth = adaptive.maxContentWidth,
                modifier = Modifier.weight(1f)
            ) {
                AdaptiveTwoPane(
                    splitEnabled = true,
                    primaryFraction = 0.44f,
                    spacing = adaptive.paneSpacing,
                    modifier = Modifier.fillMaxSize(),
                    primaryPane = {
                        OnboardingHeroPreviewCard(
                            page = activePage,
                            pages = pages,
                            currentIndex = pagerState.currentPage,
                            isDark = isDark,
                            primaryInk = primaryInk,
                            secondaryInk = secondaryInk,
                            accentInk = accentInk,
                            onSelectPage = { index ->
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    },
                    secondaryPane = {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) { index ->
                                val page = pages[index]
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OnboardingStepsCard(
                                        page = page,
                                        isDark = isDark,
                                        primaryInk = primaryInk,
                                        secondaryInk = secondaryInk,
                                        accentInk = accentInk
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            OnboardingBottomControls(
                                currentPage = pagerState.currentPage,
                                pageCount = pages.size,
                                isDark = isDark,
                                accentInk = accentInk,
                                onPrevious = {
                                    scope.launch {
                                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                                    }
                                },
                                onNextOrFinish = {
                                    if (pagerState.currentPage == pages.lastIndex) finish()
                                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                            )
                        }
                    }
                )
            }
        } else {
            AdaptiveCenteredContainer(
                maxWidth = 640.dp,
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { index ->
                        val page = pages[index]
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            LiquidGlassBox(
                                modifier = Modifier.fillMaxWidth(),
                                state = hazeState,
                                cornerRadius = 28.dp,
                                widgetSize = GlassWidgetSize.BIG,
                                sceneType = GlassSceneType.GENERAL,
                                interactive = false,
                                skipRenderLayer = true,
                                shadowElevation = 10.dp
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(84.dp)
                                            .clip(CircleShape)
                                            .background(accentInk.copy(alpha = if (isDark) 0.22f else 0.12f))
                                            .border(
                                                0.8.dp,
                                                accentInk.copy(alpha = 0.35f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = page.icon,
                                            contentDescription = null,
                                            tint = accentInk,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        text = page.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accentInk,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = page.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryInk,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = page.subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = secondaryInk,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(20.dp))
                                    OnboardingStepList(
                                        lines = page.body,
                                        isDark = isDark,
                                        primaryInk = primaryInk,
                                        accentInk = accentInk
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    OnboardingBottomControls(
                        currentPage = pagerState.currentPage,
                        pageCount = pages.size,
                        isDark = isDark,
                        accentInk = accentInk,
                        onPrevious = {
                            scope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                            }
                        },
                        onNextOrFinish = {
                            if (pagerState.currentPage == pages.lastIndex) finish()
                            else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun OnboardingHeroPreviewCard(
    page: OnboardingPage,
    pages: List<OnboardingPage>,
    currentIndex: Int,
    isDark: Boolean,
    primaryInk: Color,
    secondaryInk: Color,
    accentInk: Color,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    LiquidGlassBox(
        modifier = modifier,
        state = LocalGlassHazeState.current,
        cornerRadius = 28.dp,
        widgetSize = GlassWidgetSize.BIG,
        sceneType = GlassSceneType.IMMERSIVE,
        interactive = false,
        skipRenderLayer = true,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(accentInk.copy(alpha = if (isDark) 0.24f else 0.13f))
                    .border(1.dp, accentInk.copy(alpha = 0.40f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = accentInk,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = page.badge,
                style = MaterialTheme.typography.labelMedium,
                color = accentInk,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = primaryInk,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = page.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryInk,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            // 阶段预览快捷切换胶囊列表
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pages.forEachIndexed { idx, item ->
                    val selected = idx == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                when {
                                    selected -> accentInk.copy(alpha = if (isDark) 0.24f else 0.14f)
                                    isDark -> Color.White.copy(alpha = 0.06f)
                                    else -> Color.White.copy(alpha = 0.55f)
                                }
                            )
                            .border(
                                width = 0.7.dp,
                                color = if (selected) accentInk.copy(alpha = 0.55f)
                                else Color.White.copy(alpha = if (isDark) 0.12f else 0.45f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectPage(idx)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (selected) accentInk else secondaryInk,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) primaryInk else secondaryInk,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = accentInk,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingStepsCard(
    page: OnboardingPage,
    isDark: Boolean,
    primaryInk: Color,
    secondaryInk: Color,
    accentInk: Color
) {
    GlassSectionCard(
        title = page.badge,
        cornerRadius = 26.dp,
        contentPadding = PaddingValues(24.dp),
        state = LocalGlassHazeState.current
    ) {
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = primaryInk
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = page.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryInk
        )
        Spacer(Modifier.height(18.dp))
        OnboardingStepList(
            lines = page.body,
            isDark = isDark,
            primaryInk = primaryInk,
            accentInk = accentInk
        )
    }
}

@Composable
private fun OnboardingStepList(
    lines: List<String>,
    isDark: Boolean,
    primaryInk: Color,
    accentInk: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        lines.forEach { line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.06f)
                        else Color.White.copy(alpha = 0.56f)
                    )
                    .border(
                        width = 0.6.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.10f)
                        else Color.White.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentInk)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = line.trimStart('·', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    color = primaryInk
                )
            }
        }
    }
}

@Composable
private fun OnboardingBottomControls(
    currentPage: Int,
    pageCount: Int,
    isDark: Boolean,
    accentInk: Color,
    onPrevious: () -> Unit,
    onNextOrFinish: () -> Unit
) {
    val hazeState = LocalGlassHazeState.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pageCount) { i ->
                val selected = currentPage == i
                Box(
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .width(if (selected) 20.dp else 7.dp)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) accentInk
                            else if (isDark) Color.White.copy(alpha = 0.24f)
                            else MaterialTheme.colorScheme.outline
                        )
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPage > 0) {
                GlassCapsuleButton(
                    text = "上一步",
                    onClick = onPrevious,
                    filled = false,
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    state = hazeState,
                    modifier = Modifier.weight(0.4f).height(44.dp)
                )
            }
            GlassCapsuleButton(
                text = if (currentPage == pageCount - 1) "开始阅读" else "下一步",
                onClick = onNextOrFinish,
                filled = true,
                icon = if (currentPage == pageCount - 1) null else Icons.AutoMirrored.Rounded.ArrowForward,
                state = hazeState,
                modifier = Modifier.weight(if (currentPage > 0) 0.6f else 1f).height(44.dp)
            )
        }
    }
}
