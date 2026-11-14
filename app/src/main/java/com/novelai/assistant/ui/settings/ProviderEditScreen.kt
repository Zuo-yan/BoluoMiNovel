package com.novelai.assistant.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.network.ProviderKind
import com.novelai.assistant.data.network.ProviderPlatform
import com.novelai.assistant.data.network.ProviderPreset
import com.novelai.assistant.data.network.ProviderPresets
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.AdaptiveGlassDialog
import com.novelai.assistant.ui.components.AdaptiveGlassSheet
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassCircleButton
import com.novelai.assistant.ui.components.GlassGradientBar
import com.novelai.assistant.ui.components.GlassGradientDirection
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.SensitiveScreen
import com.novelai.assistant.ui.theme.NovelColors
import java.text.DateFormat
import java.util.Date

@Composable
fun ProviderEditScreen(
    providerId: String,
    onBack: () -> Unit,
    viewModel: ProviderEditViewModel = hiltViewModel()
) {
    SensitiveScreen()
    val adaptive = LocalAdaptiveMetrics.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var requestDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GlassGradientBar(
                direction = GlassGradientDirection.TOP_TO_BOTTOM,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassCircleButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                        size = 40.dp
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (state.isNew) "添加模型服务" else "编辑模型服务",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.config.providerName.isNotBlank()) {
                            Text(
                                text = state.config.providerName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (!state.isNew) {
                        GlassCircleButton(
                            icon = Icons.Rounded.Delete,
                            contentDescription = "删除配置",
                            onClick = { requestDelete = true },
                            size = 40.dp,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    GlassCapsuleButton(
                        text = "保存",
                        onClick = { viewModel.save { onBack() } },
                        enabled = state.headerError == null
                    )
                }
            }
        }
    ) { padding ->
        AdaptiveCenteredContainer(
            maxWidth = adaptive.maxFormWidth,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            ProviderEditContent(
                providerId = providerId,
                onSaved = { onBack() },
                onDeleted = onBack,
                showInlineHeader = false,
                externalConfirmDelete = requestDelete,
                onDismissExternalDelete = { requestDelete = false },
                contentPadding = PaddingValues(
                    start = adaptive.horizontalPadding,
                    end = adaptive.horizontalPadding,
                    top = 10.dp,
                    bottom = 28.dp
                ),
                viewModel = viewModel
            )
        }
    }
}

/**
 * 可复用的 BYOK 提供商配置编辑表单（同时驱动独立路由页与平板 Settings List-Detail 右侧详情面板）。
 */
@Composable
fun ProviderEditContent(
    providerId: String,
    onSaved: (String) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    showInlineHeader: Boolean = false,
    onCloseInline: (() -> Unit)? = null,
    externalConfirmDelete: Boolean = false,
    onDismissExternalDelete: () -> Unit = {},
    loadForceKey: Any? = null,
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp),
    viewModel: ProviderEditViewModel = hiltViewModel()
) {
    SensitiveScreen()
    val adaptive = LocalAdaptiveMetrics.current
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    LaunchedEffect(providerId, loadForceKey) {
        viewModel.load(providerId, force = loadForceKey != null)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val config = state.config

    var chooseProvider by remember { mutableStateOf(false) }
    var pendingPreset by remember { mutableStateOf<ProviderPreset?>(null) }
    var chooseProtocol by remember { mutableStateOf(false) }
    var modelPicker by remember { mutableStateOf(false) }
    var revealKey by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var internalConfirmDelete by remember { mutableStateOf(false) }

    val confirmDelete = externalConfirmDelete || internalConfirmDelete
    val dismissDelete = {
        internalConfirmDelete = false
        onDismissExternalDelete()
    }

    val preset = ProviderPresets.items.firstOrNull { it.id == config.presetId }
    val platform = preset?.platforms?.firstOrNull { it.id == config.platformId }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showInlineHeader) {
            LiquidGlassBox(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                widgetSize = GlassWidgetSize.BIG,
                sceneType = GlassSceneType.GENERAL,
                interactive = false,
                skipRenderLayer = true,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (state.isNew) "添加模型服务" else "编辑 · ${config.providerName.ifBlank { "未命名配置" }}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "连接你的模型服务 · 密钥仅存本机 Keystore",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (onCloseInline != null) {
                        GlassCircleButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "返回概览",
                            onClick = onCloseInline,
                            size = 38.dp
                        )
                    }
                    if (!state.isNew) {
                        GlassCircleButton(
                            icon = Icons.Rounded.Delete,
                            contentDescription = "删除配置",
                            onClick = { internalConfirmDelete = true },
                            size = 38.dp,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    GlassCapsuleButton(
                        text = "保存",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.save(onSaved)
                        },
                        enabled = state.headerError == null
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "连接你的模型服务",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "选择厂商与平台，填写密钥，再选择模型。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        GlassSectionCard(
            title = "服务与平台",
            contentPadding = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { chooseProvider = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = preset?.name ?: if (state.isNew) "选择厂商" else "已有自定义配置",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = platform?.label ?: "明确选择本次连接的平台／地域",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                }

                OutlinedTextField(
                    value = config.providerName,
                    onValueChange = { value -> viewModel.update { it.copy(providerName = value) } },
                    label = { Text("配置名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { chooseProtocol = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Text(
                        text = config.kind.label,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                }

                OutlinedTextField(
                    value = config.baseUrl,
                    onValueChange = { value -> viewModel.update { it.copy(baseUrl = value.trim()) } },
                    label = { Text("API 基础地址（Base URL）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "协议须与服务地址对应；同一厂商不同平台使用各自的 Key。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = config.apiKey,
                    onValueChange = { value -> viewModel.update { it.copy(apiKey = value.trim()) } },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { revealKey = !revealKey }) {
                            Icon(
                                if (revealKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                if (revealKey) "隐藏密钥" else "显示密钥"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "密钥经 Android Keystore 加密保存在本机，直连目标接口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        GlassSectionCard(
            title = "模型与输出",
            contentPadding = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = config.selectedModel,
                    onValueChange = { value -> viewModel.update { it.copy(selectedModel = value.trim()) } },
                    label = { Text("模型 ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassCapsuleButton(
                        text = if (state.loadingModels) "正在获取模型…" else "获取可用模型",
                        onClick = { viewModel.fetchModels() },
                        filled = false,
                        enabled = !state.loadingModels && config.baseUrl.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    )
                    if (state.loadingModels) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("自动使用最大输出", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("识别模型上限后自动填写", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(config.autoMaxTokens, { viewModel.setAuto(it) })
                }

                OutlinedTextField(
                    value = state.maxTokensDraft,
                    onValueChange = { viewModel.setMaxTokens(it) },
                    label = { Text("单次最大输出 Token") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("直接修改会切换为手动；上限不代表必定生成这么多正文。") },
                    modifier = Modifier.fillMaxWidth()
                )

                val cap = config.capability?.takeIf { it.matches(config) }
                Surface(
                    color = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            "上下文容量：${cap?.context?.let { "%,d".format(it) } ?: "未知"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "最大输出：${cap?.output?.let { "%,d".format(it) } ?: "未知"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.loadingCapability) "正在识别模型能力…"
                            else cap?.source ?: "选择模型后识别；未识别时保留当前值",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        cap?.checkedAt?.takeIf { it > 0 }?.let { time ->
                            Text(
                                "更新于 ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { viewModel.detectCapability(true) },
                            enabled = !state.loadingCapability && config.selectedModel.isNotBlank(),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("重新识别")
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { advanced = !advanced }) {
                Text(if (advanced) "收起高级参数" else "展开高级参数")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("启用该服务", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Switch(config.isEnabled, { value -> viewModel.update { it.copy(isEnabled = value) } })
            }
        }

        if (advanced) {
            GlassSectionCard(
                title = "高级参数",
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("发送 Temperature", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                        Switch(config.temperatureEnabled, { value -> viewModel.update { it.copy(temperatureEnabled = value) } })
                    }
                    if (config.temperatureEnabled) {
                        Text(
                            "Temperature ${"%.2f".format(config.temperature)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = config.temperature,
                            onValueChange = { value -> viewModel.update { it.copy(temperature = value) } },
                            valueRange = 0f..2f
                        )
                    }
                    if (config.kind == ProviderKind.OPENAI_COMPATIBLE) {
                        Text("Chat 输出参数", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        listOf(
                            "auto" to "自动（官方 OpenAI 使用 max_completion_tokens）",
                            "max_tokens" to "max_tokens · 兼容服务",
                            "max_completion_tokens" to "max_completion_tokens"
                        ).forEach { (value, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.update { it.copy(chatTokenField = value) } }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = config.chatTokenField == value,
                                    onClick = { viewModel.update { it.copy(chatTokenField = value) } }
                                )
                                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = state.headerDraft,
                        onValueChange = { viewModel.updateCustomHeadersJson(it) },
                        label = { Text("自定义请求头（JSON）") },
                        isError = state.headerError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.headerError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        GlassSectionCard(
            title = "连通性诊断",
            footer = "连接测试只读取模型接口；生成能力以实际伴读或续写为准。",
            contentPadding = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassCapsuleButton(
                    text = if (state.pingState is PingUiState.Testing) "正在测试连接…" else "测试模型接口连接",
                    onClick = { viewModel.testConnection() },
                    filled = true,
                    enabled = state.pingState !is PingUiState.Testing && config.baseUrl.isNotBlank(),
                    icon = Icons.Rounded.AutoAwesome,
                    modifier = Modifier.fillMaxWidth()
                )
                state.pingState?.let { ping ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (ping) {
                            is PingUiState.Ok -> {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = NovelColors.Mint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "模型接口连接成功 · ${ping.latencyMs}ms",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDark) NovelColors.Mint else Color(0xFF00877F)
                                )
                            }
                            is PingUiState.Fail -> {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    ping.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            PingUiState.Testing -> {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("正在测试…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    // 厂商选择 Sheet
    if (chooseProvider) {
        AdaptiveGlassSheet(
            onDismissRequest = { chooseProvider = false },
            maxWidth = adaptive.maxDialogSheetWidth
        ) {
            Text(
                "选择厂商",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                items(ProviderPresets.items, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.name, color = MaterialTheme.colorScheme.onSurface) },
                        supportingContent = {
                            Text(
                                "${item.platforms.size} 个可选入口",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                chooseProvider = false
                                pendingPreset = item
                            }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            }
        }
    }

    // 平台与地域选择弹窗
    pendingPreset?.let { selected ->
        PlatformDialog(
            preset = selected,
            onDismiss = { pendingPreset = null },
            onConfirm = { selectedPlatform, workspace ->
                viewModel.applyPreset(selected, selectedPlatform, workspace)
                pendingPreset = null
            }
        )
    }

    // 协议选择弹窗
    if (chooseProtocol) {
        AdaptiveGlassDialog(
            onDismissRequest = { chooseProtocol = false },
            maxWidth = adaptive.maxDialogSheetWidth
        ) {
            Text(
                "选择 API 协议",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            ProviderKind.entries.forEach { kind ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.update { it.copy(kind = kind) }
                            chooseProtocol = false
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = kind == config.kind,
                        onClick = {
                            viewModel.update { it.copy(kind = kind) }
                            chooseProtocol = false
                        }
                    )
                    Text(kind.label, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { chooseProtocol = false }) { Text("关闭") }
            }
        }
    }

    LaunchedEffect(state.modelsRequest) {
        if (state.models.isNotEmpty()) modelPicker = true
    }

    // 模型选择 Sheet
    if (modelPicker) {
        AdaptiveGlassSheet(
            onDismissRequest = { modelPicker = false },
            maxWidth = adaptive.maxDialogSheetWidth
        ) {
            var filter by remember { mutableStateOf("") }
            Text(
                "选择模型 · ${state.models.size}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("筛选模型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            val filtered = remember(state.models, filter) {
                if (filter.isBlank()) state.models else state.models.filter { it.contains(filter, ignoreCase = true) }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                items(filtered, key = { it }) { model ->
                    val selected = model == config.selectedModel
                    ListItem(
                        headlineContent = {
                            Text(
                                model,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.update { it.copy(selectedModel = model) }
                                modelPicker = false
                            }
                    )
                }
            }
        }
    }

    // 删除确认弹窗
    if (confirmDelete) {
        AdaptiveGlassDialog(
            onDismissRequest = dismissDelete,
            maxWidth = adaptive.maxDialogSheetWidth
        ) {
            Text(
                "删除模型配置？",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "指向此配置的任务分流也会清除。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = dismissDelete) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        dismissDelete()
                        viewModel.delete(onDeleted)
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PlatformDialog(
    preset: ProviderPreset,
    onDismiss: () -> Unit,
    onConfirm: (ProviderPlatform, String) -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    var selected by remember(preset.id) { mutableStateOf<ProviderPlatform?>(preset.platforms.firstOrNull()) }
    var workspace by remember(preset.id) { mutableStateOf("") }

    AdaptiveGlassDialog(
        onDismissRequest = onDismiss,
        maxWidth = adaptive.maxDialogSheetWidth
    ) {
        Text(
            "${preset.name} · 选择平台／地域",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            preset.platforms.forEach { platform ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selected = platform }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected?.id == platform.id, onClick = { selected = platform })
                    Text(platform.label, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (selected?.workspace == true) {
                OutlinedTextField(
                    value = workspace,
                    onValueChange = { workspace = it },
                    label = { Text("Workspace ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            selected?.let {
                Text(
                    it.kind.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) { Text("取消") }
            Spacer(Modifier.width(8.dp))
            GlassCapsuleButton(
                text = "使用此入口",
                onClick = { selected?.let { onConfirm(it, workspace) } },
                enabled = selected != null && (selected?.workspace != true || workspace.matches(Regex("[A-Za-z0-9-]+")))
            )
        }
    }
}
