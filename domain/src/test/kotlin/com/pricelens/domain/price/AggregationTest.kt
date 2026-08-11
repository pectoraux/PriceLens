package com.pricelens.domain.price

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregationTest {

    @Test
    fun `aggregate should handle exact consensus`() {
        val obs = listOf(
            PriceObservation("u1", 200, 1.0f),
            PriceObservation("u2", 200, 1.0f),
            PriceObservation("u3", 200, 1.0f)
        )
        
        val band = Aggregation.aggregate(obs)
        assertEquals(200L, band?.p50)
    }

    @Test
    fun `aggregate should reject outliers via MAD`() {
        val obs = listOf(
            PriceObservation("u1", 100, 1.0f),
            PriceObservation("u2", 110, 1.0f),
            PriceObservation("u3", 105, 1.0f),
            PriceObservation("u4", 1000, 1.0f) // Extreme outlier
        )
        
        val band = Aggregation.aggregate(obs)
        // Median should be around 105, not dragged to 300+ by the outlier
        assertTrue(band!!.p50 < 150)
    }

    @Test
    fun `aggregate should enforce influence cap`() {
        // One attacker with many high-weight observations (50.0 total weight)
        val observations = mutableListOf<PriceObservation>()
        repeat(50) { i ->
            observations.add(PriceObservation("attacker", 1000, 1.0f))
        }
        // Nine honest users with 1.0 weight each (9.0 total weight)
        repeat(9) { i ->
            observations.add(PriceObservation("honest_$i", 100, 1.0f))
        }
        
        val band = Aggregation.aggregate(observations)
        
        // Attacker raw weight is 50/59 ≈ 84%.
        // Capped weight should be 15% of total.
        // If the median was dragged to 1000, the cap is failing.
        // Since honest users have majority weight after capping, p50 should be 100.
        assertEquals(100L, band?.p50)
    }
}
