package com.kdev66.ecoscan.ui.history

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kdev66.ecoscan.R
import com.kdev66.ecoscan.report.data.ReportRepository
import com.kdev66.ecoscan.report.data.ReportSummary
import com.kdev66.ecoscan.report.export.ExportFiles
import com.kdev66.ecoscan.ui.components.PhotoThumbnail
import com.kdev66.ecoscan.ui.components.formatCaptureDate
import com.kdev66.ecoscan.ui.components.labelRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryExportState(
    val running: Boolean = false,
    /** Archive prête à partager (consommée par l'écran). */
    val ready: ExportFiles.Export? = null,
    val failed: Boolean = false,
)

class HistoryViewModel(repository: ReportRepository, private val exports: ExportFiles) : ViewModel() {
    /** `null` pendant le chargement. */
    val reports: StateFlow<List<ReportSummary>?> = repository.observeHistory()
        .map<List<ReportSummary>, List<ReportSummary>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _export = MutableStateFlow(HistoryExportState())
    val export: StateFlow<HistoryExportState> = _export.asStateFlow()

    fun exportAll() {
        if (_export.value.running) return
        _export.value = HistoryExportState(running = true)
        viewModelScope.launch {
            _export.value = try {
                HistoryExportState(ready = exports.exportAll())
            } catch (e: Exception) {
                Log.e("EcoScan", "export impossible", e)
                HistoryExportState(failed = true)
            }
        }
    }

    fun exportHandled() = _export.update { HistoryExportState() }
}

/** Historique local (PRD § F4) et export de tous les signalements (PRD § F5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    exports: ExportFiles,
    onOpenReport: (String) -> Unit,
    onBack: () -> Unit,
) {
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val export by viewModel.export.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val chooserTitle = stringResource(R.string.export_share)
    val failedMessage = stringResource(R.string.export_failed)
    val missingMessage = export.ready?.let {
        if (it.missingPhotos > 0) pluralStringResource(R.plurals.export_missing_photos, it.missingPhotos, it.missingPhotos) else null
    }

    // L'état n'est remis à zéro qu'à la fin : le changer plus tôt annulerait cet effet avant ses messages.
    LaunchedEffect(export) {
        val ready = export.ready
        when {
            ready != null -> {
                try {
                    context.startActivity(exports.shareIntent(ready, chooserTitle))
                    missingMessage?.let { snackbar.showSnackbar(it) }
                } catch (e: ActivityNotFoundException) {
                    snackbar.showSnackbar(failedMessage)
                }
                viewModel.exportHandled()
            }
            export.failed -> {
                snackbar.showSnackbar(failedMessage)
                viewModel.exportHandled()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (export.running) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                    } else if (!reports.isNullOrEmpty()) {
                        IconButton(onClick = viewModel::exportAll) {
                            Icon(Icons.Filled.Share, stringResource(R.string.export_all))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = reports
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), Alignment.Center) {
                Text(
                    stringResource(R.string.history_empty),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                items(list, key = { it.id }) { item ->
                    HistoryRow(item, onClick = { onOpenReport(item.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: ReportSummary, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        PhotoThumbnail(item.photo, maxSide = 160, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
        Column(Modifier.weight(1f)) {
            Text(formatCaptureDate(item.createdAtMs, item.utcOffsetMinutes), style = MaterialTheme.typography.titleSmall)
            Text(
                pluralStringResource(R.plurals.review_total, item.total, item.total),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(item.context.labelRes()) +
                    if (item.located) "" else " · " + stringResource(R.string.position_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
