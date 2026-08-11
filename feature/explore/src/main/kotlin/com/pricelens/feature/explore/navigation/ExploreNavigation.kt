package com.pricelens.feature.explore.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.pricelens.feature.explore.ExploreScreen
import kotlinx.serialization.Serializable

@Serializable
data class ExploreRoute(val geohash: String? = null)

fun NavGraphBuilder.exploreScreen(onNavigateBack: () -> Unit) {
    composable<ExploreRoute>(
        deepLinks = listOf(
            navDeepLink { uriPattern = "pricelens://locality/{geohash}" }
        )
    ) {
        ExploreScreen(onNavigateBack = onNavigateBack)
    }
}
