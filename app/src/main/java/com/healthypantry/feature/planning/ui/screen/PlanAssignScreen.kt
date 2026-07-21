package com.healthypantry.feature.planning.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthypantry.app.theme.LocalHealthyPantryExtraColors
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.planning.domain.model.MealSlot
import com.healthypantry.feature.planning.ui.vm.PlanViewModel
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.ui.vm.RecipeMacroSummary
import com.healthypantry.feature.recipes.ui.vm.RecipeViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Which half of [PlanAssignContent]'s segmented toggle is active. */
private enum class PlanAssignMode { RECIPE, QUICK_ADD }

/**
 * Recipe-or-quick-add picker for one day/[MealSlot] (Destinations.PLAN_ASSIGN, wired starting in
 * the Organic planning-UI redesign PR). Reached by tapping a "+ Add {meal}" button on
 * [WeekPlanScreen].
 *
 * "Assign" REPLACES whatever entry already occupies [day]/[mealSlot], matching the design
 * mockup's `confirmAssign` behavior: [PlanViewModel] has no combined "replace this slot's entry"
 * method (`assignRecipe`/`quickAddItem` only ever create a brand-new [com.healthypantry.feature.planning.domain.model.PlanEntry],
 * id `0`), so this container finds any existing entry for [day]/[mealSlot] from [PlanViewModel.uiState]
 * itself and deletes it before assigning the new one — see `replaceSlotEntry` below. The delete and
 * the create/quick-add run as two independent guarded writes (not one transaction), but that's safe
 * here: they never target the same row (the existing entry keeps its own id; the new one gets a
 * fresh one), so whichever completes first, the final state is the same.
 */
@Composable
fun PlanAssignScreen(
    day: Long,
    mealSlot: MealSlot,
    onBack: () -> Unit,
    onAssigned: () -> Unit,
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

    fun replaceSlotEntry() {
        uiState.entries
            .find { it.entry.dateEpochDay == day && it.entry.mealSlot == mealSlot }
            ?.let { viewModel.deleteEntry(it.entry) }
    }

    PlanAssignContent(
        day = day,
        mealSlot = mealSlot,
        recipes = uiState.recipes,
        foodItems = uiState.foodItems,
        macroSummariesByRecipeId = recipeUiState.macroSummariesByRecipeId,
        isSaving = uiState.isSaving,
        onBack = onBack,
        onAssignRecipe = { recipeId, servings ->
            replaceSlotEntry()
            viewModel.assignRecipe(day, mealSlot, recipeId, servings)
            onAssigned()
        },
        onQuickAdd = { foodItemId, quantity ->
            replaceSlotEntry()
            viewModel.quickAddItem(day, mealSlot, foodItemId, quantity)
            onAssigned()
        },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/** Stateless/presentational half (container-presentational split, see `PantryListContent`). */
@Composable
fun PlanAssignContent(
    day: Long,
    mealSlot: MealSlot,
    recipes: List<Recipe>,
    foodItems: List<FoodItem>,
    macroSummariesByRecipeId: Map<Long, RecipeMacroSummary>,
    isSaving: Boolean,
    onBack: () -> Unit,
    onAssignRecipe: (recipeId: Long, servings: Double) -> Unit,
    onQuickAdd: (foodItemId: Long, quantity: Double) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var mode by remember { mutableStateOf(PlanAssignMode.RECIPE) }
    var selectedRecipeId by remember { mutableStateOf<Long?>(null) }
    var selectedFoodItemId by remember { mutableStateOf<Long?>(null) }
    var quantityText by remember { mutableStateOf("1") }

    val dayLabel = LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val quantity = quantityText.toDoubleOrNull()
    val canAssign = !isSaving && when (mode) {
        PlanAssignMode.RECIPE -> selectedRecipeId != null
        PlanAssignMode.QUICK_ADD -> selectedFoodItemId != null && quantity != null
    }

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlanAssignHeader(dayLabel = dayLabel, mealSlot = mealSlot, onBack = onBack)

            AssignModeToggle(mode = mode, onSelect = { mode = it })

            when (mode) {
                PlanAssignMode.RECIPE -> RecipePickerList(
                    recipes = recipes,
                    macroSummariesByRecipeId = macroSummariesByRecipeId,
                    selectedRecipeId = selectedRecipeId,
                    onSelect = { selectedRecipeId = it },
                )

                PlanAssignMode.QUICK_ADD -> QuickAddPicker(
                    foodItems = foodItems,
                    selectedFoodItemId = selectedFoodItemId,
                    onSelectFoodItem = { selectedFoodItemId = it },
                    quantityText = quantityText,
                    onQuantityChanged = { quantityText = it },
                )
            }

            Button(
                onClick = {
                    when (mode) {
                        PlanAssignMode.RECIPE -> selectedRecipeId?.let { recipeId ->
                            val servings = recipes.find { it.id == recipeId }?.servings?.toDouble() ?: 1.0
                            onAssignRecipe(recipeId, servings)
                        }

                        PlanAssignMode.QUICK_ADD -> {
                            val foodItemId = selectedFoodItemId
                            if (foodItemId != null && quantity != null) onQuickAdd(foodItemId, quantity)
                        }
                    }
                },
                enabled = canAssign,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Assign to ${mealSlot.name.lowercase()}" },
            ) { Text("Assign to ${mealSlot.name.lowercase().replaceFirstChar { it.uppercase() }}") }
        }
    }
}

@Composable
private fun PlanAssignHeader(dayLabel: String, mealSlot: MealSlot, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = null)
        }
        Text(
            text = "$dayLabel · ${mealSlot.name.lowercase().replaceFirstChar { it.uppercase() }}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun AssignModeToggle(mode: PlanAssignMode, onSelect: (PlanAssignMode) -> Unit, modifier: Modifier = Modifier) {
    val extraColors = LocalHealthyPantryExtraColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color = extraColors.neutral200, shape = MaterialTheme.shapes.large)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AssignModeOption(
            label = "Recipe",
            selected = mode == PlanAssignMode.RECIPE,
            onClick = { onSelect(PlanAssignMode.RECIPE) },
            modifier = Modifier.weight(1f),
        )
        AssignModeOption(
            label = "Quick add",
            selected = mode == PlanAssignMode.QUICK_ADD,
            onClick = { onSelect(PlanAssignMode.QUICK_ADD) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AssignModeOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val extraColors = LocalHealthyPantryExtraColors.current
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = "$label mode" },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) extraColors.accent800 else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun RecipePickerList(
    recipes: List<Recipe>,
    macroSummariesByRecipeId: Map<Long, RecipeMacroSummary>,
    selectedRecipeId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (recipes.isEmpty()) {
            Text(text = "No recipes yet.", style = MaterialTheme.typography.bodyMedium)
        }
        recipes.forEach { recipe ->
            RecipePickerRow(
                recipe = recipe,
                macroSummary = macroSummariesByRecipeId[recipe.id],
                selected = recipe.id == selectedRecipeId,
                onClick = { onSelect(recipe.id) },
            )
        }
    }
}

@Composable
private fun RecipePickerRow(
    recipe: Recipe,
    macroSummary: RecipeMacroSummary?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extraColors = LocalHealthyPantryExtraColors.current
    val macros = macroSummary?.macroTotals
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Select recipe ${recipe.name}" },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) extraColors.accent100 else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(1.5.dp, extraColors.accent500) else null,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = recipe.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${fmt1(macros?.calories)} kcal · ${fmt1(macros?.proteinGrams)}g protein",
                style = MaterialTheme.typography.bodySmall,
                color = extraColors.neutral700,
            )
        }
    }
}

/**
 * Item picker + quantity input for "Quick add" mode. No separate unit picker: unlike a
 * [com.healthypantry.feature.recipes.domain.model.RecipeIngredient] (which carries its own
 * [com.healthypantry.core.unit.MeasurementUnit] converted via a registered `ConversionFactor`),
 * [com.healthypantry.feature.planning.domain.model.PlanEntry]'s quick-add `quantity` has no unit
 * field at all — [PlanViewModel.quickAddItem][com.healthypantry.feature.planning.ui.vm.PlanViewModel.quickAddItem]
 * takes it as already expressed in the picked [FoodItem]'s own [FoodItem.canonicalUnit] (same
 * contract [ComputeMacroTotalsUseCase.computeForQuickAdd][com.healthypantry.feature.planning.domain.usecase.ComputeMacroTotalsUseCase.computeForQuickAdd]
 * relies on) — so once an item is picked, its canonical unit is shown as a label instead of a
 * second, meaningless picker.
 */
@Composable
private fun QuickAddPicker(
    foodItems: List<FoodItem>,
    selectedFoodItemId: Long?,
    onSelectFoodItem: (Long) -> Unit,
    quantityText: String,
    onQuantityChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var foodItemPickerExpanded by remember { mutableStateOf(false) }
    val selectedFoodItem = foodItems.find { it.id == selectedFoodItemId }
    val extraColors = LocalHealthyPantryExtraColors.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = { foodItemPickerExpanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Pantry item picker" },
            ) { Text(text = selectedFoodItem?.name ?: "Choose pantry item", modifier = Modifier.weight(1f)) }
            DropdownMenu(expanded = foodItemPickerExpanded, onDismissRequest = { foodItemPickerExpanded = false }) {
                foodItems.forEach { foodItem ->
                    DropdownMenuItem(
                        text = { Text(foodItem.name) },
                        onClick = {
                            onSelectFoodItem(foodItem.id)
                            foodItemPickerExpanded = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = quantityText,
            onValueChange = onQuantityChanged,
            label = { Text("Quantity") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        if (selectedFoodItem != null) {
            Text(
                text = "in ${selectedFoodItem.canonicalUnit.name.lowercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = extraColors.neutral600,
            )
        }
    }
}

/** One-decimal formatting, e.g. `500.0`; `—` for an unknown (`null`) macro value — same convention
 * as `PantryListScreen`'s `fmt1`. */
private fun fmt1(value: Double?): String = if (value == null) "—" else "%.1f".format(value)
