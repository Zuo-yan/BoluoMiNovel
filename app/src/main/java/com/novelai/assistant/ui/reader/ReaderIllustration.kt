package com.novelai.assistant.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novelai.assistant.ui.components.AsyncCover

data class ReaderIllustration(val url: String) {
    companion object {
        private val tag = Regex("\\[img(?:=[^]]*)?]\\s*(https?://[^\\s\\[]+)\\s*\\[/img]", RegexOption.IGNORE_CASE)
        fun parse(text: String): ReaderIllustration? {
            val match = tag.matchEntire(text.trim()) ?: return null
            val url = match.groupValues[1].replaceFirst("http://", "https://")
            val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
            if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
            return ReaderIllustration(url)
        }
    }
}

@Composable
fun ReaderIllustrationView(image: ReaderIllustration, modifier: Modifier = Modifier) {
    var opened by remember(image.url) { mutableStateOf(false) }
    AsyncCover("章节插图，点击放大", image.url, modifier.clickable { opened = true },
        contentScale = ContentScale.Fit, maxDimension = 2048,
        placeholder = { Text("插图加载中或暂不可用，点击查看") })
    if (opened) Dialog({ opened = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var x by remember { mutableFloatStateOf(0f) }
        var y by remember { mutableFloatStateOf(0f) }
        var retry by remember { mutableIntStateOf(0) }
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton({ opened = false }) { Text("关闭插图") }
                    TextButton({ scale = 1f; x = 0f; y = 0f }) { Text("还原") }
                    TextButton({ retry++ }) { Text("重新加载") }
                }
                Box(Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        x = (x + pan.x).coerceIn(-size.width * (scale - 1), size.width * (scale - 1))
                        y = (y + pan.y).coerceIn(-size.height * (scale - 1), size.height * (scale - 1))
                    }
                }) {
                    key(retry) {
                        AsyncCover("章节插图", image.url, Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale; scaleY = scale; translationX = x; translationY = y
                        }, contentScale = ContentScale.Fit, maxDimension = 2048,
                            placeholder = { Text("插图暂不可用，可重新加载", Modifier.padding(24.dp)) })
                    }
                }
            }
        }
    }
}
