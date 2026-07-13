package com.healthypantry.app.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * Uses a plain [Text] label as each tab's "icon" slot (first letter of the tab name) rather than
 * pulling in a Material icon library, matching this project's existing convention of textual
 * affordances over icon assets (e.g. `PantryListContent`'s `+` FAB, `RecipeScreen`'s buttons).
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
                icon = { Text(item.label.take(1)) },
                label = { Text(item.label) },
            )
        }
    }
}
