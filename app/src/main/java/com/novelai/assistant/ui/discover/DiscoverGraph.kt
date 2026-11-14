package com.novelai.assistant.ui.discover

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.novelai.assistant.ui.novel.Routes

fun NavGraphBuilder.discoverGraph(
    navController: NavHostController,
    onOpenReader: (bookId: String, chapterIndex: Int) -> Unit
) {
    composable(Routes.DISCOVER) {
        DiscoverScreen(
            onOpenBook = { navController.navigate(Routes.sfDetail(it)) },
            onOpenCatalog = { navController.navigate(Routes.sfCatalog(it)) },
            onOpenFanqie = { navController.navigate(Routes.FANQIE) },
            onOpenNovelBrowse = { navController.navigate(Routes.sfBrowse()) },
            onOpenComics = { navController.navigate(Routes.sfComics()) },
            onOpenRecommendations = { navController.navigate(Routes.SF_RECOMMENDATIONS) }
        )
    }
    composable(Routes.FANQIE) {
        FanqieScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.SF_BROWSE) {
        SfBrowseScreen(
            onBack = { navController.popBackStack() },
            onOpenBookDetail = { navController.navigate(Routes.sfDetail(it)) }
        )
    }
    composable(Routes.SF_COMICS) {
        SfComicBrowseScreen(
            onBack = { navController.popBackStack() },
            onOpenComic = { navController.navigate(Routes.sfComic(it)) }
        )
    }
    composable(
        Routes.SF_COMIC_READER,
        arguments = listOf(navArgument("slug") { type = NavType.StringType })
    ) {
        SfComicReaderScreen(onBack = { navController.popBackStack() })
    }
    composable(
        Routes.SF_DETAIL,
        arguments = listOf(navArgument("bookId") { type = NavType.StringType })
    ) { entry ->
        val id = entry.arguments?.getString("bookId").orEmpty()
        SfBookDetailScreen(
            bookId = id,
            onBack = { navController.popBackStack() },
            onOpenReader = onOpenReader,
            onOpenCatalog = { bookId -> navController.navigate(Routes.sfCatalog(bookId)) },
            onOpenRelatedLists = { navController.navigate(Routes.sfRelatedLists(it)) },
            onOpenAuthor = { authorBookId, name -> navController.navigate(Routes.sfAuthor(authorBookId, name)) }
        )
    }
    composable(
        Routes.SF_AUTHOR,
        arguments = listOf(
            navArgument("bookId") { type = NavType.StringType },
            navArgument("name") { type = NavType.StringType; defaultValue = "" }
        )
    ) { entry ->
        SfAuthorScreen(
            bookId = entry.arguments?.getString("bookId").orEmpty(),
            authorName = entry.arguments?.getString("name").orEmpty(),
            onBack = { navController.popBackStack() },
            onOpenBook = { navController.navigate(Routes.sfDetail(it)) }
        )
    }
    composable(Routes.SF_RECOMMENDATIONS) {
        SfExploreScreen(
            onBack = { navController.popBackStack() },
            onOpenBookDetail = { navController.navigate(Routes.sfDetail(it)) }
        )
    }
    composable(
        Routes.SF_RELATED_LISTS,
        arguments = listOf(navArgument("bookId") { type = NavType.StringType })
    ) {
        SfExploreScreen(
            onBack = { navController.popBackStack() },
            onOpenBookDetail = { navController.navigate(Routes.sfDetail(it)) }
        )
    }
    composable(
        Routes.SF_CATALOG,
        arguments = listOf(navArgument("bookId") { type = NavType.StringType })
    ) { entry ->
        val id = entry.arguments?.getString("bookId").orEmpty()
        SfCatalogScreen(
            bookId = id,
            onBack = { navController.popBackStack() },
            onRead = { index -> onOpenReader(id, index) }
        )
    }
}
