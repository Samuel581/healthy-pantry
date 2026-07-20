package com.healthypantry.app.navigation

/**
 * Route constants for the app's single [androidx.navigation.NavHost] (see
 * `HealthyPantryNavHost`). Kept as plain string constants (not a sealed/type-safe route
 * hierarchy) to match this project's preference for simple, explicit code over reflection-driven
 * navigation APIs elsewhere (see e.g. `PantryViewModel`'s hand-rolled `StateFlow` composition).
 */
object Destinations {
    /** Nested graph route for the Pantry tab (list -> add/edit item form). */
    const val PANTRY_GRAPH = "pantry_graph"
    const val PANTRY_LIST = "pantry_list"
    const val ITEM_FORM = "item_form?itemId={itemId}"

    fun itemForm(itemId: Long? = null) = if (itemId != null) "item_form?itemId=$itemId" else "item_form"

    /** `RecipeScreen`/`WeekPlanScreen` already own an internal list/form toggle (see their own
     * KDoc), so each is a single top-level destination here, not its own nested graph. */
    const val RECIPES = "recipes"
    const val PLAN = "plan"
}

/** One entry in [BOTTOM_NAV_DESTINATIONS]. */
data class BottomNavDestination(val route: String, val label: String)

/**
 * The three top-level tabs (spec/proposal.md "Scaffold + bottom NavigationBar"). [PANTRY_GRAPH]
 * (not [Destinations.PANTRY_LIST]) is the tab's route so switching back to the Pantry tab always
 * resumes wherever that nested graph's own back stack was left (see `HealthyPantryBottomBar`).
 */
val BOTTOM_NAV_DESTINATIONS = listOf(
    BottomNavDestination(Destinations.PANTRY_GRAPH, "Pantry"),
    BottomNavDestination(Destinations.RECIPES, "Recipes"),
    BottomNavDestination(Destinations.PLAN, "Plan"),
)
