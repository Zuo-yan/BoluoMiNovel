package com.novelai.assistant.ui.discover

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.sf.*
import com.novelai.assistant.ui.components.LargeTitleHeader
import com.novelai.assistant.ui.components.SectionCard

@Composable
internal fun SfLoginDialog(onDismiss: () -> Unit, protection: SfSafetyState = SfSafetyState(), onLogin: (String, String) -> Unit) {
    com.novelai.assistant.ui.components.SensitiveScreen()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val clock by produceState(System.currentTimeMillis(), protection.until) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) }
    }
    val canLogin = !protection.blocked || (protection.requiresLogin && clock >= protection.until)
    AlertDialog(onDismissRequest = { password = ""; onDismiss() }, title = { Text("登录菠萝包") },
        properties = androidx.compose.ui.window.DialogProperties(securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn),
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("使用菠萝包账号或手机号与密码登录。若账号需要验证码或安全验证，请先在官方 App 完成。")
                OutlinedTextField(username, { username = it }, label = { Text("账号 / 手机号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("密码") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                Text("密码仅用于本次登录请求，不会保存。", style = MaterialTheme.typography.bodySmall)
                Text("通过非官方协议接入，平台可能要求验证或限制账号；本应用不能承诺避免封号。", style = MaterialTheme.typography.bodySmall)
                if (!canLogin) Text("请求保护仍在生效，请返回处理暂停原因并等待冷却结束。", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = canLogin && username.isNotBlank() && password.isNotBlank(), onClick = {
            val value = password; password = ""; onLogin(username, value)
        }) { Text("登录") } },
        dismissButton = { TextButton({ password = ""; onDismiss() }) { Text("取消") } })
}

@Composable
fun SfTaskCard(viewModel: SfViewModel) {
    val task by viewModel.task.collectAsStateWithLifecycle()
    if (task.chapterIds.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    SectionCard(title = "下载任务 · ${task.title}") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SfSafetyNotice(viewModel)
            Text("${task.message} · ${task.completed}/${task.chapterIds.size}")
            if (task.running) LinearProgressIndicator(progress = { (task.completed + task.failures.size).toFloat() / task.chapterIds.size }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.running) OutlinedButton({ viewModel.downloads.pause() }) { Text("暂停下载") }
                else OutlinedButton({ viewModel.downloads.resume() }) { Text(if (task.paused) "继续下载" else "重试未完成章节") }
                if (task.failures.isNotEmpty()) TextButton({ expanded = !expanded }) { Text("缺章清单（${task.failures.size}）") }
            }
            if (expanded) {
                task.failures.entries.take(50).forEach { (id, error) -> Text("章节 ${id.substringAfterLast(':')}：$error", style = MaterialTheme.typography.bodySmall) }
                if (task.failures.size > 50) Text("共 ${task.failures.size} 条，导出缺章说明会保留完整清单")
            }
        }
    }
}

private data class ExportChoice(val format: BookExportFormat, val ids: List<String>, val partial: Boolean, val name: String)

@Composable
fun SfBookToolsDialog(bookId: String, currentChapterIndex: Int, onDismiss: () -> Unit, viewModel: SfViewModel = hiltViewModel(key = "sf-tools")) {
    LaunchedEffect(bookId) { viewModel.selectLocal(bookId) }
    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val task by viewModel.task.collectAsStateWithLifecycle()
    var from by remember(bookId, currentChapterIndex) { mutableStateOf((currentChapterIndex + 1).toString()) }
    var to by remember(bookId, currentChapterIndex) { mutableStateOf((currentChapterIndex + 1).toString()) }
    var pending by remember { mutableStateOf<ExportChoice?>(null) }
    var confirmPartial by remember { mutableStateOf<ExportChoice?>(null) }
    fun result(uri: android.net.Uri?) { val choice = pending; pending = null; if (uri != null && choice != null) viewModel.export(uri, choice.format, choice.ids, choice.partial) }
    val txt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain"), ::result)
    val md = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown"), ::result)
    val epub = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/epub+zip"), ::result)
    fun launch(choice: ExportChoice) {
        pending = choice
        when (choice.format) { BookExportFormat.TXT -> txt.launch(choice.name); BookExportFormat.MARKDOWN -> md.launch(choice.name); BookExportFormat.EPUB -> epub.launch(choice.name) }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("下载与导出") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${book?.title.orEmpty()} · ${chapters.size} 章")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, { from = it }, label = { Text("起始章") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(to, { to = it }, label = { Text("结束章") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            Row {
                TextButton({ from = "${currentChapterIndex + 1}"; to = from }) { Text("本章") }
                TextButton({ from = "1"; to = chapters.size.toString() }) { Text("全书") }
            }
            Button({ viewModel.download(from.toIntOrNull() ?: 0, to.toIntOrNull() ?: 0) }, enabled = !task.running && chapters.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("下载所选章节供离线阅读") }
            Text("只获取免费及账号已购正文。已下载章节会被复用；登录失效时任务暂停。", style = MaterialTheme.typography.bodySmall)
            SfTaskCard(viewModel)
            Text("导出所选范围", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BookExportFormat.entries.forEach { format ->
                    OutlinedButton(enabled = !state.busy && !task.running && pending == null, onClick = {
                        val begin = from.toIntOrNull() ?: 0; val end = to.toIntOrNull() ?: 0
                        if (begin < 1 || end < begin || end > chapters.size) { viewModel.showError("章节范围无效"); return@OutlinedButton }
                        val selected = chapters.subList(begin - 1, end)
                        if (selected.none { it.content.isNotBlank() }) { viewModel.showError("请先下载所选章节"); return@OutlinedButton }
                        val partial = selected.any { it.content.isBlank() }
                        val name = "${book?.title.orEmpty().replace(Regex("[\\\\/:*?\"<>|]"), "_")}_${begin}-${end}${if (partial) "_不完整" else ""}.${format.extension}"
                        val choice = ExportChoice(format, selected.map { it.id }, partial, name)
                        if (partial) confirmPartial = choice else launch(choice)
                    }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(format.extension.uppercase()) }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("关闭") } })
    confirmPartial?.let { choice ->
        val missing = chapters.filter { it.id in choice.ids && it.content.isBlank() }
        AlertDialog(onDismissRequest = { confirmPartial = null }, title = { Text("所选范围缺少 ${missing.size} 章") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("仅导出已取得正文的章节，并在文件中附上缺章说明。")
                missing.take(20).forEach { Text("${it.chapterIndex + 1}. ${it.title}", style = MaterialTheme.typography.bodySmall) }
            } }, confirmButton = { TextButton({ confirmPartial = null; launch(choice) }) { Text("导出不完整版本") } },
            dismissButton = { TextButton({ confirmPartial = null }) { Text("返回下载") } })
    }
}
