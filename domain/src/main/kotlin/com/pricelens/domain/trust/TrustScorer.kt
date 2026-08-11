package com.pricelens.domain.trust

import kotlin.math.max

/**
 * Calculates the total trust weight for an observation (H-07).
 */
object TrustScorer {

    /**
     * Multi-factor trust scoring formula.
     * Weights are monotonic in each factor and bounded to [0, 1.2].
     */
    fun calculateWeight(
        reputation: Float,
        attestationTier: Float,
        provenanceTier: Float,
        geoConfidence: Float,
        deviceReliability: Float,
        freshnessDecay: Float = 1.0f
    ): Float {
        // device_reliability floored at 0.6 (fairness constraint - doc 02)
        val fairDeviceReliability = max(0.6f, deviceReliability)

        val weight = reputation * 
                     attestationTier * 
                     provenanceTier * 
                     geoConfidence * 
                     fairDeviceReliability * 
                     freshnessDecay

        return weight.coerceIn(0.0f, 1.2f)
    }
}
