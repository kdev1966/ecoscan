package com.kdev66.ecoscan.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kdev66.ecoscan.appContainer
import com.kdev66.ecoscan.ui.result.ResultScreen
import com.kdev66.ecoscan.ui.scanner.ScannerScreen
import com.kdev66.ecoscan.ui.scanner.ScannerViewModel
import com.kdev66.ecoscan.ui.theme.EcoScanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = appContainer
        setContent {
            EcoScanTheme {
                // Portée de l'activité : le scanner et le résultat partagent le même état.
                val scanner: ScannerViewModel = viewModel(
                    factory = viewModelFactory { initializer { ScannerViewModel { container.detector } } },
                )
                EcoScanNavHost(scanner)
            }
        }
    }
}

private object Routes {
    const val SCANNER = "scanner"
    const val RESULT = "result"
}

@Composable
private fun EcoScanNavHost(scanner: ScannerViewModel) {
    val navController = rememberNavController()
    val state by scanner.state.collectAsStateWithLifecycle()

    // Une photo analysée ouvre le résultat.
    LaunchedEffect(state.captured) {
        if (state.captured != null && navController.currentDestination?.route != Routes.RESULT) {
            navController.navigate(Routes.RESULT)
        }
    }

    NavHost(navController, startDestination = Routes.SCANNER) {
        composable(Routes.SCANNER) { ScannerScreen(scanner) }
        composable(Routes.RESULT) {
            val captured = state.captured
            if (captured != null) {
                ResultScreen(captured, onNewScan = {
                    scanner.newScan()
                    navController.popBackStack(Routes.SCANNER, inclusive = false)
                })
            }
        }
    }
}
