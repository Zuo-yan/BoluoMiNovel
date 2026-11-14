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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelai.assistant.data.sf.SfSafetyState
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
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
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val tintColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.46f else 0.62f)
    val contentColor = MaterialTheme.colorScheme.onErrorContainer

    LiquidGlassBox(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        widgetSize = GlassWidgetSize.BIG,
        sceneType = GlassSceneType.GENERAL,
        interactive = false,
        skipRenderLayer = true,
        tint = tintColor,
        shadowElevation = 4.dp,
        borderWidth = 0.8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "菠萝包请求已暂停",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            Text(
                text = protection.message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            SafetyCooldownText(protection.until, contentColor)
            Text(
                text = "冷却结束不会自动恢复请求。已保存正文仍可离线阅读。",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.86f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (protection.requiresLogin) {
                    if (onLogin != null) {
                        TextButton(onLogin) {
                            Text("官方验证后重新登录", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Text(
                            text = "请返回菠萝包页面，在官方验证并完成冷却后重新登录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    TextButton(onAcknowledgeCooldown) {
                        Text("已检查，解除暂停", color = contentColor)
                    }
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
private fun SafetyCooldownText(until: Long, textColor: androidx.compose.ui.graphics.Color) {
    val clock by produceState(System.currentTimeMillis(), until) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    if (clock < until) {
        Text(
            text = "冷却至 ${DateFormat.getDateTimeInstance().format(Date(until))}",
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
    }
}
