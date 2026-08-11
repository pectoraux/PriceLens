package com.pricelens.ml.portion

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class MassEstimatorTest {

    private val estimator = MassEstimator()

    @Test
    fun `estimateMass should return high uncertainty when distance error is large`() {
        // 10% distance error: σ_d/d = 0.1
        // σ_mass/mass ≈ sqrt((2 * 0.1)^2 + 0.1^2 + 0.05^2)
        // σ_mass/mass ≈ sqrt(0.04 + 0.01 + 0.0025) = sqrt(0.0525) ≈ 0.229 (22.9%)
        val result = estimator.estimateMass(
            projectedAreaMm2 = 1000f,
            shapeFactor = 0.5f,
            densityKgL = 1.0f,
            distanceMm = 300f,
            distanceUncertaintyMm = 30f // 10%
        )

        assertTrue(result.uncertaintyPercent > 20f)
        assertTrue(result.isConfident) // 22.9% < 25%
    }

    @Test
    fun `estimateMass should not be confident when distance error exceeds budget`() {
        // 15% distance error: σ_d/d = 0.15
        // σ_mass/mass ≈ sqrt((2 * 0.15)^2 + 0.1^2 + 0.05^2)
        // σ_mass/mass ≈ sqrt(0.09 + 0.01 + 0.0025) = sqrt(0.1025) ≈ 0.32 (32%)
        val result = estimator.estimateMass(
            projectedAreaMm2 = 1000f,
            shapeFactor = 0.5f,
            densityKgL = 1.0f,
            distanceMm = 300f,
            distanceUncertaintyMm = 45f // 15%
        )

        assertFalse(result.isConfident) // 32% > 25%
        assertEquals(null, null) // Placeholder
    }
}
