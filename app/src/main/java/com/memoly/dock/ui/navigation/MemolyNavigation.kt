package com.memoly.dock.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memoly.dock.ui.detail.DetailScreen
import com.memoly.dock.ui.editor.EditorScreen
import com.memoly.dock.ui.onboarding.OnboardingScreen
import com.memoly.dock.ui.settings.SettingsScreen
import com.memoly.dock.ui.timeline.TimelineScreen

/**
 * Navigation routes for Memoly.
 */
object MemolyRoutes {
    const val ONBOARDING = "onboarding"
    const val TIMELINE = "timeline"
    const val EDITOR = "editor?itemId={itemId}"
    const val DETAIL = "detail/{memoryId}"
    const val SETTINGS = "settings"

    fun editor(itemId: Long? = null): String {
        return if (itemId != null) "editor?itemId=$itemId" else "editor"
    }

    fun detail(memoryId: Long): String = "detail/$memoryId"
}

/**
 * Main navigation graph for Memoly.
 */
@Composable
fun MemolyNavigation(
    startDestination: String = MemolyRoutes.TIMELINE,
    onOnboardingComplete: () -> Unit = {},
    deepLinkMemoryId: Long? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { 300 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -300 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -300 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { 300 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        // Onboarding
        composable(MemolyRoutes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    onOnboardingComplete()
                    navController.navigate(MemolyRoutes.TIMELINE) {
                        popUpTo(MemolyRoutes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // Timeline
        composable(MemolyRoutes.TIMELINE) {
            TimelineScreen(
                onAddClick = { navController.navigate(MemolyRoutes.editor()) },
                onItemClick = { id -> navController.navigate(MemolyRoutes.detail(id)) },
                onSettingsClick = { navController.navigate(MemolyRoutes.SETTINGS) }
            )
        }

        // Editor (create or edit)
        composable(
            route = MemolyRoutes.EDITOR,
            arguments = listOf(
                navArgument("itemId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId")?.takeIf { it != -1L }
            EditorScreen(
                editItemId = itemId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Detail
        composable(
            route = MemolyRoutes.DETAIL,
            arguments = listOf(
                navArgument("memoryId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val memoryId = backStackEntry.arguments?.getLong("memoryId") ?: return@composable
            DetailScreen(
                memoryId = memoryId,
                onNavigateBack = { navController.popBackStack() },
                onEditClick = { id ->
                    navController.navigate(MemolyRoutes.editor(id))
                }
            )
        }

        // Settings
        composable(MemolyRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }

    // Handle deep link navigation (from notification tap)
    LaunchedEffect(deepLinkMemoryId) {
        deepLinkMemoryId?.let { id ->
            navController.navigate(MemolyRoutes.detail(id)) {
                launchSingleTop = true
            }
            onDeepLinkConsumed()
        }
    }
}
