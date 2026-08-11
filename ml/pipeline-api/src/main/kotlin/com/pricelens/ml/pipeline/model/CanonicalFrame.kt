package com.pricelens.ml.pipeline.model

import android.graphics.Bitmap

/**
 * Represents a frame that has been normalized geometrically and photometrically.
 * (Rectilinear, 55° hFOV, 448x448, sRGB-D65).
 */
data class CanonicalFrame(
    val bitmap: Bitmap,
    val sensorTimestampNs: Long,
    val metadata: Map<String, Any> = emptyMap(),
    val frameHash: String? = null
) {
    companion object {
        const val WIDTH = 448
        const val HEIGHT = 448
        const val HFOV_DEG = 55.0f
    }
}
