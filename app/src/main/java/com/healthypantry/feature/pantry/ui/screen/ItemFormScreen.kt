package com.healthypantry.feature.pantry.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthypantry.app.theme.LocalHealthyPantryExtraColors
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.nutrition.domain.model.NutritionResult
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.StockBatch
import com.healthypantry.feature.pantry.ui.vm.ConversionRowState
import com.healthypantry.feature.pantry.ui.vm.ItemFormUiState
import com.healthypantry.feature.pantry.ui.vm.ItemFormViewModel
import com.healthypantry.feature.pantry.ui.vm.PantryViewModel
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Spec: "Barcode Scan via Open Food Facts", "Manual Entry with USDA FDC Fallback" (nutrition-
 * lookup domain, openspec/changes/pantry-tracker/specs/nutrition-lookup/spec.md).
 *
 * Stateful container mirroring `PantryListScreen`'s container/presentational split: obtains
 * [ItemFormViewModel] (owns lookup-in-progress state) and [PantryViewModel] (persistence -
 * reused rather than duplicating `upsert` logic) via [hiltViewModel], and delegates rendering to
 * the stateless [ItemFormContent]. [existingItem] puts the form in edit mode.
 *
 * On save, every registered [com.healthypantry.core.unit.ConversionFactor] built from the form's
 * "Unit conversions" rows is persisted one-by-one via [PantryViewModel.addConversionFactor] —
 * against the just-returned id for a new item, or [existingItem]'s id when editing. For a brand
 * new item, [ItemFormContent]'s optional "Starting batch" quantity/expiry (if both were filled
 * in) is attached via [PantryViewModel.addStockBatch] once [PantryViewModel.addItem] resolves its
 * new id — this is why the add-path now runs inside [coroutineScope] rather than firing
 * [onSaved] immediately: the starting batch and conversions need that id first. The edit path
 * stays fire-and-forget (no id resolution needed) so [onSaved] still fires immediately there.
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
    val coroutineScope = rememberCoroutineScope()

    // Neither PantryViewModel.updateItem (fire-and-forget) nor the addItem launch below wait for
    // persistence to confirm before this screen navigates away - a save failure (e.g. a Room
    // constraint violation) can only surface after onSaved() has already navigated away, so it's
    // collected here rather than awaited, matching PantryListScreen's own errorEvent-as-Snackbar
    // convention.
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
        onResultSelected = itemFormViewModel::onResultSelected,
        onAddConversionRow = itemFormViewModel::addConversionRow,
        onUpdateConversionRow = itemFormViewModel::updateConversionRow,
        onRemoveConversionRow = itemFormViewModel::removeConversionRow,
        onSaveClick = { startingBatchQuantity, startingBatchExpiryDate ->
            val item = itemFormViewModel.buildFoodItem(existingId = existingItem?.id ?: 0L)
            if (existingItem != null) {
                pantryViewModel.updateItem(item)
                itemFormViewModel.buildConversionFactors().forEach { factor ->
                    pantryViewModel.addConversionFactor(existingItem.id, factor)
                }
                onSaved()
            } else {
                coroutineScope.launch {
                    val newId = pantryViewModel.addItem(item)
                    itemFormViewModel.buildConversionFactors().forEach { factor ->
                        pantryViewModel.addConversionFactor(newId, factor)
                    }
                    if (startingBatchQuantity != null && startingBatchQuantity > 0.0) {
                        pantryViewModel.addStockBatch(
                            StockBatch(
                                foodItemId = newId,
                                quantity = startingBatchQuantity,
                                expiryDate = startingBatchExpiryDate,
                                addedAt = Instant.now(),
                            ),
                        )
                    }
                    onSaved()
                }
            }
        },
        onCancelClick = onCancel,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/** Which step of the add-item flow is showing (design mockup "Item Form — mode-based flow"). Edit
 * mode always starts (and stays) at [MANUAL] — see [ItemFormContent]. */
private enum class FormMode { CHOICE, MANUAL }

/**
 * Stateless/presentational half of [ItemFormScreen] (container-presentational split, see
 * `PantryListContent`), driven directly by `ItemFormScreenTest` with hand-built [ItemFormUiState]
 * fixtures - no Hilt/scanner/network dependency needed.
 *
 * [FormMode] is local, ephemeral UI state (same convention as `ItemDetailScreen`'s
 * `AddBatchForm` quantity/date fields) rather than [ItemFormViewModel] state: a brand-new item
 * starts at [FormMode.CHOICE] ("Scan barcode" / "Manual entry"); editing an existing item
 * ([isEditing]) skips straight to [FormMode.MANUAL], pre-filled, same as before this redesign.
 * [uiState].`barcode` turning non-null (a scan just completed, success or miss - see
 * [ItemFormViewModel.onBarcodeScanned]) drives the [LaunchedEffect] that flips [FormMode.CHOICE]
 * to [FormMode.MANUAL] automatically: this reuses the ViewModel's existing prefill-then-manual-
 * entry flow directly rather than adding a distinct "scan result" confirmation screen/state (the
 * mockup's `scanResult` step) - a successful match's fields are already prefilled by the time
 * this fires, and a miss's [ItemFormUiState.lookupError] is already surfaced right below the name
 * field, so a second confirmation step would only duplicate what manual mode already shows.
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
    onResultSelected: (NutritionResult) -> Unit,
    onAddConversionRow: () -> Unit,
    onUpdateConversionRow: (Int, ConversionRowState) -> Unit,
    onRemoveConversionRow: (Int) -> Unit,
    onSaveClick: (startingBatchQuantity: Double?, startingBatchExpiryDate: LocalDate?) -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var mode by remember(isEditing) { mutableStateOf(if (isEditing) FormMode.MANUAL else FormMode.CHOICE) }

    LaunchedEffect(uiState.barcode) {
        if (uiState.barcode != null) mode = FormMode.MANUAL
    }

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
            ItemFormHeader(isEditing = isEditing, onBack = onCancelClick)
            Spacer(modifier = Modifier.height(16.dp))

            when (mode) {
                FormMode.CHOICE -> ChoiceModeContent(
                    onScanBarcodeClick = onScanBarcodeClick,
                    onManualEntryClick = { mode = FormMode.MANUAL },
                )

                FormMode.MANUAL -> ManualModeContent(
                    uiState = uiState,
                    isEditing = isEditing,
                    onNameChanged = onNameChanged,
                    onCanonicalUnitChanged = onCanonicalUnitChanged,
                    onCaloriesChanged = onCaloriesChanged,
                    onProteinChanged = onProteinChanged,
                    onCarbsChanged = onCarbsChanged,
                    onFatChanged = onFatChanged,
                    onUsdaQueryChanged = onUsdaQueryChanged,
                    onSearchUsdaClick = onSearchUsdaClick,
                    onResultSelected = onResultSelected,
                    onAddConversionRow = onAddConversionRow,
                    onUpdateConversionRow = onUpdateConversionRow,
                    onRemoveConversionRow = onRemoveConversionRow,
                    onSaveClick = onSaveClick,
                    onCancelClick = onCancelClick,
                )
            }
        }
    }
}

@Composable
private fun ItemFormHeader(isEditing: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = null)
        }
        Text(
            text = if (isEditing) "Edit item" else "Add item",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ChoiceModeContent(
    onScanBarcodeClick: () -> Unit,
    onManualEntryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceCard(
            icon = Icons.Rounded.QrCodeScanner,
            title = "Scan barcode",
            subtitle = "Looks up Open Food Facts, no camera permission needed",
            contentDescription = "Scan barcode",
            onClick = onScanBarcodeClick,
        )
        ChoiceCard(
            icon = Icons.Rounded.Edit,
            title = "Manual entry",
            subtitle = "With a USDA FoodData Central search fallback",
            contentDescription = "Manual entry",
            onClick = onManualEntryClick,
        )
    }
}

@Composable
private fun ChoiceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extraColors = LocalHealthyPantryExtraColors.current
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = extraColors.accent100, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icon, contentDescription = null, tint = extraColors.accent700)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = extraColors.neutral700)
            }
        }
    }
}

@Composable
private fun ManualModeContent(
    uiState: ItemFormUiState,
    isEditing: Boolean,
    onNameChanged: (String) -> Unit,
    onCanonicalUnitChanged: (MeasurementUnit) -> Unit,
    onCaloriesChanged: (String) -> Unit,
    onProteinChanged: (String) -> Unit,
    onCarbsChanged: (String) -> Unit,
    onFatChanged: (String) -> Unit,
    onUsdaQueryChanged: (String) -> Unit,
    onSearchUsdaClick: (String) -> Unit,
    onResultSelected: (NutritionResult) -> Unit,
    onAddConversionRow: () -> Unit,
    onUpdateConversionRow: (Int, ConversionRowState) -> Unit,
    onRemoveConversionRow: (Int) -> Unit,
    onSaveClick: (startingBatchQuantity: Double?, startingBatchExpiryDate: LocalDate?) -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var startingBatchQuantityText by remember { mutableStateOf("") }
    var startingBatchDateText by remember { mutableStateOf("") }
    var startingBatchDateError by remember { mutableStateOf(false) }

    val unit = unitLabel(uiState.canonicalUnit)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Design decision: no distinct "scanResult" confirmation step (see ItemFormContent KDoc)
        // - this card is just a lightweight acknowledgement inline within the already-prefilled
        // manual form, only for a NEW item right after a successful scan.
        if (!isEditing && uiState.barcode != null && uiState.lookupError == null) {
            ScanResultCard(uiState = uiState, unit = unit)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        if (uiState.searchResults.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Search results", style = MaterialTheme.typography.labelLarge)
                uiState.searchResults.forEach { result ->
                    UsdaResultRow(result = result, onClick = { onResultSelected(result) })
                }
            }
        }

        OutlinedTextField(
            value = uiState.name,
            onValueChange = onNameChanged,
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
        )

        UnitSelector(selected = uiState.canonicalUnit, onSelected = onCanonicalUnitChanged)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Macros per $unit", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.caloriesPerUnit,
                    onValueChange = onCaloriesChanged,
                    label = { Text("Kcal") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = uiState.proteinGramsPerUnit,
                    onValueChange = onProteinChanged,
                    label = { Text("Protein (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.carbsGramsPerUnit,
                    onValueChange = onCarbsChanged,
                    label = { Text("Carbs (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = uiState.fatGramsPerUnit,
                    onValueChange = onFatChanged,
                    label = { Text("Fat (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        UnitConversionsSection(
            conversions = uiState.conversions,
            canonicalUnit = uiState.canonicalUnit,
            onAddConversionRow = onAddConversionRow,
            onUpdateConversionRow = onUpdateConversionRow,
            onRemoveConversionRow = onRemoveConversionRow,
        )

        if (!isEditing) {
            StartingBatchSection(
                unit = unit,
                quantityText = startingBatchQuantityText,
                onQuantityChanged = { startingBatchQuantityText = it },
                dateText = startingBatchDateText,
                onDateChanged = {
                    startingBatchDateText = it
                    startingBatchDateError = false
                },
                dateError = startingBatchDateError,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onCancelClick) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val parsedDate = if (startingBatchDateText.isBlank()) {
                        null
                    } else {
                        runCatching { LocalDate.parse(startingBatchDateText) }.getOrNull()
                    }
                    if (startingBatchDateText.isNotBlank() && parsedDate == null) {
                        startingBatchDateError = true
                        return@Button
                    }
                    onSaveClick(startingBatchQuantityText.toDoubleOrNull(), parsedDate)
                },
            ) {
                Text(if (isEditing) "Save changes" else "Add to pantry")
            }
        }
    }
}

@Composable
private fun ScanResultCard(uiState: ItemFormUiState, unit: String, modifier: Modifier = Modifier) {
    val extraColors = LocalHealthyPantryExtraColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = extraColors.accent100),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "Open Food Facts match", style = MaterialTheme.typography.labelMedium, color = extraColors.accent800)
            Text(
                text = uiState.name.ifBlank { "Unnamed item" },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Barcode ${uiState.barcode} · per $unit",
                style = MaterialTheme.typography.bodySmall,
                color = extraColors.neutral700,
            )
        }
    }
}

@Composable
private fun UsdaResultRow(result: NutritionResult, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val extraColors = LocalHealthyPantryExtraColors.current
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = extraColors.neutral200,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = result.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = usdaMacroSummary(result), style = MaterialTheme.typography.bodySmall, color = extraColors.neutral700)
        }
    }
}

/** "kcal/100g · P · C · F" one-line macro summary for a USDA search result row. */
private fun usdaMacroSummary(result: NutritionResult): String {
    val kcal = result.caloriesPer100?.let { "%.0f kcal".format(it) } ?: "— kcal"
    val protein = result.proteinGramsPer100?.let { "%.1fg P".format(it) } ?: "—g P"
    val carbs = result.carbsGramsPer100?.let { "%.1fg C".format(it) } ?: "—g C"
    val fat = result.fatGramsPer100?.let { "%.1fg F".format(it) } ?: "—g F"
    return "$kcal/100g · $protein · $carbs · $fat"
}

@Composable
private fun UnitConversionsSection(
    conversions: List<ConversionRowState>,
    canonicalUnit: MeasurementUnit,
    onAddConversionRow: () -> Unit,
    onUpdateConversionRow: (Int, ConversionRowState) -> Unit,
    onRemoveConversionRow: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Unit conversions", style = MaterialTheme.typography.titleMedium)
        conversions.forEachIndexed { index, row ->
            ConversionRow(
                row = row,
                canonicalUnit = canonicalUnit,
                rowNumber = index + 1,
                onChange = { updated -> onUpdateConversionRow(index, updated) },
                onRemove = { onRemoveConversionRow(index) },
            )
        }
        TextButton(onClick = onAddConversionRow) {
            Text("+ Add conversion")
        }
    }
}

@Composable
private fun ConversionRow(
    row: ConversionRowState,
    canonicalUnit: MeasurementUnit,
    rowNumber: Int,
    onChange: (ConversionRowState) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UnitSelector(
            selected = row.fromUnit,
            onSelected = { onChange(row.copy(fromUnit = it, toUnit = canonicalUnit)) },
            modifier = Modifier.weight(1f),
        )
        Text(text = "=", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = row.factor,
            onValueChange = { onChange(row.copy(factor = it, toUnit = canonicalUnit)) },
            label = { Text(unitLabel(canonicalUnit)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.semantics { contentDescription = "Remove conversion $rowNumber" },
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null)
        }
    }
}

@Composable
private fun StartingBatchSection(
    unit: String,
    quantityText: String,
    onQuantityChanged: (String) -> Unit,
    dateText: String,
    onDateChanged: (String) -> Unit,
    dateError: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Starting batch (optional)", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = quantityText,
                onValueChange = onQuantityChanged,
                label = { Text("Quantity ($unit)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = dateText,
                onValueChange = onDateChanged,
                label = { Text("Expiry (YYYY-MM-DD)") },
                isError = dateError,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun UnitSelector(
    selected: MeasurementUnit,
    onSelected: (MeasurementUnit) -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptionLabel: String = "Unit selector",
) {
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = modifier) {
        Text(
            text = "Unit: ${selected.name}",
            modifier = Modifier
                .clickable { expanded = true }
                .padding(vertical = 8.dp)
                .semantics { contentDescription = contentDescriptionLabel },
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

/** Lowercase display form of a [MeasurementUnit], e.g. `GRAM` -> `gram`. */
private fun unitLabel(unit: MeasurementUnit): String = unit.name.lowercase()
