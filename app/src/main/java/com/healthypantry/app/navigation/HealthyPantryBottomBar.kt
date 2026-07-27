package com.healthypantry.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Material3 bottom [NavigationBar] for the three top-level tabs ([BOTTOM_NAV_DESTINATIONS]).
 * Follows the standard Compose Navigation save/restore-state convention for bottom nav: `popUpTo`
 * the graph's own start destination with `saveState = true`, `launchSingleTop = true`,
 * `restoreState = true` — switching tabs preserves each tab's own back stack/scroll position
 * instead of recreating it from scratch on every tap.
 *
 * Each tab's icon slot uses a real Material icon ([bottomNavIcon]); the selected-state pill
 * background comes from M3's own [NavigationBarItem] indicator, themed automatically via the
 * Organic [androidx.compose.material3.ColorScheme] (see `HealthyPantryTheme`).
 */
@Composable
fun HealthyPantryBottomBar(navController: NavHostController) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    NavigationBar {
        BOTTOM_NAV_DESTINATIONS.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(imageVector = bottomNavIcon(item.route), contentDescription = null) },
                label = { Text(item.label) },
            )
        }
    }
}

/** Maps a [BottomNavDestination.route] to its tab icon. */
private fun bottomNavIcon(route: String): ImageVector = when (route) {
    Destinations.PANTRY_GRAPH -> Icons.Rounded.Inventory2
    Destinations.RECIPES -> Icons.Rounded.MenuBook
    Destinations.PLAN -> Icons.Rounded.CalendarMonth
    else -> Icons.Rounded.Inventory2
}
