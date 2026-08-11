package com.pricelens.domain.policy

/**
 * Checks if a given quantity is plausible for a category (I-05).
 */
object PlausibilityPolicy {
    fun isQuantityPlausible(
        quantity: Double,
        unit: String,
        thresholds: ProfileThresholds
    ): Boolean {
        return when (unit.lowercase()) {
            "kg", "l" -> quantity <= thresholds.maxPlausibleKg
            "piece" -> quantity <= thresholds.maxPlausiblePieces
            else -> true
        }
    }
}
