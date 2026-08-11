package com.pricelens.feature.capture.camera

import android.annotation.SuppressLint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.pricelens.core.common.logging.Logger
import com.pricelens.feature.capture.camera.model.AnalyzableFrame
import com.pricelens.feature.capture.camera.model.toMetadata
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@OptIn(ExperimentalCamera2Interop::class)
class CameraSession @Inject constructor(
    private val cameraManager: CameraManager,
    private val sensorManager: SensorManager,
    private val logger: Logger
) : SensorEventListener {
    private val _frames = MutableSharedFlow<AnalyzableFrame>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val frames: SharedFlow<AnalyzableFrame> = _frames

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    private val imuData = ConcurrentLinkedQueue<Triple<Float, Float, Float>>()
    private val windowMs = 500L

    @SuppressLint("UnsafeOptInUsageError")
    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        // Start IMU collection
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        val cameraProvider = cameraManager.getCameraProvider()
        val cameraSelector = cameraManager.selectOptimalCamera(cameraProvider) 
            ?: CameraSelector.DEFAULT_BACK_CAMERA

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val analysisBuilder = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(previewView.display.rotation)

        // C-03: ISP Control overrides
        val camera2Extender = Camera2Interop.Extender(analysisBuilder)
        camera2Extender.setCaptureRequestOption(
            android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE,
            android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL
        )
        camera2Extender.setCaptureRequestOption(
            android.hardware.camera2.CaptureRequest.EDGE_MODE,
            android.hardware.camera2.CaptureRequest.EDGE_MODE_OFF
        )
        camera2Extender.setCaptureRequestOption(
            android.hardware.camera2.CaptureRequest.TONEMAP_MODE,
            android.hardware.camera2.CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE
        )

        camera2Extender.setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                latestCaptureResult = result
            }
        })

        val imageAnalysis = analysisBuilder.build()
        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            val metadata = latestCaptureResult?.toMetadata()
            if (metadata != null) {
                val window = imuData.toList()
                if (!_frames.tryEmit(AnalyzableFrame(imageProxy, metadata, window))) {
                    imageProxy.close()
                }
            } else {
                imageProxy.close()
            }
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            logger.e("CameraSession", "Use case binding failed", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            val sample = Triple(event.values[0], event.values[1], event.values[2])
            imuData.add(sample)
            
            // Prune old samples (roughly 500ms at 200Hz is 100 samples)
            if (imuData.size > 100) imuData.poll()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Volatile
    private var latestCaptureResult: TotalCaptureResult? = null

    fun release() {
        sensorManager.unregisterListener(this)
        cameraExecutor.shutdown()
    }
}
