package com.novelai.assistant.ui.novel

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
import com.novelai.assistant.data.generation.GenerationNavBus
import com.novelai.assistant.data.tts.ListeningNavBus
import com.novelai.assistant.ui.assistant.AssistantScreen
import com.novelai.assistant.ui.assistant.ReaderCompanionScreen
import com.novelai.assistant.ui.components.NovelMotion
import com.novelai.assistant.ui.bookshelf.BookshelfScreen
import com.novelai.assistant.ui.components.AdaptiveNavigationType
import com.novelai.assistant.ui.components.AmbientBackdrop
import com.novelai.assistant.ui.components.GlassBottomDock
import com.novelai.assistant.ui.components.GlassNavItem
import com.novelai.assistant.ui.components.GlassNavigationRail
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.LocalGlassHazeState
import com.novelai.assistant.ui.components.bottomBarContentPadding
import com.novelai.assistant.ui.components.glassSource
import com.novelai.assistant.ui.components.navigationRailContentStartPadding
import com.novelai.assistant.ui.components.rememberGlassState
import com.novelai.assistant.ui.continuation.ContinuationScreen
import com.novelai.assistant.ui.discover.discoverGraph
import com.novelai.assistant.ui.listening.ListeningFloatingCapsule
import com.novelai.assistant.ui.listening.ListeningScreen
import com.novelai.assistant.ui.reader.ReaderScreen
import com.novelai.assistant.ui.settings.ProviderEditScreen
import com.novelai.assistant.ui.settings.SettingsScreen
import com.novelai.assistant.ui.settings.TaskRoutingScreen
import com.novelai.assistant.ui.tree.BranchTreeScreen

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
    const val TASK_ROUTING = "task_routing"
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
    fun generate(bookId: String, chapterId: String, mode: String = "CONTINUATION") =
        "generate/$bookId/$chapterId?mode=$mode"
    fun tree(bookId: String) = "tree/$bookId"
    fun providerEdit(providerId: String) = "provider_edit/$providerId"
    fun taskRouting() = "task_routing"
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

// 仅一级 Tab 页面显示主导航栏（手机底栏 / 平板侧边 Rail），阅读器及下钻伴读页面不显示
private fun isTopLevelRoute(route: String?): Boolean =
    route != null && route in topLevelRoutes

@Composable
fun NovelApp(
    listeningNavBus: ListeningNavBus? = null,
    generationNavBus: GenerationNavBus? = null
) {
    val appViewModel: AppViewModel = hiltViewModel()
    val navBus = listeningNavBus ?: appViewModel.listeningNavBus
    val genNavBus = generationNavBus ?: appViewModel.generationNavBus
    val onboardingDone by appViewModel.onboardingDone.collectAsStateWithLifecycle()

    // 引导标记加载中：显示底色占位，避免闪烁
    if (onboardingDone == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val navController = rememberNavController()
    val hazeState = rememberGlassState()
    val snackbarHostState = remember { SnackbarHostState() }
    val adaptive = LocalAdaptiveMetrics.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isImeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    val isTopLevel = isTopLevelRoute(currentRoute)
    val useRail = adaptive.navigationType == AdaptiveNavigationType.NAVIGATION_RAIL
    val showNavRail = isTopLevel && useRail
    val showBottomBar = isTopLevel && !useRail && !isImeVisible

    val navItems = remember {
        TopLevelDestination.entries.map { dest ->
            GlassNavItem(route = dest.route, label = dest.label, icon = dest.icon)
        }
    }
    val selectedIndex = TopLevelDestination.entries
        .indexOfFirst { currentRoute?.startsWith(it.route) == true }
        .coerceAtLeast(0)

    // 监听通知栏卡片点击事件：跳转到专门听书模块或 AI 生成工作台
    LaunchedEffect(navBus) {
        navBus.openListening.collect {
            navController.navigate(Routes.LISTENING) {
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(genNavBus) {
        genNavBus.openGeneration.collect { target ->
            navController.navigate(Routes.generate(target.bookId, target.chapterId, target.mode)) {
                launchSingleTop = true
            }
        }
    }

    val ttsSpeaking by appViewModel.ttsSpeaking.collectAsStateWithLifecycle()
    val ttsPaused by appViewModel.ttsPaused.collectAsStateWithLifecycle()
    val currentSpeakingBookId by appViewModel.ttsBookId.collectAsStateWithLifecycle()
    val ttsBookTitle by appViewModel.ttsBookTitle.collectAsStateWithLifecycle()
    val ttsChapterTitle by appViewModel.ttsChapterTitle.collectAsStateWithLifecycle()
    val generationState by appViewModel.generationState.collectAsStateWithLifecycle()
    val listenPillPosition by appViewModel.listenPillPosition.collectAsStateWithLifecycle()

    val currentArgBookId = backStackEntry?.arguments?.getString("bookId")
    val isReadingCurrentTtsBook = currentRoute?.startsWith("reader") == true &&
        currentSpeakingBookId.isNotBlank() &&
        currentArgBookId == currentSpeakingBookId
    val isViewingCurrentGenScreen = currentRoute?.startsWith("generate") == true &&
        generationState != null &&
        currentArgBookId == generationState?.bookId

    val showTtsInCapsule = (ttsSpeaking || ttsPaused) &&
        !isReadingCurrentTtsBook &&
        currentRoute != Routes.LISTENING
    val showGenInCapsule = generationState?.hasDisplayableState == true &&
        !isViewingCurrentGenScreen

    val showCapsule = currentRoute != null &&
        currentRoute != Routes.ONBOARDING &&
        (showTtsInCapsule || showGenInCapsule)

    CompositionLocalProvider(LocalGlassHazeState provides hazeState) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AmbientBackdrop(modifier = Modifier.glassSource(hazeState))

            val railStartPadding = if (showNavRail) navigationRailContentStartPadding() else 0.dp
            val snackbarBottomPadding = if (showBottomBar) {
                bottomBarContentPadding(extra = 8.dp)
            } else {
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
            }

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(
                            start = railStartPadding,
                            bottom = snackbarBottomPadding
                        )
                    )
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    AnimatedVisibility(
                        visible = showNavRail,
                        enter = NovelMotion.fadeEnter(200) + expandHorizontally(
                            animationSpec = NovelMotion.expressiveSpring(),
                            expandFrom = Alignment.Start
                        ),
                        exit = NovelMotion.fadeExit(150) + shrinkHorizontally(
                            animationSpec = NovelMotion.spatialSpring(),
                            shrinkTowards = Alignment.Start
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(
                                    start = adaptive.navigationRailInset,
                                    end = adaptive.navigationRailInset,
                                    top = adaptive.navigationRailInset,
                                    bottom = adaptive.navigationRailInset
                                ),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            GlassNavigationRail(
                                items = navItems,
                                selectedIndex = selectedIndex,
                                onSelect = { index ->
                                    navigateTopLevel(navController, navItems[index].route)
                                },
                                state = hazeState
                            )
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = if (onboardingDone == true) Routes.BOOKSHELF else Routes.ONBOARDING,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .glassSource(hazeState),
                        enterTransition = {
                            if (targetState.destination.route in topLevelRoutes) NovelMotion.topLevelEnter()
                            else NovelMotion.pushEnter()
                        },
                        exitTransition = {
                            if (targetState.destination.route in topLevelRoutes && initialState.destination.route in topLevelRoutes) {
                                NovelMotion.topLevelExit()
                            } else {
                                NovelMotion.pushExit()
                            }
                        },
                        popEnterTransition = {
                            if (targetState.destination.route in topLevelRoutes && initialState.destination.route in topLevelRoutes) {
                                NovelMotion.topLevelEnter()
                            } else {
                                NovelMotion.popEnter()
                            }
                        },
                        popExitTransition = {
                            if (targetState.destination.route in topLevelRoutes && initialState.destination.route in topLevelRoutes) {
                                NovelMotion.topLevelExit()
                            } else {
                                NovelMotion.popExit()
                            }
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
                                onEditProvider = { navController.navigate(Routes.providerEdit(it)) },
                                onOpenTaskRouting = { navController.navigate(Routes.taskRouting()) }
                            )
                        }
                        composable(Routes.TASK_ROUTING) {
                            TaskRoutingScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.LISTENING) {
                            ListeningScreen(
                                onBack = { navController.popBackStack() },
                                onOpenReader = { bookId, chapterIndex ->
                                    navController.navigate(Routes.readerAt(bookId, chapterIndex))
                                },
                                onOpenTtsRouting = {
                                    navController.navigate(Routes.taskRouting())
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
            }

            AnimatedVisibility(
                visible = showBottomBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = NovelMotion.fadeEnter(200) + slideInVertically(NovelMotion.expressiveSpring()) { it / 2 },
                exit = NovelMotion.fadeExit(140) + slideOutVertically(NovelMotion.spatialSpring()) { it / 2 }
            ) {
                val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
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
                    GlassBottomDock(
                        items = navItems,
                        selectedIndex = selectedIndex,
                        onSelect = { index ->
                            navigateTopLevel(navController, navItems[index].route)
                        },
                        modifier = Modifier
                            .widthIn(max = adaptive.maxDockWidth.coerceAtMost(380.dp))
                            .fillMaxWidth(),
                        state = hazeState
                    )
                }
            }

            if (showCapsule) {
                ListeningFloatingCapsule(
                    isVisible = showTtsInCapsule || showGenInCapsule,
                    showTts = showTtsInCapsule,
                    paused = ttsPaused,
                    bookTitle = ttsBookTitle,
                    chapterTitle = ttsChapterTitle,
                    generationState = if (showGenInCapsule) generationState else null,
                    positionFrac = listenPillPosition,
                    onTogglePlayPause = appViewModel::toggleTtsPlayPause,
                    onSavePosition = appViewModel::saveListenPillPosition,
                    onOpenListening = {
                        navController.navigate(Routes.LISTENING) {
                            launchSingleTop = true
                        }
                    },
                    onOpenGeneration = { target ->
                        navController.navigate(
                            Routes.generate(target.bookId, target.parentChapterId)
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onStopGeneration = appViewModel::stopBackgroundGeneration,
                    onDismissGeneration = appViewModel::dismissBackgroundGeneration,
                    hazeState = hazeState,
                    railVisible = showNavRail,
                    bottomDockVisible = showBottomBar
                )
            }
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
