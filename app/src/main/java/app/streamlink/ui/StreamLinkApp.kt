package app.streamlink.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.streamlink.i18n.L10n
import app.streamlink.i18n.LocalLanguage
import app.streamlink.ui.screens.HomeScreen
import app.streamlink.ui.screens.SettingsScreen
import app.streamlink.ui.screens.TargetsScreen
import app.streamlink.ui.screens.VdoNinjaScreen
import app.streamlink.ui.screens.VdoNinjaWebScreen

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val TARGETS = "targets"
    const val VDONINJA = "vdoninja"
    const val VDONINJA_WEB = "vdoninjaweb?url={url}&title={title}"

    fun vdoNinjaWeb(url: String, title: String): String =
        "vdoninjaweb?url=${Uri.encode(url)}&title=${Uri.encode(title)}"
}

@Composable
fun StreamLinkApp(viewModel: StreamViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // PT: Mantém o tradutor fora de composables sincronizado com a escolha do
    // utilizador. / EN: Keeps the non-composable translator in sync with the
    // user's selection.
    SideEffect { L10n.update(settings.language) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    CompositionLocalProvider(LocalLanguage provides settings.language) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenTargets = { navController.navigate(Routes.TARGETS) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenTargets = { navController.navigate(Routes.TARGETS) },
                    onOpenVdoNinja = { navController.navigate(Routes.VDONINJA) }
                )
            }
            composable(Routes.TARGETS) {
                TargetsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenVdoNinja = { navController.navigate(Routes.VDONINJA) }
                )
            }
            composable(Routes.VDONINJA) {
                VdoNinjaScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenWeb = { url ->
                        navController.navigate(Routes.vdoNinjaWeb(url, "VDO.Ninja"))
                    },
                    onTargetReady = {
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    }
                )
            }
            composable(
                route = Routes.VDONINJA_WEB,
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType; defaultValue = "" },
                    navArgument("title") { type = NavType.StringType; defaultValue = "VDO.Ninja" }
                )
            ) { entry ->
                VdoNinjaWebScreen(
                    url = entry.arguments?.getString("url").orEmpty(),
                    title = entry.arguments?.getString("title").orEmpty().ifBlank { "VDO.Ninja" },
                    onEnter = viewModel::pauseMicMonitoring,
                    onExit = viewModel::resumeMicMonitoring,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
    }
}
