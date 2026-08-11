package com.pricelens.ml.portion

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

data class MassResult(
    val massGrams: Float,
    val uncertaintyPercent: Float,
    val isConfident: Boolean
)

@Singleton
class MassEstimator @Inject constructor() {

    /**
     * Estimates mass using the formula: mass = projectedArea^1.5 * shapeFactor * density (G-04).
     */
    fun estimateMass(
        projectedAreaMm2: Float,
        shapeFactor: Float,
        densityKgL: Float,
        distanceMm: Float,
        distanceUncertaintyMm: Float,
        shapeUncertainty: Float = 0.1f, // Typical variance in morphology
        densityUncertainty: Float = 0.05f // From food refs
    ): MassResult {
        // mass = volume * density. Volume is proportional to area^1.5 for a 3D object.
        // Area has dimensions L^2, so Area^1.5 has dimensions L^3 (Volume).
        // mm3 to cm3 (mL) conversion: 1e-3. densityKgL is kg/1000cm3.
        // We use mm throughout for precision, then convert at the end.
        val massKg = (projectedAreaMm2.pow(1.5f) * shapeFactor) * (densityKgL / 1e6f)
        val massGrams = massKg * 1000f

        // Propagate uncertainty: σ_mass/mass ≈ sqrt((2σ_d/d)² + σ_shape² + σ_density²)
        // Note: 2*σ_d/d because area depends on distance squared.
        val relDistanceUncertainty = distanceUncertaintyMm / distanceMm
        val sigmaMassRel = sqrt(
            (2 * relDistanceUncertainty).pow(2) + 
            shapeUncertainty.pow(2) + 
            densityUncertainty.pow(2)
        )

        return MassResult(
            massGrams = massGrams,
            uncertaintyPercent = sigmaMassRel * 100f,
            // Hard Rule (G-04): relative error > 25% -> not confident
            isConfident = sigmaMassRel <= 0.25f
        )
    }
}
