package io.github.zalexanninev15.tokitoki.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.zalexanninev15.tokitoki.AppContainer
import io.github.zalexanninev15.tokitoki.ui.accounts.AccountsScreen
import io.github.zalexanninev15.tokitoki.ui.auth.AddAccountScreen
import io.github.zalexanninev15.tokitoki.ui.feed.FeedScreen
import io.github.zalexanninev15.tokitoki.ui.media.ImageViewerScreen
import io.github.zalexanninev15.tokitoki.ui.settings.SettingsScreen

object Routes {
    const val FEED = "feed"
    const val ACCOUNTS = "accounts"
    const val ADD_ACCOUNT = "accounts/add"
    const val SETTINGS = "settings"
    const val IMAGE = "image/{url}"

    fun image(url: String): String = "image/" + Uri.encode(url)
}

@Composable
fun TokiTokiNavHost(
    container: AppContainer,
    authCallback: Uri?,
    onAuthCallbackHandled: () -> Unit,
) {
    val navController = rememberNavController()

    // A redirect can land while any screen is showing, so completion is handled here
    // rather than inside the sign-in screen, which may well have been destroyed.
    LaunchedEffect(authCallback) {
        if (authCallback != null) {
            navController.navigate(Routes.ADD_ACCOUNT) {
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.FEED) {
        composable(Routes.FEED) {
            FeedScreen(
                container = container,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                onOpenImage = { url -> navController.navigate(Routes.image(url)) },
            )
        }

        composable(Routes.ACCOUNTS) {
            AccountsScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onAddAccount = { navController.navigate(Routes.ADD_ACCOUNT) },
            )
        }

        composable(Routes.ADD_ACCOUNT) {
            AddAccountScreen(
                container = container,
                callback = authCallback,
                onCallbackHandled = onAuthCallbackHandled,
                onDone = {
                    navController.popBackStack(Routes.FEED, inclusive = false)
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(container = container, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.IMAGE,
            arguments = listOf(navArgument("url") { type = NavType.StringType }),
        ) { entry ->
            ImageViewerScreen(
                url = Uri.decode(entry.arguments?.getString("url").orEmpty()),
                onClose = { navController.popBackStack() },
            )
        }
    }
}
