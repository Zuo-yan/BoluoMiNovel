package com.novelai.assistant.ui.novel

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.novelai.assistant.data.tts.ListeningNavBus
import com.novelai.assistant.ui.assistant.AssistantScreen
import com.novelai.assistant.ui.assistant.ReaderCompanionScreen
import com.novelai.assistant.ui.bookshelf.BookshelfScreen
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import com.novelai.assistant.ui.components.glassEffect
import com.novelai.assistant.ui.components.AmbientBackdrop
import com.novelai.assistant.ui.components.liquidGlass
import com.novelai.assistant.ui.components.glassSource
import com.novelai.assistant.ui.components.rememberGlassState
import com.novelai.assistant.ui.continuation.ContinuationScreen
import com.novelai.assistant.ui.discover.DiscoverScreen
import com.novelai.assistant.ui.listening.ListeningFloatingCapsule
import com.novelai.assistant.ui.listening.ListeningScreen
import com.novelai.assistant.ui.reader.ReaderScreen
import com.novelai.assistant.ui.settings.ProviderEditScreen
import com.novelai.assistant.ui.settings.SettingsScreen
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
    const val SF_COMIC = "sf_comic"
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
    fun sfDetail(bookId: String) = "sf_detail/$bookId"
    fun sfBrowse() = "sf_browse"
    fun sfComics() = "sf_comics"
    fun sfComic(slug: String) = "sf_comic_reader/$slug"
    fun sfRelatedLists(bookId: String) = "sf_related_lists/$bookId"
    fun sfAuthor(bookId: String, name: String) =
        "sf_author/$bookId?name=${android.net.Uri.encode(name)}"
    fun generate(bookId: String, chapterId: String, mode: String) =
        "generate/$bookId/$chapterId?mode=$mode"
    fun tree(bookId: String) = "tree/$bookId"
    fun providerEdit(providerId: String) = "provider_edit/$providerId"
    fun readerCompanion(bookId: String, quote: String, action: String = ""): String {
        val q = android.net.Uri.encode(quote)
        val a = android.net.Uri.encode(action)
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
    androidx.compose.runtime.LaunchedEffect(navBus) {
        navBus.openListening.collect {
            navController.navigate(Routes.LISTENING) {
                launchSingleTop = true
            }
        }
    }

    val currentSpeakingBookId by appViewModel.ttsPlayer.currentBookId.collectAsStateWithLifecycle()
    val currentReaderBookId = backStackEntry?.arguments?.getString("bookId")
    val isReadingCurrentTtsBook = currentRoute?.startsWith("reader") == true &&
        currentSpeakingBookId.isNotBlank() &&
        currentReaderBookId == currentSpeakingBookId

    val showCapsule = currentRoute != null &&
        !isReadingCurrentTtsBook &&
        currentRoute != Routes.LISTENING &&
        currentRoute != Routes.ONBOARDING

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop()

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = if (onboardingDone == true) Routes.BOOKSHELF else Routes.ONBOARDING,
                modifier = Modifier
                    .fillMaxSize()
                    .glassSource(hazeState),
                enterTransition = {
                    if (targetState.destination.route in topLevelRoutes) fadeIn(tween(100))
                    else slideInHorizontally(tween(220, easing = androidx.compose.animation.core.CubicBezierEasing(0.32f, 0.72f, 0f, 1f))) { it / 5 } + fadeIn(tween(160))
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
                composable(Routes.DISCOVER) {
                    DiscoverScreen(
                        onOpenBook = { navController.navigate(Routes.sfDetail(it)) },
                        onOpenCatalog = { navController.navigate("sf_catalog/$it") },
                        onOpenFanqie = { navController.navigate(Routes.FANQIE) },
                        onOpenNovelBrowse = { navController.navigate(Routes.sfBrowse()) },
                        onOpenComics = { navController.navigate(Routes.sfComics()) },
                        onOpenRecommendations = { navController.navigate(Routes.SF_RECOMMENDATIONS) }
                    )
                }
                composable(Routes.FANQIE) { com.novelai.assistant.ui.discover.FanqieScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.SF_BROWSE) {
                    com.novelai.assistant.ui.discover.SfBrowseScreen(
                        onBack = { navController.popBackStack() },
                        onOpenBookDetail = { navController.navigate(Routes.sfDetail(it)) }
                    )
                }
                composable(Routes.SF_COMICS) {
                    com.novelai.assistant.ui.discover.SfComicBrowseScreen(
                        onBack = { navController.popBackStack() },
                        onOpenComic = { navController.navigate(Routes.sfComic(it)) }
                    )
                }
                composable(Routes.SF_COMIC_READER, arguments = listOf(navArgument("slug") { type = NavType.StringType })) {
                    com.novelai.assistant.ui.discover.SfComicReaderScreen(
                        onBack = { navController.popBackStack() })
                }
                composable(Routes.SF_DETAIL, arguments = listOf(navArgument("bookId") { type = NavType.StringType })) { entry ->
                    val id = entry.arguments?.getString("bookId").orEmpty()
                    com.novelai.assistant.ui.discover.SfBookDetailScreen(
                        bookId = id,
                        onBack = { navController.popBackStack() },
                        onOpenReader = { bookId, chapterIndex ->
                            navController.navigate(if (chapterIndex >= 0) Routes.readerAt(bookId, chapterIndex) else Routes.reader(bookId))
                        },
                        onOpenCatalog = { bookId ->
                            navController.navigate("sf_catalog/$bookId")
                        },
                        onOpenRelatedLists = { navController.navigate(Routes.sfRelatedLists(it)) },
                        onOpenAuthor = { id, name -> navController.navigate(Routes.sfAuthor(id, name)) }
                    )
                }
                composable(Routes.SF_AUTHOR, arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" }
                )) { entry ->
                    com.novelai.assistant.ui.discover.SfAuthorScreen(
                        bookId = entry.arguments?.getString("bookId").orEmpty(),
                        authorName = entry.arguments?.getString("name").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenBook = { navController.navigate(Routes.sfDetail(it)) }
                    )
                }
                composable(Routes.SF_RECOMMENDATIONS) {
                    com.novelai.assistant.ui.discover.SfExploreScreen(
                        onBack = { navController.popBackStack() },
                        onOpenBookDetail = { navController.navigate(Routes.sfDetail(it)) })
                }
                composable(Routes.SF_RELATED_LISTS, arguments = listOf(navArgument("bookId") { type = NavType.StringType })) {
                    com.novelai.assistant.ui.discover.SfExploreScreen(
                        onBack = { navController.popBackStack() },
                        onOpenBookDetail = { navController.navigate(Routes.sfDetail(it)) })
                }
                composable(Routes.SF_CATALOG, arguments = listOf(navArgument("bookId") { type = NavType.StringType })) { entry ->
                    val id = entry.arguments?.getString("bookId").orEmpty()
                    com.novelai.assistant.ui.discover.SfCatalogScreen(id, { navController.popBackStack() }, { index -> navController.navigate(Routes.readerAt(id, index)) })
                }
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
            ttsPlayer = appViewModel.ttsPlayer,
            prefs = appViewModel.readingPreferencesRepository,
            hazeState = hazeState,
            onOpenListening = {
                navController.navigate(Routes.LISTENING) {
                    launchSingleTop = true
                }
            }
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
    hazeState: dev.chrisbanes.haze.HazeState,
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Color.Black.copy(alpha = 0.15f),
                spotColor = Color.Black.copy(alpha = 0.25f)
            )
            .liquidGlass(
                state = hazeState,
                shape = RoundedCornerShape(26.dp),
                blurRadius = 28.dp,
                
            )
            .padding(bottom = navBarPadding.calculateBottomPadding() * 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopLevelDestination.entries.forEach { dest ->
            val selected = currentRoute?.startsWith(dest.route) == true
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(dest.route)
                    }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = dest.icon,
                    contentDescription = dest.label,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = dest.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
    }
}
