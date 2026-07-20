package com.healthypantry.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.healthypantry.feature.pantry.ui.screen.ItemFormScreen
import com.healthypantry.feature.pantry.ui.screen.PantryListScreen
import com.healthypantry.feature.pantry.ui.vm.PantryViewModel
import com.healthypantry.feature.planning.ui.screen.WeekPlanScreen
import com.healthypantry.feature.recipes.ui.screen.RecipeScreen

/**
 * Top-level nav graph wiring together every existing screen (`PantryListScreen`/`ItemFormScreen`,
 * `RecipeScreen`, `WeekPlanScreen`) behind the three bottom-nav tabs (see
 * [BOTTOM_NAV_DESTINATIONS] / `HealthyPantryBottomBar`).
 *
 * Only the Pantry tab needs its own nested graph ([Destinations.PANTRY_GRAPH]: list -> add-item
 * form) since `ItemFormScreen` is a distinct destination reachable from the list's `+` FAB.
 * `RecipeScreen`/`WeekPlanScreen` already own an internal list/form toggle, so they stay single
 * top-level destinations here (no change to their own internal navigation).
 */
@Composable
fun HealthyPantryNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.PANTRY_GRAPH,
        modifier = modifier,
    ) {
        navigation(startDestination = Destinations.PANTRY_LIST, route = Destinations.PANTRY_GRAPH) {
            composable(Destinations.PANTRY_LIST) {
                PantryListScreen(
                    onAddItem = { navController.navigate(Destinations.itemForm()) },
                    onEditItem = { item -> navController.navigate(Destinations.itemForm(item.id)) }
                )
            }
            composable(
                route = Destinations.ITEM_FORM,
                arguments = listOf(
                    navArgument("itemId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val itemIdString = backStackEntry.arguments?.getString("itemId")
                val itemId = itemIdString?.toLongOrNull()
                val pantryViewModel: PantryViewModel = hiltViewModel()
                val existingItem by if (itemId != null) {
                    pantryViewModel.getItem(itemId).collectAsState(initial = null)
                } else {
                    remember { mutableStateOf(null) }
                }

                ItemFormScreen(
                    existingItem = existingItem,
                    onSaved = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }
        }
        composable(Destinations.RECIPES) { RecipeScreen() }
        composable(Destinations.PLAN) { WeekPlanScreen() }
    }
}
