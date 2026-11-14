package com.novelai.assistant.ui.continuation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.novelai.assistant.ui.components.OriginBadge

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun ContinuationScreen(
    bookId: String,
    parentChapterId: String,
    mode: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ContinuationViewModel = hiltViewModel()
) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val parent by viewModel.parentChapter.collectAsStateWithLifecycle()
    val instruction by viewModel.instruction.collectAsStateWithLifecycle()
    val customTitle by viewModel.customTitle.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val useCraft by viewModel.useWorkshopCraft.collectAsStateWithLifecycle()
    val allChapters by viewModel.allChapters.collectAsStateWithLifecycle()
    val continuousCount by viewModel.continuousCount.collectAsStateWithLifecycle()
    val latestCh by viewModel.latestChapter.collectAsStateWithLifecycle()
    val useLatest by viewModel.useLatestChapter.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var showChapterPicker by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(if (viewModel.isBranchMode) "剧情分支 · What-if" else "AI 续写")
                    Text(
                        "《${book?.title ?: ""}》",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (continuousCount > 0) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "连续创作中 · 已连续生成并保存 $continuousCount 章，当前正基于最新章节衔接！",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 续写模式：一键选择「基于全书最新章」或「指定历史章节」
            if (!viewModel.isBranchMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = useLatest,
                        onClick = { viewModel.setUseLatestChapter(true) },
                        label = {
                            Text(
                                if (latestCh != null) "⚡ 全书最新章（第${latestCh!!.chapterIndex + 1}章）"
                                else "⚡ 直接基于最新章续写"
                            )
                        },
                        leadingIcon = if (useLatest) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.weight(1.3f)
                    )
                    FilterChip(
                        selected = !useLatest,
                        onClick = {
                            viewModel.setUseLatestChapter(false)
                            showChapterPicker = true
                        },
                        label = { Text("指定章节") },
                        leadingIcon = if (!useLatest) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            // 基准章节卡（可切换）
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                    OriginBadge(
                        if (viewModel.isBranchMode) "AI_BRANCH" else parent?.originType?.name ?: "ORIGINAL"
                    )
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            parent?.title ?: "加载中…",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            (parent?.content ?: "").takeLast(160),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (viewModel.isBranchMode) "剧情从这一章分岔推演"
                            else if (useLatest) "⚡ 自动追踪全书最新末尾无缝续写"
                            else "正文从这一章末尾续写",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = { showChapterPicker = true }) {
                        Text("切换", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // 指令输入
            OutlinedTextField(
                value = instruction,
                onValueChange = { viewModel.instruction.value = it },
                placeholder = {
                    Text(
                        if (viewModel.isBranchMode)
                            "输入假设：例如「如果主角没有救女二，剧情会怎样？」"
                        else "补充要求（可留空）：例如「重点写主角与反派的正面冲突」"
                    )
                },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            // 生成 / 停止
            when {
                streaming -> {
                    OutlinedButton(
                        onClick = { viewModel.stop() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, Modifier.height(18.dp))
                        Text("  停止生成")
                    }
                }
                !finished -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "小说工坊技法",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = useCraft,
                                onCheckedChange = { viewModel.toggleWorkshopCraft() }
                            )
                        }
                        Text(
                            "开启后按网文创作法则生成：展示而非讲述、潜台词对话、章末钩子、去AI味",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.generate()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, Modifier.height(18.dp))
                            Text(if (viewModel.isBranchMode) "  推演平行分支" else "  开始续写")
                        }
                    }
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // 流式输出
            if (streamingText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(16.dp)
                            )
                            Text(
                                if (streaming) " 生成中…" else " 生成完成",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (streaming) {
                                CircularProgressIndicator(
                                    Modifier.padding(start = 8.dp).height(12.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(streamingText, style = MaterialTheme.typography.bodyLarge)

                        if (finished) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "保存已生成内容",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customTitle,
                                onValueChange = { viewModel.customTitle.value = it },
                                label = { Text("章节标题（可自定义，留空自动命名）") },
                                placeholder = {
                                    Text(
                                        if (viewModel.isBranchMode) "分支：${instruction.take(10).ifBlank { "平行世界" }}"
                                        else "AI 续写 · ${parent?.title?.take(12) ?: "新章"}"
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.saveAndContinue(autoGenerateNext = false)
                                    },
                                    modifier = Modifier.weight(1.3f)
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, Modifier.height(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("保存并续写下一章", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.save(onSaved)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("保存并退出", maxLines = 1)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                if (!viewModel.isBranchMode) {
                                    OutlinedButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.appendToParentChapter(onSaved)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("追加到本章末", maxLines = 1) }
                                }
                                OutlinedButton(
                                    onClick = { viewModel.reset() },
                                    modifier = Modifier.weight(1f)
                                ) { Text("重写本章", maxLines = 1) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    // 基准章节选择
    if (showChapterPicker) {
        AlertDialog(
            onDismissRequest = { showChapterPicker = false },
            title = { Text("选择基准章节") },
            text = {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    item {
                        TextButton(
                            onClick = {
                                viewModel.selectLatestChapter()
                                showChapterPicker = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "⚡ 全书最新章（第${(allChapters.maxByOrNull { it.chapterIndex }?.chapterIndex ?: 0) + 1}章 · 自动追踪）",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    items(allChapters) { ch ->
                        TextButton(
                            onClick = {
                                viewModel.selectParentChapter(ch)
                                showChapterPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${ch.chapterIndex + 1}. ${ch.title}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChapterPicker = false }) { Text("取消") }
            }
        )
    }
}
