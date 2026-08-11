package com.pricelens.domain.policy

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
        freshnessDays: Int
    ): PriceVerdict {
        // 1. Maturity Gate (G-10)
        if (nObservations < Thresholds.MIN_OBSERVATIONS_FOR_VERDICT || 
            freshnessDays > Thresholds.MAX_FRESHNESS_DAYS_FOR_VERDICT) {
            return PriceVerdict.INSUFFICIENT_DATA
        }

        val price = quotedPrice.minorUnits.toFloat()
        val low = p10.minorUnits.toFloat()
        val high = p90.minorUnits.toFloat()

        return when {
            price < low -> PriceVerdict.GOOD_DEAL
            price <= high -> PriceVerdict.USUAL_RANGE
            price <= high * Thresholds.VERDICT_WELL_ABOVE_THRESHOLD -> PriceVerdict.ABOVE_USUAL
            else -> PriceVerdict.WELL_ABOVE
        }
    }
}
