package com.pricelens.feature.capture.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.pricelens.feature.capture.CaptureScreen
import kotlinx.serialization.Serializable

@Serializable
object CaptureRoute

fun NavGraphBuilder.captureScreen(
    onNavigateToReview: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    composable<CaptureRoute> {
        CaptureScreen(
            onNavigateToReview = onNavigateToReview,
            onNavigateToSettings = onNavigateToSettings
        )
    }
}
