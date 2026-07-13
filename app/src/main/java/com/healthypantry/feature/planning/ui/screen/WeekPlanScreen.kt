package com.healthypantry.feature.planning.ui.screen

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
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.planning.domain.model.MealSlot
import com.healthypantry.feature.planning.domain.model.PlanEntry
import com.healthypantry.feature.planning.ui.vm.PlanEntryUi
import com.healthypantry.feature.planning.ui.vm.PlanUiState
import com.healthypantry.feature.planning.ui.vm.PlanViewModel
import com.healthypantry.feature.recipes.domain.model.Recipe
import java.time.LocalDate

/** Which kind of entry the assign/quick-add form is currently building. */
private enum class EntryKind { RECIPE, ITEM }

/**
 * Stateful container: hosts [PlanViewModel], surfaces [PlanViewModel.errorEvent] as a Snackbar
 * (same convention as `PantryListScreen`/`RecipeScreen`). No nav graph wiring yet (Phase 11).
 */
@Composable
fun WeekPlanScreen(
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.errorEvent.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    WeekPlanContent(
        uiState = uiState,
        onAssignRecipe = viewModel::assignRecipe,
        onQuickAdd = viewModel::quickAddItem,
        onMarkEaten = viewModel::markEaten,
        onDeleteEntry = viewModel::deleteEntry,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/** Stateless/presentational half (container-presentational split, see `PantryListContent`). */
@Composable
fun WeekPlanContent(
    uiState: PlanUiState,
    onAssignRecipe: (day: Long, mealSlot: MealSlot, recipeId: Long, servings: Double) -> Unit,
    onQuickAdd: (day: Long, mealSlot: MealSlot, foodItemId: Long, quantity: Double) -> Unit,
    onMarkEaten: (PlanEntry) -> Unit,
    onDeleteEntry: (PlanEntry) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize()
                    .semantics { contentDescription = "Loading week plan" },
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            else -> Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                AssignEntryForm(
                    days = uiState.weekRange.days,
                    recipes = uiState.recipes,
                    foodItems = uiState.foodItems,
                    isSaving = uiState.isSaving,
                    onAssignRecipe = onAssignRecipe,
                    onQuickAdd = onQuickAdd,
                )
                if (uiState.entries.isEmpty()) {
                    Text(
                        text = "No plan entries yet this week.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items = uiState.entries, key = { it.entry.id }) { entryUi ->
                            PlanEntryRow(
                                entryUi = entryUi,
                                isSaving = uiState.isSaving,
                                onMarkEaten = { onMarkEaten(entryUi.entry) },
                                onDelete = { onDeleteEntry(entryUi.entry) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanEntryRow(
    entryUi: PlanEntryUi,
    isSaving: Boolean,
    onMarkEaten: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = entryUi.entry
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "${LocalDate.ofEpochDay(entry.dateEpochDay).dayOfWeek} · ${entry.mealSlot}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(text = entryUi.displayName, style = MaterialTheme.typography.titleMedium)
        }
        Row {
            if (!entry.eaten) {
                // `enabled = !isSaving` is UI-layer defense in depth against a double-tap: the
                // real guarantee against a double stock decrement is the DB-layer atomic guard in
                // `PlanEntryDao.markEaten` (see `MarkPlanEntryEatenUseCase`), not this flag alone.
                TextButton(
                    onClick = onMarkEaten,
                    enabled = !isSaving,
                    modifier = Modifier.semantics { contentDescription = "Mark ${entryUi.displayName} eaten" },
                ) { Text("Mark eaten") }
            } else {
                Text(text = "Eaten", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.semantics { contentDescription = "Delete ${entryUi.displayName} entry" },
            ) { Text("Delete") }
        }
    }
}

@Composable
private fun AssignEntryForm(
    days: List<Long>,
    recipes: List<Recipe>,
    foodItems: List<FoodItem>,
    isSaving: Boolean,
    onAssignRecipe: (day: Long, mealSlot: MealSlot, recipeId: Long, servings: Double) -> Unit,
    onQuickAdd: (day: Long, mealSlot: MealSlot, foodItemId: Long, quantity: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var kind by remember { mutableStateOf(EntryKind.RECIPE) }
    var selectedDay by remember(days) { mutableStateOf(days.firstOrNull() ?: 0L) }
    var selectedSlot by remember { mutableStateOf(MealSlot.BREAKFAST) }
    var selectedRecipeId by remember { mutableStateOf<Long?>(null) }
    var selectedFoodItemId by remember { mutableStateOf<Long?>(null) }
    var amountText by remember { mutableStateOf("1") }

    var dayMenuExpanded by remember { mutableStateOf(false) }
    var slotMenuExpanded by remember { mutableStateOf(false) }
    var pickerMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(text = "Assign to this week", style = MaterialTheme.typography.titleMedium)

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { kind = EntryKind.RECIPE }) { Text(if (kind == EntryKind.RECIPE) "[Recipe]" else "Recipe") }
            TextButton(onClick = { kind = EntryKind.ITEM }) { Text(if (kind == EntryKind.ITEM) "[Quick-add item]" else "Quick-add item") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { dayMenuExpanded = true },
                modifier = Modifier.semantics { contentDescription = "Day picker" },
            ) { Text(LocalDate.ofEpochDay(selectedDay).dayOfWeek.toString()) }
            DropdownMenu(expanded = dayMenuExpanded, onDismissRequest = { dayMenuExpanded = false }) {
                days.forEach { day ->
                    DropdownMenuItem(
                        text = { Text(LocalDate.ofEpochDay(day).dayOfWeek.toString()) },
                        onClick = { selectedDay = day; dayMenuExpanded = false },
                    )
                }
            }

            TextButton(
                onClick = { slotMenuExpanded = true },
                modifier = Modifier.semantics { contentDescription = "Meal slot picker" },
            ) { Text(selectedSlot.name) }
            DropdownMenu(expanded = slotMenuExpanded, onDismissRequest = { slotMenuExpanded = false }) {
                MealSlot.entries.forEach { slot ->
                    DropdownMenuItem(text = { Text(slot.name) }, onClick = { selectedSlot = slot; slotMenuExpanded = false })
                }
            }
        }

        val pickerLabel = when (kind) {
            EntryKind.RECIPE -> recipes.find { it.id == selectedRecipeId }?.name ?: "Choose recipe"
            EntryKind.ITEM -> foodItems.find { it.id == selectedFoodItemId }?.name ?: "Choose item"
        }
        TextButton(
            onClick = { pickerMenuExpanded = true },
            modifier = Modifier.semantics { contentDescription = "Recipe or item picker" },
        ) { Text(pickerLabel) }
        DropdownMenu(expanded = pickerMenuExpanded, onDismissRequest = { pickerMenuExpanded = false }) {
            when (kind) {
                EntryKind.RECIPE -> recipes.forEach { recipe ->
                    DropdownMenuItem(
                        text = { Text(recipe.name) },
                        onClick = { selectedRecipeId = recipe.id; pickerMenuExpanded = false },
                    )
                }
                EntryKind.ITEM -> foodItems.forEach { foodItem ->
                    DropdownMenuItem(
                        text = { Text(foodItem.name) },
                        onClick = { selectedFoodItemId = foodItem.id; pickerMenuExpanded = false },
                    )
                }
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text(if (kind == EntryKind.RECIPE) "Servings" else "Quantity") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                val amount = amountText.toDoubleOrNull() ?: return@Button
                when (kind) {
                    EntryKind.RECIPE -> selectedRecipeId?.let { onAssignRecipe(selectedDay, selectedSlot, it, amount) }
                    EntryKind.ITEM -> selectedFoodItemId?.let { onQuickAdd(selectedDay, selectedSlot, it, amount) }
                }
            },
            // Defense in depth against a double-tap creating a duplicate PlanEntry row: see
            // `PlanEntryRow`'s "Mark eaten" button for the same `isSaving` convention.
            enabled = !isSaving,
        ) { Text("Add") }
    }
}
