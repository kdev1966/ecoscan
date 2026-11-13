package com.kdev66.ecoscan.ui.review

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdev66.ecoscan.R
import com.kdev66.ecoscan.detection.WasteClass
import com.kdev66.ecoscan.location.LocationTracker
import com.kdev66.ecoscan.report.Report
import com.kdev66.ecoscan.report.ReportContext
import com.kdev66.ecoscan.report.ReportEdits
import com.kdev66.ecoscan.report.ReviewedDetection
import com.kdev66.ecoscan.ui.components.BoxOverlay
import com.kdev66.ecoscan.ui.components.ImageFit
import com.kdev66.ecoscan.ui.components.OverlayBox
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Revue d'un signalement : corriger les boîtes, ajuster les quantités, choisir le contexte, situer, commenter,
 * enregistrer. Parcours du PRD : capturer, (corriger si besoin), enregistrer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(viewModel: ReviewViewModel, onFinished: (ReviewOutcome) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.review_detection_deleted)
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(state.outcome) { state.outcome?.let(onFinished) }
    BackHandler { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = {
                    IconButton(onClick = { confirmDiscard = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.review_discard))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = viewModel::save,
                    enabled = state.report != null && !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .height(52.dp),
                ) { Text(stringResource(R.string.review_save)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val report = state.report
        if (report == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            PhotoWithBoxes(state, onTap = viewModel::select)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Quantities(report, onSetCount = viewModel::setCount)
                ContextSection(report.context, onSelect = viewModel::setContext)
                PositionSection(state, viewModel)
                OutlinedTextField(
                    value = state.comment,
                    onValueChange = viewModel::setComment,
                    label = { Text(stringResource(R.string.review_comment)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    val selected = state.report?.detections?.firstOrNull { it.id == state.selectedId }
    if (selected != null) {
        ModalBottomSheet(onDismissRequest = { viewModel.select(null) }) {
            DetectionEditor(
                detection = selected,
                onChangeClass = { viewModel.changeClass(selected.id, it) },
                onDelete = {
                    viewModel.delete(selected.id)
                    scope.launch {
                        val result = snackbar.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Short)
                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(selected.id)
                    }
                },
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.review_discard_title)) },
            text = { Text(stringResource(R.string.review_discard_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; viewModel.discard() }) {
                    Text(stringResource(R.string.review_discard_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.review_keep)) }
            },
        )
    }
}

@Composable
private fun PhotoWithBoxes(state: ReviewUiState, onTap: (Long?) -> Unit) {
    val photo = state.photo
    val report = state.report ?: return
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
                boxes = report.detections.filterNot { it.deleted }.map {
                    OverlayBox(it.id, it.box, it.wasteClass, it.confidence, selected = it.id == state.selectedId)
                },
                imageWidth = photo.width,
                imageHeight = photo.height,
                fit = ImageFit.FIT,
                onTap = onTap,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    val kept = report.detections.count { !it.deleted }
    Text(
        stringResource(if (kept > 0) R.string.review_tap_hint else R.string.result_none),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Quantities(report: Report, onSetCount: (WasteClass, Int) -> Unit) {
    val boxes = remember(report.detections) { ReportEdits.boxCounts(report.detections) }
    val total = ReportEdits.total(report)
    Column {
        Text(stringResource(R.string.review_quantities), style = MaterialTheme.typography.titleMedium)
        Text(
            pluralStringResource(R.plurals.review_total, total, total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        for (wasteClass in WasteClass.entries) {
            val count = report.counts[wasteClass] ?: 0
            val floor = boxes[wasteClass] ?: 0
            val label = stringResource(wasteClass.labelRes)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 52.dp)) {
                Spacer(Modifier.size(12.dp).background(Color(wasteClass.argb), CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                val decrease = stringResource(R.string.review_decrease, label)
                FilledTonalIconButton(
                    onClick = { onSetCount(wasteClass, count - 1) },
                    enabled = count > floor,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = decrease; role = Role.Button },
                ) { Text("−") }
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.widthIn(min = 40.dp).padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center,
                )
                val increase = stringResource(R.string.review_increase, label)
                FilledTonalIconButton(
                    onClick = { onSetCount(wasteClass, count + 1) },
                    modifier = Modifier.clearAndSetSemantics { contentDescription = increase; role = Role.Button },
                ) { Text("+") }
            }
        }
        Text(
            stringResource(R.string.result_estimate_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ContextSection(selected: ReportContext, onSelect: (ReportContext) -> Unit) {
    Column {
        Text(stringResource(R.string.review_context), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (context in ReportContext.entries) {
                FilterChip(
                    selected = context == selected,
                    onClick = { onSelect(context) },
                    label = { Text(stringResource(context.labelRes())) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (selected.experimental) {
            Text(
                stringResource(R.string.review_experimental),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PositionSection(state: ReviewUiState, viewModel: ReviewViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.onLocationPermissionResult(result.values.any { it })
    }
    Column {
        Text(stringResource(R.string.review_position), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        val position = state.report?.position
        when (state.positionStatus) {
            PositionStatus.KNOWN -> Text(
                position?.accuracyM?.let { stringResource(R.string.position_accuracy, it.roundToInt()) }
                    ?: stringResource(R.string.position_known),
            )
            PositionStatus.SEARCHING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.position_searching))
            }
            PositionStatus.NO_PERMISSION -> if (state.canRetryPosition) {
                Text(stringResource(R.string.position_rationale), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { launcher.launch(LocationTracker.PERMISSIONS) }) {
                    Text(stringResource(R.string.position_grant))
                }
            } else {
                Text(stringResource(R.string.position_unavailable))
            }
            PositionStatus.UNAVAILABLE -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.position_unavailable), modifier = Modifier.weight(1f))
                if (state.canRetryPosition) {
                    TextButton(onClick = viewModel::retryPosition) { Text(stringResource(R.string.retry)) }
                }
            }
        }
    }
}

@Composable
private fun DetectionEditor(
    detection: ReviewedDetection,
    onChangeClass: (WasteClass) -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text(stringResource(R.string.review_edit_title), style = MaterialTheme.typography.titleMedium)
        detection.confidence?.let {
            Text(
                stringResource(
                    R.string.review_model_said,
                    stringResource((detection.modelClass ?: detection.wasteClass).labelRes),
                    (it * 100).roundToInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        for (wasteClass in WasteClass.entries) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = wasteClass == detection.wasteClass,
                        role = Role.RadioButton,
                        onClick = { onChangeClass(wasteClass) },
                    ),
            ) {
                RadioButton(selected = wasteClass == detection.wasteClass, onClick = null)
                Spacer(Modifier.width(8.dp))
                Spacer(Modifier.size(12.dp).background(Color(wasteClass.argb), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(wasteClass.labelRes))
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        OutlinedButton(
            onClick = onDelete,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.review_delete)) }
    }
}

private fun ReportContext.labelRes(): Int = when (this) {
    ReportContext.BEACH -> R.string.context_beach
    ReportContext.ROCKY_COAST -> R.string.context_rocky_coast
    ReportContext.FOREST -> R.string.context_forest
    ReportContext.CITY -> R.string.context_city
}
