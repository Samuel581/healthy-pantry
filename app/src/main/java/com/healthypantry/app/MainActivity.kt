package com.healthypantry.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    Scaffold { innerPadding ->
        Text(
            text = "Healthy Pantry",
            modifier = Modifier.padding(innerPadding),
        )
    }
}
