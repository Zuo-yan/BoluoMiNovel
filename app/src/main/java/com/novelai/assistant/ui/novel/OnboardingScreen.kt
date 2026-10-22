package com.novelai.assistant.ui.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: List<String>
)

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: AppViewModel = hiltViewModel()
) {
    val pages = remember {
        listOf(
            OnboardingPage(
                icon = Icons.Rounded.AutoAwesome,
                title = "菠萝蜜小说",
                body = listOf(
                    "菠萝包轻小说阅读器 × 深度 AI 伴读 × 沉浸式续写与平行分支",
                    "BYOK 模式：接入你自己的模型密钥，所有数据只留在你的设备上"
                )
            ),
            OnboardingPage(
                icon = Icons.Rounded.Key,
                title = "第一步 · 接入你的 AI",
                body = listOf(
                    "1. 打开「设置 → 添加提供商」",
                    "2. 选择厂商及平台／地域，或选择自定义服务",
                    "3. 填写 API Key，确认协议、服务地址与模型 ID",
                    "4. 可自动识别最大输出；连接测试读取模型接口，密钥经 Keystore 加密存储"
                )
            ),
            OnboardingPage(
                icon = Icons.Rounded.UploadFile,
                title = "第二步 · 阅读与创作",
                body = listOf(
                    "· 书架右上角导入 TXT / Markdown / EPUB",
                    "·「菠萝包」登录账号查看书架，点书直接继续阅读",
                    "· 阅读时长按段落「划线即问」，AI 自动检索相关章节作答",
                    "· 底栏「AI 续写」：单章续写，或 What-if 推演平行世界分支",
                    "· 分支树中随时回看你的 AI 衍生剧情"
                )
            )
        )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    fun finish() {
        viewModel.completeOnboarding()
        onDone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { finish() }) { Text("跳过") }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Icon(
                        page.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(28.dp).size(52.dp)
                    )
                }
                Spacer(Modifier.height(28.dp))
                Text(page.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                page.body.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
        // 指示点
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pages.size) { i ->
                val selected = pagerState.currentPage == i
                Box(
                    Modifier
                        .padding(horizontal = 5.dp)
                        .size(if (selected) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                if (pagerState.currentPage == pages.lastIndex) finish()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(if (pagerState.currentPage == pages.lastIndex) "开始阅读" else "下一步")
            Spacer(Modifier.height(0.dp))
            if (pagerState.currentPage != pages.lastIndex) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 6.dp).size(16.dp)
                )
            }
        }
    }
}
