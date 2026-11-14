package com.novelai.assistant.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/** 统一用户头像组件：复用 AsyncCover 磁盘缓存与按需采样解码管线 */
@Composable
fun SfAvatar(
    avatarUrl: String?,
    name: String,
    modifier: Modifier = Modifier.size(36.dp)
) {
    AsyncCover(
        title = name,
        uri = avatarUrl,
        modifier = modifier,
        shape = CircleShape,
        contentScale = ContentScale.Crop,
        maxDimension = 256,
        placeholder = {
            Icon(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = name,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}
