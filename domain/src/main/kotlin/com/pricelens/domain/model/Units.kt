package com.pricelens.domain.model

enum class CanonicalUnit(val displayName: String) {
    KG("kg"),
    L("L"),
    PIECE("piece"),
    BUNCH("bunch"),
    BAG("bag"),
    CRATE("crate")
}

/**
 * Handles locality-specific vernacular unit conversions (G-05).
 */
data class UnitConversion(
    val vernacularUnit: String,
    val canonicalUnit: CanonicalUnit,
    val factor: Float,
    val confidence: ConversionConfidence
)

enum class ConversionConfidence {
    MEASURED, ESTIMATED, ASSUMED
}
