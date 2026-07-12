package com.healthypantry.feature.pantry.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Thin, testable seam around the Google Code Scanner (`play-services-code-scanner`) so
 * `ItemFormScreenTest` can drive [ItemFormContent] without a real scanner/camera dependency (same
 * "extract the pure/stateless part and test that" convention as `PantryListScreenTest`).
 *
 * The Google Code Scanner needs no camera permission and no manual CameraX/ML Kit wiring - it's a
 * full-screen Google-provided UI launched via `GmsBarcodeScanner.startScan()`.
 */
fun interface BarcodeScannerLauncher {
    fun launch(onBarcodeScanned: (String) -> Unit)
}

@Composable
fun rememberBarcodeScannerLauncher(): BarcodeScannerLauncher {
    val context = LocalContext.current
    return remember {
        BarcodeScannerLauncher { onBarcodeScanned ->
            GmsBarcodeScanning.getClient(context)
                .startScan()
                .addOnSuccessListener { barcode ->
                    barcode.rawValue?.let(onBarcodeScanned)
                }
                .addOnFailureListener {
                    // User cancelled, or a scan-time error occurred: the form simply stays in
                    // its current (manual-entry-capable) state - a failed/cancelled scan must
                    // never block the form, per spec "OFF data gap".
                }
        }
    }
}
