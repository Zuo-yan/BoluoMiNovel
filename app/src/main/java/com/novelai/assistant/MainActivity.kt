package com.novelai.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.generation.GenerationNavBus
import com.novelai.assistant.data.prefs.AppThemeMode
import com.novelai.assistant.data.tts.ListeningNavBus
import com.novelai.assistant.ui.novel.AppViewModel
import com.novelai.assistant.ui.novel.NovelApp
import com.novelai.assistant.ui.theme.NovelAITheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var listeningNavBus: ListeningNavBus

    @Inject
    lateinit var generationNavBus: GenerationNavBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkListeningIntent(intent)
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val themeMode by appViewModel.appThemeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
            }
            androidx.compose.runtime.DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    }
                )
                // 关闭系统导航栏对比度遮罩，避免底部出现半透明白块
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                onDispose {}
            }
            NovelAITheme(darkTheme = darkTheme) {
                NovelApp(
                    listeningNavBus = listeningNavBus,
                    generationNavBus = generationNavBus
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkListeningIntent(intent)
    }

    private fun checkListeningIntent(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_ROUTE)) {
            ROUTE_LISTENING -> {
                intent.removeExtra(EXTRA_ROUTE)
                listeningNavBus.requestOpenListening()
            }
            ROUTE_GENERATE -> {
                val bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
                val chapterId = intent.getStringExtra(EXTRA_CHAPTER_ID).orEmpty()
                val mode = intent.getStringExtra(EXTRA_MODE) ?: "CONTINUATION"
                intent.removeExtra(EXTRA_ROUTE)
                if (bookId.isNotBlank()) {
                    generationNavBus.requestOpenGeneration(bookId, chapterId, mode)
                }
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "EXTRA_ROUTE"
        const val ROUTE_LISTENING = "listening"
        const val ROUTE_GENERATE = "generate"
        const val EXTRA_BOOK_ID = "EXTRA_BOOK_ID"
        const val EXTRA_CHAPTER_ID = "EXTRA_CHAPTER_ID"
        const val EXTRA_MODE = "EXTRA_MODE"
    }
}
