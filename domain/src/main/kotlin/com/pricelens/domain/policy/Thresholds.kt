package com.pricelens.domain.policy

import com.pricelens.domain.model.CategoryProfile

/**
 * Centralized source of truth for global ML and integrity thresholds.
 * (E-10 and L-02 compliance)
 * 
 * NOTE: Category-varying thresholds have been moved to [ProfileThresholds].
 * For backward compatibility with existing tests, some are bridged here.
 */
object Thresholds {
    // Recognition (Abstention) - Global properties of the models/device
    const val TAU_LABEL = 0.62f
    const val MARGIN_MIN = 0.15f
    const val LOW_DEVICE_RECOGNITION_THRESHOLD = 0.80f

    // Bridged category constants (DO NOT USE in production code - L-02)
    @Deprecated("Use ProfileThresholds", ReplaceWith("ProfileThresholds(CategoryProfile.FUNGIBLE_LOOSE).maxPlausibleKg"))
    val MAX_PLAUSIBLE_KG = CategoryProfile.FUNGIBLE_LOOSE.quantityBounds.maxPlausibleKg
    
    @Deprecated("Use ProfileThresholds", ReplaceWith("ProfileThresholds(CategoryProfile.FUNGIBLE_LOOSE).maxPlausiblePieces"))
    val MAX_PLAUSIBLE_PIECES = CategoryProfile.FUNGIBLE_LOOSE.quantityBounds.maxPlausiblePieces

    // Anti-Rephotography (H-03)
    const val MIN_TREMOR_VARIANCE = 0.0005f
    const val MOIRE_ENERGY_THRESHOLD = 0.15f
    const val PWM_VARIANCE_THRESHOLD = 0.10f

    // Geo-Integrity (H-06)
    const val MAX_TRAVEL_SPEED_KMH = 900.0
    const val SUSPICIOUS_TRAVEL_SPEED_KMH = 200.0
}
