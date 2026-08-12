package io.github.zalexanninev15.tokitoki

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.zalexanninev15.tokitoki.data.prefs.AppSettings
import io.github.zalexanninev15.tokitoki.nav.TokiTokiNavHost
import io.github.zalexanninev15.tokitoki.ui.theme.TokiTokiTheme

class MainActivity : ComponentActivity() {

    /** Set when the activity is resumed by an OAuth/MiAuth redirect. */
    private val pendingCallback = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingCallback.value = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data

        val container = (application as TokiTokiApp).container

        setContent {
            val settings by container.settingsStore.settings
                .collectAsState(initial = AppSettings())

            TokiTokiTheme(
                themeMode = settings.themeMode,
                fontSize = settings.fontSize,
                dynamicColor = settings.dynamicColor,
            ) {
                TokiTokiNavHost(
                    container = container,
                    authCallback = pendingCallback.value,
                    onAuthCallbackHandled = { pendingCallback.value = null },
                )
            }
        }
    }

    /**
     * launchMode="singleTask" means the redirect arrives here rather than as a new
     * activity instance, so the in-progress sign-in state is still around.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            pendingCallback.value = intent.data
        }
    }
}
