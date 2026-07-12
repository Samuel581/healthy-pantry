package com.healthypantry.feature.pantry.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.ui.vm.ItemFormUiState
import com.healthypantry.feature.pantry.ui.vm.ItemFormViewModel
import com.healthypantry.feature.pantry.ui.vm.PantryViewModel

/**
 * Spec: "Barcode Scan via Open Food Facts", "Manual Entry with USDA FDC Fallback" (nutrition-
 * lookup domain, openspec/changes/pantry-tracker/specs/nutrition-lookup/spec.md).
 *
 * Stateful container mirroring `PantryListScreen`'s container/presentational split: obtains
 * [ItemFormViewModel] (owns lookup-in-progress state) and [PantryViewModel] (persistence -
 * reused rather than duplicating `upsert` logic) via [hiltViewModel], and delegates rendering to
 * the stateless [ItemFormContent]. [existingItem] puts the form in edit mode; there is no
 * navigation entry point wired to it yet (the nav graph is a later phase), but the ViewModel and
 * Save-button branching already support both add and edit for when one is added.
 *
 * [PantryViewModel.addItem]/[PantryViewModel.updateItem] are fire-and-forget, so [onSaved] fires
 * immediately on tap rather than waiting for persistence to confirm; a later failure still
 * surfaces via [PantryViewModel.errorEvent] as a Snackbar, same as [PantryListScreen].
 */
@Composable
fun ItemFormScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    existingItem: FoodItem? = null,
    itemFormViewModel: ItemFormViewModel = hiltViewModel(),
    pantryViewModel: PantryViewModel = hiltViewModel(),
    barcodeScannerLauncher: BarcodeScannerLauncher = rememberBarcodeScannerLauncher(),
) {
    LaunchedEffect(existingItem) {
        existingItem?.let(itemFormViewModel::loadExisting)
    }
    val uiState by itemFormViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // PantryViewModel.addItem/updateItem are fire-and-forget (see PantryViewModel.launchOnIo) - a
    // save failure (e.g. a Room constraint violation) can only surface after onSaved() has already
    // navigated away, so it's collected here rather than awaited, matching PantryListScreen's own
    // errorEvent-as-Snackbar convention.
    LaunchedEffect(pantryViewModel) {
        pantryViewModel.errorEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    ItemFormContent(
        uiState = uiState,
        isEditing = existingItem != null,
        onNameChanged = itemFormViewModel::onNameChanged,
        onCanonicalUnitChanged = itemFormViewModel::onCanonicalUnitChanged,
        onCaloriesChanged = itemFormViewModel::onCaloriesChanged,
        onProteinChanged = itemFormViewModel::onProteinChanged,
        onCarbsChanged = itemFormViewModel::onCarbsChanged,
        onFatChanged = itemFormViewModel::onFatChanged,
        onUsdaQueryChanged = itemFormViewModel::onUsdaQueryChanged,
        onScanBarcodeClick = { barcodeScannerLauncher.launch(itemFormViewModel::onBarcodeScanned) },
        onSearchUsdaClick = itemFormViewModel::onUsdaSearch,
        onSaveClick = {
            val item = itemFormViewModel.buildFoodItem(existingId = existingItem?.id ?: 0L)
            if (existingItem != null) pantryViewModel.updateItem(item) else pantryViewModel.addItem(item)
            onSaved()
        },
        onCancelClick = onCancel,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Stateless/presentational half of [ItemFormScreen] (container-presentational split, see
 * `PantryListContent`), driven directly by `ItemFormScreenTest` with hand-built [ItemFormUiState]
 * fixtures - no Hilt/scanner/network dependency needed.
 */
@Composable
fun ItemFormContent(
    uiState: ItemFormUiState,
    isEditing: Boolean,
    onNameChanged: (String) -> Unit,
    onCanonicalUnitChanged: (MeasurementUnit) -> Unit,
    onCaloriesChanged: (String) -> Unit,
    onProteinChanged: (String) -> Unit,
    onCarbsChanged: (String) -> Unit,
    onFatChanged: (String) -> Unit,
    onUsdaQueryChanged: (String) -> Unit,
    onScanBarcodeClick: () -> Unit,
    onSearchUsdaClick: (String) -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = if (isEditing) "Edit item" else "Add item",
                style = MaterialTheme.typography.titleLarge,
            )

            Button(
                onClick = onScanBarcodeClick,
                modifier = Modifier.semantics { contentDescription = "Scan barcode" },
            ) {
                Text("Scan barcode")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = uiState.usdaQuery,
                    onValueChange = onUsdaQueryChanged,
                    label = { Text("Search USDA (e.g. banana raw)") },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onSearchUsdaClick(uiState.usdaQuery) }) {
                    Text("Search")
                }
            }

            if (uiState.isLookingUp) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = "Looking up nutrition data" },
                )
            }

            uiState.lookupError?.let { error ->
                Text(text = error, style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChanged,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )

            UnitSelector(selected = uiState.canonicalUnit, onSelected = onCanonicalUnitChanged)

            OutlinedTextField(
                value = uiState.caloriesPerUnit,
                onValueChange = onCaloriesChanged,
                label = { Text("Calories per unit") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.proteinGramsPerUnit,
                onValueChange = onProteinChanged,
                label = { Text("Protein (g) per unit") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.carbsGramsPerUnit,
                onValueChange = onCarbsChanged,
                label = { Text("Carbs (g) per unit") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.fatGramsPerUnit,
                onValueChange = onFatChanged,
                label = { Text("Fat (g) per unit") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onCancelClick) {
                    Text("Cancel")
                }
                Button(onClick = onSaveClick) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun UnitSelector(
    selected: MeasurementUnit,
    onSelected: (MeasurementUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = modifier) {
        Text(
            text = "Unit: ${selected.name}",
            modifier = Modifier
                .clickable { expanded = true }
                .semantics { contentDescription = "Unit selector" },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MeasurementUnit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.name) },
                    onClick = {
                        onSelected(unit)
                        expanded = false
                    },
                )
            }
        }
    }
}
