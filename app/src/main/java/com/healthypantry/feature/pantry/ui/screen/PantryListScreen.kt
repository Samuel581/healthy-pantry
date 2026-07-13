package com.healthypantry.feature.pantry.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthypantry.feature.expiry.domain.model.ExpiringBatch
import com.healthypantry.feature.expiry.ui.ExpiryBanner
import com.healthypantry.feature.expiry.ui.vm.ExpiryAlertViewModel
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.ui.vm.PantryItemUi
import com.healthypantry.feature.pantry.ui.vm.PantryUiState
import com.healthypantry.feature.pantry.ui.vm.PantryViewModel

/**
 * Spec: Item and Stock Batch CRUD, Projected vs Actual Stock
 * (openspec/changes/pantry-tracker/specs/pantry-stock/spec.md).
 *
 * Stateful container: obtains [PantryViewModel] via [hiltViewModel], collects [uiState] lifecycle
 * -aware (design.md "UI layer ... via `collectAsStateWithLifecycle`"), surfaces
 * [PantryViewModel.errorEvent] as a Snackbar, and delegates rendering to the stateless
 * [PantryListContent]. Navigating to the add/edit form (`ItemFormScreen`, PR6.3) is the caller's
 * responsibility — [onAddItem] is only the trigger point.
 */
@Composable
fun PantryListScreen(
    onAddItem: () -> Unit,
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
        onDeleteItem = viewModel::deleteItem,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Stateless/presentational half of [PantryListScreen] (container-presentational split), so it can
 * be driven directly in Compose UI tests without a Hilt/ViewModel dependency (see
 * `PantryListScreenTest`). [expiringItems] renders [ExpiryBanner] above the list content (spec
 * "Notification-Denied Fallback") — empty by default so existing callers/tests are unaffected.
 */
@Composable
fun PantryListContent(
    uiState: PantryUiState,
    onAddItem: () -> Unit,
    onDeleteItem: (FoodItem) -> Unit,
    modifier: Modifier = Modifier,
    expiringItems: List<ExpiringBatch> = emptyList(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Text("+")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            ExpiryBanner(expiringItems = expiringItems)
            when {
                uiState.isLoading -> LoadingState(modifier = Modifier.weight(1f))
                uiState.items.isEmpty() -> EmptyState(modifier = Modifier.weight(1f))
                else -> PantryItemList(
                    items = uiState.items,
                    onDeleteItem = onDeleteItem,
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
    onDeleteItem: (FoodItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(items = items, key = { it.foodItem.id }) { item ->
            PantryItemRow(item = item, onDelete = { onDeleteItem(item.foodItem) })
        }
    }
}

@Composable
private fun PantryItemRow(
    item: PantryItemUi,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = item.foodItem.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Actual: ${formatStock(item.actualStock)} · " +
                    "Projected: ${formatStock(item.projectedStock)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TextButton(
            onClick = onDelete,
            modifier = Modifier.semantics {
                contentDescription = "Delete ${item.foodItem.name}"
            },
        ) {
            Text("Delete")
        }
    }
}

/** Pure formatting helper: one decimal place, e.g. `500.0`. */
private fun formatStock(value: Double): String = "%.1f".format(value)
