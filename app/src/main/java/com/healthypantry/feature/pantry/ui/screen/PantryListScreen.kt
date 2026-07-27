package com.healthypantry.feature.pantry.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthypantry.app.theme.HealthyPantryExtraColors
import com.healthypantry.app.theme.LocalHealthyPantryExtraColors
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.expiry.domain.model.ExpiringBatch
import com.healthypantry.feature.expiry.domain.model.ExpiryStatus
import com.healthypantry.feature.expiry.ui.ExpiryBanner
import com.healthypantry.feature.expiry.ui.vm.ExpiryAlertViewModel
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.ui.vm.PantryItemUi
import com.healthypantry.feature.pantry.ui.vm.PantryUiState
import com.healthypantry.feature.pantry.ui.vm.PantryViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Spec: Item and Stock Batch CRUD, Projected vs Actual Stock
 * (openspec/changes/pantry-tracker/specs/pantry-stock/spec.md).
 *
 * Stateful container: obtains [PantryViewModel] via [hiltViewModel], collects [uiState] lifecycle
 * -aware (design.md "UI layer ... via `collectAsStateWithLifecycle`"), surfaces
 * [PantryViewModel.errorEvent] as a Snackbar, and delegates rendering to the stateless
 * [PantryListContent]. Tapping a row opens `ItemDetailScreen` (see `HealthyPantryNavHost`) —
 * [onEditItem] is named for the callback's original "go edit this item" intent, not for what
 * screen it now opens.
 */
@Composable
fun PantryListScreen(
    onAddItem: () -> Unit,
    onEditItem: (FoodItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PantryViewModel = hiltViewModel(),
    expiryAlertViewModel: ExpiryAlertViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val expiryUiState by expiryAlertViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.errorEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    PantryListContent(
        uiState = uiState,
        expiringItems = expiryUiState.expiringItems,
        onAddItem = onAddItem,
        onEditItem = onEditItem,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Stateless/presentational half of [PantryListScreen] (container-presentational split), so it can
 * be driven directly in Compose UI tests without a Hilt/ViewModel dependency (see
 * `PantryListScreenTest`).
 *
 * Design mockup ("Pantry tab"): a header row with a notification bell (badged whenever
 * [expiringItems] is non-empty — reuses [ExpiryAlertViewModel]'s already-computed list rather
 * than re-deriving its own threshold), a Projected/Actual segmented toggle
 * ([isProjectedView], purely a display choice so it's kept as local UI state rather than pushed
 * into [PantryViewModel]), [ExpiryBanner], and one card per item. Per-row delete is gone — item
 * deletion now lives on `ItemDetailScreen`, which every row already navigates to via
 * [onEditItem].
 */
@Composable
fun PantryListContent(
    uiState: PantryUiState,
    onAddItem: () -> Unit,
    onEditItem: (FoodItem) -> Unit,
    modifier: Modifier = Modifier,
    expiringItems: List<ExpiringBatch> = emptyList(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    today: LocalDate = LocalDate.now(),
) {
    var isProjectedView by rememberSaveable { mutableStateOf(true) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddItem,
                modifier = Modifier.semantics { contentDescription = "Add item" },
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            PantryHeader(hasExpiringItems = expiringItems.isNotEmpty(), modifier = Modifier.padding(vertical = 8.dp))
            StockViewToggle(
                isProjectedView = isProjectedView,
                onSelect = { isProjectedView = it },
                modifier = Modifier.padding(bottom = 14.dp),
            )
            ExpiryBanner(expiringItems = expiringItems, modifier = Modifier.padding(bottom = 12.dp))
            when {
                uiState.isLoading -> LoadingState(modifier = Modifier.weight(1f))
                uiState.items.isEmpty() -> EmptyState(modifier = Modifier.weight(1f))
                else -> PantryItemList(
                    items = uiState.items,
                    isProjectedView = isProjectedView,
                    expiringItems = expiringItems,
                    onItemClick = onEditItem,
                    today = today,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PantryHeader(hasExpiringItems: Boolean, modifier: Modifier = Modifier) {
    val extraColors = LocalHealthyPantryExtraColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = "Pantry", style = MaterialTheme.typography.headlineSmall)
        Box {
            IconButton(
                onClick = {},
                modifier = Modifier.semantics { contentDescription = "Expiring items" },
            ) {
                Icon(Icons.Rounded.Notifications, contentDescription = null)
            }
            if (hasExpiringItems) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(8.dp)
                        .background(color = extraColors.accent500, shape = CircleShape)
                        .semantics { contentDescription = "Items expiring soon" },
                )
            }
        }
    }
}

@Composable
private fun StockViewToggle(
    isProjectedView: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extraColors = LocalHealthyPantryExtraColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color = extraColors.neutral200, shape = MaterialTheme.shapes.large)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StockViewOption(
            label = "Projected",
            selected = isProjectedView,
            onClick = { onSelect(true) },
            modifier = Modifier.weight(1f),
        )
        StockViewOption(
            label = "Actual",
            selected = !isProjectedView,
            onClick = { onSelect(false) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StockViewOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extraColors = LocalHealthyPantryExtraColors.current
    Surface(
        onClick = onClick,
        modifier = modifier,
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
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Loading pantry items" },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No pantry items yet. Tap + to add one.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun PantryItemList(
    items: List<PantryItemUi>,
    isProjectedView: Boolean,
    expiringItems: List<ExpiringBatch>,
    onItemClick: (FoodItem) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = items, key = { it.foodItem.id }) { item ->
            PantryItemCard(
                item = item,
                isProjectedView = isProjectedView,
                expiringBatch = nearestExpiringBatch(item.foodItem.id, expiringItems),
                today = today,
                onClick = { onItemClick(item.foodItem) },
            )
        }
    }
}

/** The soonest-expiring [ExpiringBatch] for [foodItemId], or `null` if none is currently flagged. */
private fun nearestExpiringBatch(foodItemId: Long, expiringItems: List<ExpiringBatch>): ExpiringBatch? =
    expiringItems
        .filter { it.foodItem.id == foodItemId }
        .minByOrNull { it.stockBatch.expiryDate ?: LocalDate.MAX }

@Composable
private fun PantryItemCard(
    item: PantryItemUi,
    isProjectedView: Boolean,
    expiringBatch: ExpiringBatch?,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extraColors = LocalHealthyPantryExtraColors.current
    val foodItem = item.foodItem
    val unit = unitLabel(foodItem.canonicalUnit)
    val stockValue = if (isProjectedView) item.projectedStock else item.actualStock
    val viewLabel = if (isProjectedView) "projected" else "actual"

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Open ${foodItem.name}" },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = foodItem.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${item.batchCount} batch${if (item.batchCount == 1) "" else "es"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = extraColors.neutral700,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = fmt1(stockValue),
                        style = MaterialTheme.typography.titleLarge,
                        color = extraColors.accent800,
                    )
                    Text(
                        text = "$unit · $viewLabel".uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = extraColors.neutral600,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MacroTag(text = "${fmt1(foodItem.caloriesPerUnit)} kcal/$unit", extraColors = extraColors)
                MacroTag(text = "${fmt1(foodItem.proteinGramsPerUnit)}g P", extraColors = extraColors)
                MacroTag(text = "${fmt1(foodItem.carbsGramsPerUnit)}g C", extraColors = extraColors)
                MacroTag(text = "${fmt1(foodItem.fatGramsPerUnit)}g F", extraColors = extraColors)
                if (expiringBatch != null) {
                    ExpiryTag(expiringBatch = expiringBatch, today = today, extraColors = extraColors)
                }
            }
        }
    }
}

@Composable
private fun MacroTag(text: String, extraColors: HealthyPantryExtraColors, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = extraColors.neutral200) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = extraColors.neutral800,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ExpiryTag(
    expiringBatch: ExpiringBatch,
    today: LocalDate,
    extraColors: HealthyPantryExtraColors,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = extraColors.accent100) {
        Text(
            text = expiryTagLabel(expiringBatch, today),
            style = MaterialTheme.typography.labelMedium,
            color = extraColors.accent800,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Short "Nd left" / "Expires today" / "Expired" tag label for a card's [ExpiringBatch]. */
private fun expiryTagLabel(expiringBatch: ExpiringBatch, today: LocalDate): String {
    if (expiringBatch.status == ExpiryStatus.EXPIRED) return "Expired"
    val expiryDate = expiringBatch.stockBatch.expiryDate ?: return "Expiring soon"
    val daysLeft = ChronoUnit.DAYS.between(today, expiryDate)
    return when {
        daysLeft <= 0 -> "Expires today"
        else -> "${daysLeft}d left"
    }
}

/** Lowercase display form of a [MeasurementUnit], e.g. `GRAM` -> `gram`. */
private fun unitLabel(unit: MeasurementUnit): String = unit.name.lowercase()

/** One-decimal formatting, e.g. `500.0`; `—` for an unknown (`null`) macro value. */
private fun fmt1(value: Double?): String = if (value == null) "—" else "%.1f".format(value)
