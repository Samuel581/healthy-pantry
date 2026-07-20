package com.healthypantry.feature.pantry.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthypantry.app.theme.LocalHealthyPantryExtraColors
import com.healthypantry.core.unit.ConversionFactor
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.StockBatch
import com.healthypantry.feature.pantry.ui.vm.ItemDetailUiState
import com.healthypantry.feature.pantry.ui.vm.ItemDetailViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Spec: Item and Stock Batch CRUD, Unit Conversion Correctness, Projected vs Actual Stock
 * (openspec/changes/pantry-tracker/specs/pantry-stock/spec.md,
 * openspec/changes/pantry-tracker/specs/unit-conversion/spec.md).
 *
 * Stateful container mirroring `PantryListScreen`/`ItemFormScreen`'s container/presentational
 * split: obtains [ItemDetailViewModel] via [hiltViewModel] (its `itemId` nav arg is resolved
 * internally via `SavedStateHandle`, see the ViewModel's own KDoc), collects [ItemDetailUiState]
 * lifecycle-aware, surfaces [ItemDetailViewModel.errorEvent] as a Snackbar, and delegates
 * rendering to the stateless [ItemDetailContent]. [itemId] is only needed here to hand back to
 * [onEditItem]'s caller-owned navigation - the screen itself never re-derives it from the
 * ViewModel.
 *
 * [onItemDeleted] fires immediately after [ItemDetailViewModel.deleteItem] is invoked rather than
 * waiting for persistence to confirm, matching `ItemFormScreen.onSaveClick`'s fire-and-forget +
 * navigate-immediately convention; a later failure still surfaces via `errorEvent` even though
 * navigation has already moved on (a real risk here since the Snackbar host itself unmounts once
 * the back stack pops, same tradeoff `ItemFormScreen` already accepts).
 */
@Composable
fun ItemDetailScreen(
    itemId: Long,
    onBack: () -> Unit,
    onEditItem: () -> Unit,
    onItemDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.errorEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    ItemDetailContent(
        uiState = uiState,
        onBack = onBack,
        onEditItem = onEditItem,
        onAddBatch = viewModel::addBatch,
        onDeleteBatch = viewModel::deleteBatch,
        onDeleteItem = {
            viewModel.deleteItem()
            onItemDeleted()
        },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Stateless/presentational half of [ItemDetailScreen] (container-presentational split, see
 * `PantryListContent`), driven directly by a future `ItemDetailScreenTest` with hand-built
 * [ItemDetailUiState] fixtures - no Hilt/ViewModel dependency needed.
 *
 * [today] defaults to [LocalDate.now] but is an explicit param so batch-expiry urgency coloring
 * ([expiryUrgencyColor]) is deterministic in tests instead of depending on the real clock.
 */
@Composable
fun ItemDetailContent(
    uiState: ItemDetailUiState,
    onBack: () -> Unit,
    onEditItem: () -> Unit,
    onAddBatch: (quantity: Double, expiryDate: LocalDate?) -> Unit,
    onDeleteBatch: (StockBatch) -> Unit,
    onDeleteItem: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    today: LocalDate = LocalDate.now(),
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val foodItem = uiState.foodItem
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .semantics { contentDescription = "Loading item detail" },
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            foodItem == null -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("Item not found", style = MaterialTheme.typography.bodyLarge) }

            else -> Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                ItemDetailHeader(name = foodItem.name, onBack = onBack, onEditItem = onEditItem)
                Spacer(modifier = Modifier.height(16.dp))
                StatCardRow(
                    actualStock = uiState.actualStock,
                    projectedStock = uiState.projectedStock,
                    unit = foodItem.canonicalUnit,
                )
                Spacer(modifier = Modifier.height(16.dp))
                MacroTagsRow(foodItem = foodItem)
                if (uiState.conversions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    UnitConversionsSection(conversions = uiState.conversions)
                }
                Spacer(modifier = Modifier.height(20.dp))
                BatchesSection(
                    batches = uiState.batches,
                    unit = foodItem.canonicalUnit,
                    today = today,
                    onDeleteBatch = onDeleteBatch,
                    onAddBatch = onAddBatch,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onDeleteItem,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Delete item" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text("Delete item")
                }
            }
        }
    }
}

@Composable
private fun ItemDetailHeader(
    name: String,
    onBack: () -> Unit,
    onEditItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = null)
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        IconButton(onClick = onEditItem, modifier = Modifier.semantics { contentDescription = "Edit item" }) {
            Icon(Icons.Rounded.Edit, contentDescription = null)
        }
    }
}

@Composable
private fun StatCardRow(
    actualStock: Double,
    projectedStock: Double,
    unit: MeasurementUnit,
    modifier: Modifier = Modifier,
) {
    val extraColors = LocalHealthyPantryExtraColors.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            label = "Actual",
            value = "${fmt1(actualStock)} ${unitLabel(unit)}",
            containerColor = extraColors.accent100,
            contentColor = extraColors.accent800,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "Projected",
            value = "${fmt1(projectedStock)} ${unitLabel(unit)}",
            containerColor = extraColors.accent2_100,
            contentColor = extraColors.accent2_800,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = contentColor)
            Text(text = value, style = MaterialTheme.typography.headlineSmall, color = contentColor)
        }
    }
}

@Composable
private fun MacroTagsRow(foodItem: FoodItem, modifier: Modifier = Modifier) {
    val unit = unitLabel(foodItem.canonicalUnit)
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tag(text = "${fmt1(foodItem.caloriesPerUnit)} kcal/$unit")
        Tag(text = "${fmt1(foodItem.proteinGramsPerUnit)}g P")
        Tag(text = "${fmt1(foodItem.carbsGramsPerUnit)}g C")
        Tag(text = "${fmt1(foodItem.fatGramsPerUnit)}g F")
        OutlineTag(text = "per $unit")
    }
}

@Composable
private fun UnitConversionsSection(conversions: List<ConversionFactor>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "Unit conversions", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            conversions.forEach { conversion ->
                OutlineTag(
                    text = "1 ${unitLabel(conversion.fromUnit)} = ${fmt1(conversion.factor)} " +
                        unitLabel(conversion.toUnit),
                )
            }
        }
    }
}

@Composable
private fun BatchesSection(
    batches: List<StockBatch>,
    unit: MeasurementUnit,
    today: LocalDate,
    onDeleteBatch: (StockBatch) -> Unit,
    onAddBatch: (quantity: Double, expiryDate: LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "Batches", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        if (batches.isEmpty()) {
            Text(text = "No batches yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            batches.forEach { batch ->
                BatchRow(batch = batch, unit = unit, today = today, onDelete = { onDeleteBatch(batch) })
                HorizontalDivider(color = LocalHealthyPantryExtraColors.current.divider)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        AddBatchForm(onAddBatch = onAddBatch)
    }
}

@Composable
private fun BatchRow(
    batch: StockBatch,
    unit: MeasurementUnit,
    today: LocalDate,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = "${fmt1(batch.quantity)} ${unitLabel(unit)}", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = expiryLabel(batch.expiryDate, today),
                style = MaterialTheme.typography.bodyMedium,
                color = expiryUrgencyColor(batch.expiryDate, today),
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.semantics { contentDescription = "Delete batch" },
        ) {
            Icon(Icons.Rounded.Delete, contentDescription = null)
        }
    }
}

/**
 * Inline "Add a new batch" mini-form: a quantity field, a plain text expiry-date field
 * (`YYYY-MM-DD`, optional), and an "Add batch" button. No date-picker dependency exists in this
 * codebase yet (`ItemFormScreen`'s own fields are all plain `OutlinedTextField`s), so this mirrors
 * that same plain-text-field convention rather than introducing a new library for a single field.
 */
@Composable
private fun AddBatchForm(onAddBatch: (quantity: Double, expiryDate: LocalDate?) -> Unit, modifier: Modifier = Modifier) {
    var quantityText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var dateError by remember { mutableStateOf(false) }

    val quantity = quantityText.toDoubleOrNull()
    val parsedDate = if (dateText.isBlank()) {
        null
    } else {
        runCatching { LocalDate.parse(dateText) }.getOrNull()
    }
    val canAdd = quantity != null && quantity > 0.0 && (dateText.isBlank() || parsedDate != null)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "Add a new batch", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it },
                label = { Text("Quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = dateText,
                onValueChange = {
                    dateText = it
                    dateError = false
                },
                label = { Text("Expiry (YYYY-MM-DD)") },
                isError = dateError,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (dateText.isNotBlank() && parsedDate == null) {
                    dateError = true
                    return@Button
                }
                val safeQuantity = quantity
                if (safeQuantity != null && safeQuantity > 0.0) {
                    onAddBatch(safeQuantity, parsedDate)
                    quantityText = ""
                    dateText = ""
                }
            },
            enabled = canAdd,
            modifier = Modifier.semantics { contentDescription = "Add batch" },
        ) {
            Text("Add batch")
        }
    }
}

@Composable
private fun Tag(text: String, modifier: Modifier = Modifier) {
    val extraColors = LocalHealthyPantryExtraColors.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = extraColors.neutral200,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = extraColors.neutral800,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun OutlineTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Lowercase display form of a [MeasurementUnit], e.g. `GRAM` -> `gram`. */
private fun unitLabel(unit: MeasurementUnit): String = unit.name.lowercase()

/** One-decimal formatting, e.g. `500.0`; `—` for an unknown (`null`) macro value. */
private fun fmt1(value: Double?): String = if (value == null) "—" else "%.1f".format(value)

/**
 * Days-left display label for a batch's [expiryDate] relative to [today]: "No expiry date" when
 * absent, "Expired N days ago" once past due, "Expires today" on the exact day, otherwise
 * "Expires in N days".
 */
private fun expiryLabel(expiryDate: LocalDate?, today: LocalDate): String {
    if (expiryDate == null) return "No expiry date"
    val daysLeft = ChronoUnit.DAYS.between(today, expiryDate)
    return when {
        daysLeft < 0 -> "Expired ${-daysLeft} day${if (-daysLeft == 1L) "" else "s"} ago"
        daysLeft == 0L -> "Expires today"
        else -> "Expires in $daysLeft day${if (daysLeft == 1L) "" else "s"}"
    }
}

/**
 * Expiry-urgency color: no [expiryDate] stays neutral; already expired is [MaterialTheme]'s error
 * color; within the 5-day urgency window is the Organic accent ramp's warm "soon" tone
 * ([com.healthypantry.app.theme.HealthyPantryExtraColors.accent700]); anything further out is the
 * accent-2 ramp's calmer "ok" tone
 * ([com.healthypantry.app.theme.HealthyPantryExtraColors.accent2_700]).
 */
@Composable
private fun expiryUrgencyColor(expiryDate: LocalDate?, today: LocalDate): Color {
    val extraColors = LocalHealthyPantryExtraColors.current
    if (expiryDate == null) return MaterialTheme.colorScheme.onSurfaceVariant
    val daysLeft = ChronoUnit.DAYS.between(today, expiryDate)
    return when {
        daysLeft < 0 -> MaterialTheme.colorScheme.error
        daysLeft <= EXPIRY_URGENCY_THRESHOLD_DAYS -> extraColors.accent700
        else -> extraColors.accent2_700
    }
}

/** Mirrors the design mockup's expiry-urgency threshold: batches expiring within 5 days are
 * flagged as urgent. */
private const val EXPIRY_URGENCY_THRESHOLD_DAYS = 5L
