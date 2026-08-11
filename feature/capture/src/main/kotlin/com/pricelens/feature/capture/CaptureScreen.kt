package com.pricelens.feature.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pricelens.core.designsystem.component.AbstainPrompt
import com.pricelens.feature.capture.R
import com.pricelens.feature.capture.camera.model.AnalyzableFrame
import com.pricelens.ml.pipeline.model.PipelineResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onNavigateToReview: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CaptureEvent.NavigateToReview -> onNavigateToReview(event.observationId)
            }
        }
    }
    
    val isCalibrationMode by viewModel.isCalibrationMode.collectAsStateWithLifecycle()
    val recognitionResult: PipelineResult? by viewModel.recognitionResult.collectAsStateWithLifecycle()
    val coachingHint by viewModel.coachingHint.collectAsStateWithLifecycle()
    val geohash by viewModel.geohash.collectAsStateWithLifecycle()
    val deviceClassId by viewModel.deviceClassId.collectAsStateWithLifecycle()
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PriceLens") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = Color.Black // Better for camera preview
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ImplementationMode.COMPATIBLE.let { PreviewView.ScaleType.FILL_CENTER }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    viewModel.setupCamera(previewView, lifecycleOwner)
                }
            )

            if (isCalibrationMode) {
                // ... calibration overlay
            }

            if (recognitionResult?.abstained == true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AbstainPrompt(
                        options = recognitionResult?.candidates ?: emptyList(),
                        onOptionSelected = { viewModel.onAbstainOptionSelected(it) }
                    )
                }
            }

            // Capture UI
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                coachingHint?.let { hint ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        ),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                val canCapture = recognitionResult?.qualityIssues?.isEmpty() == true

                Button(
                    onClick = { }, // Handled in pointerInput
                    modifier = Modifier
                        .size(72.dp)
                        .pointerInput(canCapture, isCalibrationMode, geohash, deviceClassId) {
                            detectTapGestures(
                                onTap = {
                                    if (canCapture || isCalibrationMode) {
                                        viewModel.onCaptureTapped(deviceClassId, geohash)
                                    }
                                },
                                onLongPress = {
                                    // Expert override: capture even if quality is low
                                    viewModel.onCaptureTapped(deviceClassId, geohash)
                                }
                            )
                        },
                    shape = CircleShape,
                    colors = if (canCapture || isCalibrationMode) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    }
                ) {
                    Text(text = if (isCalibrationMode) "Calibrate" else "Seal")
                }
                
                if (!canCapture && !isCalibrationMode) {
                    Text(
                        text = "Hold button to override",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(text = stringResource(id = R.string.camera_permission_denied))
                    Button(
                        onClick = { launcher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(text = stringResource(id = R.string.grant_permission))
                    }
                }
            }
        }
        }
    }
}
