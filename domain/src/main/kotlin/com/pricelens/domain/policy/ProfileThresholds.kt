package com.pricelens.domain.policy

import com.pricelens.domain.model.CategoryProfile

/**
 * Resolves category-varying thresholds from a [CategoryProfile].
 * (L-02 compliance)
 */
class ProfileThresholds(private val profile: CategoryProfile) {
    
    // Consensus
    val logTolerance: Float get() = profile.logTolerance
    val consensusMinObs: Int get() = profile.consensusMinObs
    val consensusMinContributors: Int get() = profile.consensusMinContributors
    val consensusWindowDays: Int get() = profile.consensusWindowDays
    val consensusMinCombinedWeight: Float get() = 2.0f // Standard minimum weight
    
    // Maturity Gate
    val minObservationsForVerdict: Int get() = profile.minObservationsForVerdict
    val maxVerdictAgeDays: Int get() = profile.maxVerdictAgeDays
    
    // Price Verdict
    val verdictWellAboveThreshold: Float get() = profile.verdictWellAboveThreshold
    
    // Quantity Bounds
    val maxPlausibleKg: Double get() = profile.quantityBounds.maxPlausibleKg
    val maxPlausiblePieces: Double get() = profile.quantityBounds.maxPlausiblePieces

    /**
     * Replaces Thresholds.MAX_RELATIVE_ERROR_FOR_MASS (G-04).
     * Hardcoded for now as it's a model property, but could move to profile if needed.
     */
    val maxRelativeErrorForMass: Float get() = 0.25f
}
