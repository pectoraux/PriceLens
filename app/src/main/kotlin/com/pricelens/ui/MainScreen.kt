package com.pricelens.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pricelens.feature.capture.navigation.CaptureRoute
import com.pricelens.feature.contribute.navigation.ContributeRoute
import com.pricelens.feature.contribute.navigation.HistoryRoute
import com.pricelens.feature.explore.navigation.ExploreRoute
import com.pricelens.navigation.PriceLensNavHost

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        NavigationItem(
            label = "Capture",
            icon = Icons.Default.Home,
            route = CaptureRoute
        ),
        NavigationItem(
            label = "Explore",
            icon = Icons.Default.Search,
            route = ExploreRoute()
        ),
        NavigationItem(
            label = "Contribute",
            icon = Icons.Default.Add,
            route = ContributeRoute
        ),
        NavigationItem(
            label = "History",
            icon = Icons.AutoMirrored.Filled.List,
            route = HistoryRoute
        )
    )

    // Hide navigation bar on sub-screens like Review or Settings
    val showBottomBar = items.any { item ->
        currentDestination?.hierarchy?.any { it.hasRoute(item.route::class) } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    items.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.hasRoute(item.route::class) } == true
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        PriceLensNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

private data class NavigationItem<T : Any>(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: T
)
