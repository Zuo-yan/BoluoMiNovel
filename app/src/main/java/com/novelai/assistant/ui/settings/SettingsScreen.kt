package com.novelai.assistant.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.prefs.AppThemeMode
import com.novelai.assistant.data.repository.TaskScene
import com.novelai.assistant.ui.components.LargeTitleHeader
import com.novelai.assistant.ui.components.SectionCard
import com.novelai.assistant.ui.components.SettingRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onEditProvider: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val continuationRoute by viewModel.continuationRoute.collectAsStateWithLifecycle()
    val companionRoute by viewModel.companionRoute.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val appTheme by viewModel.appThemeMode.collectAsStateWithLifecycle()
    val routeModels by viewModel.routeModels.collectAsStateWithLifecycle()
    val loadingRouteModels by viewModel.loadingRouteModels.collectAsStateWithLifecycle()

    var routeScene by remember { mutableStateOf<TaskScene?>(null) }
    var confirmResetUsage by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = com.novelai.assistant.ui.components.bottomBarContentPadding())
    ) {
        LargeTitleHeader(title = "设置", subtitle = "BYOK 模式 · 密钥仅存本地加密空间")

        SectionCard(
            title = "模型与 API",
            footer = "支持 Chat Completions、Responses、Anthropic Messages 与 Gemini；密钥经 Android Keystore 加密存储"
        ) {
            providers.forEach { config ->
                val isDefault = continuationRoute.providerId == config.id && companionRoute.providerId == config.id
                SettingRow(
                    title = config.providerName,
                    subtitle = "${config.selectedModel.ifBlank { "未选模型" }} · ${config.baseUrl}",
                    icon = Icons.Rounded.Key,
                    onClick = { onEditProvider(config.id) },
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
                                        viewModel.setDefaultProvider(config)
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text("设为默认", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = config.isEnabled,
                                onCheckedChange = { viewModel.toggleEnabled(config, it) }
                            )
                        }
                    }
                )
            }
            SettingRow(
                title = "添加提供商",
                subtitle = "九家厂商预设 · 自定义 / 本地服务",
                icon = Icons.Rounded.Add,
                onClick = { onEditProvider("new") }
            )
        }

        Spacer(Modifier.height(20.dp))

        SectionCard(title = "任务分流", footer = "为不同任务指定不同模型，兼顾成本与效果") {
            val contProvider = providers.firstOrNull { it.id == continuationRoute.providerId }
            val compProvider = providers.firstOrNull { it.id == companionRoute.providerId }
            SettingRow(
                title = "续写场景",
                subtitle = TaskScene.CONTINUATION.hint,
                icon = Icons.Rounded.SwapHoriz,
                onClick = { routeScene = TaskScene.CONTINUATION },
                trailing = {
                    Text(
                        continuationRoute.model.ifBlank { contProvider?.selectedModel ?: "未配置" },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )
            SettingRow(
                title = "速览与伴读场景",
                subtitle = TaskScene.COMPANION.hint,
                icon = Icons.Rounded.Chat,
                onClick = { routeScene = TaskScene.COMPANION },
                trailing = {
                    Text(
                        companionRoute.model.ifBlank { compProvider?.selectedModel ?: "未配置" },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }

        Spacer(Modifier.height(20.dp))

        SectionCard(title = "用量统计", footer = "Token 用量为本地累计估算") {
            SettingRow(
                title = "累计输入 Token",
                icon = Icons.Rounded.Cached,
                trailing = { Text("${usage.promptTokens}", style = MaterialTheme.typography.labelLarge) }
            )
            SettingRow(
                title = "累计输出 Token",
                icon = Icons.Rounded.Cached,
                trailing = { Text("${usage.completionTokens}", style = MaterialTheme.typography.labelLarge) }
            )
            SettingRow(
                title = "清空统计",
                icon = Icons.Rounded.Delete,
                onClick = { confirmResetUsage = true }
            )
        }

        Spacer(Modifier.height(20.dp))

        SectionCard(title = "外观") {
            SettingRow(
                title = "主题模式",
                icon = Icons.Rounded.ColorLens,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = appTheme == AppThemeMode.FOLLOW_SYSTEM,
                            onClick = { viewModel.setAppTheme(AppThemeMode.FOLLOW_SYSTEM) },
                            label = { Text("跟随系统") }
                        )
                        FilterChip(
                            selected = appTheme == AppThemeMode.LIGHT,
                            onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.setAppTheme(AppThemeMode.LIGHT) },
                            label = { Text("浅色") }
                        )
                        FilterChip(
                            selected = appTheme == AppThemeMode.DARK,
                            onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.setAppTheme(AppThemeMode.DARK) },
                            label = { Text("深色") }
                        )
                    }
                }
            )
        }

        Spacer(Modifier.height(20.dp))

        val screenContext = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        var isIgnoringBattery by remember {
            mutableStateOf(
                com.novelai.assistant.data.tts.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(screenContext)
            )
        }
        DisposableEffect(lifecycleOwner, screenContext) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isIgnoringBattery =
                        com.novelai.assistant.data.tts.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(screenContext)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        SectionCard(
            title = "系统与后台听书",
            footer = "部分定制系统在手机熄屏后会强制休眠进程，设置为「无限制」可确保锁屏熄屏听书持续稳定运行"
        ) {
            SettingRow(
                title = "后台持续听书保活",
                subtitle = if (isIgnoringBattery) "已获取电池无限制白名单，锁屏熄屏不中断" else "当前未开启白名单，熄屏易被系统误杀，点此配置",
                icon = Icons.Rounded.Cached,
                onClick = {
                    com.novelai.assistant.data.tts.BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(screenContext)
                },
                trailing = {
                    Surface(
                        shape = CircleShape,
                        color = if (isIgnoringBattery) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = if (isIgnoringBattery) "已就绪" else "去开启",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isIgnoringBattery) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            )
        }

        Spacer(Modifier.height(20.dp))

        SectionCard(title = "关于", footer = "菠萝蜜小说 v${com.novelai.assistant.BuildConfig.VERSION_NAME} · 菠萝包阅读 + AI 伴读续写 · 隐私数据仅存本机") {
            SettingRow(
                title = "手势提示",
                subtitle = "阅读页点按中间唤出菜单；长按段落「划线即问」",
                icon = Icons.Rounded.ColorLens
            )
        }
    }

    // 清空用量统计确认弹窗
    if (confirmResetUsage) {
        AlertDialog(
            onDismissRequest = { confirmResetUsage = false },
            title = { Text("清空用量统计？") },
            text = { Text("确定要重置当前设备记录的累计输入与输出 Token 统计吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmResetUsage = false
                        viewModel.resetUsage()
                    }
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetUsage = false }) {
                    Text("取消")
                }
            }
        )
    }

    val routeModelError by viewModel.routeModelError.collectAsStateWithLifecycle()

    // 任务分流配置弹窗
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

@Composable
private fun RouteEditDialog(
    scene: TaskScene,
    providers: List<com.novelai.assistant.data.repository.ApiProviderConfig>,
    current: com.novelai.assistant.data.repository.ModelRoute,
    routeModels: List<String>,
    loadingModels: Boolean,
    routeModelError: String?,
    onGetRecommendedModels: (String) -> List<String>,
    onFetchModels: (String) -> Unit,
    onClearModels: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var providerId by remember(scene, current.providerId) {
        mutableStateOf(current.providerId ?: providers.firstOrNull()?.id ?: "")
    }
    val selectedProvider = providers.firstOrNull { it.id == providerId }
    var model by remember(scene, current.providerId) {
        mutableStateOf(current.model.ifBlank { selectedProvider?.selectedModel ?: "" })
    }
    var filterText by remember(scene, current.providerId) { mutableStateOf("") }
    val recs = remember(providerId) { onGetRecommendedModels(providerId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置${scene.label}模型") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (providers.isEmpty()) {
                    Text("请先在「模型与 API」中添加并启用提供商", color = MaterialTheme.colorScheme.error)
                }

                // 提供商选择
                Text("选择服务商 / API Key", style = MaterialTheme.typography.labelMedium)
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

                // 推荐常用模型
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
                                    } else AssistChipDefaults.assistChipColors()
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

                // 拉取到的模型列表及过滤搜索
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
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
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
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(providerId, model) },
                enabled = providerId.isNotBlank() && model.isNotBlank()
            ) { Text("保存配置") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
