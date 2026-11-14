package com.novelai.assistant.ui.discover

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.sf.BookExportFormat
import com.novelai.assistant.data.sf.SfDownloadTask
import com.novelai.assistant.data.sf.SfSafetyState
import com.novelai.assistant.ui.components.AdaptiveGlassDialog
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassFilterPill
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.SensitiveScreen
import com.novelai.assistant.ui.theme.NovelColors
import kotlinx.coroutines.delay

@Composable
internal fun SfLoginDialog(
    onDismiss: () -> Unit,
    protection: SfSafetyState = SfSafetyState(),
    onLogin: (String, String) -> Unit
) {
    SensitiveScreen()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val clock by produceState(System.currentTimeMillis(), protection.until) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val canLogin = !protection.blocked || (protection.requiresLogin && clock >= protection.until)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val titleColor = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText

    AdaptiveGlassDialog(
        onDismissRequest = { password = ""; onDismiss() }
    ) {
        SensitiveScreen()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "登录菠萝包",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            Text(
                text = "使用菠萝包账号或手机号与密码登录。若账号需要验证码或安全验证，请先在官方 App 完成。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("账号 / 手机号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "密码仅用于本次登录请求，不会保存。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "通过非官方协议接入，平台可能要求验证或限制账号；本应用不能承诺避免封号。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!canLogin) {
                Text(
                    text = "请求保护仍在生效，请返回处理暂停原因并等待冷却结束。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassCapsuleButton(
                    text = "取消",
                    onClick = { password = ""; onDismiss() },
                    filled = false,
                    modifier = Modifier.weight(1f)
                )
                GlassCapsuleButton(
                    text = "登录",
                    onClick = {
                        val value = password
                        password = ""
                        onLogin(username, value)
                    },
                    filled = true,
                    enabled = canLogin && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.weight(1.2f)
                )
            }
        }
    }
}

@Composable
fun SfTaskCard(
    task: SfDownloadTask,
    safety: SfSafetyState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAcknowledgeCooldown: () -> Unit,
    onForceBypassSafety: () -> Unit
) {
    if (task.chapterIds.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryColor = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText

    GlassSectionCard(
        title = "下载任务 · ${task.title}",
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SfSafetyNotice(safety, onAcknowledgeCooldown, onForceBypassSafety)
            Text(
                text = "${task.message} · ${task.completed}/${task.chapterIds.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = primaryColor
            )
            if (task.running) {
                LinearProgressIndicator(
                    progress = { (task.completed + task.failures.size).toFloat() / task.chapterIds.size },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.running) {
                    GlassCapsuleButton(
                        text = "暂停下载",
                        onClick = onPause,
                        filled = false
                    )
                } else {
                    GlassCapsuleButton(
                        text = if (task.paused) "继续下载" else "重试未完成章节",
                        onClick = onResume,
                        filled = true
                    )
                }
                if (task.failures.isNotEmpty()) {
                    TextButton({ expanded = !expanded }) {
                        Text("缺章清单（${task.failures.size}）")
                    }
                }
            }
            if (expanded) {
                task.failures.entries.take(50).forEach { (id, error) ->
                    Text(
                        text = "章节 ${id.substringAfterLast(':')}：$error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (task.failures.size > 50) {
                    Text(
                        text = "共 ${task.failures.size} 条，导出缺章说明会保留完整清单",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class ExportChoice(val format: BookExportFormat, val ids: List<String>, val partial: Boolean, val name: String)

@Composable
fun SfBookToolsDialog(
    bookId: String,
    currentChapterIndex: Int,
    onDismiss: () -> Unit,
    viewModel: SfBookToolsViewModel = hiltViewModel(key = "sf-tools")
) {
    LaunchedEffect(bookId) { viewModel.selectLocal(bookId) }
    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()
    val task by viewModel.task.collectAsStateWithLifecycle()
    val safety by viewModel.safety.collectAsStateWithLifecycle()
    var from by remember(bookId, currentChapterIndex) { mutableStateOf((currentChapterIndex + 1).toString()) }
    var to by remember(bookId, currentChapterIndex) { mutableStateOf((currentChapterIndex + 1).toString()) }
    var pending by remember { mutableStateOf<ExportChoice?>(null) }
    var confirmPartial by remember { mutableStateOf<ExportChoice?>(null) }

    fun result(uri: Uri?) {
        val choice = pending
        pending = null
        if (uri != null && choice != null) {
            viewModel.export(uri, choice.format, choice.ids, choice.partial)
        }
    }

    val txt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain"), ::result)
    val md = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown"), ::result)
    val epub = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/epub+zip"), ::result)

    fun launch(choice: ExportChoice) {
        pending = choice
        when (choice.format) {
            BookExportFormat.TXT -> txt.launch(choice.name)
            BookExportFormat.MARKDOWN -> md.launch(choice.name)
            BookExportFormat.EPUB -> epub.launch(choice.name)
        }
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val titleColor = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val accentColor = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary

    AdaptiveGlassDialog(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "下载与导出",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            Text(
                text = "${book?.title.orEmpty()} · ${chapters.size} 章",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = from,
                    onValueChange = { from = it },
                    label = { Text("起始章") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("结束章") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isCurrentChapter = from == "${currentChapterIndex + 1}" && to == from
                val isFullBook = from == "1" && to == chapters.size.toString() && chapters.isNotEmpty()
                GlassFilterPill(
                    label = "本章",
                    selected = isCurrentChapter,
                    onClick = { from = "${currentChapterIndex + 1}"; to = from }
                )
                GlassFilterPill(
                    label = "全书",
                    selected = isFullBook,
                    onClick = { from = "1"; to = chapters.size.toString() }
                )
            }
            GlassCapsuleButton(
                text = "下载所选章节供离线阅读",
                onClick = { viewModel.download(from.toIntOrNull() ?: 0, to.toIntOrNull() ?: 0) },
                filled = true,
                enabled = !task.running && chapters.isNotEmpty(),
                icon = Icons.Rounded.Download,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "只获取免费及账号已购正文。已下载章节会被复用；登录失效时任务暂停。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SfTaskCard(
                task = task,
                safety = safety,
                onPause = { viewModel.downloads.pause() },
                onResume = { viewModel.downloads.resume() },
                onAcknowledgeCooldown = { viewModel.acknowledgeCooldown() },
                onForceBypassSafety = { viewModel.forceBypassSafety() }
            )
            Text(
                text = "导出所选范围",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookExportFormat.entries.forEach { format ->
                    GlassCapsuleButton(
                        text = format.extension.uppercase(),
                        filled = false,
                        enabled = !exporting && !task.running && pending == null,
                        onClick = {
                            val begin = from.toIntOrNull() ?: 0
                            val end = to.toIntOrNull() ?: 0
                            if (begin < 1 || end < begin || end > chapters.size) {
                                viewModel.showError("章节范围无效")
                                return@GlassCapsuleButton
                            }
                            val selected = chapters.subList(begin - 1, end)
                            if (selected.none { it.content.isNotBlank() }) {
                                viewModel.showError("请先下载所选章节")
                                return@GlassCapsuleButton
                            }
                            val partial = selected.any { it.content.isBlank() }
                            val name = "${book?.title.orEmpty().replace(Regex("[\\\\/:*?\"<>|]"), "_")}_${begin}-${end}${if (partial) "_不完整" else ""}.${format.extension}"
                            val choice = ExportChoice(format, selected.map { it.id }, partial, name)
                            if (partial) confirmPartial = choice else launch(choice)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
            successMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = accentColor) }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Spacer(Modifier.weight(1f))
                GlassCapsuleButton(
                    text = "关闭",
                    onClick = onDismiss,
                    filled = false
                )
            }
        }
    }

    confirmPartial?.let { choice ->
        val missing = chapters.filter { it.id in choice.ids && it.content.isBlank() }
        AdaptiveGlassDialog(
            onDismissRequest = { confirmPartial = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "所选范围缺少 ${missing.size} 章",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "仅导出已取得正文的章节，并在文件中附上缺章说明。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    missing.take(20).forEach {
                        Text(
                            text = "${it.chapterIndex + 1}. ${it.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCapsuleButton(
                        text = "返回下载",
                        onClick = { confirmPartial = null },
                        filled = false,
                        modifier = Modifier.weight(1f)
                    )
                    GlassCapsuleButton(
                        text = "导出不完整版本",
                        onClick = { confirmPartial = null; launch(choice) },
                        filled = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }
    }
}
