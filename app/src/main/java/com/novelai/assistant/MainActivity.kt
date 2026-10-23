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
                onDispose {}
            }
            NovelAITheme(darkTheme = darkTheme) {
                NovelApp(listeningNavBus = listeningNavBus)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkListeningIntent(intent)
    }

    private fun checkListeningIntent(intent: Intent?) {
        if (intent?.getStringExtra("EXTRA_ROUTE") == "listening") {
            intent.removeExtra("EXTRA_ROUTE")
            listeningNavBus.requestOpenListening()
        }
    }
}
