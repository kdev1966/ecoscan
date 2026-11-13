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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kdev66.ecoscan.AppContainer
import com.kdev66.ecoscan.R
import com.kdev66.ecoscan.appContainer
import com.kdev66.ecoscan.detection.WasteDetector
import com.kdev66.ecoscan.ui.review.ReviewOutcome
import com.kdev66.ecoscan.ui.review.ReviewScreen
import com.kdev66.ecoscan.ui.review.ReviewViewModel
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
                // Portée de l'activité : le scanner garde son état pendant la revue.
                val scanner: ScannerViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            ScannerViewModel(
                                detectorProvider = { container.detector },
                                createDraft = { photo, result ->
                                    val draft = container.reports.createDraft(photo, result, WasteDetector.MODEL_VERSION)
                                    // La position est cherchée dès la capture, pendant que l'utilisateur est sur place.
                                    container.location.locate(draft.id, draft.createdAtMs)
                                    draft.id
                                },
                            )
                        }
                    },
                )
                EcoScanNavHost(container, scanner)
            }
        }
    }
}

private object Routes {
    const val SCANNER = "scanner"
    const val REVIEW = "review/{reportId}"
    fun review(reportId: String) = "review/$reportId"
}

@Composable
private fun EcoScanNavHost(container: AppContainer, scanner: ScannerViewModel) {
    val navController = rememberNavController()
    val state by scanner.state.collectAsStateWithLifecycle()

    // Un brouillon laissé par une application interrompue est rouvert au démarrage (PRD § 6).
    LaunchedEffect(Unit) {
        container.reports.latestDraftId()?.let { navController.openReview(it) }
    }
    // Une photo analysée ouvre sa revue.
    LaunchedEffect(state.capturedReportId) {
        state.capturedReportId?.let { navController.openReview(it) }
    }

    NavHost(navController, startDestination = Routes.SCANNER) {
        composable(Routes.SCANNER) { ScannerScreen(scanner) }
        composable(Routes.REVIEW, arguments = listOf(navArgument("reportId") { type = NavType.StringType })) { entry ->
            val reportId = requireNotNull(entry.arguments?.getString("reportId"))
            val review: ReviewViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ReviewViewModel(reportId, container.reports, container.location, container.applicationScope) }
                },
            )
            ReviewScreen(review, onFinished = { outcome ->
                scanner.newScan(
                    when (outcome) {
                        ReviewOutcome.SAVED -> R.string.report_saved
                        ReviewOutcome.DISCARDED -> R.string.report_discarded
                        ReviewOutcome.MISSING -> null
                    },
                )
                navController.popBackStack(Routes.SCANNER, inclusive = false)
            })
        }
    }
}

private fun NavHostController.openReview(reportId: String) {
    if (currentBackStackEntry?.arguments?.getString("reportId") == reportId) return
    navigate(Routes.review(reportId)) { launchSingleTop = true }
}
