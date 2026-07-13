package com.healthypantry.feature.recipes.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.ui.vm.RecipeFormState
import com.healthypantry.feature.recipes.ui.vm.RecipeIngredientFormRow
import com.healthypantry.feature.recipes.ui.vm.RecipeUiState
import com.healthypantry.feature.recipes.ui.vm.RecipeViewModel

private enum class RecipeScreenMode { LIST, FORM }

/**
 * Stateful container: hosts [RecipeViewModel], toggles between the list and form
 * (create/edit) modes, and surfaces [RecipeViewModel.errorEvent] as a Snackbar (same convention
 * as `PantryListScreen`/`ItemFormScreen`). No nav graph wiring yet (Phase 11) — same precedent as
 * `ItemFormScreen` currently having no nav entry point.
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
            onDeleteClick = viewModel::deleteRecipe,
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
            modifier = modifier,
            snackbarHostState = snackbarHostState,
        )
    }
}

/** Stateless/presentational list half (container-presentational split, see `PantryListContent`). */
@Composable
fun RecipeListContent(
    uiState: RecipeUiState,
    onAddClick: () -> Unit,
    onEditClick: (Recipe) -> Unit,
    onDeleteClick: (Recipe) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) { Text("+") }
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

            else -> LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                items(items = uiState.recipes, key = { it.id }) { recipe ->
                    RecipeRow(recipe = recipe, onEdit = { onEditClick(recipe) }, onDelete = { onDeleteClick(recipe) })
                }
            }
        }
    }
}

@Composable
private fun RecipeRow(recipe: Recipe, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = recipe.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "${recipe.servings} servings", style = MaterialTheme.typography.bodyMedium)
        }
        Row {
            TextButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = "Edit ${recipe.name}" }) {
                Text("Edit")
            }
            TextButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = "Delete ${recipe.name}" }) {
                Text("Delete")
            }
        }
    }
}

/** Stateless/presentational create/edit-form half. */
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
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(
                text = if (formState.id == 0L) "Add recipe" else "Edit recipe",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = formState.name,
                onValueChange = onNameChanged,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = formState.servings,
                onValueChange = onServingsChanged,
                label = { Text("Servings") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = formState.notes,
                onValueChange = onNotesChanged,
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )

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

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onCancelClick) { Text("Cancel") }
                Button(onClick = onSaveClick) { Text("Save") }
            }
        }
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

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = { foodItemPickerExpanded = true },
            modifier = Modifier.semantics { contentDescription = "Ingredient picker" },
        ) {
            Text(selectedFoodItem?.name ?: "Choose ingredient")
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

        OutlinedTextField(
            value = row.quantity,
            onValueChange = { onChanged(row.copy(quantity = it)) },
            label = { Text("Qty") },
            modifier = Modifier.padding(start = 8.dp),
        )

        TextButton(
            onClick = { unitPickerExpanded = true },
            modifier = Modifier.semantics { contentDescription = "Ingredient unit picker" },
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

        TextButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = "Remove ingredient" }) {
            Text("Remove")
        }
    }
}
