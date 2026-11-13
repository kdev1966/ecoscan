package com.kdev66.ecoscan.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdev66.ecoscan.R
import com.kdev66.ecoscan.camera.CameraPreview
import com.kdev66.ecoscan.ui.components.CategoryCounts
import com.kdev66.ecoscan.ui.components.DetectionOverlay
import com.kdev66.ecoscan.ui.components.ImageFit

@Composable
fun ScannerScreen(viewModel: ScannerViewModel) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    if (hasPermission) {
        CameraScanner(viewModel)
    } else {
        PermissionRequest(onGrant = { launcher.launch(Manifest.permission.CAMERA) })
    }
}

@Composable
private fun CameraScanner(viewModel: ScannerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(
            analyzer = viewModel::analyzeFrame,
            analysisExecutor = viewModel.analysisExecutor,
            imageCapture = imageCapture,
            onError = viewModel::onCameraError,
            modifier = Modifier.fillMaxSize(),
        )
        state.live?.let { DetectionOverlay(it, ImageFit.FILL, Modifier.fillMaxSize()) }

        // Consigne de prise de vue (le modèle a appris sur des vues verticales prises par drone)
        Text(
            stringResource(R.string.scanner_hint),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        ) {
            state.live?.let { live ->
                CategoryCounts(
                    live.countsByClass(),
                    chipColor = Color.Black.copy(alpha = 0.6f),
                    textColor = Color.White,
                )
                Text(
                    stringResource(R.string.inference_time, live.durationMs.toInt()),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            CaptureButton(capturing = state.capturing, onClick = { viewModel.capture(imageCapture) })
        }

        val snackbar = remember { SnackbarHostState() }
        val message = state.message?.let { stringResource(it) }
        LaunchedEffect(message) {
            if (message != null) {
                snackbar.showSnackbar(message)
                viewModel.messageShown()
            }
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )

        state.error?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(android.R.string.ok)) } },
                text = { Text(stringResource(message)) },
            )
        }
    }
}

@Composable
private fun CaptureButton(capturing: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (capturing) R.string.analyzing else R.string.capture)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .background(if (capturing) Color.White.copy(alpha = 0.4f) else Color.White, CircleShape)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .clickable(enabled = !capturing, onClick = onClick),
    ) {
        if (capturing) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PermissionRequest(onGrant: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(32.dp),
    ) {
        Text(
            stringResource(R.string.permission_camera_rationale),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text(stringResource(R.string.permission_grant)) }
    }
}
