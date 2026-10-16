package com.novelai.assistant.ui.discover

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.ui.components.LargeTitleHeader
import com.novelai.assistant.ui.components.SectionCard

@Composable
fun DiscoverScreen(onOpenBook: (String) -> Unit, onOpenCatalog: (String) -> Unit, onOpenFanqie: () -> Unit) {
    SfScreen(onOpenBook, onOpenCatalog, onOpenFanqie)
}

@Composable
fun FanqieScreen(onBack: () -> Unit, viewModel: DiscoverViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.setSource(FetchSource.FANQIE) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val progress by viewModel.importProgress.collectAsStateWithLifecycle()
    val limit by viewModel.maxChapters.collectAsStateWithLifecycle()
    val relay by viewModel.relay.collectAsStateWithLifecycle()
    var relayDraft by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onBack, modifier = Modifier.padding(horizontal = 8.dp)) { Text("‹ 返回菠萝包") }
        LargeTitleHeader("番茄小说", subtitle = "获取目录 · 下载到本地书架")
        SectionCard(title = "书源") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(input, { viewModel.input.value = it }, label = { Text("番茄书号或书籍链接") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button({ viewModel.fetch() }, enabled = state !is DiscoverUiState.Loading && progress == null, modifier = Modifier.fillMaxWidth()) { Text("获取目录") }
                Text("番茄正文依赖第三方中转：$relay", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton({ relayDraft = relay }) { Text("修改地址") }
                    TextButton({ viewModel.resetRelay() }) { Text("恢复默认") }
                }
            }
        }
        when (val s = state) {
            DiscoverUiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            is DiscoverUiState.Loaded -> SectionCard(title = s.book.title) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${s.book.author} · ${s.book.chapters.size} 章")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "全部", 20 to "前20章", 50 to "前50章").forEach { (n, text) -> FilterChip(limit == n, { viewModel.setMaxChapters(n) }, label = { Text(text) }) }
                    }
                    if (progress != null) {
                        Text("正在获取第 ${progress!! + 1} 章")
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        OutlinedButton({ viewModel.cancelImport() }) { Text("取消导入") }
                    } else Button({ viewModel.import() }, modifier = Modifier.fillMaxWidth()) { Text("下载并导入书架") }
                }
            }
            is DiscoverUiState.Imported -> Text("《${s.title}》已导入 ${s.chapters} 章", Modifier.padding(16.dp))
            is DiscoverUiState.Error -> Text(s.message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            DiscoverUiState.Idle -> Text("输入书号获取目录，再下载到本地阅读", Modifier.padding(16.dp))
        }
    }
    relayDraft?.let { draft -> AlertDialog(onDismissRequest = { relayDraft = null }, title = { Text("番茄中转 API") },
        text = { OutlinedTextField(draft, { relayDraft = it }, singleLine = true) },
        confirmButton = { TextButton({ viewModel.setRelay(draft); relayDraft = null }) { Text("保存") } },
        dismissButton = { TextButton({ relayDraft = null }) { Text("取消") } }) }
}
