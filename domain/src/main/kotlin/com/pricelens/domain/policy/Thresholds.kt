package com.pricelens.domain.policy

/**
 * Centralized source of truth for all ML and pricing thresholds. (E-10 compliance)
 * No threshold literal should exist anywhere else in the codebase.
 */
object Thresholds {
    // Recognition (Abstention)
    const val TAU_LABEL = 0.62f
    const val MARGIN_MIN = 0.15f
    const val LOW_DEVICE_RECOGNITION_THRESHOLD = 0.80f

    // Price Verdict Boundaries (Multipliers of P90)
    const val VERDICT_ABOVE_USUAL_THRESHOLD = 1.0f // Above P90
    const val VERDICT_WELL_ABOVE_THRESHOLD = 1.5f // 1.5x P90

    // Maturity Gate (G-10)
    const val MIN_OBSERVATIONS_FOR_VERDICT = 5
    const val MAX_FRESHNESS_DAYS_FOR_VERDICT = 21

    // Mass Estimation (G-04)
    const val MAX_RELATIVE_ERROR_FOR_MASS = 0.25f

    // Plausibility Limits (I-05)
    const val MAX_PLAUSIBLE_KG = 50.0
    const val MAX_PLAUSIBLE_PIECES = 100.0

    // Anti-Rephotography (H-03)
    const val MIN_TREMOR_VARIANCE = 0.0005f
    const val MOIRE_ENERGY_THRESHOLD = 0.15f
    const val PWM_VARIANCE_THRESHOLD = 0.10f

    // Geo-Integrity (H-06)
    const val MAX_TRAVEL_SPEED_KMH = 900.0
    const val SUSPICIOUS_TRAVEL_SPEED_KMH = 200.0

    // Consensus (H-10)
    const val CONSENSUS_MIN_CONTRIBUTORS = 3
    const val CONSENSUS_WINDOW_DAYS = 14
    const val CONSENSUS_PRICE_LOG_TOLERANCE = 0.25f // ± 25% on log-price
    const val CONSENSUS_MIN_COMBINED_WEIGHT = 2.0f
}
