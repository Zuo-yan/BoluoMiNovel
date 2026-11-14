package com.novelai.assistant.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.BuildConfig
import com.novelai.assistant.data.prefs.AppThemeMode
import com.novelai.assistant.data.repository.ApiProviderConfig
import com.novelai.assistant.data.repository.ModelRoute
import com.novelai.assistant.data.repository.TaskScene
import com.novelai.assistant.data.repository.TokenUsage
import com.novelai.assistant.data.tts.BatteryOptimizationHelper
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.AdaptiveGlassDialog
import com.novelai.assistant.ui.components.AdaptiveTwoPane
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassFilterPill
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.HonorGlassDiagnostics
import com.novelai.assistant.ui.components.LargeTitleHeader
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.NovelMotion
import com.novelai.assistant.ui.components.SettingRow
import com.novelai.assistant.ui.components.adaptiveContentPadding
import com.novelai.assistant.ui.components.bottomBarContentPadding
import com.novelai.assistant.ui.theme.NovelColors

@Composable
fun SettingsScreen(
    onEditProvider: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val adaptive = LocalAdaptiveMetrics.current
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val continuationRoute by viewModel.continuationRoute.collectAsStateWithLifecycle()
    val companionRoute by viewModel.companionRoute.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val appTheme by viewModel.appThemeMode.collectAsStateWithLifecycle()
    val routeModels by viewModel.routeModels.collectAsStateWithLifecycle()
    val loadingRouteModels by viewModel.loadingRouteModels.collectAsStateWithLifecycle()
    val routeModelError by viewModel.routeModelError.collectAsStateWithLifecycle()

    var routeScene by remember { mutableStateOf<TaskScene?>(null) }
    var confirmResetUsage by remember { mutableStateOf(false) }

    // 双栏平板模式下右侧详情当前选中的提供商（null 表示展示液态玻璃与分流概览）
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var editNonce by remember { mutableIntStateOf(0) }

    val screenContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isIgnoringBattery by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(screenContext))
    }
    DisposableEffect(lifecycleOwner, screenContext) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBattery = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(screenContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val openProvider: (String) -> Unit = { id ->
        if (adaptive.twoPaneEnabled) {
            selectedProviderId = id
            editNonce++
        } else {
            onEditProvider(id)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        val contentPadding = adaptiveContentPadding(top = 4.dp, bottomExtra = 20.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            if (adaptive.twoPaneEnabled) {
                AdaptiveTwoPane(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = adaptive.horizontalPadding),
                    splitEnabled = true,
                    primaryFraction = 0.40f,
                    primaryPane = {
                        SettingsListPane(
                            providers = providers,
                            continuationRoute = continuationRoute,
                            companionRoute = companionRoute,
                            usage = usage,
                            appTheme = appTheme,
                            isIgnoringBattery = isIgnoringBattery,
                            selectedProviderId = selectedProviderId,
                            onSelectProvider = openProvider,
                            onToggleEnabled = { cfg, enabled -> viewModel.toggleEnabled(cfg, enabled) },
                            onSetDefaultProvider = { cfg -> viewModel.setDefaultProvider(cfg) },
                            onOpenRoute = { scene -> routeScene = scene },
                            onRequestResetUsage = { confirmResetUsage = true },
                            onSetAppTheme = { mode -> viewModel.setAppTheme(mode) },
                            onRequestBatteryWhitelist = {
                                BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(screenContext)
                            },
                            contentPadding = PaddingValues(top = 4.dp, bottom = bottomBarContentPadding(20.dp))
                        )
                    },
                    secondaryPane = {
                        AnimatedContent(
                            targetState = selectedProviderId to editNonce,
                            transitionSpec = {
                                (fadeIn(androidx.compose.animation.core.tween(210, easing = NovelMotion.fluidDecelerate)) +
                                    scaleIn(initialScale = 0.985f, animationSpec = NovelMotion.spatialSpring())) togetherWith
                                    (fadeOut(androidx.compose.animation.core.tween(130)) +
                                        scaleOut(targetScale = 0.99f))
                            },
                            label = "settingsSecondaryPane"
                        ) { (activeId, _) ->
                            if (activeId != null) {
                                ProviderEditContent(
                                    providerId = activeId,
                                    onSaved = { savedId -> selectedProviderId = savedId },
                                    onDeleted = { selectedProviderId = null },
                                    showInlineHeader = true,
                                    onCloseInline = { selectedProviderId = null },
                                    loadForceKey = editNonce,
                                    contentPadding = PaddingValues(top = 8.dp, bottom = bottomBarContentPadding(24.dp))
                                )
                            } else {
                                SettingsOverviewDetailPane(
                                    providers = providers,
                                    continuationRoute = continuationRoute,
                                    companionRoute = companionRoute,
                                    appTheme = appTheme,
                                    onAddProvider = { openProvider("new") },
                                    onSelectProvider = openProvider,
                                    onConfigureRoute = { routeScene = it },
                                    contentPadding = PaddingValues(top = 8.dp, bottom = bottomBarContentPadding(24.dp))
                                )
                            }
                        }
                    }
                )
            } else {
                AdaptiveCenteredContainer(
                    maxWidth = adaptive.maxFormWidth,
                    modifier = Modifier.fillMaxSize()
                ) {
                    SettingsListPane(
                        providers = providers,
                        continuationRoute = continuationRoute,
                        companionRoute = companionRoute,
                        usage = usage,
                        appTheme = appTheme,
                        isIgnoringBattery = isIgnoringBattery,
                        selectedProviderId = null,
                        onSelectProvider = openProvider,
                        onToggleEnabled = { cfg, enabled -> viewModel.toggleEnabled(cfg, enabled) },
                        onSetDefaultProvider = { cfg -> viewModel.setDefaultProvider(cfg) },
                        onOpenRoute = { scene -> routeScene = scene },
                        onRequestResetUsage = { confirmResetUsage = true },
                        onSetAppTheme = { mode -> viewModel.setAppTheme(mode) },
                        onRequestBatteryWhitelist = {
                            BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(screenContext)
                        },
                        contentPadding = contentPadding
                    )
                }
            }
        }
    }

    if (confirmResetUsage) {
        AdaptiveGlassDialog(
            onDismissRequest = { confirmResetUsage = false },
            maxWidth = adaptive.maxDialogSheetWidth
        ) {
            Text(
                "清空用量统计？",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "确定要重置当前设备记录的累计输入与输出 Token 统计吗？此操作无法撤销。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { confirmResetUsage = false }) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        confirmResetUsage = false
                        viewModel.resetUsage()
                    }
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    routeScene?.let { scene ->
        RouteEditDialog(
            scene = scene,
            providers = providers,
            current = if (scene == TaskScene.CONTINUATION) continuationRoute else companionRoute,
            routeModels = routeModels,
            loadingModels = loadingRouteModels,
            routeModelError = routeModelError,
            onGetRecommendedModels = { viewModel.getRecommendedModels(it) },
            onFetchModels = { viewModel.fetchModelsFor(it) },
            onClearModels = { viewModel.clearRouteModels() },
            onDismiss = {
                viewModel.clearRouteModels()
                routeScene = null
            },
            onSave = { providerId, model ->
                viewModel.saveRoute(scene, providerId, model)
                viewModel.clearRouteModels()
                routeScene = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsListPane(
    providers: List<ApiProviderConfig>,
    continuationRoute: ModelRoute,
    companionRoute: ModelRoute,
    usage: TokenUsage,
    appTheme: AppThemeMode,
    isIgnoringBattery: Boolean,
    selectedProviderId: String?,
    onSelectProvider: (String) -> Unit,
    onToggleEnabled: (ApiProviderConfig, Boolean) -> Unit,
    onSetDefaultProvider: (ApiProviderConfig) -> Unit,
    onOpenRoute: (TaskScene) -> Unit,
    onRequestResetUsage: () -> Unit,
    onSetAppTheme: (AppThemeMode) -> Unit,
    onRequestBatteryWhitelist: () -> Unit,
    contentPadding: PaddingValues
) {
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        LargeTitleHeader(
            title = "设置",
            subtitle = "BYOK 模式 · 密钥仅存本地加密空间",
            horizontalPadding = 0.dp
        )

        GlassSectionCard(
            title = "模型与 API",
            footer = "支持 Chat Completions、Responses、Anthropic Messages 与 Gemini；密钥经 Android Keystore 加密存储"
        ) {
            providers.forEach { config ->
                val isDefault = continuationRoute.providerId == config.id && companionRoute.providerId == config.id
                val isSelected = selectedProviderId == config.id
                val rowBg = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.20f else 0.12f)
                } else {
                    Color.Transparent
                }
                val rowModifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(rowBg)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(16.dp)
                            )
                        } else {
                            Modifier
                        }
                    )

                SettingRow(
                    title = config.providerName,
                    subtitle = "${config.selectedModel.ifBlank { "未选模型" }} · ${config.baseUrl}",
                    icon = Icons.Rounded.Key,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else NovelColors.Indigo,
                    modifier = rowModifier,
                    onClick = { onSelectProvider(config.id) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isDefault) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        "默认",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else if (config.isEnabled) {
                                TextButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSetDefaultProvider(config)
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text("设为默认", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            Switch(
                                checked = config.isEnabled,
                                onCheckedChange = { onToggleEnabled(config, it) }
                            )
                        }
                    }
                )
            }
            val addSelected = selectedProviderId == "new"
            SettingRow(
                title = "添加提供商",
                subtitle = "九家厂商预设 · 自定义 / 本地服务",
                icon = Icons.Rounded.Add,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (addSelected) MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.20f else 0.12f)
                        else Color.Transparent
                    ),
                onClick = { onSelectProvider("new") },
                trailing = {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        GlassSectionCard(
            title = "任务分流",
            footer = "为不同任务指定不同模型，兼顾成本与效果"
        ) {
            val contProvider = providers.firstOrNull { it.id == continuationRoute.providerId }
            val compProvider = providers.firstOrNull { it.id == companionRoute.providerId }
            SettingRow(
                title = "续写场景",
                subtitle = TaskScene.CONTINUATION.hint,
                icon = Icons.Rounded.SwapHoriz,
                onClick = { onOpenRoute(TaskScene.CONTINUATION) },
                trailing = {
                    Text(
                        continuationRoute.model.ifBlank { contProvider?.selectedModel ?: "未配置" },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
            HorizontalDivider(
                Modifier.padding(start = 52.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            SettingRow(
                title = "速览与伴读场景",
                subtitle = TaskScene.COMPANION.hint,
                icon = Icons.AutoMirrored.Rounded.Chat,
                onClick = { onOpenRoute(TaskScene.COMPANION) },
                trailing = {
                    Text(
                        companionRoute.model.ifBlank { compProvider?.selectedModel ?: "未配置" },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }

        GlassSectionCard(title = "外观") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.ColorLens,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("主题模式", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("深色与浅色外观将与阅读器黑白底色双向同步", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassFilterPill(
                        label = "跟随系统",
                        selected = appTheme == AppThemeMode.FOLLOW_SYSTEM,
                        onClick = { onSetAppTheme(AppThemeMode.FOLLOW_SYSTEM) }
                    )
                    GlassFilterPill(
                        label = "浅色",
                        selected = appTheme == AppThemeMode.LIGHT,
                        onClick = { onSetAppTheme(AppThemeMode.LIGHT) }
                    )
                    GlassFilterPill(
                        label = "深色",
                        selected = appTheme == AppThemeMode.DARK,
                        onClick = { onSetAppTheme(AppThemeMode.DARK) }
                    )
                }
            }
        }

        GlassSectionCard(title = "用量统计", footer = "Token 用量为本地累计估算") {
            SettingRow(
                title = "累计输入 Token",
                icon = Icons.Rounded.Cached,
                trailing = {
                    Text(
                        "${usage.promptTokens}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            )
            HorizontalDivider(
                Modifier.padding(start = 52.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            SettingRow(
                title = "累计输出 Token",
                icon = Icons.Rounded.Cached,
                trailing = {
                    Text(
                        "${usage.completionTokens}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            )
            HorizontalDivider(
                Modifier.padding(start = 52.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            SettingRow(
                title = "清空统计",
                icon = Icons.Rounded.Delete,
                tint = MaterialTheme.colorScheme.error,
                onClick = onRequestResetUsage
            )
        }

        GlassSectionCard(
            title = "系统与后台听书",
            footer = "部分定制系统在手机熄屏后会强制休眠进程，设置为「无限制」可确保锁屏熄屏听书持续稳定运行"
        ) {
            SettingRow(
                title = "后台持续听书保活",
                subtitle = if (isIgnoringBattery) "已获取电池无限制白名单，锁屏熄屏不中断"
                else "当前未开启白名单，熄屏易被系统误杀，点此配置",
                icon = Icons.Rounded.Cached,
                onClick = onRequestBatteryWhitelist,
                trailing = {
                    Surface(
                        shape = CircleShape,
                        color = if (isIgnoringBattery) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = if (isIgnoringBattery) "已就绪" else "去开启",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isIgnoringBattery) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            )
        }

        GlassSectionCard(
            title = "关于",
            footer = "菠萝蜜小说 v${BuildConfig.VERSION_NAME} · 菠萝包阅读 + AI 伴读续写 · 隐私数据仅存本机"
        ) {
            SettingRow(
                title = "手势提示",
                subtitle = "阅读页点按中间唤出菜单；长按段落「划线即问」",
                icon = Icons.Rounded.ColorLens
            )
            HorizontalDivider(
                Modifier.padding(start = 52.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            SettingRow(
                title = "液态玻璃引擎",
                subtitle = HonorGlassDiagnostics.summary(),
                icon = Icons.Rounded.AutoAwesome
            )
        }
    }
}

/**
 * 平板双栏未选中特定提供商时右侧展示的：AI 模型配置快捷概览与任务分流工作台。
 */
@Composable
private fun SettingsOverviewDetailPane(
    providers: List<ApiProviderConfig>,
    continuationRoute: ModelRoute,
    companionRoute: ModelRoute,
    appTheme: AppThemeMode,
    onAddProvider: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onConfigureRoute: (TaskScene) -> Unit,
    contentPadding: PaddingValues
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val activeText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val enabledCount = providers.count { it.isEnabled }
    val contProvider = providers.firstOrNull { it.id == continuationRoute.providerId }
    val compProvider = providers.firstOrNull { it.id == companionRoute.providerId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // AI 模型配置快捷入口卡
        GlassSectionCard(
            title = "模型配置工作台",
            contentPadding = PaddingValues(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (providers.isEmpty()) "尚未配置 AI 模型服务" else "已连接 $enabledCount / ${providers.size} 项模型服务",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = activeText
                )
                Text(
                    text = when (appTheme) {
                        AppThemeMode.DARK -> "当前外观与阅读背景同步为深色模式"
                        AppThemeMode.LIGHT -> "当前外观与阅读背景同步为浅色模式"
                        AppThemeMode.FOLLOW_SYSTEM -> "当前外观与阅读背景跟随系统深浅色"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    GlassCapsuleButton(
                        text = if (providers.isEmpty()) "立即添加模型服务" else "新增模型服务",
                        onClick = onAddProvider,
                        filled = true,
                        icon = Icons.Rounded.Add
                    )
                    if (providers.isNotEmpty()) {
                        GlassCapsuleButton(
                            text = "编辑首项配置",
                            onClick = { onSelectProvider(providers.first().id) },
                            filled = false,
                            icon = Icons.Rounded.Tune
                        )
                    }
                }
            }
        }

        // AI 任务分流状态卡
        GlassSectionCard(
            title = "AI 任务分流概览",
            footer = "为长篇续写与伴读解析分别指派最合适的模型，支持点击右侧按钮即时切换",
            contentPadding = PaddingValues(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("续写与剧情分支", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = "${contProvider?.providerName ?: "未指派服务"} · ${continuationRoute.model.ifBlank { contProvider?.selectedModel ?: "未选模型" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    GlassCapsuleButton(
                        text = "配置模型",
                        onClick = { onConfigureRoute(TaskScene.CONTINUATION) },
                        filled = false
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("伴读与划词解析", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = "${compProvider?.providerName ?: "未指派服务"} · ${companionRoute.model.ifBlank { compProvider?.selectedModel ?: "未选模型" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    GlassCapsuleButton(
                        text = "配置模型",
                        onClick = { onConfigureRoute(TaskScene.COMPANION) },
                        filled = false
                    )
                }
            }
        }

        // BYOK 快速配置与安全说明
        GlassSectionCard(
            title = "BYOK 安全与快速上手 · 已启用 $enabledCount / ${providers.size} 项",
            contentPadding = PaddingValues(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        "纯本地密钥架构：所有 API Key 均通过 Android Keystore 加密存储，直连厂商官方或自建兼容网关。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (providers.isNotEmpty()) {
                    Text(
                        "在左侧点击任意提供商即可在此直接编辑参数、拉取模型或测试连通性。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteEditDialog(
    scene: TaskScene,
    providers: List<ApiProviderConfig>,
    current: ModelRoute,
    routeModels: List<String>,
    loadingModels: Boolean,
    routeModelError: String?,
    onGetRecommendedModels: (String) -> List<String>,
    onFetchModels: (String) -> Unit,
    onClearModels: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    var providerId by remember(scene, current.providerId) {
        mutableStateOf(current.providerId ?: providers.firstOrNull()?.id ?: "")
    }
    val selectedProvider = providers.firstOrNull { it.id == providerId }
    var model by remember(scene, current.providerId) {
        mutableStateOf(current.model.ifBlank { selectedProvider?.selectedModel ?: "" })
    }
    var filterText by remember(scene, current.providerId) { mutableStateOf("") }
    val recs = remember(providerId) { onGetRecommendedModels(providerId) }

    AdaptiveGlassDialog(
        onDismissRequest = onDismiss,
        maxWidth = adaptive.maxDialogSheetWidth
    ) {
        Text(
            "配置${scene.label}模型",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (providers.isEmpty()) {
                Text("请先在「模型与 API」中添加并启用提供商", color = MaterialTheme.colorScheme.error)
            }

            Text("选择服务商 / API Key", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                providers.forEach { p ->
                    FilterChip(
                        selected = p.id == providerId,
                        onClick = {
                            providerId = p.id
                            onClearModels()
                            filterText = ""
                            if (p.selectedModel.isNotBlank()) model = p.selectedModel
                        },
                        label = { Text(p.providerName) }
                    )
                }
            }

            if (recs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("推荐模型：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        recs.forEach { candidate ->
                            AssistChip(
                                onClick = { model = candidate },
                                label = { Text(candidate, style = MaterialTheme.typography.labelSmall) },
                                colors = if (model == candidate) {
                                    AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                } else {
                                    AssistChipDefaults.assistChipColors()
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("当前模型 ID") },
                singleLine = true,
                trailingIcon = {
                    if (loadingModels) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { onFetchModels(providerId) }) { Text("拉取模型") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (routeModelError != null) {
                Text(
                    routeModelError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (routeModels.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        placeholder = { Text("搜索拉取的 ${routeModels.size} 个模型…") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )
                    val filtered = remember(routeModels, filterText) {
                        if (filterText.isBlank()) routeModels
                        else routeModels.filter { it.contains(filterText, ignoreCase = true) }
                    }
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(filtered, key = { it }) { m ->
                            val isSelected = model == m
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { model = m },
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = m,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) { Text("取消") }
            Spacer(Modifier.width(8.dp))
            GlassCapsuleButton(
                text = "保存配置",
                onClick = { onSave(providerId, model) },
                enabled = providerId.isNotBlank() && model.isNotBlank()
            )
        }
    }
}
