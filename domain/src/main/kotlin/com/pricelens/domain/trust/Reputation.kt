package com.pricelens.domain.trust

import kotlin.math.sqrt

/**
 * Implements the Beta-Bernoulli reputation model with Lower Confidence Bound (H-08).
 * Ensures reputation is slow to earn and fast to lose.
 */
data class Reputation(
    val agreements: Double = 0.0,
    val disagreements: Double = 0.0
) {
    /**
     * Calculates the reputation score as the Lower Confidence Bound (P10) of the Beta distribution.
     */
    fun score(): Float {
        val a = agreements + 1.0
        val b = disagreements + 1.0
        val sum = a + b
        
        val mean = a / sum
        val variance = (a * b) / (sum * sum * (sum + 1.0))
        val stdDev = sqrt(variance)
        
        // z = 1.28 for P10 (10th percentile)
        val lcb = mean - 1.28 * stdDev
        
        return lcb.toFloat().coerceIn(0.0f, 1.0f)
    }

    /**
     * Updates reputation based on a new outcome.
     * Disagreements carry 2.5x the weight of agreements.
     */
    fun update(isAgreement: Boolean, weight: Float = 1.0f): Reputation {
        return if (isAgreement) {
            copy(agreements = agreements + weight)
        } else {
            // "Fast to lose": disagreements weighted 2.5x
            copy(disagreements = disagreements + weight * 2.5)
        }
    }
}
