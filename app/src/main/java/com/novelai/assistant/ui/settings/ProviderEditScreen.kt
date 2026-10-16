package com.novelai.assistant.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.network.*
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(providerId: String, onBack: () -> Unit, viewModel: ProviderEditViewModel = hiltViewModel()) {
    LaunchedEffect(providerId) { viewModel.load(providerId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val config = state.config
    var chooseProvider by remember { mutableStateOf(false) }
    var pendingPreset by remember { mutableStateOf<ProviderPreset?>(null) }
    var chooseProtocol by remember { mutableStateOf(false) }
    var modelPicker by remember { mutableStateOf(false) }
    var revealKey by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var headerDraft by remember(config.id) { mutableStateOf(buildJsonObject { config.customHeaders.forEach { (k,v) -> put(k,v) } }.toString()) }
    var headerError by remember { mutableStateOf<String?>(null) }
    val preset = ProviderPresets.items.firstOrNull { it.id == config.presetId }
    val platform = preset?.platforms?.firstOrNull { it.id == config.platformId }
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (state.isNew) "添加模型服务" else "编辑模型服务") }, navigationIcon = {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
        }, actions = {
            if (!state.isNew) IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.Delete, "删除配置") }
            TextButton({ if (headerError == null) viewModel.save(onBack) }, enabled = headerError == null) { Text("保存") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("连接你的模型服务", style = MaterialTheme.typography.headlineSmall)
            Text("选择厂商与平台，填写密钥，再选择模型。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsGroup("服务与平台") {
                OutlinedButton({ chooseProvider = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(preset?.name ?: if (state.isNew) "选择厂商" else "已有自定义配置")
                        Text(platform?.label ?: "明确选择本次连接的平台／地域", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Rounded.KeyboardArrowDown, null)
                }
                OutlinedTextField(config.providerName, { value -> viewModel.update { it.copy(providerName = value) } }, label = { Text("配置名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton({ chooseProtocol = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Text(config.kind.label, Modifier.weight(1f)); Icon(Icons.Rounded.KeyboardArrowDown, null)
                }
                OutlinedTextField(config.baseUrl, { value -> viewModel.update { it.copy(baseUrl = value.trim()) } }, label = { Text("API 基础地址（Base URL）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("协议须与服务地址对应；同一厂商不同平台使用各自的 Key。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(config.apiKey, { value -> viewModel.update { it.copy(apiKey = value.trim()) } }, label = { Text("API Key") }, singleLine = true,
                    visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), trailingIcon = {
                        IconButton({ revealKey = !revealKey }) { Icon(if (revealKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (revealKey) "隐藏密钥" else "显示密钥") }
                    }, modifier = Modifier.fillMaxWidth())
                Text("密钥经 Keystore 加密保存在本机。", style = MaterialTheme.typography.bodySmall)
            }
            SettingsGroup("模型与输出") {
                OutlinedTextField(config.selectedModel, { value -> viewModel.update { it.copy(selectedModel = value.trim()) } }, label = { Text("模型 ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton({ viewModel.fetchModels() }, enabled = !state.loadingModels && config.baseUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    if (state.loadingModels) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(if (state.loadingModels) "  正在获取模型…" else "获取可用模型")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("自动使用最大输出", style = MaterialTheme.typography.titleSmall); Text("识别模型上限后自动填写", style = MaterialTheme.typography.bodySmall) }
                    Switch(config.autoMaxTokens, { viewModel.setAuto(it) })
                }
                OutlinedTextField(state.maxTokensDraft, { viewModel.setMaxTokens(it) }, label = { Text("单次最大输出 Token") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("直接修改会切换为手动；上限不代表必定生成这么多正文。") }, modifier = Modifier.fillMaxWidth())
                val cap = config.capability?.takeIf { it.matches(config) }
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("上下文容量：${cap?.context?.let { "%,d".format(it) } ?: "未知"}", style = MaterialTheme.typography.bodyMedium)
                        Text("最大输出：${cap?.output?.let { "%,d".format(it) } ?: "未知"}", style = MaterialTheme.typography.bodyMedium)
                        Text(if (state.loadingCapability) "正在识别模型能力…" else cap?.source ?: "选择模型后识别；未识别时保留当前值", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        cap?.checkedAt?.takeIf { it > 0 }?.let { time -> Text("更新于 ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(time))}", style = MaterialTheme.typography.labelSmall) }
                        TextButton({ viewModel.detectCapability(true) }, enabled = !state.loadingCapability && config.selectedModel.isNotBlank()) { Text("重新识别") }
                    }
                }
            }
            TextButton({ advanced = !advanced }) { Text(if (advanced) "收起高级参数" else "高级参数") }
            if (advanced) SettingsGroup("高级参数") {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("发送 Temperature", Modifier.weight(1f)); Switch(config.temperatureEnabled, { value -> viewModel.update { it.copy(temperatureEnabled = value) } }) }
                if (config.temperatureEnabled) {
                    Text("Temperature ${"%.2f".format(config.temperature)}")
                    Slider(config.temperature, { value -> viewModel.update { it.copy(temperature = value) } }, valueRange = 0f..2f)
                }
                if (config.kind == ProviderKind.OPENAI_COMPATIBLE) {
                    Text("Chat 输出参数", style = MaterialTheme.typography.titleSmall)
                    listOf("auto" to "自动（官方 OpenAI 使用 max_completion_tokens）", "max_tokens" to "max_tokens · 兼容服务", "max_completion_tokens" to "max_completion_tokens").forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth().clickable { viewModel.update { it.copy(chatTokenField = value) } }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(config.chatTokenField == value, { viewModel.update { it.copy(chatTokenField = value) } }); Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                OutlinedTextField(headerDraft, { value ->
                    headerDraft = value
                    try {
                        val headers = Json.parseToJsonElement(value.ifBlank { "{}" }).jsonObject.mapValues { (_,v) -> require(v is JsonPrimitive && v.isString); v.content }
                        viewModel.update { it.copy(customHeaders = headers) }; headerError = null
                    } catch (_: Exception) { headerError = "请使用字符串键值的 JSON 对象" }
                }, label = { Text("自定义请求头（JSON）") }, isError = headerError != null, modifier = Modifier.fillMaxWidth())
                headerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("启用该服务", Modifier.weight(1f)); Switch(config.isEnabled, { value -> viewModel.update { it.copy(isEnabled = value) } }) }
            OutlinedButton({ viewModel.testConnection() }, enabled = state.pingState !is PingUiState.Testing && config.baseUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("测试模型接口连接") }
            Text("连接测试只读取模型接口；生成能力以实际伴读或续写为准。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.pingState?.let { ping -> Text(when (ping) { is PingUiState.Ok -> "模型接口连接成功 · ${ping.latencyMs}ms"; is PingUiState.Fail -> ping.message; PingUiState.Testing -> "正在测试…" }) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (chooseProvider) ModalBottomSheet(onDismissRequest = { chooseProvider = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
            item { Text("选择厂商", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp)) }
            items(ProviderPresets.items, key = { it.id }) { item -> ListItem(headlineContent = { Text(item.name) }, modifier = Modifier.clickable {
                chooseProvider = false; pendingPreset = item
            }) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    pendingPreset?.let { selected -> PlatformDialog(selected, { pendingPreset = null }) { platform, workspace ->
        viewModel.applyPreset(selected, platform, workspace); headerDraft = "{}"; headerError = null; pendingPreset = null
    } }
    if (chooseProtocol) AlertDialog(onDismissRequest = { chooseProtocol = false }, title = { Text("选择 API 协议") }, text = {
        Column { ProviderKind.entries.forEach { kind ->
            Row(Modifier.fillMaxWidth().clickable { viewModel.update { it.copy(kind = kind) }; chooseProtocol = false }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(kind == config.kind, { viewModel.update { it.copy(kind = kind) }; chooseProtocol = false }); Text(kind.label)
            }
        } }
    }, confirmButton = { TextButton({ chooseProtocol = false }) { Text("关闭") } })
    LaunchedEffect(state.modelsRequest) { if (state.models.isNotEmpty()) modelPicker = true }
    if (modelPicker) ModalBottomSheet(onDismissRequest = { modelPicker = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        var filter by remember { mutableStateOf("") }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("选择模型 · ${state.models.size}", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(filter, { filter = it }, label = { Text("筛选模型") }, modifier = Modifier.fillMaxWidth())
            LazyColumn { items(state.models.filter { it.contains(filter, true) }, key = { it }) { model ->
                ListItem(headlineContent = { Text(model) }, modifier = Modifier.clickable { viewModel.update { it.copy(selectedModel = model) }; modelPicker = false })
            } }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除模型配置？") }, text = { Text("指向此配置的任务分流也会清除。") },
        confirmButton = { TextButton({ viewModel.delete(onBack) }) { Text("删除") } }, dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } })
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun PlatformDialog(preset: ProviderPreset, onDismiss: () -> Unit, onConfirm: (ProviderPlatform, String) -> Unit) {
    var selected by remember(preset.id) { mutableStateOf<ProviderPlatform?>(null) }
    var workspace by remember(preset.id) { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("${preset.name} · 选择平台／地域") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            preset.platforms.forEach { platform -> Row(Modifier.fillMaxWidth().clickable { selected = platform }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected?.id == platform.id, { selected = platform }); Text(platform.label)
            } }
            if (selected?.workspace == true) OutlinedTextField(workspace, { workspace = it }, label = { Text("Workspace ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            selected?.let { Text(it.kind.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }, confirmButton = { TextButton({ selected?.let { onConfirm(it, workspace) } }, enabled = selected != null && (selected?.workspace != true || workspace.matches(Regex("[A-Za-z0-9-]+")))) { Text("使用此入口") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } })
}
