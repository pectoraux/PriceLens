package com.pricelens.domain.policy

import com.pricelens.domain.model.CategoryProfile
import com.pricelens.domain.model.Money

enum class PriceVerdict {
    GOOD_DEAL,
    USUAL_RANGE,
    ABOVE_USUAL,
    WELL_ABOVE,
    INSUFFICIENT_DATA
}

object PriceVerdictPolicy {

    /**
     * Determines the fair-price verdict for a given price based on its cell band (G-10).
     */
    fun evaluate(
        quotedPrice: Money,
        p10: Money,
        p50: Money,
        p90: Money,
        nObservations: Int,
        freshnessDays: Int,
        thresholds: ProfileThresholds = ProfileThresholds(CategoryProfile.FUNGIBLE_LOOSE)
    ): PriceVerdict {
        // 1. Maturity Gate (G-10)
        if (nObservations < thresholds.minObservationsForVerdict || 
            freshnessDays > thresholds.maxVerdictAgeDays) {
            return PriceVerdict.INSUFFICIENT_DATA
        }

        val price = quotedPrice.minorUnits.toFloat()
        val low = p10.minorUnits.toFloat()
        val high = p90.minorUnits.toFloat()

        return when {
            price < low -> PriceVerdict.GOOD_DEAL
            price <= high -> PriceVerdict.USUAL_RANGE
            price <= high * thresholds.verdictWellAboveThreshold -> PriceVerdict.ABOVE_USUAL
            else -> PriceVerdict.WELL_ABOVE
        }
    }
}
