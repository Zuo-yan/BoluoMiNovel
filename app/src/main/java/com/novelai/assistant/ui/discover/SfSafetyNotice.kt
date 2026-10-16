package com.novelai.assistant.ui.discover

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
internal fun SfSafetyNotice(viewModel: SfViewModel, onLogin: (() -> Unit)? = null) {
    val protection by viewModel.safety.collectAsStateWithLifecycle()
    val clock by produceState(System.currentTimeMillis(), protection.until) {
        while (true) { value = System.currentTimeMillis(); delay(1000) }
    }
    if (!protection.blocked) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("菠萝包请求已暂停", style = MaterialTheme.typography.titleSmall)
            Text(protection.message, style = MaterialTheme.typography.bodyMedium)
            if (clock < protection.until) Text("冷却至 ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(protection.until))}", style = MaterialTheme.typography.bodySmall)
            Text("冷却结束不会自动恢复请求。已保存正文仍可离线阅读。", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (protection.requiresLogin) {
                    if (onLogin != null) TextButton(onLogin, enabled = clock >= protection.until) { Text("官方验证后重新登录") }
                    else Text("请返回菠萝包页面，在官方验证并完成冷却后重新登录。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                } else {
                    TextButton({ viewModel.acknowledgeCooldown() }, enabled = clock >= protection.until) { Text("已检查，解除暂停") }
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = { viewModel.forceBypassSafety() },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("无视风险继续", color = MaterialTheme.colorScheme.onError)
                }
            }
        }
    }
}
