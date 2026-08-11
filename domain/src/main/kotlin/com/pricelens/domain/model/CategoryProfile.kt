package com.pricelens.domain.model

/**
 * Encapsulates all parameters that vary by product category (doc 09).
 */
data class CategoryProfile(
    val slug: String,
    val archetype: Archetype,
    val version: Int = 1,

    // Agreement and consensus
    val logTolerance: Float,
    val consensusMinObs: Int,
    val consensusMinContributors: Int,
    val consensusWindowDays: Int,

    // Measurement
    val portionEstimation: PortionEstimationMode,
    val quantityBounds: QuantityBounds,

    // Maturity gate
    val minObservationsForVerdict: Int,
    val maxVerdictAgeDays: Int,
    
    // Price Verdict Boundaries
    val verdictWellAboveThreshold: Float = 1.5f
) {
    enum class Archetype {
        FUNGIBLE_LOOSE,
        PACKAGED_SKU,
        DURABLE_MODEL,
        GRADED_MATERIAL
    }

    enum class PortionEstimationMode {
        REQUIRED,
        OPTIONAL,
        FORBIDDEN
    }

    data class QuantityBounds(
        val maxPlausibleKg: Double,
        val maxPlausiblePieces: Double
    )

    companion object {
        /**
         * The default profile for loose produce (reproduces current constants).
         */
        val FUNGIBLE_LOOSE = CategoryProfile(
            slug = "fungible_loose",
            archetype = Archetype.FUNGIBLE_LOOSE,
            logTolerance = 0.25f,
            consensusMinObs = 3,
            consensusMinContributors = 3,
            consensusWindowDays = 14,
            portionEstimation = PortionEstimationMode.REQUIRED,
            quantityBounds = QuantityBounds(
                maxPlausibleKg = 50.0,
                maxPlausiblePieces = 100.0
            ),
            minObservationsForVerdict = 5,
            maxVerdictAgeDays = 21,
            verdictWellAboveThreshold = 1.5f
        )
    }
}
