package com.pricelens.domain.policy

import com.pricelens.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceVerdictPolicyTest {

    private val p10 = Money(100, "USD")
    private val p50 = Money(150, "USD")
    private val p90 = Money(200, "USD")

    @Test
    fun `evaluate should return GOOD_DEAL when below P10`() {
        val result = PriceVerdictPolicy.evaluate(
            quotedPrice = Money(80, "USD"),
            p10 = p10, p50 = p50, p90 = p90,
            nObservations = 10, freshnessDays = 1
        )
        assertEquals(PriceVerdict.GOOD_DEAL, result)
    }

    @Test
    fun `evaluate should return USUAL_RANGE when between P10 and P90`() {
        val result = PriceVerdictPolicy.evaluate(
            quotedPrice = Money(150, "USD"),
            p10 = p10, p50 = p50, p90 = p90,
            nObservations = 10, freshnessDays = 1
        )
        assertEquals(PriceVerdict.USUAL_RANGE, result)
    }

    @Test
    fun `evaluate should return WELL_ABOVE when far above P90`() {
        // P90 is 200. Well above threshold is 1.5x P90 = 300.
        val result = PriceVerdictPolicy.evaluate(
            quotedPrice = Money(350, "USD"),
            p10 = p10, p50 = p50, p90 = p90,
            nObservations = 10, freshnessDays = 1
        )
        assertEquals(PriceVerdict.WELL_ABOVE, result)
    }

    @Test
    fun `evaluate should return INSUFFICIENT_DATA when observations are low`() {
        val result = PriceVerdictPolicy.evaluate(
            quotedPrice = Money(150, "USD"),
            p10 = p10, p50 = p50, p90 = p90,
            nObservations = 3, freshnessDays = 1
        )
        assertEquals(PriceVerdict.INSUFFICIENT_DATA, result)
    }
}
