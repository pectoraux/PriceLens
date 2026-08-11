package com.pricelens.domain.policy

import com.pricelens.domain.model.CategoryProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileThresholdsTest {

    @Test
    fun `ProfileThresholds with FUNGIBLE_LOOSE should match old constants`() {
        val profile = CategoryProfile.FUNGIBLE_LOOSE
        val thresholds = ProfileThresholds(profile)

        // Consensus
        assertEquals(0.25f, thresholds.logTolerance, 0.001f)
        assertEquals(3, thresholds.consensusMinObs)
        assertEquals(3, thresholds.consensusMinContributors)
        assertEquals(14, thresholds.consensusWindowDays)
        assertEquals(2.0f, thresholds.consensusMinCombinedWeight, 0.001f)

        // Maturity Gate
        assertEquals(5, thresholds.minObservationsForVerdict)
        assertEquals(21, thresholds.maxVerdictAgeDays)

        // Quantity Bounds (via Bridge check)
        assertEquals(Thresholds.MAX_PLAUSIBLE_KG, thresholds.maxPlausibleKg, 0.001)
        assertEquals(Thresholds.MAX_PLAUSIBLE_PIECES, thresholds.maxPlausiblePieces, 0.001)
    }
}
