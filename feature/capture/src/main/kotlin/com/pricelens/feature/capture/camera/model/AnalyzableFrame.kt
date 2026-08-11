package com.pricelens.feature.capture.camera.model

import android.hardware.camera2.CaptureResult
import androidx.camera.core.ImageProxy

/**
 * Encapsulates a camera frame and its associated [CaptureResult] metadata.
 * The [image] must be closed by the consumer.
 */
data class AnalyzableFrame(
    val image: ImageProxy,
    val metadata: CaptureResultMetadata,
    val imuWindow: List<Triple<Float, Float, Float>>? = null
)

/**
 * Subset of [CaptureResult] metadata needed for Stage 0 and Stage 1.
 */
data class CaptureResultMetadata(
    val exposureTimeNs: Long?,
    val sensitivity: Int?,
    val lensAperture: Float?,
    val sensorTimestamp: Long?,
    val focalLength: Float?,
    val afState: Int?,
    val awbState: Int?,
    val aeState: Int?,
    val focusDistance: Float? = null
)

internal fun CaptureResult.toMetadata(): CaptureResultMetadata {
    return CaptureResultMetadata(
        exposureTimeNs = get(CaptureResult.SENSOR_EXPOSURE_TIME),
        sensitivity = get(CaptureResult.SENSOR_SENSITIVITY),
        lensAperture = get(CaptureResult.LENS_APERTURE),
        sensorTimestamp = get(CaptureResult.SENSOR_TIMESTAMP),
        focalLength = get(CaptureResult.LENS_FOCAL_LENGTH),
        afState = get(CaptureResult.CONTROL_AF_STATE),
        awbState = get(CaptureResult.CONTROL_AWB_STATE),
        aeState = get(CaptureResult.CONTROL_AE_STATE),
        focusDistance = get(CaptureResult.LENS_FOCUS_DISTANCE)
    )
}
