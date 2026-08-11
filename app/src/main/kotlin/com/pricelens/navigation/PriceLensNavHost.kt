package com.pricelens.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pricelens.feature.capture.navigation.CaptureRoute
import com.pricelens.feature.capture.navigation.captureScreen
import com.pricelens.feature.contribute.navigation.HistoryRoute
import com.pricelens.feature.contribute.navigation.contributeScreen
import com.pricelens.feature.contribute.navigation.historyScreen
import com.pricelens.feature.explore.navigation.exploreScreen
import com.pricelens.feature.review.navigation.ReviewRoute
import com.pricelens.feature.review.navigation.reviewScreen
import com.pricelens.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
object SettingsRoute

@Composable
fun PriceLensNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: Any = CaptureRoute
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        captureScreen(
            onNavigateToReview = { id ->
                navController.navigate(ReviewRoute(id))
            },
            onNavigateToSettings = {
                navController.navigate(SettingsRoute)
            }
        )
        reviewScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToHistory = {
                navController.navigate(HistoryRoute) {
                    popUpTo(CaptureRoute) { inclusive = false }
                }
            }
        )
        contributeScreen(onNavigateBack = { navController.popBackStack() })
        historyScreen(onNavigateBack = { navController.popBackStack() })
        exploreScreen(onNavigateBack = { navController.popBackStack() })
        composable<SettingsRoute> {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
