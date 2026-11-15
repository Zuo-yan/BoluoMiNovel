package com.novelai.assistant.ui.listening

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.network.AiTtsVoice
import com.novelai.assistant.data.prefs.TtsEngineMode
import com.novelai.assistant.data.tts.TtsTimerMode
import com.novelai.assistant.data.tts.TtsVoiceOption
import com.novelai.assistant.ui.bookshelf.BookCover
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.AdaptiveGlassSheet
import com.novelai.assistant.ui.components.AdaptiveTwoPane
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassCircleButton
import com.novelai.assistant.ui.components.GlassFilterPill
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.LocalGlassHazeState
import com.novelai.assistant.ui.components.tactileScale
import com.novelai.assistant.ui.theme.NovelColors
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningScreen(
    onBack: () -> Unit,
    onOpenReader: (bookId: String, chapterIndex: Int) -> Unit,
    onOpenTtsRouting: () -> Unit = {},
    viewModel: ListeningViewModel = hiltViewModel()
) {
    val speaking by viewModel.speaking.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val rate by viewModel.rate.collectAsStateWithLifecycle()
    val currentBookId by viewModel.currentBookId.collectAsStateWithLifecycle()
    val currentBookTitle by viewModel.currentBookTitle.collectAsStateWithLifecycle()
    val currentChapterIndex by viewModel.currentChapterIndex.collectAsStateWithLifecycle()
    val currentChapterTitle by viewModel.currentChapterTitle.collectAsStateWithLifecycle()
    val currentBook by viewModel.currentBook.collectAsStateWithLifecycle()

    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val voiceName by viewModel.voiceName.collectAsStateWithLifecycle()
    val engineMode by viewModel.engineMode.collectAsStateWithLifecycle()
    val aiVoice by viewModel.aiVoice.collectAsStateWithLifecycle()
    val aiVoices by viewModel.aiVoices.collectAsStateWithLifecycle()
    val aiBuffering by viewModel.aiBuffering.collectAsStateWithLifecycle()
    val aiFallbackEnabled by viewModel.aiFallbackEnabled.collectAsStateWithLifecycle()
    val ttsLastError by viewModel.ttsLastError.collectAsStateWithLifecycle()
    val activeAiTtsLabel by viewModel.activeAiTtsLabel.collectAsStateWithLifecycle()
    val timerMode by viewModel.timerMode.collectAsStateWithLifecycle()

    val adaptive = LocalAdaptiveMetrics.current
    val hazeState = LocalGlassHazeState.current
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val primaryInk = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryInk = if (isDark) Color(0xFFBEBECA) else MaterialTheme.colorScheme.onSurfaceVariant
    val accentInk = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary

    var showVoiceSheet by remember { mutableStateOf(false) }
    var showTimerSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }

    val isPlaying = speaking && !paused

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentBookTitle.ifBlank { "正在听书" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = when {
                                    aiBuffering && isPlaying -> "AI 正在合成语音…"
                                    isPlaying && engineMode == TtsEngineMode.AI_MODEL && activeAiTtsLabel != null ->
                                        "AI 语音模型播放中"
                                    isPlaying -> "正在播放"
                                    paused -> "已暂停"
                                    else -> "未在播放"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPlaying) accentInk else secondaryInk
                            )
                            if (aiBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = accentInk
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    GlassCircleButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                        size = 40.dp,
                        tint = primaryInk,
                        state = hazeState,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                actions = {
                    if (currentBookId.isNotBlank()) {
                        GlassCapsuleButton(
                            text = "正文",
                            icon = Icons.AutoMirrored.Rounded.MenuBook,
                            filled = false,
                            state = hazeState,
                            onClick = {
                                onOpenReader(currentBookId, currentChapterIndex)
                            },
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        if (adaptive.twoPaneEnabled) {
            AdaptiveCenteredContainer(
                maxWidth = adaptive.maxContentWidth,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
                    .padding(horizontal = adaptive.horizontalPadding, vertical = 12.dp)
            ) {
                AdaptiveTwoPane(
                    splitEnabled = true,
                    primaryFraction = 0.44f,
                    spacing = adaptive.paneSpacing,
                    modifier = Modifier.fillMaxSize(),
                    primaryPane = {
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxSize(),
                            state = hazeState,
                            cornerRadius = 28.dp,
                            widgetSize = GlassWidgetSize.BIG,
                            sceneType = GlassSceneType.IMMERSIVE,
                            interactive = false,
                            skipRenderLayer = true,
                            shadowElevation = 10.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 24.dp, vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                ListeningCoverArtwork(
                                    book = currentBook,
                                    width = 220.dp
                                )
                                Spacer(Modifier.height(22.dp))
                                Text(
                                    text = currentChapterTitle.ifBlank { "准备就绪" },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryInk,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(6.dp))
                                ChapterParagraphIndicator(
                                    chapterIndex = currentChapterIndex,
                                    positionFlow = viewModel.position,
                                    textColor = secondaryInk
                                )
                                Spacer(Modifier.height(24.dp))
                                GlassTransportControls(
                                    isPlaying = isPlaying,
                                    isDark = isDark,
                                    primaryInk = primaryInk,
                                    onPrevious = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.playPrevious()
                                    },
                                    onTogglePlayPause = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.togglePlayPause()
                                    },
                                    onNext = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.playNext()
                                    },
                                    onStop = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.stop()
                                    }
                                )
                            }
                        }
                    },
                    secondaryPane = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CurrentParagraphCard(
                                isPlaying = isPlaying,
                                isDark = isDark,
                                primaryInk = primaryInk,
                                accentInk = accentInk,
                                paragraphTextFlow = viewModel.currentParagraphText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 210.dp, max = 340.dp)
                            )

                            ttsLastError?.let { errMsg ->
                                TtsNoticeBanner(
                                    message = errMsg,
                                    onDismiss = { viewModel.clearTtsError() }
                                )
                            }

                            ListeningOptionGrid(
                                rate = rate,
                                timerMode = timerMode,
                                remainingSecondsFlow = viewModel.timerRemainingSeconds,
                                engineMode = engineMode,
                                voices = voices,
                                voiceName = voiceName,
                                aiVoice = aiVoice,
                                activeAiTtsLabel = activeAiTtsLabel,
                                isDark = isDark,
                                primaryInk = primaryInk,
                                secondaryInk = secondaryInk,
                                accentInk = accentInk,
                                onOpenSpeed = { showSpeedSheet = true },
                                onOpenTimer = { showTimerSheet = true },
                                onOpenVoice = { showVoiceSheet = true }
                            )
                        }
                    }
                )
            }
        } else {
            AdaptiveCenteredContainer(
                maxWidth = adaptive.maxReadableWidth,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = adaptive.horizontalPadding, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(8.dp))
                    ListeningCoverArtwork(
                        book = currentBook,
                        width = 148.dp
                    )

                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = currentChapterTitle.ifBlank { "准备就绪" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = primaryInk,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(4.dp))
                    ChapterParagraphIndicator(
                        chapterIndex = currentChapterIndex,
                        positionFlow = viewModel.position,
                        textColor = secondaryInk
                    )

                    Spacer(Modifier.height(20.dp))
                    CurrentParagraphCard(
                        isPlaying = isPlaying,
                        isDark = isDark,
                        primaryInk = primaryInk,
                        accentInk = accentInk,
                        paragraphTextFlow = viewModel.currentParagraphText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(142.dp)
                    )

                    Spacer(Modifier.height(24.dp))
                    GlassTransportControls(
                        isPlaying = isPlaying,
                        isDark = isDark,
                        primaryInk = primaryInk,
                        onPrevious = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.playPrevious()
                        },
                        onTogglePlayPause = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.togglePlayPause()
                        },
                        onNext = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.playNext()
                        },
                        onStop = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.stop()
                        }
                    )

                    ttsLastError?.let { errMsg ->
                        Spacer(Modifier.height(14.dp))
                        TtsNoticeBanner(
                            message = errMsg,
                            onDismiss = { viewModel.clearTtsError() }
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    ListeningOptionGrid(
                        rate = rate,
                        timerMode = timerMode,
                        remainingSecondsFlow = viewModel.timerRemainingSeconds,
                        engineMode = engineMode,
                        voices = voices,
                        voiceName = voiceName,
                        aiVoice = aiVoice,
                        activeAiTtsLabel = activeAiTtsLabel,
                        isDark = isDark,
                        primaryInk = primaryInk,
                        secondaryInk = secondaryInk,
                        accentInk = accentInk,
                        onOpenSpeed = { showSpeedSheet = true },
                        onOpenTimer = { showTimerSheet = true },
                        onOpenVoice = { showVoiceSheet = true }
                    )

                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }

    if (showSpeedSheet) {
        AdaptiveGlassSheet(
            onDismissRequest = { showSpeedSheet = false },
            maxWidth = adaptive.maxDialogSheetWidth
        ) {
            SpeedSelectorSheet(
                currentRate = rate,
                isDark = isDark,
                primaryInk = primaryInk,
                secondaryInk = secondaryInk,
                accentInk = accentInk,
                onSelectRate = { viewModel.setRate(it) },
                onDismiss = { showSpeedSheet = false }
            )
        }
    }

    if (showTimerSheet) {
        AdaptiveGlassSheet(
            onDismissRequest = { showTimerSheet = false },
            maxWidth = adaptive.maxDialogSheetWidth
        ) {
            TimerSelectorSheet(
                currentMode = timerMode,
                primaryInk = primaryInk,
                accentInk = accentInk,
                remainingSecondsFlow = viewModel.timerRemainingSeconds,
                onSelectMode = {
                    viewModel.setTimerMode(it)
                    showTimerSheet = false
                }
            )
        }
    }

    if (showVoiceSheet) {
        AdaptiveGlassSheet(
            onDismissRequest = { showVoiceSheet = false },
            maxWidth = adaptive.maxDialogSheetWidth
        ) {
            VoiceSelectorSheet(
                engineMode = engineMode,
                voices = voices,
                selectedVoiceName = voiceName,
                aiVoices = aiVoices,
                selectedAiVoice = aiVoice,
                activeAiTtsLabel = activeAiTtsLabel,
                aiFallbackEnabled = aiFallbackEnabled,
                aiBuffering = aiBuffering,
                ttsLastError = ttsLastError,
                isDark = isDark,
                primaryInk = primaryInk,
                secondaryInk = secondaryInk,
                accentInk = accentInk,
                onClearError = { viewModel.clearTtsError() },
                onSelectEngineMode = { mode -> viewModel.setEngineMode(mode) },
                onSelectVoice = { name ->
                    viewModel.setEngineMode(TtsEngineMode.SYSTEM)
                    viewModel.setVoice(name)
                    showVoiceSheet = false
                },
                onAudition = { name -> viewModel.auditionVoice(name) },
                onSelectAiVoice = { voiceId ->
                    viewModel.setAiVoice(voiceId)
                    showVoiceSheet = false
                },
                onAuditionAiVoice = { voiceId -> viewModel.auditionAiVoice(voiceId) },
                onRefreshAiVoices = { viewModel.refreshAiVoices() },
                onToggleAiFallback = { enabled -> viewModel.setAiFallbackEnabled(enabled) },
                onOpenTtsRouting = {
                    showVoiceSheet = false
                    onOpenTtsRouting()
                }
            )
        }
    }
}

@Composable
private fun ListeningCoverArtwork(
    book: BookEntity?,
    width: Dp
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(0.72f)
            .shadow(18.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(0.8.dp, Color.White.copy(alpha = 0.25f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (book != null) {
            BookCover(
                book = book,
                modifier = Modifier.fillMaxSize(),
                corner = shape
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(52.dp)
                )
            }
        }
    }
}

@Composable
private fun GlassTransportControls(
    isPlaying: Boolean,
    isDark: Boolean,
    primaryInk: Color,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit
) {
    LiquidGlassBox(
        modifier = Modifier.fillMaxWidth(),
        state = LocalGlassHazeState.current,
        cornerRadius = 32.dp,
        widgetSize = GlassWidgetSize.SMALL,
        sceneType = GlassSceneType.IMMERSIVE,
        interactive = true,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(50.dp)
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "上一章",
                    tint = primaryInk,
                    modifier = Modifier.size(32.dp)
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(68.dp)
                    .border(
                        1.dp,
                        Color.White.copy(alpha = if (isDark) 0.28f else 0.65f),
                        CircleShape
                    )
                    .clip(CircleShape)
                    .clickable(onClick = onTogglePlayPause)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(50.dp)
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "下一章",
                    tint = primaryInk,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = onStop,
                modifier = Modifier.size(50.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Rounded.Stop,
                    contentDescription = "停止听书",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun TtsNoticeBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭提示",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ListeningOptionGrid(
    rate: Float,
    timerMode: TtsTimerMode,
    remainingSecondsFlow: StateFlow<Int?>,
    engineMode: TtsEngineMode,
    voices: List<TtsVoiceOption>,
    voiceName: String?,
    aiVoice: String,
    activeAiTtsLabel: String?,
    isDark: Boolean,
    primaryInk: Color,
    secondaryInk: Color,
    accentInk: Color,
    onOpenSpeed: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenVoice: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ListenOptionPill(
            icon = Icons.Rounded.Speed,
            title = "语速",
            value = "%.2fx".format(Locale.US, rate),
            isDark = isDark,
            primaryInk = primaryInk,
            secondaryInk = secondaryInk,
            accentInk = accentInk,
            modifier = Modifier.weight(1f),
            onClick = onOpenSpeed
        )

        TimerOptionPill(
            timerMode = timerMode,
            remainingSecondsFlow = remainingSecondsFlow,
            isDark = isDark,
            primaryInk = primaryInk,
            secondaryInk = secondaryInk,
            accentInk = accentInk,
            modifier = Modifier.weight(1f),
            onClick = onOpenTimer
        )

        val isAiActive = engineMode == TtsEngineMode.AI_MODEL && activeAiTtsLabel != null
        val selectedVoice = voices.firstOrNull { it.name == voiceName }
        val voiceLabel = if (isAiActive) {
            "AI · ${aiVoice.ifBlank { "默认" }}"
        } else {
            selectedVoice?.label ?: "系统默认"
        }
        ListenOptionPill(
            icon = if (isAiActive) Icons.Rounded.GraphicEq else Icons.Rounded.RecordVoiceOver,
            title = if (isAiActive) "AI 音色" else "音色",
            value = voiceLabel,
            isDark = isDark,
            primaryInk = primaryInk,
            secondaryInk = secondaryInk,
            accentInk = accentInk,
            highlight = isAiActive,
            modifier = Modifier.weight(1f),
            onClick = onOpenVoice
        )
    }
}

@Composable
private fun ChapterParagraphIndicator(
    chapterIndex: Int,
    positionFlow: StateFlow<Int>,
    textColor: Color
) {
    val position by positionFlow.collectAsStateWithLifecycle()
    Text(
        text = "第 ${chapterIndex + 1} 章 · 第 ${position + 1} 段",
        style = MaterialTheme.typography.bodyMedium,
        color = textColor
    )
}

@Composable
private fun CurrentParagraphCard(
    isPlaying: Boolean,
    isDark: Boolean,
    primaryInk: Color,
    accentInk: Color,
    paragraphTextFlow: StateFlow<String>,
    modifier: Modifier = Modifier
) {
    val currentParagraphText by paragraphTextFlow.collectAsStateWithLifecycle()
    LiquidGlassBox(
        modifier = modifier,
        state = LocalGlassHazeState.current,
        cornerRadius = 22.dp,
        widgetSize = GlassWidgetSize.BIG,
        sceneType = GlassSceneType.GENERAL,
        interactive = false,
        skipRenderLayer = true,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) accentInk
                            else if (isDark) Color(0xFF7E8090) else MaterialTheme.colorScheme.outline
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isPlaying) "正在朗读：" else "当前段落：",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentInk,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = currentParagraphText.ifBlank { "（无段落文本或已停止）" },
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp),
                color = primaryInk
            )
        }
    }
}

@Composable
private fun TimerOptionPill(
    timerMode: TtsTimerMode,
    remainingSecondsFlow: StateFlow<Int?>,
    isDark: Boolean,
    primaryInk: Color,
    secondaryInk: Color,
    accentInk: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val remainingSeconds by remainingSecondsFlow.collectAsStateWithLifecycle()
    val timerLabel = when {
        remainingSeconds != null -> formatRemainingTime(remainingSeconds)
        timerMode == TtsTimerMode.END_OF_CHAPTER -> "本章停"
        timerMode != TtsTimerMode.OFF -> timerMode.label
        else -> "关闭"
    }
    ListenOptionPill(
        icon = Icons.Rounded.Timer,
        title = "定时",
        value = timerLabel,
        isDark = isDark,
        primaryInk = primaryInk,
        secondaryInk = secondaryInk,
        accentInk = accentInk,
        modifier = modifier,
        highlight = timerMode != TtsTimerMode.OFF,
        onClick = onClick
    )
}

@Composable
private fun ListenOptionPill(
    icon: ImageVector,
    title: String,
    value: String,
    isDark: Boolean,
    primaryInk: Color,
    secondaryInk: Color,
    accentInk: Color,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val highlightTint = if (highlight) {
        accentInk.copy(alpha = if (isDark) 0.22f else 0.14f)
    } else {
        null
    }
    LiquidGlassBox(
        modifier = modifier
            .tactileScale(interactionSource, pressedScale = 0.96f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        state = LocalGlassHazeState.current,
        cornerRadius = 18.dp,
        widgetSize = GlassWidgetSize.SMALL,
        sceneType = GlassSceneType.GENERAL,
        interactive = true,
        tint = highlightTint,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (highlight) accentInk else secondaryInk,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = secondaryInk
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) accentInk else primaryInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SpeedSelectorSheet(
    currentRate: Float,
    isDark: Boolean,
    primaryInk: Color,
    secondaryInk: Color,
    accentInk: Color,
    onSelectRate: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(currentRate) }
    val presetRates = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "调节朗读语速",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = primaryInk
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "当前倍率：%.2fx".format(Locale.US, sliderValue),
            style = MaterialTheme.typography.bodyMedium,
            color = accentInk,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(18.dp))
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                onSelectRate(it)
            },
            valueRange = 0.5f..2.5f,
            steps = 19,
            colors = SliderDefaults.colors(
                thumbColor = accentInk,
                activeTrackColor = accentInk,
                inactiveTrackColor = secondaryInk.copy(alpha = 0.28f)
            )
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            presetRates.forEach { rate ->
                val isSelected = abs(sliderValue - rate) < 0.04f
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isDark -> Color.White.copy(alpha = 0.10f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            sliderValue = rate
                            onSelectRate(rate)
                        }
                ) {
                    Text(
                        text = "%.2fx".format(Locale.US, rate),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else primaryInk,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        GlassCapsuleButton(
            text = "完成",
            onClick = onDismiss,
            filled = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun TimerSelectorSheet(
    currentMode: TtsTimerMode,
    primaryInk: Color,
    accentInk: Color,
    remainingSecondsFlow: StateFlow<Int?>,
    onSelectMode: (TtsTimerMode) -> Unit
) {
    val remainingSeconds by remainingSecondsFlow.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "定时停止朗读",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = primaryInk
        )
        val remaining = remainingSeconds
        if (remaining != null && remaining > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "正在倒计时：${formatRemainingTime(remaining)}",
                style = MaterialTheme.typography.bodyMedium,
                color = accentInk,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(14.dp))

        TtsTimerMode.entries.forEach { mode ->
            val isSelected = (mode == currentMode)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) accentInk.copy(alpha = 0.14f) else Color.Transparent
                    )
                    .clickable { onSelectMode(mode) }
                    .padding(vertical = 13.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) accentInk else primaryInk,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                if (isSelected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = accentInk,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun VoiceSelectorSheet(
    engineMode: TtsEngineMode,
    voices: List<TtsVoiceOption>,
    selectedVoiceName: String?,
    aiVoices: List<AiTtsVoice>,
    selectedAiVoice: String,
    activeAiTtsLabel: String?,
    aiFallbackEnabled: Boolean,
    aiBuffering: Boolean,
    ttsLastError: String?,
    isDark: Boolean,
    primaryInk: Color,
    secondaryInk: Color,
    accentInk: Color,
    onClearError: () -> Unit,
    onSelectEngineMode: (TtsEngineMode) -> Unit,
    onSelectVoice: (String?) -> Unit,
    onAudition: (String?) -> Unit,
    onSelectAiVoice: (String) -> Unit,
    onAuditionAiVoice: (String) -> Unit,
    onRefreshAiVoices: () -> Unit,
    onToggleAiFallback: (Boolean) -> Unit,
    onOpenTtsRouting: () -> Unit
) {
    var activeTab by remember(engineMode) { mutableStateOf(engineMode) }
    var customVoiceInput by remember(selectedAiVoice) { mutableStateOf(selectedAiVoice) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "语音引擎与音色",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryInk
            )
            if (aiBuffering) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.8.dp,
                        color = accentInk
                    )
                    Text("合成试听中…", style = MaterialTheme.typography.labelSmall, color = accentInk)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        ttsLastError?.let { errMsg ->
            TtsNoticeBanner(
                message = errMsg,
                onDismiss = onClearError
            )
            Spacer(Modifier.height(10.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassFilterPill(
                label = "系统语音",
                selected = activeTab == TtsEngineMode.SYSTEM,
                onClick = {
                    activeTab = TtsEngineMode.SYSTEM
                    onSelectEngineMode(TtsEngineMode.SYSTEM)
                }
            )
            GlassFilterPill(
                label = "AI 语音模型",
                selected = activeTab == TtsEngineMode.AI_MODEL,
                onClick = {
                    activeTab = TtsEngineMode.AI_MODEL
                    if (activeAiTtsLabel != null) {
                        onSelectEngineMode(TtsEngineMode.AI_MODEL)
                    }
                }
            )
        }
        Spacer(Modifier.height(10.dp))

        ttsLastError?.let { errMsg ->
            TtsNoticeBanner(
                message = errMsg,
                onDismiss = onClearError
            )
            Spacer(Modifier.height(10.dp))
        }

        if (activeTab == TtsEngineMode.SYSTEM) {
            Text(
                text = "由设备系统 TTS 引擎提供，离线可用、零额度消耗",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryInk
            )
            Spacer(Modifier.height(10.dp))

        ttsLastError?.let { errMsg ->
            TtsNoticeBanner(
                message = errMsg,
                onDismiss = onClearError
            )
            Spacer(Modifier.height(10.dp))
        }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
            ) {
                item(key = "__system_default__") {
                    VoiceOptionRow(
                        title = "系统默认音色",
                        subtitle = "使用系统 TTS 默认语音",
                        isSelected = engineMode == TtsEngineMode.SYSTEM && selectedVoiceName == null,
                        isDark = isDark,
                        primaryInk = primaryInk,
                        secondaryInk = secondaryInk,
                        accentInk = accentInk,
                        onSelect = { onSelectVoice(null) },
                        onAudition = { onAudition(null) }
                    )
                }

                if (voices.isEmpty()) {
                    item(key = "__empty_voices__") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "当前设备引擎未导出更多音色列表，默认支持基础语音",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryInk,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                itemsIndexed(voices, key = { index, voice -> "${index}_${voice.name}" }) { _, voice ->
                    VoiceOptionRow(
                        title = voice.label,
                        subtitle = voice.name,
                        isSelected = engineMode == TtsEngineMode.SYSTEM && voice.name == selectedVoiceName,
                        isNetworkRequired = voice.isNetworkRequired,
                        isDark = isDark,
                        primaryInk = primaryInk,
                        secondaryInk = secondaryInk,
                        accentInk = accentInk,
                        onSelect = { onSelectVoice(voice.name) },
                        onAudition = { onAudition(voice.name) }
                    )
                }
            }
        } else {
            if (activeAiTtsLabel == null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "尚未配置 AI 语音合成模型",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryInk
                        )
                        Text(
                            text = "请先在「设置 -> AI 模型任务分流 -> AI 语音合成」中指定支持 Gemini TTS 或 OpenAI /v1/audio/speech 的服务商与模型。",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryInk
                        )
                        GlassCapsuleButton(
                            text = "前往配置语音模型",
                            icon = Icons.Rounded.Tune,
                            filled = true,
                            onClick = onOpenTtsRouting,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accentInk.copy(alpha = if (isDark) 0.14f else 0.09f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "当前模型：$activeAiTtsLabel",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "按需向前预取 1~2 句 · 支持倍速与本地磁盘缓存",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryInk
                            )
                        }
                        TextButton(onClick = onOpenTtsRouting) {
                            Text("更换模型")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "异常时自动回退系统语音",
                        style = MaterialTheme.typography.bodySmall,
                        color = primaryInk
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onRefreshAiVoices,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "刷新扩展音色",
                                tint = accentInk,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Switch(
                            checked = aiFallbackEnabled,
                            onCheckedChange = onToggleAiFallback
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customVoiceInput,
                        onValueChange = { customVoiceInput = it },
                        label = { Text("自定义音色 ID（可选）") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { onSelectAiVoice(customVoiceInput) },
                        enabled = customVoiceInput.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("应用")
                    }
                }
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    itemsIndexed(aiVoices, key = { index, v -> "${index}_${v.id}" }) { _, voice ->
                        VoiceOptionRow(
                            title = voice.displayName,
                            subtitle = voice.description.ifBlank { voice.id },
                            isSelected = engineMode == TtsEngineMode.AI_MODEL && voice.id == selectedAiVoice,
                            isNetworkRequired = true,
                            isDark = isDark,
                            primaryInk = primaryInk,
                            secondaryInk = secondaryInk,
                            accentInk = accentInk,
                            onSelect = {
                                onSelectAiVoice(voice.id)
                            },
                            onAudition = { onAuditionAiVoice(voice.id) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun VoiceOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isDark: Boolean,
    primaryInk: Color,
    secondaryInk: Color,
    accentInk: Color,
    isNetworkRequired: Boolean = false,
    onSelect: () -> Unit,
    onAudition: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) accentInk.copy(alpha = if (isDark) 0.16f else 0.10f) else Color.Transparent)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelect()
            }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) accentInk else primaryInk
                )
                if (isNetworkRequired) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "在线",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.tertiaryContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = secondaryInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedButton(
            onClick = onAudition,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            border = BorderStroke(0.8.dp, secondaryInk.copy(alpha = 0.45f)),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(
                Icons.Rounded.VolumeUp,
                contentDescription = null,
                tint = accentInk,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("试听", style = MaterialTheme.typography.labelSmall, color = primaryInk)
        }

        if (isSelected) {
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Rounded.Check, contentDescription = null, tint = accentInk)
        }
    }
}

private fun formatRemainingTime(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return ""
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
}
