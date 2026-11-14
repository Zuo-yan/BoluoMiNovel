package com.novelai.assistant.ui.novel

import android.net.Uri
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.novelai.assistant.data.tts.ListeningNavBus
import com.novelai.assistant.ui.assistant.AssistantScreen
import com.novelai.assistant.ui.assistant.ReaderCompanionScreen
import com.novelai.assistant.ui.bookshelf.BookshelfScreen
import com.novelai.assistant.ui.components.AmbientBackdrop
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.glassSource
import com.novelai.assistant.ui.components.rememberGlassState
import com.novelai.assistant.ui.continuation.ContinuationScreen
import com.novelai.assistant.ui.discover.discoverGraph
import com.novelai.assistant.ui.listening.ListeningFloatingCapsule
import com.novelai.assistant.ui.listening.ListeningScreen
import com.novelai.assistant.ui.reader.ReaderScreen
import com.novelai.assistant.ui.settings.ProviderEditScreen
import com.novelai.assistant.ui.settings.SettingsScreen
import com.novelai.assistant.ui.tree.BranchTreeScreen
import dev.chrisbanes.haze.HazeState

object Routes {
    const val ONBOARDING = "onboarding"
    const val BOOKSHELF = "bookshelf"
    const val DISCOVER = "discover"
    const val FANQIE = "fanqie"
    const val SF_CATALOG = "sf_catalog/{bookId}"
    const val SF_DETAIL = "sf_detail/{bookId}"
    const val SF_BROWSE = "sf_browse"
    const val SF_COMICS = "sf_comics"
    const val SF_COMIC_READER = "sf_comic_reader/{slug}"
    const val SF_RECOMMENDATIONS = "sf_recommendations"
    const val SF_RELATED_LISTS = "sf_related_lists/{bookId}"
    const val SF_AUTHOR = "sf_author/{bookId}?name={name}"
    const val ASSISTANT = "assistant"
    const val SETTINGS = "settings"
    const val LISTENING = "listening"
    const val READER = "reader/{bookId}?chapter={chapter}"
    const val GENERATE = "generate/{bookId}/{chapterId}?mode={mode}"
    const val TREE = "tree/{bookId}"
    const val PROVIDER_EDIT = "provider_edit/{providerId}"
    const val READER_COMPANION = "reader_companion/{bookId}?quote={quote}&action={action}"

    fun listening() = "listening"
    fun assistant() = "assistant"
    fun reader(bookId: String) = "reader/$bookId"
    fun readerAt(bookId: String, chapterIndex: Int) = "reader/$bookId?chapter=$chapterIndex"
    fun sfCatalog(bookId: String) = "sf_catalog/$bookId"
    fun sfDetail(bookId: String) = "sf_detail/$bookId"
    fun sfBrowse() = "sf_browse"
    fun sfComics() = "sf_comics"
    fun sfComic(slug: String) = "sf_comic_reader/$slug"
    fun sfRelatedLists(bookId: String) = "sf_related_lists/$bookId"
    fun sfAuthor(bookId: String, name: String) =
        "sf_author/$bookId?name=${Uri.encode(name)}"
    fun generate(bookId: String, chapterId: String, mode: String) =
        "generate/$bookId/$chapterId?mode=$mode"
    fun tree(bookId: String) = "tree/$bookId"
    fun providerEdit(providerId: String) = "provider_edit/$providerId"
    fun readerCompanion(bookId: String, quote: String, action: String = ""): String {
        val q = Uri.encode(quote)
        val a = Uri.encode(action)
        return "reader_companion/$bookId?quote=$q&action=$a"
    }
}

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    BOOKSHELF("bookshelf", "书架", Icons.AutoMirrored.Rounded.LibraryBooks),
    DISCOVER("discover", "菠萝包", Icons.Rounded.Explore),
    ASSISTANT("assistant", "AI助手", Icons.Rounded.AutoAwesome),
    SETTINGS("settings", "设置", Icons.Rounded.Settings)
}

private val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()

// 仅一级 Tab 页面显示底部导航栏，阅读器及下钻伴读页面不显示
private fun isTopLevelRoute(route: String?): Boolean =
    route != null && route in topLevelRoutes

@Composable
fun NovelApp(listeningNavBus: ListeningNavBus? = null) {
    val appViewModel: AppViewModel = hiltViewModel()
    val navBus = listeningNavBus ?: appViewModel.listeningNavBus
    val onboardingDone by appViewModel.onboardingDone.collectAsStateWithLifecycle()

    // 引导标记加载中：显示底色占位，避免闪烁
    if (onboardingDone == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val navController = rememberNavController()
    val hazeState = rememberGlassState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isImeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    val showBottomBar = isTopLevelRoute(currentRoute) && !isImeVisible

    // 监听通知栏卡片点击事件：跳转到专门听书模块
    LaunchedEffect(navBus) {
        navBus.openListening.collect {
            navController.navigate(Routes.LISTENING) {
                launchSingleTop = true
            }
        }
    }

    val ttsSpeaking by appViewModel.ttsSpeaking.collectAsStateWithLifecycle()
    val ttsPaused by appViewModel.ttsPaused.collectAsStateWithLifecycle()
    val currentSpeakingBookId by appViewModel.ttsBookId.collectAsStateWithLifecycle()
    val ttsBookTitle by appViewModel.ttsBookTitle.collectAsStateWithLifecycle()
    val ttsChapterTitle by appViewModel.ttsChapterTitle.collectAsStateWithLifecycle()
    val listenPillPosition by appViewModel.listenPillPosition.collectAsStateWithLifecycle()

    val currentReaderBookId = backStackEntry?.arguments?.getString("bookId")
    val isReadingCurrentTtsBook = currentRoute?.startsWith("reader") == true &&
        currentSpeakingBookId.isNotBlank() &&
        currentReaderBookId == currentSpeakingBookId

    val showCapsule = currentRoute != null &&
        !isReadingCurrentTtsBook &&
        currentRoute != Routes.LISTENING &&
        currentRoute != Routes.ONBOARDING

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AmbientBackdrop()

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { _ ->
            NavHost(
                navController = navController,
                startDestination = if (onboardingDone == true) Routes.BOOKSHELF else Routes.ONBOARDING,
                modifier = Modifier
                    .fillMaxSize()
                    .glassSource(hazeState),
                enterTransition = {
                    if (targetState.destination.route in topLevelRoutes) fadeIn(tween(100))
                    else slideInHorizontally(tween(220, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f))) { it / 5 } + fadeIn(tween(160))
                },
                exitTransition = {
                    fadeOut(tween(100))
                },
                popEnterTransition = {
                    fadeIn(tween(140))
                },
                popExitTransition = {
                    slideOutHorizontally(tween(180)) { it / 5 } + fadeOut(tween(100))
                }
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(
                        onDone = {
                            navController.navigate(Routes.BOOKSHELF) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Routes.BOOKSHELF) {
                    BookshelfScreen(
                        onOpenBook = { navController.navigate(Routes.reader(it)) },
                        onOpenTree = { navController.navigate(Routes.tree(it)) }
                    )
                }
                discoverGraph(
                    navController = navController,
                    onOpenReader = { bookId, chapterIndex ->
                        navController.navigate(
                            if (chapterIndex >= 0) Routes.readerAt(bookId, chapterIndex)
                            else Routes.reader(bookId)
                        )
                    }
                )
                composable(Routes.ASSISTANT) {
                    AssistantScreen(hazeState = hazeState)
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onEditProvider = { navController.navigate(Routes.providerEdit(it)) }
                    )
                }
                composable(Routes.LISTENING) {
                    ListeningScreen(
                        onBack = { navController.popBackStack() },
                        onOpenReader = { bookId, chapterIndex ->
                            navController.navigate(Routes.readerAt(bookId, chapterIndex))
                        }
                    )
                }
                composable(
                    Routes.READER,
                    arguments = listOf(
                        navArgument("bookId") { type = NavType.StringType },
                        navArgument("chapter") { type = NavType.IntType; defaultValue = -1 }
                    )
                ) { entry ->
                    ReaderScreen(
                        bookId = entry.arguments?.getString("bookId").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onGenerate = { bookId, chapterId, mode ->
                            navController.navigate(Routes.generate(bookId, chapterId, mode))
                        },
                        onOpenTree = { navController.navigate(Routes.tree(it)) },
                        onAsk = { bookId, quote, action ->
                            navController.navigate(Routes.readerCompanion(bookId, quote, action))
                        },
                        onOpenDetail = { bookId ->
                            navController.navigate(Routes.sfDetail(bookId))
                        }
                    )
                }
                composable(
                    Routes.READER_COMPANION,
                    arguments = listOf(
                        navArgument("bookId") { type = NavType.StringType },
                        navArgument("quote") { type = NavType.StringType; defaultValue = "" },
                        navArgument("action") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { entry ->
                    ReaderCompanionScreen(
                        bookId = entry.arguments?.getString("bookId").orEmpty(),
                        quote = entry.arguments?.getString("quote").orEmpty(),
                        action = entry.arguments?.getString("action").orEmpty(),
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    Routes.GENERATE,
                    arguments = listOf(
                        navArgument("bookId") { type = NavType.StringType },
                        navArgument("chapterId") { type = NavType.StringType },
                        navArgument("mode") { type = NavType.StringType; defaultValue = "CONTINUATION" }
                    )
                ) { entry ->
                    ContinuationScreen(
                        bookId = entry.arguments?.getString("bookId").orEmpty(),
                        parentChapterId = entry.arguments?.getString("chapterId").orEmpty(),
                        mode = entry.arguments?.getString("mode") ?: "CONTINUATION",
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() }
                    )
                }
                composable(
                    Routes.TREE,
                    arguments = listOf(navArgument("bookId") { type = NavType.StringType })
                ) { entry ->
                    val bid = entry.arguments?.getString("bookId").orEmpty()
                    BranchTreeScreen(
                        bookId = bid,
                        onOpenChapter = { chapterIndex ->
                            navController.navigate(Routes.readerAt(bid, chapterIndex))
                        },
                        onContinueChapter = { chapterId, mode ->
                            navController.navigate(Routes.generate(bid, chapterId, mode))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    Routes.PROVIDER_EDIT,
                    arguments = listOf(navArgument("providerId") { type = NavType.StringType })
                ) { entry ->
                    ProviderEditScreen(
                        providerId = entry.arguments?.getString("providerId") ?: "new",
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        if (showBottomBar) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                GlassBottomBar(
                    hazeState = hazeState,
                    currentRoute = currentRoute,
                    onSelect = { dest -> navigateTopLevel(navController, dest) }
                )
            }
        }

        if (showCapsule) {
            ListeningFloatingCapsule(
                isVisible = ttsSpeaking || ttsPaused,
                paused = ttsPaused,
                bookTitle = ttsBookTitle,
                chapterTitle = ttsChapterTitle,
                positionFrac = listenPillPosition,
                onTogglePlayPause = appViewModel::toggleTtsPlayPause,
                onSavePosition = appViewModel::saveListenPillPosition,
                onOpenListening = {
                    navController.navigate(Routes.LISTENING) {
                        launchSingleTop = true
                    }
                },
                hazeState = hazeState
            )
        }
    }
}

private fun navigateTopLevel(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun GlassBottomBar(
    hazeState: HazeState,
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val destinations = TopLevelDestination.entries
    val selectedIndex = destinations.indexOfFirst { currentRoute?.startsWith(it.route) == true }
        .coerceAtLeast(0)

    var isDragging by remember { mutableStateOf(false) }
    var dragIndexFloat by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var lastHapticIndex by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex, isDragging) {
        if (!isDragging) {
            dragIndexFloat = selectedIndex.toFloat()
            lastHapticIndex = selectedIndex
        }
    }

    val animatedIndex by animateFloatAsState(
        targetValue = if (isDragging) dragIndexFloat else selectedIndex.toFloat(),
        animationSpec = if (isDragging) {
            spring(dampingRatio = 0.85f, stiffness = 900f)
        } else {
            spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "liquidPillIndex"
    )
    val pillScaleX by animateFloatAsState(
        targetValue = if (isDragging) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "pillScaleX"
    )
    val pillScaleY by animateFloatAsState(
        targetValue = if (isDragging) 1.14f else 1.0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "pillScaleY"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = navBarPadding.calculateBottomPadding() + 8.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassBox(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth(),
            state = hazeState,
            cornerRadius = 32.dp,
            widgetSize = GlassWidgetSize.SMALL,
            sceneType = GlassSceneType.IMMERSIVE,
            interactive = true,
            shadowElevation = 14.dp
        ) {
            val density = LocalDensity.current
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                val totalWidthPx = with(density) { maxWidth.toPx() }
                val count = destinations.size
                val slotWidthDp = maxWidth / count
                val slotWidthPx = (totalWidthPx / count).coerceAtLeast(1f)
                val barHeightDp = 50.dp
                val pillShape = RoundedCornerShape(26.dp)

                val dragGestureModifier = Modifier.pointerInput(count, slotWidthPx, selectedIndex) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val idx = (offset.x / slotWidthPx).coerceIn(0f, (count - 1).toFloat())
                            dragIndexFloat = idx
                            lastHapticIndex = idx.roundToInt().coerceIn(0, count - 1)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val next = (dragIndexFloat + dragAmount / slotWidthPx)
                                .coerceIn(0f, (count - 1).toFloat())
                            dragIndexFloat = next
                            val hovered = next.roundToInt().coerceIn(0, count - 1)
                            if (hovered != lastHapticIndex) {
                                lastHapticIndex = hovered
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            val targetIdx = dragIndexFloat.roundToInt().coerceIn(0, count - 1)
                            isDragging = false
                            if (targetIdx != selectedIndex) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(destinations[targetIdx].route)
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                }

                // 底层可流动液态水滴指示器（随拖动平滑滑动、物理放大并呈现晶体高光边缘）
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset((animatedIndex * slotWidthPx).roundToInt(), 0)
                        }
                        .width(slotWidthDp)
                        .height(barHeightDp)
                        .graphicsLayer {
                            scaleX = pillScaleX
                            scaleY = pillScaleY
                        }
                        .clip(pillShape)
                        .background(
                            brush = if (isDark) {
                                Brush.verticalGradient(
                                    colors = if (isDragging) {
                                        listOf(
                                            Color.White.copy(alpha = 0.24f),
                                            Color(0xFF8ECAFF).copy(alpha = 0.14f),
                                            Color.White.copy(alpha = 0.10f)
                                        )
                                    } else {
                                        listOf(
                                            Color.White.copy(alpha = 0.17f),
                                            Color.White.copy(alpha = 0.09f)
                                        )
                                    }
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = if (isDragging) {
                                        listOf(
                                            Color.White.copy(alpha = 0.85f),
                                            Color(0xFFEAF4FF).copy(alpha = 0.62f)
                                        )
                                    } else {
                                        listOf(
                                            Color.White.copy(alpha = 0.72f),
                                            Color.White.copy(alpha = 0.52f)
                                        )
                                    }
                                )
                            },
                            shape = pillShape
                        )
                        .border(
                            width = if (isDragging) 1.dp else 0.7.dp,
                            brush = if (isDark) {
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (isDragging) 0.55f else 0.30f),
                                        Color(0xFF9AC8FF).copy(alpha = if (isDragging) 0.25f else 0.10f),
                                        Color.White.copy(alpha = if (isDragging) 0.35f else 0.16f)
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.98f),
                                        Color(0xFFAAD2FF).copy(alpha = if (isDragging) 0.55f else 0.25f),
                                        Color.White.copy(alpha = 0.65f)
                                    )
                                )
                            },
                            shape = pillShape
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeightDp)
                        .then(dragGestureModifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeVisualIndex = if (isDragging) {
                        dragIndexFloat.roundToInt().coerceIn(0, count - 1)
                    } else {
                        selectedIndex
                    }

                    destinations.forEachIndexed { index, dest ->
                        val active = index == activeVisualIndex
                        val itemScale by animateFloatAsState(
                            targetValue = if (active && isDragging) 1.08f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 450f),
                            label = "tabScale_$index"
                        )
                        val contentColor = when {
                            active && isDark -> Color(0xFF66B2FF)
                            active -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(barHeightDp)
                                .graphicsLayer {
                                    scaleX = itemScale
                                    scaleY = itemScale
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(dest.route)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                        ) {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.label,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = dest.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}
