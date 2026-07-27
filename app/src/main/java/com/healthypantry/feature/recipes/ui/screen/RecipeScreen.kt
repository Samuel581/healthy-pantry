package com.healthypantry.feature.recipes.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthypantry.app.theme.HealthyPantryExtraColors
import com.healthypantry.app.theme.LocalHealthyPantryExtraColors
import com.healthypantry.core.common.Result
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.core.unit.UnitConverter
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.planning.domain.model.MacroTotals
import com.healthypantry.feature.planning.domain.usecase.ComputeMacroTotalsUseCase
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.domain.model.RecipeIngredient
import com.healthypantry.feature.recipes.domain.model.RecipeIngredientDetail
import com.healthypantry.feature.recipes.domain.model.RecipeWithIngredients
import com.healthypantry.feature.recipes.ui.vm.RecipeFormState
import com.healthypantry.feature.recipes.ui.vm.RecipeIngredientFormRow
import com.healthypantry.feature.recipes.ui.vm.RecipeMacroSummary
import com.healthypantry.feature.recipes.ui.vm.RecipeUiState
import com.healthypantry.feature.recipes.ui.vm.RecipeViewModel

private enum class RecipeScreenMode { LIST, FORM }

/**
 * Stateful container: hosts [RecipeViewModel], toggles between the list and form
 * (create/edit) modes, and surfaces [RecipeViewModel.errorEvent] as a Snackbar (same convention
 * as `PantryListScreen`/`ItemFormScreen`). No nav graph wiring yet (Phase 11) — same precedent as
 * `ItemFormScreen` currently having no nav entry point.
 *
 * Delete moved from the list (per-row) to the form (visible only when editing) in the Organic
 * redesign — same "delete lives where you already are, not on every row" precedent
 * `PantryListScreen` set when its own per-row delete moved to `ItemDetailScreen`. [formState]
 * already carries every field [RecipeViewModel.deleteRecipe] needs to identify a
 * persisted [Recipe] (it was loaded from one via [RecipeViewModel.loadRecipeForEdit]), so no
 * extra state is threaded through just for the delete button.
 */
@Composable
fun RecipeScreen(
    modifier: Modifier = Modifier,
    viewModel: RecipeViewModel = hiltViewModel(),
) {
    var mode by remember { mutableStateOf(RecipeScreenMode.LIST) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.errorEvent.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    when (mode) {
        RecipeScreenMode.LIST -> RecipeListContent(
            uiState = uiState,
            onAddClick = {
                viewModel.startNewRecipe()
                mode = RecipeScreenMode.FORM
            },
            onEditClick = { recipe ->
                viewModel.loadRecipeForEdit(recipe.id)
                mode = RecipeScreenMode.FORM
            },
            modifier = modifier,
            snackbarHostState = snackbarHostState,
        )

        RecipeScreenMode.FORM -> RecipeFormContent(
            formState = formState,
            availableFoodItems = uiState.foodItems,
            onNameChanged = viewModel::onNameChanged,
            onServingsChanged = viewModel::onServingsChanged,
            onNotesChanged = viewModel::onNotesChanged,
            onAddIngredientRow = viewModel::addIngredientRow,
            onIngredientRowChanged = viewModel::updateIngredientRow,
            onRemoveIngredientRow = viewModel::removeIngredientRow,
            onSaveClick = {
                viewModel.saveRecipe()
                mode = RecipeScreenMode.LIST
            },
            onCancelClick = { mode = RecipeScreenMode.LIST },
            onDeleteClick = {
                viewModel.deleteRecipe(formState.toRecipeForDelete())
                mode = RecipeScreenMode.LIST
            },
            modifier = modifier,
            snackbarHostState = snackbarHostState,
        )
    }
}

/** Reconstructs the persisted [Recipe] a [RecipeFormState] was loaded from, so
 * [RecipeViewModel.deleteRecipe] has something to delete — mirrors
 * [RecipeViewModel.saveRecipe]'s own `formState -> Recipe` field mapping. */
private fun RecipeFormState.toRecipeForDelete(): Recipe = Recipe(
    id = id,
    name = name,
    servings = servings.toIntOrNull() ?: 1,
    notes = notes.ifBlank { null },
    createdAt = createdAt,
)

/**
 * Stateless/presentational list half (container-presentational split, see `PantryListContent`).
 *
 * Design mockup ("Recipes tab"): one card per recipe — name, an ingredient-count label, and a
 * three-tone macro tag row ([AccentMacroTag] kcal / [Accent2MacroTag] protein / two
 * [NeutralMacroTag] for carbs+fat). The whole card is tappable to edit; there is no per-row
 * delete affordance anymore (see [RecipeScreen] KDoc).
 */
@Composable
fun RecipeListContent(
    uiState: RecipeUiState,
    onAddClick: () -> Unit,
    onEditClick: (Recipe) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                modifier = Modifier.semantics { contentDescription = "Add recipe" },
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize()
                    .semantics { contentDescription = "Loading recipes" },
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.recipes.isEmpty() -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("No recipes yet. Tap + to add one.", style = MaterialTheme.typography.bodyLarge) }

            else -> LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(items = uiState.recipes, key = { it.id }) { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        macroSummary = uiState.macroSummariesByRecipeId[recipe.id],
                        onClick = { onEditClick(recipe) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: Recipe,
    macroSummary: RecipeMacroSummary?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extraColors = LocalHealthyPantryExtraColors.current
    val ingredientCount = macroSummary?.ingredientCount ?: 0
    val macros = macroSummary?.macroTotals

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Open ${recipe.name}" },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = ingredientCountLabel(ingredientCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = extraColors.neutral600,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AccentMacroTag(text = "${fmt1(macros?.calories)} kcal", extraColors = extraColors)
                Accent2MacroTag(text = "${fmt1(macros?.proteinGrams)}g P", extraColors = extraColors)
                NeutralMacroTag(text = "${fmt1(macros?.carbsGrams)}g C", extraColors = extraColors)
                NeutralMacroTag(text = "${fmt1(macros?.fatGrams)}g F", extraColors = extraColors)
            }
        }
    }
}

/** "4 ingredients" / "1 ingredient" — same singular/plural convention as `PantryItemCard`'s batch count. */
private fun ingredientCountLabel(count: Int): String = "$count ingredient${if (count == 1) "" else "s"}"

@Composable
private fun AccentMacroTag(text: String, extraColors: HealthyPantryExtraColors, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = extraColors.accent100) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = extraColors.accent800,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Accent2MacroTag(text: String, extraColors: HealthyPantryExtraColors, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = extraColors.accent2_100) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = extraColors.accent2_800,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun NeutralMacroTag(text: String, extraColors: HealthyPantryExtraColors, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = extraColors.neutral200) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = extraColors.neutral800,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** One-decimal formatting, e.g. `500.0`; `—` for an unknown (`null`) macro value — same convention
 * as `PantryListScreen`'s `fmt1`. */
private fun fmt1(value: Double?): String = if (value == null) "—" else "%.1f".format(value)

/**
 * Stateless/presentational create/edit-form half.
 *
 * Design mockup ("Recipe form"): a back-arrow header, labeled fields, one [IngredientFormRow]
 * card per ingredient (item picker + remove on top, quantity + unit below), a "Computed total"
 * banner reflecting the in-progress ingredient list, a full-width primary "Save recipe" button,
 * and — only when editing an existing recipe ([formState].id != 0) — an accent-outlined
 * "Delete recipe" button.
 */
@Composable
fun RecipeFormContent(
    formState: RecipeFormState,
    availableFoodItems: List<FoodItem>,
    onNameChanged: (String) -> Unit,
    onServingsChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onAddIngredientRow: () -> Unit,
    onIngredientRowChanged: (Int, RecipeIngredientFormRow) -> Unit,
    onRemoveIngredientRow: (Int) -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val extraColors = LocalHealthyPantryExtraColors.current
    val computeMacroTotalsUseCase = remember { ComputeMacroTotalsUseCase(UnitConverter()) }
    val isEditing = formState.id != 0L
    val macroTotals = computeFormMacroTotals(formState, availableFoodItems, computeMacroTotalsUseCase)

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RecipeFormHeader(isEditing = isEditing, onBack = onCancelClick)

            OutlinedTextField(
                value = formState.name,
                onValueChange = onNameChanged,
                label = { Text("Recipe name") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = formState.servings,
                onValueChange = onServingsChanged,
                label = { Text("Servings") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = formState.notes,
                onValueChange = onNotesChanged,
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Ingredients", style = MaterialTheme.typography.titleMedium)
                formState.ingredients.forEachIndexed { index, row ->
                    IngredientFormRow(
                        row = row,
                        availableFoodItems = availableFoodItems,
                        onChanged = { onIngredientRowChanged(index, it) },
                        onRemove = { onRemoveIngredientRow(index) },
                    )
                }
                TextButton(onClick = onAddIngredientRow) { Text("+ Add ingredient") }
            }

            ComputedTotalBanner(macroTotals = macroTotals, extraColors = extraColors)

            Button(onClick = onSaveClick, modifier = Modifier.fillMaxWidth()) {
                Text("Save recipe")
            }

            if (isEditing) {
                OutlinedButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Delete recipe" },
                    border = BorderStroke(1.dp, extraColors.accent700),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = extraColors.accent700),
                ) {
                    Text("Delete recipe")
                }
            }
        }
    }
}

@Composable
private fun RecipeFormHeader(isEditing: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = null)
        }
        Text(
            text = if (isEditing) "Edit recipe" else "New recipe",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun IngredientFormRow(
    row: RecipeIngredientFormRow,
    availableFoodItems: List<FoodItem>,
    onChanged: (RecipeIngredientFormRow) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var foodItemPickerExpanded by remember { mutableStateOf(false) }
    var unitPickerExpanded by remember { mutableStateOf(false) }
    val selectedFoodItem = availableFoodItems.find { it.id == row.foodItemId }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { foodItemPickerExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Ingredient picker" },
                    ) {
                        Text(
                            text = selectedFoodItem?.name ?: "Choose ingredient",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    DropdownMenu(expanded = foodItemPickerExpanded, onDismissRequest = { foodItemPickerExpanded = false }) {
                        availableFoodItems.forEach { foodItem ->
                            DropdownMenuItem(
                                text = { Text(foodItem.name) },
                                onClick = {
                                    onChanged(row.copy(foodItemId = foodItem.id))
                                    foodItemPickerExpanded = false
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = "Remove ingredient" }) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = row.quantity,
                    onValueChange = { onChanged(row.copy(quantity = it)) },
                    label = { Text("Qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { unitPickerExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Ingredient unit picker" },
                    ) {
                        Text(row.unit.name)
                    }
                    DropdownMenu(expanded = unitPickerExpanded, onDismissRequest = { unitPickerExpanded = false }) {
                        MeasurementUnit.entries.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit.name) },
                                onClick = {
                                    onChanged(row.copy(unit = unit))
                                    unitPickerExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComputedTotalBanner(macroTotals: MacroTotals, extraColors: HealthyPantryExtraColors, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = extraColors.accent100) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "COMPUTED TOTAL",
                style = MaterialTheme.typography.labelSmall,
                color = extraColors.accent800,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ComputedTotalTag(text = "${fmt1(macroTotals.calories)} kcal", extraColors = extraColors)
                ComputedTotalTag(text = "${fmt1(macroTotals.proteinGrams)}g P", extraColors = extraColors)
                ComputedTotalTag(text = "${fmt1(macroTotals.carbsGrams)}g C", extraColors = extraColors)
                ComputedTotalTag(text = "${fmt1(macroTotals.fatGrams)}g F", extraColors = extraColors)
            }
        }
    }
}

@Composable
private fun ComputedTotalTag(text: String, extraColors: HealthyPantryExtraColors, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.background) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = extraColors.accent800,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * Live "Computed total" for the in-progress form — mirrors [RecipeViewModel]'s
 * [com.healthypantry.feature.recipes.ui.vm.RecipeMacroSummary] rollup (same
 * [ComputeMacroTotalsUseCase.computeForRecipe] call, no registered conversion factors, see that
 * ViewModel's KDoc for the tradeoff), but runs directly in this screen layer against
 * [formState]/[availableFoodItems] since the form's ingredient rows are still raw, unparsed,
 * unsaved [RecipeIngredientFormRow]s that never touch [RecipeViewModel.uiState]. Rows missing a
 * picked ingredient or an unparseable quantity are silently excluded from the total — same
 * "best-effort, don't block on invalid rows" convention [RecipeViewModel.saveRecipe] itself uses.
 */
private fun computeFormMacroTotals(
    formState: RecipeFormState,
    availableFoodItems: List<FoodItem>,
    computeMacroTotalsUseCase: ComputeMacroTotalsUseCase,
): MacroTotals {
    val servings = formState.servings.toIntOrNull()?.takeIf { it > 0 } ?: 1
    val ingredientDetails = formState.ingredients.mapIndexedNotNull { index, row ->
        val foodItemId = row.foodItemId ?: return@mapIndexedNotNull null
        val foodItem = availableFoodItems.find { it.id == foodItemId } ?: return@mapIndexedNotNull null
        val quantity = row.quantity.toDoubleOrNull() ?: return@mapIndexedNotNull null
        RecipeIngredientDetail(
            ingredient = RecipeIngredient(
                recipeId = formState.id,
                foodItemId = foodItemId,
                quantity = quantity,
                unit = row.unit,
                sortOrder = index,
            ),
            foodItem = foodItem,
        )
    }
    val recipeWithIngredients = RecipeWithIngredients(
        recipe = Recipe(
            id = formState.id,
            name = formState.name,
            servings = servings,
            notes = formState.notes.ifBlank { null },
            createdAt = formState.createdAt,
        ),
        ingredients = ingredientDetails,
    )
    val result = computeMacroTotalsUseCase.computeForRecipe(
        recipeWithIngredients = recipeWithIngredients,
        requestedServings = servings.toDouble(),
        conversionFactorsByFoodItemId = emptyMap(),
    )
    return when (result) {
        is Result.Success -> result.value
        is Result.Failure -> MacroTotals.ZERO.copy(isComplete = false)
    }
}
