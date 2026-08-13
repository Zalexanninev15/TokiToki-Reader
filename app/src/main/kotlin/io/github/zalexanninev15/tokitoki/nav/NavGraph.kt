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
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.zalexanninev15.tokitoki.ui.feed.FeedScreen
import io.github.zalexanninev15.tokitoki.ui.follows.FollowsScreen
import io.github.zalexanninev15.tokitoki.ui.follows.FollowsViewModel
import io.github.zalexanninev15.tokitoki.ui.media.ImageViewerScreen
import io.github.zalexanninev15.tokitoki.ui.settings.SettingsScreen

object Routes {
    const val FEED = "feed"
    const val ACCOUNTS = "accounts"
    const val ADD_ACCOUNT = "accounts/add"
    const val SETTINGS = "settings"
    const val IMAGE = "image/{url}"
    const val FOLLOWS = "follows/{accountId}"

    fun follows(accountId: String): String = "follows/${Uri.encode(accountId)}"

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
                // Long-pressing a source tab jumps straight to that account's
                // subscriptions, which is the shortcut requested for the feature.
                onOpenFollows = { id -> navController.navigate(Routes.follows(id)) },
                onOpenImage = { url -> navController.navigate(Routes.image(url)) },
            )
        }

        composable(Routes.ACCOUNTS) {
            AccountsScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onAddAccount = { navController.navigate(Routes.ADD_ACCOUNT) },
                onOpenFollows = { id -> navController.navigate(Routes.follows(id)) },
            )
        }

        composable(
            route = Routes.FOLLOWS,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
        ) { entry ->
            val context = LocalContext.current
            val accountId = Uri.decode(entry.arguments?.getString("accountId").orEmpty())
            val viewModel: FollowsViewModel = viewModel(
                factory = FollowsViewModel.Factory(container.followsRepository, accountId),
            )
            FollowsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenProfile = { url ->
                    CustomTabsIntent.Builder().setShowTitle(true).build()
                        .launchUrl(context, url.toUri())
                },
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
