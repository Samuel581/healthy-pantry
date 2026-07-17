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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.healthypantry.app.navigation.HealthyPantryBottomBar
import com.healthypantry.app.navigation.HealthyPantryNavHost
import com.healthypantry.app.theme.HealthyPantryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealthyPantryTheme {
                HealthyPantryRoot()
            }
        }
    }
}

/**
 * App root: hosts the bottom-nav [Scaffold] (`HealthyPantryBottomBar` + `HealthyPantryNavHost`,
 * see `app/navigation`) wiring together the Pantry/Recipes/Plan tabs (design.md "app/ ...
 * nav, theme"; proposal.md "Scaffold + bottom NavigationBar").
 */
@Composable
private fun HealthyPantryRoot() {
    RequestNotificationPermission()
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { HealthyPantryBottomBar(navController) },
    ) { innerPadding ->
        HealthyPantryNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
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
