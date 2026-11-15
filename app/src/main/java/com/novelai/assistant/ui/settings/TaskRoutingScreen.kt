package com.novelai.assistant.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.network.ModelMetadataClient
import com.novelai.assistant.data.network.ProviderKind
import com.novelai.assistant.data.repository.ApiProviderConfig
import com.novelai.assistant.data.repository.ModelRoute
import com.novelai.assistant.data.repository.TaskScene
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.AdaptiveGlassDialog
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassCircleButton
import com.novelai.assistant.ui.components.GlassGradientBar
import com.novelai.assistant.ui.components.GlassGradientDirection
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.SettingRow
import com.novelai.assistant.ui.components.adaptiveContentPadding

/**
 * 任务分流独立二级菜单（手机端全屏下钻页面）
 */
@Composable
fun TaskRoutingScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val adaptive = LocalAdaptiveMetrics.current
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
                            text = "AI 模型任务分流",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "按创作、伴读、向量与听书场景指派最合适的模型",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    ) { padding ->
        AdaptiveCenteredContainer(
            maxWidth = adaptive.maxFormWidth,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            TaskRoutingContent(
                viewModel = viewModel,
                showInlineHeader = false,
                contentPadding = adaptiveContentPadding(top = 8.dp, bottomExtra = 20.dp)
            )
        }
    }
}

/**
 * 任务分流配置内容面板：同时复用于手机二级页与平板双栏右侧内联工作台
 */
@Composable
fun TaskRoutingContent(
    viewModel: SettingsViewModel = hiltViewModel(),
    showInlineHeader: Boolean = false,
    onCloseInline: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val outlineRoute by viewModel.outlineRoute.collectAsStateWithLifecycle()
    val continuationRoute by viewModel.continuationRoute.collectAsStateWithLifecycle()
    val companionRoute by viewModel.companionRoute.collectAsStateWithLifecycle()
    val embeddingRoute by viewModel.embeddingRoute.collectAsStateWithLifecycle()
    val ttsRoute by viewModel.ttsRoute.collectAsStateWithLifecycle()

    val embeddingTestState by viewModel.embeddingTestState.collectAsStateWithLifecycle()
    val ttsTestState by viewModel.ttsTestState.collectAsStateWithLifecycle()
    val routeModels by viewModel.routeModels.collectAsStateWithLifecycle()
    val loadingRouteModels by viewModel.loadingRouteModels.collectAsStateWithLifecycle()
    val routeModelError by viewModel.routeModelError.collectAsStateWithLifecycle()

    var routeScene by remember { mutableStateOf<TaskScene?>(null) }

    val outlineProvider = providers.firstOrNull { it.id == outlineRoute.providerId && it.isEnabled }
    val contProvider = providers.firstOrNull { it.id == continuationRoute.providerId }
    val compProvider = providers.firstOrNull { it.id == companionRoute.providerId }
    val embProvider = providers.firstOrNull { it.id == embeddingRoute.providerId && it.isEnabled }
    val ttsProvider = providers.firstOrNull { it.id == ttsRoute.providerId && it.isEnabled }

    val contModelLabel = continuationRoute.model.ifBlank { contProvider?.selectedModel ?: "未配置" }
    val outlineActiveLabel = if (outlineProvider != null) {
        "${outlineProvider.providerName} · ${outlineRoute.model.ifBlank { outlineProvider.selectedModel }}"
    } else {
        "跟随续写 · $contModelLabel"
    }
    val embActiveLabel = if (embProvider != null && embeddingRoute.model.isNotBlank()) {
        "${embProvider.providerName} · ${embeddingRoute.model}"
    } else {
        "未启用（仅段落 BM25）"
    }
    val ttsActiveLabel = if (ttsProvider != null && ttsRoute.model.isNotBlank()) {
        "${ttsProvider.providerName} · ${ttsRoute.model}"
    } else {
        "未启用（默认系统语音）"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (showInlineHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "AI 模型任务分流",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "为创作、检索与听书语音分别指定最佳模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onCloseInline != null) {
                    GlassCircleButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "关闭分流面板",
                        onClick = onCloseInline,
                        size = 38.dp
                    )
                }
            }
        }

        GlassSectionCard(
            title = "创作与伴读模型",
            footer = "大纲策划可单独指派高阶推理模型；正文续写与伴读归档各司其职，兼顾质量与成本"
        ) {
            SettingRow(
                title = TaskScene.OUTLINE.label,
                subtitle = TaskScene.OUTLINE.hint,
                icon = Icons.Rounded.Tune,
                onClick = { routeScene = TaskScene.OUTLINE },
                trailing = {
                    RouteValueText(
                        text = outlineActiveLabel,
                        highlighted = outlineProvider != null
                    )
                }
            )
            HorizontalDivider(
                Modifier.padding(start = 52.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            SettingRow(
                title = TaskScene.CONTINUATION.label,
                subtitle = TaskScene.CONTINUATION.hint,
                icon = Icons.Rounded.SwapHoriz,
                onClick = { routeScene = TaskScene.CONTINUATION },
                trailing = {
                    RouteValueText(
                        text = contModelLabel,
                        highlighted = contProvider != null
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
                onClick = { routeScene = TaskScene.COMPANION },
                trailing = {
                    RouteValueText(
                        text = companionRoute.model.ifBlank { compProvider?.selectedModel ?: "未配置" },
                        highlighted = compProvider != null
                    )
                }
            )
        }

        GlassSectionCard(
            title = "检索与语音增强（可选）",
            footer = "留空时分别默认使用纯本地 BM25 检索与设备系统语音，不产生额外 API 费用；支持 DeepSeek（/v1/tools/tts）、Google Gemini TTS 及 OpenAI /audio/speech 兼容网关"
        ) {
            SettingRow(
                title = TaskScene.EMBEDDING.label,
                subtitle = TaskScene.EMBEDDING.hint,
                icon = Icons.Rounded.AutoAwesome,
                onClick = { routeScene = TaskScene.EMBEDDING },
                trailing = {
                    RouteValueText(
                        text = embActiveLabel,
                        highlighted = embProvider != null && embeddingRoute.model.isNotBlank()
                    )
                }
            )
            HorizontalDivider(
                Modifier.padding(start = 52.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            SettingRow(
                title = TaskScene.TTS.label,
                subtitle = TaskScene.TTS.hint,
                icon = Icons.Rounded.GraphicEq,
                onClick = { routeScene = TaskScene.TTS },
                trailing = {
                    RouteValueText(
                        text = ttsActiveLabel,
                        highlighted = ttsProvider != null && ttsRoute.model.isNotBlank()
                    )
                }
            )
        }
    }

    routeScene?.let { scene ->
        val currentRoute = when (scene) {
            TaskScene.OUTLINE -> outlineRoute
            TaskScene.CONTINUATION -> continuationRoute
            TaskScene.COMPANION -> companionRoute
            TaskScene.EMBEDDING -> embeddingRoute
            TaskScene.TTS -> ttsRoute
        }
        RouteEditDialog(
            scene = scene,
            providers = providers,
            current = currentRoute,
            routeModels = routeModels,
            loadingModels = loadingRouteModels,
            routeModelError = routeModelError,
            embeddingTestState = embeddingTestState,
            ttsTestState = ttsTestState,
            onGetRecommendedModels = { viewModel.getRecommendedModels(it, scene) },
            onFetchModels = { viewModel.fetchModelsFor(it, scene) },
            onClearModels = { viewModel.clearRouteModels() },
            onTestEmbedding = { pid, m -> viewModel.testEmbedding(pid, m) },
            onClearEmbeddingTest = { viewModel.clearEmbeddingTest() },
            onTestTts = { pid, m -> viewModel.testTts(pid, m) },
            onClearTtsTest = { viewModel.clearTtsTest() },
            onClearRoute = {
                viewModel.clearRoute(scene)
                viewModel.clearRouteModels()
                viewModel.clearEmbeddingTest()
                viewModel.clearTtsTest()
                routeScene = null
            },
            onDismiss = {
                viewModel.clearRouteModels()
                viewModel.clearEmbeddingTest()
                viewModel.clearTtsTest()
                routeScene = null
            },
            onSave = { providerId, model ->
                viewModel.saveRoute(scene, providerId, model)
                viewModel.clearRouteModels()
                viewModel.clearEmbeddingTest()
                viewModel.clearTtsTest()
                routeScene = null
            }
        )
    }
}

@Composable
internal fun RouteEditDialog(
    scene: TaskScene,
    providers: List<ApiProviderConfig>,
    current: ModelRoute,
    routeModels: List<String>,
    loadingModels: Boolean,
    routeModelError: String?,
    embeddingTestState: EmbeddingTestUiState,
    ttsTestState: TtsTestUiState,
    onGetRecommendedModels: (String) -> List<String>,
    onFetchModels: (String) -> Unit,
    onClearModels: () -> Unit,
    onTestEmbedding: (String, String) -> Unit,
    onClearEmbeddingTest: () -> Unit,
    onTestTts: (String, String) -> Unit,
    onClearTtsTest: () -> Unit,
    onClearRoute: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    val isEmbeddingScene = scene == TaskScene.EMBEDDING
    val isTtsScene = scene == TaskScene.TTS
    val isSpecializedScene = isEmbeddingScene || isTtsScene

    var providerId by remember(scene, current.providerId) {
        mutableStateOf(
            current.providerId ?: providers.firstOrNull {
                !isSpecializedScene || it.kind != ProviderKind.ANTHROPIC
            }?.id ?: providers.firstOrNull()?.id ?: ""
        )
    }
    val selectedProvider = providers.firstOrNull { it.id == providerId }
    var model by remember(scene, current.providerId) {
        mutableStateOf(
            if (isSpecializedScene) current.model
            else current.model.ifBlank { selectedProvider?.selectedModel ?: "" }
        )
    }
    var filterText by remember(scene, current.providerId) { mutableStateOf("") }
    val recs = remember(providerId, scene) { onGetRecommendedModels(providerId) }

    val clearSpecializedTests = {
        if (isEmbeddingScene) onClearEmbeddingTest()
        if (isTtsScene) onClearTtsTest()
    }

    AdaptiveGlassDialog(
        onDismissRequest = onDismiss,
        maxWidth = adaptive.maxDialogSheetWidth
    ) {
        Text(
            "配置${scene.label}模型",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        when {
            isEmbeddingScene -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    "配置后将自动开启「段落 BM25 + Embedding 混合重排」；不配置或关闭时仅使用纯本地段落级 BM25，不额外扣费。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            isTtsScene -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    "支持 DeepSeek 官方语音合成（/v1/tools/tts，填 deepseek-tts，音色 stella/tide）、Google Gemini TTS（gemini-3.8-flash-tts / gemini-2.5-flash-preview-tts）及 OpenAI /v1/audio/speech 兼容网关。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            scene == TaskScene.OUTLINE -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    "建议指定逻辑推理与长线结构把控能力最强的高阶模型（如 Claude Opus/Sonnet、GPT-5、Gemini Pro、DeepSeek-R1），专门负责「剧情走向讨论」与「长期大纲整理」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (providers.isEmpty()) {
                Text("请先在「模型与 API」中添加并启用提供商", color = MaterialTheme.colorScheme.error)
            }

            Text(
                "选择服务商 / API Key",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                            clearSpecializedTests()
                            filterText = ""
                            if (!isSpecializedScene && p.selectedModel.isNotBlank()) {
                                model = p.selectedModel
                            }
                        },
                        label = { Text(p.providerName) }
                    )
                }
            }

            if (isSpecializedScene && selectedProvider?.kind == ProviderKind.ANTHROPIC) {
                Text(
                    text = if (isEmbeddingScene) {
                        "提示：Anthropic 协议暂不提供向量接口，请选择 OpenAI 兼容 / Gemini / 硅基流动 / Ollama 等提供商。"
                    } else {
                        "提示：Anthropic 协议暂不提供语音合成接口，请选择 Google Gemini 或 OpenAI 兼容网关。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (isTtsScene && selectedProvider != null &&
                ModelMetadataClient.isDeepSeekProvider(selectedProvider)
            ) {
                Text(
                    "提示：DeepSeek 官方 TTS 为工具端点（/v1/tools/tts），不参与 /models 列表；模型 ID 保持 deepseek-tts 即可（支持 stella 女声 / tide 男声）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (recs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        when {
                            isEmbeddingScene -> "推荐向量模型："
                            isTtsScene -> "推荐语音模型："
                            else -> "推荐模型："
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        recs.forEach { candidate ->
                            AssistChip(
                                onClick = {
                                    model = candidate
                                    clearSpecializedTests()
                                },
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
                onValueChange = {
                    model = it
                    clearSpecializedTests()
                },
                label = {
                    Text(
                        when {
                            isEmbeddingScene -> "向量模型 ID（如 text-embedding-3-small）"
                            isTtsScene -> "语音模型 ID（如 gemini-3.8-flash-tts / tts-1）"
                            else -> "当前模型 ID"
                        }
                    )
                },
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
                                    .clickable {
                                        model = m
                                        clearSpecializedTests()
                                    },
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

            if (isEmbeddingScene) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = { onTestEmbedding(providerId, model) },
                        enabled = providerId.isNotBlank() && model.isNotBlank() && embeddingTestState !is EmbeddingTestUiState.Testing
                    ) {
                        Text(if (embeddingTestState is EmbeddingTestUiState.Testing) "测试中…" else "测试向量")
                    }
                    when (val st = embeddingTestState) {
                        EmbeddingTestUiState.Idle -> Unit
                        EmbeddingTestUiState.Testing -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        is EmbeddingTestUiState.Ok -> Text(
                            "可用（${st.dimensions} 维 · ${st.latencyMs}ms）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        is EmbeddingTestUiState.Fail -> Text(
                            st.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2
                        )
                    }
                }
            } else if (isTtsScene) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = { onTestTts(providerId, model) },
                        enabled = providerId.isNotBlank() &&
                            model.isNotBlank() &&
                            selectedProvider?.kind != ProviderKind.ANTHROPIC &&
                            ttsTestState !is TtsTestUiState.Testing
                    ) {
                        Text(if (ttsTestState is TtsTestUiState.Testing) "合成中…" else "测试语音")
                    }
                    when (val st = ttsTestState) {
                        TtsTestUiState.Idle -> Unit
                        TtsTestUiState.Testing -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        is TtsTestUiState.Ok -> Text(
                            "合成成功（${st.latencyMs}ms）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        is TtsTestUiState.Fail -> Text(
                            st.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2
                        )
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
            if (isEmbeddingScene && current.model.isNotBlank()) {
                TextButton(onClick = onClearRoute) {
                    Text("关闭向量", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
            } else if (isTtsScene && current.model.isNotBlank()) {
                TextButton(onClick = onClearRoute) {
                    Text("关闭 AI 语音", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
            } else if (scene == TaskScene.OUTLINE && !current.providerId.isNullOrBlank()) {
                TextButton(onClick = onClearRoute) {
                    Text("跟随续写", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
            }
            TextButton(onClick = onDismiss) { Text("取消") }
            Spacer(Modifier.width(8.dp))
            GlassCapsuleButton(
                text = "保存配置",
                onClick = { onSave(providerId, model) },
                enabled = providerId.isNotBlank() && model.isNotBlank() &&
                    (!isSpecializedScene || selectedProvider?.kind != ProviderKind.ANTHROPIC)
            )
        }
    }
}

@Composable
internal fun RouteValueText(text: String, highlighted: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
        modifier = Modifier.widthIn(max = 160.dp)
    )
}
