package com.pricelens.core.geo

import javax.inject.Inject
import javax.inject.Singleton

data class DistanceResult(
    val distanceMm: Float,
    val uncertaintyMm: Float,
    val source: String
)

@Singleton
class DistanceEstimator @Inject constructor() {

    /**
     * Estimates distance using a preference chain (G-01).
     * 1. ARCore Depth (if present in metadata)
     * 2. Lens Focus Distance
     * 3. Fallback / Manual
     */
    fun estimateDistance(frameMetadata: Map<String, Any>): DistanceResult {
        // 1. ARCore Depth Preference
        val arDepth = frameMetadata["ar_depth_mm"] as? Float
        if (arDepth != null && arDepth > 0) {
            return DistanceResult(arDepth, arDepth * 0.05f, "ARCORE_DEPTH")
        }

        // 2. Lens Focus Distance Fallback
        val focusDistanceDioptres = frameMetadata["lens_focus_distance"] as? Float
        val calibration = frameMetadata["lens_calibration"] as? String
        if (focusDistanceDioptres != null && focusDistanceDioptres > 0 && 
            (calibration == "APPROXIMATE" || calibration == "CALIBRATED")) {
            val distanceMm = (1.0f / focusDistanceDioptres) * 1000f
            // Focus distance uncertainty is typically higher, ~15%
            return DistanceResult(distanceMm, distanceMm * 0.15f, "LENS_FOCUS")
        }

        // 3. No reliable signal
        return DistanceResult(300f, 300f * 0.5f, "UNKNOWN")
    }
}
