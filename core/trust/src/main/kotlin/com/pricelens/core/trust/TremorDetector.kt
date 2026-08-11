package com.pricelens.core.trust

import com.pricelens.core.common.logging.Logger
import com.pricelens.domain.policy.Thresholds
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class TremorDetector @Inject constructor(
    private val logger: Logger
) {
    /**
     * Analyzes IMU data to detect characteristic human hand micro-tremors (2-5Hz).
     * Returns a score where 1.0 is "handheld" and < 0.3 is "suspiciously static".
     */
    fun analyzeTremor(gyroSamples: List<Triple<Float, Float, Float>>): Float {
        if (gyroSamples.size < 10) return 0.5f // Insufficient data

        // Calculate variance of gyro magnitude
        val magnitudes = gyroSamples.map { (x, y, z) -> sqrt(x * x + y * y + z * z) }
        val mean = magnitudes.average().toFloat()
        val variance = magnitudes.map { (it - mean) * (it - mean) }.average().toFloat()

        logger.i("TremorDetector", "Gyro Variance: $variance")

        // Heuristic: real human hands have a non-zero floor of tremor (Thresholds.MIN_TREMOR_VARIANCE).
        // A phone in a rig or a re-photography setup will be near-zero.
        return when {
            variance < Thresholds.MIN_TREMOR_VARIANCE -> 0.1f // Too still (rig/emulator)
            variance < Thresholds.MIN_TREMOR_VARIANCE * 2 -> 0.4f  // Suspiciously still
            else -> 1.0f               // Likely handheld
        }
    }
}
