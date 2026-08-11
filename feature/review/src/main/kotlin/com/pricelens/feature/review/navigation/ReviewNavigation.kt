package com.pricelens.feature.review.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.pricelens.feature.review.ReviewScreen
import kotlinx.serialization.Serializable

@Serializable
data class ReviewRoute(val observationId: String)

fun NavGraphBuilder.reviewScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    composable<ReviewRoute>(
        deepLinks = listOf(
            navDeepLink { uriPattern = "pricelens://review/{observationId}" }
        )
    ) {
        ReviewScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToHistory = onNavigateToHistory
        )
    }
}
