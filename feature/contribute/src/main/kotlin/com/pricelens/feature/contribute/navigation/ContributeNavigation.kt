package com.pricelens.feature.contribute.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.pricelens.feature.contribute.ContributeScreen
import com.pricelens.feature.contribute.history.HistoryScreen
import kotlinx.serialization.Serializable

@Serializable
object ContributeRoute

@Serializable
object HistoryRoute

fun NavGraphBuilder.contributeScreen(onNavigateBack: () -> Unit) {
    composable<ContributeRoute> {
        ContributeScreen(onNavigateBack = onNavigateBack)
    }
}

fun NavGraphBuilder.historyScreen(onNavigateBack: () -> Unit) {
    composable<HistoryRoute> {
        HistoryScreen(onNavigateBack = onNavigateBack)
    }
}
