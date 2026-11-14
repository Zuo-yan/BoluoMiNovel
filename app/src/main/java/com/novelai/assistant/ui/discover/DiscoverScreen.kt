package com.novelai.assistant.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.AdaptiveGlassDialog
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassCircleButton
import com.novelai.assistant.ui.components.GlassFilterPill
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.LargeTitleHeader
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.theme.NovelColors

@Composable
fun DiscoverScreen(
    onOpenBook: (String) -> Unit,
    onOpenCatalog: (String) -> Unit,
    onOpenFanqie: () -> Unit,
    onOpenNovelBrowse: () -> Unit,
    onOpenComics: () -> Unit,
    onOpenRecommendations: () -> Unit
) {
    SfScreen(onOpenBook, onOpenCatalog, onOpenFanqie, onOpenNovelBrowse, onOpenComics, onOpenRecommendations)
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

    val adaptive = LocalAdaptiveMetrics.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val accentText = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = navBottom + 24.dp)
        ) {
            AdaptiveCenteredContainer(
                maxWidth = adaptive.maxFormWidth,
                horizontalPadding = adaptive.horizontalPadding
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassCircleButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回菠萝包",
                            onClick = onBack
                        )
                        LargeTitleHeader(
                            title = "番茄小说",
                            subtitle = "获取目录 · 下载到本地书架",
                            horizontalPadding = 0.dp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    GlassSectionCard(
                        title = "书源",
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { viewModel.input.value = it },
                                label = { Text("番茄书号或书籍链接") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            GlassCapsuleButton(
                                text = "获取目录",
                                onClick = { viewModel.fetch() },
                                filled = true,
                                enabled = state !is DiscoverUiState.Loading && progress == null,
                                icon = Icons.Rounded.Search,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "番茄正文依赖第三方中转：$relay",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryText
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassCapsuleButton(
                                    text = "修改地址",
                                    onClick = { relayDraft = relay },
                                    filled = false
                                )
                                GlassCapsuleButton(
                                    text = "恢复默认",
                                    onClick = { viewModel.resetRelay() },
                                    filled = false
                                )
                            }
                        }
                    }

                    when (val s = state) {
                        DiscoverUiState.Loading -> {
                            GlassSectionCard(contentPadding = PaddingValues(16.dp)) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                        is DiscoverUiState.Loaded -> {
                            GlassSectionCard(
                                title = s.book.title,
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "${s.book.author} · ${s.book.chapters.size} 章",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = primaryText
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(0 to "全部", 20 to "前20章", 50 to "前50章").forEach { (n, text) ->
                                            GlassFilterPill(
                                                label = text,
                                                selected = limit == n,
                                                onClick = { viewModel.setMaxChapters(n) }
                                            )
                                        }
                                    }
                                    if (progress != null) {
                                        Text(
                                            text = "正在获取第 ${progress!! + 1} 章",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accentText
                                        )
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                        GlassCapsuleButton(
                                            text = "取消导入",
                                            onClick = { viewModel.cancelImport() },
                                            filled = false
                                        )
                                    } else {
                                        GlassCapsuleButton(
                                            text = "下载并导入书架",
                                            onClick = { viewModel.import() },
                                            filled = true,
                                            icon = Icons.Rounded.Download,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                        is DiscoverUiState.Imported -> {
                            GlassSectionCard(contentPadding = PaddingValues(16.dp)) {
                                Text(
                                    text = "《${s.title}》已导入 ${s.chapters} 章",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentText
                                )
                            }
                        }
                        is DiscoverUiState.Error -> {
                            GlassSectionCard(contentPadding = PaddingValues(16.dp)) {
                                Text(
                                    text = s.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        DiscoverUiState.Idle -> {
                            GlassSectionCard(contentPadding = PaddingValues(16.dp)) {
                                Text(
                                    text = "输入书号获取目录，再下载到本地阅读",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryText
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    relayDraft?.let { draft ->
        AdaptiveGlassDialog(onDismissRequest = { relayDraft = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "番茄中转 API",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { relayDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCapsuleButton(
                        text = "取消",
                        onClick = { relayDraft = null },
                        filled = false,
                        modifier = Modifier.weight(1f)
                    )
                    GlassCapsuleButton(
                        text = "保存",
                        onClick = { viewModel.setRelay(draft); relayDraft = null },
                        filled = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
