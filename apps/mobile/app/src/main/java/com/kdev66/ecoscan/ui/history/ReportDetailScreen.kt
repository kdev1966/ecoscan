package com.kdev66.ecoscan.ui.history

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kdev66.ecoscan.R
import com.kdev66.ecoscan.report.Position
import com.kdev66.ecoscan.report.Report
import com.kdev66.ecoscan.report.ReportEdits
import com.kdev66.ecoscan.report.data.ReportRepository
import com.kdev66.ecoscan.ui.components.BoxOverlay
import com.kdev66.ecoscan.ui.components.CategoryCounts
import com.kdev66.ecoscan.ui.components.ImageFit
import com.kdev66.ecoscan.ui.components.OverlayBox
import com.kdev66.ecoscan.ui.components.formatCaptureDate
import com.kdev66.ecoscan.ui.components.labelRes
import com.kdev66.ecoscan.ui.components.rememberPhoto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class ReportDetailViewModel(private val reportId: String, private val repository: ReportRepository) : ViewModel() {
    val report: StateFlow<Report?> = repository.observe(reportId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun photoFile(report: Report) = repository.photoFile(report)

    fun delete() {
        viewModelScope.launch {
            repository.discard(reportId)
            _deleted.value = true
        }
    }
}

/** Fiche d'un signalement enregistré (PRD § F4) : photo annotée, quantités, lieu, position, suppression. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(viewModel: ReportDetailViewModel, onBack: () -> Unit) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(deleted) { if (deleted) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (report != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.report_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val r = report
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            AnnotatedPhoto(r, viewModel)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text(formatCaptureDate(r.createdAtMs, r.utcOffsetMinutes), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(r.context.labelRes()) +
                            if (r.context.experimental) " · " + stringResource(R.string.experimental) else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column {
                    val total = ReportEdits.total(r)
                    Text(pluralStringResource(R.plurals.review_total, total, total), style = MaterialTheme.typography.titleMedium)
                    if (r.counts.isNotEmpty()) CategoryCounts(r.counts, Modifier.padding(top = 8.dp))
                }
                PositionBlock(r.position)
                r.comment?.let {
                    Column {
                        Text(stringResource(R.string.review_comment), style = MaterialTheme.typography.titleMedium)
                        Text(it)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.report_delete_title)) },
            text = { Text(stringResource(R.string.review_discard_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.report_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun AnnotatedPhoto(report: Report, viewModel: ReportDetailViewModel) {
    val photo = rememberPhoto(viewModel.photoFile(report))
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(if (photo != null) photo.width.toFloat() / photo.height else 3f / 4f)
            .background(Color.Black),
    ) {
        if (photo != null) {
            val image = remember(photo) { photo.asImageBitmap() }
            Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            BoxOverlay(
                boxes = report.detections.filterNot { it.deleted }.map { OverlayBox(it.id, it.box, it.wasteClass, it.confidence) },
                imageWidth = photo.width,
                imageHeight = photo.height,
                fit = ImageFit.FIT,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PositionBlock(position: Position?) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noMapApp = stringResource(R.string.report_no_map_app)
    Column {
        Text(stringResource(R.string.review_position), style = MaterialTheme.typography.titleMedium)
        if (position == null) {
            Text(stringResource(R.string.position_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val coords = "%.5f, %.5f".format(Locale.ROOT, position.latitude, position.longitude)
        Text(coords + (position.accuracyM?.let { " (±${it.roundToInt()} m)" } ?: ""))
        OutlinedButton(
            onClick = {
                // geo: est compris par les applications de cartes, y compris hors ligne (OsmAnd, Organic Maps…).
                val lat = "%.6f".format(Locale.ROOT, position.latitude)
                val lon = "%.6f".format(Locale.ROOT, position.longitude)
                val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: ActivityNotFoundException) {
                    scope.launch { snackbar.showSnackbar(noMapApp) }
                }
            },
            modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.report_open_map)) }
        SnackbarHost(snackbar)
    }
}
