package com.healthypantry.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.healthypantry.app.theme.HealthyPantryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealthyPantryTheme {
                HealthyPantryScaffold()
            }
        }
    }
}

@Composable
private fun HealthyPantryScaffold() {
    RequestNotificationPermission()
    Scaffold { innerPadding ->
        Text(
            text = "Healthy Pantry",
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * Spec "Expiry Notification Scheduling": requests `POST_NOTIFICATIONS` on API 33+ so
 * [com.healthypantry.feature.expiry.worker.ExpiryCheckWorker]'s system notification can be
 * shown. A denial is not an error state here — spec "Notification-Denied Fallback" covers it via
 * the in-app banner, independent of this permission.
 */
@Composable
private fun RequestNotificationPermission() {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
