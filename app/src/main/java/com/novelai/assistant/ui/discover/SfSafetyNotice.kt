package com.novelai.assistant.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novelai.assistant.data.sf.SfSafetyState
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SfSafetyNotice(
    protection: SfSafetyState,
    onAcknowledgeCooldown: () -> Unit,
    onForceBypass: () -> Unit,
    onLogin: (() -> Unit)? = null
) {
    if (!protection.blocked) return

    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("菠萝包请求已暂停", style = MaterialTheme.typography.titleSmall)
            Text(protection.message, style = MaterialTheme.typography.bodyMedium)
            SafetyCooldownText(protection.until)
            Text("冷却结束不会自动恢复请求。已保存正文仍可离线阅读。", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (protection.requiresLogin) {
                    if (onLogin != null) {
                        TextButton(onLogin) { Text("官方验证后重新登录") }
                    } else {
                        Text(
                            "请返回菠萝包页面，在官方验证并完成冷却后重新登录。",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    TextButton(onAcknowledgeCooldown) { Text("已检查，解除暂停") }
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = onForceBypass,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("无视风险继续", color = MaterialTheme.colorScheme.onError)
                }
            }
        }
    }
}

/** 隔离倒计时局部重组，避免父级与全局每秒重绘 */
@Composable
private fun SafetyCooldownText(until: Long) {
    val clock by produceState(System.currentTimeMillis(), until) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    if (clock < until) {
        Text(
            text = "冷却至 ${DateFormat.getDateTimeInstance().format(Date(until))}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
