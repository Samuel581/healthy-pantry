package com.healthypantry.feature.planning.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthypantry.app.theme.HealthyPantryExtraColors
import com.healthypantry.app.theme.LocalHealthyPantryExtraColors
import com.healthypantry.core.unit.UnitConverter
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.planning.domain.model.MacroTotals
import com.healthypantry.feature.planning.domain.model.MealSlot
import com.healthypantry.feature.planning.domain.model.PlanEntry
import com.healthypantry.feature.planning.domain.model.PlanEntryType
import com.healthypantry.feature.planning.domain.usecase.ComputeMacroTotalsUseCase
import com.healthypantry.feature.planning.ui.vm.PlanEntryUi
import com.healthypantry.feature.planning.ui.vm.PlanUiState
import com.healthypantry.feature.planning.ui.vm.PlanViewModel
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.ui.vm.RecipeMacroSummary
import com.healthypantry.feature.recipes.ui.vm.RecipeViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Stateful container: hosts [PlanViewModel], surfaces [PlanViewModel.errorEvent] as a Snackbar
 * (same convention as `PantryListScreen`/`RecipeScreen`). Also hosts a second, independent
 * [RecipeViewModel] instance purely to read its already-computed
 * [com.healthypantry.feature.recipes.ui.vm.RecipeUiState.macroSummariesByRecipeId] for the day
 * summary card / meal cards below (see [entryMacroTotals] KDoc for why this is an approximation,
 * not a second source of truth) — no new macro-rollup logic is added to [PlanViewModel] itself.
 *
 * Wired into [com.healthypantry.app.navigation.HealthyPantryNavHost] starting in the Organic
 * planning-UI redesign PR: [onAddToSlot] navigates to
 * `com.healthypantry.feature.planning.ui.screen.PlanAssignScreen` for the tapped day/meal slot.
 */
@Composable
fun WeekPlanScreen(
    onAddToSlot: (day: Long, mealSlot: MealSlot) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = hiltViewModel(),
    recipeViewModel: RecipeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recipeUiState by recipeViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.errorEvent.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    WeekPlanContent(
        uiState = uiState,
        macroSummariesByRecipeId = recipeUiState.macroSummariesByRecipeId,
        onMarkEaten = viewModel::markEaten,
        onDeleteEntry = viewModel::deleteEntry,
        onAddToSlot = onAddToSlot,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Stateless/presentational half (container-presentational split, see `PantryListContent`).
 *
 * Design mockup ("Plan tab"): a horizontally-scrolling row of day chips (Mon..Sun), a "Planned for
 * {day}" summary card (total macros for every entry on the selected day, plus an "Eaten so far"
 * line once at least one is marked eaten), then one card per [MealSlot] — filled (assigned entry +
 * remove + eaten toggle) or empty ("+ Add {meal}"). All four [MealSlot] values are rendered (not
 * just Breakfast/Lunch/Dinner as literally pictured) so SNACK — a slot [PlanViewModel] already
 * fully supports — stays reachable from this screen.
 */
@Composable
fun WeekPlanContent(
    uiState: PlanUiState,
    onMarkEaten: (PlanEntry) -> Unit,
    onDeleteEntry: (PlanEntry) -> Unit,
    onAddToSlot: (day: Long, mealSlot: MealSlot) -> Unit,
    modifier: Modifier = Modifier,
    macroSummariesByRecipeId: Map<Long, RecipeMacroSummary> = emptyMap(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    today: LocalDate = LocalDate.now(),
) {
    val computeMacroTotalsUseCase = remember { ComputeMacroTotalsUseCase(UnitConverter()) }
    var selectedDay by remember {
        mutableStateOf(uiState.weekRange.days.find { it == today.toEpochDay() } ?: uiState.weekRange.days.firstOrNull() ?: today.toEpochDay())
    }

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize()
                    .semantics { contentDescription = "Loading week plan" },
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            else -> {
                val dayEntries = uiState.entries.filter { it.entry.dateEpochDay == selectedDay }
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DayChipsRow(
                        days = uiState.weekRange.days,
                        selectedDay = selectedDay,
                        onSelect = { selectedDay = it },
                    )
                    PlannedForDaySummaryCard(
                        selectedDay = selectedDay,
                        dayEntries = dayEntries,
                        recipes = uiState.recipes,
                        foodItems = uiState.foodItems,
                        macroSummariesByRecipeId = macroSummariesByRecipeId,
                        computeMacroTotalsUseCase = computeMacroTotalsUseCase,
                    )
                    MealSlot.entries.forEach { mealSlot ->
                        MealSlotCard(
                            mealSlot = mealSlot,
                            entryUi = dayEntries.find { it.entry.mealSlot == mealSlot },
                            recipes = uiState.recipes,
                            foodItems = uiState.foodItems,
                            macroSummariesByRecipeId = macroSummariesByRecipeId,
                            computeMacroTotalsUseCase = computeMacroTotalsUseCase,
                            onMarkEaten = onMarkEaten,
                            onDelete = onDeleteEntry,
                            onAdd = { onAddToSlot(selectedDay, mealSlot) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayChipsRow(
    days: List<Long>,
    selectedDay: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = days, key = { it }) { day ->
            val date = LocalDate.ofEpochDay(day)
            val selected = day == selectedDay
            Surface(
                onClick = { onSelect(day) },
                modifier = Modifier
                    .width(44.dp)
                    .height(56.dp)
                    .semantics { contentDescription = "Select ${date.dayOfWeek.name}" },
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlannedForDaySummaryCard(
    selectedDay: Long,
    dayEntries: List<PlanEntryUi>,
    recipes: List<Recipe>,
    foodItems: List<FoodItem>,
    macroSummariesByRecipeId: Map<Long, RecipeMacroSummary>,
    computeMacroTotalsUseCase: ComputeMacroTotalsUseCase,
    modifier: Modifier = Modifier,
) {
    val extraColors = LocalHealthyPantryExtraColors.current
    val dayLabel = LocalDate.ofEpochDay(selectedDay).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val totals = dayEntries.fold(MacroTotals.ZERO) { acc, entryUi ->
        val macros = entryMacroTotals(entryUi.entry, recipes, foodItems, macroSummariesByRecipeId, computeMacroTotalsUseCase)
        acc + (macros ?: MacroTotals.ZERO.copy(isComplete = false))
    }
    val eatenEntries = dayEntries.filter { it.entry.eaten }
    val eatenKcal = eatenEntries.sumOf { entryUi ->
        entryMacroTotals(entryUi.entry, recipes, foodItems, macroSummariesByRecipeId, computeMacroTotalsUseCase)?.calories ?: 0.0
    }

    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = extraColors.accent2_100) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Planned for $dayLabel".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = extraColors.accent2_800,
            )
            Text(
                text = "${fmt1(totals.calories)} kcal",
                style = MaterialTheme.typography.titleLarge,
                color = extraColors.accent2_800,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DaySummaryTag(text = "${fmt1(totals.proteinGrams)}g protein", extraColors = extraColors)
                DaySummaryTag(text = "${fmt1(totals.carbsGrams)}g carbs", extraColors = extraColors)
                DaySummaryTag(text = "${fmt1(totals.fatGrams)}g fat", extraColors = extraColors)
            }
            if (eatenEntries.isNotEmpty()) {
                Text(
                    text = "Eaten so far: ${fmt1(eatenKcal)} kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = extraColors.accent2_800,
                )
            }
        }
    }
}

@Composable
private fun DaySummaryTag(text: String, extraColors: HealthyPantryExtraColors, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.background) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = extraColors.accent2_800,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun MealSlotCard(
    mealSlot: MealSlot,
    entryUi: PlanEntryUi?,
    recipes: List<Recipe>,
    foodItems: List<FoodItem>,
    macroSummariesByRecipeId: Map<Long, RecipeMacroSummary>,
    computeMacroTotalsUseCase: ComputeMacroTotalsUseCase,
    onMarkEaten: (PlanEntry) -> Unit,
    onDelete: (PlanEntry) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extraColors = LocalHealthyPantryExtraColors.current
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = mealSlot.name, style = MaterialTheme.typography.labelSmall, color = extraColors.neutral600)

            if (entryUi == null) {
                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Add ${mealSlot.slotLabel()} entry" },
                    border = BorderStroke(1.5.dp, extraColors.neutral400),
                ) { Text("+ Add ${mealSlot.slotLabel()}") }
            } else {
                val macros = entryMacroTotals(entryUi.entry, recipes, foodItems, macroSummariesByRecipeId, computeMacroTotalsUseCase)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = entryTitle(entryUi, foodItems), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${fmt1(macros?.calories)} kcal · ${fmt1(macros?.proteinGrams)}g protein",
                            style = MaterialTheme.typography.bodySmall,
                            color = extraColors.neutral700,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { onDelete(entryUi.entry) },
                            modifier = Modifier.semantics { contentDescription = "Remove ${entryUi.displayName} entry" },
                        ) { Icon(Icons.Rounded.Close, contentDescription = null) }
                        EatenToggle(
                            eaten = entryUi.entry.eaten,
                            displayName = entryUi.displayName,
                            onMarkEaten = { onMarkEaten(entryUi.entry) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Switch-looking eaten toggle — matches the mockup's 52x30 pill track visually, but is
 * intentionally NOT a real bidirectional switch: [PlanEntryRepository][com.healthypantry.feature.planning.data.repo.PlanEntryRepository]/
 * `PlanEntryDao.markEaten` has no `unmarkEaten` counterpart (reversing it would mean crediting
 * back an ambiguous FIFO stock-batch decrement — a deliberate decision, not an oversight). Once
 * [eaten] is `true` the control is both `enabled = false` (so it's visibly non-interactive, matches
 * `Surface`'s disabled affordance) AND its `onClick` is a no-op even if invoked programmatically —
 * belt-and-suspenders, same "UI-layer defense in depth" convention `PlanViewModel`'s
 * `isSaving`-guarded actions already use elsewhere in this feature.
 */
@Composable
private fun EatenToggle(
    eaten: Boolean,
    displayName: String,
    onMarkEaten: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extraColors = LocalHealthyPantryExtraColors.current
    Surface(
        onClick = { if (!eaten) onMarkEaten() },
        enabled = !eaten,
        modifier = modifier
            .width(52.dp)
            .height(30.dp)
            .semantics { contentDescription = if (eaten) "$displayName eaten" else "Mark $displayName eaten" },
        shape = MaterialTheme.shapes.large,
        color = if (eaten) MaterialTheme.colorScheme.primary else extraColors.neutral300,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(3.dp),
            contentAlignment = if (eaten) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Surface(modifier = Modifier.size(24.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                if (eaten) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** "Breakfast"/"Snack" — title-case display form of a [MealSlot], e.g. `BREAKFAST` -> `Breakfast`. */
private fun MealSlot.slotLabel(): String = name.lowercase().replaceFirstChar { it.uppercase() }

/**
 * Card title for one [PlanEntryUi]: the recipe name as-is for [PlanEntryType.RECIPE], or
 * "{qty} {unit} {item}" for [PlanEntryType.ITEM] (spec mockup) — falls back to the bare
 * [PlanEntryUi.displayName] when [foodItems] doesn't (yet) contain the referenced item.
 */
private fun entryTitle(entryUi: PlanEntryUi, foodItems: List<FoodItem>): String {
    val entry = entryUi.entry
    if (entry.type != PlanEntryType.ITEM) return entryUi.displayName
    val foodItem = entry.foodItemId?.let { id -> foodItems.find { it.id == id } } ?: return entryUi.displayName
    val quantity = entry.quantity ?: return entryUi.displayName
    return "${fmt1(quantity)} ${foodItem.canonicalUnit.name.lowercase()} ${entryUi.displayName}"
}

/**
 * Best-effort [MacroTotals] for one [PlanEntry], resolved entirely from data already in
 * [PlanUiState]/[macroSummariesByRecipeId] — no repository access, same "screen-layer, best-effort"
 * convention as `RecipeScreen`'s own `computeFormMacroTotals`.
 *
 * [PlanEntryType.ITEM] entries are exact (a single [FoodItem]'s per-canonical-unit macros times
 * [PlanEntry.quantity], via [ComputeMacroTotalsUseCase.computeForQuickAdd]). [PlanEntryType.RECIPE]
 * entries are an approximation: [macroSummariesByRecipeId] (from a sibling `RecipeViewModel`) is
 * already scaled to that [Recipe]'s OWN default [Recipe.servings], so it's re-scaled here by
 * `entry.servings / recipe.servings` rather than re-resolving the recipe's ingredients against
 * [PlanEntry.servings] directly (that would need `RecipeWithIngredients` + registered
 * `ConversionFactor`s, which this screen layer doesn't have — see `PlanViewModel.resolveWeeklyNeeds`
 * for where that heavier resolution already happens for a different purpose).
 */
private fun entryMacroTotals(
    entry: PlanEntry,
    recipes: List<Recipe>,
    foodItems: List<FoodItem>,
    macroSummariesByRecipeId: Map<Long, RecipeMacroSummary>,
    computeMacroTotalsUseCase: ComputeMacroTotalsUseCase,
): MacroTotals? = when (entry.type) {
    PlanEntryType.ITEM -> {
        val foodItem = entry.foodItemId?.let { id -> foodItems.find { it.id == id } }
        val quantity = entry.quantity
        if (foodItem != null && quantity != null) {
            computeMacroTotalsUseCase.computeForQuickAdd(foodItem, quantity)
        } else {
            null
        }
    }

    PlanEntryType.RECIPE -> {
        val recipe = entry.recipeId?.let { id -> recipes.find { it.id == id } }
        val summary = entry.recipeId?.let { macroSummariesByRecipeId[it] }
        val servings = entry.servings
        if (recipe != null && summary != null && servings != null && recipe.servings > 0) {
            summary.macroTotals.scaledBy(servings / recipe.servings)
        } else {
            null
        }
    }
}

private fun MacroTotals.scaledBy(ratio: Double): MacroTotals = MacroTotals(
    calories = calories * ratio,
    proteinGrams = proteinGrams * ratio,
    carbsGrams = carbsGrams * ratio,
    fatGrams = fatGrams * ratio,
    isComplete = isComplete,
)

/** One-decimal formatting, e.g. `500.0`; `—` for an unknown (`null`) macro value — same convention
 * as `PantryListScreen`'s `fmt1`. */
private fun fmt1(value: Double?): String = if (value == null) "—" else "%.1f".format(value)
