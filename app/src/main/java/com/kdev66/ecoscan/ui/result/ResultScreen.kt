package com.kdev66.ecoscan.ui.result

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kdev66.ecoscan.R
import com.kdev66.ecoscan.ui.components.CategoryCounts
import com.kdev66.ecoscan.ui.components.DetectionOverlay
import com.kdev66.ecoscan.ui.components.ImageFit
import com.kdev66.ecoscan.ui.scanner.CapturedAnalysis

/** Résultat de la photo capturée. La revue, la correction et l'enregistrement arrivent à l'étape 2. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(analysis: CapturedAnalysis, onNewScan: () -> Unit) {
    BackHandler(onBack = onNewScan)
    val result = analysis.result
    val counts = remember(result) { result.countsByClass() }
    val image = remember(analysis.photo) { analysis.photo.asImageBitmap() }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.result_title)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(result.imageWidth.toFloat() / result.imageHeight)
                    .background(Color.Black),
            ) {
                Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                DetectionOverlay(result, ImageFit.FIT, Modifier.fillMaxSize())
            }

            Column(Modifier.padding(16.dp)) {
                val total = result.detections.size
                Text(
                    if (total == 0) stringResource(R.string.result_none)
                    else pluralStringResource(R.plurals.items_detected, total, total),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (total > 0) {
                    Spacer(Modifier.height(12.dp))
                    CategoryCounts(counts)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.result_estimate_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.result_inference, result.durationMs.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onNewScan, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.new_scan))
                }
            }
        }
    }
}
